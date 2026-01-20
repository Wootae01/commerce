package com.commerce.controller;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.commerce.domain.CartProduct;
import com.commerce.domain.DeliveryPolicy;
import com.commerce.domain.Orders;
import com.commerce.domain.Product;
import com.commerce.domain.enums.OrderType;
import com.commerce.dto.CancelResponseDTO;
import com.commerce.dto.OrderCreateRequestDTO;
import com.commerce.dto.OrderItemDTO;
import com.commerce.dto.OrderPrepareResponseDTO;
import com.commerce.dto.OrderPriceDTO;
import com.commerce.dto.PayConfirmDTO;
import com.commerce.dto.PaySuccessDTO;
import com.commerce.mapper.OrderMapper;
import com.commerce.service.CartService;
import com.commerce.service.OrderService;
import com.commerce.service.PayService;
import com.commerce.service.ProductService;
import com.commerce.util.SecurityUtil;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Controller
@RequestMapping("/pay")
@RequiredArgsConstructor
@Slf4j
public class PayController {

	private final SecurityUtil securityUtil;
	private final PayService payService;
	private final OrderService orderService;
	private final OrderMapper orderMapper;
	private final CartService cartService;
	private final ProductService productService;

	@GetMapping("/loading")
	public String loading() {
		return "pay-loading";
	}

	@GetMapping("/fail")
	public String fail() {
		return "pay-fail";
	}

	@GetMapping("/success")
	public String success(@RequestParam("orderId") String orderNumber, Model model) {

		Orders order = orderService.findByOrderNumber(orderNumber);
		LocalDateTime time = order.getApprovedAt();
		String approvedAtText = time != null
			? time.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
			: "-";

		PaySuccessDTO dto = new PaySuccessDTO(orderNumber, order.getFinalPrice(),
			order.getPaymentType().getText(), approvedAtText);

		model.addAttribute("success", dto);

		return "pay-success";
	}

	// 결제 전 필요한 데이터 보내줌.
	@PostMapping("/prepare")
	@ResponseBody
	public ResponseEntity<?> orderPrepare(@Validated @ModelAttribute("orderForm") OrderCreateRequestDTO dto,
		BindingResult bindingResult, Model model) {

		if (bindingResult.hasErrors()) {
			log.info("orderPrepare validation failed: errorCount={}", bindingResult.getErrorCount());

			for (FieldError e : bindingResult.getFieldErrors()) {
				log.info("fieldError field={} rejectedValue={} code={} defaultMessage={}",
					e.getField(),
					e.getRejectedValue(),
					e.getCode(),              // NotBlank, Pattern, typeMismatch 등
					e.getDefaultMessage());
			}

			// DTO가 실제로 뭐로 바인딩됐는지도 같이 찍으면 더 확실함
			log.info("bound dto={}", dto);

			repopulateOrderView(dto, model);
			return ResponseEntity.badRequest().body(Map.of("message", "입력값 오류"));
		}

		Orders order = null;

		try {
			if (dto.getOrderType() == OrderType.CART) {
				order = orderService.prepareOrderFromCart(dto);
			} else if (dto.getOrderType() == OrderType.BUY_NOW) {
				order = orderService.prepareOrderFromBuyNow(dto);
			} else {
				return ResponseEntity.badRequest().body(Map.of("message", "잘못된 요청"));
			}

		} catch (IllegalArgumentException | NoSuchElementException e) {
			return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
		}

		OrderPrepareResponseDTO prepared = orderMapper.toOrderPrepareResponseDTO(order);

		return ResponseEntity.ok(prepared);

	}

	@RequestMapping(value = "/confirm")
	@ResponseBody
	public ResponseEntity<?> confirmPayment(@RequestBody PayConfirmDTO req){
		log.info("confirm req: orderId={}, paymentKey={}, amount={}",
			req.getOrderId(), req.getPaymentKey(), req.getAmount());
		Long userId = securityUtil.getCurrentUser().getId();
		payService.confirm(req, userId);

		return ResponseEntity.ok(Map.of("ok", true));
	}

	@RequestMapping(value = "/confirm/before")
	@ResponseBody
	public ResponseEntity<?> confirmPayment_before(@RequestBody PayConfirmDTO req){
		log.info("confirm req: orderId={}, paymentKey={}, amount={}",
			req.getOrderId(), req.getPaymentKey(), req.getAmount());
		Long userId = securityUtil.getCurrentUser().getId();
		payService.confirm_before(req, userId);

		return ResponseEntity.ok(Map.of("ok", true));
	}

	@PostMapping("/{orderNumber}/cancel")
	public String cancel(@PathVariable String orderNumber, Model model) {

		String cancelReason = "단순 변심";

		try {
			CancelResponseDTO dto = payService.cancel(orderNumber, cancelReason);
			model.addAttribute("result", dto);
		} catch (IllegalStateException e) {
			model.addAttribute("result",
				new CancelResponseDTO(false, orderNumber, null, 0, null, e.getMessage()));
		} catch (Exception e) {
			model.addAttribute("result",
				new CancelResponseDTO(false, orderNumber, null, 0, null,
					"주문 취소 중 오류가 발생했습니다. 잠시 후 다시 시도해 주세요."));
		}

		return "order-cancel";
	}

	private void repopulateOrderView(OrderCreateRequestDTO dto, Model model) {
		// cart 주문인 경우
		if (dto.getOrderType() == OrderType.CART) {
			List<OrderItemDTO> items = cartService.getOrderItemDTOS(dto.getCartProductIds());
			List<CartProduct> cartProducts = cartService.findAllByIdWithProduct(dto.getCartProductIds());
			model.addAttribute("orderItems", items);

			int totalPrice = cartService.getTotalPrice(cartProducts);
			int deliveryFee = DeliveryPolicy.DELIVERY_FEE;
			model.addAttribute("orderPrice",
				new OrderPriceDTO(totalPrice, deliveryFee, totalPrice + deliveryFee));

			// 즉시 주문인 경우
		} else if (dto.getOrderType() == OrderType.BUY_NOW) {
			Product product = productService.findById(dto.getProductId());
			OrderItemDTO item = orderMapper.toOrderItemDTOFromCart(product, dto.getQuantity());
			model.addAttribute("orderItems", List.of(item));

			int totalPrice = product.getPrice() * dto.getQuantity();
			int deliveryFee = DeliveryPolicy.DELIVERY_FEE;
			model.addAttribute("orderPrice",
				new OrderPriceDTO(totalPrice, deliveryFee, totalPrice + deliveryFee));
		}
	}
}
