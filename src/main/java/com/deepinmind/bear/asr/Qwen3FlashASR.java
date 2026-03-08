package com.deepinmind.bear.asr;

import com.alibaba.dashscope.audio.omni.*;
import com.alibaba.dashscope.exception.NoApiKeyException;
import com.deepinmind.bear.agent.BearyAgent;
import com.deepinmind.bear.service.SpeakerService;
import com.deepinmind.bear.session.Session;
import com.deepinmind.bear.utils.MicManager;
import com.google.gson.JsonObject;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.TargetDataLine;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Base64;
import java.util.Collections;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Service
public class Qwen3FlashASR {

    @Value("${dashscope.key}")
    private String dashscopeKey;

    @Autowired
    BearyAgent assistantAgent;

    @Autowired
    private SpeakerService speakerService;

    OmniRealtimeParam param;
    OmniRealtimeTranscriptionParam transcriptionParam;
    OmniRealtimeConfig config;
    AudioFormat audioFormat = new AudioFormat(16000f, 16, 1, true, false);

    private TargetDataLine targetDataLine;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    OmniRealtimeConversation conversation;
    Session session;

    @PostConstruct
    public void init() throws LineUnavailableException {
        targetDataLine = MicManager.openMic("1080", audioFormat);

        param = OmniRealtimeParam.builder()
                .model("qwen3-asr-flash-realtime")
                .url("wss://dashscope.aliyuncs.com/api-ws/v1/realtime")
                .apikey(dashscopeKey)
                .build();

        transcriptionParam = new OmniRealtimeTranscriptionParam();
        transcriptionParam.setLanguage("zh");
        transcriptionParam.setInputAudioFormat("pcm");
        transcriptionParam.setInputSampleRate(16000);

        config = OmniRealtimeConfig.builder()
                .modalities(Collections.singletonList(OmniRealtimeModality.TEXT))
                .transcriptionConfig(transcriptionParam)
                .turnDetectionThreshold(0.5f)
                .turnDetectionSilenceDurationMs(600)
                .build();
    }

    public Session notifyVoice() {
        if (session != null) {
            session.setStop(true);
            if (session.getSemaphore() != null && session.getSemaphore().availablePermits() == 0) {
                session.getSemaphore().release();
            }
        }

        log.info("notifyVoice called");
        session = new Session();
        session.setSemaphore(new Semaphore(1));
        session.getSemaphore().acquireUninterruptibly();

        executor.submit(() -> {
            try {
                reconize(session);
            } catch (Exception e) {
                log.error("ASR task failed", e);
            }
        });

        return session;
    }

    public void reconize(Session session) throws IOException {
        log.info("new session {}", session.getId());

        final AtomicReference<OmniRealtimeConversation> conversationRef = new AtomicReference<>(null);
        conversation = new OmniRealtimeConversation(param, new OmniRealtimeCallback() {
            @Override
            public void onOpen() { log.info("connection opened"); }

            @Override
            public void onEvent(JsonObject message) {
                String type = message.get("type").getAsString();
                switch (type) {
                    case "session.created":
                        log.info("start session: " + message.get("session").getAsJsonObject().get("id").getAsString());
                        break;
                    case "conversation.item.input_audio_transcription.completed":
                        String transcript = message.get("transcript").getAsString();
                        log.info("transcription: " + transcript);

                        if (countChineseCharacters(transcript) <= 2) {
                            log.info("Ignoring short transcription: " + transcript);
                            break;
                        }

                        if (!session.isAnswering()) {
                            session.setAnswering(true);
                            
                            // --- 异步触发说话人识别 (利用已实时缓存的数据) ---
                            final byte[] capturedAudio = session.getAudioCache();
                            if (capturedAudio != null && capturedAudio.length > 0) {
                                float[] samples = bytesToFloats(capturedAudio);
                                session.setUser(speakerService.identify(samples));
                                log.info("Speaker ID task submitted (cached size: {} bytes)", capturedAudio.length);
                            }
                            
                            assistantAgent.streamCall(session, transcript);
                        } else {
                            log.info("Ignoring transcription while answering, {}", transcript);
                        }
                        break;
                    case "response.audio_transcript.delta":
                        log.info("got llm response delta: " + message.get("delta").getAsString());
                        break;
                    case "input_audio_buffer.speech_started":
                        log.info("======VAD Speech Start======");
                        break;
                    case "input_audio_buffer.speech_stopped":
                        log.info("======VAD Speech Stop======");
                        break;
                    case "response.done":
                        log.info("======RESPONSE DONE======");
                        if (conversationRef.get() != null) {
                            log.info("[Metric] response: " + conversationRef.get().getResponseId());
                        }
                        break;
                    default: break;
                }
            }

            @Override
            public void onClose(int code, String reason) { log.info("connection closed code: " + code + ", reason: " + reason); }
        });
        conversationRef.set(conversation);

        try {
            conversation.connect();
        } catch (NoApiKeyException | InterruptedException e) {
            throw new RuntimeException(e);
        }

        conversation.updateSession(config);
        log.info("conversation updated");

        outer: while (true) {
            try {
                session.getSemaphore().acquireUninterruptibly();
                if (session.isStop()) {
                    log.info("session {} is stopped", session.getId());
                    break outer;
                }

                log.info("asr new round");
                AtomicBoolean recognized = new AtomicBoolean(false);

                targetDataLine.start();
                targetDataLine.flush();

                log.info("recording");
                byte[] buffer = new byte[1024];
                long start = System.currentTimeMillis();
                boolean first = true;

                while (System.currentTimeMillis() - start < 20000) {
                    int read = targetDataLine.read(buffer, 0, buffer.length);
                    if (read > 0) {
                        if (session.isStop() || session.isAnswering()) {
                            log.info("break recording");
                            targetDataLine.stop();
                            break;
                        }

                        if (System.currentTimeMillis() - start > 10000 && !recognized.get()) {
                            log.info("long time to exit.");
                            break outer;
                        }

                        if (first) {
                            read = trimHeadInPlace(buffer, read, audioFormat, 100);
                            first = false;
                        }

                        // --- 实时追加音频到 session 缓存供声纹识别 ---
                        session.appendAudio(buffer, read);

                        byte[] activePayload = java.util.Arrays.copyOfRange(buffer, 0, read);
                        String audioB64 = Base64.getEncoder().encodeToString(activePayload);
                        conversation.appendAudio(audioB64);
                    }
                }
            } finally {
                log.info("ASR ends, recording stopped.");
            }
        }
        if (targetDataLine != null && targetDataLine.isOpen()) targetDataLine.stop();
        log.info("VoiceInput thread completed");
    }

    private float[] bytesToFloats(byte[] bytes) {
        float[] floats = new float[bytes.length / 2];
        ByteBuffer bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < floats.length; i++) {
            if (bb.remaining() >= 2) floats[i] = bb.getShort() / 32768.0f;
        }
        return floats;
    }

    private int countChineseCharacters(String text) {
        if (text == null || text.isEmpty()) return 0;
        int count = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= 0x4E00 && c <= 0x9FFF) count++;
        }
        return count;
    }

    public static int trimHeadInPlace(byte[] pcmData, int validLength, AudioFormat format, int headMs) {
        int bytesPerMs = (int) (format.getFrameRate() * format.getFrameSize() / 1000.0);
        int cutBytes = headMs * bytesPerMs;
        if (cutBytes >= validLength) return 0;
        int newLength = validLength - cutBytes;
        System.arraycopy(pcmData, cutBytes, pcmData, 0, newLength);
        return newLength;
    }
}
