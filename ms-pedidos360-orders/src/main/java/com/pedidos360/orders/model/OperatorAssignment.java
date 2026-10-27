package com.pedidos360.orders.model;

import jakarta.persistence.*;

@Entity
@Table(name = "P360_OPERATORS")
public class OperatorAssignment {
  @Id
  @Column(length = 250)
  public String actorId;

  @Column(nullable = false)
  public Long storeId;

  public OperatorAssignment() {}
}
