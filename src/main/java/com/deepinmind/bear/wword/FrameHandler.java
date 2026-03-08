package com.deepinmind.bear.wword;

import org.springframework.stereotype.Component;

@Component
public class FrameHandler implements AudioFrameHandler {
    @Override
    public void onAudioFrame(byte[] pcmFrame, int bytesRead) {

    }
}
