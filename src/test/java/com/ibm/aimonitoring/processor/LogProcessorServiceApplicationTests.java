package com.ibm.aimonitoring.processor;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.ibm.aimonitoring.processor.consumer.LogConsumer;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Integration test for Spring Boot application context.
 * Uses mocked external dependencies to avoid requiring actual services.
 * Uses Testcontainers to provide a PostgreSQL database for testing.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class LogProcessorServiceApplicationTests {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("aimonitoring")
            .withUsername("postgres")
            .withPassword("postgres")
            .withInitScript("schema.sql");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.jpa.properties.hibernate.default_schema", () -> "ml_service");
        registry.add("spring.rabbitmq.host", () -> "localhost");
        registry.add("spring.rabbitmq.port", () -> "5672");
    }

    @MockBean
    private RabbitTemplate rabbitTemplate;
    
    @MockBean
    private RabbitAdmin rabbitAdmin;
    
    @MockBean
    private ElasticsearchClient elasticsearchClient;
    
    @MockBean
    private RestTemplate restTemplate;
    
    @MockBean
    private LogConsumer logConsumer;

    @Test
    void contextLoads() {
        // This test verifies that the Spring application context loads successfully
        // with mocked external dependencies (RabbitMQ, Elasticsearch, RestTemplate)
        // and a real PostgreSQL database provided by Testcontainers
    }
}

// Made with Bob
