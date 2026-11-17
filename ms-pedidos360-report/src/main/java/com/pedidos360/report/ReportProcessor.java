package com.pedidos360.report;
import com.pedidos360.stream.BusinessEvent;
import jakarta.validation.Validator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReportProcessor {
  private final ReportRepository orders;
  private final Validator validator;
  private final java.time.ZoneId zone;
  public ReportProcessor(ReportRepository orders, Validator validator,
      @org.springframework.beans.factory.annotation.Value("${REPORT_TIME_ZONE:America/Santiago}") String zone) {
    this.orders = orders; this.validator = validator; this.zone = java.time.ZoneId.of(zone);
  }
  @Transactional
  public void process(BusinessEvent event) {
    event.validate(validator);
    var existing = orders.lock(event.payload().id());
    if (existing.isPresent() && existing.get().eventVersion >= event.payload().version()) return;
    var order = existing.orElseGet(ReportOrder::new);
    order.apply(event, zone);
    orders.saveAndFlush(order);
  }
}

