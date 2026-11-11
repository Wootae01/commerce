package com.commerce.order.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.commerce.common.code.GeneralResponseCode;
import com.commerce.common.dto.GeneralResponse;
import com.commerce.common.dto.PageResponse;
import com.commerce.common.util.SecurityUtil;
import com.commerce.order.domain.Orders;
import com.commerce.order.dto.OrderCheckoutResponse;
import com.commerce.order.dto.OrderCreateRequestDTO;
import com.commerce.order.dto.OrderDetailResponseDTO;
import com.commerce.order.dto.OrderMapper;
import com.commerce.order.dto.OrderPrepareResponseDTO;
import com.commerce.order.dto.OrderResponseDTO;
import com.commerce.order.service.OrderCheckoutService;
import com.commerce.order.service.OrderService;
import com.commerce.payment.dto.CancelResponseDTO;
import com.commerce.payment.service.PayService;
import com.commerce.user.domain.User;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/orders")
public class OrderApiController {

	private final OrderService orderService;
	private final OrderCheckoutService orderCheckoutService;
	private final OrderMapper orderMapper;
	private final PayService payService;
	private final SecurityUtil securityUtil;

	// 장바구니에서 선택한 상품으로 주문서 조회
	@GetMapping("/checkout/cart")
	public ResponseEntity<GeneralResponse<OrderCheckoutResponse>> checkoutFromCart() {
		User user = securityUtil.getCurrentUser();
		return GeneralResponse.toResponseEntity(GeneralResponseCode.OK, orderCheckoutService.getCartCheckout(user));
	}

	// 바로구매 주문서 조회
	@GetMapping("/checkout/buy-now")
	public ResponseEntity<GeneralResponse<OrderCheckoutResponse>> checkoutBuyNow(@RequestParam Long productId,
		@RequestParam(required = false) Long optionId, @RequestParam @Min(1) int quantity) {

		User user = securityUtil.getCurrentUser();
		return GeneralResponse.toResponseEntity(GeneralResponseCode.OK,
			orderCheckoutService.getBuyNowCheckout(user, productId, optionId, quantity));
	}

	// 결제 전 주문 생성. 응답 값으로 토스 결제창을 띄운다.
	@PostMapping
	public ResponseEntity<GeneralResponse<OrderPrepareResponseDTO>> createOrder(
		@Valid @RequestBody OrderCreateRequestDTO request) {

		Orders order = orderService.prepareOrder(request);
		return GeneralResponse.toResponseEntity(GeneralResponseCode.CREATED,
			orderMapper.toOrderPrepareResponseDTO(order));
	}

	@GetMapping
	public ResponseEntity<GeneralResponse<PageResponse<OrderResponseDTO>>> getOrders(
		@RequestParam(defaultValue = "0") @Min(0) int page,
		@RequestParam(defaultValue = "10") @Min(1) @Max(100) int size) {

		User user = securityUtil.getCurrentUser();
		Page<OrderResponseDTO> result = orderService.findOrderList(user, PageRequest.of(page, size));
		return GeneralResponse.toResponseEntity(GeneralResponseCode.OK, PageResponse.from(result));
	}

	@GetMapping("/{orderNumber}")
	public ResponseEntity<GeneralResponse<OrderDetailResponseDTO>> getOrder(@PathVariable String orderNumber) {
		User user = securityUtil.getCurrentUser();
		return GeneralResponse.toResponseEntity(GeneralResponseCode.OK,
			orderService.getMyOrderDetail(orderNumber, user));
	}

	// 결제한 주문이면 토스 결제 취소까지 진행한다. 200 취소 완료, 202 취소 처리 중
	@PostMapping("/{orderNumber}/cancel")
	public ResponseEntity<GeneralResponse<CancelResponseDTO>> cancelOrder(@PathVariable String orderNumber) {
		Long userId = securityUtil.getCurrentUser().getId();
		CancelResponseDTO result = payService.cancel(orderNumber, userId);
		return GeneralResponse.toResponseEntity(CancelResponseCode.from(result.getOrderStatus()), result);
	}
}
