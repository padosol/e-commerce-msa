package com.flashsale.order.adapter.out.persistence;

import com.flashsale.order.application.port.out.CartRepository;
import com.flashsale.order.domain.Cart;
import com.flashsale.order.domain.CartItem;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class CartPersistenceAdapter implements CartRepository {

    private final JdbcTemplate jdbc;

    CartPersistenceAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Cart findByUserId(long userId) {
        List<CartItem> items = jdbc.query(
                "SELECT product_id, quantity FROM cart_items WHERE user_id = ? ORDER BY product_id",
                (rs, i) -> new CartItem(rs.getLong("product_id"), rs.getInt("quantity")), userId);
        return Cart.restore(userId, items);
    }

    @Override
    @Transactional
    public void save(Cart cart) {
        jdbc.update("DELETE FROM cart_items WHERE user_id = ?", cart.userId());
        jdbc.batchUpdate("INSERT INTO cart_items (user_id, product_id, quantity) VALUES (?, ?, ?)",
                cart.items(), 100, (ps, item) -> {
                    ps.setLong(1, cart.userId());
                    ps.setLong(2, item.productId());
                    ps.setInt(3, item.quantity());
                });
    }
}
