package com.deepinmind.bear.agent;

import org.osgi.application.ApplicationContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.stereotype.Service;

import com.aliyun.oss.OSS;
import com.aliyun.oss.internal.signer.OSSSignerParams;
import com.deepinmind.bear.core.Player;
import com.deepinmind.bear.oss.OSSService;
import com.deepinmind.bear.session.Session;
import com.deepinmind.bear.skills.ControlDevice;
import com.deepinmind.bear.skills.SingSongSkill;
import com.deepinmind.bear.tools.DingTalk;
import com.deepinmind.bear.tools.KnxDeviceControl;
import com.deepinmind.bear.tools.Printer;
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

@Service("PrintAgent")
@Slf4j
public class PrintAgent extends SubAgent {

    @Autowired
    private Printer printer;

    /** OSS服务 */
    @Autowired
    private OSSService ossService;

    @PostConstruct
    public void init() {

        // 准备工具
        Toolkit toolkit = new Toolkit();
        toolkit.registerTool(printer);

        // 创建智能体
        reActAgent = ReActAgent.builder()
                .name("打印机助手")
                .sysPrompt(promptLoader.getPromptByType("print-agent"))
                .model(DashScopeChatModel.builder()
                        .apiKey(dashscopeKey)
                        .modelName("qwen3-max")
                        .build())
                .toolkit(toolkit)
                .build();
    }

    @Override
    public Flux<Event> streamCall(Session session, String input) {
        input = ossService.listPrintFiles() + input;
        Msg msg = Msg.builder()
                .textContent(input)
                .build();

        log.info("Sending message: {}", input);

        return reActAgent.stream(msg);
    }

}
