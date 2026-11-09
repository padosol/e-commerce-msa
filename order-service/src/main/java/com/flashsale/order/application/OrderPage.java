package com.flashsale.order.application;

import com.flashsale.order.domain.Order;
import java.util.List;

public record OrderPage(List<Order> orders, int page, int size, long total) {
}
