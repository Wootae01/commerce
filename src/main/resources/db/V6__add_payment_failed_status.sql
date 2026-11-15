-- Migration script: 결제 실패한 주문을 주문 취소(CANCELED)와 구분해 PAYMENT_FAILED로 둔다. (#66)
-- 지금까지는 결제 실패도 CANCELED로 저장해, 사용자 주문 목록에서 결제 실패가 "취소완료"로 보였다.

-- Step 0: 컬럼 타입 확인. Hibernate는 @Enumerated(STRING)을 MySQL ENUM 컬럼으로 만들고,
--         ddl-auto=update는 이미 있는 컬럼의 ENUM 값 목록을 바꾸지 않는다.
--         Type이 enum(...)이면 Step 1을 실행한다. varchar면 Step 1은 건너뛴다.
SHOW COLUMNS FROM orders LIKE 'order_status';

-- Step 1: ENUM에 PAYMENT_FAILED를 추가한다. 새 코드를 배포하기 전에 실행한다.
--         (먼저 배포하면 결제 실패 반영이 'Data truncated'로 실패한다)
--         값 목록은 OrderStatus 순서와 같다. 기존 값은 그대로라 다시 실행해도 결과가 같다.
ALTER TABLE orders MODIFY order_status ENUM(
    'READY', 'PAYMENT_PENDING', 'PAYMENT_FAILED', 'PAID', 'PREPARING', 'SHIPPING',
    'CANCEL_REQUESTED', 'CANCELED', 'DELIVERED'
);

-- Step 2: 기존 결제 실패 주문을 옮긴다. 새 코드를 배포한 뒤 실행한다.
--         결제 실패 = 결제를 시도했지만(payment 행이 있음) 승인되지 않은 채(payment_type 없음) CANCELED인 주문.
--         사용자가 취소한 주문은 결제 완료였으므로 payment_type이 있다. 승인되면 결제 수단을 모를 때도 UNKNOWN으로 채운다.
--         approved_at으로 가리지 않는다. 토스 응답에 승인 시각이 없으면 결제 완료여도 approved_at이 비어 있다.
--         결제를 시도하지 않고 취소한 결제 준비 주문은 payment 행이 없어 CANCELED로 남는다.
UPDATE orders o
JOIN payment p ON p.order_id = o.order_id
SET o.order_status = 'PAYMENT_FAILED'
WHERE o.order_status = 'CANCELED'
  AND p.payment_type IS NULL;

-- Step 3: 확인. 아래 숫자가 모두 0이어야 한다.
--         failed_with_approval: 승인된(결제 수단이 있는) 결제인데 결제 실패인 주문
--         canceled_with_failure: 실패 이유가 있는데 아직 CANCELED인 주문
SELECT
    (SELECT COUNT(*) FROM orders o JOIN payment p ON p.order_id = o.order_id
        WHERE o.order_status = 'PAYMENT_FAILED' AND p.payment_type IS NOT NULL) AS failed_with_approval,
    (SELECT COUNT(*) FROM orders o JOIN payment p ON p.order_id = o.order_id
        WHERE o.order_status = 'CANCELED' AND p.failure_code IS NOT NULL) AS canceled_with_failure;
