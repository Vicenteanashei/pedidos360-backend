package com.pedidos360.notify;

import jakarta.persistence.*;
import java.time.Instant;

/** Mensaje que llegó a una DLQ: queda archivado con los datos de su muerte (cabecera x-death). */
@Entity
@Table(name = "P360_DEAD_LETTERS")
public class DeadLetter {
  @Id
  @Column(length = 64)
  public String id;

  @Column(nullable = false, length = 60)
  public String dlq;

  @Column(length = 60)
  public String originalQueue;

  @Column(length = 60)
  public String originalExchange;

  @Column(length = 150)
  public String originalRoutingKey;

  @Column(length = 40)
  public String reason;

  @Column(nullable = false)
  public Long deathCount;

  @Column(length = 36)
  public String eventId;

  @Column(length = 40)
  public String type;

  @Column(nullable = false)
  public Instant archivedAt;

  @Lob
  @Column(nullable = false)
  public String payload;

  public DeadLetter() {}
}
