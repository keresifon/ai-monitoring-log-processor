package com.ibm.aimonitoring.processor.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for log level distribution
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LogLevelDistributionDTO {
    
    /**
     * Log level (INFO, WARN, ERROR, etc.)
     */
    private String level;
    
    /**
     * Count of logs at this level
     */
    private long count;
    
    /**
     * Percentage of total logs
     */
    private double percentage;
}

// Made with Bob
