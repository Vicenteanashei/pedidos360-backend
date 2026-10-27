package com.pedidos360.orders.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "P360_OUTBOX")
public class OutboxEntry {
  @Id
  @Column(length = 36)
  public String id;

  @Column(nullable = false, length = 40)
  public String routingKey;

  @Lob
  @Column(nullable = false)
  public String payload;

  @Column(nullable = false)
  public Integer attempts = 0;

  @Column(nullable = false)
  public Instant createdAt;

  @Column(nullable = false)
  public Instant nextAttempt;

  public Instant publishedAt;

  @Column(length = 200)
  public String lastError;

  public OutboxEntry() {}
}
