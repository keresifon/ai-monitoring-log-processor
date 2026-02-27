package com.ibm.aimonitoring.processor.controller;

import com.ibm.aimonitoring.processor.service.ElasticsearchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HealthControllerTest {

    @Mock
    private ElasticsearchService elasticsearchService;

    @InjectMocks
    private HealthController healthController;

    @BeforeEach
    void setUp() {
        healthController = new HealthController(elasticsearchService);
    }

    @Test
    void testHealth_ElasticsearchUp() {
        // Given
        when(elasticsearchService.isAvailable()).thenReturn(true);

        // When
        ResponseEntity<Map<String, Object>> response = healthController.health();

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("UP", response.getBody().get("status"));
        assertEquals("log-processor", response.getBody().get("service"));
        assertEquals("UP", response.getBody().get("elasticsearch"));
        verify(elasticsearchService).isAvailable();
    }

    @Test
    void testHealth_ElasticsearchDown() {
        // Given
        when(elasticsearchService.isAvailable()).thenReturn(false);

        // When
        ResponseEntity<Map<String, Object>> response = healthController.health();

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("UP", response.getBody().get("status"));
        assertEquals("DOWN", response.getBody().get("elasticsearch"));
    }
}
