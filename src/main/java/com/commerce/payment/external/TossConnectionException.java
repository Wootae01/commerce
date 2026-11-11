package com.commerce.payment.external;

/**
 * 토스와 통신하지 못한 경우(연결 실패·끊김·타임아웃). 토스가 요청을 받았는지 알 수 없다.
 * HTTP 라이브러리의 예외를 감싸서, 호출하는 쪽이 라이브러리를 몰라도 되게 한다.
 */
public class TossConnectionException extends RuntimeException {

	public TossConnectionException(String message, Throwable cause) {
		super(message, cause);
	}
}
