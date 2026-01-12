package com.commerce.repository;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.commerce.dto.OrderCartProductRow;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class OrderCartProductJdbcRepository {

	private final JdbcTemplate jdbcTemplate;

	public void batchInsert(List<OrderCartProductRow> rows) {
		String sql = """
            insert into order_cart_product (order_id, cart_product_id)
            values (?, ?)
        """;

		jdbcTemplate.batchUpdate(sql, rows, 1000, (ps, row) -> {
			ps.setLong(1, row.orderId());
			ps.setLong(2, row.cartProductId());
		});
	}
}
