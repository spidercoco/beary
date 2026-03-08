package com.deepinmind.bear.agent;

import com.deepinmind.bear.utils.SkillLoader;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.model.DashScopeChatModel;
import io.agentscope.core.skill.AgentSkill;
import io.agentscope.core.tool.Toolkit;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * DemoAgent 示例
 * 通过 SkillLoader 从 Markdown 目录动态加载技能
 */
@Service("DemoAgent")
@Slf4j
public class DemoAgent extends SubAgent {

    @Autowired
    private SkillLoader skillLoader;

    @PostConstruct
    public void init() {
        try {
            // 1. 使用工具类加载指定名称的技能
            // 对应路径：src/main/resources/skills/weather/SKILL.md
            AgentSkill weatherSkill = skillLoader.createSkill("weather");

            // 2. 构建工具箱并注册技能
            Toolkit toolkit = new Toolkit();
            toolkit.registerTool(weatherSkill);

            // 3. 构建 ReActAgent
            reActAgent = ReActAgent.builder()
                    .name("Demo助手")
                    .sysPrompt("你是一个智能助手，可以帮助用户查询天气。")
                    .model(DashScopeChatModel.builder()
                            .apiKey(dashscopeKey)
                            .modelName("qwen-plus")
                            .build())
                    .toolkit(toolkit)
                    .build();

            log.info("DemoAgent initialized with weather skill loaded from Markdown.");

        } catch (Exception e) {
            log.error("Failed to initialize DemoAgent: {}", e.getMessage(), e);
        }
    }
}
