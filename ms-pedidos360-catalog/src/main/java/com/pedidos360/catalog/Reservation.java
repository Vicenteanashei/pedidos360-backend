package com.pedidos360.catalog;

import jakarta.persistence.*;

@Entity
@Table(name = "P360_RESERVATIONS")
public class Reservation {
  @Id
  @Column(length = 80)
  public String id;

  @Column(nullable = false, length = 64)
  public String digest;

  @Lob
  @Column(nullable = false)
  public String payload;

  @Column(nullable = false)
  public Boolean released = false;

  public Reservation() {}
}
