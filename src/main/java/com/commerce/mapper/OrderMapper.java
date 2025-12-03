package com.commerce.mapper;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.commerce.domain.CartProduct;
import com.commerce.domain.DeliveryPolicy;
import com.commerce.domain.OrderProduct;
import com.commerce.domain.Orders;
import com.commerce.domain.Product;
import com.commerce.domain.User;
import com.commerce.dto.OrderDetailResponseDTO;
import com.commerce.dto.OrderItemDTO;
import com.commerce.dto.OrderPriceDTO;
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

		return new OrderProductResponseDTO(product.getId(), product.getName(), orderProduct.getQuantity(),
			orderProduct.getPrice(), mainImageUrl);
	}

	public OrderItemDTO toOrderItemDTOFromCart(Product product, int quantity) {

		return OrderItemDTO.builder()
			.id(product.getId())
			.quantity(quantity)
			.unitPrice(product.getPrice())
			.totalPrice(quantity * product.getPrice())
			.mainImageUrl(productImageUtil.getMainImageUrl(product))
			.name(product.getName())
			.build();
	}

	public List<OrderItemDTO> toOrderItemDTOFromCart(List<CartProduct> cartProducts) {

		List<OrderItemDTO> list = new ArrayList<>();
		for (CartProduct cartProduct : cartProducts) {
			list.add(toOrderItemDTOFromCart(cartProduct));
		}
		return list;
	}

	public OrderItemDTO toOrderItemDTOFromCart(CartProduct cartProduct) {
		Product product = cartProduct.getProduct();
		String mainImageUrl = productImageUtil.getMainImageUrl(product);


		return OrderItemDTO.builder()
			.id(cartProduct.getId())
			.quantity(cartProduct.getQuantity())
			.unitPrice(product.getPrice())
			.totalPrice(product.getPrice() * cartProduct.getQuantity())
			.mainImageUrl(mainImageUrl)
			.name(product.getName())
			.build();
	}

	public OrderItemDTO toOrderItemDTOFromOrder(OrderProduct orderProduct) {
		Product product = orderProduct.getProduct();
		String mainImageUrl = productImageUtil.getMainImageUrl(product);


		return OrderItemDTO.builder()
			.id(product.getId())
			.quantity(orderProduct.getQuantity())
			.unitPrice(product.getPrice())
			.totalPrice(product.getPrice() * orderProduct.getQuantity())
			.mainImageUrl(mainImageUrl)
			.name(product.getName())
			.build();
	}

	public List<OrderItemDTO> toOrderItemDTOFromOrder(List<OrderProduct> orderProducts) {

		List<OrderItemDTO> list = new ArrayList<>();
		for (OrderProduct orderProduct : orderProducts) {
			list.add(toOrderItemDTOFromOrder(orderProduct));
		}

		return list;
	}

	public OrderDetailResponseDTO toOrderDetailResponseDTO(Orders order, User user) {
		return OrderDetailResponseDTO.builder()
			.orderNumber(order.getOrderNumber())
			.orderDate(order.getCreatedAt())
			.ordererInfo(OrderDetailResponseDTO.OrdererInfo.builder()
				.name(user.getName())
				.email(user.getEmail())
				.phone(user.getPhone())
				.build()
			)
			.shippingInfo(OrderDetailResponseDTO.ShippingInfo.builder()
				.receiverName(order.getOrderName())
				.receiverPhone(order.getOrderPhone())
				.address(order.getOrderAddress())
				.addressDetail(order.getOrderAddressDetail())
				.requestMessage(order.getRequestNote())
				.build()
			)
			.orderItems(toOrderItemDTOFromOrder(order.getOrderProducts()))
			.orderPrice(new OrderPriceDTO(order.getTotalPrice(), DeliveryPolicy.DELIVERY_FEE, order.getTotalPrice() + DeliveryPolicy.DELIVERY_FEE))
			.paymentInfo(OrderDetailResponseDTO.PaymentInfo.builder()
				.paymentMethod(order.getPaymentMethod().getText())
				.orderStatus(order.getOrderStatus().getText())
				.build())
			.build();
	}
}
