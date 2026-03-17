package com.deepinmind.bear.service;

import com.alibaba.dashscope.audio.qwen_asr.*;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

@Slf4j
@Service
public class VoiceRecognitionService {

    @Value("${dashscope.key}")
    private String apiKey;

    private final Gson gson = new Gson();

    /**
     * 识别音频 URL
     */
    public String recognize(String fileUrl) throws Exception {
        log.info("Starting voice transcription for URL: {}", fileUrl);
        
        QwenTranscriptionParam param = QwenTranscriptionParam.builder()
                .apiKey(apiKey)
                .model("qwen3-asr-flash-filetrans")
                .fileUrl(fileUrl)
                .parameter("enable_itn", false)
                .parameter("enable_words", false)
                .build();

        QwenTranscription transcription = new QwenTranscription();
        
        // 提交并等待
        QwenTranscriptionResult result = transcription.asyncCall(param);
        result = transcription.wait(QwenTranscriptionQueryParam.FromTranscriptionParam(param, result.getTaskId()));

        QwenTranscriptionTaskResult taskResult = result.getResult();
        if (taskResult != null && taskResult.getTranscriptionUrl() != null) {
            return fetchTranscriptionContent(taskResult.getTranscriptionUrl());
        }
        
        throw new Exception("Transcription failed or returned no result");
    }

    private String fetchTranscriptionContent(String transcriptionUrl) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(transcriptionUrl).openConnection();
        connection.setRequestMethod("GET");
        
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
            JsonObject json = gson.fromJson(reader, JsonObject.class);
            // Qwen ASR Flash 结果格式中通常在 transcription_url 返回的 JSON 里包含 text
            // 根据实际返回结构提取，通常是总体的 "transcription" 字段
            log.debug("Full transcription JSON: {}", json);
            
            // 尝试提取转写文本 (这里根据官方文档可能需要多层获取)
            if (json.has("transcription")) {
                return json.get("transcription").getAsString();
            }
            return json.toString(); // Fallback
        }
    }
}
