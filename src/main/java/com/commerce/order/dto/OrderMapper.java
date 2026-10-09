package com.commerce.order.dto;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.commerce.cart.domain.CartProduct;
import com.commerce.product.domain.DeliveryPolicy;
import com.commerce.product.domain.ProductOption;
import com.commerce.order.domain.OrderProduct;
import com.commerce.order.domain.Orders;
import com.commerce.product.domain.Product;
import com.commerce.user.domain.User;
import com.commerce.common.enums.PaymentType;
import com.commerce.common.util.ProductImageUtil;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class OrderMapper {

	private final ProductImageUtil productImageUtil;

	@Value("${app.base-url}")
	private String baseUrl;

	public OrderPrepareResponseDTO toOrderPrepareResponseDTO(Orders order) {
		return OrderPrepareResponseDTO.builder()
				.orderId(order.getOrderNumber())
				.amount(order.getFinalPrice())
				.customerName(order.getReceiverName())
				.customerMobilePhone(order.getReceiverPhone())
				.orderName(order.getOrderName())
				.successUrl(baseUrl + "/pay/loading")
				.failUrl(baseUrl + "/pay/fail")
				.build();
	}

	public OrderItemDTO toOrderItemDTOFromCart(Product product, int quantity, ProductOption option) {
		int additionalPrice = option != null ? option.getAdditionalPrice() : 0;
		int unitPrice = product.getPrice() + additionalPrice;

		return OrderItemDTO.builder()
			.id(product.getId())
			.quantity(quantity)
			.unitPrice(unitPrice)
			.totalPrice(quantity * unitPrice)
			.mainImageUrl(productImageUtil.getMainImageUrl(product))
			.name(product.getName())
			.optionName(option != null ? option.getName() : null)
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
		ProductOption option = cartProduct.getProductOption();
		int additionalPrice = option != null ? option.getAdditionalPrice() : 0;
		int unitPrice = product.getPrice() + additionalPrice;
		String mainImageUrl = productImageUtil.getMainImageUrl(product);

		return OrderItemDTO.builder()
			.id(cartProduct.getId())
			.quantity(cartProduct.getQuantity())
			.unitPrice(unitPrice)
			.totalPrice(unitPrice * cartProduct.getQuantity())
			.mainImageUrl(mainImageUrl)
			.name(product.getName())
			.optionName(option != null ? option.getName() : null)
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
				.receiverName(order.getReceiverName())
				.receiverPhone(order.getReceiverPhone())
				.address(order.getReceiverAddress())
				.addressDetail(order.getOrderAddressDetail())
				.requestMessage(order.getRequestNote())
				.build()
			)
			.orderItems(toOrderItemDTOFromOrder(order.getOrderProducts()))
			.orderPrice(new OrderPriceDTO(order.getFinalPrice(), DeliveryPolicy.DELIVERY_FEE, order.getFinalPrice() + DeliveryPolicy.DELIVERY_FEE))
			.paymentInfo(OrderDetailResponseDTO.PaymentInfo.builder()
				.paymentMethod(order.getPaymentType() == null
					? PaymentType.UNKNOWN.getText() : order.getPaymentType().getText())
				.orderStatus(order.getOrderStatus().getText())
				.build())
			.build();
	}
}
