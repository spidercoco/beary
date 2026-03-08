package com.deepinmind.bear.skills;

import io.agentscope.core.skill.AgentSkill;
import io.agentscope.core.skill.util.SkillUtil;
import org.springframework.stereotype.Component;

/**
 * Sing Song Skill - Allows the agent to sing songs when requested
 */
@Component
public class ControlDevice {

    public AgentSkill createSkill() {
        return AgentSkill.builder()
            .name("control_device")
            .description("控制家里的智能家电，如灯，窗帘，空调等")
            .skillContent(loadSkillContent())
            .addResource("references/devices.md", loadDevicesReference())
            .source("custom")
            .build();
    }

    private String loadSkillContent() {
        return """
                ## 使用方法
                一、先确认设备名是否明确，必须要在设备列表中，不明确可以追问。
                二、一定要确认清楚设备名在设备列表后，再调用对应的工具来操作对应的设备。

                ## 设备列表
                主卧：主卧筒灯、主卧灯带灯、主卧主灯、主卧空调、主卧布帘、主卧纱帘
主卫（主卧内）：主卫筒灯、主卫灯带灯
客厅：客厅筒灯、客厅灯带灯、客厅主灯、客厅空调、客厅布帘、客厅纱帘
餐厅：餐厅主灯、餐厅筒灯、餐厅空调
次卧：次卧主灯、次卧筒灯、次卧灯带灯、次卧空调、次卧纱帘
玄关：玄关筒灯、玄关灯带
儿童房：儿童房空调
多功能房：多功能房空调

所有设备仅支持两个操作：开 / 关

                """;
    }

    private String loadDevicesReference() {
        return """
                主卧：主卧筒灯、主卧灯带灯、主卧主灯、主卧空调、主卧布帘、主卧纱帘
主卫（主卧内）：主卫筒灯、主卫灯带灯
客厅：客厅筒灯、客厅灯带灯、客厅主灯、客厅空调、客厅布帘、客厅纱帘
餐厅：餐厅主灯、餐厅筒灯、餐厅空调
次卧：次卧主灯、次卧筒灯、次卧灯带灯、次卧空调、次卧纱帘
玄关：玄关筒灯、玄关灯带
儿童房：儿童房空调
多功能房：多功能房空调

所有设备仅支持两个操作：开 / 关
                """;
    }
}