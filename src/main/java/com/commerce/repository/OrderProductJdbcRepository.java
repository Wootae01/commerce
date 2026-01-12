package com.commerce.repository;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.commerce.dto.OrderProductRow;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class OrderProductJdbcRepository {

	private final JdbcTemplate jdbcTemplate;

	public void batchInsert(List<OrderProductRow> rows) {

		String sql = """
            insert into order_product (order_id, product_id, price, quantity)
            values (?, ?, ?, ?)
        """;

		jdbcTemplate.batchUpdate(sql, rows, 1000, (ps, row) -> {
			ps.setLong(1, row.orderId());
			ps.setLong(2, row.productId());
			ps.setInt(3, row.price());
			ps.setInt(4, row.quantity());
		});
	}
}
