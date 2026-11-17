package com.pedidos360.orders.model;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "P360_STREAM_OUTBOX", uniqueConstraints =
    @UniqueConstraint(columnNames = {"orderId", "eventVersion"}))
public class StreamOutbox {
  @Id @Column(length = 36) public String id;
  @Column(nullable = false) public Long orderId;
  @Column(nullable = false) public long eventVersion;
  @Lob @Column(nullable = false) public String payload;
  @Column(nullable = false) public Instant createdAt;
  @Column(nullable = false) public Instant nextAttempt;
  @Column(nullable = false) public int attempts;
  public Instant publishedAt;
  @Column(length = 100) public String lastError;
}

