package com.deepinmind.bear.service;

import java.util.Base64;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.deepinmind.bear.agent.ChildMonitorAgent;
import com.deepinmind.bear.core.AudioService;
import com.deepinmind.bear.session.Session;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class ChildMonitorService {

    @Autowired
    private ChildMonitorAgent childMonitorAgent;

    @Autowired
    private AudioService qwen3tts;

    @Autowired
    private CameraService cameraService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private final AtomicReference<ScheduledFuture<?>> activeTask = new AtomicReference<>();

    public String checkChild() {
        try {
            // Capture image from camera
            byte[] imageBytes = cameraService.peekFrameRaw();
            if (imageBytes == null) {
                log.error("Failed to capture image from camera");
                return "拍照失败";
            }
            String imageBase64 = Base64.getEncoder().encodeToString(imageBytes);

            Session session = new Session();
            String result = childMonitorAgent.analyze(session, imageBase64)
                    .map(msg -> msg.getTextContent())
                    .block();

            log.info("Child monitor analysis result: {}", result);

            JsonNode jsonNode = objectMapper.readTree(result);

            boolean hasChild = jsonNode.path("hasChild").asBoolean(false);
            String activityType = jsonNode.path("activityType").asText("");
            String warn = jsonNode.path("warn").asText("");

            // Play warn if there's a posture warning
            if (!warn.isEmpty()) {
                qwen3tts.play(warn);
            }

            if (!hasChild) {
                return "没有检测到小朋友";
            }

            return "小朋友正在" + activityType;
        } catch (Exception e) {
            log.error("Failed to check child", e);
            return "检查失败";
        }
    }

    public String startPeriodicMonitor(int durationSeconds, int intervalSeconds) {
        if (intervalSeconds <= 0 || durationSeconds <= 0) {
            return "参数错误：时间和间隔必须大于0";
        }

        // Only one task allowed at a time
        if (activeTask.get() != null && !activeTask.get().isDone()) {
            return "已有正在运行的监控任务，请先停止当前任务再启动新任务";
        }

        ScheduledFuture<?> future = scheduler.scheduleAtFixedRate(() -> {
            try {
                log.info("Executing periodic child monitor");
                checkChild();
            } catch (Exception e) {
                log.error("Periodic monitor error", e);
            }
        }, 0, intervalSeconds, TimeUnit.SECONDS);

        activeTask.set(future);

        // Auto-stop after duration
        scheduler.schedule(() -> {
            stopPeriodicMonitor();
            log.info("Periodic monitor auto-stopped after {} seconds", durationSeconds);
        }, durationSeconds, TimeUnit.SECONDS);

        return String.format("已开始周期性监控，将持续%d秒，每%d秒检查一次", durationSeconds, intervalSeconds);
    }

    public String stopPeriodicMonitor() {
        ScheduledFuture<?> future = activeTask.getAndSet(null);
        if (future != null && !future.isDone()) {
            future.cancel(false);
            return "已停止监控任务";
        }
        return "当前没有运行中的监控任务";
    }

    @PreDestroy
    public void destroy() {
        stopPeriodicMonitor();
        scheduler.shutdown();
    }
}
