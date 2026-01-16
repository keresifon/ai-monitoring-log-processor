package com.ibm.aimonitoring.processor.repository;

import com.ibm.aimonitoring.processor.model.AnomalyDetection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Repository for anomaly detection results
 */
@Repository
public interface AnomalyDetectionRepository extends JpaRepository<AnomalyDetection, Long> {

    /**
     * Find anomaly detection by log ID
     */
    Optional<AnomalyDetection> findByLogId(String logId);

    /**
     * Find all anomalies detected within a time range
     */
    @Query("SELECT a FROM AnomalyDetection a WHERE a.isAnomaly = true AND a.detectedAt BETWEEN :startTime AND :endTime ORDER BY a.detectedAt DESC")
    List<AnomalyDetection> findAnomaliesBetween(@Param("startTime") Instant startTime, @Param("endTime") Instant endTime);

    /**
     * Find recent anomalies (last N records)
     */
    @Query("SELECT a FROM AnomalyDetection a WHERE a.isAnomaly = true ORDER BY a.detectedAt DESC")
    List<AnomalyDetection> findRecentAnomalies();

    /**
     * Count anomalies in time range
     */
    @Query("SELECT COUNT(a) FROM AnomalyDetection a WHERE a.isAnomaly = true AND a.detectedAt BETWEEN :startTime AND :endTime")
    Long countAnomaliesBetween(@Param("startTime") Instant startTime, @Param("endTime") Instant endTime);

    /**
     * Find high confidence anomalies (confidence > threshold)
     */
    @Query("SELECT a FROM AnomalyDetection a WHERE a.isAnomaly = true AND a.confidence > :threshold ORDER BY a.confidence DESC, a.detectedAt DESC")
    List<AnomalyDetection> findHighConfidenceAnomalies(@Param("threshold") Double threshold);
    
    /**
     * Find anomalies detected after a specific time
     */
    @Query("SELECT a FROM AnomalyDetection a WHERE a.isAnomaly = true AND a.detectedAt > :startTime ORDER BY a.detectedAt DESC")
    List<AnomalyDetection> findByDetectedAtAfterOrderByDetectedAtDesc(@Param("startTime") Instant startTime);
}

// Made with Bob
