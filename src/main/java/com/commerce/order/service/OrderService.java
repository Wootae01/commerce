package com.commerce.order.service;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.commerce.common.code.GeneralResponseCode;
import com.commerce.common.exception.ApiException;
import java.util.UUID;
import java.util.stream.Collectors;

import com.commerce.common.util.ProductImageUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.commerce.cart.domain.CartProduct;
import com.commerce.order.domain.OrderAmount;
import com.commerce.order.domain.Orders;
import com.commerce.product.domain.Product;
import com.commerce.product.domain.ProductOption;
import com.commerce.user.domain.User;
import com.commerce.common.enums.OrderStatus;
import com.commerce.payment.domain.Payment;
import com.commerce.payment.repository.PaymentRepository;
import com.commerce.admin.dto.AdminOrderListResponseDTO;
import com.commerce.admin.dto.AdminOrderSearchCond;
import com.commerce.order.dto.OrderCartProductRow;
import com.commerce.order.dto.OrderCreateRequestDTO;
import com.commerce.order.dto.OrderDetailResponseDTO;
import com.commerce.order.dto.OrderMapper;
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
import com.commerce.product.service.ProductService;
import com.commerce.common.util.SecurityUtil;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class OrderService {

	private final OrderRepository orderRepository;
	private final CartProductRepository cartProductRepository;
	private final ProductService productService;
	private final SecurityUtil securityUtil;
	private final ProductRepository productRepository;
	private final OrderProductRepository orderProductRepository;
	private final OrderProductJdbcRepository orderProductJdbcRepository;
	private final OrderCartProductJdbcRepository orderCartProductJdbcRepository;
	private final ProductImageUtil productImageUtil;
	private final OrderMapper orderMapper;
	private final PaymentRepository paymentRepository;

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
					r.optionName(),
					r.quantity(),
					r.price(),
					imageUrl
				)
			);
		}
		List<OrderResponseDTO> result = new ArrayList<>(byOrderId.values());

		return new PageImpl<>(result, pageable, orderHeaders.getTotalElements());
	}

	// 관리자 주문 상태 변경: 배송 흐름으로 한 단계씩만. 하나라도 허용되지 않으면 예외로 전부 롤백
	@Transactional
	public void changeStatus(List<Long> orderIds,OrderStatus status) {
		List<Orders> orders = orderRepository.findAllById(orderIds);
		for (Orders order : orders) {
			order.advanceDelivery(status);
		}
	}

	// 관리자 주문 검색
	public Page<AdminOrderListResponseDTO> getOrderList(AdminOrderSearchCond cond, Pageable pageable) {
		LocalDateTime start = null;
		LocalDateTime end = null;
		if (cond.getStartDate() != null) {
			start = cond.getStartDate().atTime(LocalTime.MIN);
		}
		if (cond.getEndDate() != null) {
			end = cond.getEndDate().atTime(LocalTime.MAX);
		}
		return orderRepository.searchAdminOrders(cond.getKeyword(), start, end,
			cond.getOrderStatus(), cond.getPaymentType(), pageable)
			.map(AdminOrderListResponseDTO::from);
	}

	public Orders findByOrderNumber(String orderNumber) {
		return orderRepository.findByOrderNumber(orderNumber)
			.orElseThrow(() -> new ApiException(GeneralResponseCode.ORDER_NOT_FOUND));
	}

	// 타인의 주문번호로 요청하는 경우를 막기 위해 주문 소유자를 검증한다.
	// 남의 주문도 없는 주문과 같은 응답을 줘서 주문번호가 있는지 알 수 없게 한다. 구별은 경고 로그로만 한다.
	public Orders findMyOrder(String orderNumber, Long userId) {
		Orders order = findByOrderNumber(orderNumber);
		if (!order.getUser().getId().equals(userId)) {
			log.warn("본인 주문이 아닙니다. orderNumber={}, orderUserId={}, userId={}", orderNumber,
				order.getUser().getId(), userId);
			throw new ApiException(GeneralResponseCode.ORDER_NOT_FOUND);
		}
		return order;
	}

	// 구매자 주문 상세(화면·API)
	public OrderDetailResponseDTO getMyOrderDetail(String orderNumber, User user) {
		Orders order = findMyOrder(orderNumber, user.getId());
		Payment payment = paymentRepository.findByOrderId(order.getId()).orElse(null);
		return orderMapper.toOrderDetailResponseDTO(order, payment, user);
	}

	/**
	 * 결제 전 주문을 만들어 저장한다(READY). 응답으로 토스 결제창을 띄운다.
	 *
	 * <p>바로구매와 장바구니 주문은 무엇을 몇 개 사는지 알아내는 입구만 다르고, 그 뒤(재고 확인·상품 합계·주문 이름·저장)는 같다.
	 * 장바구니 주문은 결제 후 장바구니에서 뺄 상품을 알 수 있게 장바구니 연결도 저장한다.
	 *
	 * <p>주문 상품·장바구니 연결은 JDBC batch로 넣으므로, 돌려준 주문 객체의 orderProducts·orderCartProducts는 비어 있다. 읽으면 안 된다.
	 */
	@Transactional
	public Orders prepareOrder(OrderCreateRequestDTO dto) {
		return switch (dto.getOrderType()) {
			case CART -> {
				List<CartProduct> cartProducts = findOrderableCartProducts(dto.getCartProductIds());
				Orders order = saveOrder(dto, cartProducts.stream().map(OrderProductDraft::of).toList());
				saveCartLinks(order, cartProducts);
				yield order;
			}
			case BUY_NOW -> saveOrder(dto, List.of(buyNowDraft(dto)));
		};
	}

	// 장바구니 주문 입구: 본인 장바구니 상품만 조회하고, 요청한 ID 중 하나라도 빠지면 거부한다.
	// 장바구니에 담은 뒤 판매 중지된 상품·옵션은 주문할 수 없다.
	private List<CartProduct> findOrderableCartProducts(List<Long> cartProductIds) {
		if (cartProductIds == null || cartProductIds.isEmpty()) {
			throw new ApiException(GeneralResponseCode.ORDER_EMPTY);
		}

		Long userId = securityUtil.getCurrentUser().getId();
		List<CartProduct> cartProducts = cartProductRepository.findAllByIdWithProductAndUserId(cartProductIds, userId);
		if (cartProducts.size() != cartProductIds.stream().distinct().count()) {
			throw new ApiException(GeneralResponseCode.CART_ITEM_NOT_FOUND);
		}

		for (CartProduct cartProduct : cartProducts) {
			if (cartProduct.getProduct().isDeleted()) {
				throw new ApiException(GeneralResponseCode.PRODUCT_NOT_FOUND);
			}
			if (cartProduct.getProductOption().isDeleted()) {
				throw new ApiException(GeneralResponseCode.PRODUCT_OPTION_NOT_FOUND);
			}
		}
		return cartProducts;
	}

	// 바로구매 입구: 판매 중지된 상품·옵션이면 조회에서 거부된다.
	private OrderProductDraft buyNowDraft(OrderCreateRequestDTO dto) {
		if (dto.getProductId() == null || dto.getQuantity() < 1) {
			throw new ApiException(GeneralResponseCode.INVALID_REQUEST);
		}

		Product product = productService.findById(dto.getProductId());
		ProductOption option = productService.resolveOption(product.getId(), dto.getOptionId());
		return new OrderProductDraft(product, option, dto.getQuantity());
	}

	// 공통: 재고 확인 → 상품 합계 → 주문 이름 → 주문 저장 → 주문 상품 저장
	private Orders saveOrder(OrderCreateRequestDTO dto, List<OrderProductDraft> drafts) {
		for (OrderProductDraft draft : drafts) {
			validateStock(draft.option(), draft.quantity());
		}

		int productsTotal = drafts.stream().mapToInt(OrderProductDraft::lineTotal).sum();
		Orders order = orderRepository.saveAndFlush(createOrderEntity(dto, buildOrderName(drafts), productsTotal));

		List<OrderProductRow> rows = drafts.stream()
			.map(draft -> new OrderProductRow(order.getId(), draft.product().getId(), draft.option().getId(),
				draft.unitPrice(), draft.quantity()))
			.toList();
		try {
			orderProductJdbcRepository.batchInsert(rows);
		} catch (DataAccessException e) {
			log.error("주문 상품 batch insert 실패: orderId={}, productCount={}", order.getId(), rows.size(), e);
			throw new ApiException(GeneralResponseCode.ORDER_CREATE_FAILED);
		}
		return order;
	}

	// 장바구니 주문만: 결제 후 장바구니에서 뺄 상품을 기록한다.
	private void saveCartLinks(Orders order, List<CartProduct> cartProducts) {
		List<OrderCartProductRow> rows = cartProducts.stream()
			.map(cartProduct -> new OrderCartProductRow(order.getId(), cartProduct.getId()))
			.sorted(Comparator.comparing(OrderCartProductRow::cartProductId))
			.toList();
		try {
			orderCartProductJdbcRepository.batchInsert(rows);
		} catch (DataAccessException e) {
			log.error("장바구니 연결 batch insert 실패: orderId={}, cartProductCount={}", order.getId(), rows.size(), e);
			throw new ApiException(GeneralResponseCode.ORDER_CREATE_FAILED);
		}
	}

	// 주문 이름: 첫 상품명, 상품이 여럿이면 "첫 상품명 외 N건"
	private static String buildOrderName(List<OrderProductDraft> drafts) {
		String firstName = drafts.get(0).product().getName();
		int others = drafts.size() - 1;
		return others > 0 ? firstName + " 외 " + others + "건" : firstName;
	}

	// 저장 전 주문 상품: 어떤 상품의 어떤 옵션을 몇 개 사는지. 단가는 옵션에서 정한다.
	private record OrderProductDraft(Product product, ProductOption option, int quantity) {

		static OrderProductDraft of(CartProduct cartProduct) {
			return new OrderProductDraft(cartProduct.getProduct(), cartProduct.getProductOption(), cartProduct.getQuantity());
		}

		int unitPrice() {
			return option.getUnitPrice();
		}

		int lineTotal() {
			return unitPrice() * quantity;
		}
	}

	private Orders createOrderEntity(OrderCreateRequestDTO dto, String orderName, int productsTotal) {

		Orders orders = Orders
			.builder()
			.receiverAddress(dto.getAddress())
			.orderNumber(UUID.randomUUID().toString().replace("-", ""))
			.orderAddressDetail(dto.getAddressDetail())
			.orderName(orderName)
			.receiverName(dto.getName())
			.receiverPhone(dto.getPhone())
			.requestNote(dto.getRequestNote())
			.orderType(dto.getOrderType())
			.orderStatus(OrderStatus.READY)
			.user(securityUtil.getCurrentUser())
			.amount(OrderAmount.forNewOrder(productsTotal))
			.build();

		return orders;
	}

	private static void validateStock(ProductOption option, int quantity) {
		if (option.getStock() - quantity < 0) {
			throw new ApiException(GeneralResponseCode.PRODUCT_OUT_OF_STOCK, "재고가 부족합니다. (옵션: " + option.getName() + ")");
		}
	}

}
