package com.pedidos360.orders.repository;
import com.pedidos360.orders.model.StreamOutbox;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.*;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;

public interface StreamOutboxRepository extends JpaRepository<StreamOutbox, String> {
  long countByPublishedAtIsNull();
  @Query("""
      select e from StreamOutbox e where e.publishedAt is null and e.nextAttempt <= :now
      and not exists (select p from StreamOutbox p where p.orderId=e.orderId
          and p.eventVersion<e.eventVersion and p.publishedAt is null)
      order by e.createdAt, e.id
      """)
  List<StreamOutbox> ready(Instant now, Pageable page);
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select e from StreamOutbox e where e.id=:id")
  Optional<StreamOutbox> lock(String id);
}

