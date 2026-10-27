package com.pedidos360.orders.repository;

import com.pedidos360.orders.model.OperatorAssignment;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OperatorRepository extends JpaRepository<OperatorAssignment, String> {}
