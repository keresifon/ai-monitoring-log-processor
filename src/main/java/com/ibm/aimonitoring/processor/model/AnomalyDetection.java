package com.ibm.aimonitoring.processor.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Entity for storing anomaly detection results
 */
@Entity
@Table(name = "anomaly_detections", schema = "ml_service")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnomalyDetection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "model_id")
    private Long modelId;

    @Column(name = "log_id", nullable = false)
    private String logId;

    @Column(name = "anomaly_score", nullable = false)
    private Double anomalyScore;

    @Column(name = "is_anomaly", nullable = false)
    private Boolean isAnomaly;

    @Column(name = "features", columnDefinition = "jsonb")
    private String features;

    @Column(name = "detected_at", nullable = false)
    private Instant detectedAt;

    @Column(name = "model_version")
    private String modelVersion;

    @Column(name = "confidence")
    private Double confidence;

    @PrePersist
    protected void onCreate() {
        if (detectedAt == null) {
            detectedAt = Instant.now();
        }
    }
}

// Made with Bob
