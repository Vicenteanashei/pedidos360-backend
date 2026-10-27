package com.pedidos360.notify.config;

import jakarta.validation.constraints.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("app.messaging.consumer")
public record ConsumerPolicy(
    @DefaultValue("3") @Min(1) @Max(10) int maxAttempts,
    @DefaultValue("1000") @Min(0) @Max(60000) long initialDelay,
    @DefaultValue("4000") @Min(0) @Max(60000) long maxDelay) {
  public ConsumerPolicy {
    if (maxDelay < initialDelay) throw new IllegalArgumentException("max-delay must be >= initial-delay");
  }
}
