package com.ibm.aimonitoring.processor;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.ibm.aimonitoring.processor.consumer.LogConsumer;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.client.RestTemplate;

/**
 * Integration test for Spring Boot application context.
 * Uses mocked external dependencies to avoid requiring actual services.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:postgresql://localhost:5432/aimonitoring",
    "spring.datasource.username=postgres",
    "spring.datasource.password=postgres",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect",
    "spring.rabbitmq.host=localhost",
    "spring.rabbitmq.port=5672"
})
class LogProcessorServiceApplicationTests {

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
    }
}

// Made with Bob
