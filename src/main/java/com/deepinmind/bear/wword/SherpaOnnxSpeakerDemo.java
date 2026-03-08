package com.deepinmind.bear.wword;

import com.k2fsa.sherpa.onnx.*;
import javax.sound.sampled.*;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Sherpa-ONNX 说话人识别 Demo (实战版)
 * 演示：如何从本地 WAV 文件提取声纹并识别说话人身份
 */
public class SherpaOnnxSpeakerDemo {

    public static void main(String[] args) {
        // --- 1. 配置路径 (请根据实际情况修改) ---
        String modelPath = "onnx_resources/3dspeaker_speech_eres2net_base_sv_zh-cn_3dspeaker_16k.onnx";
        
        // 用于注册的样例文件
        String papaWav = "yezi.wav";
        String mamaWav = "moyu.wav";
        
        // 用于测试的待识别文件
        String testWav = "yezi2.wav";

        // --- 2. 初始化引擎 (使用 1.12.28 Builder 模式) ---
        SpeakerEmbeddingExtractorConfig config = SpeakerEmbeddingExtractorConfig.builder()
            .setModel(modelPath)
            .setNumThreads(4)
            .setDebug(false)
            .build();

        SpeakerEmbeddingExtractor extractor = new SpeakerEmbeddingExtractor(config);
        SpeakerEmbeddingManager manager = new SpeakerEmbeddingManager(extractor.getDim());
        System.out.println("声纹引擎就绪，模型: " + modelPath);

        // --- 3. 注册说话人 (从 WAV 文件) ---
        System.out.println("\n--- 正在从文件注册声纹 ---");
        registerFromFile(extractor, manager, "爸爸", papaWav);
        registerFromFile(extractor, manager, "妈妈", mamaWav);

        // --- 4. 识别测试文件 ---
        System.out.println("\n--- 正在识别测试音频: " + testWav + " ---");
        try {
            float[] testSamples = readWavAsFloats(new File(testWav));
            if (testSamples == null) return;

            long startTime = System.currentTimeMillis();
            float[] testEmbedding = extractEmbedding(extractor, testSamples);
            
            // 搜索最佳匹配 (阈值 0.5)
            String identifiedName = manager.search(testEmbedding, 0.5f);
            
            long duration = System.currentTimeMillis() - startTime;

            if (identifiedName != null && !identifiedName.isEmpty()) {
                System.out.println("识别成功！匹配到: " + identifiedName + " (耗时: " + duration + "ms)");
            } else {
                System.out.println("无法识别该声音，未能在数据库中找到匹配项。");
            }
        } catch (Exception e) {
            System.err.println("识别过程中出错: " + e.getMessage());
        }

        // 5. 释放资源
        extractor.release();
    }

    private static float[] readWavAsFloats(File wavFile) throws Exception {
        if (!wavFile.exists()) {
            System.err.println("错误: 找不到音频文件 " + wavFile.getAbsolutePath());
            return null;
        }

        try (AudioInputStream ais = AudioSystem.getAudioInputStream(wavFile)) {
            AudioFormat format = ais.getFormat();
            if (format.getSampleRate() != 16000f) {
                System.err.println("警告: 文件采样率为 " + format.getSampleRate() + "Hz，建议使用 16000Hz。");
            }

            byte[] bytes = ais.readAllBytes();
            int samplesCount = bytes.length / 2;
            float[] floats = new float[samplesCount];
            ByteBuffer bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
            for (int i = 0; i < samplesCount; i++) {
                floats[i] = bb.getShort() / 32768.0f;
            }
            return floats;
        }
    }

    private static void registerFromFile(SpeakerEmbeddingExtractor extractor, SpeakerEmbeddingManager manager, String name, String filePath) {
        try {
            File file = new File(filePath);
            if (!file.exists()) {
                System.out.println("跳过注册 '" + name + "': 文件不存在");
                return;
            }
            float[] samples = readWavAsFloats(file);
            if (samples != null) {
                float[] embedding = extractEmbedding(extractor, samples);
                if (manager.add(name, embedding)) {
                    System.out.println("成功注册 '" + name + "'，声纹已保存。");
                }
            }
        } catch (Exception e) {
            System.err.println("注册 '" + name + "' 失败: " + e.getMessage());
        }
    }

    private static float[] extractEmbedding(SpeakerEmbeddingExtractor extractor, float[] samples) {
        OnlineStream stream = extractor.createStream();
        // 1.12.28 acceptWaveform 参数顺序修正
        stream.acceptWaveform(samples, 16000);
        stream.inputFinished();
        float[] embedding = extractor.compute(stream);
        stream.release();
        return embedding;
    }
}
