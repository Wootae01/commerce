package com.commerce.order.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import com.commerce.order.domain.Orders;
import com.commerce.user.domain.User;
import com.commerce.common.enums.OrderStatus;
import com.commerce.common.enums.PaymentType;
import com.commerce.order.dto.OrderHeaderRow;

public interface OrderRepository extends JpaRepository<Orders, Long> {

	void deleteByOrderNumber(String orderNumber);

	Optional<Orders> findByOrderNumber(String orderNumber);

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

	@Query("""
			select o from Orders o
			join fetch o.orderProducts op
			join fetch op.product p
			left join fetch op.productOption
			where o.orderNumber = :orderNumber
	""")
	Optional<Orders> findByOrderNumberWithProduct(@Param("orderNumber") String orderNumber);

	@Query("""
		select o from Orders o
		 join fetch o.user u
		where(:orderStatus is null or o.orderStatus = :orderStatus)
		and (:paymentType is null or o.paymentType =:paymentType)
		and (:keyword is null
			or o.orderNumber like concat('%', :keyword, '%')
			or u.name like concat('%', :keyword, '%')
			or o.receiverPhone like concat('%', :keyword, '%')
			)
		and (:startDateTime is null or o.createdAt >= :startDateTime)
		and (:endDateTime is null or o.createdAt <= :endDateTime)
		order by o.createdAt desc
	""")
	Page<Orders> searchAdminOrders(@Param("keyword") String keyword,
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
