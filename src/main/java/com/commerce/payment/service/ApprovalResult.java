package com.commerce.payment.service;

import com.commerce.common.enums.OrderStatus;

/**
 * 승인 요청 결과.
 * @param status 반영 후 주문 상태
 * @param tossUnreachable 승인 요청이 네트워크 오류·5xx로 끝나 토스와 통신하지 못했는지. 보정 스케줄러가 토스 장애를 판단하는 데 쓴다
 */
public record ApprovalResult(OrderStatus status, boolean tossUnreachable) {
}
