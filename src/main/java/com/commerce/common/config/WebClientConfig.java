package com.commerce.common.config;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import io.netty.channel.ChannelOption;
import reactor.netty.http.client.HttpClient;

@Configuration
public class WebClientConfig {

	@Bean
	public WebClient tossWebClient(@Value("${toss.payments.secret-key}") String secretKey, @Value("${toss.base-url}") String baseUrl) {
		String basic = Base64.getEncoder()
			.encodeToString((secretKey + ":").getBytes(StandardCharsets.UTF_8));

		// 응답을 모르면 1번 재요청하므로, 최악 대기 시간은 (연결 3초 + 응답 10초) × 2
		HttpClient httpClient = HttpClient.create()
				.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 3000) // 연결 최대 3초 (기본 30초)
				.responseTimeout(Duration.ofSeconds(10)); // 응답 대기 최대 10초

		return WebClient.builder()
				.baseUrl(baseUrl)
				.clientConnector(new ReactorClientHttpConnector(httpClient))
				.defaultHeader(HttpHeaders.AUTHORIZATION, "Basic " + basic)
				.defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
				.build();
	}
}
