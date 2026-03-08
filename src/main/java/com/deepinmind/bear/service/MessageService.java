package com.deepinmind.bear.service;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.deepinmind.bear.core.AudioService;
import com.deepinmind.bear.core.WSService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service("message")
public class MessageService implements WSService {

    @Autowired
    AudioService qwen3tts;

    @Value("${beary.info.path:}")
    private String infoRoot;

    private final List<Map<String, String>> messageCache = new CopyOnWriteArrayList<>();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private String storageFilePath;

    @PostConstruct
    public void init() {
        storageFilePath = (infoRoot != null && !infoRoot.isEmpty() ? infoRoot : ".") + "/voicemails.json";
        File file = new File(storageFilePath);
        log.info("Voice message storage absolute path: {}", file.getAbsolutePath());
        loadMessages();
    }

    private void loadMessages() {
        try {
            File file = new File(storageFilePath);
            if (file.exists()) {
                List<Map<String, String>> loadedMessages = objectMapper.readValue(file, 
                    new TypeReference<List<Map<String, String>>>() {});
                messageCache.clear();
                messageCache.addAll(loadedMessages);
                log.info("Loaded {} voicemails from {}", messageCache.size(), storageFilePath);
            }
        } catch (IOException e) {
            log.error("Failed to load voicemails from file", e);
        }
    }

    private void saveMessages() {
        try {
            File file = new File(storageFilePath);
            log.info("Saving {} messages to: {}", messageCache.size(), file.getAbsolutePath());
            objectMapper.writeValue(file, messageCache);
            log.info("Successfully saved voicemails to file.");
        } catch (IOException e) {
            log.error("Failed to save voicemails to file: {}", e.getMessage(), e);
        }
    }

    @Override
    public Map<String, String> handleMessage(Map<String, String> message) {
        // 打印收到的JSON消息
        log.info("Received message: {}", message);

        // 缓存消息到列表
        messageCache.add(message);
        saveMessages();

        // 提取并打印text字段中的content内容
        if (message.containsKey("content")) {
            String msg = message.get("content");
            if (message.containsKey("role")) {
                String role = message.get("role");
                msg = role + "说：" + msg;
                qwen3tts.play(msg, role);
            } else {
                qwen3tts.play(msg);
            }

        }

        // 返回响应
        return Map.of("status", "received");
    }

    public List<Map<String, String>> getMessageCache() {
        return new ArrayList<>(messageCache);
    }

    @Tool(name = "getVoicemailCount", description = "获取语音信箱中未读消息的数量")
    public int getVoicemailCount() {
        return messageCache.size();
    }

    @Tool(name = "readVoicemails", description = "读取语音信箱中的消息。可以指定读取最新几条消息。并将消息逐条语音播放，播放后会从信箱中删除。")
    public String readVoicemails(
            @ToolParam(name = "count", description = "要读取的消息数量。如果是0或负数，则读取所有消息。") int count) {
        if (messageCache.isEmpty()) {
            return "语音信箱为空。";
        }

        int size = messageCache.size();
        int actualCount = (count <= 0 || count >= size) ? size : count;
        
        // 确定要读取的消息范围
        List<Map<String, String>> messagesToRead = new ArrayList<>();
        int startIndex = size - actualCount;
        for (int i = 0; i < actualCount; i++) {
            messagesToRead.add(messageCache.get(startIndex + i));
        }

        StringBuilder sb = new StringBuilder();
        for (Map<String, String> m : messagesToRead) {
            String role = m.getOrDefault("role", "未知发送者");
            String content = m.getOrDefault("content", "无内容");
            String displayMsg = String.format("[%s]: %s", role, content);
            
            // 语音播放
            qwen3tts.play(role + "说：" + content, role);
            
            // 从信箱中删除该条消息
            messageCache.remove(m);
            
            sb.append(displayMsg).append("\n");
        }

        saveMessages(); // 删除后保存状态
        return sb.toString().trim();
    }


}
