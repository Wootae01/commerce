package com.commerce.repository;

import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.commerce.dto.FeaturedItem;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Repository
public class ProductJdbcRepository {

	private final JdbcTemplate jdbcTemplate;


	/**
	 * UPDATE product p
	 * JOIN (
	 *   SELECT ? AS product_id, ? AS qty
	 *   UNION ALL SELECT ?, ?
	 *   ...
	 * ) t ON p.product_id = t.product_id
	 * SET p.stock = p.stock - t.qty
	 *
	 * 반드시 transactional 안에서 사용할 것.
	 */
	public int  updateStock(Map<Long, Integer> qtyByProductId, boolean isIncrease) {

		if (qtyByProductId == null || qtyByProductId.isEmpty()) return 0;

		List<Long> productIds = qtyByProductId.keySet().stream().sorted().distinct().toList();

		// qty 검증
		for (Long id : productIds) {
			Integer qty = qtyByProductId.get(id);
			if (qty == null || qty <= 0) {
				throw new IllegalArgumentException("invalid qty: productId=" + id + ", qty=" + qty);
			}
		}

		StringBuilder sql = new StringBuilder();
		sql.append("update product p join (");

		List<Object> params = new ArrayList<>();

		boolean first = true;
		for (Long productId : productIds) {
			int qty = qtyByProductId.get(productId);

			if (first) {
				sql.append("select ? as product_id, ? as qty ");
				first = false;
			} else {
				sql.append("union all select ?, ? ");
			}

			params.add(productId);
			params.add(qty);
		}

		sql.append(") t on p.product_id = t.product_id ");

		if (isIncrease) {
			sql.append("set p.stock = p.stock + t.qty");
			// 증가면 부족 조건 없음
		} else {
			sql.append("set p.stock = p.stock - t.qty ");
			sql.append("where p.stock >= t.qty");
		}

		int updated = jdbcTemplate.update(sql.toString(), params.toArray());

		if (!isIncrease && updated != productIds.size()) {
			throw new IllegalStateException("재고 부족(또는 상품 누락) - rollback");

		}
		return updated;
	}

	// 홈 노출 여부 수정
	public void updateFeaturedBatch(List<FeaturedItem> items) {

		String sql = """
				update product
				set featured = ?, featured_rank = ?
				where product_id = ?
			""";

		jdbcTemplate.batchUpdate(sql, items, 500, (ps, it) -> {
			ps.setBoolean(1, Boolean.TRUE.equals(it.getFeatured()));

			if (it.getFeaturedRank() == null) {
				ps.setNull(2, Types.INTEGER);
			} else {
				ps.setInt(2, it.getFeaturedRank());
			}
			ps.setLong(3, it.getProductId());

		});
	}
}
