package com.commerce.common.code;

import org.springframework.http.HttpStatus;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum GeneralResponseCode implements ApiResponseCode {
	// Common
	OK(HttpStatus.OK, "요청이 성공적으로 처리되었습니다."),
	CREATED(HttpStatus.CREATED, "성공적으로 생성되었습니다."),
	INVALID_REQUEST(HttpStatus.BAD_REQUEST, "유효하지 않은 요청입니다."),
	INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "알 수 없는 오류가 발생했습니다."),

	// User
	USER_NOT_FOUND(HttpStatus.NOT_FOUND, "등록된 사용자가 아닙니다."),
	ADMIN_NOT_FOUND(HttpStatus.NOT_FOUND, "해당 관리자를 찾을 수 없습니다."),

	// Cart
	CART_ITEM_NOT_FOUND(HttpStatus.NOT_FOUND, "장바구니에 해당 상품이 없습니다."),

	// Product
	PRODUCT_NOT_FOUND(HttpStatus.NOT_FOUND, "해당 상품을 찾을 수 없습니다."),
	PRODUCT_OPTION_NOT_FOUND(HttpStatus.NOT_FOUND, "해당 옵션을 찾을 수 없습니다."),
	PRODUCT_OUT_OF_STOCK(HttpStatus.BAD_REQUEST, "재고가 부족합니다."),
	PRODUCT_OPTION_MISMATCH(HttpStatus.BAD_REQUEST, "해당 상품의 옵션이 아닙니다."),

	// Order
	ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, "해당 주문이 존재하지 않습니다."),
	ORDER_ACCESS_DENIED(HttpStatus.FORBIDDEN, "본인 주문이 아닙니다."),
	ORDER_EMPTY(HttpStatus.BAD_REQUEST, "주문 상품이 없습니다."),
	ORDER_CREATE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "주문 생성 중 오류가 발생했습니다."),
	ORDER_CANCEL_NOT_ALLOWED(HttpStatus.CONFLICT, "현재 상태에서는 주문을 취소할 수 없습니다."),

	// Payment
	PAYMENT_ALREADY_PROCESSED(HttpStatus.CONFLICT, "이미 처리된 주문입니다."),
	PAYMENT_NOT_PAYABLE(HttpStatus.CONFLICT, "결제 가능한 상태가 아닙니다."),
	PAYMENT_AMOUNT_INVALID(HttpStatus.BAD_REQUEST, "결제 금액이 일치하지 않습니다."),

	// Image
	IMAGE_EMPTY(HttpStatus.BAD_REQUEST, "빈 파일입니다."),
	IMAGE_TYPE_INVALID(HttpStatus.BAD_REQUEST, "이미지 파일만 업로드할 수 있습니다.");

	private final HttpStatus status;
	private final String message;
}
