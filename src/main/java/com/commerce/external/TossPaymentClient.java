package com.commerce.external;

import java.util.Map;

import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import com.commerce.dto.PayConfirmDTO;
import com.fasterxml.jackson.databind.JsonNode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class TossPaymentClient {

	private final WebClient tossWebClient;

	public JsonNode cancel(String paymentKey, Map<String, Object> data) {
		return tossWebClient.post()
			.uri("/v1/payments/{paymentKey}/cancel", paymentKey)
			.bodyValue(data)
			.retrieve()
			.bodyToMono(JsonNode.class)
			.block();
	}

	public JsonNode confirm(PayConfirmDTO req) {
		JsonNode tossResponse;
		try{
			tossResponse = tossWebClient.post()
				.uri("/v1/payments/confirm")
				.bodyValue(new PayConfirmDTO(req.getPaymentKey(), req.getOrderId(), req.getAmount()))
				.retrieve()
				.onStatus(HttpStatusCode::isError, res ->
					res.bodyToMono(String.class)
						.defaultIfEmpty("")
						.map(body -> new ResponseStatusException(res.statusCode(),
							"toss confirm error: " + body))
				)
				.bodyToMono(JsonNode.class)
				.block();
		} catch (WebClientResponseException e) {
			log.error("toss confirm failed: status={}, body={}", e.getStatusCode(), e.getResponseBodyAsString(), e);
			throw new ResponseStatusException(e.getStatusCode(), "토스 승인 실패");
		} catch (Exception e) {
			log.error("toss confirm exception", e);
			throw e;

		}


		return tossResponse;
	}
}
