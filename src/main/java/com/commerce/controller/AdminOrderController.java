package com.commerce.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;


import lombok.RequiredArgsConstructor;

@Controller("/admin/orders")
@RequiredArgsConstructor
public class AdminOrderController {

	@GetMapping
	public String orderList(Model model) {
		return "admin/order-list";
	}

}
