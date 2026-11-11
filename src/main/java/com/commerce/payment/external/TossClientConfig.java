package com.commerce.payment.external;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.web.client.RestClientBuilderConfigurer;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

@Configuration
public class TossClientConfig {

	@Bean
	public RestClient tossRestClient(
		RestClientBuilderConfigurer configurer,
		@Value("${toss.payments.secret-key}") String secretKey,
		@Value("${toss.base-url}") String baseUrl,
		@Value("${toss.connect-timeout}") Duration connectTimeout,
		@Value("${toss.read-timeout}") Duration readTimeout) {
		String basic = Base64.getEncoder()
			.encodeToString((secretKey + ":").getBytes(StandardCharsets.UTF_8));

		// 응답을 모르면 1번 재요청하므로, 최악 대기 시간은 (연결 + 응답 타임아웃) × 2
		ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.defaults()
			.withConnectTimeout(connectTimeout)
			.withReadTimeout(readTimeout);

		// HTTP 라이브러리를 직접 정한다. 테스트 클래스패스에는 WireMock이 가져온 Apache·Jetty 클라이언트가 있어서,
		// Boot가 고르게 두면 테스트와 운영이 다른 라이브러리로 돈다. JDK HttpClient로 바꾸려면 simple()을 jdk()로
		// RestClient.builder()로 바로 만들면 Boot 자동설정(http.client.requests 메트릭 등)이 빠지므로 configurer를 거친다
		return configurer.configure(RestClient.builder())
			.requestFactory(ClientHttpRequestFactoryBuilder.simple().build(settings))
			.baseUrl(baseUrl)
			.defaultHeader(HttpHeaders.AUTHORIZATION, "Basic " + basic)
			.defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
			.build();
	}
}
