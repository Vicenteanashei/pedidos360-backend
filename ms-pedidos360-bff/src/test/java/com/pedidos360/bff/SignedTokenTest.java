package com.pedidos360.bff;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.*;
import java.security.*;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.*;

@SpringBootTest
@AutoConfigureMockMvc
class SignedTokenTest {
  static final KeyPair KEYS;

  static {
    try {
      var generator = KeyPairGenerator.getInstance("RSA");
      generator.initialize(2048);
      KEYS = generator.generateKeyPair();
    } catch (Exception e) {
      throw new ExceptionInInitializerError(e);
    }
  }

  @Autowired MockMvc mvc;

  @TestConfiguration
  static class Config {
    @Bean
    JwtDecoder decoder() {
      var decoder = NimbusJwtDecoder.withPublicKey((RSAPublicKey) KEYS.getPublic()).build();
      decoder.setJwtValidator(
          new DelegatingOAuth2TokenValidator<>(
              JwtValidators.createDefaultWithIssuer("https://test.local"),
              new JwtClaimValidator<List<String>>(
                  "aud", aud -> aud != null && aud.contains("test-api"))));
      return decoder;
    }

    @Bean
    RoleEndpoint roles() {
      return new RoleEndpoint();
    }
  }

  @RestController
  static class RoleEndpoint {
    @GetMapping("/api/rabbit/role-check")
    @PreAuthorize("hasRole('Admin')")
    public Map<String, String> admin() {
      return Map.of("role", "Admin");
    }
  }

  String token(String issuer, String audience, String scope, String role, boolean expired)
      throws Exception {
    var claims =
        new JWTClaimsSet.Builder()
            .issuer(issuer)
            .subject("user")
            .audience(audience)
            .issueTime(new Date(System.currentTimeMillis() - 1000))
            .expirationTime(new Date(expired ? 1 : System.currentTimeMillis() + 300000))
            .claim("scp", scope)
            .claim("roles", List.of(role))
            .build();
    var signed = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims);
    signed.sign(new RSASSASigner((RSAPrivateKey) KEYS.getPrivate()));
    return signed.serialize();
  }

  @Test
  void signedTokenRoleApplied() throws Exception {
    mvc.perform(
            get("/api/rabbit/role-check")
                .header(
                    "Authorization",
                    "Bearer "
                        + token(
                            "https://test.local", "test-api", "access_as_user", "Admin", false)))
        .andExpect(status().isOk());
  }

  @Test
  void scopeAndRoleBothRequired() throws Exception {
    for (var values :
        List.of(
            List.of("access_as_user", "Cliente"),
            List.of("other", "Admin"),
            List.of("access_as_user", "ADMIN")))
      mvc.perform(
              get("/api/rabbit/role-check")
                  .header(
                      "Authorization",
                      "Bearer "
                          + token(
                              "https://test.local",
                              "test-api",
                              values.get(0),
                              values.get(1),
                              false)))
          .andExpect(status().isForbidden());
  }

  @Test
  void issuerAudienceExpiryRejected() throws Exception {
    for (var values :
        List.of(
            List.of("https://wrong.local", "test-api", "false"),
            List.of("https://test.local", "other", "false"),
            List.of("https://test.local", "test-api", "true")))
      mvc.perform(
              get("/api/data")
                  .header(
                      "Authorization",
                      "Bearer "
                          + token(
                              values.get(0),
                              values.get(1),
                              "access_as_user",
                              "Admin",
                              Boolean.parseBoolean(values.get(2)))))
          .andExpect(status().isUnauthorized());
  }

  @Test
  void invalidSignatureRejected() throws Exception {
    String original = token("https://test.local", "test-api", "access_as_user", "Admin", false);
    String[] parts = original.split("\\.");
    byte[] signature = Base64.getUrlDecoder().decode(parts[2]);
    signature[0] ^= 1;
    String invalid =
        parts[0]
            + "."
            + parts[1]
            + "."
            + Base64.getUrlEncoder().withoutPadding().encodeToString(signature);
    mvc.perform(get("/api/data").header("Authorization", "Bearer " + invalid))
        .andExpect(status().isUnauthorized());
  }
}
