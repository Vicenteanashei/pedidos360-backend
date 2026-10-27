package com.pedidos360.common;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.*;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class ApiErrors {
  @ExceptionHandler(ResponseStatusException.class)
  ProblemDetail status(ResponseStatusException e) {
    return ProblemDetail.forStatusAndDetail(
        e.getStatusCode(), e.getReason() == null ? "Solicitud rechazada" : e.getReason());
  }

  @ExceptionHandler(RestClientResponseException.class)
  ProblemDetail remote(RestClientResponseException e) {
    return ProblemDetail.forStatusAndDetail(
        e.getStatusCode(), "Servicio interno rechazó la operación: " + e.getStatusText());
  }

  @ExceptionHandler(ResourceAccessException.class)
  ProblemDetail unavailable(ResourceAccessException e) {
    return ProblemDetail.forStatusAndDetail(
        HttpStatus.SERVICE_UNAVAILABLE, "Servicio interno no disponible; vuelve a intentar");
  }

  @ExceptionHandler({
    OptimisticLockingFailureException.class,
    DataIntegrityViolationException.class
  })
  ProblemDetail conflict(Exception e) {
    return ProblemDetail.forStatusAndDetail(
        HttpStatus.CONFLICT, "Conflicto concurrente; actualiza y vuelve a intentar");
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ProblemDetail invalid(MethodArgumentNotValidException e) {
    return ProblemDetail.forStatusAndDetail(
        HttpStatus.BAD_REQUEST,
        e.getBindingResult().getFieldErrors().stream()
            .map(x -> x.getField() + ": " + x.getDefaultMessage())
            .reduce((a, b) -> a + ", " + b)
            .orElse("Datos inválidos"));
  }

  @ExceptionHandler({
    jakarta.validation.ConstraintViolationException.class,
    org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,
    org.springframework.http.converter.HttpMessageNotReadableException.class
  })
  ProblemDetail badInput(Exception e) {
    return ProblemDetail.forStatusAndDetail(
        HttpStatus.BAD_REQUEST, "Revisa los parámetros y el cuerpo de la solicitud");
  }
}
