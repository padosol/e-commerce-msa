CREATE DATABASE user_db;
CREATE USER 'user_svc'@'%' IDENTIFIED BY 'user_pw';
GRANT ALL PRIVILEGES ON user_db.* TO 'user_svc'@'%';

CREATE DATABASE product_db;
CREATE USER 'product_svc'@'%' IDENTIFIED BY 'product_pw';
GRANT ALL PRIVILEGES ON product_db.* TO 'product_svc'@'%';

CREATE DATABASE inventory_db;
CREATE USER 'inventory_svc'@'%' IDENTIFIED BY 'inventory_pw';
GRANT ALL PRIVILEGES ON inventory_db.* TO 'inventory_svc'@'%';

CREATE DATABASE order_db;
CREATE USER 'order_svc'@'%' IDENTIFIED BY 'order_pw';
GRANT ALL PRIVILEGES ON order_db.* TO 'order_svc'@'%';

CREATE DATABASE payment_db;
CREATE USER 'payment_svc'@'%' IDENTIFIED BY 'payment_pw';
GRANT ALL PRIVILEGES ON payment_db.* TO 'payment_svc'@'%';
