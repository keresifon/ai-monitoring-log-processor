package com.ibm.aimonitoring.processor.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for service log counts
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServiceLogCountDTO {
    
    /**
     * Service name
     */
    private String service;
    
    /**
     * Number of logs from this service
     */
    private long count;
}

// Made with Bob
