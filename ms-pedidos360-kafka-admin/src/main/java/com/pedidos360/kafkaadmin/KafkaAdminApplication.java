package com.pedidos360.kafkaadmin;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
@SpringBootApplication(scanBasePackages = {"com.pedidos360.kafkaadmin", "com.pedidos360.common", "com.pedidos360.stream"})
public class KafkaAdminApplication {
  public static void main(String[] args) { SpringApplication.run(KafkaAdminApplication.class, args); }
}

