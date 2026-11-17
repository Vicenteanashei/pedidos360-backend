package com.pedidos360.audit;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.*;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;

public interface AuditRepository extends JpaRepository<AuditRecord, String>, JpaSpecificationExecutor<AuditRecord> {
  List<AuditRecord> findByPublishedAtIsNullAndNextAttemptLessThanEqualOrderByOccurredAt(Instant now, Pageable page);
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select a from AuditRecord a where a.id=:id")
  Optional<AuditRecord> lock(String id);
}

