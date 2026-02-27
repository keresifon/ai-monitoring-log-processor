package com.ibm.aimonitoring.processor.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibm.aimonitoring.processor.dto.LogEntryDTO;
import com.ibm.aimonitoring.processor.dto.MLPredictionResponse;
import com.ibm.aimonitoring.processor.model.AnomalyDetection;
import com.ibm.aimonitoring.processor.repository.AnomalyDetectionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LogProcessorServiceTest {

    @Mock
    private ElasticsearchService elasticsearchService;

    @Mock
    private MLServiceClient mlServiceClient;

    @Mock
    private AnomalyDetectionRepository anomalyDetectionRepository;

    @Mock
    private ObjectMapper objectMapper;

    private LogProcessorService logProcessorService;

    private LogEntryDTO testLogEntry;

    @BeforeEach
    void setUp() {
        // Create a new instance with mocked dependencies
        // For self-injection, we pass the service itself (will be set after construction)
        logProcessorService = new LogProcessorService(
                elasticsearchService,
                mlServiceClient,
                anomalyDetectionRepository,
                objectMapper,
                null // Will be set to self after construction
        );
        // Use reflection to set self reference
        try {
            java.lang.reflect.Field selfField = LogProcessorService.class.getDeclaredField("self");
            selfField.setAccessible(true);
            selfField.set(logProcessorService, logProcessorService);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set self reference", e);
        }

        testLogEntry = LogEntryDTO.builder()
                .timestamp(Instant.now())
                .level("INFO")
                .message("Test log message")
                .service("test-service")
                .host("test-host")
                .environment("test")
                .build();
    }

    @Test
    void testProcessLog_Success() {
        // Given
        String documentId = "doc-123";
        when(elasticsearchService.indexLog(any(LogEntryDTO.class))).thenReturn(documentId);
        when(mlServiceClient.predictAnomaly(anyString(), any(LogEntryDTO.class))).thenReturn(null);

        // When
        logProcessorService.processLog(testLogEntry);

        // Then
        verify(elasticsearchService).indexLog(any(LogEntryDTO.class));
        // Note: detectAnomaliesAsync is called asynchronously, so we verify ML service is called
        // In a real async scenario, we'd need to wait, but for unit tests we verify the call was made
    }

    @Test
    void testProcessLog_ElasticsearchFailure() {
        // Given
        when(elasticsearchService.indexLog(any(LogEntryDTO.class)))
                .thenThrow(new RuntimeException("Elasticsearch error"));

        // When/Then
        assertThrows(LogProcessorService.LogProcessingException.class, () -> {
            logProcessorService.processLog(testLogEntry);
        });
    }

    @Test
    void testNormalizeLog_WithNullTimestamp() {
        // Given
        LogEntryDTO logEntry = LogEntryDTO.builder()
                .level("info")
                .message("test")
                .build();

        // When
        logProcessorService.processLog(logEntry);

        // Then
        assertNotNull(logEntry.getTimestamp());
        assertEquals("INFO", logEntry.getLevel());
    }

    @Test
    void testNormalizeLog_WithLongMessage() {
        // Given
        String longMessage = "a".repeat(15000);
        assertEquals(15000, longMessage.length()); // Verify original length
        LogEntryDTO logEntry = LogEntryDTO.builder()
                .message(longMessage)
                .level("ERROR")
                .service("test")
                .build();

        when(elasticsearchService.indexLog(any(LogEntryDTO.class))).thenReturn("doc-1");
        when(mlServiceClient.predictAnomaly(anyString(), any(LogEntryDTO.class))).thenReturn(null);

        // When
        logProcessorService.processLog(logEntry);

        // Then - message should be truncated to 10000 + "... [truncated]" = 10015 characters
        // "... [truncated]" is 15 characters (3 dots + space + 11 chars in brackets)
        assertNotNull(logEntry.getMessage(), "Message should not be null");
        assertEquals(10015, logEntry.getMessage().length(), 
                "Message should be truncated to 10000 characters plus '... [truncated]' (15 chars)");
        assertTrue(logEntry.getMessage().endsWith("... [truncated]"), 
                "Message should end with truncation indicator");
        assertEquals(10000, logEntry.getMessage().substring(0, logEntry.getMessage().length() - 15).length(),
                "First part of message should be exactly 10000 characters");
    }

    @Test
    void testNormalizeLog_WithEmptyEnvironment() {
        // Given
        LogEntryDTO logEntry = LogEntryDTO.builder()
                .level("WARN")
                .message("test")
                .service("test")
                .environment("")
                .build();

        when(elasticsearchService.indexLog(any(LogEntryDTO.class))).thenReturn("doc-1");
        when(mlServiceClient.predictAnomaly(anyString(), any(LogEntryDTO.class))).thenReturn(null);

        // When
        logProcessorService.processLog(logEntry);

        // Then
        assertEquals("unknown", logEntry.getEnvironment());
    }

    @Test
    void testEnrichLog_AddsMetadata() {
        // Given
        LogEntryDTO logEntry = LogEntryDTO.builder()
                .level("ERROR")
                .message("Connection timeout exception occurred")
                .service("api-gateway")
                .build();

        when(elasticsearchService.indexLog(any(LogEntryDTO.class))).thenReturn("doc-1");
        when(mlServiceClient.predictAnomaly(anyString(), any(LogEntryDTO.class))).thenReturn(null);

        // When
        logProcessorService.processLog(logEntry);

        // Then
        assertNotNull(logEntry.getMetadata());
        assertTrue(logEntry.getMetadata().containsKey("processedAt"));
        assertTrue(logEntry.getMetadata().containsKey("processor"));
        assertTrue(logEntry.getMetadata().containsKey("messageLength"));
        assertTrue((Boolean) logEntry.getMetadata().get("hasException"));
        assertTrue((Boolean) logEntry.getMetadata().get("hasTimeout"));
        assertTrue((Boolean) logEntry.getMetadata().get("hasConnection"));
    }

    @Test
    void testDetectAnomaliesAsync_WithPrediction() throws JsonProcessingException {
        // Given
        String logId = "log-123";
        LogEntryDTO logEntry = LogEntryDTO.builder()
                .message("Test message")
                .level("ERROR")
                .service("test-service")
                .metadata(new HashMap<>())
                .build();

        MLPredictionResponse prediction = MLPredictionResponse.builder()
                .isAnomaly(true)
                .anomalyScore(0.85)
                .confidence(0.75)
                .modelVersion("v1.0")
                .build();

        when(mlServiceClient.predictAnomaly(eq(logId), any(LogEntryDTO.class))).thenReturn(prediction);
        when(objectMapper.writeValueAsString(any(Map.class))).thenReturn("{\"messageLength\":12}");
        when(anomalyDetectionRepository.save(any(AnomalyDetection.class))).thenReturn(new AnomalyDetection());

        // When - call protected method directly (same package)
        logProcessorService.detectAnomaliesAsync(logId, logEntry);

        // Then
        verify(mlServiceClient).predictAnomaly(logId, logEntry);
        verify(anomalyDetectionRepository).save(any(AnomalyDetection.class));
        assertEquals(true, logEntry.getMetadata().get("anomalyDetected"));
        assertEquals(0.85, logEntry.getMetadata().get("anomalyScore"));
    }

    @Test
    void testDetectAnomaliesAsync_WithNullPrediction() {
        // Given
        String logId = "log-123";
        LogEntryDTO logEntry = LogEntryDTO.builder()
                .message("Test message")
                .build();

        when(mlServiceClient.predictAnomaly(eq(logId), any(LogEntryDTO.class))).thenReturn(null);

        // When - call protected method directly (same package)
        logProcessorService.detectAnomaliesAsync(logId, logEntry);

        // Then
        verify(mlServiceClient).predictAnomaly(logId, logEntry);
        verify(anomalyDetectionRepository, never()).save(any());
    }

    @Test
    void testDetectAnomaliesAsync_WithHighConfidenceAnomaly() throws JsonProcessingException {
        // Given
        String logId = "log-123";
        LogEntryDTO logEntry = LogEntryDTO.builder()
                .message("Test message")
                .level("ERROR")
                .service("test")
                .metadata(new HashMap<>())
                .build();

        MLPredictionResponse prediction = MLPredictionResponse.builder()
                .isAnomaly(true)
                .anomalyScore(0.95)
                .confidence(0.85)
                .modelVersion("v1.0")
                .build();

        when(mlServiceClient.predictAnomaly(eq(logId), any(LogEntryDTO.class))).thenReturn(prediction);
        when(objectMapper.writeValueAsString(any(Map.class))).thenReturn("{}");
        when(anomalyDetectionRepository.save(any(AnomalyDetection.class))).thenReturn(new AnomalyDetection());

        // When - call protected method directly (same package)
        logProcessorService.detectAnomaliesAsync(logId, logEntry);

        // Then
        verify(anomalyDetectionRepository).save(any(AnomalyDetection.class));
        // High confidence anomaly should trigger alert log (confidence > 0.7)
    }

    @Test
    void testDetectAnomaliesAsync_WithNullMetadataAndPrediction() throws JsonProcessingException {
        // Given - logEntry has null metadata, prediction is non-null (covers logEntry.getMetadata() == null branch)
        String logId = "log-123";
        LogEntryDTO logEntry = LogEntryDTO.builder()
                .message("Test message")
                .level("ERROR")
                .service("test")
                .metadata(null)
                .build();

        MLPredictionResponse prediction = MLPredictionResponse.builder()
                .isAnomaly(true)
                .anomalyScore(0.80)
                .confidence(0.75)
                .modelVersion("v1.0")
                .build();

        when(mlServiceClient.predictAnomaly(eq(logId), any(LogEntryDTO.class))).thenReturn(prediction);
        when(objectMapper.writeValueAsString(any(Map.class))).thenReturn("{}");
        when(anomalyDetectionRepository.save(any(AnomalyDetection.class))).thenReturn(new AnomalyDetection());

        // When
        logProcessorService.detectAnomaliesAsync(logId, logEntry);

        // Then - metadata should be initialized and populated
        assertNotNull(logEntry.getMetadata());
        assertEquals(true, logEntry.getMetadata().get("anomalyDetected"));
        assertEquals(0.80, logEntry.getMetadata().get("anomalyScore"));
    }

    @Test
    void testDetectAnomaliesAsync_WithException() {
        // Given
        String logId = "log-123";
        LogEntryDTO logEntry = LogEntryDTO.builder().build();

        when(mlServiceClient.predictAnomaly(eq(logId), any(LogEntryDTO.class)))
                .thenThrow(new RuntimeException("ML service error"));

        // When - call protected method directly (same package)
        // Should not throw exception, just log error
        assertDoesNotThrow(() -> logProcessorService.detectAnomaliesAsync(logId, logEntry));

        // Then
        verify(mlServiceClient).predictAnomaly(logId, logEntry);
    }

    @Test
    void testSaveAnomalyDetection_Success() throws JsonProcessingException {
        // Given
        String logId = "log-123";
        LogEntryDTO logEntry = LogEntryDTO.builder()
                .message("Test message")
                .level("ERROR")
                .service("test-service")
                .metadata(new HashMap<>())
                .build();

        MLPredictionResponse prediction = MLPredictionResponse.builder()
                .isAnomaly(true)
                .anomalyScore(0.80)
                .confidence(0.70)
                .modelVersion("v1.0")
                .build();

        when(mlServiceClient.predictAnomaly(eq(logId), any(LogEntryDTO.class))).thenReturn(prediction);
        when(objectMapper.writeValueAsString(any(Map.class))).thenReturn("{\"features\":\"test\"}");
        when(anomalyDetectionRepository.save(any(AnomalyDetection.class))).thenReturn(new AnomalyDetection());

        // When - call protected method directly (same package)
        logProcessorService.detectAnomaliesAsync(logId, logEntry);

        // Then
        ArgumentCaptor<AnomalyDetection> captor = ArgumentCaptor.forClass(AnomalyDetection.class);
        verify(anomalyDetectionRepository).save(captor.capture());
        
        AnomalyDetection saved = captor.getValue();
        assertEquals(logId, saved.getLogId());
        assertEquals(0.80, saved.getAnomalyScore());
        assertEquals(true, saved.getIsAnomaly());
        assertEquals(0.70, saved.getConfidence());
        assertEquals("v1.0", saved.getModelVersion());
    }

    @Test
    void testSaveAnomalyDetection_JsonProcessingException() throws JsonProcessingException {
        // Given
        String logId = "log-123";
        LogEntryDTO logEntry = LogEntryDTO.builder()
                .message("Test")
                .level("ERROR")
                .service("test")
                .metadata(new HashMap<>())
                .build();

        MLPredictionResponse prediction = MLPredictionResponse.builder()
                .isAnomaly(false)
                .anomalyScore(0.50)
                .confidence(0.60)
                .modelVersion("v1.0")
                .build();

        when(mlServiceClient.predictAnomaly(eq(logId), any(LogEntryDTO.class))).thenReturn(prediction);
        when(objectMapper.writeValueAsString(any(Map.class)))
                .thenThrow(new JsonProcessingException("JSON error") {});

        // When - call protected method directly (same package)
        // Should handle exception gracefully
        assertDoesNotThrow(() -> logProcessorService.detectAnomaliesAsync(logId, logEntry));

        // Then - should handle exception gracefully
        verify(anomalyDetectionRepository, never()).save(any());
    }

    @Test
    void testNormalizeLog_WithNullLevel() {
        // Given
        LogEntryDTO logEntry = LogEntryDTO.builder()
                .message("test")
                .service("test")
                .build();

        when(elasticsearchService.indexLog(any(LogEntryDTO.class))).thenReturn("doc-1");
        when(mlServiceClient.predictAnomaly(anyString(), any(LogEntryDTO.class))).thenReturn(null);

        // When
        logProcessorService.processLog(logEntry);

        // Then - level remains null if it was null (normalization only uppercases existing levels)
        assertNull(logEntry.getLevel(), "Level should remain null when not provided");
        assertNotNull(logEntry.getTimestamp(), "Timestamp should be set during normalization");
        verify(elasticsearchService).indexLog(any(LogEntryDTO.class));
    }

    @Test
    void testEnrichLog_WithNullMessage() {
        // Given
        LogEntryDTO logEntry = LogEntryDTO.builder()
                .level("INFO")
                .service("test")
                .message(null)
                .build();

        when(elasticsearchService.indexLog(any(LogEntryDTO.class))).thenReturn("doc-1");
        when(mlServiceClient.predictAnomaly(anyString(), any(LogEntryDTO.class))).thenReturn(null);

        // When
        logProcessorService.processLog(logEntry);

        // Then
        assertNotNull(logEntry.getMetadata());
        assertTrue(logEntry.getMetadata().containsKey("processedAt"));
    }

    @Test
    void testEnrichLog_WithEmptyMessage() {
        // Given
        LogEntryDTO logEntry = LogEntryDTO.builder()
                .level("INFO")
                .service("test")
                .message("")
                .build();

        when(elasticsearchService.indexLog(any(LogEntryDTO.class))).thenReturn("doc-1");
        when(mlServiceClient.predictAnomaly(anyString(), any(LogEntryDTO.class))).thenReturn(null);

        // When
        logProcessorService.processLog(logEntry);

        // Then
        assertNotNull(logEntry.getMetadata());
        assertEquals(0, logEntry.getMetadata().get("messageLength"));
    }

    @Test
    void testDetectAnomaliesAsync_WithLowConfidence() throws JsonProcessingException {
        // Given
        String logId = "log-123";
        LogEntryDTO logEntry = LogEntryDTO.builder()
                .message("Test message")
                .level("ERROR")
                .service("test")
                .metadata(new HashMap<>())
                .build();

        MLPredictionResponse prediction = MLPredictionResponse.builder()
                .isAnomaly(true)
                .anomalyScore(0.60)
                .confidence(0.50) // Low confidence
                .modelVersion("v1.0")
                .build();

        when(mlServiceClient.predictAnomaly(eq(logId), any(LogEntryDTO.class))).thenReturn(prediction);
        when(objectMapper.writeValueAsString(any(Map.class))).thenReturn("{}");
        when(anomalyDetectionRepository.save(any(AnomalyDetection.class))).thenReturn(new AnomalyDetection());

        // When
        logProcessorService.detectAnomaliesAsync(logId, logEntry);

        // Then
        verify(anomalyDetectionRepository).save(any(AnomalyDetection.class));
        // Low confidence should not trigger alert (confidence > 0.7 is false)
    }

    @Test
    void testDetectAnomaliesAsync_WithConfidenceExactly07() throws JsonProcessingException {
        // Given - anomaly with confidence exactly 0.7 (boundary: > 0.7 is false, no alert)
        String logId = "log-123";
        LogEntryDTO logEntry = LogEntryDTO.builder()
                .message("Test message")
                .level("ERROR")
                .service("test")
                .metadata(new HashMap<>())
                .build();

        MLPredictionResponse prediction = MLPredictionResponse.builder()
                .isAnomaly(true)
                .anomalyScore(0.75)
                .confidence(0.70) // Exactly 0.7 - does not trigger high-confidence alert
                .modelVersion("v1.0")
                .build();

        when(mlServiceClient.predictAnomaly(eq(logId), any(LogEntryDTO.class))).thenReturn(prediction);
        when(objectMapper.writeValueAsString(any(Map.class))).thenReturn("{}");
        when(anomalyDetectionRepository.save(any(AnomalyDetection.class))).thenReturn(new AnomalyDetection());

        // When
        logProcessorService.detectAnomaliesAsync(logId, logEntry);

        // Then - saves anomaly but does NOT trigger high-confidence log (confidence > 0.7 is false)
        verify(anomalyDetectionRepository).save(any(AnomalyDetection.class));
    }

    @Test
    void testDetectAnomaliesAsync_WithNonAnomaly() throws JsonProcessingException {
        // Given
        String logId = "log-123";
        LogEntryDTO logEntry = LogEntryDTO.builder()
                .message("Test message")
                .level("INFO")
                .service("test")
                .metadata(new HashMap<>())
                .build();

        MLPredictionResponse prediction = MLPredictionResponse.builder()
                .isAnomaly(false)
                .anomalyScore(0.30)
                .confidence(0.80)
                .modelVersion("v1.0")
                .build();

        when(mlServiceClient.predictAnomaly(eq(logId), any(LogEntryDTO.class))).thenReturn(prediction);
        when(objectMapper.writeValueAsString(any(Map.class))).thenReturn("{}");
        when(anomalyDetectionRepository.save(any(AnomalyDetection.class))).thenReturn(new AnomalyDetection());

        // When
        logProcessorService.detectAnomaliesAsync(logId, logEntry);

        // Then
        verify(anomalyDetectionRepository).save(any(AnomalyDetection.class));
        assertEquals(false, logEntry.getMetadata().get("anomalyDetected"));
    }

    @Test
    void testEnrichLog_WithConnectKeyword() {
        // Given - message contains "connect" but not "connection" (covers CONNECT_KEYWORD path)
        LogEntryDTO logEntry = LogEntryDTO.builder()
                .level("ERROR")
                .message("Failed to connect to database")
                .service("db-service")
                .build();

        when(elasticsearchService.indexLog(any(LogEntryDTO.class))).thenReturn("doc-1");
        when(mlServiceClient.predictAnomaly(anyString(), any(LogEntryDTO.class))).thenReturn(null);

        // When
        logProcessorService.processLog(logEntry);

        // Then
        assertTrue((Boolean) logEntry.getMetadata().get("hasConnection"),
                "Message with 'connect' should set hasConnection=true");
    }

    @Test
    void testSaveAnomalyDetection_WithRepositoryException() throws JsonProcessingException {
        // Given
        String logId = "log-123";
        LogEntryDTO logEntry = LogEntryDTO.builder()
                .message("Test")
                .level("ERROR")
                .service("test")
                .metadata(new HashMap<>())
                .build();

        MLPredictionResponse prediction = MLPredictionResponse.builder()
                .isAnomaly(true)
                .anomalyScore(0.80)
                .confidence(0.70)
                .modelVersion("v1.0")
                .build();

        when(mlServiceClient.predictAnomaly(eq(logId), any(LogEntryDTO.class))).thenReturn(prediction);
        when(objectMapper.writeValueAsString(any(Map.class))).thenReturn("{}");
        when(anomalyDetectionRepository.save(any(AnomalyDetection.class)))
                .thenThrow(new RuntimeException("Database error"));

        // When - should handle exception gracefully
        assertDoesNotThrow(() -> logProcessorService.detectAnomaliesAsync(logId, logEntry));

        // Then
        verify(anomalyDetectionRepository).save(any(AnomalyDetection.class));
    }
}
