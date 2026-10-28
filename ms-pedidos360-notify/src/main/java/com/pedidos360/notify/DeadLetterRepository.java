package com.pedidos360.notify;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeadLetterRepository extends JpaRepository<DeadLetter, String> {
  List<DeadLetter> findAllByOrderByArchivedAtDesc(Pageable page);
}
