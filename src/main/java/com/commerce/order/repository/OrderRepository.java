package com.commerce.order.repository;

import com.commerce.common.enums.OrderStatus;
import com.commerce.common.enums.PaymentType;
import com.commerce.order.domain.Orders;
import com.commerce.order.dto.AdminOrderRow;
import com.commerce.order.dto.OrderHeaderRow;
import com.commerce.user.domain.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Orders, Long> {

	@Query("select o from Orders o where o.orderNumber = :orderNumber")
	Optional<Orders> findByOrderNumber(@Param("orderNumber") String orderNumber);

	/**
	 * 결제 처리 시 동일 주문에 대한 중복 요청을 막기 위해 비관적 락(FOR UPDATE)으로 조회한다.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select o from Orders o where o.id = :id")
	Optional<Orders> findByIdWithLock(@Param("id") Long id);

	/** {@link #findByIdWithLock}과 동일하나 orderNumber로 조회한다. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select o from Orders o where o.orderNumber = :orderNumber")
	Optional<Orders> findByOrderNumberWithLock(@Param("orderNumber") String orderNumber);

	/**
	 * 보정 스케줄러 대상: updatedAfter 이후 updatedBefore 이전에 마지막으로 바뀐 상태의 주문.
	 * 아직 처리하지 않은 주문, 처리한 지 오래된 주문 순으로 꺼내서, 결과를 계속 모르는 주문이 앞자리를 차지하지 않게 한다.
	 */
	@Query("""
		select o from Orders o
		where o.orderStatus = :orderStatus
		and o.updatedAt <= :updatedBefore
		and o.updatedAt > :updatedAfter
		order by o.lastRecoveryAt asc nulls first, o.updatedAt asc
	""")
	List<Orders> findRecoveryTargets(@Param("orderStatus") OrderStatus orderStatus,
		@Param("updatedBefore") LocalDateTime updatedBefore,
		@Param("updatedAfter") LocalDateTime updatedAfter,
		Limit limit);

	/** 보정 스케줄러가 자동 처리를 그만둔 주문: updatedBefore 이전에 마지막으로 바뀐 상태의 주문 */
	@Query("""
		select o from Orders o
		where o.orderStatus = :orderStatus
		and o.updatedAt <= :updatedBefore
	""")
	List<Orders> findRecoveryExpired(@Param("orderStatus") OrderStatus orderStatus,
		@Param("updatedBefore") LocalDateTime updatedBefore);

	/** 보정 스케줄러가 처리한 시각을 남긴다. 벌크 update라 updated_at(PENDING이 된 시각)은 바뀌지 않는다. */
	@Transactional
	@Modifying
	@Query("update Orders o set o.lastRecoveryAt = :at where o.id = :id")
	void markRecoveryAttempted(@Param("id") Long id, @Param("at") LocalDateTime at);

	@Query("""
			select o from Orders o
			join fetch o.orderProducts op
			join fetch op.product p
			join fetch op.productOption
			where o.orderNumber = :orderNumber
	""")
	Optional<Orders> findByOrderNumberWithProduct(@Param("orderNumber") String orderNumber);

	// 결제 수단은 Payment에 있어 함께 조인한다(결제 전 주문은 Payment가 없으므로 left join)
	@Query(value = """
		select new com.commerce.order.dto.AdminOrderRow(
			o.id, o.orderNumber, o.createdAt, u.name, p.paymentType, o.receiverPhone, o.finalPrice, o.orderStatus
		)
		from Orders o
		join o.user u
		left join Payment p on p.order = o
		where (:orderStatus is null or o.orderStatus = :orderStatus)
		and (:paymentType is null or p.paymentType = :paymentType)
		and (:keyword is null
			or o.orderNumber like concat('%', :keyword, '%')
			or u.name like concat('%', :keyword, '%')
			or o.receiverPhone like concat('%', :keyword, '%')
			)
		and (:startDateTime is null or o.createdAt >= :startDateTime)
		and (:endDateTime is null or o.createdAt <= :endDateTime)
		order by o.createdAt desc
	""")
	Page<AdminOrderRow> searchAdminOrders(@Param("keyword") String keyword,
		@Param("startDateTime") LocalDateTime startDateTime,
		@Param("endDateTime") LocalDateTime endDateTime,
		@Param("orderStatus") OrderStatus orderStatus,
		@Param("paymentType") PaymentType paymentType,
		Pageable pageable
	);

	@Query(value = """
    select new com.commerce.order.dto.OrderHeaderRow(
        o.id, o.orderNumber, o.createdAt, o.orderStatus, o.finalPrice
    )
    from Orders o
    where o.user = :user
    order by o.createdAt desc
""", countQuery = """
    select count(o) from Orders o where o.user = :user
""")
	Page<OrderHeaderRow> findOrderHeaders(@Param("user") User user, Pageable pageable);

}
