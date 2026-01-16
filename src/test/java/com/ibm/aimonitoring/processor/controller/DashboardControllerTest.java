package com.ibm.aimonitoring.processor.controller;

import com.ibm.aimonitoring.processor.dto.*;
import com.ibm.aimonitoring.processor.model.AnomalyDetection;
import com.ibm.aimonitoring.processor.repository.AnomalyDetectionRepository;
import com.ibm.aimonitoring.processor.service.ElasticsearchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DashboardControllerTest {

    @Mock
    private ElasticsearchService elasticsearchService;

    @Mock
    private AnomalyDetectionRepository anomalyDetectionRepository;

    @InjectMocks
    private DashboardController dashboardController;

    @BeforeEach
    void setUp() {
        dashboardController = new DashboardController(elasticsearchService, anomalyDetectionRepository);
    }

    @Test
    void testGetMetrics_Success() {
        // Given
        DashboardMetricsDTO expectedMetrics = DashboardMetricsDTO.builder()
                .totalLogs(1000L)
                .errorCount(50L)
                .warningCount(100L)
                .activeAlerts(5L)
                .anomalyCount(10L)
                .logsPerMinute(100.0)
                .errorRate(5.0)
                .build();

        when(elasticsearchService.getDashboardMetrics()).thenReturn(expectedMetrics);

        // When
        ResponseEntity<DashboardMetricsDTO> response = dashboardController.getMetrics();

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(1000L, response.getBody().getTotalLogs());
        assertEquals(50L, response.getBody().getErrorCount());
        verify(elasticsearchService).getDashboardMetrics();
    }

    @Test
    void testGetMetrics_ServiceUnavailable() {
        // Given
        when(elasticsearchService.getDashboardMetrics())
                .thenThrow(new RuntimeException("Elasticsearch error"));

        // When
        ResponseEntity<DashboardMetricsDTO> response = dashboardController.getMetrics();

        // Then
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(0L, response.getBody().getTotalLogs());
        assertEquals(0.0, response.getBody().getErrorRate());
    }

    @Test
    void testGetLogVolume_Success() {
        // Given
        int hours = 24;
        List<LogVolumeDTO> expectedVolume = List.of(
                LogVolumeDTO.builder()
                        .timestamp(Instant.now())
                        .count(100L)
                        .build()
        );

        when(elasticsearchService.getLogVolume(any(Instant.class), any(Instant.class)))
                .thenReturn(expectedVolume);

        // When
        ResponseEntity<List<LogVolumeDTO>> response = dashboardController.getLogVolume(hours);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().size());
        verify(elasticsearchService).getLogVolume(any(Instant.class), any(Instant.class));
    }

    @Test
    void testGetLogVolume_ServiceUnavailable() {
        // Given
        when(elasticsearchService.getLogVolume(any(Instant.class), any(Instant.class)))
                .thenThrow(new RuntimeException("Elasticsearch error"));

        // When
        ResponseEntity<List<LogVolumeDTO>> response = dashboardController.getLogVolume(24);

        // Then
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertTrue(response.getBody().isEmpty());
    }

    @Test
    void testGetLogLevelDistribution_Success() {
        // Given
        List<LogLevelDistributionDTO> expectedDistribution = List.of(
                LogLevelDistributionDTO.builder()
                        .level("ERROR")
                        .count(50L)
                        .percentage(5.0)
                        .build()
        );

        when(elasticsearchService.getLogLevelDistribution()).thenReturn(expectedDistribution);

        // When
        ResponseEntity<List<LogLevelDistributionDTO>> response = 
                dashboardController.getLogLevelDistribution();

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().size());
        verify(elasticsearchService).getLogLevelDistribution();
    }

    @Test
    void testGetLogLevelDistribution_ServiceUnavailable() {
        // Given
        when(elasticsearchService.getLogLevelDistribution())
                .thenThrow(new RuntimeException("Elasticsearch error"));

        // When
        ResponseEntity<List<LogLevelDistributionDTO>> response = 
                dashboardController.getLogLevelDistribution();

        // Then
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertTrue(response.getBody().isEmpty());
    }

    @Test
    void testGetTopServices_Success() {
        // Given
        int limit = 10;
        List<ServiceLogCountDTO> expectedServices = List.of(
                ServiceLogCountDTO.builder()
                        .service("api-gateway")
                        .count(500L)
                        .build()
        );

        when(elasticsearchService.getTopServices(limit)).thenReturn(expectedServices);

        // When
        ResponseEntity<List<ServiceLogCountDTO>> response = dashboardController.getTopServices(limit);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().size());
        verify(elasticsearchService).getTopServices(limit);
    }

    @Test
    void testGetTopServices_ServiceUnavailable() {
        // Given
        when(elasticsearchService.getTopServices(anyInt()))
                .thenThrow(new RuntimeException("Elasticsearch error"));

        // When
        ResponseEntity<List<ServiceLogCountDTO>> response = dashboardController.getTopServices(10);

        // Then
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertTrue(response.getBody().isEmpty());
    }

    @Test
    void testGetAnomalies_Success() {
        // Given
        int hours = 24;
        Instant startTime = Instant.now().minus(hours, java.time.temporal.ChronoUnit.HOURS);
        List<AnomalyDetection> expectedAnomalies = List.of(
                AnomalyDetection.builder()
                        .logId("log-123")
                        .isAnomaly(true)
                        .anomalyScore(0.85)
                        .detectedAt(Instant.now())
                        .build()
        );

        when(anomalyDetectionRepository.findByDetectedAtAfterOrderByDetectedAtDesc(any(Instant.class)))
                .thenReturn(expectedAnomalies);

        // When
        ResponseEntity<List<AnomalyDetection>> response = dashboardController.getAnomalies(hours);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().size());
        verify(anomalyDetectionRepository).findByDetectedAtAfterOrderByDetectedAtDesc(any(Instant.class));
    }

    @Test
    void testGetAnomalies_InternalServerError() {
        // Given
        when(anomalyDetectionRepository.findByDetectedAtAfterOrderByDetectedAtDesc(any(Instant.class)))
                .thenThrow(new RuntimeException("Database error"));

        // When
        ResponseEntity<List<AnomalyDetection>> response = dashboardController.getAnomalies(24);

        // Then
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertTrue(response.getBody().isEmpty());
    }

    @Test
    void testGetRecentAlerts() {
        // When
        ResponseEntity<List<Object>> response = dashboardController.getRecentAlerts(10);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isEmpty());
    }
}
