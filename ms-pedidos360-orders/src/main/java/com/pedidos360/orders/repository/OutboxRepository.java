package com.pedidos360.orders.repository;

import com.pedidos360.orders.model.OutboxEntry;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.*;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;

public interface OutboxRepository extends JpaRepository<OutboxEntry, String> {
  List<OutboxEntry>
      findByPublishedAtIsNullAndAttemptsLessThanAndNextAttemptLessThanEqualOrderByCreatedAt(
          int max, Instant now, Pageable page);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select e from OutboxEntry e where e.id=:id")
  Optional<OutboxEntry> lock(String id);
}
