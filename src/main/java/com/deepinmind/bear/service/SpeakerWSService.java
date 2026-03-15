package com.deepinmind.bear.service;

import com.deepinmind.bear.core.WSService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * WebSocket 指令处理器：声纹注册
 * 指令名称: voice_enroll
 */
@Slf4j
@Service("voice_enroll")
public class SpeakerWSService implements WSService {

    @Autowired
    private SpeakerService speakerService;

    @Autowired
    private com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    @Override
    public Map<String, String> handleMessage(Map<String, String> message) {
        String content = message.get("content"); 
        
        log.info("Received voice_enroll command via JSON");
        Map<String, String> resultMap = new HashMap<>();

        try {
            if (content == null) {
                resultMap.put("status", "error");
                resultMap.put("message", "Empty content");
                return resultMap;
            }

            // 直接解析 JSON
            Map<String, String> payload = objectMapper.readValue(content, Map.class);
            String role = payload.get("role");
            String base64Audio = payload.get("audio");

            if (role == null || base64Audio == null) {
                resultMap.put("status", "error");
                resultMap.put("message", "Missing 'role' or 'audio' in JSON");
                return resultMap;
            }

            log.info("Enrolling voiceprint for role: {}", role);
            boolean success = speakerService.registerFromBase64(role, base64Audio);
            
            if (success) {
                resultMap.put("status", "success");
                resultMap.put("message", "声纹注册成功");
            } else {
                resultMap.put("status", "error");
                resultMap.put("message", "声纹注册失败");
            }
            
        } catch (Exception e) {
            log.error("Voice enrollment failed: {}", e.getMessage());
            resultMap.put("status", "error");
            resultMap.put("message", "Enrollment failed: " + e.getMessage());
        }

        return resultMap;
    }
}
