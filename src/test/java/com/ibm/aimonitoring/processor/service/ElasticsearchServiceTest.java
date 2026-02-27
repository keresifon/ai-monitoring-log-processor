package com.ibm.aimonitoring.processor.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.Result;
import co.elastic.clients.elasticsearch._types.aggregations.DateHistogramAggregate;
import co.elastic.clients.elasticsearch._types.aggregations.DateHistogramBucket;
import co.elastic.clients.elasticsearch._types.aggregations.LongTermsAggregate;
import co.elastic.clients.elasticsearch._types.aggregations.LongTermsBucket;
import co.elastic.clients.elasticsearch._types.aggregations.StringTermsAggregate;
import co.elastic.clients.elasticsearch._types.aggregations.StringTermsBucket;
import co.elastic.clients.elasticsearch._types.aggregations.AvgAggregate;
import co.elastic.clients.elasticsearch.core.IndexResponse;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.core.search.TotalHitsRelation;
import co.elastic.clients.elasticsearch.indices.CreateIndexResponse;
import co.elastic.clients.elasticsearch.indices.ElasticsearchIndicesClient;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import co.elastic.clients.transport.endpoints.BooleanResponse;
import com.ibm.aimonitoring.processor.dto.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ElasticsearchServiceTest {

    @Mock
    private ElasticsearchClient elasticsearchClient;

    @Mock
    private BooleanResponse booleanResponse;

    @Mock
    private IndexResponse indexResponse;

    @Mock
    private ElasticsearchIndicesClient indicesClient;

    @Mock
    private CreateIndexResponse createIndexResponse;

    private ElasticsearchService elasticsearchService;

    private LogEntryDTO testLogEntry;

    @BeforeEach
    void setUp() {
        elasticsearchService = new ElasticsearchService(elasticsearchClient);
        ReflectionTestUtils.setField(elasticsearchService, "indexName", "logs");
        ReflectionTestUtils.setField(elasticsearchService, "numberOfShards", 1);
        ReflectionTestUtils.setField(elasticsearchService, "numberOfReplicas", 0);

        testLogEntry = LogEntryDTO.builder()
                .timestamp(Instant.now())
                .level("ERROR")
                .message("Test log message")
                .service("test-service")
                .host("test-host")
                .environment("test")
                .traceId("trace-123")
                .spanId("span-456")
                .metadata(new HashMap<>())
                .build();
    }

    @Test
    void testIndexLog_Success() throws IOException {
        // Given
        doReturn(indexResponse).when(elasticsearchClient).index(any(java.util.function.Function.class));
        when(indexResponse.result()).thenReturn(Result.Created);
        when(indexResponse.id()).thenReturn("doc-123");

        // When
        String documentId = elasticsearchService.indexLog(testLogEntry);

        // Then
        assertNotNull(documentId);
        assertEquals("doc-123", documentId);
    }

    @Test
    void testIndexLog_UpdatedResult() throws IOException {
        // Given
        doReturn(indexResponse).when(elasticsearchClient).index(any(java.util.function.Function.class));
        when(indexResponse.result()).thenReturn(Result.Updated);
        when(indexResponse.id()).thenReturn("doc-123");

        // When
        String documentId = elasticsearchService.indexLog(testLogEntry);

        // Then
        assertNotNull(documentId);
        assertEquals("doc-123", documentId);
    }

    @Test
    void testIndexLog_UnexpectedResult() throws IOException {
        // Given
        doReturn(indexResponse).when(elasticsearchClient).index(any(java.util.function.Function.class));
        when(indexResponse.result()).thenReturn(Result.Deleted);

        // When
        String documentId = elasticsearchService.indexLog(testLogEntry);

        // Then
        assertNull(documentId);
    }

    @Test
    void testIndexLog_IOException() throws IOException {
        // Given
        doThrow(new IOException("Elasticsearch error")).when(elasticsearchClient).index(any(java.util.function.Function.class));

        // When/Then
        assertThrows(ElasticsearchService.ElasticsearchIndexException.class, () -> {
            elasticsearchService.indexLog(testLogEntry);
        });
    }

    @Test
    void testIndexLog_WithNullFields() throws IOException {
        // Given
        LogEntryDTO logEntry = LogEntryDTO.builder()
                .level("INFO")
                .message("test")
                .build();

        doReturn(indexResponse).when(elasticsearchClient).index(any(java.util.function.Function.class));
        when(indexResponse.result()).thenReturn(Result.Created);
        when(indexResponse.id()).thenReturn("doc-123");

        // When
        String documentId = elasticsearchService.indexLog(logEntry);

        // Then
        assertNotNull(documentId);
    }

    @Test
    void testIsAvailable_Success() throws IOException {
        // Given
        when(elasticsearchClient.ping()).thenReturn(booleanResponse);
        when(booleanResponse.value()).thenReturn(true);

        // When
        boolean result = elasticsearchService.isAvailable();

        // Then
        assertTrue(result);
        verify(elasticsearchClient).ping();
    }

    @Test
    void testIsAvailable_Failure() throws IOException {
        // Given
        when(elasticsearchClient.ping()).thenReturn(booleanResponse);
        when(booleanResponse.value()).thenReturn(false);

        // When
        boolean result = elasticsearchService.isAvailable();

        // Then
        assertFalse(result);
    }

    @Test
    void testIsAvailable_IOException() throws IOException {
        // Given
        when(elasticsearchClient.ping()).thenThrow(new IOException("Connection failed"));

        // When
        boolean result = elasticsearchService.isAvailable();

        // Then
        assertFalse(result);
    }

    @Test
    void testSearchLogs_IOException() throws IOException {
        // Given
        LogSearchRequest request = LogSearchRequest.builder()
                .page(0)
                .size(10)
                .sortBy("timestamp")
                .sortOrder("desc")
                .build();

        doThrow(new IOException("Search failed")).when(elasticsearchClient).search(any(java.util.function.Function.class), eq(Map.class));

        // When/Then
        assertThrows(ElasticsearchService.ElasticsearchIndexException.class, () -> {
            elasticsearchService.searchLogs(request);
        });
    }

    @Test
    void testGetDashboardMetrics_IOException() throws IOException {
        // Given
        doThrow(new IOException("Search failed")).when(elasticsearchClient).search(any(java.util.function.Function.class), eq(Map.class));

        // When
        DashboardMetricsDTO metrics = elasticsearchService.getDashboardMetrics();

        // Then
        assertNotNull(metrics);
        assertEquals(0L, metrics.getTotalLogs());
        assertEquals(0.0, metrics.getErrorRate());
    }

    @Test
    void testGetLogVolume_IOException() throws IOException {
        // Given
        Instant startTime = Instant.now().minusSeconds(3600);
        Instant endTime = Instant.now();

        doThrow(new IOException("Search failed")).when(elasticsearchClient).search(any(java.util.function.Function.class), eq(Map.class));

        // When
        List<LogVolumeDTO> volume = elasticsearchService.getLogVolume(startTime, endTime);

        // Then
        assertNotNull(volume);
        assertTrue(volume.isEmpty());
    }

    @Test
    void testGetLogLevelDistribution_IOException() throws IOException {
        // Given
        doThrow(new IOException("Search failed")).when(elasticsearchClient).search(any(java.util.function.Function.class), eq(Map.class));

        // When
        List<LogLevelDistributionDTO> distribution = elasticsearchService.getLogLevelDistribution();

        // Then
        assertNotNull(distribution);
        assertTrue(distribution.isEmpty());
    }

    @Test
    void testGetTopServices_IOException() throws IOException {
        // Given
        doThrow(new IOException("Search failed")).when(elasticsearchClient).search(any(java.util.function.Function.class), eq(Map.class));

        // When
        List<ServiceLogCountDTO> services = elasticsearchService.getTopServices(10);

        // Then
        assertNotNull(services);
        assertTrue(services.isEmpty());
    }

    @Test
    void testInit_WithNullClient() {
        // Given - client is null
        ElasticsearchService service = new ElasticsearchService(null);
        ReflectionTestUtils.setField(service, "indexName", "logs");

        // When
        service.init();

        // Then - should not throw, just log warning
        assertDoesNotThrow(service::init);
    }

    @Test
    void testInit_WithNullIndices() {
        // Given
        when(elasticsearchClient.indices()).thenReturn(null);

        // When
        elasticsearchService.init();

        // Then - should not throw, just log warning
        assertDoesNotThrow(() -> elasticsearchService.init());
    }

    @Test
    void testInit_IndexCreated() throws IOException {
        // Given - index does not exist (service uses Function builder for create)
        when(elasticsearchClient.indices()).thenReturn(indicesClient);
        when(indicesClient.exists(any(ExistsRequest.class))).thenReturn(booleanResponse);
        when(booleanResponse.value()).thenReturn(false);
        doReturn(createIndexResponse).when(indicesClient).create(any(Function.class));
        when(createIndexResponse.acknowledged()).thenReturn(true);

        // When
        elasticsearchService.init();

        // Then
        verify(indicesClient).exists(any(ExistsRequest.class));
        verify(indicesClient).create(any(Function.class));
    }

    @Test
    void testInit_IndexAlreadyExists() throws IOException {
        // Given - index already exists
        when(elasticsearchClient.indices()).thenReturn(indicesClient);
        when(indicesClient.exists(any(ExistsRequest.class))).thenReturn(booleanResponse);
        when(booleanResponse.value()).thenReturn(true);

        // When
        elasticsearchService.init();

        // Then
        verify(indicesClient).exists(any(ExistsRequest.class));
        verify(indicesClient, never()).create(any(Function.class));
    }

    @Test
    void testSearchLogs_Success() throws IOException {
        // Given
        Map<String, Object> sourceDoc = new HashMap<>();
        sourceDoc.put("timestamp", "2024-01-15T10:30:00Z");
        sourceDoc.put("level", "ERROR");
        sourceDoc.put("message", "Test error message");
        sourceDoc.put("service", "test-service");
        sourceDoc.put("host", "host1");
        sourceDoc.put("environment", "prod");

        var hit = Hit.of(h -> h.index("logs").source(sourceDoc));
        var searchResponse = SearchResponse.of(s -> s
                .took(0)
                .timedOut(false)
                .shards(sh -> sh.total(1).failed(0).successful(1))
                .hits(h -> h
                        .total(t -> t.value(1L).relation(TotalHitsRelation.Eq))
                        .hits(List.of(hit))
                )
        );

        when(elasticsearchClient.search(any(Function.class), eq(Map.class))).thenReturn(searchResponse);

        LogSearchRequest request = LogSearchRequest.builder()
                .page(0)
                .size(20)
                .sortBy("timestamp")
                .sortOrder("desc")
                .build();

        // When
        LogSearchResponse response = elasticsearchService.searchLogs(request);

        // Then
        assertNotNull(response);
        assertEquals(1, response.getTotal());
        assertEquals(1, response.getLogs().size());
        LogEntryDTO logEntry = response.getLogs().get(0);
        assertEquals("ERROR", logEntry.getLevel());
        assertEquals("Test error message", logEntry.getMessage());
        assertEquals("test-service", logEntry.getService());
    }

    @Test
    void testSearchLogs_WithFilters() throws IOException {
        // Given - search with query, levels, services, time range
        var emptyResponse = SearchResponse.of(s -> s
                .took(0)
                .timedOut(false)
                .shards(sh -> sh.total(1).failed(0).successful(1))
                .hits(h -> h.total(t -> t.value(0L).relation(TotalHitsRelation.Eq)).hits(List.of()))
        );

        when(elasticsearchClient.search(any(Function.class), eq(Map.class))).thenReturn(emptyResponse);

        LogSearchRequest request = LogSearchRequest.builder()
                .query("error")
                .levels(List.of("ERROR", "WARN"))
                .services(List.of("api-gateway"))
                .startTime(Instant.now().minusSeconds(3600))
                .endTime(Instant.now())
                .page(0)
                .size(10)
                .sortBy("timestamp")
                .sortOrder("asc")
                .build();

        // When
        LogSearchResponse response = elasticsearchService.searchLogs(request);

        // Then
        assertNotNull(response);
        assertEquals(0, response.getTotal());
    }

    @Test
    void testGetDashboardMetrics_Success() throws IOException {
        // Given - 3 search calls: total, error, warning
        var totalResponse = SearchResponse.of(s -> s
                .took(0)
                .timedOut(false)
                .shards(sh -> sh.total(1).failed(0).successful(1))
                .hits(h -> h.total(t -> t.value(1000L).relation(TotalHitsRelation.Eq)).hits(List.of()))
        );
        var errorResponse = SearchResponse.of(s -> s
                .took(0)
                .timedOut(false)
                .shards(sh -> sh.total(1).failed(0).successful(1))
                .hits(h -> h.total(t -> t.value(50L).relation(TotalHitsRelation.Eq)).hits(List.of()))
        );
        var warningResponse = SearchResponse.of(s -> s
                .took(0)
                .timedOut(false)
                .shards(sh -> sh.total(1).failed(0).successful(1))
                .hits(h -> h.total(t -> t.value(100L).relation(TotalHitsRelation.Eq)).hits(List.of()))
        );

        when(elasticsearchClient.search(any(Function.class), eq(Map.class)))
                .thenReturn(totalResponse, errorResponse, warningResponse);

        // When
        DashboardMetricsDTO metrics = elasticsearchService.getDashboardMetrics();

        // Then
        assertNotNull(metrics);
        assertEquals(1000L, metrics.getTotalLogs());
        assertEquals(50L, metrics.getErrorCount());
        assertEquals(100L, metrics.getWarningCount());
        assertEquals(5.0, metrics.getErrorRate()); // 50/1000 * 100
        assertEquals(1000.0 / (24 * 60), metrics.getLogsPerMinute());
    }

    @Test
    void testConvertToLogEntry_WithMetadata() throws IOException {
        // Given - document with metadata
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("key", "value");
        Map<String, Object> sourceDoc = new HashMap<>();
        sourceDoc.put("timestamp", "2024-01-15T10:30:00Z");
        sourceDoc.put("level", "INFO");
        sourceDoc.put("message", "Msg");
        sourceDoc.put("service", "svc");
        sourceDoc.put("host", "h");
        sourceDoc.put("environment", "env");
        sourceDoc.put("traceId", "t1");
        sourceDoc.put("spanId", "s1");
        sourceDoc.put("metadata", metadata);

        var hit = Hit.of(h -> h.index("logs").source(sourceDoc));
        var searchResponse = SearchResponse.of(s -> s
                .took(0)
                .timedOut(false)
                .shards(sh -> sh.total(1).failed(0).successful(1))
                .hits(h -> h
                        .total(t -> t.value(1L).relation(TotalHitsRelation.Eq))
                        .hits(List.of(hit))
                )
        );
        when(elasticsearchClient.search(any(Function.class), eq(Map.class))).thenReturn(searchResponse);

        LogSearchRequest request = LogSearchRequest.builder().page(0).size(10).build();

        // When
        LogSearchResponse response = elasticsearchService.searchLogs(request);

        // Then
        assertNotNull(response.getLogs().get(0).getMetadata());
        assertEquals("value", response.getLogs().get(0).getMetadata().get("key"));
    }

    @Test
    void testIndexLog_WithMetadataNull() throws IOException {
        // Given - log entry without metadata (convertToDocument path)
        LogEntryDTO logEntry = LogEntryDTO.builder()
                .level("INFO")
                .message("test")
                .service("svc")
                .build();

        doReturn(indexResponse).when(elasticsearchClient).index(any(Function.class));
        when(indexResponse.result()).thenReturn(Result.Created);
        when(indexResponse.id()).thenReturn("doc-1");

        // When
        String id = elasticsearchService.indexLog(logEntry);

        // Then
        assertEquals("doc-1", id);
    }

    @Test
    void testGetLogVolume_Success() throws IOException {
        // Given - DateHistogramAggregate with keyAsString
        long epochMillis = Instant.parse("2024-01-15T10:00:00Z").toEpochMilli();
        var bucket = DateHistogramBucket.of(b -> b
                .key(epochMillis)
                .keyAsString("2024-01-15T10:00:00.000Z")
                .docCount(42));
        var volumeAgg = DateHistogramAggregate.of(a -> a.buckets(b -> b.array(List.of(bucket))));

        var aggMap = new HashMap<String, co.elastic.clients.elasticsearch._types.aggregations.Aggregate>();
        aggMap.put("volume_over_time", volumeAgg._toAggregate());

        var searchResponse = SearchResponse.of(s -> s
                .took(0)
                .timedOut(false)
                .shards(sh -> sh.total(1).failed(0).successful(1))
                .hits(h -> h.total(t -> t.value(0L).relation(TotalHitsRelation.Eq)).hits(List.of()))
                .aggregations(aggMap));

        when(elasticsearchClient.search(any(Function.class), eq(Map.class))).thenReturn(searchResponse);

        Instant start = Instant.parse("2024-01-15T00:00:00Z");
        Instant end = Instant.parse("2024-01-15T23:59:59Z");

        // When
        List<LogVolumeDTO> result = elasticsearchService.getLogVolume(start, end);

        // Then
        assertEquals(1, result.size());
        assertEquals(Instant.parse("2024-01-15T10:00:00.000Z"), result.get(0).getTimestamp());
        assertEquals(42, result.get(0).getCount());
    }

    @Test
    void testGetLogVolume_KeyAsStringFallback() throws IOException {
        // Given - bucket with null keyAsString (fallback to epoch millis)
        long epochMillis = Instant.parse("2024-01-15T10:00:00Z").toEpochMilli();
        var bucket = DateHistogramBucket.of(b -> b.key(epochMillis).docCount(5));
        var volumeAgg = DateHistogramAggregate.of(a -> a.buckets(bk -> bk.array(List.of(bucket))));

        var aggMap = new HashMap<String, co.elastic.clients.elasticsearch._types.aggregations.Aggregate>();
        aggMap.put("volume_over_time", volumeAgg._toAggregate());

        var searchResponse = SearchResponse.of(s -> s
                .took(0)
                .timedOut(false)
                .shards(sh -> sh.total(1).failed(0).successful(1))
                .hits(h -> h.total(t -> t.value(0L).relation(TotalHitsRelation.Eq)).hits(List.of()))
                .aggregations(aggMap));

        when(elasticsearchClient.search(any(Function.class), eq(Map.class))).thenReturn(searchResponse);

        // When
        List<LogVolumeDTO> result = elasticsearchService.getLogVolume(Instant.EPOCH, Instant.now());

        // Then
        assertEquals(1, result.size());
        assertEquals(Instant.ofEpochMilli(epochMillis), result.get(0).getTimestamp());
        assertEquals(5, result.get(0).getCount());
    }

    @Test
    void testGetLogLevelDistribution_Success() throws IOException {
        // Given - StringTermsAggregate (sterms)
        var errorBucket = StringTermsBucket.of(b -> b.key(FieldValue.of("ERROR")).docCount(50));
        var infoBucket = StringTermsBucket.of(b -> b.key(FieldValue.of("INFO")).docCount(100));
        var levelAgg = StringTermsAggregate.of(a -> a.buckets(b -> b.array(List.of(errorBucket, infoBucket))));

        var aggMap = new HashMap<String, co.elastic.clients.elasticsearch._types.aggregations.Aggregate>();
        aggMap.put("level_distribution", levelAgg._toAggregate());

        var searchResponse = SearchResponse.of(s -> s
                .took(0)
                .timedOut(false)
                .shards(sh -> sh.total(1).failed(0).successful(1))
                .hits(h -> h.total(t -> t.value(150L).relation(TotalHitsRelation.Eq)).hits(List.of()))
                .aggregations(aggMap));

        when(elasticsearchClient.search(any(Function.class), eq(Map.class))).thenReturn(searchResponse);

        // When
        List<LogLevelDistributionDTO> result = elasticsearchService.getLogLevelDistribution();

        // Then
        assertEquals(2, result.size());
        assertEquals("ERROR", result.get(0).getLevel());
        assertEquals(50, result.get(0).getCount());
        assertEquals(100.0 * 50 / 150, result.get(0).getPercentage());
    }

    @Test
    void testGetTopServices_Success() throws IOException {
        // Given - StringTermsAggregate for services
        var bucket1 = StringTermsBucket.of(b -> b.key(FieldValue.of("api-gateway")).docCount(200));
        var bucket2 = StringTermsBucket.of(b -> b.key(FieldValue.of("auth-service")).docCount(150));
        var servicesAgg = StringTermsAggregate.of(a -> a.buckets(b -> b.array(List.of(bucket1, bucket2))));

        var aggMap = new HashMap<String, co.elastic.clients.elasticsearch._types.aggregations.Aggregate>();
        aggMap.put("top_services", servicesAgg._toAggregate());

        var searchResponse = SearchResponse.of(s -> s
                .took(0)
                .timedOut(false)
                .shards(sh -> sh.total(1).failed(0).successful(1))
                .hits(h -> h.total(t -> t.value(0L).relation(TotalHitsRelation.Eq)).hits(List.of()))
                .aggregations(aggMap));

        when(elasticsearchClient.search(any(Function.class), eq(Map.class))).thenReturn(searchResponse);

        // When
        List<ServiceLogCountDTO> result = elasticsearchService.getTopServices(10);

        // Then
        assertEquals(2, result.size());
        assertEquals("api-gateway", result.get(0).getService());
        assertEquals(200, result.get(0).getCount());
    }

    @Test
    void testSearchLogs_ConvertToLogEntry_WithNullFields() throws IOException {
        // Given - document with null/empty fields (convertToLogEntry branches)
        Map<String, Object> sourceDoc = new HashMap<>();
        sourceDoc.put("timestamp", null);
        sourceDoc.put("level", null);
        sourceDoc.put("message", null);
        sourceDoc.put("service", null);
        sourceDoc.put("host", null);
        sourceDoc.put("environment", null);
        sourceDoc.put("traceId", null);
        sourceDoc.put("spanId", null);
        sourceDoc.put("metadata", null);

        var hit = Hit.of(h -> h.index("logs").source(sourceDoc));
        var searchResponse = SearchResponse.of(s -> s
                .took(0)
                .timedOut(false)
                .shards(sh -> sh.total(1).failed(0).successful(1))
                .hits(h -> h.total(t -> t.value(1L).relation(TotalHitsRelation.Eq)).hits(List.of(hit)))
        );
        when(elasticsearchClient.search(any(Function.class), eq(Map.class))).thenReturn(searchResponse);

        LogSearchRequest request = LogSearchRequest.builder().page(0).size(10).build();

        // When
        LogSearchResponse response = elasticsearchService.searchLogs(request);

        // Then
        LogEntryDTO log = response.getLogs().get(0);
        assertNull(log.getTimestamp());
        assertNull(log.getLevel());
        assertNull(log.getMessage());
        assertNull(log.getService());
        assertNull(log.getMetadata());
    }

    @Test
    void testInit_WithIOException() throws IOException {
        // Given - exists() throws IOException
        when(elasticsearchClient.indices()).thenReturn(indicesClient);
        when(indicesClient.exists(any(ExistsRequest.class))).thenThrow(new IOException("ES unavailable"));

        // When - should not throw (catches and logs)
        assertDoesNotThrow(elasticsearchService::init);
    }

    @Test
    void testGetLogVolume_NoAggregations() throws IOException {
        // Given - response with no aggregations
        var searchResponse = SearchResponse.of(s -> s
                .took(0)
                .timedOut(false)
                .shards(sh -> sh.total(1).failed(0).successful(1))
                .hits(h -> h.total(t -> t.value(0L).relation(TotalHitsRelation.Eq)).hits(List.of()))
        );
        when(elasticsearchClient.search(any(Function.class), eq(Map.class))).thenReturn(searchResponse);

        // When
        List<LogVolumeDTO> result = elasticsearchService.getLogVolume(Instant.EPOCH, Instant.now());

        // Then
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testGetLogVolume_AggregationMissingKey() throws IOException {
        // Given - aggregations exist but volume_over_time key missing
        var aggMap = new HashMap<String, co.elastic.clients.elasticsearch._types.aggregations.Aggregate>();
        aggMap.put("other_agg", AvgAggregate.of(a -> a.value(0.0))._toAggregate());

        var searchResponse = SearchResponse.of(s -> s
                .took(0)
                .timedOut(false)
                .shards(sh -> sh.total(1).failed(0).successful(1))
                .hits(h -> h.total(t -> t.value(0L).relation(TotalHitsRelation.Eq)).hits(List.of()))
                .aggregations(aggMap));

        when(elasticsearchClient.search(any(Function.class), eq(Map.class))).thenReturn(searchResponse);

        List<LogVolumeDTO> result = elasticsearchService.getLogVolume(Instant.EPOCH, Instant.now());

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testGetLogVolume_NotDateHistogram() throws IOException {
        // Given - aggregation with wrong type (stats instead of date histogram)
        var aggMap = new HashMap<String, co.elastic.clients.elasticsearch._types.aggregations.Aggregate>();
        aggMap.put("volume_over_time", AvgAggregate.of(a -> a.value(0.0))._toAggregate());

        var searchResponse = SearchResponse.of(s -> s
                .took(0)
                .timedOut(false)
                .shards(sh -> sh.total(1).failed(0).successful(1))
                .hits(h -> h.total(t -> t.value(0L).relation(TotalHitsRelation.Eq)).hits(List.of()))
                .aggregations(aggMap));

        when(elasticsearchClient.search(any(Function.class), eq(Map.class))).thenReturn(searchResponse);

        List<LogVolumeDTO> result = elasticsearchService.getLogVolume(Instant.EPOCH, Instant.now());

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testGetLogLevelDistribution_WithLterms() throws IOException {
        // Given - LongTermsAggregate (lterms branch)
        var bucket1 = LongTermsBucket.of(b -> b.key(1L).docCount(30));
        var bucket2 = LongTermsBucket.of(b -> b.key(2L).docCount(70));
        var levelAgg = LongTermsAggregate.of(a -> a.buckets(b -> b.array(List.of(bucket1, bucket2))));

        var aggMap = new HashMap<String, co.elastic.clients.elasticsearch._types.aggregations.Aggregate>();
        aggMap.put("level_distribution", levelAgg._toAggregate());

        var searchResponse = SearchResponse.of(s -> s
                .took(0)
                .timedOut(false)
                .shards(sh -> sh.total(1).failed(0).successful(1))
                .hits(h -> h.total(t -> t.value(100L).relation(TotalHitsRelation.Eq)).hits(List.of()))
                .aggregations(aggMap));

        when(elasticsearchClient.search(any(Function.class), eq(Map.class))).thenReturn(searchResponse);

        List<LogLevelDistributionDTO> result = elasticsearchService.getLogLevelDistribution();

        assertEquals(2, result.size());
        assertEquals("1", result.get(0).getLevel());
        assertEquals(30, result.get(0).getCount());
        assertEquals("2", result.get(1).getLevel());
        assertEquals(70, result.get(1).getCount());
    }

    @Test
    void testGetLogLevelDistribution_NoAggregations() throws IOException {
        var searchResponse = SearchResponse.of(s -> s
                .took(0)
                .timedOut(false)
                .shards(sh -> sh.total(1).failed(0).successful(1))
                .hits(h -> h.total(t -> t.value(0L).relation(TotalHitsRelation.Eq)).hits(List.of()))
        );
        when(elasticsearchClient.search(any(Function.class), eq(Map.class))).thenReturn(searchResponse);

        List<LogLevelDistributionDTO> result = elasticsearchService.getLogLevelDistribution();

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testGetLogLevelDistribution_NotTerms() throws IOException {
        var aggMap = new HashMap<String, co.elastic.clients.elasticsearch._types.aggregations.Aggregate>();
        aggMap.put("level_distribution", AvgAggregate.of(a -> a.value(0.0))._toAggregate());

        var searchResponse = SearchResponse.of(s -> s
                .took(0)
                .timedOut(false)
                .shards(sh -> sh.total(1).failed(0).successful(1))
                .hits(h -> h.total(t -> t.value(100L).relation(TotalHitsRelation.Eq)).hits(List.of()))
                .aggregations(aggMap));

        when(elasticsearchClient.search(any(Function.class), eq(Map.class))).thenReturn(searchResponse);

        List<LogLevelDistributionDTO> result = elasticsearchService.getLogLevelDistribution();

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testGetTopServices_WithLterms() throws IOException {
        var bucket1 = LongTermsBucket.of(b -> b.key(100L).docCount(80));
        var bucket2 = LongTermsBucket.of(b -> b.key(200L).docCount(50));
        var servicesAgg = LongTermsAggregate.of(a -> a.buckets(b -> b.array(List.of(bucket1, bucket2))));

        var aggMap = new HashMap<String, co.elastic.clients.elasticsearch._types.aggregations.Aggregate>();
        aggMap.put("top_services", servicesAgg._toAggregate());

        var searchResponse = SearchResponse.of(s -> s
                .took(0)
                .timedOut(false)
                .shards(sh -> sh.total(1).failed(0).successful(1))
                .hits(h -> h.total(t -> t.value(0L).relation(TotalHitsRelation.Eq)).hits(List.of()))
                .aggregations(aggMap));

        when(elasticsearchClient.search(any(Function.class), eq(Map.class))).thenReturn(searchResponse);

        List<ServiceLogCountDTO> result = elasticsearchService.getTopServices(10);

        assertEquals(2, result.size());
        assertEquals("100", result.get(0).getService());
        assertEquals(80, result.get(0).getCount());
    }

    @Test
    void testGetTopServices_NoAggregations() throws IOException {
        var searchResponse = SearchResponse.of(s -> s
                .took(0)
                .timedOut(false)
                .shards(sh -> sh.total(1).failed(0).successful(1))
                .hits(h -> h.total(t -> t.value(0L).relation(TotalHitsRelation.Eq)).hits(List.of()))
        );
        when(elasticsearchClient.search(any(Function.class), eq(Map.class))).thenReturn(searchResponse);

        List<ServiceLogCountDTO> result = elasticsearchService.getTopServices(5);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testGetTopServices_NotTerms() throws IOException {
        var aggMap = new HashMap<String, co.elastic.clients.elasticsearch._types.aggregations.Aggregate>();
        aggMap.put("top_services", AvgAggregate.of(a -> a.value(0.0))._toAggregate());

        var searchResponse = SearchResponse.of(s -> s
                .took(0)
                .timedOut(false)
                .shards(sh -> sh.total(1).failed(0).successful(1))
                .hits(h -> h.total(t -> t.value(0L).relation(TotalHitsRelation.Eq)).hits(List.of()))
                .aggregations(aggMap));

        when(elasticsearchClient.search(any(Function.class), eq(Map.class))).thenReturn(searchResponse);

        List<ServiceLogCountDTO> result = elasticsearchService.getTopServices(10);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }
}
