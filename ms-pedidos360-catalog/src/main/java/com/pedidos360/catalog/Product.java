package com.pedidos360.catalog;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "P360_PRODUCTS")
public class Product {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Version public Long version;

  @Column(nullable = false, length = 80)
  public String name;

  @Column(nullable = false, length = 80)
  public String category;

  @Column(nullable = false, precision = 12, scale = 2)
  public BigDecimal price;

  @Column(nullable = false)
  public Long storeId;

  @Column(nullable = false)
  public Integer stock;

  @Column(nullable = false)
  public Boolean active = true;

  public Product() {}
}
