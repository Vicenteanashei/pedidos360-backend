package com.pedidos360.bff.controller;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class OrdersProxyController {
  private final RestClient client;
  private final Map<String, String> services;

  public OrdersProxyController(
      RestClient c,
      @Value("${app.services.orders-url}") String orders,
      @Value("${app.services.catalog-url}") String catalog,
      @Value("${app.services.notify-url}") String notify,
      @Value("${app.services.rabbit-admin-url}") String admin) {
    client = c;
    services =
        Map.of(
            "orders",
            orders,
            "audit",
            orders,
            "reports",
            orders,
            "operators",
            orders,
            "outbox",
            orders,
            "inventory-operations",
            orders,
            "catalog",
            catalog,
            "notifications",
            notify,
            "rabbit",
            admin);
  }

  @RequestMapping(
      path = {
        "/api/orders",
        "/api/orders/**",
        "/api/catalog/products",
        "/api/catalog/products/**",
        "/api/audit",
        "/api/reports",
        "/api/operators",
        "/api/operators/**",
        "/api/outbox",
        "/api/outbox/**",
        "/api/inventory-operations",
        "/api/notifications",
        "/api/notifications/**",
        "/api/rabbit",
        "/api/rabbit/**"
      },
      method = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.DELETE})
  public ResponseEntity<byte[]> proxy(
      HttpServletRequest request, @RequestBody(required = false) byte[] body) {
    String path = request.getRequestURI();
    String name = path.split("/")[2];
    String base = services.get(name);
    if (base == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    // No service-key header is forwarded: browser cannot invoke internal stock endpoints.
    var spec =
        client
            .method(HttpMethod.valueOf(request.getMethod()))
            .uri(
                URI.create(
                    base
                        + path
                        + (request.getQueryString() == null ? "" : "?" + request.getQueryString())))
            .header("Authorization", request.getHeader("Authorization"));
    String correlation = org.slf4j.MDC.get("correlationId");
    if (correlation != null) spec.header("X-Correlation-Id", correlation);
    if (request.getContentType() != null)
      spec.contentType(MediaType.parseMediaType(request.getContentType()));
    if (body != null && body.length > 0) spec.body(body);
    return spec.exchange(
        (req, res) -> {
          var headers = new HttpHeaders();
          if (res.getHeaders().getContentType() != null)
            headers.setContentType(res.getHeaders().getContentType());
          if (res.getHeaders().getFirst("Content-Disposition") != null)
            headers.set("Content-Disposition", res.getHeaders().getFirst("Content-Disposition"));
          return ResponseEntity.status(res.getStatusCode())
              .headers(headers)
              .body(res.getBody().readAllBytes());
        });
  }
}
