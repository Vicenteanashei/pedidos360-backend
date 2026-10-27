package com.pedidos360.rabbitadmin;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class RabbitAdminConfiguration {
  @Bean("rabbitManagementClient")
  RestClient rabbitManagementClient(RestClient.Builder builder,
      @Value("${app.rabbit-management-url}") String url,
      @Value("${app.rabbit-management-user:${spring.rabbitmq.username}}") String user,
      @Value("${app.rabbit-management-password:${spring.rabbitmq.password}}") String password) {
    var factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(3000);
    factory.setReadTimeout(10000);
    return builder.baseUrl(url.replaceAll("/+$", "") + "/api")
        .requestFactory(factory).defaultHeaders(h -> h.setBasicAuth(user, password)).build();
  }
}
