package com.pedidos360.notify;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ReceiptRepository extends JpaRepository<Receipt, String> {
  java.util.List<Receipt> findByOwnerIdOrderByProcessedAtDesc(
      String ownerId, org.springframework.data.domain.Pageable page);
}
