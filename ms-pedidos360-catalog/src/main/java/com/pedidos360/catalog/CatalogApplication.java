package com.pedidos360.catalog;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"com.pedidos360.catalog", "com.pedidos360.common"})
public class CatalogApplication {
  public static void main(String[] args) {
    SpringApplication.run(CatalogApplication.class, args);
  }
}
