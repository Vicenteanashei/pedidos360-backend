package com.pedidos360.bff.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * El BFF es un Resource Server: no emite tokens, solo valida los que emite Entra ID.
 * Firma, issuer, audiencia y vigencia se validan con application.yml (issuer-uri + audiences).
 * Para usar la API basta un token valido emitido para ella (scope access_as_user); no se usan roles.
 */
@Configuration
public class SecurityConfig {

	/** Scope delegado que expone el registro de la API en Entra ID (viene en todo token pedido para la API). */
	private static final String SCOPE = "SCOPE_access_as_user";

	@Bean
	SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
		http
			.cors(Customizer.withDefaults())
			.csrf(csrf -> csrf.disable())
			.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.authorizeHttpRequests(auth -> auth
				// Preflight CORS del navegador (no lleva token)
				.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
				// Prueba del tutorial y pedidos: cualquier usuario con un token valido para la API
				.requestMatchers(HttpMethod.GET, "/api/data").hasAuthority(SCOPE)
				.requestMatchers("/api/orders", "/api/orders/**").hasAuthority(SCOPE)
				// Todo lo demas se rechaza
				.anyRequest().denyAll())
			.oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));

		return http.build();
	}
}
