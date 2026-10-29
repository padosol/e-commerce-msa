package com.flashsale.order.application;

import com.flashsale.order.domain.FailReason;
import java.util.Map;

public record OrderStats(long eventId, long paid, long failed, long pending, Map<FailReason, Long> failedByReason) {
}
