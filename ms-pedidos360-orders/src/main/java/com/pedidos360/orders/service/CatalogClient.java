package com.pedidos360.orders.service;

import com.pedidos360.common.Actor;
import com.pedidos360.orders.model.CustomerOrder;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class CatalogClient {
  private final RestClient client;
  private final String serviceKey;

  public record Product(Long id, Long storeId, BigDecimal price, Boolean active) {}

  public CatalogClient(
      RestClient.Builder builder,
      @Value("${app.catalog-url}") String url,
      @Value("${INTERNAL_SERVICE_KEY}") String key) {
    var f = new SimpleClientHttpRequestFactory();
    f.setConnectTimeout(3000);
    f.setReadTimeout(10000);
    client = builder.baseUrl(url).requestFactory(f).build();
    serviceKey = key;
  }

  public Product product(Long id) {
    return client
        .get()
        .uri("/api/catalog/products/{id}", id)
        .header("Authorization", Actor.bearer())
        .retrieve()
        .body(Product.class);
  }

  public void reserve(CustomerOrder o, String reservationId) {
    client
        .put()
        .uri("/api/catalog/reservations/{id}", reservationId)
        .header("X-Service-Key", serviceKey)
        .body(
            Map.of(
                "storeId",
                o.getStoreId(),
                "items",
                o.getItems().stream()
                    .map(i -> Map.of("productId", i.getProductId(), "quantity", i.getQuantity()))
                    .toList()))
        .retrieve()
        .toBodilessEntity();
  }

  public void release(String reservationId) {
    client
        .delete()
        .uri("/api/catalog/reservations/{id}", reservationId)
        .header("X-Service-Key", serviceKey)
        .retrieve()
        .toBodilessEntity();
  }
}
