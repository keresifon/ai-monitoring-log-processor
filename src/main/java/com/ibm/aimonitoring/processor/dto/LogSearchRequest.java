package com.ibm.aimonitoring.processor.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * DTO for log search requests
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LogSearchRequest {
    
    /**
     * Free-text search query
     */
    private String query;
    
    /**
     * Filter by log level (ERROR, WARN, INFO, DEBUG, TRACE)
     */
    private List<String> levels;
    
    /**
     * Filter by service name
     */
    private List<String> services;
    
    /**
     * Filter by host
     */
    private List<String> hosts;
    
    /**
     * Start time for time range filter (ISO 8601 format)
     */
    private Instant startTime;
    
    /**
     * End time for time range filter (ISO 8601 format)
     */
    private Instant endTime;
    
    /**
     * Page number (0-based)
     */
    @Builder.Default
    private int page = 0;
    
    /**
     * Page size (max 100)
     */
    @Builder.Default
    private int size = 20;
    
    /**
     * Sort field (timestamp, level, service, etc.)
     */
    @Builder.Default
    private String sortBy = "timestamp";
    
    /**
     * Sort direction (asc or desc)
     */
    @Builder.Default
    private String sortOrder = "desc";
}

// Made with Bob