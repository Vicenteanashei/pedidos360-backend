package com.pedidos360.orders.service;

import com.pedidos360.orders.dto.OrderRequest;
import com.pedidos360.orders.dto.OrderResponse;
import com.pedidos360.orders.exception.InvalidStatusTransitionException;
import com.pedidos360.orders.exception.OrderNotFoundException;
import com.pedidos360.orders.model.CustomerOrder;
import com.pedidos360.orders.model.OrderItem;
import com.pedidos360.orders.model.OrderStatus;
import com.pedidos360.orders.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OrderService {

	private final OrderRepository repository;

	@Transactional
	public OrderResponse create(OrderRequest request) {
		CustomerOrder order = new CustomerOrder();
		order.setStatus(OrderStatus.CREADO);
		apply(order, request);
		// TODO Kafka: publicar OrderCreated en orders.events
		// TODO RabbitMQ: comando email.send (confirmacion al cliente)
		return OrderResponse.from(repository.save(order));
	}

	@Transactional(readOnly = true)
	public OrderResponse findById(Long id) {
		return OrderResponse.from(get(id));
	}

	@Transactional(readOnly = true)
	public List<OrderResponse> search(OrderStatus status, LocalDateTime from, LocalDateTime to) {
		return repository.findAll(OrderRepository.filter(status, from, to), Sort.by(Sort.Direction.DESC, "createdAt"))
				.stream().map(OrderResponse::from).toList();
	}

	@Transactional
	public OrderResponse update(Long id, OrderRequest request) {
		CustomerOrder order = get(id);
		if (order.getStatus() != OrderStatus.CREADO) {
			throw new InvalidStatusTransitionException(
					"Solo se puede editar un pedido en estado CREADO (estado actual: " + order.getStatus() + ")");
		}
		apply(order, request);
		return OrderResponse.from(repository.save(order));
	}

	@Transactional
	public OrderResponse changeStatus(Long id, OrderStatus newStatus) {
		CustomerOrder order = get(id);
		OrderStatus current = order.getStatus();

		if (!current.canTransitionTo(newStatus)) {
			throw new InvalidStatusTransitionException(
					"No se puede pasar de " + current + " a " + newStatus
							+ ". Permitidos: " + current.allowedTransitions());
		}

		order.setStatus(newStatus);
		LocalDateTime now = LocalDateTime.now();
		switch (newStatus) {
			case ACEPTADO -> {
				order.setAcceptedAt(now);
				// TODO catalog: descontar stock (regla: el stock decrece al aceptar)
				// TODO RabbitMQ: kitchen.ticket (ticket a cocina)
			}
			case DESPACHADO -> order.setDispatchedAt(now);
			case ENTREGADO -> order.setDeliveredAt(now);
			default -> { }
		}
		// TODO Kafka: publicar OrderAccepted/OrderPreparing/... en orders.events
		// TODO RabbitMQ: email.send con el nuevo estado
		return OrderResponse.from(repository.save(order));
	}

	@Transactional
	public void delete(Long id) {
		repository.delete(get(id));
	}

	private CustomerOrder get(Long id) {
		return repository.findById(id).orElseThrow(() -> new OrderNotFoundException(id));
	}

	private void apply(CustomerOrder order, OrderRequest request) {
		order.setStoreId(request.storeId());
		order.setCustomerName(request.customerName());
		order.setCustomerEmail(request.customerEmail());
		order.setDeliveryAddress(request.deliveryAddress());
		order.setNotes(request.notes());
		order.replaceItems(request.items().stream().map(i -> {
			OrderItem item = new OrderItem();
			item.setProductId(i.productId());
			item.setQuantity(i.quantity());
			item.setUnitPrice(i.unitPrice());
			return item;
		}).toList());
	}
}
