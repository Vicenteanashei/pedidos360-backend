package com.pedidos360.notify;

// Controlled transient failure, enabled only in a configured demonstration environment.
public class DemoDeliveryFailureException extends IllegalStateException {
  public DemoDeliveryFailureException() {
    super("Fallo solicitado para demostrar reintentos y DLQ");
  }
}
