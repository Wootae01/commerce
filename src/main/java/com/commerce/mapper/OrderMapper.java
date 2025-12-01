package com.commerce.mapper;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.commerce.domain.OrderProduct;
import com.commerce.domain.Orders;
import com.commerce.domain.Product;
import com.commerce.dto.OrderProductResponseDTO;
import com.commerce.dto.OrderResponseDTO;
import com.commerce.util.ProductImageUtil;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class OrderMapper {

	private final ProductImageUtil productImageUtil;

	public List<OrderResponseDTO> toOrderResponseDTO(List<Orders> orders) {
		List<OrderResponseDTO> list = new ArrayList<>();
		for (Orders order : orders) {
			list.add(toOrderResponseDTO(order));
		}

		return list;
	}

	public OrderResponseDTO toOrderResponseDTO(Orders order) {

		List<OrderProduct> orderProducts = order.getOrderProducts();
		List<OrderProductResponseDTO> productResponseDTOList = toOrderProductResponseDTOS(orderProducts);

		OrderResponseDTO orderResponseDTO = new OrderResponseDTO(
			order.getOrderNumber(), order.getCreatedAt(), order.getOrderStatus().getText(),productResponseDTOList, order.getTotalPrice()
		);

		return orderResponseDTO;
	}

	public List<OrderProductResponseDTO> toOrderProductResponseDTOS(List<OrderProduct> orderProducts) {
		List<OrderProductResponseDTO> list = new ArrayList<>();

		for (OrderProduct orderProduct : orderProducts) {
			list.add(toOrderProductResponseDTO(orderProduct));
		}

		return list;
	}

	public OrderProductResponseDTO toOrderProductResponseDTO(OrderProduct orderProduct) {
		Product product = orderProduct.getProduct();
		String mainImageUrl = productImageUtil.getMainImageUrl(product);

		return new OrderProductResponseDTO(orderProduct.getProduct().getName(), orderProduct.getQuantity(),
			orderProduct.getPrice(), mainImageUrl);
	}
}
