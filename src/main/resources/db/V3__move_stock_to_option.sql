-- Migration script: 재고를 product.stock에서 product_option.stock으로 옮긴다.
-- 모든 상품은 옵션을 1개 이상 가지고, 장바구니·주문은 항상 옵션을 가리킨다.
-- 새 코드 배포 직후 실행한다. product.stock(NOT NULL)이 남아 있으면 상품 등록이 실패한다.

-- Step 1: 옵션이 없는 상품에 '단품' 옵션을 만들고 상품 재고를 옮긴다.
INSERT INTO product_option (product_id, name, stock, additional_price)
SELECT p.product_id, '단품', p.stock, 0
FROM product p
WHERE NOT EXISTS (
    SELECT 1 FROM product_option o WHERE o.product_id = p.product_id
);

-- Step 2: 옵션 없이 담긴 장바구니 상품을 '단품' 옵션에 연결한다.
--         옵션이 1개뿐인 상품만 대상이라 Step 1에서 만든 옵션이 연결된다.
UPDATE cart_product cp
JOIN (
    SELECT product_id, MIN(id) AS option_id
    FROM product_option
    GROUP BY product_id
    HAVING COUNT(*) = 1
) single ON single.product_id = cp.product_id
SET cp.product_option_id = single.option_id
WHERE cp.product_option_id IS NULL;

-- Step 3: 옵션이 여러 개인 상품을 옵션 없이 담은 장바구니 상품은 고를 옵션을 알 수 없으므로 지운다.
DELETE FROM cart_product WHERE product_option_id IS NULL;

-- Step 4: 옵션 없이 주문한 주문 상품을 '단품' 옵션에 연결한다. (취소 시 재고 복원 대상)
UPDATE order_product op
JOIN (
    SELECT product_id, MIN(id) AS option_id
    FROM product_option
    GROUP BY product_id
    HAVING COUNT(*) = 1
) single ON single.product_id = op.product_id
SET op.product_option_id = single.option_id
WHERE op.product_option_id IS NULL;

-- Step 5: 확인. 옵션 상품을 옵션 없이 주문한 기록이 남으면 결과가 나온다.
--         결과가 있으면 Step 6의 order_product NOT NULL 변경이 실패하므로, 해당 주문을 먼저 처리한다.
SELECT op.order_product_id, op.order_id, op.product_id
FROM order_product op
WHERE op.product_option_id IS NULL;

-- Step 6: 장바구니·주문 상품은 항상 옵션을 가리킨다. (ddl-auto=update는 기존 컬럼의 NULL 허용을 바꾸지 않는다)
ALTER TABLE cart_product MODIFY product_option_id BIGINT NOT NULL;
ALTER TABLE order_product MODIFY product_option_id BIGINT NOT NULL;

-- Step 7: 상품 재고 컬럼 삭제
ALTER TABLE product DROP COLUMN stock;
