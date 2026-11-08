-- Migration script: 주문에 배송비(delivery_fee)를 저장한다. (#47)
-- 새 코드 배포 직후 실행한다. 배포하면 ddl-auto=update가 orders.delivery_fee 컬럼(int NOT NULL)을 만들고, 기존 주문은 0이 들어간다.
-- 이 스크립트를 실행하기 전까지 기존 주문의 상세에서 배송비가 0원으로 보인다(상품 합계·최종 결제금액은 맞게 보인다).

-- Step 1: 기존 주문의 배송비를 채운다. 결제 금액(final_price)은 상품 합계 + 배송비로 저장해 왔으므로 차이가 그 주문의 배송비다.
--         상품 합계는 주문 상품의 단가(price) × 수량을 더한 값이다.
--         배송비가 0인 주문만 고치므로 다시 실행해도 결과가 같다. (배포 후 만든 주문은 이미 배송비가 들어 있다)
UPDATE orders o
JOIN (
    SELECT op.order_id, SUM(op.price * op.quantity) AS products_total
    FROM order_product op
    GROUP BY op.order_id
) t ON t.order_id = o.order_id
SET o.delivery_fee = o.final_price - t.products_total
WHERE o.delivery_fee = 0;

-- Step 2: 확인. 지금까지 배송비는 3,000원 하나였으므로 아래 숫자가 모두 0이어야 한다.
--         unexpected_fee가 0이 아니면 그 주문들의 final_price와 주문 상품을 직접 확인한다.
--         no_order_product는 주문 상품이 없어 Step 1에서 채우지 못한 주문이다.
SELECT
    (SELECT COUNT(*) FROM orders WHERE delivery_fee <> 3000
        AND order_id IN (SELECT order_id FROM order_product)) AS unexpected_fee,
    (SELECT COUNT(*) FROM orders
        WHERE order_id NOT IN (SELECT order_id FROM order_product)) AS no_order_product;
