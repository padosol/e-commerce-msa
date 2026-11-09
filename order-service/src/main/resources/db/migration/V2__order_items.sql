CREATE TABLE order_items (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    product_name VARCHAR(200) NOT NULL,
    unit_price BIGINT NOT NULL,
    quantity INT NOT NULL,
    CONSTRAINT fk_order_items_order FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE CASCADE
);
INSERT INTO order_items (order_id, product_id, product_name, unit_price, quantity)
    SELECT id, product_id, '', amount DIV quantity, quantity FROM orders;
ALTER TABLE orders
    MODIFY event_id BIGINT NULL,
    DROP COLUMN product_id,
    DROP COLUMN quantity,
    ADD INDEX idx_orders_user (user_id, id);
