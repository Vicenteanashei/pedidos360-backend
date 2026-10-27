package com.pedidos360.orders.repository;

import com.pedidos360.orders.model.AuditEntry;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditRepository extends JpaRepository<AuditEntry, Long> {}
