package com.commerce.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.commerce.domain.OrderProduct;
import com.commerce.domain.User;
import com.commerce.dto.OrderListRow;

public interface OrderProductRepository extends JpaRepository<OrderProduct, Long> {
	@Query("""
    select new com.commerce.dto.OrderListRow(
        o.orderNumber,
        o.createdAt,
        o.orderStatus,
        o.finalPrice,
        p.id,
        p.name,
        op.quantity,
        op.price
    )
    from OrderProduct op
    join op.order o
    join op.product p
    where o.user = :user
    order by o.createdAt desc
""")
	List<OrderListRow> findOrderListRows(@Param("user") User user);
}
