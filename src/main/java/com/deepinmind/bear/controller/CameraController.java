package com.deepinmind.bear.controller;

import com.deepinmind.bear.service.CameraService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/aibeary/${namespace}/camera")
public class CameraController {

    @Autowired
    private CameraService cameraService;

    /**
     * 拍照并上传分析
     */
    @GetMapping("/capture")
    public ResponseEntity<Map<String, String>> capture() {
        String analysis = cameraService.captureAndUpload();
        if (analysis != null) {
            return ResponseEntity.ok(Map.of("status", "success", "content", analysis));
        } else {
            return ResponseEntity.internalServerError().body(Map.of("status", "error", "message", "Capture failed"));
        }
    }

    /**
     * 获取实时单帧画面
     */
    @GetMapping(value = "/stream", produces = MediaType.IMAGE_JPEG_VALUE)
    public ResponseEntity<byte[]> stream() {
        byte[] frame = cameraService.peekFrameRaw();
        if (frame != null) {
            return ResponseEntity.ok()
                    .contentType(MediaType.IMAGE_JPEG)
                    .body(frame);
        } else {
            return ResponseEntity.notFound().build();
        }
    }
}
