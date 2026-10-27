package com.pedidos360.rabbitadmin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"com.pedidos360.rabbitadmin", "com.pedidos360.common"})
public class RabbitadminApplication {
  public static void main(String[] args) {
    SpringApplication.run(RabbitadminApplication.class, args);
  }
}
