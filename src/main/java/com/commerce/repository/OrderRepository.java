package com.commerce.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.commerce.domain.Orders;
import com.commerce.domain.enums.OrderStatus;
import com.commerce.domain.enums.PaymentType;

public interface OrderRepository extends JpaRepository<Orders, Long> {

	void deleteByOrderNumber(String orderNumber);

	Optional<Orders> findByOrderNumber(String orderNumber);

	@Query("""
			select o from Orders o
			join fetch o.orderProducts op
			join fetch op.product p
			where o.id = :orderId
	""")
	Optional<Orders> findByOrderNumberWithProduct(Long orderId);

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
	List<Orders> searchAdminOrders(@Param("keyword") String keyword,
		@Param("startDateTime") LocalDateTime startDateTime,
		@Param("endDateTime") LocalDateTime endDateTime,
		@Param("orderStatus") OrderStatus orderStatus,
		@Param("paymentType") PaymentType paymentType);

}
