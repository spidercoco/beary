package com.deepinmind.bear;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.HashMap;
import java.util.Map;

@ControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Global exception handler for MaxUploadSizeExceededException
     * Handles file upload size limit exceeded errors
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, Object>> handleMaxUploadSizeExceeded(MaxUploadSizeExceededException e) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("error", "文件大小超过限制。单个文件最大 100MB，总请求大小最大 200MB");
        response.put("maxFileSize", "100MB");
        response.put("maxRequestSize", "200MB");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }
}

