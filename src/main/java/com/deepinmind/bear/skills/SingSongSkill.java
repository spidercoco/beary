package com.deepinmind.bear.skills;

import io.agentscope.core.skill.AgentSkill;
import io.agentscope.core.skill.util.SkillUtil;
import org.springframework.stereotype.Component;

/**
 * Sing Song Skill - Allows the agent to sing songs when requested
 */
@Component
public class SingSongSkill {

    public AgentSkill createSkill() {
        return AgentSkill.builder()
            .name("sing_song")
            .description("Sing a song when the user requests it")
            .skillContent(loadSkillContent())
            .addResource("references/songs.md", loadSongsReference())
            .source("custom")
            .build();
    }

    private String loadSkillContent() {
        return """
                # Song Performance Skill
                
                ## Purpose
                This skill enables the assistant to sing songs when requested by the user.
                
                ## When to Use
                - When user asks to sing a song
                - When user says "唱首歌" or "sing a song"
                - When user requests a specific song performance
                
                ## Response Guidelines
                - Respond with a cheerful, singing tone
                - Include musical elements in your response
                - Keep the song appropriate for all audiences
                - You can sing in Chinese or English based on user preference
                """;
    }

    private String loadSongsReference() {
        return """
                # 歌曲列表 (Song List)
                
                ## 经典儿歌 (Classic Children's Songs)
                - 小星星 (Twinkle Twinkle Little Star)
                - 两只老虎 (Two Tigers)
                - 找朋友 (Looking for Friends)
                
                ## 流行歌曲 (Popular Songs)
                - 月亮代表我的心 (The Moon Represents My Heart)
                - 甜蜜蜜 (Sweet Honey)
                
                ## 英文歌曲 (English Songs)
                - Happy Birthday
                - Jingle Bells
                - You Are My Sunshine
                
                ## 使用说明 (Usage Instructions)
                - 根据用户请求选择合适的歌曲
                - 可以询问用户想听什么类型的歌
                - 保持积极向上的歌曲内容
                """;
    }
}