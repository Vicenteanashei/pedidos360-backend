package com.pedidos360.orders.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(OrderNotFoundException.class)
	public ProblemDetail handleNotFound(OrderNotFoundException ex) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
	}

	@ExceptionHandler(InvalidStatusTransitionException.class)
	public ProblemDetail handleInvalidTransition(InvalidStatusTransitionException ex) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
		String detail = ex.getBindingResult().getFieldErrors().stream()
				.map(e -> e.getField() + ": " + e.getDefaultMessage())
				.collect(Collectors.joining(", "));
		return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
	}

	@ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
	public ProblemDetail handleBadInput(Exception ex) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
				"Request invalido (revisa el JSON, el estado o el formato de fechas ISO: 2026-09-10T00:00:00)");
	}
}
