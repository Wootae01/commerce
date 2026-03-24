package com.commerce.order.service;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.commerce.common.exception.BusinessException;
import com.commerce.common.exception.EntityNotFoundException;
import org.springframework.http.HttpStatus;
import java.util.UUID;
import java.util.stream.Collectors;

import com.commerce.common.util.ProductImageUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.commerce.cart.domain.CartProduct;
import com.commerce.product.domain.DeliveryPolicy;
import com.commerce.order.domain.OrderProduct;
import com.commerce.order.domain.Orders;
import com.commerce.product.domain.Product;
import com.commerce.user.domain.User;
import com.commerce.common.enums.OrderStatus;
import com.commerce.common.enums.OrderType;
import com.commerce.admin.dto.AdminOrderSearchCond;
import com.commerce.order.dto.OrderCartProductRow;
import com.commerce.order.dto.OrderCreateRequestDTO;
import com.commerce.order.dto.OrderHeaderRow;
import com.commerce.order.dto.OrderItemRow;
import com.commerce.order.dto.OrderProductResponseDTO;
import com.commerce.order.dto.OrderProductRow;
import com.commerce.order.dto.OrderResponseDTO;
import com.commerce.product.dto.ProductMainImageRow;
import com.commerce.cart.repository.CartProductRepository;
import com.commerce.order.repository.OrderCartProductJdbcRepository;
import com.commerce.order.repository.OrderProductJdbcRepository;
import com.commerce.order.repository.OrderProductRepository;
import com.commerce.order.repository.OrderRepository;
import com.commerce.product.repository.ProductRepository;
import com.commerce.common.util.SecurityUtil;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderService {

	private final OrderRepository orderRepository;
	private final CartProductRepository cartProductRepository;
	private final SecurityUtil securityUtil;
	private final ProductRepository productRepository;
	private final OrderProductRepository orderProductRepository;
	private final OrderProductJdbcRepository orderProductJdbcRepository;
	private final OrderCartProductJdbcRepository orderCartProductJdbcRepository;
	private final ProductImageUtil productImageUtil;


	@Value("${app.image.default-path}")
	private String imageDefaultPath;

	// 주문 리스트 반환
	public Page<OrderResponseDTO> findOrderList(User user, Pageable pageable) {

		// 주문 단위 페이징
		Page<OrderHeaderRow> orderHeaders = orderRepository.findOrderHeaders(user, pageable);
		List<OrderHeaderRow> headers = orderHeaders.getContent();
		if (headers.isEmpty()) {
			return new PageImpl<>(List.of(), pageable, orderHeaders.getTotalElements());
		}

		// 주문 아이템 조회
		List<Long> orderIds = headers.stream()
			.map(OrderHeaderRow::orderId)
			.toList();

		List<OrderItemRow> orderItemRows = orderProductRepository.findOrderItemsByOrderIds(orderIds);


		// 이미지 조회
		List<Long> productIds = orderItemRows.stream()
			.map(OrderItemRow::productId)
			.distinct()
			.toList();

		List<ProductMainImageRow> mainImages = productRepository.findMainImages(productIds);

		// productId -> mainImageUrl
		Map<Long, String> mainUrlByProductId = mainImages.stream()
				.collect(Collectors.toMap(
						ProductMainImageRow::productId,
						r -> productImageUtil.getImageUrl(r.storeFileName()),
						(a, b) -> a // 혹시 중복 키 나오면 첫 값 유지
				));

		//  orderId -> OrderResponseDTO. 헤더 기준으로 먼저 만들어 순서 보장
		Map<Long, OrderResponseDTO> byOrderId = new LinkedHashMap<>();
		for (OrderHeaderRow h : orderHeaders) {
			byOrderId.put(
				h.orderId(),
				new OrderResponseDTO(
					h.orderNumber(),
					h.orderDate(),
					h.orderStatus(),
					new ArrayList<>(),
					h.finalPrice()
				)
			);
		}

		// 아이템 붙이기
		for (OrderItemRow r : orderItemRows) {
			OrderResponseDTO orderDto = byOrderId.get(r.orderId());
			if (orderDto == null) continue;

			String imageUrl = mainUrlByProductId.getOrDefault(r.productId(), imageDefaultPath);

			orderDto.getProductDTOS().add(
				new OrderProductResponseDTO(
					r.productId(),
					r.productName(),
					r.quantity(),
					r.price(),
					imageUrl
				)
			);
		}
		List<OrderResponseDTO> result = new ArrayList<>(byOrderId.values());

		return new PageImpl<>(result, pageable, orderHeaders.getTotalElements());
	}

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

	public Page<Orders> getOrderList(AdminOrderSearchCond cond, Pageable pageable) {
		LocalDateTime start = null;
		LocalDateTime end = null;
		if (cond.getStartDate() != null) {
			start = cond.getStartDate().atTime(LocalTime.MIN);
		}
		if (cond.getEndDate() != null) {
			end = cond.getEndDate().atTime(LocalTime.MAX);
		}
		return orderRepository.searchAdminOrders(cond.getKeyword(), start, end,
			cond.getOrderStatus(), cond.getPaymentType(), pageable);
	}

	public Orders findByOrderNumber(String orderNumber) {
		return orderRepository.findByOrderNumber(orderNumber)
			.orElseThrow(() -> new EntityNotFoundException("해당 주문이 존재하지 않습니다."));
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
		List<CartProduct> cartProducts = cartProductRepository.findAllByIdWithProduct(cartProductIds);

		// 1. 상품 재고 확인
		validateStock(cartProducts);

		// 2. 가격 계산
		int totalPrice = getTotalPrice(cartProducts);

		// 주문 이름 생성
		String orderName = buildOrderName(cartProducts);

		// 3. order 객체 생성
		Orders orders = createOrderEntity(dto, OrderStatus.READY, OrderType.CART, orderName, totalPrice);
		orders = orderRepository.saveAndFlush(orders);

		// 4. orderProduct 생성
		List<OrderProductRow> orderProductRows = new ArrayList<>();
		List<OrderCartProductRow> orderCartProductRows = new ArrayList<>();
		for (CartProduct cartProduct : cartProducts) {
			Product product = cartProduct.getProduct();
			orderProductRows.add(
				new OrderProductRow(orders.getId(), product.getId(),
					product.getPrice(), cartProduct.getQuantity())
			);

			orderCartProductRows.add(new OrderCartProductRow(orders.getId(), cartProduct.getId()));
		}

		// 데드락 방지
		orderProductRows.sort(Comparator.comparing(OrderProductRow::productId));
		orderCartProductRows.sort(Comparator.comparing(OrderCartProductRow::cartProductId));

		orderProductJdbcRepository.batchInsert(orderProductRows);
		orderCartProductJdbcRepository.batchInsert(orderCartProductRows);

		return orders; // 이 객체에서 orderProduct, orderCartProduct 사용하면 안됨.
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
				throw new BusinessException("재고가 부족합니다.", HttpStatus.BAD_REQUEST);
			}
		}
	}

	private static void validateStock(Product product, int quantity) {
		int stock = product.getStock();
		if (stock - quantity < 0) {
			throw new BusinessException("재고가 부족합니다.", HttpStatus.BAD_REQUEST);
		}
	}

}
