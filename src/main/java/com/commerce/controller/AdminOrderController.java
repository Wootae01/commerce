package com.commerce.controller;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.apache.tomcat.util.buf.UriUtil;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.util.UriUtils;

import com.commerce.domain.Orders;
import com.commerce.domain.enums.OrderStatus;
import com.commerce.dto.AdminOrderListResponseDTO;
import com.commerce.dto.AdminOrderSearchCond;
import com.commerce.mapper.OrderMapper;
import com.commerce.service.OrderService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Controller
@RequiredArgsConstructor
@RequestMapping("/admin/orders")
@Slf4j
public class AdminOrderController {

	private final OrderService orderService;
	private final OrderMapper orderMapper;

	@GetMapping
	public String orderList(AdminOrderSearchCond cond, @RequestParam(defaultValue = "0") int page, Model model) {
		page = Math.max(page, 0);
		log.info("search cond => keyword={}, status={}, paymentType={}, start={}, end={}",
			cond.getKeyword(), cond.getOrderStatus(), cond.getPaymentType(),
			cond.getStartDate(), cond.getEndDate());

		// 1. 모든 주문 찾아서 dto로 변환
		int size = 20; // page size
		Page<Orders> pageOrder = orderService.getOrderList(cond, PageRequest.of(page, size));
		List<AdminOrderListResponseDTO> dtos = orderMapper.toAdminOrderListResponseDTOS(pageOrder.getContent());
		model.addAttribute("orders", dtos);
		model.addAttribute("page", pageOrder);

		String qs = buildQs(cond);
		model.addAttribute("qs", qs);

		return "admin/order-list";
	}

	@PostMapping("/status")
	public String changeStatus(@RequestParam(value = "orderIds", required = false) List<Long> orderIds,
							   @RequestParam(value = "status", required = false) OrderStatus status) {
		if (orderIds == null || orderIds.isEmpty() || status == null) {
			return "redirect:/admin/orders";
		}
		orderService.changeStatus(orderIds, status);

		return "redirect:/admin/orders";
	}

	private static String buildQs(AdminOrderSearchCond cond) {
		List<String> parts = new ArrayList<>();
		if (cond.getKeyword() != null && !cond.getKeyword().isBlank()) {
			parts.add("keyword=" + UriUtils.encodeQueryParam(cond.getKeyword(), StandardCharsets.UTF_8));
		}
		if (cond.getStartDate() != null) {
			parts.add("startDate=" + cond.getStartDate()); // yyyy-MM-dd면 그대로 OK
		}
		if (cond.getEndDate() != null) {
			parts.add("endDate=" + cond.getEndDate());
		}
		if (cond.getOrderStatus() != null) {
			parts.add("orderStatus=" + cond.getOrderStatus().name());
		}
		if (cond.getPaymentType() != null) {
			parts.add("paymentType=" + cond.getPaymentType().name());
		}

		return String.join("&", parts);
	}

}
