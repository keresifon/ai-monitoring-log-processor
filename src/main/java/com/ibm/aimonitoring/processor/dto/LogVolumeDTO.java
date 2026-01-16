package com.ibm.aimonitoring.processor.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * DTO for log volume over time
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LogVolumeDTO {
    
    /**
     * Timestamp
     */
    private Instant timestamp;
    
    /**
     * Number of logs at this timestamp
     */
    private long count;
}

// Made with Bob
