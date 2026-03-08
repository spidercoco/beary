package com.deepinmind.bear.session;

import com.alibaba.dashscope.audio.ttsv2.SpeechSynthesizer;
import lombok.Data;

import java.io.ByteArrayOutputStream;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;

@Data
public class Session {

    private Long id = System.currentTimeMillis();

    private java.util.concurrent.Future<String> user;

    private Semaphore semaphore;

    private SpeechSynthesizer synthesizer;

    private boolean stop = false;

    private boolean answering = false;

    private int noneCount = 0;
    
    // 使用线程安全的流式缓冲区实时追加音频
    private final ByteArrayOutputStream audioStream = new ByteArrayOutputStream();

    /**
     * 追加音频数据
     */
    public synchronized void appendAudio(byte[] data, int len) {
        // 限制缓存大小，防止内存溢出（约 10秒 16kHz PCM 数据 = 320KB）
        if (audioStream.size() < 320000) {
            audioStream.write(data, 0, len);
        }
    }

    /**
     * 获取当前已缓存的所有音频
     */
    public synchronized byte[] getAudioCache() {
        return audioStream.toByteArray();
    }

    public void incrementNoneCount() {
        this.noneCount++;
    }

    public boolean idleForLongTime() {
        return this.noneCount > 3;
    }
    
    public void resume() {
        if (this.semaphore != null) {
            this.semaphore.release();
        }
    }
}
