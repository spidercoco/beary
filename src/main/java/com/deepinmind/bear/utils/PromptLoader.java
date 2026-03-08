package com.deepinmind.bear.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Prompt配置文件加载器
 * 用于加载本地的prompt大文本配置文件
 */
@Component
@Slf4j
public class PromptLoader {

    /**
     * 从classpath加载prompt文件
     * @param fileName 文件名（相对于classpath的路径）
     * @return prompt文本内容
     */
    public String getString(String fileName) {
        try {
            ClassPathResource resource = new ClassPathResource(fileName);
            if (!resource.exists()) {
                log.warn("Prompt file not found in classpath: {}", fileName);
                return "";
            }
            
            return readContent(resource.getInputStream());
        } catch (IOException e) {
            log.error("Error loading prompt file from classpath: {}", fileName, e);
            return "";
        }
    }

    /**
     * 从文件系统加载prompt文件
     * @param filePath 文件的完整路径
     * @return prompt文本内容
     */
    public String getStringFromFile(String filePath) {
        try {
            Path path = Paths.get(filePath);
            if (!Files.exists(path)) {
                log.warn("Prompt file not found in filesystem: {}", filePath);
                throw new RuntimeException("Prompt file not found in filesystem: " + filePath);
            }
            
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("Error loading prompt file from filesystem: {}", filePath, e);
            throw new RuntimeException("Error loading prompt file from filesystem: " + filePath);
        }
    }

    /**
     * 从InputStream读取内容
     * @param inputStream 输入流
     * @return 文本内容
     * @throws IOException 读取异常
     */
    private String readContent(InputStream inputStream) throws IOException {
        StringBuilder content = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
        }
        // System.out.println(content.toString());
        return content.toString();
    }


    /**
     * 加载特定用途的prompt模板
     * @param promptType prompt类型
     * @return 对应的prompt文本
     */
    public String getPromptByType(String promptType) {
        String fileName = "prompts/" + promptType + "-prompt.txt";
        return getString(fileName);
    }
}