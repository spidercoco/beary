package com.deepinmind.bear.agent;

import org.osgi.application.ApplicationContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.stereotype.Service;

import com.deepinmind.bear.core.Player;
import com.deepinmind.bear.session.Session;
import com.deepinmind.bear.skills.ControlDevice;
import com.deepinmind.bear.skills.SingSongSkill;
import com.deepinmind.bear.tools.KnxDeviceControl;
import com.deepinmind.bear.tts.Qwen3TTS;
import com.deepinmind.bear.utils.PromptLoader;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.Event;
import io.agentscope.core.memory.InMemoryMemory;
import io.agentscope.core.memory.autocontext.AutoContextMemory;
import io.agentscope.core.message.Msg;
import io.agentscope.core.model.DashScopeChatModel;
import io.agentscope.core.skill.SkillBox;
import io.agentscope.core.tool.Toolkit;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service("DeviceAgent")
@Slf4j
public class DeviceAgent extends SubAgent {

    @Autowired
    private KnxDeviceControl knxDeviceControl;


    @PostConstruct
    public void init() {
        
        // 准备工具
        Toolkit toolkit = new Toolkit();
        toolkit.registerTool(knxDeviceControl);

        DashScopeChatModel model = DashScopeChatModel.builder()
                .apiKey(dashscopeKey)
                .modelName("qwen-plus")
                // .enableThinking(false)
                .build();
        // 创建内存
        AutoContextMemory memory = new AutoContextMemory(config, model);

        // 创建智能体
        reActAgent = ReActAgent.builder()
            .name("Jarvis")
            .sysPrompt(promptLoader.getPromptByType("control-device"))
            .model(model)
            .toolkit(toolkit)
            .memory(memory)
            .build();
    }

    public static void main(String[] args) {
                // 准备工具
        Toolkit toolkit = new Toolkit();

        toolkit.registerTool(new KnxDeviceControl());

// skillBox.registerSkill(new ControlDevice().createSkill());

        // 创建智能体
        ReActAgent reActAgent = ReActAgent.builder()
            .name("Jarvis")
           .sysPrompt(new PromptLoader().getPromptByType("control-device"))
                        // .sysPrompt("你是一个名为 Jarvis 的助手")

            .model(DashScopeChatModel.builder()
                .apiKey("sk-583169a373194f9ca6ef9286c7ca0252")
                .modelName("qwen3-max")
                .build())
            .toolkit(toolkit)
            .memory(new InMemoryMemory())
            .build();

        // 发送消息
        Msg msg = Msg.builder()
            .textContent("你好！Jarvis,关灯")
            .build();

        Msg response = reActAgent.call(msg).block();
        System.out.println(response.getTextContent());

        msg = Msg.builder()
            .textContent("主卧的")
            .build();

        response = reActAgent.call(msg).block();
        System.out.println(response.getTextContent());
    }

}
