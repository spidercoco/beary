package com.deepinmind.bear.utils;

import lombok.extern.slf4j.Slf4j;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Properties;

@Slf4j
public class ConfigManager {

    private static final String CONFIG_FILE = "beary_info/conf/application.properties";

    /**
     * 检查是否已完成初始化配置
     */
    public static boolean isConfigured() {
        File file = new File(CONFIG_FILE);
        if (!file.exists()) return false;

        Properties props = new Properties();
        try (InputStream is = new FileInputStream(file)) {
            props.load(is);
            // 检查核心配置是否存在
            return props.containsKey("dashscope.key") && props.containsKey("namespace");
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * 保存配置到运行时文件
     */
    public static void saveConfig(String namespace, String apiKey, String deviceRole) throws IOException {
        Files.createDirectories(Paths.get("beary_info/conf/"));
        Properties props = new Properties();
        props.setProperty("namespace", namespace);
        props.setProperty("dashscope.key", apiKey);
        props.setProperty("device.role", deviceRole);
        
        // 设置一些默认路径
        props.setProperty("beary.info.path", ""); 
        props.setProperty("wakeword.engine", "onnx");

        try (OutputStream os = new FileOutputStream(CONFIG_FILE)) {
            props.store(os, "Runtime Configuration for Aibeary");
        }
    }

    /**
     * 获取运行时配置文件路径
     */
    public static String getRuntimeConfigPath() {
        return new File(CONFIG_FILE).getAbsolutePath();
    }
}
