package com.deepinmind.bear.service;

import com.deepinmind.bear.agent.BotAgent;
import com.deepinmind.bear.core.WSService;
import com.deepinmind.bear.session.Session;
import io.agentscope.core.message.Msg;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

/**
 * WebSocket 指令处理器：处理机器人指令 (@机器人)
 * 指令名称: bot_command
 * 支持文本、图片或两者结合
 * 该 Agent 不走 Router。
 */
@Slf4j
@Service("bot_command")
public class BotWSService implements WSService {

    @Autowired
    private BotAgent botAgent;

    @Override
    public Map<String, String> handleMessage(Map<String, String> message) {
        String content = message.get("content");
        String base64Image = message.get("image");
        log.info("Received bot_command: content={}, hasImage={}", content, base64Image != null);

        Map<String, String> resultMap = new HashMap<>();
        Session session = new Session();

        try {
            String input = (content != null && !content.isEmpty()) ? content : "请分析这张图片并完成任务";
            
            // 直接调用 BotAgent，它内部会根据是否有图片选择 vision 或 text 模型
            Mono<Msg> resultMono = botAgent.call(session, input, base64Image);
            Msg resultMsg = resultMono.block();
            
            resultMap.put("status", "success");
            resultMap.put("content", resultMsg.getTextContent());
            
        } catch (Exception e) {
            log.error("Bot command execution failed", e);
            resultMap.put("status", "error");
            resultMap.put("content", "执行失败: " + e.getMessage());
        }

        return resultMap;
    }
}
