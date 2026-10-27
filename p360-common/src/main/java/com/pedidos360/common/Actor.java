package com.pedidos360.common;

import java.util.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

public final class Actor {
  private Actor() {}

  public static JwtAuthenticationToken token() {
    var a = SecurityContextHolder.getContext().getAuthentication();
    if (a instanceof JwtAuthenticationToken t) return t;
    throw new AccessDeniedException("Se requiere identidad JWT");
  }

  public static String id() {
    var j = token().getToken();
    String tid = j.getClaimAsString("tid"), oid = j.getClaimAsString("oid");
    return (tid == null ? j.getIssuer().toString() : tid)
        + ":"
        + (oid == null ? j.getSubject() : oid);
  }

  public static boolean has(String role) {
    return token().getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_" + role));
  }

  public static String bearer() {
    return "Bearer " + token().getToken().getTokenValue();
  }

  public static void roles(String... roles) {
    for (String r : roles) if (has(r)) return;
    throw new AccessDeniedException("Rol sin permiso para esta operación");
  }
}
