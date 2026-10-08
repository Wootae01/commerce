package com.commerce.payment.external;

import java.util.Map;

import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import com.commerce.common.code.ExternalResponseCode;
import com.commerce.common.exception.ApiException;
import com.commerce.payment.dto.PayConfirmDTO;
import com.fasterxml.jackson.databind.JsonNode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class TossPaymentClient {

	private final WebClient tossWebClient;

	public JsonNode cancel(String paymentKey, Map<String, Object> data) {
		JsonNode jsonNode;
		try {
			jsonNode = tossWebClient.post()
				.uri("/v1/payments/{paymentKey}/cancel", paymentKey)
				.bodyValue(data)
				.retrieve()
				.bodyToMono(JsonNode.class)
				.block();
		} catch (WebClientResponseException e) {
			log.error("toss cancel failed: status={}, body={}", e.getStatusCode(), e.getResponseBodyAsString(), e);
			throw new ApiException(ExternalResponseCode.PG_CANCEL_ERROR);
		} catch (Exception e) {
			log.error("toss cancel exception", e);
			throw e;
		}
		return jsonNode;
	}

	public JsonNode getPayment(String orderId) {
		try {
			return tossWebClient.get()
				.uri("/v1/payments/orders/{orderId}", orderId)
				.retrieve()
				.bodyToMono(JsonNode.class)
				.block();
		} catch (WebClientResponseException e) {
			log.error("toss getPayment failed: status={}, body={}", e.getStatusCode(), e.getResponseBodyAsString(), e);
			throw new ApiException(ExternalResponseCode.PG_QUERY_ERROR);
		} catch (Exception e) {
			log.error("toss getPayment exception", e);
			throw e;
		}
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
						.map(body -> {
							log.error("toss confirm error: status={}, body={}", res.statusCode(), body);
							return new ApiException(ExternalResponseCode.PG_APPROVAL_ERROR);
						})
				)
				.bodyToMono(JsonNode.class)
				.block();
		} catch (WebClientResponseException e) {
			log.error("toss confirm failed: status={}, body={}", e.getStatusCode(), e.getResponseBodyAsString(), e);
			throw new ApiException(ExternalResponseCode.PG_APPROVAL_ERROR);
		} catch (Exception e) {
			log.error("toss confirm exception", e);
			throw e;

		}


		return tossResponse;
	}
}
