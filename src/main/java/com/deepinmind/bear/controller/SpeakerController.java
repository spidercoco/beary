package com.deepinmind.bear.controller;

import com.deepinmind.bear.service.SpeakerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/aibeary/${namespace}/speaker")
public class SpeakerController {

    @Autowired
    private SpeakerService speakerService;

    /**
     * 注册说话人声纹
     * POST /api/speaker/register?role=爸爸
     * Body: form-data, key="audio", file=@xxx.wav
     */
    @PostMapping("/register")
    public Map<String, Object> register(
            @RequestParam("role") String role,
            @RequestParam("audio") MultipartFile audioFile) {
        
        Map<String, Object> response = new HashMap<>();
        try {
            if (audioFile.isEmpty()) {
                response.put("status", "error");
                response.put("message", "音频文件为空");
                return response;
            }

            String result = speakerService.register(role, audioFile);
            response.put("status", "success");
            response.put("message", result);
            
        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", "注册失败: " + e.getMessage());
        }
        return response;
    }

    /**
     * 获取当前底库中的所有角色 (Debug用)
     */
    @GetMapping("/list")
    public Map<String, Object> list() {
        // 此处可以根据 beary_info/conf/ 目录下的文件列表返回
        return new HashMap<>();
    }
}
