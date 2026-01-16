package com.ibm.aimonitoring.processor.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for dashboard metrics
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardMetricsDTO {
    
    /**
     * Total number of logs
     */
    private long totalLogs;
    
    /**
     * Number of error logs
     */
    private long errorCount;
    
    /**
     * Number of warning logs
     */
    private long warningCount;
    
    /**
     * Number of active alerts
     */
    private long activeAlerts;
    
    /**
     * Number of anomalies detected
     */
    private long anomalyCount;
    
    /**
     * Average logs per minute
     */
    private double logsPerMinute;
    
    /**
     * Error rate percentage
     */
    private double errorRate;
}

// Made with Bob
