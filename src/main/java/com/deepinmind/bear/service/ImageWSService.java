package com.deepinmind.bear.service;

import com.deepinmind.bear.agent.BotAgent;
import com.deepinmind.bear.core.WSService;
import com.deepinmind.bear.oss.OSSService;
import com.deepinmind.bear.session.Session;
import io.agentscope.core.message.Msg;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * WebSocket 指令处理器：接收并分析图片
 * 指令名称: submit_image
 */
@Slf4j
@Service("submit_image")
public class ImageWSService implements WSService {

    @Autowired
    private OSSService ossService;

    @Autowired
    private BotAgent botAgent;

    @Value("${namespace}")
    private String namespace;

    @Value("${beary.info.path:}")
    private String infoRoot;

    private final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    @Override
    public Map<String, String> handleMessage(Map<String, String> message) {
        String base64Image = message.get("content");
        log.info("Received submit_image command via WebSocket");
        Map<String, String> resultMap = new HashMap<>();

        try {
            if (base64Image == null || base64Image.isEmpty()) {
                resultMap.put("status", "error");
                resultMap.put("message", "Image data is empty");
                return resultMap;
            }

            // 1. 保存到本地缓存 (保留原有的保存逻辑)
            try {
                byte[] imageBytes = Base64.getDecoder().decode(base64Image);
                String fileName = UUID.randomUUID().toString() + ".jpg";
                String datePath = LocalDate.now().format(dateFormatter);
                String localDir = getInfoRoot() + "beary_info/images/" + datePath + "/";
                Files.createDirectories(Paths.get(localDir));
                Path localPath = Paths.get(localDir + fileName);
                Files.write(localPath, imageBytes);
                log.info("Image saved locally: {}", localPath);
            } catch (Exception e) {
                log.warn("Failed to save image locally, but continuing with analysis: {}", e.getMessage());
            }

            // 2. 调用 BotAgent 进行分析 (因为有图片，BotAgent 内部会选择 vision 模型)
            String prompt = message.getOrDefault("prompt", "请分析这张图片，如果是课程表请帮我更新课程表。");
            Mono<Msg> resultMono = botAgent.call(new Session(), prompt, base64Image);
            Msg resultMsg = resultMono.block();
            
            resultMap.put("status", "success");
            resultMap.put("message", resultMsg.getTextContent());
            
        } catch (Exception e) {
            log.error("Image analysis failed", e);
            resultMap.put("status", "error");
            resultMap.put("message", "Failed to analyze image: " + e.getMessage());
        }

        return resultMap;
    }

    private String getInfoRoot() {
        if (infoRoot.isEmpty()) return "";
        return infoRoot.endsWith("/") ? infoRoot : infoRoot + "/";
    }
}
