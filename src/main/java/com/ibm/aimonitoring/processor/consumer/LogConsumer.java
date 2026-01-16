package com.ibm.aimonitoring.processor.consumer;

import com.ibm.aimonitoring.processor.dto.LogEntryDTO;
import com.ibm.aimonitoring.processor.service.LogProcessorService;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * RabbitMQ consumer for processing log entries
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LogConsumer {

    private final LogProcessorService logProcessorService;

    /**
     * Consume and process log entries from RabbitMQ
     *
     * @param logEntry the log entry to process
     * @param channel the RabbitMQ channel
     * @param deliveryTag the message delivery tag
     */
    @RabbitListener(queues = "${rabbitmq.queue.name:logs.raw}")
    public void consumeLog(
            LogEntryDTO logEntry,
            Channel channel,
            @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag,
            Message message) {
        
        try {
            log.debug("Received log from queue: service={}, level={}", 
                    logEntry.getService(), logEntry.getLevel());

            // Process the log entry
            logProcessorService.processLog(logEntry);

            // Manually acknowledge the message
            channel.basicAck(deliveryTag, false);
            
            log.debug("Log processed and acknowledged: {}", deliveryTag);

        } catch (Exception e) {
            log.error("Error processing log: {}", e.getMessage(), e);
            
            try {
                // Reject and requeue the message (will go to DLQ after max retries)
                channel.basicNack(deliveryTag, false, false);
                log.warn("Log message rejected and sent to DLQ: {}", deliveryTag);
            } catch (IOException ioException) {
                log.error("Failed to reject message: {}", ioException.getMessage(), ioException);
            }
        }
    }
}

// Made with Bob
