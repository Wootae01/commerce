package com.commerce.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.commerce.domain.Cart;
import com.commerce.domain.CartProduct;
import com.commerce.domain.DeliveryPolicy;
import com.commerce.domain.OrderProduct;
import com.commerce.domain.Orders;
import com.commerce.domain.Product;
import com.commerce.domain.enums.OrderStatus;
import com.commerce.domain.enums.PaymentType;
import com.commerce.dto.OrderCreateRequestDTO;
import com.commerce.repository.CartProductRepository;
import com.commerce.repository.OrderRepository;
import com.commerce.util.SecurityUtil;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class OrderService {

	private final OrderRepository orderRepository;
	private final CartProductRepository cartProductRepository;
	private final SecurityUtil securityUtil;

	public Orders createOrder(OrderCreateRequestDTO dto, List<Long> cartProductIds, String payment) {
		
		List<CartProduct> cartProducts = cartProductRepository.findAllById(cartProductIds);
		
		// 1. 상품 재고 확인
		validateStock(cartProducts);
		// 2. 가격 계산
		int totalPrice = getTotalPrice(cartProducts);

		// 3. order 객체 생성
		Orders orders = createOrderEntity(dto, payment, totalPrice);

		// 4. 재고 차감, orderProduct 생성
		for (CartProduct cartProduct : cartProducts) {
			Product product = cartProduct.getProduct();
			OrderProduct orderProduct = OrderProduct.builder()
				.product(product)
				.order(orders)
				.price(product.getPrice())
				.quantity(cartProduct.getQuantity())
				.build();

			orders.getOrderProducts().add(orderProduct);
			product.decreaseStock(cartProduct.getQuantity());
		}

		// 5. 장바구니 삭제
		for (CartProduct cartProduct : cartProducts) {
			Cart cart = cartProduct.getCart();
			cart.deleteProduct(cartProduct);
		}

		return orderRepository.save(orders);


	}

	private Orders createOrderEntity(OrderCreateRequestDTO dto, String payment, int totalPrice) {

		OrderStatus orderStatus = null;
		PaymentType paymentType = PaymentType.valueOf(payment);
		if (paymentType.equals(PaymentType.CASH)) {
			orderStatus = OrderStatus.WAITING_FOR_DEPOSIT;
		} else {
			orderStatus = OrderStatus.WAITING_FOR_PAYMENT;
		}

		Orders orders = Orders
			.builder()
			.orderAddress(dto.getAddress())
			.orderNumber(UUID.randomUUID().toString().replace("-", "").substring(0, 12))
			.orderAddressDetail(dto.getAddressDetail())
			.orderName(dto.getName())
			.orderPhone(dto.getPhone())
			.requestNote(dto.getRequestNote())
			.paymentMethod(paymentType)
			.orderStatus(orderStatus)
			.user(securityUtil.getCurrentUser())
			.totalPrice(totalPrice + DeliveryPolicy.DELIVERY_FEE)
			.build();

		return orders;
	}

	private static int getTotalPrice(List<CartProduct> cartProducts) {
		int sum = 0;
		for (CartProduct cartProduct : cartProducts) {
			sum += cartProduct.getProduct().getPrice() * cartProduct.getQuantity();
		}
		return sum;
	}

	private static void validateStock(List<CartProduct> cartProducts) {
		for (CartProduct cartProduct : cartProducts) {
			Product product = cartProduct.getProduct();
			// 재고 부족한 경우
			if (product.getStock() - cartProduct.getQuantity() < 0) {
				throw new RuntimeException("재고가 부족합니다.");
			}
		}
	}

}
