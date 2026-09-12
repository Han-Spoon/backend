package com.hanspoon.backend_api.global.config;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfig {

    @Bean
    public RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }

    @Bean("aiOcrRestClient")
    public RestClient aiOcrRestClient(
            RestClient.Builder builder,
            @Value("${app.ai-service.base-url:http://localhost:8000}") String aiServiceBaseUrl,
            @Value("${app.ai-service.connect-timeout:500ms}") Duration connectTimeout,
            @Value("${app.ai-service.ocr-read-timeout:18s}") Duration readTimeout) {
        return aiClient(builder, aiServiceBaseUrl, connectTimeout, readTimeout);
    }

    @Bean("aiRuleEngineRestClient")
    public RestClient aiRuleEngineRestClient(
            RestClient.Builder builder,
            @Value("${app.ai-service.base-url:http://localhost:8000}") String aiServiceBaseUrl,
            @Value("${app.ai-service.connect-timeout:500ms}") Duration connectTimeout,
            @Value("${app.ai-service.rule-engine-read-timeout:2s}") Duration readTimeout) {
        return aiClient(builder, aiServiceBaseUrl, connectTimeout, readTimeout);
    }

    @Bean("aiResultRestClient")
    public RestClient aiResultRestClient(
            RestClient.Builder builder,
            @Value("${app.ai-service.base-url:http://localhost:8000}") String aiServiceBaseUrl,
            @Value("${app.ai-service.connect-timeout:500ms}") Duration connectTimeout,
            @Value("${app.ai-service.result-read-timeout:7s}") Duration readTimeout) {
        return aiClient(builder, aiServiceBaseUrl, connectTimeout, readTimeout);
    }

    private RestClient aiClient(
            RestClient.Builder builder, String baseUrl, Duration connectTimeout, Duration readTimeout) {
        return builder.clone()
                .baseUrl(baseUrl)
                .requestFactory(clientHttpRequestFactory(connectTimeout, readTimeout))
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    private SimpleClientHttpRequestFactory clientHttpRequestFactory(Duration connectTimeout, Duration readTimeout) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeout);
        requestFactory.setReadTimeout(readTimeout);
        return requestFactory;
    }
}
