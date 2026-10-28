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
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class TossPaymentClient {

	private final WebClient tossWebClient;
	private final ObjectMapper objectMapper;

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

	// paymentKey로 조회하면 승인 전 결제도 IN_PROGRESS로 보인다. 주문번호 조회는 테스트 키에서 404만 돌아왔다(#10)
	public JsonNode getPayment(String paymentKey) {
		try {
			return tossWebClient.get()
				.uri("/v1/payments/{paymentKey}", paymentKey)
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
				// 같은 키로 다시 요청하면 토스가 한 번만 승인하고 같은 응답을 돌려준다
				.header("Idempotency-Key", req.getOrderId())
				.bodyValue(new PayConfirmDTO(req.getPaymentKey(), req.getOrderId(), req.getAmount()))
				.retrieve()
				.onStatus(HttpStatusCode::isError, res ->
					res.bodyToMono(String.class)
						.defaultIfEmpty("")
						.map(body -> {
							log.error("toss confirm error: status={}, body={}", res.statusCode(), body);
							return new TossApiException(ExternalResponseCode.PG_APPROVAL_ERROR, res.statusCode(), errorCode(body));
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

	// 토스 에러 응답 본문 { "code": ..., "message": ... }에서 code를 꺼낸다
	private String errorCode(String body) {
		try {
			return objectMapper.readTree(body).path("code").asText();
		} catch (Exception e) {
			return "";
		}
	}
}
