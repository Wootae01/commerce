package com.commerce.order.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
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

import com.commerce.cart.domain.CartProduct;
import com.commerce.cart.service.CartService;
import com.commerce.common.code.GeneralResponseCode;
import com.commerce.common.dto.GeneralResponse;
import com.commerce.common.dto.PageResponse;
import com.commerce.common.enums.OrderType;
import com.commerce.common.exception.ApiException;
import com.commerce.common.util.SecurityUtil;
import com.commerce.order.domain.Orders;
import com.commerce.order.dto.OrderCheckoutResponse;
import com.commerce.order.dto.OrderCreateRequestDTO;
import com.commerce.order.dto.OrderDetailResponseDTO;
import com.commerce.order.dto.OrderItemDTO;
import com.commerce.order.dto.OrderMapper;
import com.commerce.order.dto.OrderPrepareResponseDTO;
import com.commerce.order.dto.OrderPriceDTO;
import com.commerce.order.dto.OrderResponseDTO;
import com.commerce.order.service.OrderService;
import com.commerce.payment.dto.CancelResponseDTO;
import com.commerce.payment.service.PayService;
import com.commerce.product.domain.DeliveryPolicy;
import com.commerce.product.domain.Product;
import com.commerce.product.domain.ProductOption;
import com.commerce.product.service.ProductService;
import com.commerce.user.domain.User;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/orders")
public class OrderApiController {

	private static final String CANCEL_REASON = "단순 변심";

	private final OrderService orderService;
	private final OrderMapper orderMapper;
	private final CartService cartService;
	private final ProductService productService;
	private final PayService payService;
	private final SecurityUtil securityUtil;

	@Value("${toss.payments.client-key}")
	private String tossClientKey;

	// 장바구니에서 선택한 상품으로 주문서 조회
	@GetMapping("/checkout/cart")
	public ResponseEntity<GeneralResponse<OrderCheckoutResponse>> checkoutFromCart() {
		List<CartProduct> cartProducts = cartService.getSelectedProduct();
		List<OrderItemDTO> items = orderMapper.toOrderItemDTOFromCart(cartProducts);
		int totalPrice = cartService.getTotalPrice(cartProducts);

		return GeneralResponse.toResponseEntity(GeneralResponseCode.OK,
			buildCheckout(OrderType.CART, items, totalPrice));
	}

	// 바로구매 주문서 조회
	@GetMapping("/checkout/buy-now")
	public ResponseEntity<GeneralResponse<OrderCheckoutResponse>> checkoutBuyNow(@RequestParam Long productId,
		@RequestParam(required = false) Long optionId, @RequestParam @Min(1) int quantity) {

		Product product = productService.findById(productId);
		ProductOption option = optionId != null ? productService.findOptionById(optionId) : null;
		if (option != null && !option.getProduct().getId().equals(productId)) {
			throw new ApiException(GeneralResponseCode.PRODUCT_OPTION_MISMATCH);
		}

		OrderItemDTO item = orderMapper.toOrderItemDTOFromCart(product, quantity, option);
		return GeneralResponse.toResponseEntity(GeneralResponseCode.OK,
			buildCheckout(OrderType.BUY_NOW, List.of(item), item.getTotalPrice()));
	}

	// 결제 전 주문 생성. 응답 값으로 토스 결제창을 띄운다.
	@PostMapping
	public ResponseEntity<GeneralResponse<OrderPrepareResponseDTO>> createOrder(
		@Valid @RequestBody OrderCreateRequestDTO request) {

		Orders order = switch (request.getOrderType()) {
			case CART -> orderService.prepareOrderFromCart(request);
			case BUY_NOW -> orderService.prepareOrderFromBuyNow(request);
		};
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
		Orders order = orderService.findMyOrder(orderNumber, user.getId());
		return GeneralResponse.toResponseEntity(GeneralResponseCode.OK,
			orderMapper.toOrderDetailResponseDTO(order, user));
	}

	// 결제한 주문이면 토스 결제 취소까지 진행한다.
	@PostMapping("/{orderNumber}/cancel")
	public ResponseEntity<GeneralResponse<CancelResponseDTO>> cancelOrder(@PathVariable String orderNumber) {
		Long userId = securityUtil.getCurrentUser().getId();
		CancelResponseDTO result = payService.cancel(orderNumber, CANCEL_REASON, userId);
		return GeneralResponse.toResponseEntity(GeneralResponseCode.OK, result);
	}

	private OrderCheckoutResponse buildCheckout(OrderType orderType, List<OrderItemDTO> items, int totalPrice) {
		int deliveryFee = DeliveryPolicy.DELIVERY_FEE;
		OrderPriceDTO orderPrice = new OrderPriceDTO(totalPrice, deliveryFee, totalPrice + deliveryFee);
		User user = securityUtil.getCurrentUser();

		return new OrderCheckoutResponse(orderType, items, OrderCheckoutResponse.Orderer.from(user), orderPrice,
			tossClientKey);
	}
}
