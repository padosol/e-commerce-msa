package com.flashsale.order.application;

import com.flashsale.order.domain.FailReason;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
import org.springframework.stereotype.Component;

/** In-memory per-event counts of failures that happen before an order row exists. */
@Component
public class PreOrderFailures {

    private final Map<Long, Map<FailReason, LongAdder>> counts = new ConcurrentHashMap<>();

    public void record(long eventId, FailReason reason) {
        counts.computeIfAbsent(eventId, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(reason, k -> new LongAdder())
                .increment();
    }

    public Map<FailReason, Long> snapshot(long eventId) {
        Map<FailReason, Long> result = new EnumMap<>(FailReason.class);
        Map<FailReason, LongAdder> byReason = counts.get(eventId);
        if (byReason != null) {
            byReason.forEach((reason, adder) -> {
                long n = adder.sum();
                if (n > 0) {
                    result.put(reason, n);
                }
            });
        }
        return result;
    }

    public void reset() {
        counts.clear();
    }
}
