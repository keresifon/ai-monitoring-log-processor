package com.ibm.aimonitoring.processor.controller;

import com.ibm.aimonitoring.processor.dto.LogSearchRequest;
import com.ibm.aimonitoring.processor.dto.LogSearchResponse;
import com.ibm.aimonitoring.processor.service.ElasticsearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * REST controller for log search operations
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/logs")
@RequiredArgsConstructor
public class LogSearchController {

    private final ElasticsearchService elasticsearchService;

    /**
     * Search logs with filters and pagination
     *
     * @param page page number (0-based)
     * @param size page size
     * @param sortBy field to sort by
     * @param sortDirection sort direction (asc/desc)
     * @param level log level filter
     * @param service service name filter
     * @param query message search text
     * @param startTime start time filter (ISO format)
     * @param endTime end time filter (ISO format)
     * @return paginated search results
     */
    @GetMapping("/search")
    public ResponseEntity<LogSearchResponse> searchLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(defaultValue = "timestamp") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDirection,
            @RequestParam(required = false) String level,
            @RequestParam(required = false) String service,
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime
    ) {
        log.debug("Searching logs: page={}, size={}, level={}, service={}",
                  page, size, level, service);

        LogSearchRequest.LogSearchRequestBuilder builder = LogSearchRequest.builder()
                .page(page)
                .size(size)
                .sortBy(sortBy)
                .sortOrder(sortDirection)
                .query(query);

        if (level != null && !level.isEmpty()) {
            builder.levels(Arrays.asList(level.split(",")));
        }
        
        if (service != null && !service.isEmpty()) {
            builder.services(Arrays.asList(service.split(",")));
        }
        
        if (startTime != null && !startTime.isEmpty()) {
            builder.startTime(Instant.parse(startTime));
        }
        
        if (endTime != null && !endTime.isEmpty()) {
            builder.endTime(Instant.parse(endTime));
        }

        LogSearchResponse response = elasticsearchService.searchLogs(builder.build());
        
        return ResponseEntity.ok(response);
    }
}

// Made with Bob
