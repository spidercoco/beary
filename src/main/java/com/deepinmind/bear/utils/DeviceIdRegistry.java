package com.deepinmind.bear.utils;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * 设备名称 -> 设备ID 映射加载器
 * 启动时从 classpath 下的配置文件加载到内存
 */
@Component
public class DeviceIdRegistry {

    /**
     * 配置文件路径（classpath 下），可在 application.yml/properties 中覆盖
     * 例如：smarthome.device-config=devices.properties
     */
    @Value("${smarthome.device-config:devices.properties}")
    private String configLocation;

    private Map<String, String> deviceIdMap = Collections.emptyMap();

    @PostConstruct
    public void init() throws IOException {
        Properties properties = new Properties();

        ClassPathResource resource = new ClassPathResource(configLocation);
        if (!resource.exists()) {
            throw new IllegalStateException("设备配置文件不存在: " + configLocation);
        }

        try (Reader reader = new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8)) {
            properties.load(reader);
        }

        Map<String, String> map = new HashMap<>();
        for (String name : properties.stringPropertyNames()) {
            String id = properties.getProperty(name);
            if (id != null) {
                String trimmedName = name.trim();
                String trimmedId = id.trim();
                if (!trimmedName.isEmpty() && !trimmedId.isEmpty()) {
                    map.put(trimmedName, trimmedId);
                }
            }
        }

        this.deviceIdMap = Collections.unmodifiableMap(map);
        // 如果需要，可以在这里打印日志
        // log.info("Loaded {} device mappings from {}", deviceIdMap.size(), configLocation);
    }

    /**
     * 获取只读的设备映射表
     */
    public Map<String, String> getDeviceIdMap() {
        return deviceIdMap;
    }

    /**
     * 根据设备名称获取设备ID（例如 KNX GA）
     */
    public String getDeviceId(String deviceName) {
        return deviceIdMap.get(deviceName);
    }
}
