package com.pedidos360.rabbitadmin.dto;

import jakarta.validation.constraints.*;

public final class RabbitInputs {
  private RabbitInputs() {}
  public static final String DEMO_PATTERN = "demo\\.[a-zA-Z0-9_.-]{1,80}";
  public static final String PROPERTIES_KEY_PATTERN = "[a-zA-Z0-9._~:#*%+=-]{1,512}";

  public record QueueInput(@NotBlank @Pattern(regexp = DEMO_PATTERN) String name) {}
  public record ExchangeInput(
      @NotBlank @Pattern(regexp = DEMO_PATTERN) String name,
      @NotBlank @Pattern(regexp = "direct|topic|fanout") String type) {}
  public record BindingInput(
      @NotBlank @Pattern(regexp = DEMO_PATTERN) String exchange,
      @NotBlank @Pattern(regexp = DEMO_PATTERN) String queue,
      @NotNull @Size(max = 150) @Pattern(regexp = "[a-zA-Z0-9._#*:-]*") String routingKey) {}
}
