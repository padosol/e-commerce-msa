package com.flashsale.product.domain;

public class Product {

    private final Long id;
    private final String name;
    private final long price;
    private final boolean listed;

    private Product(Long id, String name, long price, boolean listed) {
        this.id = id;
        this.name = name;
        this.price = price;
        this.listed = listed;
    }

    public static Product listed(String name, long price) {
        return new Product(null, name, price, true);
    }

    public static Product unlisted(String name, long price) {
        return new Product(null, name, price, false);
    }

    public static Product restore(long id, String name, long price, boolean listed) {
        return new Product(id, name, price, listed);
    }

    public Long id() {
        return id;
    }

    public String name() {
        return name;
    }

    public long price() {
        return price;
    }

    public boolean listed() {
        return listed;
    }
}
