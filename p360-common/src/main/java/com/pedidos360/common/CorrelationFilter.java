package com.pedidos360.common;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.*;
import java.util.*;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class CorrelationFilter extends OncePerRequestFilter {
  protected void doFilterInternal(
      HttpServletRequest req, HttpServletResponse res, FilterChain chain)
      throws ServletException, IOException {
    String id = req.getHeader("X-Correlation-Id");
    if (id == null || !id.matches("[A-Za-z0-9-]{1,64}")) id = UUID.randomUUID().toString();
    MDC.put("correlationId", id);
    res.setHeader("X-Correlation-Id", id);
    try {
      chain.doFilter(req, res);
    } finally {
      MDC.remove("correlationId");
    }
  }
}
