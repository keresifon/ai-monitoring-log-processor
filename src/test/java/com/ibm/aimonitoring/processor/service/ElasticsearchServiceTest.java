package com.ibm.aimonitoring.processor.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.Result;
import co.elastic.clients.elasticsearch.core.IndexResponse;
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
}
