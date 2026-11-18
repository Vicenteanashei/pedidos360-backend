package com.pedidos360.kafkaadmin;
import java.util.*;
import java.util.concurrent.*;
import org.apache.kafka.clients.admin.*;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.config.ConfigResource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true")
public class KafkaPlatformService {
  private final Admin admin;
  public KafkaPlatformService(Admin admin) { this.admin = admin; }
  private <T> T await(org.apache.kafka.common.KafkaFuture<T> future) {
    try { return future.get(10, TimeUnit.SECONDS); }
    catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Operación Kafka interrumpida");
    } catch (Exception ex) {
      throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
          "Kafka no pudo completar la operación; revisa el estado del clúster");
    }
  }
  private void name(String name) {
    if (name == null || !name.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,99}") || name.contains(".."))
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nombre de tópico inválido");
  }
  private void demo(String name) {
    name(name);
    if (!name.startsWith("demo."))
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Operación destructiva limitada a tópicos demo.*");
  }
  private void group(String group) {
    if (group == null || !group.matches("(p360-|demo\\.)[A-Za-z0-9._-]{1,80}"))
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Grupo no administrable");
  }

  public Map<String, Object> cluster() {
    var result = admin.describeCluster();
    var nodes = await(result.nodes()).stream().map(n ->
        Map.of("id", n.id(), "host", n.host(), "port", n.port())).toList();
    var controller = await(result.controller());
    return Map.of("clusterId", await(result.clusterId()), "brokers", nodes,
        "controllerId", controller == null ? -1 : controller.id());
  }

  public List<Map<String, Object>> topics() {
    var names = await(admin.listTopics().names());
    if (names.isEmpty()) return List.of();
    var descriptions = await(admin.describeTopics(names).allTopicNames());
    return descriptions.values().stream().sorted(Comparator.comparing(TopicDescription::name))
        .map(d -> {
          var result = new LinkedHashMap<String, Object>();
          result.put("name", d.name()); result.put("partitions", d.partitions().size());
          result.put("replicationFactor", d.partitions().isEmpty() ? 0 : d.partitions().getFirst().replicas().size());
          result.put("inSyncReplicas", d.partitions().stream().map(p ->
              Map.of("partition", p.partition(), "isr", p.isr().size(),
                  "replicas", p.replicas().size(), "leader", p.leader() == null ? -1 : p.leader().id())).toList());
          result.put("protected", !d.name().startsWith("demo."));
          return (Map<String, Object>) result;
        }).toList();
  }

  public Map<String, String> configuration(String topic) {
    name(topic);
    var resource = new ConfigResource(ConfigResource.Type.TOPIC, topic);
    var config = await(admin.describeConfigs(List.of(resource)).all()).get(resource);
    var result = new TreeMap<String, String>();
    for (String key : List.of("cleanup.policy", "retention.ms", "retention.bytes",
        "min.insync.replicas", "max.message.bytes")) {
      var entry = config.get(key);
      if (entry != null && entry.value() != null) result.put(key, entry.value());
    }
    return result;
  }

  public void create(KafkaController.TopicInput input) {
    demo(input.name());
    if (await(admin.listTopics().names()).size() >= 100)
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Límite de tópicos alcanzado");
    int brokers = await(admin.describeCluster().nodes()).size();
    if (input.replicationFactor() > brokers)
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Réplicas superan brokers disponibles");
    var topic = new NewTopic(input.name(), input.partitions(), input.replicationFactor());
    topic.configs(Map.of("retention.ms", input.retentionMs().toString(),
        "cleanup.policy", input.cleanupPolicy(), "min.insync.replicas",
        input.replicationFactor() >= 2 ? "2" : "1"));
    await(admin.createTopics(List.of(topic)).all());
  }

  public void update(String topic, KafkaController.TopicChange change) {
    name(topic);
    boolean managed = Set.of("orders.events", "audit.timeline", "orders.events.report.DLT",
        "orders.events.audit.DLT", "audit.timeline.timeline.DLT").contains(topic);
    if (!managed && !topic.startsWith("demo."))
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Tópico no administrable");
    if (managed) {
      long minimum = topic.equals("audit.timeline") ? 1209600000L :
          topic.endsWith(".DLT") ? 604800000L : 259200000L;
      String expected = topic.equals("audit.timeline") ? "compact,delete" : "delete";
      if (change.partitions() != null && change.partitions() != 3 ||
          change.retentionMs() != null && change.retentionMs() < minimum ||
          change.cleanupPolicy() != null && !change.cleanupPolicy().equals(expected))
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cambio incompatible con la topología del caso");
    }
    var description = await(admin.describeTopics(List.of(topic)).allTopicNames()).get(topic);
    if (change.partitions() != null) {
      if (change.partitions() < description.partitions().size())
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Kafka no permite reducir particiones");
      if (change.partitions() > description.partitions().size())
        await(admin.createPartitions(Map.of(topic, NewPartitions.increaseTo(change.partitions()))).all());
    }
    var operations = new ArrayList<AlterConfigOp>();
    if (change.retentionMs() != null)
      operations.add(new AlterConfigOp(new ConfigEntry("retention.ms", change.retentionMs().toString()), AlterConfigOp.OpType.SET));
    if (change.cleanupPolicy() != null)
      operations.add(new AlterConfigOp(new ConfigEntry("cleanup.policy", change.cleanupPolicy()), AlterConfigOp.OpType.SET));
    if (!operations.isEmpty())
      await(admin.incrementalAlterConfigs(Map.of(new ConfigResource(ConfigResource.Type.TOPIC, topic), operations)).all());
  }

  public void delete(String topic) {
    demo(topic);
    await(admin.deleteTopics(List.of(topic)).all());
  }

  public void resetConfiguration(String topic, String property) {
    demo(topic);
    if (!Set.of("retention.ms", "cleanup.policy").contains(property))
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Configuración no modificable");
    var resource = new ConfigResource(ConfigResource.Type.TOPIC, topic);
    var operation = new AlterConfigOp(new ConfigEntry(property, null), AlterConfigOp.OpType.DELETE);
    await(admin.incrementalAlterConfigs(Map.of(resource, List.of(operation))).all());
  }

  public List<String> groups() {
    return await(admin.listConsumerGroups().all()).stream().map(ConsumerGroupListing::groupId)
        .filter(id -> id.startsWith("p360-") || id.startsWith("demo.")).sorted().toList();
  }

  public Map<String, Object> groupState(String group) {
    group(group);
    var description = await(admin.describeConsumerGroups(List.of(group)).all()).get(group);
    var offsets = await(admin.listConsumerGroupOffsets(group).partitionsToOffsetAndMetadata());
    Map<TopicPartition, OffsetSpec> ends = new HashMap<>();
    offsets.keySet().forEach(p -> ends.put(p, OffsetSpec.latest()));
    var latest = ends.isEmpty() ? Map.<TopicPartition, ListOffsetsResult.ListOffsetsResultInfo>of() :
        await(admin.listOffsets(ends).all());
    var positions = offsets.entrySet().stream().map(entry -> {
      long committed = entry.getValue() == null ? 0 : entry.getValue().offset();
      long end = latest.get(entry.getKey()).offset();
      return Map.of("topic", entry.getKey().topic(), "partition", entry.getKey().partition(),
          "offset", committed, "endOffset", end, "lag", Math.max(0L, end - committed));
    }).toList();
    return Map.of("groupId", group, "state", description.state().toString(),
        "members", description.members().size(), "positions", positions);
  }

  public void reset(String group, KafkaController.OffsetInput input) {
    group(group); name(input.topic());
    if (!Set.of("orders.events", "audit.timeline").contains(input.topic()) && !input.topic().startsWith("demo."))
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Tópico no administrable");
    var description = await(admin.describeConsumerGroups(List.of(group)).all()).get(group);
    if (!description.members().isEmpty())
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Detén el grupo antes de cambiar offsets");
    var topic = await(admin.describeTopics(List.of(input.topic())).allTopicNames()).get(input.topic());
    if (input.partition() >= topic.partitions().size())
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Partición inexistente");
    var partition = new TopicPartition(input.topic(), input.partition());
    long earliest = await(admin.listOffsets(Map.of(partition, OffsetSpec.earliest())).all()).get(partition).offset();
    long end = await(admin.listOffsets(Map.of(partition, OffsetSpec.latest())).all()).get(partition).offset();
    if (input.offset() < earliest || input.offset() > end)
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Offset fuera del historial retenido");
    await(admin.alterConsumerGroupOffsets(group, Map.of(partition, new OffsetAndMetadata(input.offset()))).all());
  }
}

