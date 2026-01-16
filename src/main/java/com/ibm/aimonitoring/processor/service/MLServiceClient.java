package com.ibm.aimonitoring.processor.service;

import com.ibm.aimonitoring.processor.dto.LogEntryDTO;
import com.ibm.aimonitoring.processor.dto.MLPredictionRequest;
import com.ibm.aimonitoring.processor.dto.MLPredictionResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;

/**
 * Client for ML Service anomaly detection
 */
@Slf4j
@Service
public class MLServiceClient {

    private final WebClient webClient;
    
    @Value("${ml.service.url:http://localhost:8000}")
    private String mlServiceUrl;
    
    @Value("${ml.service.timeout:5000}")
    private int timeout;
    
    @Value("${ml.service.retry.max-attempts:3}")
    private int maxRetryAttempts;

    public MLServiceClient(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.build();
    }

    /**
     * Extract features from log entry for ML prediction
     */
    private MLPredictionRequest.LogFeatures extractFeatures(LogEntryDTO logEntry) {
        String message = logEntry.getMessage() != null ? logEntry.getMessage() : "";
        String messageLower = message.toLowerCase();
        
        return MLPredictionRequest.LogFeatures.builder()
                .messageLength(message.length())
                .level(logEntry.getLevel() != null ? logEntry.getLevel().toUpperCase() : "INFO")
                .service(logEntry.getService() != null ? logEntry.getService() : "unknown")
                .hasException(messageLower.contains("exception") || messageLower.contains("error"))
                .hasTimeout(messageLower.contains("timeout") || messageLower.contains("timed out"))
                .hasConnectionError(messageLower.contains("connection") && 
                                   (messageLower.contains("refused") || 
                                    messageLower.contains("failed") || 
                                    messageLower.contains("reset")))
                .build();
    }

    /**
     * Predict if a log entry is anomalous
     * 
     * @param logId Unique log identifier
     * @param logEntry Log entry to analyze
     * @return Prediction response or null if service unavailable
     */
    public MLPredictionResponse predictAnomaly(String logId, LogEntryDTO logEntry) {
        try {
            MLPredictionRequest.LogFeatures features = extractFeatures(logEntry);
            
            MLPredictionRequest request = MLPredictionRequest.builder()
                    .logId(logId)
                    .features(features)
                    .build();
            
            log.debug("Calling ML service for log: {}", logId);
            
            return webClient.post()
                    .uri(mlServiceUrl + "/api/v1/predict")
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(MLPredictionResponse.class)
                    .timeout(Duration.ofMillis(timeout))
                    .retryWhen(Retry.backoff(maxRetryAttempts, Duration.ofMillis(100))
                            .filter(throwable -> !(throwable instanceof WebClientResponseException.NotFound))
                            .onRetryExhaustedThrow((retryBackoffSpec, retrySignal) -> {
                                log.warn("ML service retry exhausted for log: {}", logId);
                                return retrySignal.failure();
                            }))
                    .doOnSuccess(response -> 
                        log.debug("ML prediction for log {}: isAnomaly={}, score={}", 
                                logId, response.getIsAnomaly(), response.getAnomalyScore()))
                    .doOnError(error -> 
                        log.error("Error calling ML service for log {}: {}", logId, error.getMessage()))
                    .onErrorResume(error -> {
                        log.warn("ML service unavailable, skipping anomaly detection for log: {}", logId);
                        return Mono.empty();
                    })
                    .block();
                    
        } catch (Exception e) {
            log.error("Unexpected error in ML prediction for log {}: {}", logId, e.getMessage(), e);
            return null;
        }
    }

    /**
     * Check if ML service is available
     * 
     * @return true if service is healthy
     */
    public boolean isServiceAvailable() {
        try {
            return webClient.get()
                    .uri(mlServiceUrl + "/api/v1/health")
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofMillis(2000))
                    .map(response -> response.contains("\"status\":\"UP\""))
                    .onErrorReturn(false)
                    .block();
        } catch (Exception e) {
            log.debug("ML service health check failed: {}", e.getMessage());
            return false;
        }
    }
}

// Made with Bob
