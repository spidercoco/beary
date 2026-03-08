package com.deepinmind.bear.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.deepinmind.bear.service.ChildMonitorService;

import java.util.HashMap;
import java.util.Map;

/**
 * Controller for child monitoring endpoints.
 */
@RestController
@RequestMapping("/api/monitor")
public class ChildMonitorController {

    @Autowired
    private ChildMonitorService childMonitorService;

    /**
     * Start periodic monitoring.
     * GET /api/monitor/start?durationSeconds=300&intervalSeconds=30
     */
    @GetMapping("/start")
    public Map<String, Object> startMonitor(
            @RequestParam(defaultValue = "300") int durationSeconds,
            @RequestParam(defaultValue = "30") int intervalSeconds) {
        Map<String, Object> response = new HashMap<>();
        String result = childMonitorService.startPeriodicMonitor(durationSeconds, intervalSeconds);
        response.put("status", result.contains("已开始") ? "success" : "error");
        response.put("message", result);
        return response;
    }

    /**
     * Stop periodic monitoring.
     * GET /api/monitor/stop
     */
    @GetMapping("/stop")
    public Map<String, Object> stopMonitor() {
        Map<String, Object> response = new HashMap<>();
        String result = childMonitorService.stopPeriodicMonitor();
        response.put("status", result.contains("已停止") ? "success" : "info");
        response.put("message", result);
        return response;
    }
}
