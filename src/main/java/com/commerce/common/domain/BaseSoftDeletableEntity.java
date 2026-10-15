package com.commerce.common.domain;

import java.time.LocalDateTime;

import jakarta.persistence.MappedSuperclass;
import lombok.Getter;

/**
 * 수정 이력에 더해 삭제 이력을 가지는 엔티티.
 * 삭제는 deletedAt을 채우는 soft delete이며, 조회 쿼리에서 deletedAt IS NULL 조건으로 직접 거른다.
 * 지난 주문은 삭제된 상품·옵션도 계속 보여줘야 하므로 @SQLRestriction으로 일괄 숨기지 않는다.
 */
@MappedSuperclass
@Getter
public abstract class BaseSoftDeletableEntity extends BaseUpdatableEntity {

	private LocalDateTime deletedAt;

	private String deletedBy;

	public void softDelete(String deletedBy) {
		this.deletedAt = LocalDateTime.now();
		this.deletedBy = deletedBy;
	}

	public boolean isDeleted() {
		return deletedAt != null;
	}
}
