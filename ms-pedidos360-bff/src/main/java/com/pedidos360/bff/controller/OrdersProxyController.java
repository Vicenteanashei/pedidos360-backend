package com.pedidos360.bff.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.URI;
import java.util.Optional;

/**
 * Reenvia a ms-pedidos360-orders las llamadas que ya pasaron la validacion del JWT y las reglas de rol.
 * No duplica reglas de negocio: devuelve tal cual el estado y el cuerpo del micro (incluidos 400/404/409).
 */
@RestController
public class OrdersProxyController {

	private final RestClient client;
	private final String ordersUrl;

	public OrdersProxyController(RestClient servicesClient, @Value("${app.services.orders-url}") String ordersUrl) {
		this.client = servicesClient;
		this.ordersUrl = ordersUrl;
	}

	@RequestMapping(
			path = { "/api/orders", "/api/orders/**" },
			method = { RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.DELETE })
	public ResponseEntity<byte[]> proxy(HttpServletRequest request, @RequestBody(required = false) byte[] body)
			throws IOException {

		URI target = URI.create(ordersUrl + request.getRequestURI()
				+ (request.getQueryString() != null ? "?" + request.getQueryString() : ""));

		RestClient.RequestBodySpec spec = client
				.method(HttpMethod.valueOf(request.getMethod()))
				.uri(target);

		Optional.ofNullable(request.getContentType())
				.ifPresent(contentType -> spec.contentType(MediaType.parseMediaType(contentType)));

		if (body != null && body.length > 0) {
			spec.body(body);
		}

		return spec.exchange((req, res) -> {
			HttpHeaders headers = new HttpHeaders();
			Optional.ofNullable(res.getHeaders().getContentType()).ifPresent(headers::setContentType);
			return ResponseEntity.status(res.getStatusCode())
					.headers(headers)
					.body(res.getBody().readAllBytes());
		}, false);
	}
}
