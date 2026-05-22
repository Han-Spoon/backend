package com.hanspoon.backend_api.global.ratelimit;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;

@Configuration
public class Bucket4jConfig {

	@Bean
	public Map<String, Bucket> openAiRateLimitBuckets() {
		return new ConcurrentHashMap<>();
	}

	@Bean
	public OpenAiBucketFactory openAiBucketFactory(
			@Value("${app.ratelimit.openai.capacity:30}") long capacity,
			@Value("${app.ratelimit.openai.refill-period:1m}") Duration refillPeriod) {

		Bandwidth limit = Bandwidth.builder()
				.capacity(capacity)
				.refillGreedy(capacity, refillPeriod)
				.build();

		return () -> Bucket.builder()
				.addLimit(limit)
				.build();
	}

	@FunctionalInterface
	public interface OpenAiBucketFactory {

		Bucket create();
	}
}
