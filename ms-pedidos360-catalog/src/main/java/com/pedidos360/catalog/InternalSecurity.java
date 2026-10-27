package com.pedidos360.catalog;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;

@Configuration
public class InternalSecurity {
  @Bean
  @Order(1)
  SecurityFilterChain internal(HttpSecurity http, @Value("${INTERNAL_SERVICE_KEY}") String key)
      throws Exception {
    if (key.length() < 32)
      throw new IllegalArgumentException("INTERNAL_SERVICE_KEY requiere al menos 32 caracteres");
    var filter =
        new OncePerRequestFilter() {
          protected void doFilterInternal(
              HttpServletRequest req, HttpServletResponse res, FilterChain chain)
              throws ServletException, IOException {
            String supplied = req.getHeader("X-Service-Key");
            if (supplied == null
                || !MessageDigest.isEqual(
                    key.getBytes(StandardCharsets.UTF_8),
                    supplied.getBytes(StandardCharsets.UTF_8))) {
              res.sendError(403);
              return;
            }
            SecurityContextHolder.getContext()
                .setAuthentication(
                    new UsernamePasswordAuthenticationToken(
                        "orders-service",
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_SERVICE"))));
            chain.doFilter(req, res);
          }
        };
    return http.securityMatcher("/api/catalog/reservations/**")
        .csrf(c -> c.disable())
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .addFilterBefore(filter, AnonymousAuthenticationFilter.class)
        .authorizeHttpRequests(a -> a.anyRequest().hasRole("SERVICE"))
        .build();
  }
}
