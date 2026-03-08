package com.deepinmind.bear.service;

import java.lang.invoke.StringConcatFactory;
import java.util.Map;

import org.osgi.service.component.annotations.Activate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.deepinmind.bear.core.AudioService;
import com.deepinmind.bear.core.WSService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
// @Service("play")
public class DingTalkService implements WSService {

    @Autowired
    AudioService qwen3tts;

    @Override
    public Map<String, String> handleMessage(Map<String, String> message) {
        // 打印收到的JSON消息
        log.info("Received message: {}", message);

        // 提取并打印text字段中的content内容
        if (message.containsKey("content")) {
            qwen3tts.play(message.get("content"));
        }

                    


        // 返回响应
        return Map.of("status", "received");
    }

}
