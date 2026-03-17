package com.deepinmind.bear.controller;

import com.deepinmind.bear.service.HomeworkService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/aibeary/${namespace}/homework")
public class HomeworkController {

    @Autowired
    private HomeworkService homeworkService;

    @PostMapping("/submit")
    public ResponseEntity<Map<String, Object>> submit(@RequestBody Map<String, String> data) {
        String content = data.get("content");
        Map<String, Object> response = new HashMap<>();
        
        if (content == null || content.trim().isEmpty()) {
            response.put("status", "error");
            response.put("message", "作业内容不能为空");
            return ResponseEntity.badRequest().body(response);
        }

        try {
            String result = homeworkService.saveHomework(content);
            response.put("status", "success");
            response.put("message", result);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", "提交失败: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }
}
