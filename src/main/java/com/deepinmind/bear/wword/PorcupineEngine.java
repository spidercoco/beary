package com.deepinmind.bear.wword;

import ai.picovoice.porcupine.Porcupine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

@Slf4j
public class PorcupineEngine implements WakeWordEngine {
    private final String accessKey;
    private Porcupine porcupine;

    public PorcupineEngine(String accessKey) {
        this.accessKey = accessKey;
    }

    @Override
    public void init() throws Exception {
        String os = System.getProperty("os.name").toLowerCase();
        String ppnFileName = os.contains("linux") ? "小熊_zh_linux_v4_0_0.ppn" : "小熊_zh_mac_v4_0_0.ppn";
        String ppnPath = extractResource(ppnFileName);
        String pvPath = extractResource("porcupine_params_zh.pv");

        porcupine = new Porcupine.Builder()
                .setAccessKey(accessKey)
                .setKeywordPaths(new String[]{ppnPath})
                .setModelPath(pvPath)
                .build();
        log.info("Porcupine engine initialized successfully.");
    }

    @Override
    public boolean process(short[] samples) {
        try {
            return porcupine.process(samples) >= 0;
        } catch (Exception e) {
            log.error("Porcupine processing error: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public void release() {
        if (porcupine != null) porcupine.delete();
    }

    private String extractResource(String resourceName) throws IOException {
        Path dir = Paths.get("pv_resources");
        if (!Files.exists(dir)) Files.createDirectories(dir);
        Path targetFile = dir.resolve(resourceName);
        try (InputStream is = new ClassPathResource(resourceName).getInputStream()) {
            Files.copy(is, targetFile, StandardCopyOption.REPLACE_EXISTING);
        }
        return targetFile.toAbsolutePath().toString();
    }
}
