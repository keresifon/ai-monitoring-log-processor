package com.ibm.aimonitoring.processor.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Configuration for async processing
 */
@Configuration
@EnableAsync
public class AsyncConfig {
    // Async configuration is handled by application.yml
    // spring.task.execution properties
}

// Made with Bob
