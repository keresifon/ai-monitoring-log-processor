package com.ibm.aimonitoring.processor.service;

import com.ibm.aimonitoring.processor.dto.LogEntryDTO;
import com.ibm.aimonitoring.processor.dto.MLPredictionRequest;
import com.ibm.aimonitoring.processor.dto.MLPredictionResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class MLServiceClientTest {

    private MLServiceClient mlServiceClient;
    private LogEntryDTO testLogEntry;

    @BeforeEach
    void setUp() {
        mlServiceClient = new MLServiceClient(WebClient.builder());
        ReflectionTestUtils.setField(mlServiceClient, "mlServiceUrl", "http://localhost:8000");
        ReflectionTestUtils.setField(mlServiceClient, "timeout", 5000);
        ReflectionTestUtils.setField(mlServiceClient, "maxRetryAttempts", 3);

        testLogEntry = LogEntryDTO.builder()
                .message("Connection timeout exception occurred")
                .level("ERROR")
                .service("api-gateway")
                .build();
    }

    @Test
    void testExtractFeatures_WithExceptionKeywords() {
        // Given
        LogEntryDTO logEntry = LogEntryDTO.builder()
                .message("Database connection exception error")
                .level("error")
                .service("db-service")
                .build();

        // When
        MLPredictionRequest.LogFeatures features = extractFeaturesUsingReflection(logEntry);

        // Then
        assertNotNull(features);
        assertTrue(features.getHasException());
        assertEquals("ERROR", features.getLevel());
        assertEquals("db-service", features.getService());
    }

    @Test
    void testExtractFeatures_WithTimeoutKeywords() {
        // Given
        LogEntryDTO logEntry = LogEntryDTO.builder()
                .message("Request timed out after 30 seconds")
                .level("WARN")
                .service("api-service")
                .build();

        // When
        MLPredictionRequest.LogFeatures features = extractFeaturesUsingReflection(logEntry);

        // Then
        assertNotNull(features);
        assertTrue(features.getHasTimeout());
    }

    @Test
    void testExtractFeatures_WithConnectionError() {
        // Given
        LogEntryDTO logEntry = LogEntryDTO.builder()
                .message("Connection refused to database server")
                .level("ERROR")
                .service("db-service")
                .build();

        // When
        MLPredictionRequest.LogFeatures features = extractFeaturesUsingReflection(logEntry);

        // Then
        assertNotNull(features);
        assertTrue(features.getHasConnectionError());
    }

    @Test
    void testExtractFeatures_WithNullValues() {
        // Given
        LogEntryDTO logEntry = LogEntryDTO.builder()
                .message(null)
                .level(null)
                .service(null)
                .build();

        // When
        MLPredictionRequest.LogFeatures features = extractFeaturesUsingReflection(logEntry);

        // Then
        assertNotNull(features);
        assertEquals(0, features.getMessageLength());
        assertEquals("INFO", features.getLevel());
        assertEquals("unknown", features.getService());
    }

    @Test
    void testPredictAnomaly_ServiceUnavailable_ReturnsNull() {
        // Given - ML service is not available (will timeout or fail)
        String logId = "log-123";
        // Using real WebClient which will fail to connect to non-existent service
        
        // When
        MLPredictionResponse result = mlServiceClient.predictAnomaly(logId, testLogEntry);

        // Then - should return null gracefully
        assertNull(result);
    }

    @Test
    void testIsServiceAvailable_ServiceUnavailable_ReturnsFalse() {
        // Given - ML service is not available
        
        // When
        boolean result = mlServiceClient.isServiceAvailable();

        // Then - should return false when service is unavailable
        assertFalse(result);
    }

    // Helper method to test private extractFeatures method using reflection
    private MLPredictionRequest.LogFeatures extractFeaturesUsingReflection(LogEntryDTO logEntry) {
        try {
            java.lang.reflect.Method method = MLServiceClient.class.getDeclaredMethod("extractFeatures", LogEntryDTO.class);
            method.setAccessible(true);
            return (MLPredictionRequest.LogFeatures) method.invoke(mlServiceClient, logEntry);
        } catch (Exception e) {
            throw new RuntimeException("Failed to invoke extractFeatures", e);
        }
    }
}
