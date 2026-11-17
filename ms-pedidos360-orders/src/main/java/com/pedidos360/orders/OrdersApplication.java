package com.pedidos360.orders;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"com.pedidos360.orders", "com.pedidos360.common", "com.pedidos360.stream"})
public class OrdersApplication {

  public static void main(String[] args) {
    SpringApplication.run(OrdersApplication.class, args);
  }
}
