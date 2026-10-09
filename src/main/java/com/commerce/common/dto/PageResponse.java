package com.commerce.common.dto;

import java.util.List;

import org.springframework.data.domain.Page;

/**
 * Spring Data Page를 그대로 직렬화하면 형식이 고정되지 않으므로, API 응답용으로 필요한 값만 담는다.
 */
public record PageResponse<T>(
	List<T> content,
	int page,
	int size,
	long totalElements,
	int totalPages,
	boolean hasNext
) {
	public static <T> PageResponse<T> from(Page<T> page) {
		return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(),
			page.getTotalPages(), page.hasNext());
	}
}
