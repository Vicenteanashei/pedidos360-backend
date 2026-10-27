package com.pedidos360.notify;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"com.pedidos360.notify", "com.pedidos360.common"})
public class NotifyApplication {
  public static void main(String[] args) {
    SpringApplication.run(NotifyApplication.class, args);
  }
}
