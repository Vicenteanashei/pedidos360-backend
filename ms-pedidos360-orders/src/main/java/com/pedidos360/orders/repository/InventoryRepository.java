package com.pedidos360.orders.repository;

import com.pedidos360.orders.model.InventoryOperation;
import java.util.*;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InventoryRepository extends JpaRepository<InventoryOperation, String> {
  List<InventoryOperation> findByCompletedFalse(Pageable page);

  List<InventoryOperation> findByOrderId(Long orderId);
}
