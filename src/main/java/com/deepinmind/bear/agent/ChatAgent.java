package com.deepinmind.bear.agent;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.deepinmind.bear.service.AlarmService;
import com.deepinmind.bear.service.CameraService;
import com.deepinmind.bear.service.MessageService;
import com.deepinmind.bear.session.Session;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import com.deepinmind.bear.skills.ControlDevice;
import com.deepinmind.bear.tools.KnxDeviceControl;
import com.deepinmind.bear.utils.PromptLoader;
import com.deepinmind.bear.utils.SkillLoader;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.model.DashScopeChatModel;
import io.agentscope.core.skill.AgentSkill;
import io.agentscope.core.skill.SkillBox;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.core.tool.mcp.McpClientBuilder;
import io.agentscope.core.tool.mcp.McpClientWrapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

@Service("ChatAgent")
@Slf4j
public class ChatAgent extends SubAgent {


    @Autowired
    private AlarmService alarmService;

    @Autowired
    private MessageService messageService;

    @Autowired
    private SkillLoader skillLoader;

    @PostConstruct
    public void init() throws IOException {
        // 准备工具
        Toolkit toolkit = new Toolkit();
        toolkit.registerTool(alarmService);
        toolkit.registerTool(messageService);

        AgentSkill timetableSkill = skillLoader.createSkill("timetable");
        AgentSkill voicemailSkill = skillLoader.createSkill("voicemail");

SkillBox skillBox = new SkillBox(toolkit);
skillBox.registerSkill(timetableSkill);
skillBox.registerSkill(voicemailSkill);

        // try {
        // McpClientWrapper client = McpClientBuilder.create("mcp")
        // .sseTransport("https://dashscope.aliyuncs.com/api/v1/mcps/WebSearch/sse")
        // .header("Authorization", "Bearer " + dashscopeKey)
        // .header("X-Client-Version", "1.0")
        // .header("X-Custom-Header", "value")
        // .buildAsync()
        // .block();
        // toolkit.registerMcpClient(client).block();
        // } catch (Exception e) {
        //     log.error("Failed to register MCP client", e);
        // }


        // 创建智能体
        reActAgent = ReActAgent.builder()
                .name("Jarvis")
                .sysPrompt(String.format(promptLoader.getPromptByType("chat-agent"),
                        LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))))
                .model(DashScopeChatModel.builder()
                        .apiKey(dashscopeKey)
                        .modelName("qwen3-max")
                        .build())
                .toolkit(toolkit)
                .skillBox(skillBox)
                .build();
    }


}
