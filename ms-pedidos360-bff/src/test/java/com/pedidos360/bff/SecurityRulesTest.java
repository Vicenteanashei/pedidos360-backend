package com.pedidos360.bff;

import com.pedidos360.bff.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Sin token 401; con token pero rol o scope incorrecto 403. El 200 se prueba end-to-end con el compose. */
@SpringBootTest
@AutoConfigureMockMvc
class SecurityRulesTest {

	@Autowired
	private MockMvc mvc;

	private static SimpleGrantedAuthority scope() {
		return new SimpleGrantedAuthority("SCOPE_access_as_user");
	}

	private static SimpleGrantedAuthority role(String role) {
		return new SimpleGrantedAuthority("ROLE_" + role);
	}

	@Test
	void sinTokenDevuelve401() throws Exception {
		mvc.perform(get("/api/orders")).andExpect(status().isUnauthorized());
	}

	@Test
	void adminNoCreaPedidos() throws Exception {
		mvc.perform(post("/api/orders").with(jwt().authorities(scope(), role("Admin")))
						.contentType("application/json").content("{}"))
				.andExpect(status().isForbidden());
	}

	@Test
	void clienteNoCambiaEstado() throws Exception {
		mvc.perform(put("/api/orders/1/status").with(jwt().authorities(scope(), role("Cliente")))
						.contentType("application/json").content("{\"status\":\"ACEPTADO\"}"))
				.andExpect(status().isForbidden());
	}

	@Test
	void operadorNoEliminaPedidos() throws Exception {
		mvc.perform(delete("/api/orders/1").with(jwt().authorities(scope(), role("Operador"))))
				.andExpect(status().isForbidden());
	}

	@Test
	void tokenSinScopeDeLaApiEsRechazado() throws Exception {
		mvc.perform(get("/api/orders").with(jwt().authorities(role("Admin"))))
				.andExpect(status().isForbidden());
	}

	@Test
	void rutaNoMapeadaSeDeniega() throws Exception {
		mvc.perform(get("/api/otra-cosa").with(jwt().authorities(scope(), role("Admin"))))
				.andExpect(status().isForbidden());
	}

	@Test
	void claimRolesSeMapeaARoleSpring() {
		Jwt token = Jwt.withTokenValue("t").header("alg", "RS256")
				.claim("scp", "access_as_user").claim("roles", List.of("Operador")).build();

		List<String> authorities = new SecurityConfig().jwtAuthenticationConverter().convert(token)
				.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList();

		assertThat(authorities).containsExactlyInAnyOrder("SCOPE_access_as_user", "ROLE_Operador");
	}
}
