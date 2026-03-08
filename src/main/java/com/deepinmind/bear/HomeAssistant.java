package com.deepinmind.bear;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
public class HomeAssistant {

    @Value("${ha.token}")
    private String token;

    public boolean control(String device) {
        OkHttpClient client = new OkHttpClient();
        ObjectMapper objectMapper = new ObjectMapper();

        try {
            // Prepare the JSON payload for the external API call
            Map<String, String> payload = new HashMap<>();
            payload.put("entity_id", device);

            log.info("entity_id: {}", device);

            String jsonPayload = objectMapper.writeValueAsString(payload);

            // Create the request body
            okhttp3.MediaType mediaType = okhttp3.MediaType.get("application/json; charset=utf-8");
            okhttp3.RequestBody body = okhttp3.RequestBody.create(jsonPayload, mediaType);

            // Build the request (you can change the URL as needed)
            Request httpRequest = new Request.Builder()
                    .url("http://192.168.1.23:8123/api/services/light/turn_off") // Example endpoint - replace with your actual endpoint
                    .post(body).addHeader("Authorization", "Bearer " + token)
                    .addHeader("Content-Type", "application/json")
                    .build();

            // Execute the request
            try (Response response = client.newCall(httpRequest).execute()) {
                String responseBody = response.body().string();

                System.out.println(responseBody);
                // Parse the response and return as Map for JSON serialization

                return true;
            }

        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }
}
