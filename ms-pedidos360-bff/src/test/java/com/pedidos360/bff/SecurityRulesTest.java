package com.pedidos360.bff;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Sin token 401; token sin el scope de la API 403; rutas no mapeadas 403.
 * El 200 con token valido se prueba end-to-end (aqui no hay ms-orders levantado).
 */
@SpringBootTest
@AutoConfigureMockMvc
class SecurityRulesTest {

	@Autowired
	private MockMvc mvc;

	@Test
	void sinTokenDevuelve401() throws Exception {
		mvc.perform(get("/api/orders")).andExpect(status().isUnauthorized());
		mvc.perform(post("/api/orders").contentType("application/json").content("{}")).andExpect(status().isUnauthorized());
		mvc.perform(delete("/api/orders/1")).andExpect(status().isUnauthorized());
	}

	@Test
	void tokenSinScopeDeLaApiEsRechazado() throws Exception {
		mvc.perform(get("/api/orders").with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_otra.cosa"))))
				.andExpect(status().isForbidden());
	}

	@Test
	void rutaNoMapeadaSeDeniega() throws Exception {
		mvc.perform(get("/api/otra-cosa").with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_access_as_user"))))
				.andExpect(status().isForbidden());
	}

	@Test
	void preflightCorsNoPideToken() throws Exception {
		mvc.perform(options("/api/orders")
						.header("Origin", "http://localhost:5173")
						.header("Access-Control-Request-Method", "GET"))
				.andExpect(status().isOk());
	}
}
