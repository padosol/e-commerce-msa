package com.flashsale.inventory.stock;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.validation.BindValidationException;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;
import org.springframework.context.annotation.Configuration;

class OptimisticPropertiesTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
        .withUserConfiguration(EnableOptimisticProperties.class);

    @Test
    void maxRetriesDefaultsToZero() {
        runner.run(ctx -> assertThat(ctx.getBean(OptimisticProperties.class).maxRetries()).isZero());
    }

    @Test
    void maxRetriesThreeIsAccepted() {
        runner.withPropertyValues("inventory.optimistic.max-retries=3")
            .run(ctx -> assertThat(ctx.getBean(OptimisticProperties.class).maxRetries()).isEqualTo(3));
    }

    @Test
    void maxRetriesAboveThreeFailsStartup() {
        runner.withPropertyValues("inventory.optimistic.max-retries=4")
            .run(ctx -> assertThat(ctx).hasFailed()
                .getFailure().rootCause().isInstanceOf(BindValidationException.class));
    }

    @Test
    void negativeMaxRetriesFailsStartup() {
        runner.withPropertyValues("inventory.optimistic.max-retries=-1")
            .run(ctx -> assertThat(ctx).hasFailed()
                .getFailure().rootCause().isInstanceOf(BindValidationException.class));
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(OptimisticProperties.class)
    static class EnableOptimisticProperties {
    }
}
