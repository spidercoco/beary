package com.deepinmind.bear;

import javax.sound.sampled.*;
import java.io.*;

/**
 * WAV文件处理工具类
 */
public class WavFileUtils {
    
    /**
     * 验证文件是否为有效的WAV文件
     * @param filePath 文件路径
     * @return 是否为有效的WAV文件
     */
    public static boolean isValidWavFile(String filePath) {
        try {
            File file = new File(filePath);
            if (!file.exists() || !file.isFile()) {
                return false;
            }
            
            AudioFileFormat audioFileFormat = AudioSystem.getAudioFileFormat(file);
            return audioFileFormat.getType() == AudioFileFormat.Type.WAVE;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * 获取WAV文件信息
     * @param filePath 文件路径
     * @return WAV文件信息
     * @throws IOException 如果文件操作失败
     * @throws UnsupportedAudioFileException 如果不是支持的音频格式
     */
    public static WavFileInfo getWavFileInfo(String filePath) throws IOException, UnsupportedAudioFileException {
        File file = new File(filePath);
        if (!file.exists()) {
            throw new FileNotFoundException("文件不存在: " + filePath);
        }
        
        AudioFileFormat audioFileFormat = AudioSystem.getAudioFileFormat(file);
        AudioFormat audioFormat = audioFileFormat.getFormat();
        
        return new WavFileInfo(
            filePath,
            file.length(),
            audioFormat.getSampleRate(),
            audioFormat.getSampleSizeInBits(),
            audioFormat.getChannels(),
            audioFormat.getEncoding().toString(),
            audioFileFormat.getFrameLength(),
            calculateDuration(audioFormat, audioFileFormat.getFrameLength())
        );
    }
    
    /**
     * 计算音频时长（秒）
     * @param audioFormat 音频格式
     * @param frameLength 帧长度
     * @return 时长（秒）
     */
    private static double calculateDuration(AudioFormat audioFormat, long frameLength) {
        if (frameLength == AudioSystem.NOT_SPECIFIED) {
            return -1; // 无法确定时长
        }
        
        return (double) frameLength / audioFormat.getFrameRate();
    }
    
    /**
     * 转换音频格式
     * @param inputPath 输入文件路径
     * @param outputPath 输出文件路径
     * @param targetFormat 目标音频格式
     * @throws IOException 如果文件操作失败
     * @throws UnsupportedAudioFileException 如果不是支持的音频格式
     */
    public static void convertAudioFormat(String inputPath, String outputPath, AudioFormat targetFormat) 
            throws IOException, UnsupportedAudioFileException {
        
        File inputFile = new File(inputPath);
        if (!inputFile.exists()) {
            throw new FileNotFoundException("输入文件不存在: " + inputPath);
        }
        
        try (AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(inputFile)) {
            // 检查是否需要转换格式
            if (audioInputStream.getFormat().matches(targetFormat)) {
                // 格式相同，直接复制
                try (FileOutputStream fos = new FileOutputStream(outputPath)) {
                    byte[] buffer = new byte[4096];
                    int bytesRead;
                    while ((bytesRead = audioInputStream.read(buffer)) != -1) {
                        fos.write(buffer, 0, bytesRead);
                    }
                }
                return;
            }
            
            // 转换格式
            try (AudioInputStream convertedStream = AudioSystem.getAudioInputStream(targetFormat, audioInputStream)) {
                File outputFile = new File(outputPath);
                File parentDir = outputFile.getParentFile();
                if (parentDir != null && !parentDir.exists()) {
                    parentDir.mkdirs();
                }
                
                AudioSystem.write(convertedStream, AudioFileFormat.Type.WAVE, outputFile);
            }
        }
    }
    
    /**
     * 裁剪音频文件
     * @param inputPath 输入文件路径
     * @param outputPath 输出文件路径
     * @param startTime 开始时间（秒）
     * @param duration 持续时间（秒）
     * @throws IOException 如果文件操作失败
     * @throws UnsupportedAudioFileException 如果不是支持的音频格式
     */
    public static void trimAudio(String inputPath, String outputPath, double startTime, double duration) 
            throws IOException, UnsupportedAudioFileException {
        
        File inputFile = new File(inputPath);
        if (!inputFile.exists()) {
            throw new FileNotFoundException("输入文件不存在: " + inputPath);
        }
        
        try (AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(inputFile)) {
            AudioFormat audioFormat = audioInputStream.getFormat();
            
            // 计算开始和结束的帧位置
            long startFrame = (long) (startTime * audioFormat.getFrameRate());
            long durationFrames = (long) (duration * audioFormat.getFrameRate());
            
            // 跳过到开始位置
            audioInputStream.skip(startFrame * audioFormat.getFrameSize());
            
            // 创建裁剪后的音频流
            long framesToRead = Math.min(durationFrames, audioInputStream.getFrameLength() - startFrame);
            AudioInputStream trimmedStream = new AudioInputStream(
                audioInputStream,
                audioFormat,
                framesToRead
            );
            
            // 写入输出文件
            File outputFile = new File(outputPath);
            File parentDir = outputFile.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }
            
            AudioSystem.write(trimmedStream, AudioFileFormat.Type.WAVE, outputFile);
        }
    }
    
    /**
     * 合并多个WAV文件
     * @param inputPaths 输入文件路径数组
     * @param outputPath 输出文件路径
     * @throws IOException 如果文件操作失败
     * @throws UnsupportedAudioFileException 如果不是支持的音频格式
     */
    public static void mergeWavFiles(String[] inputPaths, String outputPath) 
            throws IOException, UnsupportedAudioFileException {
        
        if (inputPaths.length == 0) {
            throw new IllegalArgumentException("至少需要一个输入文件");
        }
        
        // 使用第一个文件的格式作为目标格式
        File firstFile = new File(inputPaths[0]);
        if (!firstFile.exists()) {
            throw new FileNotFoundException("第一个文件不存在: " + inputPaths[0]);
        }
        
        AudioFileFormat firstFileFormat = AudioSystem.getAudioFileFormat(firstFile);
        AudioFormat targetFormat = firstFileFormat.getFormat();
        
        ByteArrayOutputStream mergedData = new ByteArrayOutputStream();
        
        for (String inputPath : inputPaths) {
            File inputFile = new File(inputPath);
            if (!inputFile.exists()) {
                throw new FileNotFoundException("文件不存在: " + inputPath);
            }
            
            try (AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(inputFile)) {
                AudioFormat sourceFormat = audioInputStream.getFormat();
                
                // 如果格式不同，需要转换
                try (AudioInputStream streamToUse = !sourceFormat.matches(targetFormat) ? 
                    AudioSystem.getAudioInputStream(targetFormat, audioInputStream) : audioInputStream) {
                    
                    // 读取音频数据
                    byte[] buffer = new byte[4096];
                    int bytesRead;
                    while ((bytesRead = streamToUse.read(buffer)) != -1) {
                        mergedData.write(buffer, 0, bytesRead);
                    }
                }
            }
        }
        
        // 创建合并后的音频流
        byte[] mergedBytes = mergedData.toByteArray();
        ByteArrayInputStream bais = new ByteArrayInputStream(mergedBytes);
        AudioInputStream mergedStream = new AudioInputStream(bais, targetFormat, mergedBytes.length / targetFormat.getFrameSize());
        
        // 写入输出文件
        File outputFile = new File(outputPath);
        File parentDir = outputFile.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs();
        }
        
        AudioSystem.write(mergedStream, AudioFileFormat.Type.WAVE, outputFile);
    }
    
    /**
     * WAV文件信息类
     */
    public static class WavFileInfo {
        private final String filePath;
        private final long fileSize;
        private final float sampleRate;
        private final int sampleSizeInBits;
        private final int channels;
        private final String encoding;
        private final long frameLength;
        private final double duration;
        
        public WavFileInfo(String filePath, long fileSize, float sampleRate, int sampleSizeInBits, 
                          int channels, String encoding, long frameLength, double duration) {
            this.filePath = filePath;
            this.fileSize = fileSize;
            this.sampleRate = sampleRate;
            this.sampleSizeInBits = sampleSizeInBits;
            this.channels = channels;
            this.encoding = encoding;
            this.frameLength = frameLength;
            this.duration = duration;
        }
        
        // Getters
        public String getFilePath() { return filePath; }
        public long getFileSize() { return fileSize; }
        public float getSampleRate() { return sampleRate; }
        public int getSampleSizeInBits() { return sampleSizeInBits; }
        public int getChannels() { return channels; }
        public String getEncoding() { return encoding; }
        public long getFrameLength() { return frameLength; }
        public double getDuration() { return duration; }
        
        @Override
        public String toString() {
            return String.format(
                "WAV文件信息:\n" +
                "文件路径: %s\n" +
                "文件大小: %d 字节 (%.2f KB)\n" +
                "采样率: %.0f Hz\n" +
                "位深度: %d bit\n" +
                "声道数: %d\n" +
                "编码格式: %s\n" +
                "帧数: %d\n" +
                "时长: %.2f 秒",
                filePath,
                fileSize,
                fileSize / 1024.0,
                sampleRate,
                sampleSizeInBits,
                channels,
                encoding,
                frameLength,
                duration
            );
        }
    }
}
