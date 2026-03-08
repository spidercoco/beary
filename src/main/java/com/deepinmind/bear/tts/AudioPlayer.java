package com.deepinmind.bear.tts;

import java.io.IOException;
import java.util.Base64;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;

import com.deepinmind.bear.session.Session;

import lombok.extern.slf4j.Slf4j;

// 实时PCM音频播放器类
@Slf4j
public class AudioPlayer {
    private int sampleRate;
    private SourceDataLine line;
    private AudioFormat audioFormat;
    private Thread decoderThread;
    private Thread playerThread;
    private Session session;
    private AtomicBoolean isStopping = new AtomicBoolean(false);
    private BlockingQueue<String> b64AudioBuffer = new LinkedBlockingQueue<>();
    private BlockingQueue<byte[]> rawAudioBuffer = new LinkedBlockingQueue<>();

    // 构造函数初始化音频格式和音频线路
    public AudioPlayer(Session session, int sampleRate) throws LineUnavailableException {
        this.session = session;
        this.sampleRate = sampleRate;
        this.audioFormat = new AudioFormat(this.sampleRate, 16, 1, true, false);
        DataLine.Info info = new DataLine.Info(SourceDataLine.class, audioFormat);
        line = (SourceDataLine) AudioSystem.getLine(info);
        line.open(audioFormat);
        line.start();

        decoderThread = new Thread(() -> {
            try {
                while (!session.isStop() && !isStopping.get()) {
                    // 使用 take() 阻塞等待，避免 Thread.sleep(100) 导致的卡顿
                    String b64Audio = b64AudioBuffer.poll(100, TimeUnit.MILLISECONDS);
                    if (b64Audio != null) {
                        byte[] rawAudio = Base64.getDecoder().decode(b64Audio);
                        rawAudioBuffer.put(rawAudio);
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "audio-decoder-thread");

        playerThread = new Thread(() -> {
            try {
                while (!session.isStop() && !isStopping.get()) {
                    // 使用 take() 阻塞等待数据
                    byte[] rawAudio = rawAudioBuffer.poll(100, TimeUnit.MILLISECONDS);
                    if (rawAudio != null) {
                        playChunk(rawAudio);
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "audio-player-thread");

        decoderThread.start();
        playerThread.start();
    }

    // 播放一个音频块
    private void playChunk(byte[] chunk) {
        if (chunk == null || chunk.length == 0) return;
        // line.write 会在内部缓冲区满时自动阻塞，不需要手动 sleep
        line.write(chunk, 0, chunk.length);
    }

    public void write(String b64Audio) {
        if (b64Audio != null) {
            b64AudioBuffer.add(b64Audio);
        }
    }

    public void cancel() {
        b64AudioBuffer.clear();
        rawAudioBuffer.clear();
    }

    public void waitForComplete() throws InterruptedException {
        // 等待所有待处理的数据被处理
        while ((!b64AudioBuffer.isEmpty() || !rawAudioBuffer.isEmpty()) && !session.isStop()) {
            Thread.sleep(10);
        }
        if (line != null && line.isOpen()) {
            line.drain();
        }
    }

    public void shutdown() throws InterruptedException {
        isStopping.set(true);
        if (decoderThread != null) decoderThread.interrupt();
        if (playerThread != null) playerThread.interrupt();
        
        if (decoderThread != null) decoderThread.join(500);
        if (playerThread != null) playerThread.join(500);

        if (line != null) {
            if (line.isRunning()) {
                line.stop();
            }
            line.close();
        }
    }
}
