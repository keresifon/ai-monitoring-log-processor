package com.ibm.aimonitoring.processor.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for ML service prediction
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MLPredictionRequest {
    private String logId;
    private LogFeatures features;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LogFeatures {
        private Integer messageLength;
        private String level;
        private String service;
        private Boolean hasException;
        private Boolean hasTimeout;
        private Boolean hasConnectionError;
    }
}

// Made with Bob
