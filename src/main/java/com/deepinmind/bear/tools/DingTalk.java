package com.deepinmind.bear.tools;

import io.agentscope.core.message.Msg;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import nu.pattern.OpenCV;
import reactor.core.publisher.Mono;

import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.videoio.VideoCapture;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import com.deepinmind.bear.agent.CameraAgent;
import com.deepinmind.bear.oss.OSSService;
import com.deepinmind.bear.session.Session;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

@Component
@Slf4j
public class DingTalk {
    
    // 创建RestTemplate实例
    private final RestTemplate restTemplate = new RestTemplate();

    // 钉钉机器人配置
    @Value("${dingtalk.accessToken}")
    private String accessToken;

    @Value("${dingtalk.secret:}")
    private String secret;

    // 手机号配置，格式：爸爸=18910103392,妈妈=15811207928
    @Value("${dingtalk.phoneNumbers}")
    private String phoneNumbersConfig;

    private final Map<String, String> phoneNumberMap = new HashMap<>();

    @PostConstruct
    public void init() {
        // 解析手机号配置
        if (phoneNumbersConfig != null && !phoneNumbersConfig.isEmpty()) {
            String[] pairs = phoneNumbersConfig.split(",");
            for (String pair : pairs) {
                String[] kv = pair.split("=");
                if (kv.length == 2) {
                    phoneNumberMap.put(kv[0].trim(), kv[1].trim());
                }
            }
        }

        OpenCV.loadLocally();
        log.info("OpenCV loaded successfully, version: {}", Core.VERSION);
        
        log.info("DingTalk initialized with {} phone numbers", phoneNumberMap.size());
    }

    private String getUrl() {
        String baseUrl = "https://oapi.dingtalk.com/robot/send?access_token=" + accessToken;
        if (secret == null || secret.isEmpty()) {
            return baseUrl;
        }

        try {
            long timestamp = System.currentTimeMillis();
            String stringToSign = timestamp + "\n" + secret;
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(new javax.crypto.spec.SecretKeySpec(secret.getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] signData = mac.doFinal(stringToSign.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            String sign = java.net.URLEncoder.encode(Base64.getEncoder().encodeToString(signData), "UTF-8");
            return baseUrl + "&timestamp=" + timestamp + "&sign=" + sign;
        } catch (Exception e) {
            log.error("Error calculating DingTalk signature", e);
            return baseUrl;
        }
    }

    @Tool(name = "send_text", description = "发送文本消息到钉钉")
    public String send(@ToolParam(name = "receiver", description = "接收人：dad|mom") String receiver, 
        @ToolParam(name = "text", description = "消息内容") String text) {
        log.info("Sending message to {} with text: {}", receiver, text);
        
        // 构建消息体
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("msgtype", "text");
        
        Map<String, String> textContent = new HashMap<>();
        textContent.put("content", "嘟嘟：" + text);
        requestBody.put("text", textContent);

        Map<String, Object> ats = new HashMap<>();
        String phoneNumber = phoneNumberMap.get(receiver);
        if (phoneNumber != null) {
            ats.put("atMobiles", java.util.Collections.singletonList(phoneNumber));
        }
        requestBody.put("at", ats);

        try {
            String targetUrl = getUrl();
            log.info("Sending request to DingTalk API: {}", targetUrl);
            // 发送POST请求
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.postForObject(targetUrl, requestBody, Map.class);
            
            log.info("DingTalk API response: {}", response);
            
            if (response != null && response.containsKey("errmsg")) {
                String result = "消息发送状态: " + response.get("errmsg");
                log.info(result);
                return result;
            } else {
                String result = "消息发送完成，但未收到明确响应";
                log.info(result);
                return result;
            }
        } catch (Exception e) {
            String error = "发送消息失败: " + e.getMessage();
            log.error(error, e);
            return error;
        }   
    }


    @Autowired
    private OSSService ossService;

    @Autowired
    private CameraAgent cameraAgent;

    @Value("${namespace}")
    private String namespace;

    private int index = 0;

    /**
     * Capture image from webcam and send base64 to msgbox endpoint
     * @return true if successful, false otherwise
     */
    @Tool(name = "send_image", description = "Capture image from camera and send image to msgbox")
    public boolean sendImage(@ToolParam(name = "receiver", description = "接收人：dad|mom") String receiver, 
        @ToolParam(name = "text", description = "消息内容") String text) {
                    VideoCapture camera = null;
        try {
            // Open default camera (index 0)
            camera = new VideoCapture(index);
            
            if (!camera.isOpened()) {
                log.error("Cannot open camera");
                return false;
            }

            // Wait a bit for camera to warm up
            Thread.sleep(500);

            // Capture frame
            Mat frame = new Mat();
            if (!camera.read(frame)) {
                log.error("Failed to capture image from camera");
                return false;
            }

            // Convert Mat to byte array (JPEG)
            MatOfByte buffer = new MatOfByte();
            Imgcodecs.imencode(".jpg", frame, buffer);
            byte[] imageBytes = buffer.toArray();

            // Convert to base64
            String base64Image = Base64.getEncoder().encodeToString(imageBytes);
            log.info("Image captured successfully, base64 length: {}", base64Image.length());
            
            // Release resources
            frame.release();
            buffer.release();

            // Upload to OSS and get URL
            String objectName = namespace + "/camera/" + System.currentTimeMillis() + ".jpg";
            ossService.uploadFile(objectName, 
                new MockMultipartFile(objectName, objectName, "image/jpeg", imageBytes));
            
            // 获取一个月的带签名访问链接
            String imageUrl = ossService.getPresignedUrl(objectName);
            log.info("Image uploaded and presigned URL generated: {}", imageUrl);

            // Send to msgbox endpoint (passing URL instead of base64)
            sendToMsgbox(imageUrl);

            // 构建消息体
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("msgtype", "markdown");
            
            Map<String, String> markdown = new HashMap<>();
            markdown.put("title", "小熊AI提醒");
            // 组合文本与图片
            String markdownText = "### 消息\n" + text + "\n\n" + "![image](" + imageUrl + ")";
            markdown.put("text", markdownText);
            requestBody.put("markdown", markdown);

            Map<String, Object> ats = new HashMap<>();
            String phoneNumber = phoneNumberMap.get(receiver);
            if (phoneNumber != null) {
                ats.put("atMobiles", java.util.Collections.singletonList(phoneNumber));
            }
            requestBody.put("at", ats);

            String targetUrl = getUrl();
            log.info("Sending request to DingTalk API: {}", targetUrl);
            // 发送POST请求
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.postForObject(targetUrl, requestBody, Map.class);
            
            log.info("DingTalk API response: {}", response);
            
            return true;
        } catch (Exception e) {
            log.error("Error capturing image: {}", e.getMessage(), e);
            return false;
        } finally {
            if (camera != null && camera.isOpened()) {
                camera.release();
            }
        }
    }

    /**
     * Send image URL to msgbox endpoint
     * @param imageUrl OSS signed URL
     * @return true if successful
     */
    private boolean sendToMsgbox(String imageUrl) {
        try {
            String url = "http://it.deepinmind.com/api/" + namespace + "/msgbox";
            
            Map<String, String> requestBody = Map.of(
                "content", imageUrl,
                "type", "image_url" // Changed type to image_url to distinguish
            );
            
            log.info("Sending image URL to msgbox: {}", url);
            ResponseEntity<String> response = restTemplate.postForEntity(url, requestBody, String.class);
            
            boolean success = response.getStatusCode().is2xxSuccessful();
            log.info("Msgbox send result: {}", success);
            return success;
            
        } catch (Exception e) {
            log.error("Error sending to msgbox: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Capture image from webcam using OpenCV and upload to OSS
     * @return The analysis result from CameraAgent, or null if failed
     */
    public String captureAndUpload() {
        VideoCapture camera = null;
        try {
            // Open default camera (index 0)
            camera = new VideoCapture(index);
            
            if (!camera.isOpened()) {
                log.error("Cannot open camera");
                return null;
            }

            // Wait a bit for camera to warm up
            Thread.sleep(500);

            // Capture frame
            Mat frame = new Mat();
            if (!camera.read(frame)) {
                log.error("Failed to capture image from camera");
                return null;
            }

            // Convert Mat to byte array (JPEG)
            MatOfByte buffer = new MatOfByte();
            Imgcodecs.imencode(".jpg", frame, buffer);
            byte[] imageBytes = buffer.toArray();

            // Convert to base64
            String base64Image = Base64.getEncoder().encodeToString(imageBytes);
            log.info("Image captured successfully, base64 length: {}", base64Image.length());
            
            // Release resources
            frame.release();
            buffer.release();

            String objectName = namespace + "/camera/camera.jpg";

            // Upload to OSS
            String imageUrl = ossService.uploadFile(objectName, 
                new MockMultipartFile(objectName, objectName, "image/jpeg", imageBytes));

            log.info("Image uploaded to OSS: {}", imageUrl);

            // Call camera agent for analysis
            Mono<Msg> msg = cameraAgent.call(new Session(), "看看图片中的人在做什么？", base64Image);
            return msg.block().getTextContent();

        } catch (Exception e) {
            log.error("Error capturing or uploading image: {}", e.getMessage(), e);
            return null;
        } finally {
            if (camera != null && camera.isOpened()) {
                camera.release();
            }
        }
    }

    /**
     * Simple wrapper to convert byte array to MultipartFile-like object for OSS upload
     */
    private static class MockMultipartFile implements org.springframework.web.multipart.MultipartFile {
        private final String name;
        private final String originalFilename;
        private final String contentType;
        private final byte[] content;

        public MockMultipartFile(String name, String originalFilename, String contentType, byte[] content) {
            this.name = name;
            this.originalFilename = originalFilename;
            this.contentType = contentType;
            this.content = content;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getOriginalFilename() {
            return originalFilename;
        }

        @Override
        public String getContentType() {
            return contentType;
        }

        @Override
        public boolean isEmpty() {
            return content.length == 0;
        }

        @Override
        public long getSize() {
            return content.length;
        }

        @Override
        public byte[] getBytes() {
            return content;
        }

        @Override
        public java.io.InputStream getInputStream() {
            return new ByteArrayInputStream(content);
        }

        @Override
        public void transferTo(java.io.File dest) throws java.io.IOException {
            java.nio.file.Files.write(dest.toPath(), content);
        }
    }
}