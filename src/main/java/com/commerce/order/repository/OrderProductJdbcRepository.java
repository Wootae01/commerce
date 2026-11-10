package com.commerce.order.repository;

import java.util.Comparator;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.commerce.order.dto.OrderProductRow;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class OrderProductJdbcRepository {

	private final JdbcTemplate jdbcTemplate;

	/**
	 * 주문 상품 행을 넣을 때 MySQL은 FK 확인으로 옵션 행에 S락을 잡는다. 결제 재고 차감은 같은 옵션 행에 optionId 오름차순으로 X락을 잡는다.
	 * 넣는 순서가 이와 다르면 두 트랜잭션이 락을 엇갈려 잡아 데드락이 난다. 그래서 부르는 쪽에 맡기지 않고 여기서 optionId 오름차순으로 넣는다.
	 */
	public void batchInsert(List<OrderProductRow> rows) {

		String sql = """
            insert into order_product (order_id, product_id, product_option_id, price, quantity)
            values (?, ?, ?, ?, ?)
        """;

		List<OrderProductRow> sorted = rows.stream()
			.sorted(Comparator.comparing(OrderProductRow::optionId))
			.toList();

		jdbcTemplate.batchUpdate(sql, sorted, 1000, (ps, row) -> {
			ps.setLong(1, row.orderId());
			ps.setLong(2, row.productId());
			ps.setLong(3, row.optionId());
			ps.setInt(4, row.price());
			ps.setInt(5, row.quantity());
		});
	}
}
