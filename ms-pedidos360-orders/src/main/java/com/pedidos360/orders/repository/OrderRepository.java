package com.pedidos360.orders.repository;

import com.pedidos360.orders.model.CustomerOrder;
import com.pedidos360.orders.model.OrderStatus;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public interface OrderRepository extends JpaRepository<CustomerOrder, Long>, JpaSpecificationExecutor<CustomerOrder> {

	/** Filtros opcionales: solo se agrega la condicion si el parametro viene. */
	static Specification<CustomerOrder> filter(OrderStatus status, LocalDateTime from, LocalDateTime to) {
		return (root, query, cb) -> {
			List<Predicate> predicates = new ArrayList<>();
			if (status != null) {
				predicates.add(cb.equal(root.get("status"), status));
			}
			if (from != null) {
				predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
			}
			if (to != null) {
				predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), to));
			}
			return cb.and(predicates.toArray(Predicate[]::new));
		};
	}
}
