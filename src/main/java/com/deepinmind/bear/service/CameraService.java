package com.deepinmind.bear.service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.Map;

import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.MatOfInt;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.videoio.VideoCapture;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.deepinmind.bear.agent.CameraAgent;
import com.deepinmind.bear.core.WSService;
import com.deepinmind.bear.oss.OSSService;
import com.deepinmind.bear.session.Session;

import io.agentscope.core.message.Msg;
import io.agentscope.core.tool.Tool;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import nu.pattern.OpenCV;
import reactor.core.publisher.Mono;

@Slf4j
@Service("camera")
public class CameraService implements WSService {

    @Autowired
    private OSSService ossService;

    @Autowired
    private CameraAgent cameraAgent;

    @Value("${namespace}")
    private String namespace;

    private int index = 0;
    private VideoCapture sharedCamera = null;
    private long lastCameraUseTime = 0;
    private Thread cameraCleanupThread;

    @PostConstruct
    public void init() throws IOException {
        OpenCV.loadLocally();
        log.info("OpenCV loaded successfully, version: {}", Core.VERSION);
        
        cameraCleanupThread = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(2000);
                    synchronized (this) {
                        if (sharedCamera != null && sharedCamera.isOpened()) {
                            if (System.currentTimeMillis() - lastCameraUseTime > 10000) {
                                sharedCamera.release();
                                sharedCamera = null;
                                log.info("Released idle camera");
                            }
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
        cameraCleanupThread.setDaemon(true);
        cameraCleanupThread.start();
    }
    
    @PreDestroy
    public void destroy() {
        if (cameraCleanupThread != null) cameraCleanupThread.interrupt();
        synchronized (this) {
            if (sharedCamera != null && sharedCamera.isOpened()) {
                sharedCamera.release();
            }
        }
    }
    
    private synchronized boolean openSharedCamera() throws InterruptedException {
        if (sharedCamera == null || !sharedCamera.isOpened()) {
            sharedCamera = new VideoCapture(index);
            if (!sharedCamera.isOpened()) {
                sharedCamera = null;
                return false;
            }
            Thread.sleep(500); 
        }
        lastCameraUseTime = System.currentTimeMillis();
        return true;
    }

    public synchronized byte[] peekFrameRaw() {
        try {
            if (!openSharedCamera()) return null;
            Mat frame = new Mat();
            if (!sharedCamera.read(frame)) return null;
            MatOfByte buffer = new MatOfByte();
            // 降低到 35，减小体积，提升帧率
            MatOfInt params = new MatOfInt(Imgcodecs.IMWRITE_JPEG_QUALITY, 35);
            Imgcodecs.imencode(".jpg", frame, buffer, params);
            byte[] bytes = buffer.toArray();
            frame.release(); buffer.release(); params.release();
            return bytes;
        } catch (Exception e) { return null; }
    }

    @Override
    public Map<String, String> handleMessage(Map<String, String> message) {
        log.info("Received camera control: {}", message);
        String content = captureAndUpload();
        if (content != null) {
            return Map.of("status", "success", "content", content);
        } else {
            return Map.of("status", "error", "message", "Capture failed");
        }
    }

    private final RestTemplate restTemplate = new RestTemplate();

    @Tool(name = "send_image", description = "Capture image from camera and send image to msgbox")
    public synchronized boolean captureAndSendToMsgbox() {
        try {
            if (!openSharedCamera()) return false;
            Mat frame = new Mat();
            if (!sharedCamera.read(frame)) return false;
            MatOfByte buffer = new MatOfByte();
            Imgcodecs.imencode(".jpg", frame, buffer);
            byte[] imageBytes = buffer.toArray();
            String base64Image = Base64.getEncoder().encodeToString(imageBytes);
            frame.release(); buffer.release();
            return sendToMsgbox(base64Image);
        } catch (Exception e) { return false; }
    }

    private boolean sendToMsgbox(String base64Image) {
        try {
            String url = "http://it.deepinmind.com/api/" + namespace + "/msgbox";
            Map<String, String> requestBody = Map.of("content", base64Image, "type", "image");
            ResponseEntity<String> response = restTemplate.postForEntity(url, requestBody, String.class);
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) { return false; }
    }

    public synchronized String captureAndUpload() {
        try {
            if (!openSharedCamera()) return null;
            Mat frame = new Mat();
            if (!sharedCamera.read(frame)) return null;
            MatOfByte buffer = new MatOfByte();
            Imgcodecs.imencode(".jpg", frame, buffer);
            byte[] imageBytes = buffer.toArray();
            String base64Image = Base64.getEncoder().encodeToString(imageBytes);
            frame.release(); buffer.release();
            String objectName = namespace + "/camera/camera.jpg";
            ossService.uploadFile(objectName, new MockMultipartFile(objectName, objectName, "image/jpeg", imageBytes));
            Mono<Msg> msg = cameraAgent.call(new Session(), "看看图片中的人在做什么？", base64Image);
            return msg.block().getTextContent();
        } catch (Exception e) { return null; }
    }

    private static class MockMultipartFile implements org.springframework.web.multipart.MultipartFile {
        private final String name, originalFilename, contentType;
        private final byte[] content;
        public MockMultipartFile(String n, String of, String ct, byte[] c) {
            name=n; originalFilename=of; contentType=ct; content=c;
        }
        @Override public String getName() { return name; }
        @Override public String getOriginalFilename() { return originalFilename; }
        @Override public String getContentType() { return contentType; }
        @Override public boolean isEmpty() { return content.length == 0; }
        @Override public long getSize() { return content.length; }
        @Override public byte[] getBytes() { return content; }
        @Override public java.io.InputStream getInputStream() { return new ByteArrayInputStream(content); }
        @Override public void transferTo(java.io.File d) throws java.io.IOException { java.nio.file.Files.write(d.toPath(), content); }
    }
}
