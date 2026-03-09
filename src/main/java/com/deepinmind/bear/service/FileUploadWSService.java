package com.deepinmind.bear.service;

import com.deepinmind.bear.core.WSService;
import com.deepinmind.bear.oss.OSSService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * WebSocket 指令处理器：接收文件上传
 * 指令名称: file_upload
 */
@Slf4j
@Service("file_upload")
public class FileUploadWSService implements WSService {

    @Autowired
    private OSSService ossService;

    @Value("${namespace}")
    private String namespace;

    @Value("${beary.info.path:}")
    private String infoRoot;

    private final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    @Override
    public Map<String, String> handleMessage(Map<String, String> message) {
        String base64File = message.get("file");
        String fileName = message.get("fileName");
        log.info("Received file_upload command via WebSocket: {}", fileName);
        
        Map<String, String> resultMap = new HashMap<>();

        try {
            if (base64File == null || base64File.isEmpty()) {
                resultMap.put("status", "error");
                resultMap.put("message", "File data is empty");
                return resultMap;
            }

            byte[] fileBytes = Base64.getDecoder().decode(base64File);
            if (fileName == null || fileName.isEmpty()) {
                fileName = UUID.randomUUID().toString();
            }
            
            String datePath = LocalDate.now().format(dateFormatter);

            // 1. 保存到本地缓存
            try {
                String localDir = getInfoRoot() + "beary_info/uploads/" + datePath + "/";
                Files.createDirectories(Paths.get(localDir));
                Path localPath = Paths.get(localDir + fileName);
                Files.write(localPath, fileBytes);
                log.info("File saved locally: {}", localPath);
            } catch (Exception e) {
                log.warn("Failed to save file locally: {}", e.getMessage());
            }

            // 2. 上传到 OSS
            String ossKey = namespace + "/uploads/" + datePath + "/" + fileName;
            String url = ossService.uploadFile(ossKey, fileBytes);
            log.info("File uploaded to OSS: {}", url);
            
            resultMap.put("status", "success");
            resultMap.put("message", "文件已成功上传并备份");
            resultMap.put("url", url);
            
        } catch (Exception e) {
            log.error("File upload failed", e);
            resultMap.put("status", "error");
            resultMap.put("message", "Failed to upload file: " + e.getMessage());
        }

        return resultMap;
    }

    private String getInfoRoot() {
        if (infoRoot == null || infoRoot.isEmpty()) return "";
        return infoRoot.endsWith("/") ? infoRoot : infoRoot + "/";
    }
}
