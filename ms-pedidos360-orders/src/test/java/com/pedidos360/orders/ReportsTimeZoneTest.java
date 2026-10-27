package com.pedidos360.orders;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.pedidos360.orders.controller.ReportsController;
import com.pedidos360.orders.model.CustomerOrder;
import com.pedidos360.orders.repository.OrderRepository;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.domain.Specification;

class ReportsTimeZoneTest {
  @Test
  void groupsUtcInstantsInChileAcrossDayAndDstBoundaries() {
    var orders = mock(OrderRepository.class);
    var first = delivered("2026-10-09T01:00:00", "5000"); // Oct 8 at 22:00 Chile.
    var second = delivered("2026-10-09T04:00:00", "2500"); // Oct 9 at 01:00 Chile.
    var winter = delivered("2026-06-02T03:00:00", "1000"); // June 1 at 23:00 Chile (UTC-4).
    when(orders.findAll(org.mockito.ArgumentMatchers.<Specification<CustomerOrder>>any()))
        .thenReturn(List.of(first, second, winter));
    var report = new ReportsController(orders, "America/Santiago").report("all");
    assertEquals("America/Santiago", report.get("timeZone"));
    assertEquals(new BigDecimal("8500"), report.get("sales"));
    assertEquals(3, report.get("deliveredOrders"));
    assertEquals(
        List.of(
            Map.of("label", "2026-06-01", "value", new BigDecimal("1000")),
            Map.of("label", "2026-10-08", "value", new BigDecimal("5000")),
            Map.of("label", "2026-10-09", "value", new BigDecimal("2500"))),
        report.get("dailySales"));
    assertEquals(60000.0, report.get("averageLeadTimeMs"));
  }

  @Test
  void hourlySalesSumSameHourAndSeparateRepeatedDstHour() {
    var orders = mock(OrderRepository.class);
    when(orders.findAll(org.mockito.ArgumentMatchers.<Specification<CustomerOrder>>any()))
        .thenReturn(
            List.of(
                delivered("2026-10-09T01:00:00", "5000"),
                delivered("2026-10-09T01:59:00", "2500"),
                delivered("2026-04-05T02:30:00", "1000"),
                delivered("2026-04-05T03:30:00", "2000")));
    var report = new ReportsController(orders, "America/Santiago").report("all");
    assertEquals(new BigDecimal("10500"), report.get("sales"));
    assertEquals(
        List.of(
            Map.of("label", "2026-04-04 23:00 -03:00", "value", new BigDecimal("1000")),
            Map.of("label", "2026-04-04 23:00 -04:00", "value", new BigDecimal("2000")),
            Map.of("label", "2026-10-08 22:00 -03:00", "value", new BigDecimal("7500"))),
        report.get("hourlySales"));
  }

  @Test
  void emptyReportUsesZeroMetricsAndInvalidZoneFailsFast() {
    var orders = mock(OrderRepository.class);
    when(orders.findAll(org.mockito.ArgumentMatchers.<Specification<CustomerOrder>>any()))
        .thenReturn(List.of());
    var report = new ReportsController(orders, "America/Santiago").report("all");
    assertEquals(BigDecimal.ZERO, report.get("sales"));
    assertEquals(0.0, report.get("averageLeadTimeMs"));
    assertEquals(List.of(), report.get("hourlySales"));
    assertThrows(DateTimeException.class, () -> new ReportsController(orders, "zona-inexistente"));
  }

  private CustomerOrder delivered(String at, String total) {
    var order = new CustomerOrder();
    order.setDeliveredAt(LocalDateTime.parse(at));
    order.setCreatedAt(order.getDeliveredAt().minusMinutes(1));
    order.setTotal(new BigDecimal(total));
    return order;
  }
}
