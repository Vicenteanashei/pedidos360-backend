package com.pedidos360.bff.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Endpoint de prueba del tutorial (hito H5): solo responde si el token es valido.
 * Sin token o con token invalido, Spring Security corta antes con 401.
 */
@RestController
public class DataController {

	@GetMapping("/api/data")
	public Map<String, Object> data(@AuthenticationPrincipal Jwt jwt) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("mensaje", "Acceso autorizado a Spring Boot");
		body.put("usuario", jwt.getClaimAsString("preferred_username"));
		body.put("nombre", jwt.getClaimAsString("name"));
		body.put("scope", jwt.getClaimAsString("scp"));
		body.put("fecha", OffsetDateTime.now().toString());
		return body;
	}
}
