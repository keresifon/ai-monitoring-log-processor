package com.ibm.aimonitoring.processor.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import org.apache.http.HttpHost;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder;
import org.apache.http.impl.nio.reactor.IOReactorConfig;
import org.elasticsearch.client.RestClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Elasticsearch configuration. Tunes the low-level HTTP client (Apache HttpClient 4.x
 * used by Elasticsearch {@code RestClient}): TCP keep-alive and request timeouts help
 * when load balancers or Elasticsearch close idle connections.
 * <p>
 * {@code elasticsearch.scheme} must match the server (HTTP vs HTTPS); TLS endpoints will
 * close plaintext connections, which surfaces as {@code ConnectionClosedException} on the client.
 */
@Configuration
public class ElasticsearchConfig {

    @Bean(destroyMethod = "close")
    public RestClient restClient(
            @Value("${elasticsearch.host:localhost}") String host,
            @Value("${elasticsearch.port:9200}") int port,
            @Value("${elasticsearch.scheme:http}") String scheme,
            @Value("${elasticsearch.connect-timeout-ms:10000}") int connectTimeoutMs,
            @Value("${elasticsearch.socket-timeout-ms:120000}") int socketTimeoutMs) {

        String normalizedScheme = scheme == null ? "http" : scheme.trim().toLowerCase();
        if (!"http".equals(normalizedScheme) && !"https".equals(normalizedScheme)) {
            throw new IllegalArgumentException(
                    "elasticsearch.scheme must be 'http' or 'https', got: " + scheme);
        }

        return RestClient.builder(new HttpHost(host, port, normalizedScheme))
                .setHttpClientConfigCallback((HttpAsyncClientBuilder builder) -> {
                    builder.setDefaultIOReactorConfig(
                            IOReactorConfig.custom()
                                    .setSoKeepAlive(true)
                                    .build());
                    builder.setDefaultRequestConfig(
                            RequestConfig.custom()
                                    .setConnectTimeout(connectTimeoutMs)
                                    .setSocketTimeout(socketTimeoutMs)
                                    .build());
                    return builder;
                })
                .build();
    }

    @Bean
    public ElasticsearchClient elasticsearchClient(RestClient restClient) {
        RestClientTransport transport = new RestClientTransport(
                restClient,
                new JacksonJsonpMapper()
        );
        return new ElasticsearchClient(transport);
    }
}
