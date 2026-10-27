package com.pedidos360.common;

import java.util.*;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.*;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {
  @Bean
  SecurityFilterChain security(HttpSecurity http) throws Exception {
    var scopes = new JwtGrantedAuthoritiesConverter();
    var converter = new JwtAuthenticationConverter();
    converter.setJwtGrantedAuthoritiesConverter(
        jwt -> {
          var all = new ArrayList<>(scopes.convert(jwt));
          for (String role :
              Optional.ofNullable(jwt.getClaimAsStringList("roles")).orElse(List.of()))
            if (Set.of("Admin", "Operador", "Cliente", "Auditor").contains(role))
              all.add(new SimpleGrantedAuthority("ROLE_" + role));
          return all;
        });
    return http.cors(Customizer.withDefaults())
        .csrf(c -> c.disable())
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            a ->
                a.requestMatchers(HttpMethod.OPTIONS, "/**")
                    .permitAll()
                    .requestMatchers("/actuator/health", "/actuator/health/**")
                    .permitAll()
                    .requestMatchers(
                        "/api/data",
                        "/api/orders",
                        "/api/orders/**",
                        "/api/catalog/**",
                        "/api/audit",
                        "/api/reports",
                        "/api/operators",
                        "/api/operators/**",
                        "/api/outbox",
                        "/api/outbox/**",
                        "/api/inventory-operations",
                        "/api/notifications",
                        "/api/notifications/**",
                        "/api/rabbit/**",
                        "/v3/api-docs/**",
                        "/swagger-ui/**",
                        "/swagger-ui.html")
                    .hasAuthority("SCOPE_access_as_user")
                    .anyRequest()
                    .denyAll())
        .oauth2ResourceServer(o -> o.jwt(j -> j.jwtAuthenticationConverter(converter)))
        .build();
  }
}
