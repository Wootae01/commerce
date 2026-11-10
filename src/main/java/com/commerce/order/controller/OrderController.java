package com.commerce.order.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.commerce.user.domain.User;
import com.commerce.order.dto.OrderCheckoutResponse;
import com.commerce.order.dto.OrderCreateRequestDTO;
import com.commerce.order.dto.OrderDetailResponseDTO;
import com.commerce.order.dto.OrderResponseDTO;
import com.commerce.order.service.OrderCheckoutService;
import com.commerce.order.service.OrderService;
import com.commerce.common.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Controller
@RequestMapping("/orders")
@RequiredArgsConstructor
@Slf4j
public class OrderController {

    private final SecurityUtil securityUtil;
    private final OrderService orderService;
    private final OrderCheckoutService orderCheckoutService;

    @GetMapping("/cart")
    public String cartOrder(Model model) {
        User user = securityUtil.getCurrentUser();
        OrderCheckoutResponse checkout = orderCheckoutService.getCartCheckout(user);

        addCheckout(model, checkout, orderForm(checkout));
        return "order";
    }


    @GetMapping("/buy-now")
    public String buyNow(Long productId, @RequestParam(required = false) Long optionId, int quantity, Model model) {
        User user = securityUtil.getCurrentUser();
        OrderCheckoutResponse checkout = orderCheckoutService.getBuyNowCheckout(user, productId, optionId, quantity);

        // 바로구매는 무엇을 살지가 요청 파라미터에 있어서 결제하기 폼에 다시 실어 보낸다
        OrderCreateRequestDTO orderForm = orderForm(checkout);
        orderForm.setProductId(productId);
        orderForm.setQuantity(quantity);
        orderForm.setOptionId(optionId);

        addCheckout(model, checkout, orderForm);
        return "order";
    }


    @GetMapping("/list")
    public String viewOrderList(@RequestParam(defaultValue = "0") int page, Model model) {
        User user = securityUtil.getCurrentUser();
        int size = 10; // page 사이즈
        Page<OrderResponseDTO> dtoPage = orderService.findOrderList(user, PageRequest.of(page, size));
        model.addAttribute("page", dtoPage);

        return "order-list";
    }

    @GetMapping("/detail/{orderNumber}")
    public String orderDetail(@PathVariable String orderNumber, Model model) {
        User currentUser = securityUtil.getCurrentUser();
        OrderDetailResponseDTO dto = orderService.getMyOrderDetail(orderNumber, currentUser);
        model.addAttribute("order", dto);
        return "order-list-detail";
    }


    private void addCheckout(Model model, OrderCheckoutResponse checkout, OrderCreateRequestDTO orderForm) {
        model.addAttribute("orderItems", checkout.items());
        model.addAttribute("orderForm", orderForm);
        model.addAttribute("orderPrice", checkout.orderPrice());
        model.addAttribute("tossClientKey", checkout.tossClientKey());
    }

    // 결제하기 폼의 초기값. 배송지 입력란은 주문자 기본 정보로 채운다
    private OrderCreateRequestDTO orderForm(OrderCheckoutResponse checkout) {
        OrderCheckoutResponse.Orderer orderer = checkout.orderer();
        OrderCreateRequestDTO orderForm = new OrderCreateRequestDTO();
        orderForm.setOrderType(checkout.orderType());
        orderForm.setAddress(orderer.address());
        orderForm.setPhone(orderer.phone());
        orderForm.setName(orderer.name());
        orderForm.setCustomerKey(orderer.customerKey());
        return orderForm;
    }

}
