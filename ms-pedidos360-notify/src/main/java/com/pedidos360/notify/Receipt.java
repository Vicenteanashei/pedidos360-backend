package com.pedidos360.notify;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "P360_RECEIPTS")
public class Receipt {
  @Id
  @Column(length = 36)
  public String eventId;

  @Column(nullable = false, length = 40)
  public String type;

  @Column(nullable = false, length = 250)
  public String ownerId;

  @Column(nullable = false)
  public Long orderId;

  @Column(nullable = false)
  public Long storeId;

  @Column(nullable = false)
  public Instant processedAt;

  @Column(nullable = false, length = 100)
  public String artifact;

  public Receipt() {}
}
