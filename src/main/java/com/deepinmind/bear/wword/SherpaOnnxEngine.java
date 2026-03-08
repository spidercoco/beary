package com.deepinmind.bear.wword;

import com.k2fsa.sherpa.onnx.*;
import lombok.extern.slf4j.Slf4j;
import java.io.File;

@Slf4j
public class SherpaOnnxEngine implements WakeWordEngine {
    private KeywordSpotter kws;
    private OnlineStream stream;

    private String finalPath = "beary_info/onnx/";

    @Override
    public void init() throws Exception {
        KeywordSpotterConfig config = KeywordSpotterConfig.builder()
            .setKeywordsFile(finalPath + "keywords.txt")
            .setMaxActivePaths(4)
            .setOnlineModelConfig(OnlineModelConfig.builder()
                .setTokens(finalPath + "tokens.txt")
                .setNumThreads(1)
                .setTransducer(OnlineTransducerModelConfig.builder()
                    .setEncoder(finalPath + "encoder-epoch-12-avg-2-chunk-16-left-64.onnx")
                    .setDecoder(finalPath + "decoder-epoch-12-avg-2-chunk-16-left-64.onnx")
                    .setJoiner(finalPath + "joiner-epoch-12-avg-2-chunk-16-left-64.onnx")
                    .build())
                .build())
            .build();

        kws = new KeywordSpotter(config);
        stream = kws.createStream();
        log.info("Sherpa-ONNX engine initialized successfully.");
    }

    @Override
    public boolean process(short[] samples) {
        float[] floatSamples = new float[samples.length];
        for (int i = 0; i < samples.length; i++) {
            floatSamples[i] = samples[i] / 32768.0f;
        }

        if (stream != null) {
            stream.acceptWaveform(floatSamples, 16000); 
            while (kws.isReady(stream)) {
                kws.decode(stream);
            }
            String result = kws.getResult(stream).getKeyword();
            return !result.isEmpty();
        }
        return false;
    }

    @Override
    public void release() {
        if (stream != null) stream.release();
        if (kws != null) kws.release();
    }
}
