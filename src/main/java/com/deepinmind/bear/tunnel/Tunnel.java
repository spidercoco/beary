package com.deepinmind.bear.tunnel;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.enums.ReadyState;
import org.java_websocket.handshake.ServerHandshake;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import com.deepinmind.bear.core.WSService;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class Tunnel {

    @Value("${namespace}")
    private String tunnelName;

    private WebSocketClient tunnelClient;

    private ScheduledExecutorService reconnectExecutor;

    private ScheduledFuture<?> pingTask;

    private final AtomicBoolean reconnecting = new AtomicBoolean(false);

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    ApplicationContext context;

    @PostConstruct
    public void init() {
        reconnectExecutor = Executors.newSingleThreadScheduledExecutor();
        connect();
    }

    @PreDestroy
    public void destroy() {
        log.info("Destroying Tunnel component...");
        if (reconnectExecutor != null) {
            reconnectExecutor.shutdownNow();
        }
        cleanup();
    }

    private void ping() {
        WebSocketClient client = tunnelClient;
        if (client != null && client.isOpen()) {
            log.debug("Sending ping to tunnel...");
            try {
                client.sendPing();
            } catch (Exception e) {
                log.warn("Failed to send ping: {}", e.getMessage());
            }
        }
    }

    private synchronized void connect() {
        if (tunnelClient != null && !tunnelClient.isClosed() && !tunnelClient.isClosing()) {
            log.info("Tunnel is already connected or connecting. Current state: {}", tunnelClient.getReadyState());
            return;
        }

        cleanup();

        try {
            tunnelClient = createWebSocketClient();
            log.info("Attempting to connect to tunnel: {}", tunnelClient.getURI());
            
            // Connect with timeout
            boolean connected = tunnelClient.connectBlocking(10, TimeUnit.SECONDS);
            if (!connected) {
                log.warn("Connection attempt timed out for tunnel: {}", tunnelClient.getURI());
                scheduleReconnect();
            } else {
                log.info("Tunnel connection established.");
                pingTask = reconnectExecutor.scheduleAtFixedRate(this::ping, 10, 10, TimeUnit.SECONDS);
            }
        } catch (URISyntaxException e) {
            log.error("Invalid tunnel URI: {}", e.getMessage());
        } catch (InterruptedException e) {
            log.error("Tunnel connection interrupted");
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.error("Unexpected error during tunnel connection: {}", e.getMessage(), e);
            scheduleReconnect();
        }
    }

    private void cleanup() {
        if (pingTask != null) {
            pingTask.cancel(false);
            pingTask = null;
        }
        if (tunnelClient != null) {
            try {
                WebSocketClient client = tunnelClient;
                tunnelClient = null;
                log.info("Closing tunnel client...");
                client.close();
            } catch (Exception e) {
                log.error("Error closing tunnel client: {}", e.getMessage());
            }
        }
    }

    private void scheduleReconnect() {
        if (reconnectExecutor == null || reconnectExecutor.isShutdown()) {
            return;
        }
        if (reconnecting.compareAndSet(false, true)) {
            log.info("Scheduling tunnel reconnection in 5 seconds...");
            try {
                reconnectExecutor.schedule(() -> {
                    try {
                        reconnecting.set(false);
                        connect();
                    } catch (Exception e) {
                        log.error("Error during scheduled reconnection: {}", e.getMessage());
                    }
                }, 5, TimeUnit.SECONDS);
            } catch (Exception e) {
                log.error("Failed to schedule reconnection: {}", e.getMessage());
                reconnecting.set(false);
            }
        }
    }

    private WebSocketClient createWebSocketClient() throws URISyntaxException {
        URI uri = new URI("ws://it.deepinmind.com/tunnel/" + tunnelName);
        return new WebSocketClient(uri) {
            @Override
            public void onOpen(ServerHandshake handshakedata) {
                log.info("==================================================");
                log.info("Successfully connected to tunnel: {}", getURI());
                log.info("Waiting for requests from the server...");
                log.info("==================================================");
            }

            @Override
            public void onMessage(String message) {
                log.info("--- Tunnel Request Received ---");
                log.debug("Raw JSON from server: {}", message);

                String correlationId = "unknown";
                try {
                    JSONObject requestJson = new JSONObject(message);
                    correlationId = requestJson.optString("correlationId", "unknown");
                    String command = requestJson.getString("command");
                    
                    Map<String, String> map = new HashMap<>();
                    for (String key : requestJson.keySet()) {
                        if (!requestJson.isNull(key)) {
                            map.put(key, requestJson.get(key).toString());
                        }
                    }

                    log.info("Processing command: {} with correlationId: {}", command, correlationId);

                    // Call service handler and get result
                    Map<String, String> resultMap;
                    try {
                        WSService service = context.getBean(command, WSService.class);
                        resultMap = service.handleMessage(map);
                    } catch (Exception e) {
                        log.error("Error executing command '{}': {}", command, e.getMessage());
                        resultMap = new HashMap<>();
                        resultMap.put("error", "Execution failed: " + e.getMessage());
                    }

                    // Convert result Map to JSON string
                    String responseBodyContent;
                    try {
                        responseBodyContent = objectMapper.writeValueAsString(resultMap);
                    } catch (Exception e) {
                        log.error("Failed to serialize result for correlationId {}: {}", correlationId, e.getMessage());
                        responseBodyContent = "{\"error\":\"Serialization failed\"}";
                    }

                    // Construct the response JSON
                    JSONObject responseJson = new JSONObject();
                    responseJson.put("correlationId", correlationId);
                    responseJson.put("responseBody", responseBodyContent);

                    // Send the response back to the server
                    String jsonResponseString = responseJson.toString();
                    send(jsonResponseString);
                    log.info("Response sent for correlationId: {}", correlationId);
                } catch (Exception e) {
                    log.error("Protocol error processing tunnel message: {}", e.getMessage());
                } finally {
                    log.info("--- Tunnel Request Processed ---");
                }
            }

            @Override
            public void onClose(int code, String reason, boolean remote) {
                log.info("Tunnel connection closed by {}. Code: {}, Reason: {}",
                        remote ? "server" : "us", code, reason);
                
                // Only trigger reconnect if this instance is still the active client
                if (this == tunnelClient) {
                    if (pingTask != null) {
                        pingTask.cancel(true);
                        pingTask = null;
                    }
                    scheduleReconnect();
                }
            }

            @Override
            public void onError(Exception ex) {
                log.error("Tunnel WebSocket error: {}", ex.getMessage());
            }
        };
    }
}
