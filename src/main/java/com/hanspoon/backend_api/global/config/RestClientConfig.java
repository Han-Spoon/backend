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

	@Bean
	public RestClient azureAiRestClient(
			RestClient.Builder builder,
			@Value("${app.azure-ai.endpoint:https://han-spoon-openai.openai.azure.com}") String azureAiEndpoint,
			@Value("${app.azure-ai.api-key:}") String azureAiApiKey,
			@Value("${app.rest-client.connect-timeout:3s}") Duration connectTimeout,
			@Value("${app.rest-client.read-timeout:10s}") Duration readTimeout) {

		return builder
				.baseUrl(azureAiEndpoint)
				.requestFactory(clientHttpRequestFactory(connectTimeout, readTimeout))
				.defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
				.defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
				.defaultHeader("api-key", azureAiApiKey)
				.build();
	}

	private SimpleClientHttpRequestFactory clientHttpRequestFactory(Duration connectTimeout, Duration readTimeout) {
		SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
		requestFactory.setConnectTimeout(connectTimeout);
		requestFactory.setReadTimeout(readTimeout);
		return requestFactory;
	}
}
