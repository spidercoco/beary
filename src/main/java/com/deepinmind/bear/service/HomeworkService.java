package com.deepinmind.bear.service;

import com.deepinmind.bear.oss.OSSService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;

@Slf4j
@Service
public class HomeworkService {

    @Autowired
    private OSSService ossService;

    @Value("${namespace}")
    private String namespace;

    @Value("${beary.info.path:}")
    private String infoRoot;

    private final DateTimeFormatter yearFormatter = DateTimeFormatter.ofPattern("yyyy");
    private final DateTimeFormatter monthFormatter = DateTimeFormatter.ofPattern("MM");
    private final DateTimeFormatter dayFormatter = DateTimeFormatter.ofPattern("dd");

    /**
     * 更新课程表
     */
    @Tool(name = "update_timetable", description = "更新课程表内容")
    public String updateTimetable(@ToolParam(name = "markdown格式的课程表内容，通常包含每天的课程安排") String content) {
        return saveTimetable(content);
    }

    /**
     * 保存课程表（本地缓存 + OSS备份）
     */
    public String saveTimetable(String content) {
        log.info("Saving timetable");

        try {
            byte[] data = content.getBytes(StandardCharsets.UTF_8);
            
            // 1. 保存到本地缓存
            String localPath = getInfoRoot() + "beary_info/school/timetable.txt";
            Path dirPath = Paths.get(getInfoRoot() + "beary_info/school/");
            if (!Files.exists(dirPath)) {
                Files.createDirectories(dirPath);
            }
            Files.write(Paths.get(localPath), data);

            // 2. 保存到 OSS
            ossService.uploadFile(namespace + "/school/timetable.txt", content);
            
            return "课程表已成功更新";
        } catch (Exception e) {
            log.error("Failed to save timetable", e);
            return "更新失败: " + e.getMessage();
        }
    }

    /**
     * 获取当天的作业内容
     */
    @Tool(name = "get_homework", description = "获取当天的作业内容")
    public String getTodayHomework() {
        LocalDate now = LocalDate.now();
        return getHomeworkByDate(now);
    }

    /**
     * 获取指定日期的作业内容（优先从本地缓存取）
     */
    public String getHomeworkByDate(LocalDate date) {
        String year = date.format(yearFormatter);
        String month = date.format(monthFormatter);
        String day = date.format(dayFormatter);

        // 1. 尝试从本地缓存读取
        String localPath = getLocalPath(year, month, day);
        File localFile = new File(localPath);
        
        if (localFile.exists()) {
            log.info("Loading homework from local cache: {}", localPath);
            try {
                return Files.readString(localFile.toPath(), StandardCharsets.UTF_8);
            } catch (Exception e) {
                log.error("Failed to read local homework file", e);
            }
        }

        // 2. 本地不存在，尝试从 OSS 下载
        log.info("Homework not found in cache, fetching from OSS...");
        String ossPath = String.format("%s/homework/%s/%s/%s/content.txt", namespace, year, month, day);
        
        try {
            byte[] contentBytes = ossService.downloadFile(ossPath);
            if (contentBytes != null && contentBytes.length > 0) {
                String content = new String(contentBytes, StandardCharsets.UTF_8);
                
                // 保存到本地缓存
                saveToLocalCache(year, month, day, contentBytes);
                return content;
            }
        } catch (Exception e) {
            log.warn("Failed to fetch homework from OSS: {}. Path: {}", e.getMessage(), ossPath);
        }

        return "今天没有布置作业哦，或者作业信息尚未同步。";
    }

    /**
     * 保存当天的作业内容（本地缓存 + OSS备份）
     */
    @Tool(name = "save_homework", description = "保存当天的作业内容")
    public String saveHomework(String content) {
        LocalDate now = LocalDate.now();
        String year = now.format(yearFormatter);
        String month = now.format(monthFormatter);
        String day = now.format(dayFormatter);

        log.info("Saving homework for today: {}/{}/{}", year, month, day);

        try {
            byte[] data = content.getBytes(StandardCharsets.UTF_8);
            
            // 1. 保存到本地缓存
            saveToLocalCache(year, month, day, data);

            // 2. 异步保存到 OSS
            String ossPath = String.format("%s/homework/%s/%s/%s/content.txt", namespace, year, month, day);
            ossService.appendObject("homework/" + year + "/" + month + "/" + day + "/content.txt", content);
            
            return "作业已成功保存到本地及云端";
        } catch (Exception e) {
            log.error("Failed to save homework", e);
            return "保存失败: " + e.getMessage();
        }
    }

    /**
     * 获取课程表内容（优先从本地缓存取）
     */
    @Tool(name = "get_timetable", description = "获取当前课程表内容")
    public String getTimetable() {
        String localPath = getInfoRoot() + "beary_info/school/timetable.txt";
        File localFile = new File(localPath);

        if (localFile.exists()) {
            log.info("Loading timetable from local cache.");
            try {
                return Files.readString(localFile.toPath(), StandardCharsets.UTF_8);
            } catch (Exception e) {
                log.error("Failed to read local timetable file", e);
            }
        }

        // 本地不存在，尝试从 OSS 下载
        log.info("Timetable not found in cache, fetching from OSS...");
        String ossPath = String.format("%s/school/timetable.txt", namespace);
        
        try {
            byte[] contentBytes = ossService.downloadFile(ossPath);
            if (contentBytes != null && contentBytes.length > 0) {
                // 保存到本地缓存
                Path dirPath = Paths.get(getInfoRoot() + "beary_info/school/");
                if (!Files.exists(dirPath)) Files.createDirectories(dirPath);
                Files.write(Paths.get(localPath), contentBytes);
                
                return new String(contentBytes, StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            log.warn("Failed to fetch timetable from OSS: {}", e.getMessage());
        }

        return "还不知道课程表呢，请在云端上传 timetable.txt 到 school 目录。";
    }

    private void saveToLocalCache(String y, String m, String d, byte[] data) {
        try {
            Path dirPath = Paths.get(getLocalDir(y, m, d));
            if (!Files.exists(dirPath)) {
                Files.createDirectories(dirPath);
            }
            Files.write(Paths.get(getLocalPath(y, m, d)), data);
            log.info("Homework saved to local cache.");
        } catch (Exception e) {
            log.error("Failed to save homework to cache", e);
        }
    }

    private String getInfoRoot() {
        if (infoRoot.isEmpty()) return "";
        return infoRoot.endsWith("/") ? infoRoot : infoRoot + "/";
    }

    private String getLocalDir(String y, String m, String d) {
        return getInfoRoot() + "beary_info/homework/" + y + "/" + m + "/" + d + "/";
    }

    private String getLocalPath(String y, String m, String d) {
        return getLocalDir(y, m, d) + "content.txt";
    }
}
