-- Migration script: 결제 정보(payment_key, payment_type, approved_at)를 orders에서 payment로 옮긴다. (#23)
-- 주문 하나에 결제는 하나다. 결제를 시도하지 않은 주문(payment_key 없음)은 payment 행이 없다.
-- 새 코드 배포 직후 실행한다. 배포하면 ddl-auto=update가 payment 테이블을 만든다.
-- 새 코드는 orders의 결제 컬럼을 쓰지 않으므로, 배포 후 이 스크립트를 실행하기 전까지 기존 주문의 결제 수단·승인 시각이 '기타'·'-'로 보인다.

-- Step 1: 결제를 시도한 주문의 결제 정보를 payment로 복사한다. amount는 승인 요청한 금액(final_price)이다.
--         다시 실행해도 같은 주문을 두 번 넣지 않는다.
INSERT INTO payment (order_id, payment_key, amount, payment_type, approved_at, created_at, updated_at)
SELECT o.order_id, o.payment_key, o.final_price, o.payment_type, o.approved_at, o.created_at, o.updated_at
FROM orders o
WHERE o.payment_key IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM payment p WHERE p.order_id = o.order_id
);

-- Step 2: 확인. 두 숫자가 같아야 한다. 다르면 Step 3을 실행하지 않는다.
--         payment 전체 행 수와 비교하지 않는다. 배포 후 이 스크립트 전에 시작된 결제는 payment에만 있고
--         orders.payment_key는 비어 있어서, 전체와 비교하면 정상인데도 숫자가 어긋난다.
--         그래서 orders의 payment_key와 같은 키로 옮겨진 행만 센다.
SELECT
    (SELECT COUNT(*) FROM orders WHERE payment_key IS NOT NULL) AS orders_with_payment,
    (SELECT COUNT(*)
     FROM orders o
     JOIN payment p ON p.order_id = o.order_id AND p.payment_key = o.payment_key
     WHERE o.payment_key IS NOT NULL) AS copied_payments;

-- Step 3: orders의 결제 컬럼 삭제. (ddl-auto=update는 컬럼을 지우지 않는다)
--         payment_key의 unique 제약(인덱스)은 컬럼과 함께 지워진다.
ALTER TABLE orders
    DROP COLUMN payment_key,
    DROP COLUMN payment_type,
    DROP COLUMN approved_at;
