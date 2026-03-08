package com.deepinmind.bear.agent;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import com.deepinmind.bear.core.Player;
import com.deepinmind.bear.session.Session;
import com.deepinmind.bear.tools.KnxDeviceControl;
import com.deepinmind.bear.tts.Qwen3TTS;
import com.deepinmind.bear.utils.PromptLoader;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.Event;
import io.agentscope.core.message.Msg;
import io.agentscope.core.model.DashScopeChatModel;
import io.agentscope.core.skill.SkillBox;
import io.agentscope.core.tool.Toolkit;
import kotlin.jvm.internal.SerializedIr;
import kotlin.sequences.MergingSequence;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@Service
public class Router {

    ReActAgent reActAgent;

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    public Router(PromptLoader promptLoader, @Value("${dashscope.key}") String dashscopeKey) {
        // 创建智能体
        reActAgent = ReActAgent.builder()
                .name("Jarvis")
                .sysPrompt(new PromptLoader().getPromptByType("router-agent"))
                // .sysPrompt("你是一个名为 Jarvis 的助手")

                .model(DashScopeChatModel.builder()
                        .apiKey(dashscopeKey)
                        .modelName("qwen-flash")
                        .build())
                .build();
    }

    public SubAgent route(Session session, String input) {
        // 发送消息
        Msg msg = Msg.builder()
                .textContent(input)
                .build();


    log.info("router: {},", input);

        Msg response = reActAgent.call(msg).block();

        log.info("router: {}, response: {}", input, response.getTextContent());

        if(response.getTextContent().equals("NoneAgent")) {
            session.incrementNoneCount();
            if(session.idleForLongTime()) {
                log.info("User has been idle for too long, stopping the session");
                session.setStop(true);
            }

            if(session.getSemaphore() != null)
                session.getSemaphore().release();
            return null;
        }

        return applicationContext.getBean(response.getTextContent(), SubAgent.class);
    }
}
