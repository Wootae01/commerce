    create database if not exists commerce;
    use commerce;

    -- 기존 데이터 초기화
    SET FOREIGN_KEY_CHECKS = 0;
    TRUNCATE TABLE order_cart_product;
    TRUNCATE TABLE order_product;
    TRUNCATE TABLE orders;
    TRUNCATE TABLE cart_product;
    TRUNCATE TABLE cart;
    TRUNCATE TABLE product_option;
    TRUNCATE TABLE image;
    TRUNCATE TABLE product;
    TRUNCATE TABLE `user`;
    SET FOREIGN_KEY_CHECKS = 1;

    INSERT INTO admin(username, role, password)
    SELECT "admin", "ROLE_ADMIN", "$2a$10$33jAxhxoVRtTN4ybMoSkB.ZY2jqjcx/8o2FyhgnrrDJlTkvLRF0Ji"
        WHERE NOT EXISTS (SELECT 1 FROM admin WHERE username = "admin");


    -- user 1000명 삽입
    INSERT INTO `user` (username, role, created_at, updated_at)
    WITH RECURSIVE seq(n) AS (
        SELECT 0
        UNION ALL
        SELECT n + 1 FROM seq WHERE n < 999
        )
    SELECT CONCAT('user', n), 'ROLE_USER', NOW(), NOW()
    FROM seq
        WHERE NOT EXISTS(SELECT 1 FROM user);

    -- 상품 100개 삽입
    INSERT INTO product (price, stock, admin_id, name, description, featured, created_at, updated_at)
    WITH RECURSIVE seq(n) AS (
        SELECT 0
        UNION ALL
        SELECT n+1 FROM seq WHERE n < 100
    )
    SELECT n*1000, 1000000, 1, CONCAT('상품', n), n, false, NOW(), NOW()
    FROM seq;

    -- 이미지 삽입
    -- 메인 이미지 삽입 (product_id로 추적)
    INSERT INTO image (product_id, upload_file_name, store_file_name, img_order, created_at, updated_at)
    SELECT
        p.product_id,
        'test-main_image',
        '/images/default.png',
        0,
        NOW(),
        NOW()
    FROM product p
    WHERE p.main_image_id IS NULL
      AND NOT EXISTS (
        SELECT 1 FROM image i WHERE i.product_id = p.product_id AND i.img_order = 0
    );

    -- 상품에 메인 이미지 연결 (img_order = 0인 이미지를 main_image_id로 설정)
    UPDATE product p
    SET p.main_image_id = (
        SELECT i.image_id
        FROM image i
        WHERE i.product_id = p.product_id
          AND i.img_order = 0
        LIMIT 1
    )
    WHERE p.main_image_id IS NULL;

    -- 서브 이미지
    INSERT INTO image (product_id, upload_file_name, store_file_name, img_order, created_at, updated_at)
    WITH RECURSIVE seq(n) AS (
        SELECT 1
        UNION ALL
        SELECT n+1 FROM seq WHERE n < 3
    )
    SELECT
        p.product_id,
        CONCAT('test-sub-', seq.n),
        '/images/default.png',
        seq.n,
        NOW(),
        NOW()
    FROM product p
    CROSS JOIN seq
    WHERE NOT EXISTS (
        SELECT 1 FROM image i
        WHERE i.product_id = p.product_id AND i.img_order = seq.n
    );

    -- order 50개 삽입
    INSERT INTO orders (
        order_number, payment_key, user_id, final_price, order_name,
        payment_type, order_status, approved_at, receiver_name,
        receiver_address, order_address_detail, receiver_phone, order_type, created_at, updated_at
    )
    WITH RECURSIVE seq(n) AS (
        SELECT 0
        UNION ALL
        SELECT n+1 FROM seq WHERE n < 50
    ),
        u AS (
            SELECT user_id FROM user
        )
    SELECT
        UUID()                                 AS order_number,
        UUID()                                  AS payment_key,
        u.user_id                              AS user_id,
        30000                                  AS final_price,
        'N+1 TEST ORDER'                       AS order_name,
        'EASY_PAY'                             AS payment_type,
        'PAID'                                 AS order_status,
        NOW()                                  AS approved_at,
        '홍길동'                               AS receiver_name,
        '서울시 테스트구 테스트로 123'           AS receiver_address,
        '101동 101호'                          AS order_address_detail,
        '01012345678'                          AS receiver_phone,
        'CART'                                 AS order_type,
        NOW()                                  AS created_at,
        NOW()                                   AS updated_at
    FROM u
    CROSS JOIN seq;

    -- orderProduct 삽입
    INSERT INTO order_product (order_id, product_id, quantity, price, created_at, updated_at)
    WITH
        items(k) AS (
            SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3
        ),
        o AS (
            SELECT
                order_id,
                ROW_NUMBER() OVER (ORDER BY order_id) AS rn
            FROM orders
        ),
        p AS (
            SELECT
                product_id,
                price,
                ROW_NUMBER() OVER (ORDER BY product_id) AS rn,
                    COUNT(*) OVER () AS total
            FROM product
        ),
        mapped AS (
            SELECT
                o.order_id,
                p.product_id,
                1 AS quantity,
                p.price AS price
            FROM o
                     JOIN items
                     JOIN p
                          ON p.rn = (MOD((o.rn - 1) * 3 + (items.k - 1), p.total) + 1)
        )
    SELECT
        m.order_id, m.product_id, m.quantity, m.price, NOW(), NOW()
    FROM mapped m
             LEFT JOIN order_product op
                       ON op.order_id = m.order_id AND op.product_id = m.product_id
    WHERE op.order_id IS NULL;

    -- cart
    INSERT INTO cart (created_at, updated_at, user_id)
    SELECT NOW(), NOW(), u.user_id
    FROM user u
             LEFT JOIN cart c ON c.user_id = u.user_id
    WHERE c.user_id IS NULL;

    -- 상품 절반(하위 50개)에 옵션 3개씩 삽입 (S/M/L)
    INSERT INTO product_option (name, stock, additional_price, product_id)
    WITH
        opts(opt_name, opt_price) AS (
            SELECT 'S',  0    UNION ALL
            SELECT 'M', 1000  UNION ALL
            SELECT 'L', 2000
        ),
        half_products AS (
            SELECT product_id
            FROM product
            ORDER BY product_id DESC
            LIMIT 50
        )
    SELECT o.opt_name, 100, o.opt_price, p.product_id
    FROM half_products p
    CROSS JOIN opts o
    WHERE NOT EXISTS (
        SELECT 1 FROM product_option po WHERE po.product_id = p.product_id
    );

    -- 옵션 있는 상품의 order_product에 옵션 연결 (S/M/L 균등 분배)
    UPDATE order_product op
    JOIN (
        SELECT po.id AS option_id, po.product_id,
               ROW_NUMBER() OVER (PARTITION BY po.product_id ORDER BY po.id) AS rn
        FROM product_option po
    ) ranked ON ranked.product_id = op.product_id
        AND ranked.rn = (MOD(op.order_product_id, 3) + 1)
    SET op.product_option_id = ranked.option_id
    WHERE op.product_option_id IS NULL;

    -- cart product 만들기 카트 당 50개
    INSERT INTO cart_product (is_checked, quantity, cart_id, product_id, created_at, updated_at, product_option_id)
    SELECT
        0, 1, c.cart_id, rp.product_id, NOW(), NOW(),
        (
            SELECT ranked.id FROM (
                SELECT po.id, ROW_NUMBER() OVER (ORDER BY po.id) - 1 AS rn
                FROM product_option po
                WHERE po.product_id = rp.product_id
            ) ranked
            WHERE ranked.rn = MOD(c.cart_id, 3)
        )
    FROM cart c
    JOIN (
        SELECT product_id
        FROM product
        ORDER BY RAND()
        LIMIT 30
    ) rp
    LEFT JOIN cart_product cp
              ON cp.cart_id = c.cart_id AND cp.product_id = rp.product_id
    WHERE cp.product_id IS NULL;