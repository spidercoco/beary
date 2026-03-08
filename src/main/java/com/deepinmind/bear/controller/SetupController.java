package com.deepinmind.bear.controller;

import com.deepinmind.bear.utils.ConfigManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
public class SetupController {

    @PostMapping("/api/setup/save")
    public Map<String, Object> saveConfig(@RequestBody Map<String, String> data) {
        Map<String, Object> response = new HashMap<>();
        try {
            String ns = data.get("namespace");
            String key = data.get("apiKey");
            String role = data.get("role");

            ConfigManager.saveConfig(ns, key, role);
            
            response.put("status", "success");
            response.put("message", "配置保存成功，系统即将在 3 秒后重启...");
            
            // 异步重启系统
            new Thread(() -> {
                try {
                    Thread.sleep(3000);
                    log.info("System restarting after configuration...");
                    System.exit(0); // 退出当前进程，依靠守护进程（如 systemd）自动重启加载新配置
                } catch (InterruptedException ignored) {}
            }).start();

        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", "保存失败: " + e.getMessage());
        }
        return response;
    }
}
