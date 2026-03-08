package com.deepinmind.bear.agent;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import com.deepinmind.bear.session.Session;
import com.deepinmind.bear.utils.PromptLoader;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.Event;
import io.agentscope.core.memory.autocontext.AutoContextConfig;
import io.agentscope.core.memory.autocontext.AutoContextMemory;
import io.agentscope.core.message.Msg;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
public abstract class SubAgent {

    @Autowired
    protected PromptLoader promptLoader;

    @Value("${dashscope.key}")
    protected String dashscopeKey;

    // 配置
    protected AutoContextConfig config = AutoContextConfig.builder()
        .msgThreshold(30)
        .lastKeep(10)
        .tokenRatio(0.3)
        .build();



    protected ReActAgent reActAgent;
    public Flux<Event> streamCall(Session session, String input) {
                Msg msg = Msg.builder()
            .textContent(input)
            .build();

        log.info("Sending message: {}", input);

        return reActAgent.stream(msg);
    }

        public Mono<Msg> call(Session session, String input) {
                Msg msg = Msg.builder()
            .textContent(input)
            .build();

        log.info("Sending message: {}", input);

        return reActAgent.call(msg);
    }
}
