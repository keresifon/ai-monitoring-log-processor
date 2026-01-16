package com.ibm.aimonitoring.processor.controller;

import com.ibm.aimonitoring.processor.service.ElasticsearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * Health check controller
 */
@RestController
@RequestMapping("/api/v1/processor")
@RequiredArgsConstructor
public class HealthController {

    private final ElasticsearchService elasticsearchService;

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("service", "log-processor");
        
        // Check Elasticsearch connectivity
        boolean esAvailable = elasticsearchService.isAvailable();
        health.put("elasticsearch", esAvailable ? "UP" : "DOWN");
        
        return ResponseEntity.ok(health);
    }
}

// Made with Bob
