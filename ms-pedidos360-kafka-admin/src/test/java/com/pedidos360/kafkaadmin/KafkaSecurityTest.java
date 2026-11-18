package com.pedidos360.kafkaadmin;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class KafkaSecurityTest {
  @Autowired MockMvc mvc;
  @MockitoBean KafkaPlatformService service;
  static SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor actor(String role) {
    return SecurityMockMvcRequestPostProcessors.jwt().authorities(
        new SimpleGrantedAuthority("SCOPE_access_as_user"), new SimpleGrantedAuthority("ROLE_" + role));
  }
  @Test void nonAdminsCannotInspectOrMutateKafka() throws Exception {
    mvc.perform(get("/api/kafka/topics")).andExpect(status().isUnauthorized());
    for (String role : List.of("Cliente", "Operador", "Auditor")) {
      mvc.perform(get("/api/kafka/topics").with(actor(role))).andExpect(status().isForbidden());
      mvc.perform(delete("/api/kafka/topics/demo.test").with(actor(role))).andExpect(status().isForbidden());
      mvc.perform(delete("/api/kafka/topics/demo.test/config/retention.ms").with(actor(role)))
          .andExpect(status().isForbidden());
    }
    verifyNoInteractions(service);
  }

  @Test void adminsCanRemoveAConfigurationOverride() throws Exception {
    mvc.perform(delete("/api/kafka/topics/demo.test/config/retention.ms").with(actor("Admin")))
        .andExpect(status().isNoContent());
    verify(service).resetConfiguration("demo.test", "retention.ms");
  }
  @Test void invalidTopicConfigurationFailsBeforeBrokerAccess() throws Exception {
    for (String input : List.of(
        "{\"name\":\"orders.events\",\"partitions\":3,\"replicationFactor\":3,\"retentionMs\":86400000,\"cleanupPolicy\":\"delete\"}",
        "{\"name\":\"demo.test\",\"partitions\":0,\"replicationFactor\":3,\"retentionMs\":86400000,\"cleanupPolicy\":\"delete\"}",
        "{\"name\":\"demo.test\",\"partitions\":3,\"replicationFactor\":3,\"retentionMs\":-1,\"cleanupPolicy\":\"delete\"}"))
      mvc.perform(post("/api/kafka/topics").with(actor("Admin"))
          .contentType(MediaType.APPLICATION_JSON).content(input)).andExpect(status().isBadRequest());
    verifyNoInteractions(service);
  }
}

