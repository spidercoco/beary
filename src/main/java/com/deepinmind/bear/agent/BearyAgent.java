package com.deepinmind.bear.agent;

import com.deepinmind.bear.core.AudioService;
import com.deepinmind.bear.core.Player;
import com.deepinmind.bear.session.Session;

import ch.qos.logback.core.util.StringUtil;
import io.agentscope.core.agent.Event;
import io.micrometer.common.util.StringUtils;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

import javax.sound.sampled.LineUnavailableException;

@Service
@Slf4j
public class BearyAgent {

    @Value("${dashscope.key}")
    private String dashscopeKey;
    // 会话ID

    public long time = System.currentTimeMillis();

    @Autowired
    private Router router;

    // 控制标志

    @Autowired
    private AudioService tts;

    private boolean done = true;

    /**
     * 同步调用并返回完整消息文本
     */
    public io.agentscope.core.message.Msg call(Session session, String input, String base64Image) {
        SubAgent subAgent = router.route(session, input);
        if (subAgent == null) return null;

        String finalInput = input;
        try {
            String user = session.getUser().get(100, TimeUnit.MILLISECONDS);
            if (StringUtils.isNotBlank(user)) {
                finalInput = "我是" + user + "。" + input;
            }
        } catch (Exception e) {
            log.error("Failed to get user from session", e);
        }

        log.info("Unified call input: {}", finalInput);
        
        // 直接调用 subAgent 的非流式方法，或者从流式中聚合文本
        // 这里采用聚合流式结果的方式，以兼容所有子 Agent
        return subAgent.streamCall(session, finalInput)
                .map(event -> event.getMessage())
                .filter(msg -> msg != null)
                .reduce((m1, m2) -> {
                    // 这里可以根据需要拼接或聚合，但通常最后一个消息包含完整文本或流式拼接
                    // 简单的流式拼接逻辑：
                    return m2; 
                })
                .block();
    }

    public void streamCall(Session session, String input) {
        synchronized (this) {
            while (!done) {
                try {
                    this.wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            done = false;
            call(session, input);
        }
    }

    private void call(Session session, String input) {

        SubAgent subAgent = router.route(session, input);

        if (subAgent == null) {
            synchronized (this) {
                done = true;
                this.notifyAll();
            }
            return;
        }

        String finalInput = input;
        String user = "";
        try {
            user = session.getUser().get(100, TimeUnit.MILLISECONDS);   
        } catch (Exception e) {
            log.error("Failed to get user from session", e);
        }

        if(StringUtils.isNotBlank(user)) {
            finalInput = "我是" + user + "。" + input;
        }
        

        log.info("finalInput: {}", finalInput);

        Flux<Event> response = subAgent.streamCall(session, finalInput);

        try {
            Player player = tts.getNewPlayer(session);
            StringBuilder textBuffer = new StringBuilder();

            response.doFinally(sig -> {
                // Send any remaining text in the buffer before stopping
                if (textBuffer.length() > 0 && !session.isStop()) {
                    player.play(textBuffer.toString());
                }
                player.stop();
                log.info("Response completed3.");
                synchronized (this) {
                    done = true;
                    this.notifyAll();
                }

            }).subscribe(event -> {
                // Handle text content
                if (event.getMessage() != null) {
                    String responseText = event.getMessage().getTextContent();

                    if (event.isLast()) {
                        log.info("last message: {}", responseText);
                        // Ensure any final buffered text is played
                        if (textBuffer.length() > 0 && !session.isStop()) {
                            player.play(textBuffer.toString());
                            textBuffer.setLength(0);
                        }
                    } else {
                        log.info("non-last message: {}", responseText);
                        if (!session.isStop()) {
                            textBuffer.append(responseText);
                            // Check for punctuation or length to flush buffer
                            if (responseText.matches(".*[，。！？,!\\?\\n].*") || textBuffer.length() > 15) {
                                player.play(textBuffer.toString());
                                textBuffer.setLength(0); // Clear buffer
                            }
                        } else {
                            log.info("Session stopped, not playing: {}", responseText);
                            textBuffer.setLength(0); // Clear buffer if stopped
                        }
                    }
                }

            });
        } catch (LineUnavailableException | InterruptedException e) {
            e.printStackTrace();
        } finally {
            log.info("Response completed.");
        }
    }

}