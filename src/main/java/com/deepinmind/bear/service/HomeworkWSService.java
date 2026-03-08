package com.deepinmind.bear.service;

import com.deepinmind.bear.core.WSService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * WebSocket 指令处理器：提交作业
 * 指令名称: submit_homework
 */
@Slf4j
@Service("submit_homework")
public class HomeworkWSService implements WSService {

    @Autowired
    private HomeworkService homeworkService;

    @Override
    public Map<String, String> handleMessage(Map<String, String> message) {
        String content = message.get("content");
        
        log.info("Received submit_homework command");
        Map<String, String> resultMap = new HashMap<>();

        try {
            if (content == null || content.trim().isEmpty()) {
                resultMap.put("status", "error");
                resultMap.put("message", "Homework content is empty");
                return resultMap;
            }

            log.info("Saving homework via WebSocket command...");
            String result = homeworkService.saveHomework(content);
            
            resultMap.put("status", "success");
            resultMap.put("message", result);
            
        } catch (Exception e) {
            log.error("Homework submission failed: {}", e.getMessage());
            resultMap.put("status", "error");
            resultMap.put("message", "Submission failed: " + e.getMessage());
        }

        return resultMap;
    }
}
