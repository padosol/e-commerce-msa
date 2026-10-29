CREATE TABLE products (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    price BIGINT NOT NULL
);

CREATE TABLE flash_sale_events (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_id BIGINT NOT NULL,
    open_at DATETIME(6) NOT NULL,
    sale_quantity INT NOT NULL,
    CONSTRAINT fk_flash_sale_events_product FOREIGN KEY (product_id) REFERENCES products (id)
);
