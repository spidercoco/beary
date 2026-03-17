package com.deepinmind.bear.controller;

import com.deepinmind.bear.agent.BearyAgent;
import com.deepinmind.bear.agent.Router;
import com.deepinmind.bear.agent.SubAgent;
import com.deepinmind.bear.oss.OSSService;
import com.deepinmind.bear.service.MessageService;
import com.deepinmind.bear.service.VoiceRecognitionService;
import com.deepinmind.bear.session.Session;
import io.agentscope.core.message.Msg;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/aibeary/${namespace}/bot")
public class BotController {

    @Autowired
    private com.deepinmind.bear.agent.BotAgent botAgent;

    @Autowired
    private MessageService messageService;

    @Autowired
    private Router router;

    @Autowired
    private OSSService ossService;

    @Autowired
    private VoiceRecognitionService voiceRecognitionService;

    @Autowired
    private com.deepinmind.bear.service.HomeworkService homeworkService;

    @Value("${namespace}")
    private String namespace;

    /**
     * 健康检查接口
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "ok");
        response.put("namespace", namespace);
        response.put("timestamp", System.currentTimeMillis());
        return ResponseEntity.ok(response);
    }

    /**
     * 语音对话接口 - 识别并回复
     */
    @PostMapping("/voice")
    public ResponseEntity<Map<String, Object>> botVoice(
            @RequestParam("audio") MultipartFile audioFile,
            @RequestParam(required = false) String role) {
        log.info("Received bot voice command from role: {}", role);
        
        Map<String, Object> response = new HashMap<>();
        try {
            // 1. 上传到 OSS 供 ASR 访问
            String objectName = namespace + "/tmp/asr_" + System.currentTimeMillis() + ".wav";
            String fileUrl = ossService.uploadFile(objectName, audioFile);
            
            // 2. 语音识别
            String transcript = voiceRecognitionService.recognize(fileUrl);
            log.info("ASR transcript: {}", transcript);
            
            if (transcript == null || transcript.isEmpty()) {
                throw new Exception("未能识别出文字");
            }

            // 3. 调用 Agent 处理
            Session session = new Session();
            String finalInput = transcript;
            if (role != null) {
                session.setUser(java.util.concurrent.CompletableFuture.completedFuture(role));
                finalInput = "我是" + role + "。" + transcript;
            }
            
            io.agentscope.core.message.Msg resultMsg = botAgent.call(session, finalInput, null).block();
            
            response.put("status", "success");
            response.put("transcript", transcript);
            response.put("content", resultMsg != null ? resultMsg.getTextContent() : "无响应");
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Bot voice command failed", e);
            response.put("status", "error");
            response.put("message", e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * 机器人对话接口 - 支持流式 (SSE)
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> botStream(@RequestParam String content, @RequestParam(required = false) String role) {
        log.info("Received streaming bot command: {}, role: {}", content, role);
        Session session = new Session();
        String finalInput = content;
        if (role != null) {
            session.setUser(java.util.concurrent.CompletableFuture.completedFuture(role));
            finalInput = "我是" + role + "。" + content;
        }

        return botAgent.streamCall(session, finalInput)
                .map(event -> {
                    if (event.getMessage() != null) {
                        return event.getMessage().getTextContent();
                    }
                    return "";
                })
                .filter(text -> !text.isEmpty());
    }

    /**
     * 机器人对话接口 - 同步返回
     */
    @PostMapping("/command")
    public ResponseEntity<Map<String, Object>> botCommand(@RequestBody Map<String, String> payload) {
        String content = payload.get("content");
        String base64Image = payload.get("image");
        String role = payload.get("role");
        log.info("Received unified bot command: {}, role: {}", content, role);

        Map<String, Object> response = new HashMap<>();
        try {
            Session session = new Session();
            String finalInput = content;
            if (role != null) {
                session.setUser(java.util.concurrent.CompletableFuture.completedFuture(role));
                finalInput = "我是" + role + "。" + content;
            }
            
            io.agentscope.core.message.Msg resultMsg = botAgent.call(session, finalInput, base64Image).block();
            
            response.put("status", "success");
            response.put("content", resultMsg != null ? resultMsg.getTextContent() : "无响应");
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
