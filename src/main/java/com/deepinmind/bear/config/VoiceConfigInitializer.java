package com.deepinmind.bear.config;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import jakarta.annotation.PostConstruct;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Voice configuration initializer
 * Fetches role and voice settings from remote server at startup
 */
@Slf4j
@Component
public class VoiceConfigInitializer {

    @Value("${namespace}")
    private String namespace;

    @Value("${voice.config.url:http://it.deepinmind.com/adm/appconfig}")
    private String configUrl;

    @Getter
    private boolean initialized = false;

    // Store role to voice mapping from config list
    @Getter
    private final Map<String, String> roleVoiceMap = new HashMap<>();

    private final RestTemplate restTemplate = new RestTemplate();

    @PostConstruct
    public void init() {
        log.info("Initializing voice configuration for namespace: {}", namespace);
        
        try {
            fetchVoiceConfig();
        } catch (Exception e) {
            log.error("Failed to fetch voice configuration: {}", e.getMessage(), e);
            // Set default values
            setDefaultConfig();
        }
    }

    private void fetchVoiceConfig() {
        String url = configUrl + "/" + namespace;
        log.info("Fetching voice config from: {}", url);

        ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
        
        if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
            Map<String, Object> responseBody = response.getBody();
            
            // Extract configs list from response
            List<Map<String, Object>> configs = (List<Map<String, Object>>) responseBody.get("configs");
            
            if (configs != null && !configs.isEmpty()) {
                // Build role to voice mapping from all configs
                for (Map<String, Object> cfg : configs) {
                    String configRole = (String) cfg.get("role");
                    String configVoice = (String) cfg.get("voice");
                    
                    if (configRole != null && configVoice != null) {
                        roleVoiceMap.put(configRole, configVoice);
                    }
                }
                
                this.initialized = true;
                log.info("Voice configuration loaded successfully: {} role-voice mappings", roleVoiceMap.size());
            } else {
                log.warn("No configs found in response");
                setDefaultConfig();
            }
        } else {
            log.warn("Failed to load voice config, response: {}", response.getStatusCode());
            setDefaultConfig();
        }
    }

    private void setDefaultConfig() {
        this.initialized = false;
        
        // Set default mapping
        roleVoiceMap.put("assistant", "xiaoyun");
        
        log.info("Using default voice configuration: {}", roleVoiceMap);
    }

    /**
     * Get voice by role
     * @param role the role name
     * @return voice name for the role, or null if not found
     */
    public String getVoiceByRole(String role) {
        return roleVoiceMap.get(role);
    }

    /**
     * Check if role exists in mapping
     * @param role the role name
     * @return true if exists
     */
    public boolean hasRole(String role) {
        return roleVoiceMap.containsKey(role);
    }
}
