package com.commerce.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import com.commerce.domain.CartProduct;
import com.commerce.domain.DeliveryPolicy;
import com.commerce.domain.Orders;
import com.commerce.domain.Product;
import com.commerce.domain.User;
import com.commerce.domain.enums.OrderType;
import com.commerce.dto.OrderCreateRequestDTO;
import com.commerce.dto.OrderDetailResponseDTO;
import com.commerce.dto.OrderItemDTO;
import com.commerce.dto.OrderPriceDTO;
import com.commerce.dto.OrderResponseDTO;
import com.commerce.mapper.OrderMapper;
import com.commerce.service.CartService;
import com.commerce.service.OrderService;
import com.commerce.service.ProductService;
import com.commerce.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Controller
@RequestMapping("/orders")
@RequiredArgsConstructor
@Slf4j
public class OrderController {

    private final CartService cartService;
    private final SecurityUtil securityUtil;
    private final OrderService orderService;
    private final OrderMapper orderMapper;
    private final ProductService productService;

    @Value("${toss.payments.client-key}")
    private String tossClientKey;

    @GetMapping("/cart")
    public String cartOrder(Model model) {
        // 1. cartProduct에서 선택한 상품 찾고 모델에 담기
        List<CartProduct> cartProducts = cartService.getSelectedProduct();
        List<OrderItemDTO> dto = orderMapper.toOrderItemDTOFromCart(cartProducts);
        model.addAttribute("orderItems", dto);

        // 2. 사용자 기본 정보 모델에 담기
        User user = securityUtil.getCurrentUser();
        OrderCreateRequestDTO orderDTO = setBasicUserInfo(user);
        orderDTO.setOrderType(OrderType.CART);
        model.addAttribute("orderForm", orderDTO);

        // 3. 주문 가격
        int totalPrice = cartService.getTotalPrice(cartProducts);
        int deliveryFee = DeliveryPolicy.DELIVERY_FEE;
        OrderPriceDTO orderPriceDTO = new OrderPriceDTO(totalPrice, deliveryFee, totalPrice + deliveryFee);
        model.addAttribute("orderPrice", orderPriceDTO);

        // 토스 클라이언트 키
        model.addAttribute("tossClientKey", tossClientKey);

        return "order";
    }


    @GetMapping("/buy-now")
    public String buyNow(Long productId, int quantity, Model model) {
        // 1. 상품 정보 담기
        Product product = productService.findById(productId);
        OrderItemDTO dto = orderMapper.toOrderItemDTOFromCart(product,quantity);
        model.addAttribute("orderItems", List.of(dto));

        // 2. 사용자 기본 정보 모델에 담기
        User user = securityUtil.getCurrentUser();
        OrderCreateRequestDTO orderDTO = setBasicUserInfo(user);
        orderDTO.setOrderType(OrderType.BUY_NOW);
        orderDTO.setProductId(productId);
        orderDTO.setQuantity(quantity);
        model.addAttribute("orderForm", orderDTO);

        // 3. 주문 가격
        int totalPrice = product.getPrice() * quantity;
        int deliveryFee = DeliveryPolicy.DELIVERY_FEE;
        OrderPriceDTO orderPriceDTO = new OrderPriceDTO(totalPrice, deliveryFee, totalPrice + deliveryFee);
        model.addAttribute("orderPrice", orderPriceDTO);

        // 토스 클라이언트 키
        model.addAttribute("tossClientKey", tossClientKey);

        return "order";
    }


    @GetMapping("/list")
    public String viewOrderList(Model model) {
        User user = securityUtil.getCurrentUser();
        List<OrderResponseDTO> dtos = orderService.findOrderList(user);
        model.addAttribute("orders", dtos);

        return "order-list";
    }

    @GetMapping("/detail/{orderNumber}")
    public String orderDetail(@PathVariable String orderNumber, Model model) {
        Orders order = orderService.findByOrderNumber(orderNumber);
        User user = order.getUser();

        OrderDetailResponseDTO dto = orderMapper.toOrderDetailResponseDTO(order, user);
        model.addAttribute("order", dto);
        return "order-list-detail";
    }


    private OrderCreateRequestDTO setBasicUserInfo(User user) {
        OrderCreateRequestDTO orderDTO = new OrderCreateRequestDTO();
        orderDTO.setAddress(user.getAddress());
        orderDTO.setPhone(user.getPhone());
        orderDTO.setName(user.getName());
        orderDTO.setCustomerKey(user.getCustomerPaymentKey());
        return orderDTO;
    }

}
