package com.pedidos360.audit;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "P360_TIMELINE")
public class AuditRecord {
  @Id @Column(length = 36) public String id;
  @Version public Long lockVersion;
  @Column(nullable = false) public Long orderId;
  @Column(nullable = false) public long eventVersion;
  @Column(nullable = false) public Long storeId;
  @Column(nullable = false, length = 250) public String actorId;
  @Column(nullable = false, length = 40) public String action;
  @Column(nullable = false, length = 40) public String type;
  @Column(length = 30) public String previousStatus;
  @Column(nullable = false, length = 30) public String status;
  @Column(nullable = false) public Instant occurredAt;
  @Column(nullable = false, length = 128) public String correlationId;
  @Column(nullable = false, length = 80) public String source;
  @Lob @Column(nullable = false) public String payload;
  @Column(nullable = false) public Instant nextAttempt;
  @Column(nullable = false) public int attempts;
  public Instant publishedAt;
  public Instant projectedAt;
  @Column(length = 100) public String lastError;
}

