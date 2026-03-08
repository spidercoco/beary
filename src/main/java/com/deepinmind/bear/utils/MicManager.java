package com.deepinmind.bear.utils;

import javax.sound.sampled.*;
import java.util.ArrayList;
import java.util.List;

public class MicManager {

    /**
     * 枚举所有可用麦克风（排除扬声器）
     */
    public static List<Mixer.Info> listAvailableMics() {
        List<Mixer.Info> mics = new ArrayList<>();
        for (Mixer.Info info : AudioSystem.getMixerInfo()) {
            Mixer mixer = AudioSystem.getMixer(info);
            Line.Info[] lines = mixer.getTargetLineInfo();
            if (lines != null && lines.length > 0) {
                mics.add(info);
            }
        }
        return mics;
    }

    /**
     * 打开指定 AudioFormat 的麦克风
     *
     * @param preferredName 首选设备名（可为空）
     * @param format        指定 AudioFormat
     * @return 可用的 TargetDataLine
     */
    public static TargetDataLine openMic(String preferredName, AudioFormat format) {
        List<Mixer.Info> mics = listAvailableMics();

        // 1️⃣ 尝试首选设备
        if (preferredName != null && !preferredName.isEmpty()) {
            for (Mixer.Info info : mics) {
                if (info.getName().toLowerCase().contains(preferredName.toLowerCase())) {
                    TargetDataLine line = tryOpenLine(info, format);
                    if (line != null) return line;
                }
            }
        }

        // 2️⃣ 尝试外接 USB / 非内置麦克风
        for (Mixer.Info info : mics) {
            String name = info.getName().toLowerCase();
            if (name.contains("microphone") && !name.contains("macbook")) {
                TargetDataLine line = tryOpenLine(info, format);
                if (line != null) return line;
            }
        }

        // 3️⃣ 尝试内置麦克风
        for (Mixer.Info info : mics) {
            String name = info.getName().toLowerCase();
            if (name.contains("macbook") || name.contains("internal")) {
                TargetDataLine line = tryOpenLine(info, format);
                if (line != null) return line;
            }
        }

        // 4️⃣ 尝试其他设备
        for (Mixer.Info info : mics) {
            TargetDataLine line = tryOpenLine(info, format);
            if (line != null) return line;
        }

        throw new RuntimeException("No usable microphone found for format: " + format);
    }

    /**
     * 尝试打开指定设备和格式
     */
    private static TargetDataLine tryOpenLine(Mixer.Info info, AudioFormat format) {
        Mixer mixer = AudioSystem.getMixer(info);
        DataLine.Info dataLineInfo = new DataLine.Info(TargetDataLine.class, format);

        if (!AudioSystem.isLineSupported(dataLineInfo)) {
            // 设备不支持该格式
            return null;
        }

        try {
            TargetDataLine line = (TargetDataLine) mixer.getLine(dataLineInfo);
            line.open(format);
            // line.start();
            System.out.println("Using mic: " + info.getName() + " Format: " + format);
            return line;
        } catch (LineUnavailableException | IllegalArgumentException e) {
            System.out.println("Cannot open " + info.getName() + " with format " + format + ": " + e.getMessage());
            return null;
        }
    }

    public static void main(String[] args) {
        try {
            System.out.println("Detected microphones:");
            for (Mixer.Info info : listAvailableMics()) {
                System.out.println(" - " + info.getName());
            }

            // 指定格式
            AudioFormat format = new AudioFormat(16000, 16, 1, true, false);
            TargetDataLine line = openMic("MacBook", format);

            if (line != null) {
                System.out.println("Mic opened successfully: " + line.getFormat());
            } else {
                System.out.println("No usable microphone found for format: " + format);
            }

            Thread.sleep(10000);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}