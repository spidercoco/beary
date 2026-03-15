package com.deepinmind.bear.service;

import com.k2fsa.sherpa.onnx.*;
import com.deepinmind.bear.oss.OSSService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import ws.schild.jave.Encoder;
import ws.schild.jave.MultimediaObject;
import ws.schild.jave.encode.AudioAttributes;
import ws.schild.jave.encode.EncodingAttributes;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;

@Slf4j
@Service
public class SpeakerService {

    @Autowired
    private OSSService ossService;

    @Value("${namespace}")
    private String namespace;

    @Value("${beary.info.path:}")
    private String infoRoot;

    private SpeakerEmbeddingExtractor extractor;
    private SpeakerEmbeddingManager manager;
    
    private String getInfoRoot() {
        if (infoRoot == null || infoRoot.isEmpty()) return "";
        return infoRoot.endsWith("/") ? infoRoot : infoRoot + "/";
    }

    private String getModelPath() {
        return getInfoRoot() + "beary_info/onnx/3dspeaker_speech_eres2net_base_sv_zh-cn_3dspeaker_16k.onnx";
    }

    private String getVoicePrintDir() {
        return getInfoRoot() + "beary_info/voice_print/role/";
    }

    @PostConstruct
    public void init() {
        try {
            ensureDirectories();
            
            File modelFile = new File(getModelPath());
            if (!modelFile.exists()) {
                log.error("Speaker model NOT FOUND at: {}", modelFile.getAbsolutePath());
                return;
            }

            SpeakerEmbeddingExtractorConfig config = SpeakerEmbeddingExtractorConfig.builder()
                    .setModel(getModelPath())
                    .setNumThreads(2)
                    .setDebug(false)
                    .build();
            extractor = new SpeakerEmbeddingExtractor(config);
            manager = new SpeakerEmbeddingManager(extractor.getDim());
            
            loadStoredEmbeddings();
            
            log.info("Speaker Service initialized. Dimension: {}", extractor.getDim());
        } catch (Exception e) {
            log.error("Failed to init Speaker Service: {}", e.getMessage());
        }
    }

    private void ensureDirectories() throws IOException {
        Path path = Paths.get(getVoicePrintDir());
        if (!Files.exists(path)) Files.createDirectories(path);
    }

    public java.util.concurrent.CompletableFuture<String> identify(float[] samples) {
        return java.util.concurrent.CompletableFuture.supplyAsync(() -> {
            if (extractor == null || manager == null) return "引擎未就绪";
            try {
                OnlineStream stream = extractor.createStream();
                stream.acceptWaveform(samples, 16000);
                stream.inputFinished();
                float[] embedding = extractor.compute(stream);
                stream.release();
                String name = manager.search(embedding, 0.5f);
                return (name != null && !name.isEmpty()) ? name : "未知";
            } catch (Exception e) {
                log.error("Speaker identification error: {}", e.getMessage());
                return "识别异常";
            }
        });
    }

    public boolean register(String role, MultipartFile audioFile) throws Exception {
        log.info("Registering speaker: {}", role);
        byte[] originalData = audioFile.getBytes();
        
        // 1. 保存原始文件以备查
        String timestamp = String.valueOf(System.currentTimeMillis());
        File rawFile = new File(getVoicePrintDir() + role + "_" + timestamp + ".raw");
        Files.write(rawFile.toPath(), originalData);

        // 2. 转换为标准 WAV (16k, Mono, PCM)
        File wavFile = new File(getVoicePrintDir() + role + ".wav");
        convertToStandardWav(rawFile, wavFile);

        // 3. 备份到 OSS
        backupToOSS(role, audioFile);

        // 4. 处理并注册
        byte[] wavData = Files.readAllBytes(wavFile.toPath());
        return processAndRegister(role, wavData);
    }

    private void convertToStandardWav(File source, File target) throws Exception {
        log.info("Converting {} to standard WAV...", source.getName());
        AudioAttributes audio = new AudioAttributes();
        audio.setCodec("pcm_s16le");
        audio.setBitRate(256000);
        audio.setChannels(1);
        audio.setSamplingRate(16000);

        EncodingAttributes attrs = new EncodingAttributes();
        attrs.setOutputFormat("wav");
        attrs.setAudioAttributes(audio);

        Encoder encoder = new Encoder();
        encoder.encode(new MultimediaObject(source), target, attrs);
        log.info("Conversion completed: {}", target.getAbsolutePath());
    }

    private boolean processAndRegister(String role, byte[] wavData) throws Exception {
        float[] samples = convertWavToFloats(wavData);
        if (samples == null) throw new Exception("音频解析失败");

        OnlineStream stream = extractor.createStream();
        stream.acceptWaveform(samples, 16000);
        stream.inputFinished();
        float[] newEmbedding = extractor.compute(stream);
        stream.release();

        float[] finalEmbedding;
        File binFile = new File(getVoicePrintDir() + role + ".bin");
        
        if (binFile.exists()) {
            log.info("Updating voiceprint for '{}' via blending.", role);
            try {
                float[] oldEmbedding = readEmbeddingFromDisk(binFile);
                finalEmbedding = new float[newEmbedding.length];
                for (int i = 0; i < newEmbedding.length; i++) {
                    finalEmbedding[i] = (oldEmbedding[i] + newEmbedding[i]) / 2.0f;
                }
                normalizeInPlace(finalEmbedding);
            } catch (Exception e) {
                finalEmbedding = newEmbedding;
            }
        } else {
            finalEmbedding = newEmbedding;
        }

        if (manager.add(role, finalEmbedding)) {
            saveEmbeddingToDisk(role, finalEmbedding);
            return true;
        }
        return false;
    }

    private void normalizeInPlace(float[] v) {
        float sum = 0;
        for (float f : v) sum += f * f;
        float norm = (float) Math.sqrt(sum);
        if (norm > 1e-6) {
            for (int i = 0; i < v.length; i++) v[i] /= norm;
        }
    }

    private void saveEmbeddingToDisk(String role, float[] embedding) throws IOException {
        File file = new File(getVoicePrintDir() + role + ".bin");
        try (DataOutputStream dos = new DataOutputStream(new FileOutputStream(file))) {
            for (float f : embedding) dos.writeFloat(f);
        }
    }

    private void loadStoredEmbeddings() {
        File dir = new File(getVoicePrintDir());
        if (!dir.exists()) return;

        File[] binFiles = dir.listFiles((d, name) -> name.endsWith(".bin"));
        if (binFiles != null) {
            for (File f : binFiles) {
                try {
                    String role = f.getName().replace(".bin", "");
                    manager.add(role, readEmbeddingFromDisk(f));
                    log.info("Loaded cached voiceprint: {}", role);
                } catch (Exception e) {
                    log.error("Failed to load bin {}: {}", f.getName(), e.getMessage());
                }
            }
        }
    }

    private float[] readEmbeddingFromDisk(File file) throws IOException {
        float[] embedding = new float[extractor.getDim()];
        try (DataInputStream dis = new DataInputStream(new FileInputStream(file))) {
            for (int i = 0; i < embedding.length; i++) embedding[i] = dis.readFloat();
        }
        return embedding;
    }

    private void backupToOSS(String role, MultipartFile audioFile) {
        try {
            String objectName = namespace + "/speakers/" + role + "_" + System.currentTimeMillis() + ".wav";
            ossService.uploadFile(objectName, audioFile);
        } catch (Exception e) {
            log.warn("OSS Backup failed: {}", e.getMessage());
        }
    }

    private float[] convertWavToFloats(byte[] wavData) throws Exception {
        try (AudioInputStream ais = AudioSystem.getAudioInputStream(new ByteArrayInputStream(wavData))) {
            byte[] bytes = ais.readAllBytes();
            int samplesCount = bytes.length / 2;
            float[] floats = new float[samplesCount];
            ByteBuffer bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
            for (int i = 0; i < samplesCount; i++) {
                if (bb.remaining() >= 2) floats[i] = bb.getShort() / 32768.0f;
            }
            return floats;
        }
    }

    @PreDestroy
    public void stop() {
        if (extractor != null) extractor.release();
    }
}
