package com.deepinmind.bear.utils;

import io.agentscope.core.skill.AgentSkill;
import io.agentscope.core.skill.util.SkillUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@Component
@Slf4j
public class SkillLoader {

    private final PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();

    /**
     * 根据技能名称创建 AgentSkill
     * 自动从 classpath:/skills/{skillName}/ 目录下加载
     * - 主文件：SKILL.md
     * - 资源文件：该目录下除 SKILL.md 以外的所有文件
     */
    public AgentSkill createSkill(String skillName) throws IOException {
        String baseDir = "skills/" + skillName + "/";
        String mainFile = baseDir + "SKILL.md";
        
        log.info("Loading skill: {} from {}", skillName, baseDir);

        // 1. 加载主 Markdown 内容
        Resource mainResource = resolver.getResource("classpath:" + mainFile);
        if (!mainResource.exists()) {
            throw new IOException("Main skill file not found: " + mainFile);
        }
        String skillMd = StreamUtils.copyToString(mainResource.getInputStream(), StandardCharsets.UTF_8);

        // 2. 遍历加载目录下所有辅助资源
        Map<String, String> resources = new HashMap<>();
        Resource[] allResources = resolver.getResources("classpath:" + baseDir + "**/*");
        
        for (Resource res : allResources) {
            String path = res.getURL().getPath();
            // 排除目录本身和主 SKILL.md 文件
            if (!res.isReadable() || path.endsWith("/") || path.endsWith("SKILL.md")) {
                continue;
            }

            // 获取相对于技能目录的路径作为资源 Key
            String relativePath = getRelativePath(baseDir, res);
            String content = StreamUtils.copyToString(res.getInputStream(), StandardCharsets.UTF_8);
            resources.put(relativePath, content);
            log.debug("Loaded skill resource: {}", relativePath);
        }

        // 3. 使用 SkillUtil 创建技能
        return SkillUtil.createFrom(skillMd, resources);
    }

    private String getRelativePath(String baseDir, Resource resource) throws IOException {
        String description = resource.getDescription();
        // 处理 classpath 资源的特殊路径描述
        int index = description.indexOf(baseDir);
        if (index != -1) {
            return description.substring(index + baseDir.length()).replace("]", "");
        }
        return resource.getFilename();
    }
}
