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

    /**
     * AI 서비스(OCR + Rule Engine) 호출용 RestClient.
     * OCR + 룰엔진 + 결과생성 파이프라인이 길어 read-timeout 을 넉넉히 둔다.
     */
    @Bean
    public RestClient aiServiceRestClient(
            RestClient.Builder builder,
            @Value("${app.ai-service.base-url:http://localhost:8000}") String aiServiceBaseUrl,
            @Value("${app.ai-service.connect-timeout:3s}") Duration connectTimeout,
            @Value("${app.ai-service.read-timeout:30s}") Duration readTimeout) {

        return builder.baseUrl(aiServiceBaseUrl)
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
