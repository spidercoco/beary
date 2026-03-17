package com.deepinmind.bear.agent;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.deepinmind.bear.service.HomeworkService;
import com.deepinmind.bear.tools.KnxDeviceControl;
import com.deepinmind.bear.session.Session;
import com.deepinmind.bear.utils.PromptLoader;
import com.deepinmind.bear.utils.SkillLoader;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.message.Base64Source;
import io.agentscope.core.message.ImageBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.model.DashScopeChatModel;
import io.agentscope.core.skill.AgentSkill;
import io.agentscope.core.skill.SkillBox;
import io.agentscope.core.tool.AgentTool;
import io.agentscope.core.tool.Toolkit;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Service("BotAgent")
@Slf4j
public class BotAgent extends SubAgent {

        private final ReActAgent visionAgent;
        private final ReActAgent textAgent;

        @Autowired
        public BotAgent(PromptLoader promptLoader,
                        HomeworkService homeworkService,
                        KnxDeviceControl knxDeviceControl,
                        SkillLoader skillLoader,
                        @Value("${dashscope.key}") String dashscopeKey) throws IOException {

                // 1. Vision Agent (不配置工具和技能)
                visionAgent = ReActAgent.builder()
                                .name("BotVision")
                                .sysPrompt(promptLoader.getPromptByType("bot-agent"))
                                .model(DashScopeChatModel.builder()
                                                .apiKey(dashscopeKey)
                                                .modelName("qwen3-vl-plus")
                                                .enableThinking(true)
                                                .build())
                                .build();

                // 2. Text Agent (配置工具和技能)
                Toolkit textToolkit = new Toolkit();
                textToolkit.registerTool(homeworkService);
                textToolkit.registerTool(knxDeviceControl);

                AgentSkill timetableSkill = skillLoader.createSkill("timetable");
                AgentSkill deviceControlSkill = skillLoader.createSkill("device_control");

                SkillBox textSkillBox = new SkillBox(textToolkit);
                textSkillBox.registerSkill(timetableSkill);
                textSkillBox.registerSkill(deviceControlSkill);

                textAgent = ReActAgent.builder()
                                .name("BotText")
                                .sysPrompt(String.format(promptLoader.getPromptByType("bot-agent"),
                                                LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))))
                                .model(DashScopeChatModel.builder()
                                                .apiKey(dashscopeKey)
                                                .modelName("qwen3-max")
                                                .build())
                                .toolkit(textToolkit)
                                .skillBox(textSkillBox)
                                .build();
        }

        public Mono<Msg> call(Session session, String input, String imageBase64) {
                if (imageBase64 != null && !imageBase64.isEmpty()) {
                        Msg msg = Msg.builder()
                                        .content(List.of(
                                                        TextBlock.builder().text(input).build(),
                                                        ImageBlock.builder()
                                                                        .source(new Base64Source("image/jpeg",
                                                                                        imageBase64))
                                                                        .build()))
                                        .build();
                        log.info("BotAgent (Vision) calling with input: {}", input);
                        return visionAgent.call(msg);
                } else {
                        Msg msg = Msg.builder()
                                        .textContent(input)
                                        .build();
                        log.info("BotAgent (Text) calling with input: {}", input);
                        return textAgent.call(msg);
                }
        }

        @Override
        public Mono<Msg> call(Session session, String input) {
                return call(session, input, null);
        }
}
