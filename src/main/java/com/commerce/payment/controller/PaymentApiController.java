package com.commerce.payment.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.commerce.common.code.ApiResponseCode;
import com.commerce.common.code.GeneralResponseCode;
import com.commerce.common.dto.GeneralResponse;
import com.commerce.common.enums.OrderStatus;
import com.commerce.common.util.SecurityUtil;
import com.commerce.payment.dto.PayConfirmDTO;
import com.commerce.payment.dto.PaySuccessDTO;
import com.commerce.payment.service.PayService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/payments")
public class PaymentApiController {

	private final PayService payService;
	private final SecurityUtil securityUtil;

	// 토스 결제창에서 돌아온 뒤 결제 승인
	@PostMapping("/confirm")
	// 성공·확인 중이면 GeneralResponse<PaySuccessDTO>, 결제 실패면 ErrorResponse
	public ResponseEntity<?> confirm(@RequestBody PayConfirmDTO request) {
		log.info("confirm req: orderId={}, paymentKey={}, amount={}",
			request.getOrderId(), request.getPaymentKey(), request.getAmount());
		Long userId = securityUtil.getCurrentUser().getId();
		OrderStatus status = payService.confirm(request, userId);

		ApiResponseCode code = PayConfirmResponseCode.from(status);
		PaySuccessDTO result = payService.getPaymentResult(request.getOrderId(), userId);
		if (code.getStatus().isError()) {
			return PayConfirmResponseCode.rejected(code, result);
		}

		return GeneralResponse.toResponseEntity(code, result);
	}

	// 결제 결과 조회
	@GetMapping("/{orderNumber}")
	public ResponseEntity<GeneralResponse<PaySuccessDTO>> getPayment(@PathVariable String orderNumber) {
		Long userId = securityUtil.getCurrentUser().getId();
		return GeneralResponse.toResponseEntity(GeneralResponseCode.OK, payService.getPaymentResult(orderNumber, userId));
	}
}
