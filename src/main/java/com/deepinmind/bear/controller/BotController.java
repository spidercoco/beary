package com.deepinmind.bear.controller;

import com.deepinmind.bear.agent.BotAgent;
import com.deepinmind.bear.service.MessageService;
import com.deepinmind.bear.session.Session;
import io.agentscope.core.message.Msg;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/aibeary/${namespace}/bot")
public class BotController {

    @Autowired
    private BotAgent botAgent;

    @Autowired
    private MessageService messageService;

    /**
     * 机器人对话接口 (替代 bot_command WebSocket)
     */
    @PostMapping("/command")
    public ResponseEntity<Map<String, Object>> botCommand(@RequestBody Map<String, String> payload) {
        String content = payload.get("content");
        String base64Image = payload.get("image");
        log.info("Received bot command request: {}", content);

        Map<String, Object> response = new HashMap<>();
        try {
            String input = (content != null && !content.isEmpty()) ? content : "请分析这张图片";
            Mono<Msg> resultMono = botAgent.call(new Session(), input, base64Image);
            Msg resultMsg = resultMono.block();
            
            response.put("status", "success");
            response.put("content", resultMsg.getTextContent());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Bot command failed", e);
            response.put("status", "error");
            response.put("message", e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * 留言/消息接口 (替代 message WebSocket)
     */
    @PostMapping("/message")
    public ResponseEntity<Map<String, Object>> sendMessage(@RequestBody Map<String, String> payload) {
        log.info("Received message request: {}", payload);
        
        // 复用 MessageService 的逻辑
        Map<String, String> result = messageService.handleMessage(payload);
        
        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("data", result);
        return ResponseEntity.ok(response);
    }
}
