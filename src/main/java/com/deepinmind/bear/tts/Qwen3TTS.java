package com.deepinmind.bear.tts;

import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.alibaba.dashscope.audio.qwen_tts_realtime.QwenTtsRealtime;
import com.alibaba.dashscope.audio.qwen_tts_realtime.QwenTtsRealtimeAudioFormat;
import com.alibaba.dashscope.audio.qwen_tts_realtime.QwenTtsRealtimeCallback;
import com.alibaba.dashscope.audio.qwen_tts_realtime.QwenTtsRealtimeConfig;
import com.alibaba.dashscope.audio.qwen_tts_realtime.QwenTtsRealtimeParam;
import com.alibaba.dashscope.exception.NoApiKeyException;
import com.deepinmind.bear.config.VoiceConfigInitializer;
import com.deepinmind.bear.core.AudioService;
import com.deepinmind.bear.core.Player;
import com.deepinmind.bear.session.Session;
import com.google.gson.JsonObject;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class Qwen3TTS implements AudioService {

    @Value("${dashscope.key}")
    private String dashscopeKey;

    @Autowired
    VoiceConfigInitializer voiceConfigInitializer;

    AudioFormat outFormat = new AudioFormat(
            24000, // 采样率: 22.05kHz
            16, // 采样大小: 16位
            1, // 声道: 单声道
            true, // 有符号
            false // 小端序
    );
    DataLine.Info outLine = new DataLine.Info(SourceDataLine.class, outFormat);

    // 音频播放相关
    private SourceDataLine audioOutputLine = (SourceDataLine) AudioSystem.getLine(outLine);

    public Qwen3TTS() throws LineUnavailableException {
        audioOutputLine.open(outFormat);
        audioOutputLine.start();
    }

    public void play(String text) {
        try {
            Player player = getNewPlayer(new Session());
            player.play(text);
            player.stop();
        } catch (LineUnavailableException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void play(String text, String role) {
        try {
            Player player = getNewPlayer(new Session(), role);
            player.play(text);
            player.stop();
        } catch (LineUnavailableException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    public Player getNewPlayer(Session session) throws LineUnavailableException, InterruptedException {
        return getNewPlayer(session, "mom");
    }

    public Player getNewPlayer(Session session, String role) throws LineUnavailableException, InterruptedException {
        // log.info("Creating new player");
        QwenTtsRealtimeParam param = QwenTtsRealtimeParam.builder()
                .model("qwen3-tts-vc-realtime-2026-01-15")
                // .model("qwen3-tts-flash-realtime")
                // 以下为新加坡地域url，若使用北京地域的模型，需将url替换为：wss://dashscope.aliyuncs.com/api-ws/v1/realtime
                .url("wss://dashscope.aliyuncs.com/api-ws/v1/realtime")
                // 新加坡和北京地域的API Key不同。获取API
                // Key：https://www.alibabacloud.com/help/zh/model-studio/get-api-key
                .apikey(dashscopeKey)
                .build();
        AtomicReference<CountDownLatch> completeLatch = new AtomicReference<>(new CountDownLatch(1));
        final AtomicReference<QwenTtsRealtime> qwenTtsRef = new AtomicReference<>(null);

        AudioPlayer audioPlayer = new AudioPlayer(session, 24000);

        QwenTtsRealtime qwenTtsRealtime = new QwenTtsRealtime(param, new QwenTtsRealtimeCallback() {
            @Override
            public void onOpen() {
                // 连接建立时的处理
            }

            @Override
            public void onEvent(JsonObject message) {
                String type = message.get("type").getAsString();
                switch (type) {
                    case "session.created":
                        // 会话创建时的处理
                        break;
                    case "response.audio.delta":
                        // System.out.println(" esponse.audio.delta");
                        String recvAudioB64 = message.get("delta").getAsString();
                        if (session.isStop()) {
                            log.info("session is stopped, break");
                            break;
                        } else {
                            log.debug("recv audio: ");
                        }
                        // 实时播放音频
                        audioPlayer.write(recvAudioB64);
                        break;
                    case "response.done":
                        // 响应完成时的处理
                        break;
                    case "session.finished":
                        // 会话结束时的处理
                        completeLatch.get().countDown();
                        System.out.println(" 收到Complete，语音合成结束");
                        // audioOutputLine.drain();
                        try {
                            Thread.sleep(150l);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        // session.getSemaphore().release();
                    case "error":
                        // 错误处理
                        log.error("Error: {}", message);
                        completeLatch.get().countDown();
                        break;
                    default:
                        break;
                }
            }

            @Override
            public void onClose(int code, String reason) {
                // 连接关闭时的处理
                System.out.println("连接关闭，代码：" + code + ", 原因：" + reason);
            }
        });
        qwenTtsRef.set(qwenTtsRealtime);
        try {
            qwenTtsRealtime.connect();
        } catch (NoApiKeyException | InterruptedException e) {
            throw new RuntimeException(e);
        }

        String voice = voiceConfigInitializer.getVoiceByRole(role);
        System.out.println("Using voice: " + voice);
        QwenTtsRealtimeConfig config = QwenTtsRealtimeConfig.builder()
                .voice(voice != null ? voice : "qwen-tts-vc-bearylovedad-voice-20260226161845534-3bd2")
                // .voice("Cherry")
                .languageType("Chinese")
                .responseFormat(QwenTtsRealtimeAudioFormat.PCM_24000HZ_MONO_16BIT)
                .mode("server_commit")
                .build();
        qwenTtsRealtime.updateSession(config);

        log.info("Player created");

        return new Player() {

            @Override
            public void play(String text) {
                qwenTtsRealtime.appendText(text);
            }

            @Override
            public void stop() {

                try {
                    log.info("start to stop player");
                    Thread.sleep(200);
                    qwenTtsRealtime.finish();
                    completeLatch.get().await();
                    qwenTtsRealtime.close();

                    // 等待音频播放完成并关闭播放器
                    audioPlayer.waitForComplete();
                    audioPlayer.shutdown();
                    Thread.sleep(100);
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    log.info("Player stopped");
                    session.setAnswering(false);
                    if (session.getSemaphore() != null) {
                        session.getSemaphore().release();
                    }
                }
            }
        };
    }

}
