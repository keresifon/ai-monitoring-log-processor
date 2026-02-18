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
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;

/**
 * Integration test for Spring Boot application context.
 * Uses Testcontainers for PostgreSQL and RabbitMQ; mocks Elasticsearch, RestTemplate, and LogConsumer.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class LogProcessorServiceApplicationTests {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("aimonitoring")
            .withUsername("postgres")
            .withPassword("postgres")
            .withInitScript("schema.sql")
            .waitingFor(Wait.forListeningPort())
            .withStartupTimeout(Duration.ofMinutes(2));

    @Container
    static RabbitMQContainer rabbit = new RabbitMQContainer(DockerImageName.parse("rabbitmq:3.12-management-alpine"))
            .withExposedPorts(5672)
            .waitingFor(Wait.forListeningPort())
            .withStartupTimeout(Duration.ofMinutes(2));

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.jpa.properties.hibernate.default_schema", () -> "ml_service");
        registry.add("spring.rabbitmq.host", rabbit::getHost);
        registry.add("spring.rabbitmq.port", () -> String.valueOf(rabbit.getAmqpPort()));
        registry.add("spring.rabbitmq.username", rabbit::getAdminUsername);
        registry.add("spring.rabbitmq.password", rabbit::getAdminPassword);
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
        // Verifies that the Spring application context loads with Testcontainers (PostgreSQL, RabbitMQ)
        // and mocked Elasticsearch, RestTemplate, and LogConsumer
        org.junit.jupiter.api.Assertions.assertNotNull(postgres, "PostgreSQL container should be initialized");
        org.junit.jupiter.api.Assertions.assertNotNull(postgres.getJdbcUrl(), "PostgreSQL container should provide JDBC URL");
    }
}

// Made with Bob
