package com.commerce.common.domain;

import java.time.LocalDateTime;

import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;

@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
@Getter
public abstract class BaseEntity {
	@CreatedDate
	@Column(updatable = false)
	private LocalDateTime createdAt;

	// 로그인 username. 사용자·관리자 테이블이 나뉘어 있고, principal에서 추가 쿼리 없이 꺼낼 수 있는 값이라 id 대신 쓴다.
	@CreatedBy
	@Column(updatable = false)
	private String createdBy;
}
