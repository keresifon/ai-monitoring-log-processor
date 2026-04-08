package com.ibm.aimonitoring.processor.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibm.aimonitoring.processor.dto.LogEntryDTO;
import com.ibm.aimonitoring.processor.dto.LogSearchRequest;
import com.ibm.aimonitoring.processor.dto.LogSearchResponse;
import com.ibm.aimonitoring.processor.service.ElasticsearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

/**
 * REST controller for log search, export, and filter helpers.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/logs")
@RequiredArgsConstructor
public class LogSearchController {

    private static final int EXPORT_MAX_ROWS = 10_000;

    private final ElasticsearchService elasticsearchService;
    private final ObjectMapper objectMapper;

    /**
     * Search logs with filters and pagination.
     * Accepts both API-style params (query, startTime, endTime) and UI params (searchText, startDate, endDate).
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
            @RequestParam(required = false) String searchText,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endTime,
            @RequestParam(required = false) String endDate) {

        LogSearchRequest request = buildSearchRequest(
                page, size, sortBy, sortDirection, level, service,
                firstNonBlank(query, searchText),
                firstNonBlank(startTime, startDate),
                firstNonBlank(endTime, endDate));

        LogSearchResponse response = elasticsearchService.searchLogs(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Distinct service names for filter autocomplete (from Elasticsearch terms agg).
     */
    @GetMapping("/services")
    public ResponseEntity<List<String>> listServiceNames() {
        List<String> names = elasticsearchService.getTopServices(500).stream()
                .map(dto -> dto.getService())
                .sorted()
                .distinct()
                .toList();
        return ResponseEntity.ok(names);
    }

    /**
     * Export matching logs as CSV (bounded by {@link #EXPORT_MAX_ROWS}).
     */
    @GetMapping(value = "/export/csv", produces = "text/csv")
    public ResponseEntity<byte[]> exportCsv(
            @RequestParam(required = false) String level,
            @RequestParam(required = false) String service,
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String searchText,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endTime,
            @RequestParam(required = false) String endDate) {

        LogSearchRequest request = buildSearchRequest(
                0, EXPORT_MAX_ROWS, "timestamp", "desc", level, service,
                firstNonBlank(query, searchText),
                firstNonBlank(startTime, startDate),
                firstNonBlank(endTime, endDate));

        LogSearchResponse response = elasticsearchService.searchLogs(request);
        byte[] body = toCsv(response.getLogs());

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"logs.csv\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(body);
    }

    /**
     * Export matching logs as JSON array (bounded by {@link #EXPORT_MAX_ROWS}).
     */
    @GetMapping(value = "/export/json", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<byte[]> exportJson(
            @RequestParam(required = false) String level,
            @RequestParam(required = false) String service,
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String searchText,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endTime,
            @RequestParam(required = false) String endDate) throws Exception {

        LogSearchRequest request = buildSearchRequest(
                0, EXPORT_MAX_ROWS, "timestamp", "desc", level, service,
                firstNonBlank(query, searchText),
                firstNonBlank(startTime, startDate),
                firstNonBlank(endTime, endDate));

        LogSearchResponse response = elasticsearchService.searchLogs(request);
        byte[] body = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(response.getLogs());

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"logs.json\"")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body);
    }

    private static LogSearchRequest buildSearchRequest(
            int page,
            int size,
            String sortBy,
            String sortDirection,
            String level,
            String service,
            String textQuery,
            String startInstant,
            String endInstant) {

        LogSearchRequest.LogSearchRequestBuilder builder = LogSearchRequest.builder()
                .page(page)
                .size(size)
                .sortBy(sortBy)
                .sortOrder(sortDirection)
                .query(textQuery);

        if (level != null && !level.isEmpty()) {
            builder.levels(Arrays.asList(level.split(",")));
        }
        if (service != null && !service.isEmpty()) {
            builder.services(Arrays.asList(service.split(",")));
        }
        Instant start = parseInstant(startInstant);
        Instant end = parseInstant(endInstant);
        if (start != null) {
            builder.startTime(start);
        }
        if (end != null) {
            builder.endTime(end);
        }
        return builder.build();
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        if (b != null && !b.isBlank()) {
            return b;
        }
        return null;
    }

    private static Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return Instant.parse(value);
    }

    private static byte[] toCsv(List<LogEntryDTO> logs) {
        StringBuilder sb = new StringBuilder();
        sb.append("timestamp,level,service,message,host,environment,traceId,spanId\n");
        for (LogEntryDTO e : logs) {
            sb.append(csvEscape(e.getTimestamp() != null ? e.getTimestamp().toString() : ""));
            sb.append(',');
            sb.append(csvEscape(e.getLevel()));
            sb.append(',');
            sb.append(csvEscape(e.getService()));
            sb.append(',');
            sb.append(csvEscape(e.getMessage()));
            sb.append(',');
            sb.append(csvEscape(e.getHost()));
            sb.append(',');
            sb.append(csvEscape(e.getEnvironment()));
            sb.append(',');
            sb.append(csvEscape(e.getTraceId()));
            sb.append(',');
            sb.append(csvEscape(e.getSpanId()));
            sb.append('\n');
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static String csvEscape(String s) {
        if (s == null) {
            return "";
        }
        if (s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }
}
