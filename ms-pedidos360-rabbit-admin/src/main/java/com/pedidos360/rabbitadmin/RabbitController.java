package com.pedidos360.rabbitadmin;

import static com.pedidos360.rabbitadmin.dto.RabbitInputs.*;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@Validated
@RequestMapping("/api/rabbit")
@PreAuthorize("hasRole('Admin')")
public class RabbitController {
  private final RabbitAdminService service;
  public RabbitController(RabbitAdminService service) { this.service = service; }

  @GetMapping("/overview")
  public Map<?, ?> overview() { return service.overview(); }
  @GetMapping("/nodes")
  public List<?> nodes() { return service.nodes(); }
  @GetMapping("/queues")
  public List<?> queues() { return service.queues(); }
  @GetMapping("/exchanges")
  public List<?> exchanges() { return service.exchanges(); }
  @GetMapping("/bindings")
  public List<?> bindings() { return service.bindings(); }

  @PostMapping("/queues")
  @ResponseStatus(HttpStatus.CREATED)
  public void createQueue(@Valid @RequestBody QueueInput input) { service.createQueue(input); }
  @DeleteMapping("/queues/{name}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deleteQueue(@PathVariable @Pattern(regexp = DEMO_PATTERN) String name) {
    service.deleteQueue(name);
  }
  @PostMapping("/exchanges")
  @ResponseStatus(HttpStatus.CREATED)
  public void createExchange(@Valid @RequestBody ExchangeInput input) { service.createExchange(input); }
  @DeleteMapping("/exchanges/{name}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deleteExchange(@PathVariable @Pattern(regexp = DEMO_PATTERN) String name) {
    service.deleteExchange(name);
  }
  @PostMapping("/bindings")
  @ResponseStatus(HttpStatus.CREATED)
  public void bind(@Valid @RequestBody BindingInput input) { service.bind(input); }
  @DeleteMapping("/bindings")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void unbind(
      @RequestParam @Pattern(regexp = DEMO_PATTERN) String exchange,
      @RequestParam @Pattern(regexp = DEMO_PATTERN) String queue,
      @RequestParam @NotBlank @Pattern(regexp = PROPERTIES_KEY_PATTERN) String propertiesKey) {
    service.unbind(exchange, queue, propertiesKey);
  }
  @PostMapping("/queues/{name}/inspect")
  public List<?> inspect(@PathVariable String name) { return service.inspect(name); }
}
