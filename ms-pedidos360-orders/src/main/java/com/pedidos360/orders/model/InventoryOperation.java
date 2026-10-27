package com.pedidos360.orders.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "P360_INVENTORY_OPS")
public class InventoryOperation {
  @Id
  @Column(length = 80)
  public String id;

  @Column(nullable = false)
  public Long orderId;

  @Column(nullable = false, length = 20)
  public String action;

  @Column(nullable = false)
  public Boolean completed = false;

  @Column(nullable = false)
  public Instant updatedAt;

  @Column(length = 200)
  public String lastError;

  public InventoryOperation() {}
}
