package com.deepinmind.bear.service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Service
public class FrpManagerService {

    @Value("${namespace}")
    private String namespace;

    @Value("${beary.info.path:}")
    private String infoRoot;

    private Process frpProcess;
    private final ExecutorService logExecutor = Executors.newSingleThreadExecutor();

    private String getInfoRoot() {
        if (infoRoot.isEmpty()) return "";
        return infoRoot.endsWith("/") ? infoRoot : infoRoot + "/";
    }

    @PostConstruct
    public void init() {
        String binDir = getInfoRoot() + "beary_info/bin/";
        try {
            Files.createDirectories(Paths.get(binDir));
            String configPath = generateConfig(binDir);
            String binPath = findBinary(binDir);
            
            if (binPath != null) {
                startFrpc(binPath, configPath);
            } else {
                log.warn("FRPC binary not found in {}. Please place frpc executable there.", binDir);
            }
        } catch (IOException e) {
            log.error("Failed to initialize FRP Manager", e);
        }
    }

    private String findBinary(String dir) {
        String os = System.getProperty("os.name").toLowerCase();
        String suffix = os.contains("win") ? ".exe" : "";
        String binPath = dir + "frpc" + suffix;
        File file = new File(binPath);
        if (file.exists()) {
            file.setExecutable(true);
            return file.getAbsolutePath();
        }
        return null;
    }

    private String generateConfig(String dir) throws IOException {
        String configPath = dir + "frpc.toml";
        // 核心隔离逻辑：使用 HTTP 类型的 locations 匹配 namespace
        String configContent = 
            "serverAddr = \"it.deepinmind.com\"\n" +
            "serverPort = 7000\n" +
            "\n" +
            "[[proxies]]\n" +
            "name = \"" + namespace + "_http\"\n" +
            "type = \"http\"\n" +
            "localIP = \"127.0.0.1\"\n" +
            "localPort = 8080\n" +
            "customDomains = [\"it.deepinmind.com\"]\n" +
            "locations = [\"/proxy/" + namespace + "\"]\n"; 

        Files.writeString(Paths.get(configPath), configContent);
        log.info("Generated frpc.toml for namespace: {}", namespace);
        return configPath;
    }

    private void startFrpc(String binPath, String configPath) {
        new Thread(() -> {
            try {
                log.info("Starting managed frpc: {} -c {}", binPath, configPath);
                ProcessBuilder pb = new ProcessBuilder(binPath, "-c", configPath);
                frpProcess = pb.start();

                logExecutor.submit(() -> {
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(frpProcess.getInputStream()))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            log.info("[FRP] {}", line);
                        }
                    } catch (IOException ignored) {}
                });

                int exitCode = frpProcess.waitFor();
                log.warn("FRPC exited with code: {}", exitCode);
            } catch (Exception e) {
                log.error("Failed to run FRPC", e);
            }
        }).start();
    }

    @PreDestroy
    public void stop() {
        if (frpProcess != null) {
            frpProcess.destroy();
        }
        logExecutor.shutdownNow();
    }
}
