package com.deepinmind.bear.service;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Service;

import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class AlarmService {

    private ScheduledExecutorService scheduler;

    @PostConstruct
    public void init() {
        scheduler = Executors.newScheduledThreadPool(2);
        log.info("AlarmService initialized");
    }

    @PreDestroy
    public void destroy() {
        if (scheduler != null) {
            scheduler.shutdown();
        }
    }

    /**
     * Set an alarm that will log a reminder message after specified seconds
     * 
     * @param seconds Number of seconds to wait  before triggering the alarm (must be positive)
     * @param reminderText The reminder message to log when alarm triggers
     * @return Status message indicating success or failure
     */
    @Tool(name = "setAlarm", description = "Set an alarm to log a reminder message after specified seconds")
    public String setAlarm(
            @ToolParam(name = "seconds", description = "Number of seconds to wait before triggering the alarm (must be positive)") int seconds,
            @ToolParam(name = "reminderText", description = "The reminder message to log when alarm triggers") String reminderText) {
        
        log.info("Received alarm request: seconds={}, text='{}'", seconds, reminderText);

        if (seconds <= 0) {
            log.warn("Invalid seconds value: {}", seconds);
            return "Error: Seconds must be a positive number";
        }

        if (reminderText == null || reminderText.trim().isEmpty()) {
            log.warn("Empty reminder text");
            return "Error: Reminder text cannot be empty";
        }

        // Schedule the alarm
        scheduler.schedule(() -> {
            log.info("⏰ ALARM: {}", reminderText);
        }, seconds, TimeUnit.SECONDS);

        log.info("Alarm scheduled for {} seconds later: '{}'", seconds, reminderText);

        return String.format("Alarm set successfully for %d seconds: '%s'", seconds, reminderText);
    }
}
