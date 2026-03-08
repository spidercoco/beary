package com.deepinmind.bear.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

/**
 * PromptLoader使用示例和测试类
 */
@Component
@Slf4j
public class PromptLoaderTest {

    @Autowired
    private PromptLoader promptLoader;

    @PostConstruct
    public void testPromptLoader() {
        log.info("=== PromptLoader 测试 ===");
        
        // 测试加载React Agent prompt
        String reactPrompt = promptLoader.getPromptByType("react-agent");
        log.info("React Agent Prompt 长度: {} 字符", reactPrompt.length());
        log.info("React Agent Prompt 预览: {}", reactPrompt.substring(0, Math.min(200, reactPrompt.length())));
        
        // 测试加载特定类型的prompt
        String voicePrompt = promptLoader.getPromptByType("voice-assistant");
        log.info("Voice Assistant Prompt 长度: {} 字符", voicePrompt.length());
        
        String homeAutoPrompt = promptLoader.getPromptByType("home-automation");
        log.info("Home Automation Prompt 长度: {} 字符", homeAutoPrompt.length());
        
        // 测试直接加载文件
        String directLoad = promptLoader.getString("prompts/react-agent-prompt.txt");
        log.info("直接加载文件长度: {} 字符", directLoad.length());
        
        log.info("=== PromptLoader 测试完成 ===");
    }
}