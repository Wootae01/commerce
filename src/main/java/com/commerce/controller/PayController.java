package com.commerce.controller;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.NoSuchElementException;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.stereotype.Service;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import net.minidev.json.JSONObject;

import com.commerce.domain.Orders;
import com.commerce.domain.enums.OrderType;
import com.commerce.dto.OrderCreateRequestDTO;
import com.commerce.dto.OrderPrepareResponseDTO;
import com.commerce.dto.PayConfirmDTO;
import com.commerce.dto.PaySuccessDTO;
import com.commerce.mapper.OrderMapper;
import com.commerce.service.OrderService;
import com.commerce.service.PayService;
import com.commerce.util.SecurityUtil;
import com.fasterxml.jackson.databind.ObjectMapper;

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
		BindingResult bindingResult) {

		if (bindingResult.hasErrors()) {
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
}
