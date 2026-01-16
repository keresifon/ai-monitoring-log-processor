package com.ibm.aimonitoring.processor.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

/**
 * DTO for log entries (matches ingestion service)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LogEntryDTO {

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
    private Instant timestamp;

    private String level;

    private String message;

    private String service;

    private String host;

    private String environment;

    private Map<String, Object> metadata;

    private String traceId;

    private String spanId;
}

// Made with Bob
