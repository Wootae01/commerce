-- order user_id fk index
create INDEX idx_user_fk on orders(user_id);

-- order user_id, created_at index
CREATE INDEX idx_order_user_create ON orders(user_id, created_at);

-- product price index
CREATE INDEX idx_product_price ON product(price);