package com.ibm.aimonitoring.processor.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO from ML service prediction
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MLPredictionResponse {
    private String logId;
    private Boolean isAnomaly;
    private Double anomalyScore;
    private Double confidence;
    private String timestamp;
    private String modelVersion;
}

// Made with Bob
