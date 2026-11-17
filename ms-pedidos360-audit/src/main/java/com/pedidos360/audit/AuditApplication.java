package com.pedidos360.audit;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
@SpringBootApplication(scanBasePackages = {"com.pedidos360.audit", "com.pedidos360.common", "com.pedidos360.stream"})
public class AuditApplication {
  public static void main(String[] args) { SpringApplication.run(AuditApplication.class, args); }
}

