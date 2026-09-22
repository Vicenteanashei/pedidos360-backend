package com.pedidos360.bff.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authorization.AuthorityAuthorizationManager;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.AuthorizationManagers;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * El BFF es un Resource Server: no emite tokens, solo valida los que emite Entra ID.
 * Firma, issuer y audiencia se validan con application.yml (issuer-uri + audiences).
 * Aqui se define que rol puede llamar a cada endpoint.
 */
@Configuration
public class SecurityConfig {

	/** Scope delegado que expone el registro de la API en Entra ID. */
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
				// Consultar pedidos: todos los roles
				.requestMatchers(HttpMethod.GET, "/api/orders", "/api/orders/**")
					.access(scopeAndRoles("Admin", "Operador", "Cliente"))
				// Crear pedido: cliente u operador (segun el caso)
				.requestMatchers(HttpMethod.POST, "/api/orders")
					.access(scopeAndRoles("Operador", "Cliente"))
				// Cambiar estado: operador o admin (va antes que PUT /api/orders/**)
				.requestMatchers(HttpMethod.PUT, "/api/orders/*/status")
					.access(scopeAndRoles("Admin", "Operador"))
				// Editar datos del pedido (solo mientras esta CREADO)
				.requestMatchers(HttpMethod.PUT, "/api/orders/*")
					.access(scopeAndRoles("Operador", "Cliente"))
				// Eliminar: solo admin
				.requestMatchers(HttpMethod.DELETE, "/api/orders/*")
					.access(scopeAndRoles("Admin"))
				.anyRequest().denyAll())
			.oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())));

		return http.build();
	}

	/** Exige el scope delegado y ademas uno de los app roles del token. */
	private AuthorizationManager<RequestAuthorizationContext> scopeAndRoles(String... roles) {
		return AuthorizationManagers.allOf(
				AuthorityAuthorizationManager.hasAuthority(SCOPE),
				AuthorityAuthorizationManager.hasAnyRole(roles));
	}

	/**
	 * Spring solo mapea el claim "scp" a SCOPE_*. Entra ID manda los app roles en el claim
	 * "roles", asi que se agregan como ROLE_Admin / ROLE_Operador / ROLE_Cliente.
	 */
	@Bean
	public JwtAuthenticationConverter jwtAuthenticationConverter() {
		JwtGrantedAuthoritiesConverter scopes = new JwtGrantedAuthoritiesConverter();

		JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
		converter.setJwtGrantedAuthoritiesConverter(jwt -> {
			Collection<GrantedAuthority> authorities = new ArrayList<>(scopes.convert(jwt));
			List<String> roles = jwt.getClaimAsStringList("roles");
			if (roles != null) {
				roles.forEach(role -> authorities.add(new SimpleGrantedAuthority("ROLE_" + role)));
			}
			return authorities;
		});
		return converter;
	}
}
