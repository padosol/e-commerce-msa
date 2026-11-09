package com.flashsale.product.domain;

public class Product {

    private final Long id;
    private final String name;
    private final long price;

    private Product(Long id, String name, long price) {
        this.id = id;
        this.name = name;
        this.price = price;
    }

    public static Product create(String name, long price) {
        return new Product(null, name, price);
    }

    public static Product restore(long id, String name, long price) {
        return new Product(id, name, price);
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
}
