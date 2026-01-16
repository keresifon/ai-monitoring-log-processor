package com.ibm.aimonitoring.processor.controller;

import com.ibm.aimonitoring.processor.dto.*;
import com.ibm.aimonitoring.processor.model.AnomalyDetection;
import com.ibm.aimonitoring.processor.repository.AnomalyDetectionRepository;
import com.ibm.aimonitoring.processor.service.ElasticsearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * REST controller for dashboard metrics and analytics
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final ElasticsearchService elasticsearchService;
    private final AnomalyDetectionRepository anomalyDetectionRepository;

    /**
     * Get dashboard metrics summary
     */
    @GetMapping("/metrics")
    public ResponseEntity<DashboardMetricsDTO> getMetrics() {
        log.info("Fetching dashboard metrics");
        
        try {
            DashboardMetricsDTO metrics = elasticsearchService.getDashboardMetrics();
            return ResponseEntity.ok(metrics);
        } catch (Exception e) {
            log.error("Error fetching dashboard metrics", e);
            // Return empty metrics instead of error
            return ResponseEntity.ok(DashboardMetricsDTO.builder()
                    .totalLogs(0)
                    .errorCount(0)
                    .warningCount(0)
                    .activeAlerts(0)
                    .anomalyCount(0)
                    .logsPerMinute(0.0)
                    .errorRate(0.0)
                    .build());
        }
    }

    /**
     * Get log volume over time
     */
    @GetMapping("/log-volume")
    public ResponseEntity<List<LogVolumeDTO>> getLogVolume(
            @RequestParam(defaultValue = "24") int hours) {
        log.info("Fetching log volume for last {} hours", hours);
        
        try {
            Instant endTime = Instant.now();
            Instant startTime = endTime.minus(hours, ChronoUnit.HOURS);
            
            List<LogVolumeDTO> volume = elasticsearchService.getLogVolume(startTime, endTime);
            return ResponseEntity.ok(volume);
        } catch (Exception e) {
            log.error("Error fetching log volume", e);
            return ResponseEntity.ok(List.of());
        }
    }

    /**
     * Get log level distribution
     */
    @GetMapping("/log-level-distribution")
    public ResponseEntity<List<LogLevelDistributionDTO>> getLogLevelDistribution() {
        log.info("Fetching log level distribution");
        
        try {
            List<LogLevelDistributionDTO> distribution = elasticsearchService.getLogLevelDistribution();
            return ResponseEntity.ok(distribution);
        } catch (Exception e) {
            log.error("Error fetching log level distribution", e);
            return ResponseEntity.ok(List.of());
        }
    }

    /**
     * Get top services by log count
     */
    @GetMapping("/top-services")
    public ResponseEntity<List<ServiceLogCountDTO>> getTopServices(
            @RequestParam(defaultValue = "10") int limit) {
        log.info("Fetching top {} services", limit);
        
        try {
            List<ServiceLogCountDTO> services = elasticsearchService.getTopServices(limit);
            return ResponseEntity.ok(services);
        } catch (Exception e) {
            log.error("Error fetching top services", e);
            return ResponseEntity.ok(List.of());
        }
    }

    /**
     * Get anomalies timeline
     */
    @GetMapping("/anomalies")
    public ResponseEntity<List<AnomalyDetection>> getAnomalies(
            @RequestParam(defaultValue = "24") int hours) {
        log.info("Fetching anomalies for last {} hours", hours);
        
        try {
            Instant startTime = Instant.now().minus(hours, ChronoUnit.HOURS);
            List<AnomalyDetection> anomalies = anomalyDetectionRepository
                    .findByDetectedAtAfterOrderByDetectedAtDesc(startTime);
            return ResponseEntity.ok(anomalies);
        } catch (Exception e) {
            log.error("Error fetching anomalies", e);
            return ResponseEntity.ok(List.of());
        }
    }

    /**
     * Get recent alerts (placeholder - would integrate with alert service)
     */
    @GetMapping("/recent-alerts")
    public ResponseEntity<List<Object>> getRecentAlerts(
            @RequestParam(defaultValue = "10") int limit) {
        log.info("Fetching recent {} alerts", limit);
        
        // This would typically call the alert service
        // For now, return empty list
        return ResponseEntity.ok(List.of());
    }
}

// Made with Bob
