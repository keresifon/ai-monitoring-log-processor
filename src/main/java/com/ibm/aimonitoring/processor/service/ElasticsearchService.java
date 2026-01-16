package com.ibm.aimonitoring.processor.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.Result;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch.core.IndexResponse;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.indices.CreateIndexResponse;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import co.elastic.clients.transport.endpoints.BooleanResponse;
import co.elastic.clients.json.JsonData;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.json.JsonData;
import com.ibm.aimonitoring.processor.dto.*;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;
import com.ibm.aimonitoring.processor.dto.*;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service for Elasticsearch operations
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ElasticsearchService {

    private static final String FIELD_TIMESTAMP = "timestamp";
    private static final String FIELD_LEVEL = "level";
    private static final String FIELD_MESSAGE = "message";
    private static final String FIELD_SERVICE = "service";
    private static final String FIELD_HOST = "host";
    private static final String FIELD_ENVIRONMENT = "environment";
    private static final String FIELD_TRACE_ID = "traceId";
    private static final String FIELD_SPAN_ID = "spanId";
    private static final String FIELD_METADATA = "metadata";

    private final ElasticsearchClient elasticsearchClient;

    @Value("${elasticsearch.index.name:logs}")
    private String indexName;

    @Value("${elasticsearch.index.shards:1}")
    private int numberOfShards;

    @Value("${elasticsearch.index.replicas:0}")
    private int numberOfReplicas;

    /**
     * Initialize Elasticsearch index on startup
     */
    @PostConstruct
    public void init() {
        try {
            createIndexIfNotExists();
            log.info("Elasticsearch service initialized successfully");
        } catch (Exception e) {
            log.error("Failed to initialize Elasticsearch service: {}", e.getMessage(), e);
        }
    }

    /**
     * Create index if it doesn't exist
     */
    private void createIndexIfNotExists() throws IOException {
        BooleanResponse exists = elasticsearchClient.indices()
                .exists(ExistsRequest.of(e -> e.index(indexName)));

        if (!exists.value()) {
            log.info("Creating Elasticsearch index: {}", indexName);
            
            CreateIndexResponse response = elasticsearchClient.indices()
                    .create(c -> c
                            .index(indexName)
                            .settings(s -> s
                                    .numberOfShards(String.valueOf(numberOfShards))
                                    .numberOfReplicas(String.valueOf(numberOfReplicas))
                            )
                            .mappings(m -> m
                                    .properties(FIELD_TIMESTAMP, p -> p.date(d -> d.format("strict_date_optional_time")))
                                    .properties(FIELD_LEVEL, p -> p.keyword(k -> k))
                                    .properties(FIELD_MESSAGE, p -> p.text(t -> t.analyzer("standard")))
                                    .properties(FIELD_SERVICE, p -> p.keyword(k -> k))
                                    .properties(FIELD_HOST, p -> p.keyword(k -> k))
                                    .properties(FIELD_ENVIRONMENT, p -> p.keyword(k -> k))
                                    .properties(FIELD_TRACE_ID, p -> p.keyword(k -> k))
                                    .properties(FIELD_SPAN_ID, p -> p.keyword(k -> k))
                                    .properties(FIELD_METADATA, p -> p.object(o -> o.enabled(true)))
                            )
                    );

            log.info("Index created: {}, acknowledged: {}", indexName, response.acknowledged());
        } else {
            log.info("Index already exists: {}", indexName);
        }
    }

    /**
     * Index a log entry to Elasticsearch
     *
     * @param logEntry the log entry to index
     * @return the document ID
     */
    public String indexLog(LogEntryDTO logEntry) {
        try {
            // Convert DTO to Map for indexing
            Map<String, Object> document = convertToDocument(logEntry);

            // Index the document
            IndexResponse response = elasticsearchClient.index(i -> i
                    .index(indexName)
                    .document(document)
            );

            if (response.result() == Result.Created || response.result() == Result.Updated) {
                log.debug("Log indexed successfully: {}", response.id());
                return response.id();
            } else {
                log.warn("Unexpected index result: {}", response.result());
                return null;
            }

        } catch (IOException e) {
            log.error("Failed to index log to Elasticsearch: {}", e.getMessage(), e);
            throw new ElasticsearchIndexException("Failed to index log", e);
        }
    }

    /**
     * Convert LogEntryDTO to a Map for Elasticsearch
     */
    private Map<String, Object> convertToDocument(LogEntryDTO logEntry) {
        Map<String, Object> document = new HashMap<>();
        
        document.put(FIELD_TIMESTAMP, logEntry.getTimestamp() != null ?
                logEntry.getTimestamp().toString() : null);
        document.put(FIELD_LEVEL, logEntry.getLevel());
        document.put(FIELD_MESSAGE, logEntry.getMessage());
        document.put(FIELD_SERVICE, logEntry.getService());
        document.put(FIELD_HOST, logEntry.getHost());
        document.put(FIELD_ENVIRONMENT, logEntry.getEnvironment());
        document.put(FIELD_TRACE_ID, logEntry.getTraceId());
        document.put(FIELD_SPAN_ID, logEntry.getSpanId());
        
        if (logEntry.getMetadata() != null) {
            document.put(FIELD_METADATA, logEntry.getMetadata());
        }
        
        return document;
    }


    /**
     * Check if Elasticsearch is available
     */
    public boolean isAvailable() {
        try {
            return elasticsearchClient.ping().value();
        } catch (IOException e) {
            log.error("Elasticsearch ping failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Search logs with filters and pagination
     *
     * @param request the search request
     * @return search response with results
     */
    public LogSearchResponse searchLogs(LogSearchRequest request) {
        try {
            SearchResponse<Map> response = elasticsearchClient.search(s -> {
                // Build the search query
                s.index(indexName)
                 .from(request.getPage() * request.getSize())
                 .size(request.getSize())
                 .sort(sort -> sort.field(f -> f
                     .field(request.getSortBy())
                     .order(request.getSortOrder().equalsIgnoreCase("asc") ? 
                            SortOrder.Asc : SortOrder.Desc)
                 ));

                // Add query filters
                s.query(q -> q.bool(b -> {
                    // Free text search
                    if (request.getQuery() != null && !request.getQuery().isEmpty()) {
                        b.must(m -> m.match(match -> match.field(FIELD_MESSAGE).query(request.getQuery())));
                    }
                    
                    // Level filter
                    if (request.getLevels() != null && !request.getLevels().isEmpty()) {
                        b.filter(f -> f.terms(t -> t.field(FIELD_LEVEL).terms(terms -> 
                            terms.value(request.getLevels().stream()
                                .map(FieldValue::of)
                                .collect(Collectors.toList()))
                        )));
                    }
                    
                    // Service filter
                    if (request.getServices() != null && !request.getServices().isEmpty()) {
                        b.filter(f -> f.terms(t -> t.field(FIELD_SERVICE).terms(terms -> 
                            terms.value(request.getServices().stream()
                                .map(FieldValue::of)
                                .collect(Collectors.toList()))
                        )));
                    }
                    
                    // Time range filter
                    if (request.getStartTime() != null || request.getEndTime() != null) {
                        b.filter(f -> f.range(r -> {
                            r.field(FIELD_TIMESTAMP);
                            if (request.getStartTime() != null) {
                                r.gte(JsonData.of(request.getStartTime().toString()));
                            }
                            if (request.getEndTime() != null) {
                                r.lte(JsonData.of(request.getEndTime().toString()));
                            }
                            return r;
                        }));
                    }
                    
                    return b;
                }));
                
                return s;
            }, Map.class);

            // Convert response to DTO
            List<LogEntryDTO> logs = response.hits().hits().stream()
                .map(hit -> convertToLogEntry(hit.source()))
                .collect(Collectors.toList());

            return LogSearchResponse.builder()
                .logs(logs)
                .total(response.hits().total().value())
                .page(request.getPage())
                .size(request.getSize())
                .build();

        } catch (IOException e) {
            log.error("Failed to search logs: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to search logs", e);
        }
    }

    /**
     * Convert Elasticsearch document to LogEntryDTO
     */
    private LogEntryDTO convertToLogEntry(Map<String, Object> document) {
        LogEntryDTO dto = new LogEntryDTO();
        dto.setTimestamp(document.get(FIELD_TIMESTAMP) != null ? 
            Instant.parse(document.get(FIELD_TIMESTAMP).toString()) : null);
        dto.setLevel(document.get(FIELD_LEVEL) != null ? document.get(FIELD_LEVEL).toString() : null);
        dto.setMessage(document.get(FIELD_MESSAGE) != null ? document.get(FIELD_MESSAGE).toString() : null);
        dto.setService(document.get(FIELD_SERVICE) != null ? document.get(FIELD_SERVICE).toString() : null);
        dto.setHost(document.get(FIELD_HOST) != null ? document.get(FIELD_HOST).toString() : null);
        dto.setEnvironment(document.get(FIELD_ENVIRONMENT) != null ? document.get(FIELD_ENVIRONMENT).toString() : null);
        dto.setTraceId(document.get(FIELD_TRACE_ID) != null ? document.get(FIELD_TRACE_ID).toString() : null);
        dto.setSpanId(document.get(FIELD_SPAN_ID) != null ? document.get(FIELD_SPAN_ID).toString() : null);
        
        @SuppressWarnings("unchecked")
        Map<String, Object> metadata = (Map<String, Object>) document.get(FIELD_METADATA);
        if (metadata != null) {
            dto.setMetadata(metadata);
        }
        
        return dto;
    }

    /**
     * Get dashboard metrics summary
     */
    public DashboardMetricsDTO getDashboardMetrics() {
        try {
            // Get total logs count
            SearchResponse<Map> totalResponse = elasticsearchClient.search(s -> s
                .index(indexName)
                .size(0)
                .query(q -> q.matchAll(m -> m))
            , Map.class);
            
            long totalLogs = totalResponse.hits().total().value();
            
            // Get error count
            SearchResponse<Map> errorResponse = elasticsearchClient.search(s -> s
                .index(indexName)
                .size(0)
                .query(q -> q.term(t -> t.field(FIELD_LEVEL).value("ERROR")))
            , Map.class);
            
            long errorCount = errorResponse.hits().total().value();
            
            // Get warning count
            SearchResponse<Map> warningResponse = elasticsearchClient.search(s -> s
                .index(indexName)
                .size(0)
                .query(q -> q.term(t -> t.field(FIELD_LEVEL).value("WARN")))
            , Map.class);
            
            long warningCount = warningResponse.hits().total().value();
            
            // Calculate logs per minute (last 24 hours)
            double logsPerMinute = totalLogs / (24.0 * 60.0);
            
            // Calculate error rate
            double errorRate = totalLogs > 0 ? (errorCount * 100.0 / totalLogs) : 0.0;
            
            return DashboardMetricsDTO.builder()
                .totalLogs(totalLogs)
                .errorCount(errorCount)
                .warningCount(warningCount)
                .activeAlerts(0)
                .anomalyCount(0)
                .logsPerMinute(logsPerMinute)
                .errorRate(errorRate)
                .build();
                
        } catch (IOException e) {
            log.error("Failed to get dashboard metrics: {}", e.getMessage(), e);
            return DashboardMetricsDTO.builder()
                .totalLogs(0).errorCount(0).warningCount(0)
                .activeAlerts(0).anomalyCount(0)
                .logsPerMinute(0.0).errorRate(0.0)
                .build();
        }
    }
    
    /**
     * Get log volume over time
     */
    public List<LogVolumeDTO> getLogVolume(Instant startTime, Instant endTime) {
        try {
            SearchResponse<Map> response = elasticsearchClient.search(s -> s
                .index(indexName)
                .size(0)
                .query(q -> q.range(r -> r
                    .field(FIELD_TIMESTAMP)
                    .gte(JsonData.of(startTime.toString()))
                    .lte(JsonData.of(endTime.toString()))
                ))
                .aggregations("volume_over_time", a -> a
                    .dateHistogram(dh -> dh
                        .field(FIELD_TIMESTAMP)
                        .fixedInterval(fi -> fi.time("1h"))
                        .minDocCount(0)
                    )
                )
            , Map.class);
            
            if (response.aggregations() == null || !response.aggregations().containsKey("volume_over_time")) {
                log.warn("No volume_over_time aggregation found in response");
                return List.of();
            }
            
            var agg = response.aggregations().get("volume_over_time");
            if (agg == null) {
                log.warn("Volume over time aggregation is null");
                return List.of();
            }
            
            if (!agg.isDateHistogram()) {
                log.warn("Volume over time aggregation is not a date histogram");
                return List.of();
            }
            
            var buckets = agg.dateHistogram().buckets().array();
            log.debug("Found {} log volume buckets", buckets.size());
            
            return buckets.stream()
                .map(bucket -> {
                    // bucket.key() returns a String in Elasticsearch Java client
                    String keyStr = bucket.keyAsString();
                    Instant timestamp;
                    if (keyStr != null && !keyStr.isEmpty()) {
                        timestamp = Instant.parse(keyStr);
                    } else {
                        // Fallback: try to parse as epoch millis
                        timestamp = Instant.ofEpochMilli(Long.valueOf(bucket.key()));
                    }
                    return LogVolumeDTO.builder()
                        .timestamp(timestamp)
                        .count(bucket.docCount())
                        .build();
                })
                .collect(Collectors.toList());
                
        } catch (Exception e) {
            log.error("Failed to get log volume: {}", e.getMessage(), e);
            e.printStackTrace();
            return List.of();
        }
    }
    
    /**
     * Get log level distribution
     */
    public List<LogLevelDistributionDTO> getLogLevelDistribution() {
        try {
            SearchResponse<Map> response = elasticsearchClient.search(s -> s
                .index(indexName)
                .size(0)
                .aggregations("level_distribution", a -> a
                    .terms(t -> t.field(FIELD_LEVEL))
                )
            , Map.class);
            
            long totalLogs = response.hits().total().value();
            log.debug("Total logs for level distribution: {}", totalLogs);
            
            if (response.aggregations() == null || !response.aggregations().containsKey("level_distribution")) {
                log.warn("No level_distribution aggregation found in response");
                return List.of();
            }
            
            var agg = response.aggregations().get("level_distribution");
            if (agg == null) {
                log.warn("Level distribution aggregation is null");
                return List.of();
            }
            
            // Try both sterms() and lterms() as the field might be mapped differently
            if (agg.isSterms()) {
                var buckets = agg.sterms().buckets().array();
                log.debug("Found {} level distribution buckets (sterms)", buckets.size());
                return buckets.stream()
                    .map(bucket -> {
                        long count = bucket.docCount();
                        double percentage = totalLogs > 0 ? (count * 100.0 / totalLogs) : 0.0;
                        return LogLevelDistributionDTO.builder()
                            .level(bucket.key().stringValue())
                            .count(count)
                            .percentage(percentage)
                            .build();
                    })
                    .collect(Collectors.toList());
            } else if (agg.isLterms()) {
                var buckets = agg.lterms().buckets().array();
                log.debug("Found {} level distribution buckets (lterms)", buckets.size());
                return buckets.stream()
                    .map(bucket -> {
                        long count = bucket.docCount();
                        double percentage = totalLogs > 0 ? (count * 100.0 / totalLogs) : 0.0;
                        return LogLevelDistributionDTO.builder()
                            .level(String.valueOf(bucket.key()))
                            .count(count)
                            .percentage(percentage)
                            .build();
                    })
                    .collect(Collectors.toList());
            } else {
                log.warn("Could not extract buckets from level_distribution aggregation - not sterms or lterms");
                return List.of();
            }
                
        } catch (Exception e) {
            log.error("Failed to get log level distribution: {}", e.getMessage(), e);
            e.printStackTrace();
            return List.of();
        }
    }
    
    /**
     * Get top services by log count
     */
    public List<ServiceLogCountDTO> getTopServices(int limit) {
        try {
            SearchResponse<Map> response = elasticsearchClient.search(s -> s
                .index(indexName)
                .size(0)
                .aggregations("top_services", a -> a
                    .terms(t -> t
                        .field(FIELD_SERVICE)
                        .size(limit)
                    )
                )
            , Map.class);
            
            if (response.aggregations() == null || !response.aggregations().containsKey("top_services")) {
                log.warn("No top_services aggregation found in response");
                return List.of();
            }
            
            var agg = response.aggregations().get("top_services");
            if (agg == null) {
                log.warn("Top services aggregation is null");
                return List.of();
            }
            
            // Try both sterms() and lterms() as the field might be mapped differently
            if (agg.isSterms()) {
                var buckets = agg.sterms().buckets().array();
                log.debug("Found {} top service buckets (sterms)", buckets.size());
                return buckets.stream()
                    .map(bucket -> ServiceLogCountDTO.builder()
                        .service(bucket.key().stringValue())
                        .count(bucket.docCount())
                        .build())
                    .collect(Collectors.toList());
            } else if (agg.isLterms()) {
                var buckets = agg.lterms().buckets().array();
                log.debug("Found {} top service buckets (lterms)", buckets.size());
                return buckets.stream()
                    .map(bucket -> ServiceLogCountDTO.builder()
                        .service(String.valueOf(bucket.key()))
                        .count(bucket.docCount())
                        .build())
                    .collect(Collectors.toList());
            } else {
                log.warn("Could not extract buckets from top_services aggregation - not sterms or lterms");
                return List.of();
            }
                
        } catch (Exception e) {
            log.error("Failed to get top services: {}", e.getMessage(), e);
            e.printStackTrace();
            return List.of();
        }
    }

    /**
     * Custom exception for Elasticsearch indexing errors
     */
    public static class ElasticsearchIndexException extends RuntimeException {
        public ElasticsearchIndexException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}

// Made with Bob
