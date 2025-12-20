package com.commerce.service;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.commerce.domain.Cart;
import com.commerce.domain.CartProduct;
import com.commerce.domain.DeliveryPolicy;
import com.commerce.domain.OrderProduct;
import com.commerce.domain.Orders;
import com.commerce.domain.Product;
import com.commerce.domain.enums.OrderStatus;
import com.commerce.domain.enums.OrderType;
import com.commerce.dto.AdminOrderSearchCond;
import com.commerce.dto.OrderCreateRequestDTO;
import com.commerce.repository.CartProductRepository;
import com.commerce.repository.OrderRepository;
import com.commerce.repository.ProductRepository;
import com.commerce.util.SecurityUtil;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderService {

	private final OrderRepository orderRepository;
	private final CartProductRepository cartProductRepository;
	private final SecurityUtil securityUtil;
	private final ProductRepository productRepository;

	// 주문 삭제
	public void deleteOrderByOrderNumber(String orderNumber) {
		orderRepository.deleteByOrderNumber(orderNumber);
	}

	// 주문 상태 변경
	@Transactional
	public void changeStatus(List<Long> orderIds,OrderStatus status) {
		List<Orders> orders = orderRepository.findAllById(orderIds);
		for (Orders order : orders) {
			order.setOrderStatus(status);
		}
	}

	public List<Orders> getOrderList(AdminOrderSearchCond cond) {
		LocalDateTime start = null;
		LocalDateTime end = null;
		if (cond.getStartDate() != null) {
			start = cond.getStartDate().atTime(LocalTime.MIN);
		}
		if (cond.getEndDate() != null) {
			end = cond.getEndDate().atTime(LocalTime.MAX);
		}
		return orderRepository.searchAdminOrders(cond.getKeyword(), start, end,
			cond.getOrderStatus(), cond.getPaymentType());
	}

	public Orders findByOrderNumber(String orderNumber) {
		return orderRepository.findByOrderNumber(orderNumber)
			.orElseThrow(() -> new NoSuchElementException("해당 주문이 존재하지 않습니다."));
	}

	@Transactional
	public Orders prepareOrderFromBuyNow(OrderCreateRequestDTO dto) {
		Product product = productRepository.findById(dto.getProductId())
			.orElseThrow();

		// 1. 상품 재고 확인
		validateStock(product, dto.getQuantity());

		// 2. 가격 계산
		int totalPrice = product.getPrice() * dto.getQuantity();

		// 3. 주문 이름 생성
		String orderName = product.getName();

		// 3. order 객체 생성
		Orders orders = createOrderEntity(dto, OrderStatus.READY, OrderType.BUY_NOW, orderName, totalPrice);

		// 4. orderProduct 생성
		OrderProduct orderProduct = OrderProduct.builder()
			.product(product)
			.order(orders)
			.price(product.getPrice())
			.quantity(dto.getQuantity())
			.build();

		orders.getOrderProducts().add(orderProduct);

		return orderRepository.save(orders);
	}

	@Transactional
	public Orders prepareOrderFromCart(OrderCreateRequestDTO dto) {
		List<Long> cartProductIds = dto.getCartProductIds();
		List<CartProduct> cartProducts = cartProductRepository.findAllById(cartProductIds);

		// 1. 상품 재고 확인
		validateStock(cartProducts);

		// 2. 가격 계산
		int totalPrice = getTotalPrice(cartProducts);

		// 주문 이름 생성
		String orderName = buildOrderName(cartProducts);

		// 3. order 객체 생성
		Orders orders = createOrderEntity(dto, OrderStatus.READY, OrderType.CART, orderName, totalPrice);

		// 4. orderProduct 생성
		for (CartProduct cartProduct : cartProducts) {
			Product product = cartProduct.getProduct();
			OrderProduct orderProduct = OrderProduct.builder()
				.product(product)
				.order(orders)
				.price(product.getPrice())
				.quantity(cartProduct.getQuantity())
				.build();

			orders.getCartProductIds().add(cartProduct.getId());
			orders.getOrderProducts().add(orderProduct);
		}


		return orderRepository.save(orders);

	}

	@Transactional
	public Orders createOrderFromBuyNow(OrderCreateRequestDTO dto) {
		Product product = productRepository.findById(dto.getProductId())
			.orElseThrow();

		// 1. 상품 재고 확인
		validateStock(product, dto.getQuantity());

		// 2. 가격 계산
		int totalPrice = product.getPrice() * dto.getQuantity();

		// 3. 주문 이름 생성
		String orderName = product.getName();

		// 3. order 객체 생성
		Orders orders = createOrderEntity(dto, OrderStatus.READY, dto.getOrderType(), orderName, totalPrice);

		// 4. 재고 차감, orderProduct 생성
		OrderProduct orderProduct = OrderProduct.builder()
			.product(product)
			.order(orders)
			.price(product.getPrice())
			.quantity(dto.getQuantity())
			.build();

		orders.getOrderProducts().add(orderProduct);
		product.decreaseStock(dto.getQuantity());

		return orderRepository.save(orders);
	}

	@Transactional
	public Orders createOrderFromCart(OrderCreateRequestDTO dto) {
		List<Long> cartProductIds = dto.getCartProductIds();
		List<CartProduct> cartProducts = cartProductRepository.findAllById(cartProductIds);
		
		// 1. 상품 재고 확인
		validateStock(cartProducts);

		// 2. 가격 계산
		int totalPrice = getTotalPrice(cartProducts);

		// 주문 이름 생성
		String orderName = buildOrderName(cartProducts);

		// 3. order 객체 생성
		Orders orders = createOrderEntity(dto, OrderStatus.READY, dto.getOrderType(), orderName, totalPrice);

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

	// 주문 이름 생성
	private String buildOrderName(List<CartProduct> cartProducts) {
		if (cartProducts.isEmpty()) {
			throw new IllegalArgumentException("주문 상품이 없습니다.");
		}

		CartProduct cartProduct = cartProducts.get(0);
		String prefix = cartProduct.getProduct().getName();

		int size = cartProducts.size() - 1;
		String suffix = (size > 0) ? " 외 " + size + "건" : "";

		return prefix + suffix;
	}

	private Orders createOrderEntity(OrderCreateRequestDTO dto, OrderStatus orderStatus, OrderType orderType, String orderName, int totalPrice) {

		Orders orders = Orders
			.builder()
			.receiverAddress(dto.getAddress())
			.orderNumber(UUID.randomUUID().toString().replace("-", ""))
			.orderAddressDetail(dto.getAddressDetail())
			.orderName(orderName)
			.receiverName(dto.getName())
			.receiverPhone(dto.getPhone())
			.requestNote(dto.getRequestNote())
			.paymentType(null)
			.orderType(orderType)
			.orderStatus(orderStatus)
			.user(securityUtil.getCurrentUser())
			.finalPrice(totalPrice + DeliveryPolicy.DELIVERY_FEE)
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

	private static void validateStock(Product product, int quantity) {
		int stock = product.getStock();
		if (stock - quantity < 0) {
			throw new RuntimeException("재고가 부족합니다.");
		}
	}

}
