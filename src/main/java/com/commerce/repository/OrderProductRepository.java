package com.commerce.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.commerce.domain.OrderProduct;
import com.commerce.domain.User;
import com.commerce.domain.enums.OrderStatus;
import com.commerce.dto.OrderListRow;
import com.commerce.dto.ProductSoldRow;

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

	@Query("select op from OrderProduct op join fetch op.product where op.order.id = :orderId")
	List<OrderProduct> findOrderProductByOrderIdWithProduct(Long orderId);

	@Query("""
	  select new com.commerce.dto.ProductSoldRow(op.product.id, sum(op.quantity))
	  from OrderProduct op
	  join op.order o
	  where o.orderStatus in :statuses
		and o.approvedAt >= :since
	  group by op.product.id
	  order by sum(op.quantity) desc
	""")
	List<ProductSoldRow> findPopularProducts( List<OrderStatus> statuses, LocalDateTime since,  Pageable pageable);
}
