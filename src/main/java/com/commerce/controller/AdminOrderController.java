package com.commerce.controller;

import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

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
	public String orderList(AdminOrderSearchCond cond, Model model) {
		log.info("search cond => keyword={}, status={}, paymentType={}, start={}, end={}",
			cond.getKeyword(), cond.getOrderStatus(), cond.getPaymentType(),
			cond.getStartDate(), cond.getEndDate());

		// 1. 모든 주문 찾아서 dto로 변환
		List<Orders> orders = orderService.getOrderList(cond);
		List<AdminOrderListResponseDTO> dtos = orderMapper.toAdminOrderListResponseDTOS(orders);
		model.addAttribute("orders", dtos);

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

}
