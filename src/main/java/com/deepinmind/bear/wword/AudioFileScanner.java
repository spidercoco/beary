package com.deepinmind.bear.wword;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

public class AudioFileScanner {
    
    /**
     * 扫描指定文件夹下的音频文件并保存路径到文件
     * @param folderPath 要扫描的文件夹路径
     * @param outputFile 输出文件路径
     * @throws IOException 文件操作异常
     */
    public static void scanAndSaveAudioFiles(String folderPath, String outputFile) throws IOException {
        // 音频文件后缀
        List<String> extensions = Arrays.asList(".mp3", ".wav", ".flac", ".aac", ".ogg", ".m4a", ".wma");
        
        // 扫描并写入文件
        try (FileWriter writer = new FileWriter(outputFile)) {
            Files.walk(Paths.get(folderPath))
                .filter(Files::isRegularFile)
                .map(path -> path.toAbsolutePath().toString())
                .filter(path -> extensions.stream().anyMatch(path.toLowerCase()::endsWith))
                .forEach(path -> {
                    try {
                        writer.write(path + "\n");
                        System.out.println(path);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                });
        }
        
        System.out.println("音频文件路径已保存到: " + outputFile);
    }
    
    /**
     * 扫描指定文件夹下的音频文件并返回路径列表
     * @param folderPath 要扫描的文件夹路径
     * @return 音频文件路径列表
     * @throws IOException 文件操作异常
     */
    public static List<String> scanAudioFiles(String folderPath) throws IOException {
        List<String> extensions = Arrays.asList(".mp3", ".wav", ".flac", ".aac", ".ogg", ".m4a", ".wma");
        
        return Files.walk(Paths.get(folderPath))
            .filter(Files::isRegularFile)
            .map(path -> path.toAbsolutePath().toString())
            .filter(path -> extensions.stream().anyMatch(path.toLowerCase()::endsWith))
            .collect(java.util.stream.Collectors.toList());
    }
    
    public static void main(String[] args) throws IOException {
        String folderPath = args.length > 0 ? args[0] : "/mnt/nas/";
        String outputFile = args.length > 1 ? args[1] : "audio_files.txt";
        
        // 调用重构后的方法
        scanAndSaveAudioFiles(folderPath, outputFile);
    }
}
