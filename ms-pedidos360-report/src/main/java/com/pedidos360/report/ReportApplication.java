package com.pedidos360.report;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
@SpringBootApplication(scanBasePackages = {"com.pedidos360.report", "com.pedidos360.common", "com.pedidos360.stream"})
public class ReportApplication {
  public static void main(String[] args) { SpringApplication.run(ReportApplication.class, args); }
}

