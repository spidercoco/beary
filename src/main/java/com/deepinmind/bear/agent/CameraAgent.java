package com.deepinmind.bear.agent;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.deepinmind.bear.session.Session;
import com.deepinmind.bear.tools.KnxDeviceControl;
import com.deepinmind.bear.utils.PromptLoader;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.message.Base64Source;
import io.agentscope.core.message.ImageBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.model.DashScopeChatModel;
import io.agentscope.core.skill.SkillBox;
import io.agentscope.core.tool.Toolkit;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Service("CameraAgent")
@Slf4j
public class CameraAgent extends SubAgent {

    private final PromptLoader promptLoader;

    private final String dashscopeKey;

    @Autowired
    public CameraAgent(PromptLoader promptLoader, @Value("${dashscope.key}") String dashscopeKey) {
        this.promptLoader = promptLoader;
        this.dashscopeKey = dashscopeKey;

        // 创建智能体
        reActAgent = ReActAgent.builder()
                .name("Jarvis")
                .sysPrompt(promptLoader.getPromptByType("camera"))
                .model(DashScopeChatModel.builder()
                        .apiKey(dashscopeKey)
                        .modelName("qwen3-vl-plus")
                        .build())
                .build();
    }

    public Mono<Msg> call(Session session, String input, String image) {
        Msg msg = Msg.builder()
                .content(List.of(
                        // TextBlock.builder().text(input).build(),
                        ImageBlock.builder().source(new Base64Source("image/jpeg", image)).build()))
                .build();

        log.info("Sending message: {}", input);

        return reActAgent.call(msg);
    }

}
