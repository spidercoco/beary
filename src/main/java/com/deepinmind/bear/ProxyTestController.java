package com.deepinmind.bear;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * Test Controller for verifying Netty Proxy (Intranet Penetration).
 * Access this via: http://it.deepinmind.com/proxy/bearylove/api/test
 */
@RestController
@RequestMapping("/api")
public class ProxyTestController {

    @GetMapping("/test")
    public Map<String, Object> testProxy() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", "Hello from your home machine (Aibeary)!");
        response.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        response.put("location", "Home (Internal Network)");
        return response;
    }

    @GetMapping("/test/{name}")
    public Map<String, Object> testWithParam(@PathVariable String name) {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("greeting", "Hello, " + name + "!");
        response.put("note", "This data traveled through the Netty Proxy tunnel.");
        return response;
    }

    @Autowired
    private com.deepinmind.bear.service.CameraService cameraService;

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ProxyTestController.class);

    /**
     * Real-time MJPEG Stream via Proxy.
     * Access this via: http://it.deepinmind.com/proxy/bearylove/api/camera/stream
     */
    @GetMapping(value = "/camera/stream", produces = "multipart/x-mixed-replace; boundary=frame")
    public void streamCamera(jakarta.servlet.http.HttpServletResponse response) {
        log.info("MJPEG Stream requested. Starting camera push...");
        response.setContentType("multipart/x-mixed-replace; boundary=frame");
        try (java.io.OutputStream os = response.getOutputStream()) {
            int frameCount = 0;
            while (!Thread.currentThread().isInterrupted()) {
                byte[] frameData = cameraService.peekFrameRaw(); 
                if (frameData != null) {
                    os.write(("--frame\r\n" +
                             "Content-Type: image/jpeg\r\n" +
                             "Content-Length: " + frameData.length + "\r\n\r\n").getBytes());
                    os.write(frameData);
                    os.write("\r\n".getBytes());
                    os.flush();

                    if (frameCount++ % 50 == 0) {
                        log.info("MJPEG Stream: Pushed {} frames...", frameCount);
                    }
                }
                Thread.sleep(40); 
            }
        } catch (Exception e) {
            log.info("MJPEG Stream: Client disconnected or error occurred.");
        }
    }
}
