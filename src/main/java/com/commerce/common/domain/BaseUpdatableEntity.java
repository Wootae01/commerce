package com.commerce.common.domain;

import java.time.LocalDateTime;

import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;

import jakarta.persistence.MappedSuperclass;
import lombok.Getter;

/**
 * 생성 이력에 더해 수정 이력을 가지는 엔티티.
 * 삭제가 필요한 엔티티는 {@link BaseSoftDeletableEntity}를 상속한다.
 */
@MappedSuperclass
@Getter
public abstract class BaseUpdatableEntity extends BaseEntity {

	@LastModifiedDate
	private LocalDateTime updatedAt;

	@LastModifiedBy
	private String updatedBy;
}
