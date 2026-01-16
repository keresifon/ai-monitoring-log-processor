package com.ibm.aimonitoring.processor.controller;

import com.ibm.aimonitoring.processor.dto.LogEntryDTO;
import com.ibm.aimonitoring.processor.dto.LogSearchRequest;
import com.ibm.aimonitoring.processor.dto.LogSearchResponse;
import com.ibm.aimonitoring.processor.service.ElasticsearchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LogSearchControllerTest {

    @Mock
    private ElasticsearchService elasticsearchService;

    @InjectMocks
    private LogSearchController logSearchController;

    @BeforeEach
    void setUp() {
        logSearchController = new LogSearchController(elasticsearchService);
    }

    @Test
    void testSearchLogs_WithDefaultParameters() {
        // Given
        LogSearchResponse expectedResponse = LogSearchResponse.builder()
                .logs(List.of(LogEntryDTO.builder().message("test").build()))
                .total(1L)
                .page(0)
                .size(50)
                .build();

        when(elasticsearchService.searchLogs(any(LogSearchRequest.class)))
                .thenReturn(expectedResponse);

        // When - using default values for page (0), size (50), sortBy ("timestamp"), sortDirection ("desc")
        ResponseEntity<LogSearchResponse> response = logSearchController.searchLogs(
                0, 50, "timestamp", "desc", null, null, null, null, null
        );

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(1L, response.getBody().getTotal());
        verify(elasticsearchService).searchLogs(any(LogSearchRequest.class));
    }

    @Test
    void testSearchLogs_WithAllParameters() {
        // Given
        String level = "ERROR";
        String service = "api-gateway";
        String query = "exception";
        String startTime = "2024-01-01T00:00:00Z";
        String endTime = "2024-01-02T00:00:00Z";

        LogSearchResponse expectedResponse = LogSearchResponse.builder()
                .logs(List.of())
                .total(0L)
                .page(1)
                .size(20)
                .build();

        when(elasticsearchService.searchLogs(any(LogSearchRequest.class)))
                .thenReturn(expectedResponse);

        // When
        ResponseEntity<LogSearchResponse> response = logSearchController.searchLogs(
                1, 20, "timestamp", "asc", level, service, query, startTime, endTime
        );

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        ArgumentCaptor<LogSearchRequest> captor = ArgumentCaptor.forClass(LogSearchRequest.class);
        verify(elasticsearchService).searchLogs(captor.capture());
        
        LogSearchRequest request = captor.getValue();
        assertEquals(1, request.getPage());
        assertEquals(20, request.getSize());
        assertEquals("timestamp", request.getSortBy());
        assertEquals("asc", request.getSortOrder());
        assertNotNull(request.getLevels());
        assertNotNull(request.getServices());
        assertEquals(query, request.getQuery());
        assertNotNull(request.getStartTime());
        assertNotNull(request.getEndTime());
    }

    @Test
    void testSearchLogs_WithMultipleLevels() {
        // Given
        String levels = "ERROR,WARN,INFO";

        LogSearchResponse expectedResponse = LogSearchResponse.builder()
                .logs(List.of())
                .total(0L)
                .page(0)
                .size(50)
                .build();

        when(elasticsearchService.searchLogs(any(LogSearchRequest.class)))
                .thenReturn(expectedResponse);

        // When - using default values for page (0), size (50), sortBy ("timestamp"), sortDirection ("desc")
        logSearchController.searchLogs(
                0, 50, "timestamp", "desc", levels, null, null, null, null
        );

        // Then
        ArgumentCaptor<LogSearchRequest> captor = ArgumentCaptor.forClass(LogSearchRequest.class);
        verify(elasticsearchService).searchLogs(captor.capture());
        
        LogSearchRequest request = captor.getValue();
        assertNotNull(request.getLevels());
        assertEquals(3, request.getLevels().size());
        assertTrue(request.getLevels().contains("ERROR"));
        assertTrue(request.getLevels().contains("WARN"));
        assertTrue(request.getLevels().contains("INFO"));
    }

    @Test
    void testSearchLogs_WithMultipleServices() {
        // Given
        String services = "api-gateway,user-service,order-service";

        LogSearchResponse expectedResponse = LogSearchResponse.builder()
                .logs(List.of())
                .total(0L)
                .page(0)
                .size(50)
                .build();

        when(elasticsearchService.searchLogs(any(LogSearchRequest.class)))
                .thenReturn(expectedResponse);

        // When - using default values for page (0), size (50), sortBy ("timestamp"), sortDirection ("desc")
        logSearchController.searchLogs(
                0, 50, "timestamp", "desc", null, services, null, null, null
        );

        // Then
        ArgumentCaptor<LogSearchRequest> captor = ArgumentCaptor.forClass(LogSearchRequest.class);
        verify(elasticsearchService).searchLogs(captor.capture());
        
        LogSearchRequest request = captor.getValue();
        assertNotNull(request.getServices());
        assertEquals(3, request.getServices().size());
    }
}
