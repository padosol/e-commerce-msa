package com.flashsale.order.application;

import com.flashsale.order.application.port.out.CartRepository;
import com.flashsale.order.application.port.out.CatalogProduct;
import com.flashsale.order.application.port.out.ProductPort;
import com.flashsale.order.domain.Cart;
import com.flashsale.order.domain.CartItem;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class CartService {

    private final CartRepository carts;
    private final ProductPort products;

    public CartService(CartRepository carts, ProductPort products) {
        this.carts = carts;
        this.products = products;
    }

    public CartView view(long userId) {
        Cart cart = carts.findByUserId(userId);
        if (cart.isEmpty()) {
            return new CartView(List.of(), 0);
        }
        return toView(cart, listed(cart));
    }

    /** The quantity is validated before the remote call; the cart is saved only if the product is sold. */
    public CartView put(long userId, long productId, int quantity) {
        Cart cart = carts.findByUserId(userId);
        cart.put(productId, quantity);
        Map<Long, CatalogProduct> listed = listed(cart);
        if (!listed.containsKey(productId)) {
            throw new ProductUnavailableException(productId);
        }
        carts.save(cart);
        return toView(cart, listed);
    }

    public void remove(long userId, long productId) {
        Cart cart = carts.findByUserId(userId);
        cart.remove(productId);
        carts.save(cart);
    }

    private Map<Long, CatalogProduct> listed(Cart cart) {
        Map<Long, CatalogProduct> byId = new HashMap<>();
        products.findListed(cart.items().stream().map(CartItem::productId).toList())
                .forEach(p -> byId.put(p.productId(), p));
        return byId;
    }

    private static CartView toView(Cart cart, Map<Long, CatalogProduct> listed) {
        long total = 0;
        List<CartView.Line> lines = new ArrayList<>();
        for (CartItem item : cart.items()) {
            CatalogProduct p = listed.get(item.productId());
            if (p == null) {
                lines.add(new CartView.Line(item.productId(), null, null, item.quantity(), null, false));
            } else {
                long subtotal = p.price() * item.quantity();
                total += subtotal;
                lines.add(new CartView.Line(item.productId(), p.name(), p.price(), item.quantity(), subtotal, true));
            }
        }
        return new CartView(lines, total);
    }
}
