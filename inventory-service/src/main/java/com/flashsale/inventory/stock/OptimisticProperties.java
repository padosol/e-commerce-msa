package com.flashsale.inventory.stock;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/** 낙관적 락 충돌 시 즉시 다시 시도하는 최대 횟수. 0~3 밖이면 기동에 실패한다. */
@ConfigurationProperties("inventory.optimistic")
@Validated
public record OptimisticProperties(@DefaultValue("0") @Min(0) @Max(3) int maxRetries) {
}
