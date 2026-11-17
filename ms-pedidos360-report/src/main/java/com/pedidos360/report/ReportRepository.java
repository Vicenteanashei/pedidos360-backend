package com.pedidos360.report;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface ReportRepository extends JpaRepository<ReportOrder, Long> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select o from ReportOrder o where o.id=:id")
  Optional<ReportOrder> lock(Long id);
  interface Totals {
    Long getOrders();
    java.math.BigDecimal getSales();
    Double getLead();
  }
  interface Bucket {
    String getLabel();
    java.math.BigDecimal getValue();
  }
  interface ProductTotal {
    Long getProductId();
    Long getQuantity();
  }
  @Query("select count(o) as orders, coalesce(sum(o.total),0) as sales, coalesce(avg(o.leadTimeMs),0) as lead from ReportOrder o where o.deleted=false and o.status='ENTREGADO' and o.deliveredAt>=:since")
  Totals totals(Instant since);
  @Query("select o.salesDay as label, sum(o.total) as value from ReportOrder o where o.deleted=false and o.status='ENTREGADO' and o.deliveredAt>=:since group by o.salesDay order by o.salesDay")
  List<Bucket> daily(Instant since);
  @Query("select o.salesHour as label, sum(o.total) as value from ReportOrder o where o.deleted=false and o.status='ENTREGADO' and o.deliveredAt>=:since group by o.salesHour order by o.salesHour")
  List<Bucket> hourly(Instant since);
  @Query("select l.productId as productId, sum(l.quantity) as quantity from ReportOrder o join o.items l where o.deleted=false and o.status='ENTREGADO' and o.deliveredAt>=:since group by l.productId order by sum(l.quantity) desc")
  List<ProductTotal> products(Instant since, org.springframework.data.domain.Pageable limit);
  @Query("select o.status, count(o) from ReportOrder o where o.deleted=false group by o.status")
  List<Object[]> states();
  @Query("select max(o.eventAt) from ReportOrder o")
  Instant latest();
}

