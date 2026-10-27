package com.pedidos360.orders.service;

import com.pedidos360.common.Actor;
import com.pedidos360.orders.model.CustomerOrder;
import com.pedidos360.orders.repository.OperatorRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

@Component
public class OrderAccess {
  private final OperatorRepository operators;

  public OrderAccess(OperatorRepository o) {
    operators = o;
  }

  public void store(Long storeId) {
    if (Actor.has("Admin")) return;
    if (Actor.has("Operador")) {
      var a = operators.findById(Actor.id());
      if (a.isPresent() && a.get().storeId.equals(storeId)) return;
      throw new AccessDeniedException("Operador no asignado a este local");
    }
    Actor.roles("Cliente");
  }

  public boolean visible(CustomerOrder o) {
    if (o.getDeleted()) return false;
    if (Actor.has("Admin")) return true;
    if (Actor.has("Operador"))
      return operators
          .findById(Actor.id())
          .map(a -> a.storeId.equals(o.getStoreId()))
          .orElse(false);
    return Actor.has("Cliente") && o.getOwnerId().equals(Actor.id());
  }

  public void read(CustomerOrder o) {
    if (!visible(o)) throw new AccessDeniedException("Pedido fuera de tu acceso");
  }
}
