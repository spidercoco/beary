package com.deepinmind.bear.agent;

import org.osgi.application.ApplicationContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.stereotype.Service;

import com.deepinmind.bear.core.Player;
import com.deepinmind.bear.service.CameraService;
import com.deepinmind.bear.session.Session;
import com.deepinmind.bear.skills.ControlDevice;
import com.deepinmind.bear.skills.SingSongSkill;
import com.deepinmind.bear.tools.DingTalk;
import com.deepinmind.bear.tools.KnxDeviceControl;
import com.deepinmind.bear.tts.Qwen3TTS;
import com.deepinmind.bear.utils.PromptLoader;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.Event;
import io.agentscope.core.message.Msg;
import io.agentscope.core.model.DashScopeChatModel;
import io.agentscope.core.skill.SkillBox;
import io.agentscope.core.tool.Toolkit;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service("MessageAgent")
@Slf4j
public class MessageAgent extends SubAgent {

    @Autowired
    private DingTalk dingTalk;


    @PostConstruct
    public void init() {
        
        // 准备工具
        Toolkit toolkit = new Toolkit();
        toolkit.registerTool(dingTalk);
        // toolkit.registerTool(cameraService);


        // 创建智能体
        reActAgent = ReActAgent.builder()
            .name("Jarvis")
            .sysPrompt(promptLoader.getPromptByType("message-agent"))
            .model(DashScopeChatModel.builder()
                .apiKey(dashscopeKey)
                .modelName("qwen3-max")
                .build())
            .toolkit(toolkit)
            .build();
    }


}
