package com.commerce.controller;

import com.commerce.domain.CartProduct;
import com.commerce.domain.DeliveryPolicy;
import com.commerce.domain.User;
import com.commerce.dto.CartProductDTO;
import com.commerce.dto.OrderDTO;
import com.commerce.mapper.CartProductMapper;
import com.commerce.service.CartService;
import com.commerce.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import javax.swing.*;
import java.util.List;

@Controller
@RequestMapping("/order")
@RequiredArgsConstructor
public class OrderController {

    private final CartService cartService;
    private final CartProductMapper cartProductMapper;
    private final SecurityUtil securityUtil;

    @GetMapping
    public String viewOrder(Model model) {
        // 1. cartProduct에서 선택한 상품 찾고 모델에 담기
        List<CartProduct> products = cartService.getSelectedProduct();
        List<CartProductDTO> productDTOs = cartProductMapper.toCartProductDTOs(products);
        model.addAttribute("products", productDTOs);

        // 2. 사용자 기본 정보 모델에 담기
        User user = securityUtil.getCurrentUser();
        OrderDTO orderDTO = setBasicUserInfo(user);
        model.addAttribute("orderForm", orderDTO);

        // 3. 전체 상품 가격
        int totalPrice = cartService.getTotalPrice(products);
        model.addAttribute("totalPrice", totalPrice);

        //4. 배송비
        model.addAttribute("deliveryFee", DeliveryPolicy.DELIVERY_FEE);

        //5. 최종 가격
        model.addAttribute("finalPrice", DeliveryPolicy.DELIVERY_FEE + totalPrice);


        return "order";
    }

    private OrderDTO setBasicUserInfo(User user) {
        OrderDTO orderDTO = new OrderDTO();
        orderDTO.setAddress(user.getAddress());
        orderDTO.setPhone(user.getPhone());
        orderDTO.setName(user.getName());
        return orderDTO;
    }

}
