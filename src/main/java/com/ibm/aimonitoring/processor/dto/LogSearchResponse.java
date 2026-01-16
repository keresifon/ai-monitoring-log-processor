package com.ibm.aimonitoring.processor.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * DTO for log search responses
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LogSearchResponse {
    
    /**
     * List of log entries matching the search criteria
     */
    private List<LogEntryDTO> logs;
    
    /**
     * Total number of matching logs
     */
    private long total;
    
    /**
     * Current page number
     */
    private int page;
    
    /**
     * Page size
     */
    private int size;
}

// Made with Bob