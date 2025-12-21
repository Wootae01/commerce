package com.commerce.util;

import java.util.Map;

import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.databind.JsonNode;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class TossPaymentClient {

	private final WebClient webClient;

	public JsonNode cancel(String paymentKey, Map<String, Object> data) {
		return webClient.post()
			.uri("/v1/payments/{paymentKey}/cancel", paymentKey)
			.bodyValue(data)
			.retrieve()
			.bodyToMono(JsonNode.class)
			.block();
	}
}
