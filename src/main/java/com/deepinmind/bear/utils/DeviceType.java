package com.deepinmind.bear.utils;

/**
 * KNX smart home device type enumeration
 */
public enum DeviceType {

    LIGHT("灯", "light"),
    CURTAIN("窗帘", "curtain"),
    AIR_CONDITIONER("空调", "airConditioner"),
    FAN("风机", "fan"),
    SWITCH("开关", "switch"),
    SENSOR("传感器", "sensor"),
    BUTTON("按钮", "button"),
    MODE("模式", "mode");

    private final String chineseName;
    private final String code;

    DeviceType(String chineseName, String code) {
        this.chineseName = chineseName;
        this.code = code;
    }

    public String getChineseName() {
        return chineseName;
    }

    public String getCode() {
        return code;
    }

    public static DeviceType fromChineseName(String name) {
        for (DeviceType type : values()) {
            if (type.chineseName.equals(name)) {
                return type;
            }
        }
        return null;
    }

    public static DeviceType fromCode(String code) {
        for (DeviceType type : values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        return null;
    }
}
