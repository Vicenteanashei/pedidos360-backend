package com.pedidos360.catalog;

import java.math.BigDecimal;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.*;

@Configuration
@Profile("demo")
public class DemoCatalog {
  @Bean
  ApplicationRunner seed(ProductRepository repo) {
    return args -> {
      if (repo.count() > 0) return;
      for (long local = 1; local <= 20; local++) {
        Product p = new Product();
        p.name = "Marraqueta (kg)";
        p.category = "Panadería";
        p.price = new BigDecimal("2200");
        p.stock = 40;
        p.storeId = local;
        repo.save(p);
        Product c = new Product();
        c.name = "Café americano";
        c.category = "Cafetería";
        c.price = new BigDecimal("1800");
        c.stock = 100;
        c.storeId = local;
        repo.save(c);
      }
    };
  }
}
