package com.pedidos360.orders.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "P360_AUDIT")
public class AuditEntry {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(nullable = false)
  public Long orderId;

  @Column(nullable = false, length = 250)
  public String actorId;

  @Column(nullable = false, length = 40)
  public String action;

  @Column(length = 30)
  public String previousStatus;

  @Column(nullable = false, length = 30)
  public String status;

  @Column(nullable = false)
  public Long storeId;

  @Column(nullable = false)
  public Instant occurredAt;

  @Column(length = 64)
  public String correlationId;

  public AuditEntry() {}
}
