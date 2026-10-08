package com.commerce.common.dto;

import org.springframework.http.ResponseEntity;

import com.commerce.common.code.ApiResponseCode;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class GeneralResponse<T> {
	private Integer status;
	private T data;

	public static <T> ResponseEntity<GeneralResponse<T>> toResponseEntity(ApiResponseCode responseCode, T data) {
		return ResponseEntity.status(responseCode.getStatus())
			.body(GeneralResponse.<T>builder()
				.status(responseCode.getStatus().value())
				.data(data)
				.build());
	}
}
