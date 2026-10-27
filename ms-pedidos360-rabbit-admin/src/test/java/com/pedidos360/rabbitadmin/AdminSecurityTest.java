package com.pedidos360.rabbitadmin;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class AdminSecurityTest {
  @Autowired MockMvc mvc;

  @Test
  void clientDenied() throws Exception {
    mvc.perform(
            get("/api/rabbit/queues")
                .with(
                    jwt()
                        .authorities(
                            new SimpleGrantedAuthority("SCOPE_access_as_user"),
                            new SimpleGrantedAuthority("ROLE_Cliente"))))
        .andExpect(status().isForbidden());
  }

  @Test
  void businessQueueDeletionDeniedBeforeNetwork() throws Exception {
    mvc.perform(
            delete("/api/rabbit/queues/q.cmd.email")
                .with(
                    jwt()
                        .authorities(
                            new SimpleGrantedAuthority("SCOPE_access_as_user"),
                            new SimpleGrantedAuthority("ROLE_Admin"))))
        .andExpect(status().isBadRequest());
  }
}
