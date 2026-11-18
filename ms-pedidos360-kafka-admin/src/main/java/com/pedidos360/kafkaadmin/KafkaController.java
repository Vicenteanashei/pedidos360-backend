package com.pedidos360.kafkaadmin;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/kafka")
@PreAuthorize("hasRole('Admin')")
public class KafkaController {
  private final KafkaPlatformService service;
  public KafkaController(KafkaPlatformService service) { this.service = service; }
  public record TopicInput(
      @NotBlank @Pattern(regexp = "demo\\.[A-Za-z0-9][A-Za-z0-9._-]{0,90}") String name,
      @Min(1) @Max(48) int partitions,
      @Min(1) @Max(3) short replicationFactor,
      @NotNull @Min(3600000) @Max(7776000000L) Long retentionMs,
      @NotBlank @Pattern(regexp = "delete|compact|compact,delete") String cleanupPolicy) {}
  public record TopicChange(
      @Min(1) @Max(48) Integer partitions,
      @Min(3600000) @Max(7776000000L) Long retentionMs,
      @Pattern(regexp = "delete|compact|compact,delete") String cleanupPolicy) {}
  public record OffsetInput(@NotBlank String topic, @Min(0) int partition, @Min(0) long offset) {}
  @GetMapping("/cluster") public Map<String, Object> cluster() { return service.cluster(); }
  @GetMapping("/topics") public List<Map<String, Object>> topics() { return service.topics(); }
  @GetMapping("/topics/{topic}/config") public Map<String, String> config(@PathVariable String topic) {
    return service.configuration(topic);
  }
  @PostMapping("/topics") @ResponseStatus(HttpStatus.CREATED)
  public void create(@Valid @RequestBody TopicInput input) { service.create(input); }
  @PutMapping("/topics/{topic}/config") @ResponseStatus(HttpStatus.NO_CONTENT)
  public void update(@PathVariable String topic, @Valid @RequestBody TopicChange input) { service.update(topic, input); }
  @DeleteMapping("/topics/{topic}/config/{property}") @ResponseStatus(HttpStatus.NO_CONTENT)
  public void resetConfiguration(@PathVariable String topic, @PathVariable String property) {
    service.resetConfiguration(topic, property);
  }
  @DeleteMapping("/topics/{topic}") @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable String topic) { service.delete(topic); }
  @GetMapping("/groups") public List<String> groups() { return service.groups(); }
  @GetMapping("/groups/{group}") public Map<String, Object> group(@PathVariable String group) {
    return service.groupState(group);
  }
  @PostMapping("/groups/{group}/offsets") @ResponseStatus(HttpStatus.NO_CONTENT)
  public void reset(@PathVariable String group, @Valid @RequestBody OffsetInput input) { service.reset(group, input); }
}

