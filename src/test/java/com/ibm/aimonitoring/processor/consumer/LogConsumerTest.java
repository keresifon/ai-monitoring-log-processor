package com.ibm.aimonitoring.processor.consumer;

import com.ibm.aimonitoring.processor.dto.LogEntryDTO;
import com.ibm.aimonitoring.processor.service.LogProcessorService;
import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LogConsumerTest {

    @Mock
    private LogProcessorService logProcessorService;

    @Mock
    private Channel channel;

    @Mock
    private Message message;

    @InjectMocks
    private LogConsumer logConsumer;

    private LogEntryDTO testLogEntry;
    private long deliveryTag = 123L;

    @BeforeEach
    void setUp() {
        logConsumer = new LogConsumer(logProcessorService);
        
        testLogEntry = LogEntryDTO.builder()
                .level("ERROR")
                .message("Test log message")
                .service("test-service")
                .build();
    }

    @Test
    void testConsumeLog_Success() throws IOException {
        // Given
        doNothing().when(logProcessorService).processLog(any(LogEntryDTO.class));
        doNothing().when(channel).basicAck(eq(deliveryTag), eq(false));

        // When
        logConsumer.consumeLog(testLogEntry, channel, deliveryTag, message);

        // Then
        verify(logProcessorService).processLog(eq(testLogEntry));
        verify(channel).basicAck(eq(deliveryTag), eq(false));
        verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
    }

    @Test
    void testConsumeLog_ProcessingException() throws IOException {
        // Given
        doThrow(new RuntimeException("Processing error"))
                .when(logProcessorService).processLog(any(LogEntryDTO.class));
        doNothing().when(channel).basicNack(eq(deliveryTag), eq(false), eq(false));

        // When
        logConsumer.consumeLog(testLogEntry, channel, deliveryTag, message);

        // Then
        verify(logProcessorService).processLog(eq(testLogEntry));
        verify(channel, never()).basicAck(anyLong(), anyBoolean());
        verify(channel).basicNack(eq(deliveryTag), eq(false), eq(false));
    }

    @Test
    void testConsumeLog_ProcessingExceptionAndNackFails() throws IOException {
        // Given
        doThrow(new RuntimeException("Processing error"))
                .when(logProcessorService).processLog(any(LogEntryDTO.class));
        doThrow(new IOException("Nack failed"))
                .when(channel).basicNack(eq(deliveryTag), eq(false), eq(false));

        // When - should not throw, just log error
        assertDoesNotThrow(() -> {
            logConsumer.consumeLog(testLogEntry, channel, deliveryTag, message);
        });

        // Then
        verify(logProcessorService).processLog(eq(testLogEntry));
        verify(channel).basicNack(eq(deliveryTag), eq(false), eq(false));
    }
}
