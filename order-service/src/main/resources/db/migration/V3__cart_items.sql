CREATE TABLE cart_items (user_id BIGINT NOT NULL, product_id BIGINT NOT NULL, quantity INT NOT NULL, PRIMARY KEY (user_id, product_id));
