package com.deepinmind.bear.wword;

public interface WakeWordEngine {
    /**
     * 初始化引擎
     */
    void init() throws Exception;

    /**
     * 处理一段音频采样
     * @param samples PCM16 格式的音频采样数据
     * @return 如果检测到唤醒词返回 true，否则返回 false
     */
    boolean process(short[] samples);

    /**
     * 释放资源
     */
    void release();
}
