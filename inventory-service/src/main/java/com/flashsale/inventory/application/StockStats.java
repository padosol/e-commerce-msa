package com.flashsale.inventory.application;

import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import java.util.concurrent.atomic.LongAdder;
import org.springframework.stereotype.Component;

/**
 * 낙관적 락의 충돌·재시도·재시도 소진 횟수. 충돌 한 번은 재시도 또는 소진 중 하나로 끝나므로
 * {@code conflicts == retries + exhausted} 가 성립한다. 통계 API 와 Micrometer 카운터가 같은 값을 읽는다.
 */
@Component
public class StockStats implements MeterBinder {

    private final LongAdder conflicts = new LongAdder();
    private final LongAdder retries = new LongAdder();
    private final LongAdder exhausted = new LongAdder();

    public void conflict() {
        conflicts.increment();
    }

    public void retry() {
        retries.increment();
    }

    public void exhausted() {
        exhausted.increment();
    }

    public Snapshot snapshot() {
        return new Snapshot(conflicts.sum(), retries.sum(), exhausted.sum());
    }

    public void reset() {
        conflicts.reset();
        retries.reset();
        exhausted.reset();
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        FunctionCounter.builder("inventory.optimistic.conflicts", conflicts, LongAdder::sum)
            .description("낙관적 락 UPDATE 가 0행을 갱신한 횟수")
            .register(registry);
        FunctionCounter.builder("inventory.optimistic.retries", retries, LongAdder::sum)
            .description("충돌 뒤 즉시 다시 시도한 횟수")
            .register(registry);
        FunctionCounter.builder("inventory.optimistic.exhausted", exhausted, LongAdder::sum)
            .description("재시도를 다 쓰고도 충돌해 CONFLICT 로 끝난 횟수")
            .register(registry);
    }

    public record Snapshot(long conflicts, long retries, long exhausted) {
    }
}
