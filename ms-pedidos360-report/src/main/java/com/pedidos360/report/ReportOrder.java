package com.pedidos360.report;
import com.pedidos360.stream.BusinessEvent;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

@Entity
@Table(name = "P360_REPORT_ORDERS")
public class ReportOrder {
  @Id public Long id;
  @Version public Long lockVersion;
  @Column(nullable = false) public long eventVersion;
  @Column(nullable = false) public Long storeId;
  @Column(nullable = false, length = 30) public String status;
  @Column(nullable = false) public boolean deleted;
  @Column(nullable = false, precision = 12, scale = 2) public BigDecimal total;
  @Column(nullable = false) public Instant createdAt;
  public Instant deliveredAt;
  @Column(nullable = false) public Instant eventAt;
  @Column(length = 10) public String salesDay;
  @Column(length = 32) public String salesHour;
  public Long leadTimeMs;
  @ElementCollection
  @CollectionTable(name = "P360_REPORT_LINES", joinColumns = @JoinColumn(name = "order_id"))
  public List<Line> items = new ArrayList<>();

  @Embeddable
  public static class Line {
    @Column(nullable = false) public Long productId;
    @Column(nullable = false) public int quantity;
    public Line() {}
    Line(BusinessEvent.Line line) { productId = line.productId(); quantity = line.quantity(); }
  }

  void apply(BusinessEvent e, java.time.ZoneId zone) {
    var p = e.payload();
    id = p.id(); eventVersion = p.version(); storeId = p.storeId(); status = p.status();
    deleted = p.deleted(); total = p.total(); createdAt = p.createdAt();
    deliveredAt = p.deliveredAt(); eventAt = e.timestamp();
    var local = deliveredAt == null ? null : deliveredAt.atZone(zone);
    salesDay = local == null ? null : local.toLocalDate().toString();
    salesHour = local == null ? null : local.format(java.time.format.DateTimeFormatter.ofPattern("uuuu-MM-dd HH:00 XXX"));
    leadTimeMs = deliveredAt == null ? null : java.time.Duration.between(createdAt, deliveredAt).toMillis();
    items.clear(); p.items().forEach(i -> items.add(new Line(i)));
  }
}

