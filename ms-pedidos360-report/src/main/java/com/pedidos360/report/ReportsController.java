package com.pedidos360.report;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping({"/api/reports", "/api/report"})
@PreAuthorize("hasRole('Admin')")
public class ReportsController {
  private final ReportRepository orders;
  private final ZoneId zone;
  public ReportsController(ReportRepository orders, @Value("${REPORT_TIME_ZONE:America/Santiago}") String zone) {
    this.orders = orders; this.zone = ZoneId.of(zone);
  }
  @GetMapping({"", "/kpis"})
  @Transactional(readOnly = true)
  public Map<String, Object> report(@RequestParam(defaultValue = "last7d") String range) {
    int days = switch (range) {
      case "last24h" -> 1; case "last7d" -> 7; case "all" -> 0;
      default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Rango inválido");
    };
    Instant since = days == 0 ? Instant.EPOCH : Instant.now().minus(Duration.ofDays(days));
    var totals = orders.totals(since);
    var result = new LinkedHashMap<String, Object>();
    result.put("timeZone", zone.getId()); result.put("deliveredOrders", totals.getOrders());
    result.put("sales", totals.getSales()); result.put("averageLeadTimeMs", totals.getLead());
    var daily = orders.daily(since);
    result.put("dailySales", daily.stream()
        .map(e -> Map.of("label", e.getLabel(), "value", e.getValue())).toList());
    result.put("dailyLeadTime", daily.stream()
        .filter(e -> e.getLeadTimeMs() != null)
        .map(e -> Map.of("label", e.getLabel(), "value", e.getLeadTimeMs())).toList());
    result.put("hourlySales", orders.hourly(since).stream()
        .map(e -> Map.of("label", e.getLabel(), "value", e.getValue())).toList());
    result.put("topProducts", orders.products(since, PageRequest.of(0, 20)).stream()
        .map(e -> Map.of("label", "Producto #" + e.getProductId(), "value", e.getQuantity())).toList());
    var states = new TreeMap<String, Long>();
    orders.states().forEach(row -> states.put((String) row[0], (Long) row[1]));
    result.put("states", states); result.put("streaming", true); result.put("lastEventAt", orders.latest());
    return result;
  }
  @GetMapping("/top-products")
  @Transactional(readOnly = true)
  public Object top(@RequestParam(defaultValue = "last7d") String range) {
    return report(range).get("topProducts");
  }
}

