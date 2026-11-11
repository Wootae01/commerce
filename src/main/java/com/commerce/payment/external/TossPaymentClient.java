package com.commerce.payment.external;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import com.commerce.common.code.ExternalResponseCode;
import com.commerce.common.exception.ApiException;
import com.commerce.payment.dto.PayConfirmDTO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

// 연결 실패는 TossConnectionException으로 감싸, 호출하는 쪽이 HTTP 라이브러리를 모르게 한다
@Component
@RequiredArgsConstructor
@Slf4j
public class TossPaymentClient {

	private final RestClient tossRestClient;
	private final ObjectMapper objectMapper;

	public JsonNode cancel(String paymentKey, Map<String, Object> data) {
		try {
			return tossRestClient.post()
				.uri("/v1/payments/{paymentKey}/cancel", paymentKey)
				.body(data)
				.retrieve()
				.body(JsonNode.class);
		} catch (RestClientResponseException e) {
			log.error("toss cancel failed: status={}, body={}", e.getStatusCode(), e.getResponseBodyAsString(), e);
			throw new ApiException(ExternalResponseCode.PG_CANCEL_ERROR);
		} catch (RestClientException e) {
			log.error("toss cancel exception", e);
			throw connectionFailureOrSelf(e, "toss cancel connection failed");
		}
	}

	// paymentKey로 조회하면 승인 전 결제도 IN_PROGRESS로 보인다. 주문번호 조회는 테스트 키에서 404만 돌아왔다(#10)
	public JsonNode getPayment(String paymentKey) {
		try {
			return tossRestClient.get()
				.uri("/v1/payments/{paymentKey}", paymentKey)
				.retrieve()
				.body(JsonNode.class);
		} catch (RestClientResponseException e) {
			log.error("toss getPayment failed: status={}, body={}", e.getStatusCode(), e.getResponseBodyAsString(), e);
			throw new ApiException(ExternalResponseCode.PG_QUERY_ERROR);
		} catch (RestClientException e) {
			log.error("toss getPayment exception", e);
			throw connectionFailureOrSelf(e, "toss getPayment connection failed");
		}
	}

	public JsonNode confirm(PayConfirmDTO req) {
		try {
			return tossRestClient.post()
				.uri("/v1/payments/confirm")
				// 같은 키로 다시 요청하면 토스가 한 번만 승인하고 같은 응답을 돌려준다
				.header("Idempotency-Key", req.getOrderId())
				.body(new PayConfirmDTO(req.getPaymentKey(), req.getOrderId(), req.getAmount()))
				.retrieve()
				.onStatus(HttpStatusCode::isError, (request, response) -> {
					String body = new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8);
					log.error("toss confirm error: status={}, body={}", response.getStatusCode(), body);
					throw new TossApiException(ExternalResponseCode.PG_APPROVAL_ERROR, response.getStatusCode(),
						errorField(body, "code"), errorField(body, "message"));
				})
				.body(JsonNode.class);
		} catch (RestClientException e) {
			log.error("toss confirm exception", e);
			throw connectionFailureOrSelf(e, "toss confirm connection failed");
		}
	}

	// 응답을 끝까지 받지 못했으면(연결 실패·끊김·타임아웃) TossConnectionException, 아니면(응답 해석 실패 등) 그대로
	// HttpURLConnection은 POST 응답을 본문을 읽을 때 받으므로, 끊김·타임아웃이 ResourceAccessException이 아니라
	// "응답 읽기 실패"(RestClientException)의 원인(IOException)으로 온다. JSON 해석 오류도 IOException이라 따로 뺀다
	private static RuntimeException connectionFailureOrSelf(RestClientException e, String message) {
		if (e instanceof ResourceAccessException) {
			return new TossConnectionException(message, e);
		}
		for (Throwable cause = e.getCause(); cause != null; cause = cause.getCause()) {
			if (cause instanceof JsonProcessingException) {
				return e;
			}
			if (cause instanceof IOException) {
				return new TossConnectionException(message, e);
			}
		}
		return e;
	}

	// 토스 에러 응답 본문 { "code": ..., "message": ... }에서 한 항목을 꺼낸다
	private String errorField(String body, String field) {
		try {
			return objectMapper.readTree(body).path(field).asText();
		} catch (Exception e) {
			return "";
		}
	}
}
