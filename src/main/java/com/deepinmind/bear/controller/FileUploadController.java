package com.deepinmind.bear.controller;

import com.deepinmind.bear.oss.OSSService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/aibeary/${namespace}/file")
public class FileUploadController {

    @Autowired
    private OSSService ossService;

    @Value("${namespace}")
    private String namespace;

    @Value("${beary.info.path:}")
    private String infoRoot;

    private final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    @PostMapping("/upload")
    public ResponseEntity<Map<String, Object>> upload(@RequestParam("file") MultipartFile file) {
        String fileName = file.getOriginalFilename();
        log.info("Received file upload request: {}", fileName);
        
        Map<String, Object> resultMap = new HashMap<>();

        try {
            if (file.isEmpty()) {
                resultMap.put("status", "error");
                resultMap.put("message", "File is empty");
                return ResponseEntity.badRequest().body(resultMap);
            }

            byte[] fileBytes = file.getBytes();
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
            resultMap.put("message", "文件已成功上传");
            resultMap.put("url", url);
            return ResponseEntity.ok(resultMap);
            
        } catch (IOException e) {
            log.error("File upload failed", e);
            resultMap.put("status", "error");
            resultMap.put("message", "Failed to upload file: " + e.getMessage());
            return ResponseEntity.internalServerError().body(resultMap);
        }
    }

    private String getInfoRoot() {
        if (infoRoot == null || infoRoot.isEmpty()) return "";
        return infoRoot.endsWith("/") ? infoRoot : infoRoot + "/";
    }
}
