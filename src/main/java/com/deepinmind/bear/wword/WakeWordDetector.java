package com.deepinmind.bear.wword;

import com.deepinmind.bear.asr.Qwen3FlashASR;
import com.deepinmind.bear.session.Session;
import com.deepinmind.bear.utils.MicManager;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import javax.sound.sampled.*;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
public class WakeWordDetector {

    @Value("${wakeword.engine:onnx}") // 默认使用 onnx，可选 picovoice
    private String engineType;

    @Value("${picovoice.token:}")
    private String accessKey;

    private WakeWordEngine engine;
    private TargetDataLine micLine;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService notificationExecutor = Executors.newSingleThreadExecutor();

    @Autowired
    private Qwen3FlashASR asr;

    @Autowired
    AudioFrameHandler handler;

    private byte[] notificationAudioBytes;
    private static final String NOTIFICATION_AUDIO = "audio.wav";

    @PostConstruct
    public void start() {
        try {
            // 1. 初始化所选引擎
            if ("picovoice".equalsIgnoreCase(engineType)) {
                engine = new PorcupineEngine(accessKey);
            } else {
                engine = new SherpaOnnxEngine();
            }
            engine.init();

            // 2. 初始化音频采集
            AudioFormat format = new AudioFormat(16000f, 16, 1, true, false);
            micLine = (TargetDataLine) MicManager.openMic("1080", format);
            micLine.start();

            loadNotificationSound();
            handler.onStart();

            running.set(true);
            ioExecutor.submit(this::runDetectionLoop);
            log.info("Wake Word Detector started with engine: {}", engineType);
        } catch (Exception e) {
            log.error("Failed to start Wake Word Detector: {}", e.getMessage());
        }
    }

    private void runDetectionLoop() {
        // Porcupine 默认帧长是 512 samples (1024 bytes)
        // Sherpa-ONNX 较灵活，我们统一使用 512 以保持逻辑简洁
        int samplesPerFrame = 512;
        ByteBuffer captureBuffer = ByteBuffer.allocate(samplesPerFrame * 2);
        captureBuffer.order(ByteOrder.LITTLE_ENDIAN);
        short[] shortBuffer = new short[samplesPerFrame];

        try {
            while (running.get()) {
                int numBytesRead = micLine.read(captureBuffer.array(), 0, captureBuffer.capacity());
                if (numBytesRead < captureBuffer.capacity()) continue;

                captureBuffer.asShortBuffer().get(shortBuffer);
                captureBuffer.clear();

                if (engine.process(shortBuffer)) {
                    log.info("!!! Wake word detected !!!");
                    final Session session = asr.notifyVoice();
                    notificationExecutor.submit(() -> playNotificationSound(session));
                }
            }
        } catch (Exception e) {
            log.error("Detection loop error", e);
        }
    }

    @PreDestroy
    public void stop() {
        running.set(false);
        ioExecutor.shutdownNow();
        notificationExecutor.shutdownNow();
        if (engine != null) engine.release();
        if (micLine != null) { micLine.stop(); micLine.close(); }
        log.info("Wake Word Detector stopped.");
    }

    // --- 通知音逻辑 (保持原样) ---

    private void playNotificationSound(Session session) {
        if (notificationAudioBytes == null || notificationAudioBytes.length == 0) {
            session.resume(); return;
        }
        try (AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(new ByteArrayInputStream(notificationAudioBytes))) {
            CountDownLatch latch = new CountDownLatch(1);
            Clip clip = AudioSystem.getClip();
            clip.open(audioInputStream);
            clip.addLineListener(event -> {
                if (event.getType() == LineEvent.Type.STOP) {
                    latch.countDown();
                    clip.close();
                    session.resume();
                }
            });
            clip.start();
            latch.await();
        } catch (Exception e) {
            log.warn("Failed to play notification sound", e);
            session.resume();
        }
    }

    private void loadNotificationSound() {
        try {
            ClassPathResource resource = new ClassPathResource(NOTIFICATION_AUDIO);
            if (!resource.exists()) return;
            try (InputStream is = resource.getInputStream(); ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[4096];
                int len;
                while ((len = is.read(buffer)) != -1) baos.write(buffer, 0, len);
                notificationAudioBytes = baos.toByteArray();
            }
        } catch (Exception e) { log.warn("Failed to load notification audio", e); }
    }
}
