package com.deepinmind.bear.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import lombok.extern.slf4j.Slf4j;

import java.util.Base64;
import java.util.Map;

/**
 * Service to push messages from Home (Aibeary) to iOS App (via Admin).
 */
@Slf4j
@Service
public class MessagePushService {

    @Value("${namespace}")
    private String namespace;

    private final RestTemplate restTemplate = new RestTemplate();
    private final String msgboxUrlTemplate = "http://it.deepinmind.com/api/%s/msgbox";

    /**
     * Push a text message to the iOS app.
     */
    public boolean pushText(String content) {
        return send(content, "text");
    }

    /**
     * Push an image to the iOS app.
     */
    public boolean pushImage(byte[] imageBytes) {
        String base64Image = Base64.getEncoder().encodeToString(imageBytes);
        return send(base64Image, "image");
    }

    private boolean send(String content, String type) {
        try {
            String url = String.format(msgboxUrlTemplate, namespace);
            Map<String, String> body = Map.of(
                "content", content,
                "type", type
            );
            
            log.info("Pushing {} message to App...", type);
            ResponseEntity<String> response = restTemplate.postForEntity(url, body, String.class);
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            log.error("Failed to push message: {}", e.getMessage());
            return false;
        }
    }
}
