package com.ibm.aimonitoring.processor.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import org.apache.http.HttpHost;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.config.ConnectionConfig;
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder;
import org.apache.http.impl.nio.reactor.IOReactorConfig;
import org.elasticsearch.client.RestClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Elasticsearch configuration. Tunes the low-level HTTP client so idle connections are
 * validated and replaced before use, which avoids {@code Connection is closed} when a
 * load balancer or Elasticsearch closes idle sockets while the pool still holds them.
 */
@Configuration
public class ElasticsearchConfig {

    @Bean(destroyMethod = "close")
    public RestClient restClient(
            @Value("${elasticsearch.host:localhost}") String host,
            @Value("${elasticsearch.port:9200}") int port,
            @Value("${elasticsearch.connect-timeout-ms:10000}") int connectTimeoutMs,
            @Value("${elasticsearch.socket-timeout-ms:120000}") int socketTimeoutMs,
            @Value("${elasticsearch.validate-after-inactivity-ms:10000}") int validateAfterInactivityMs) {

        return RestClient.builder(new HttpHost(host, port, "http"))
                .setHttpClientConfigCallback((HttpAsyncClientBuilder builder) -> {
                    builder.setDefaultIOReactorConfig(
                            IOReactorConfig.custom()
                                    .setSoKeepAlive(true)
                                    .build());
                    builder.setDefaultConnectionConfig(
                            ConnectionConfig.custom()
                                    .setValidateAfterInactivity(validateAfterInactivityMs)
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
