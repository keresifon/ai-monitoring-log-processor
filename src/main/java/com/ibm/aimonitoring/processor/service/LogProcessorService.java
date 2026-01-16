package com.ibm.aimonitoring.processor.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibm.aimonitoring.processor.dto.LogEntryDTO;
import com.ibm.aimonitoring.processor.dto.MLPredictionResponse;
import com.ibm.aimonitoring.processor.model.AnomalyDetection;
import com.ibm.aimonitoring.processor.repository.AnomalyDetectionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Service for processing log entries
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LogProcessorService {

    private static final String UNKNOWN_ENVIRONMENT = "unknown";
    private static final String EXCEPTION_KEYWORD = "exception";
    private static final String ERROR_KEYWORD = "error";
    private static final String TIMEOUT_KEYWORD = "timeout";
    private static final String CONNECTION_KEYWORD = "connection";
    private static final String CONNECT_KEYWORD = "connect";
    private static final String METADATA_KEY_ANOMALY_DETECTED = "anomalyDetected";
    private static final String METADATA_KEY_ANOMALY_SCORE = "anomalyScore";
    private static final String METADATA_KEY_ANOMALY_CONFIDENCE = "anomalyConfidence";
    private static final String METADATA_KEY_ML_MODEL_VERSION = "mlModelVersion";
    private static final String METADATA_KEY_PROCESSED_AT = "processedAt";
    private static final String METADATA_KEY_PROCESSOR = "processor";
    private static final String METADATA_KEY_MESSAGE_LENGTH = "messageLength";
    private static final String METADATA_KEY_HAS_EXCEPTION = "hasException";
    private static final String METADATA_KEY_HAS_TIMEOUT = "hasTimeout";
    private static final String METADATA_KEY_HAS_CONNECTION = "hasConnection";
    private static final String PROCESSOR_SERVICE_NAME = "log-processor-service";

    private final ElasticsearchService elasticsearchService;
    private final MLServiceClient mlServiceClient;
    private final AnomalyDetectionRepository anomalyDetectionRepository;
    private final ObjectMapper objectMapper;

    /**
     * Process a log entry: normalize, enrich, index to Elasticsearch, and detect anomalies
     *
     * @param logEntry the log entry to process
     */
    public void processLog(LogEntryDTO logEntry) {
        try {
            log.debug("Processing log: service={}, level={}, message={}",
                    logEntry.getService(), logEntry.getLevel(),
                    truncateMessage(logEntry.getMessage()));

            // Normalize the log entry
            LogEntryDTO normalizedLog = normalizeLog(logEntry);

            // Enrich with processing metadata
            LogEntryDTO enrichedLog = enrichLog(normalizedLog);

            // Index to Elasticsearch
            String documentId = elasticsearchService.indexLog(enrichedLog);

            log.debug("Log processed successfully: documentId={}", documentId);

            // Asynchronously detect anomalies
            detectAnomaliesAsync(documentId, enrichedLog);

        } catch (Exception e) {
            log.error("Failed to process log: {}", e.getMessage(), e);
            throw new LogProcessingException("Failed to process log entry", e);
        }
    }

    /**
     * Asynchronously detect anomalies using ML service
     */
    @Async
    protected void detectAnomaliesAsync(String logId, LogEntryDTO logEntry) {
        try {
            log.debug("Starting anomaly detection for log: {}", logId);
            
            MLPredictionResponse prediction = mlServiceClient.predictAnomaly(logId, logEntry);
            
            if (prediction != null) {
                // Add anomaly detection results to metadata
                if (logEntry.getMetadata() == null) {
                    logEntry.setMetadata(new HashMap<>());
                }
                
                logEntry.getMetadata().put(METADATA_KEY_ANOMALY_DETECTED, prediction.getIsAnomaly());
                logEntry.getMetadata().put(METADATA_KEY_ANOMALY_SCORE, prediction.getAnomalyScore());
                logEntry.getMetadata().put(METADATA_KEY_ANOMALY_CONFIDENCE, prediction.getConfidence());
                logEntry.getMetadata().put(METADATA_KEY_ML_MODEL_VERSION, prediction.getModelVersion());
                
                // Store anomaly detection result in database
                saveAnomalyDetection(logId, logEntry, prediction);
                
                if (prediction.getIsAnomaly()) {
                    log.warn("Anomaly detected in log {}: score={}, confidence={}",
                            logId, prediction.getAnomalyScore(), prediction.getConfidence());
                    
                    // TODO: Trigger alerts for high-confidence anomalies
                    if (prediction.getConfidence() > 0.7) {
                        log.info("High-confidence anomaly detected (confidence={}), alert should be triggered",
                                prediction.getConfidence());
                    }
                }
                
            } else {
                log.debug("ML service unavailable, skipping anomaly detection for log: {}", logId);
            }
            
        } catch (Exception e) {
            log.error("Error in async anomaly detection for log {}: {}", logId, e.getMessage(), e);
        }
    }

    /**
     * Save anomaly detection result to database
     */
    private void saveAnomalyDetection(String logId, LogEntryDTO logEntry, MLPredictionResponse prediction) {
        try {
            // Prepare features as JSON
            Map<String, Object> features = new HashMap<>();
            features.put("messageLength", logEntry.getMessage() != null ? logEntry.getMessage().length() : 0);
            features.put("level", logEntry.getLevel());
            features.put("service", logEntry.getService());
            features.put(METADATA_KEY_HAS_EXCEPTION, logEntry.getMetadata() != null &&
                    Boolean.TRUE.equals(logEntry.getMetadata().get(METADATA_KEY_HAS_EXCEPTION)));
            features.put(METADATA_KEY_HAS_TIMEOUT, logEntry.getMetadata() != null &&
                    Boolean.TRUE.equals(logEntry.getMetadata().get(METADATA_KEY_HAS_TIMEOUT)));
            features.put(METADATA_KEY_HAS_CONNECTION, logEntry.getMetadata() != null &&
                    Boolean.TRUE.equals(logEntry.getMetadata().get(METADATA_KEY_HAS_CONNECTION)));
            
            String featuresJson = objectMapper.writeValueAsString(features);
            
            // Create and save anomaly detection entity
            AnomalyDetection anomalyDetection = AnomalyDetection.builder()
                    .logId(logId)
                    .anomalyScore(prediction.getAnomalyScore())
                    .isAnomaly(prediction.getIsAnomaly())
                    .confidence(prediction.getConfidence())
                    .modelVersion(prediction.getModelVersion())
                    .features(featuresJson)
                    .detectedAt(Instant.now())
                    .build();
            
            anomalyDetectionRepository.save(anomalyDetection);
            
            log.debug("Anomaly detection result saved to database for log: {}", logId);
            
        } catch (JsonProcessingException e) {
            log.error("Error serializing features to JSON for log {}: {}", logId, e.getMessage());
        } catch (Exception e) {
            log.error("Error saving anomaly detection to database for log {}: {}", logId, e.getMessage(), e);
        }
    }

    /**
     * Normalize log entry (clean up data, set defaults)
     */
    private LogEntryDTO normalizeLog(LogEntryDTO logEntry) {
        // Ensure timestamp is set
        if (logEntry.getTimestamp() == null) {
            logEntry.setTimestamp(Instant.now());
        }

        // Normalize log level to uppercase
        if (logEntry.getLevel() != null) {
            logEntry.setLevel(logEntry.getLevel().toUpperCase());
        }

        // Trim message if too long (for display purposes)
        if (logEntry.getMessage() != null && logEntry.getMessage().length() > 10000) {
            logEntry.setMessage(logEntry.getMessage().substring(0, 10000) + "... [truncated]");
        }

        // Set default environment if not provided
        if (logEntry.getEnvironment() == null || logEntry.getEnvironment().isEmpty()) {
            logEntry.setEnvironment(UNKNOWN_ENVIRONMENT);
        }

        return logEntry;
    }

    /**
     * Enrich log entry with additional metadata
     */
    private LogEntryDTO enrichLog(LogEntryDTO logEntry) {
        // Add processing timestamp to metadata
        if (logEntry.getMetadata() == null) {
            logEntry.setMetadata(new java.util.HashMap<>());
        }

        logEntry.getMetadata().put(METADATA_KEY_PROCESSED_AT, Instant.now().toString());
        logEntry.getMetadata().put(METADATA_KEY_PROCESSOR, PROCESSOR_SERVICE_NAME);

        // Add message length for analytics
        if (logEntry.getMessage() != null) {
            logEntry.getMetadata().put(METADATA_KEY_MESSAGE_LENGTH, logEntry.getMessage().length());
        }

        // Extract error indicators
        if (logEntry.getMessage() != null) {
            String messageLower = logEntry.getMessage().toLowerCase();
            logEntry.getMetadata().put(METADATA_KEY_HAS_EXCEPTION,
                    messageLower.contains(EXCEPTION_KEYWORD) || messageLower.contains(ERROR_KEYWORD));
            logEntry.getMetadata().put(METADATA_KEY_HAS_TIMEOUT, messageLower.contains(TIMEOUT_KEYWORD));
            logEntry.getMetadata().put(METADATA_KEY_HAS_CONNECTION,
                    messageLower.contains(CONNECTION_KEYWORD) || messageLower.contains(CONNECT_KEYWORD));
        }

        return logEntry;
    }

    /**
     * Truncate message for logging purposes
     */
    private String truncateMessage(String message) {
        if (message == null) {
            return null;
        }
        return message.length() > 100 ? message.substring(0, 100) + "..." : message;
    }

    /**
     * Custom exception for log processing errors
     */
    public static class LogProcessingException extends RuntimeException {
        public LogProcessingException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}

// Made with Bob
