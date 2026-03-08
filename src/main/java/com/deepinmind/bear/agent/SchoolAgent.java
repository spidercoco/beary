package com.deepinmind.bear.agent;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.deepinmind.bear.session.Session;
import com.deepinmind.bear.service.HomeworkService;
import com.deepinmind.bear.utils.PromptLoader;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.Event;
import io.agentscope.core.message.Msg;
import io.agentscope.core.model.DashScopeChatModel;
import io.agentscope.core.tool.Toolkit;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

@Service("SchoolAgent")
@Slf4j
public class SchoolAgent extends SubAgent {

    private final PromptLoader promptLoader;
    private final String dashscopeKey;

    @Autowired
    private HomeworkService homeworkService;

    @Autowired
    public SchoolAgent(PromptLoader promptLoader, @Value("${dashscope.key}") String dashscopeKey) {
        this.promptLoader = promptLoader;
        this.dashscopeKey = dashscopeKey;
        
        Toolkit toolkit = new Toolkit();

        reActAgent = ReActAgent.builder()
            .name("Jarvis")
            .sysPrompt(promptLoader.getPromptByType("school"))
            .model(DashScopeChatModel.builder()
                .apiKey(dashscopeKey)
                .modelName("qwen-plus")
                .build())
            .toolkit(toolkit)
            .build();
    }

    @Override
    public Flux<Event> streamCall(Session session, String input) {
        // 1. 获取真实作业内容作为上下文
        String realHomework = homeworkService.getTodayHomework();
        
        // 2. 将背景知识和用户问题组合，确保模型能够回答真实的作业内容
        String combinedInput = String.format("【一周的课程安排】\\n%s\\n\\n【当天的作业信息】\n%s\n\n【用户指令】\n%s", realHomework, input);
        
        Msg msg = Msg.builder()
            .textContent(combinedInput)
            .build();

        log.info("HomeworkAgent processing with real data context.");

        return reActAgent.stream(msg);
    }
}
