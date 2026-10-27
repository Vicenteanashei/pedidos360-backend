package com.pedidos360.orders.controller;

import com.pedidos360.orders.model.*;
import com.pedidos360.orders.repository.OrderRepository;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/reports")
@PreAuthorize("hasRole('Admin')")
public class ReportsController {
  private final OrderRepository orders;
  private final ZoneId reportZone;

  public ReportsController(
      OrderRepository r,
      @org.springframework.beans.factory.annotation.Value("${REPORT_TIME_ZONE:America/Santiago}")
          String timeZone) {
    orders = r;
    reportZone = ZoneId.of(timeZone);
  }

  @GetMapping
  @Transactional(readOnly = true)
  public Map<String, Object> report(@RequestParam(defaultValue = "last7d") String range) {
    int days =
        switch (range) {
          case "last24h" -> 1;
          case "last7d" -> 7;
          case "all" -> 0;
          default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Rango inválido");
        };
    var since = days == 0 ? null : LocalDateTime.now(Clock.systemUTC()).minusDays(days);
    var delivered =
        orders.findAll(
            (root, q, cb) -> {
              var p =
                  cb.and(
                      cb.isFalse(root.get("deleted")),
                      cb.equal(root.get("status"), OrderStatus.ENTREGADO));
              return since == null
                  ? p
                  : cb.and(p, cb.greaterThanOrEqualTo(root.get("deliveredAt"), since));
            });
    var sales = new TreeMap<String, BigDecimal>();
    var hourly = new TreeMap<String, BigDecimal>();
    var products = new HashMap<Long, Long>();
    double lead = 0;
    BigDecimal total = BigDecimal.ZERO;
    for (var o : delivered) {
      total = total.add(o.getTotal());
      var local = o.getDeliveredAt().atOffset(ZoneOffset.UTC).atZoneSameInstant(reportZone);
      sales.merge(local.toLocalDate().toString(), o.getTotal(), BigDecimal::add);
      // Offset distinguishes the repeated local hour when daylight saving time ends.
      hourly.merge(
          local.format(java.time.format.DateTimeFormatter.ofPattern("uuuu-MM-dd HH:00 XXX")),
          o.getTotal(),
          BigDecimal::add);
      lead += Duration.between(o.getCreatedAt(), o.getDeliveredAt()).toMillis();
      for (var i : o.getItems())
        products.merge(i.getProductId(), i.getQuantity().longValue(), Long::sum);
    }
    return Map.of(
        "timeZone",
        reportZone.getId(),
        "deliveredOrders",
        delivered.size(),
        "sales",
        total,
        "averageLeadTimeMs",
        delivered.isEmpty() ? 0 : lead / delivered.size(),
        "dailySales",
        sales.entrySet().stream()
            .map(e -> Map.of("label", e.getKey(), "value", e.getValue()))
            .toList(),
        "hourlySales",
        hourly.entrySet().stream()
            .map(e -> Map.of("label", e.getKey(), "value", e.getValue()))
            .toList(),
        "topProducts",
        products.entrySet().stream()
            .sorted(Map.Entry.<Long, Long>comparingByValue().reversed())
            .limit(20)
            .map(e -> Map.of("label", "Producto #" + e.getKey(), "value", e.getValue()))
            .toList());
  }
}
