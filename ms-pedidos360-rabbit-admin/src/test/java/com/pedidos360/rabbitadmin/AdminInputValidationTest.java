package com.pedidos360.rabbitadmin;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class AdminInputValidationTest {
  @Autowired MockMvc mvc;
  @MockitoBean RabbitAdminService service;

  org.springframework.test.web.servlet.request.RequestPostProcessor admin() {
    return jwt().authorities(new SimpleGrantedAuthority("SCOPE_access_as_user"),
        new SimpleGrantedAuthority("ROLE_Admin"));
  }

  @Test
  void nullMissingEmptyAndUnknownExchangeTypesFailBeforeService() throws Exception {
    for (String body : new String[]{"{\"name\":\"demo.test\"}",
        "{\"name\":\"demo.test\",\"type\":null}",
        "{\"name\":\"demo.test\",\"type\":\"\"}",
        "{\"name\":\"demo.test\",\"type\":\"invalid\"}"}) {
      mvc.perform(post("/api/rabbit/exchanges").with(admin()).contentType(MediaType.APPLICATION_JSON)
          .content(body)).andExpect(status().isBadRequest());
    }
    verifyNoInteractions(service);
  }

  @Test
  void blankAndOverlongResourceNamesFailBeforeService() throws Exception {
    for (String name : new String[]{"", " ", "q.cmd.email", "demo." + "a".repeat(81)}) {
      mvc.perform(post("/api/rabbit/queues").with(admin()).contentType(MediaType.APPLICATION_JSON)
          .content("{\"name\":\"" + name + "\"}")).andExpect(status().isBadRequest());
    }
    verifyNoInteractions(service);
  }

  @Test
  void bindingRequiresKeyAndRejectsOverlongConfiguration() throws Exception {
    for (String key : new String[]{"null", "\"" + "a".repeat(151) + "\""}) {
      mvc.perform(post("/api/rabbit/bindings").with(admin()).contentType(MediaType.APPLICATION_JSON)
          .content("{\"exchange\":\"demo.topic\",\"queue\":\"demo.queue\",\"routingKey\":" + key + "}"))
          .andExpect(status().isBadRequest());
    }
    verifyNoInteractions(service);
  }

  @Test
  void invalidPropertiesKeyRejectedBeforeService() throws Exception {
    for (String key : new String[]{"", "bad\nkey", "a".repeat(513)}) {
      mvc.perform(delete("/api/rabbit/bindings").with(admin())
          .param("exchange", "demo.topic").param("queue", "demo.queue").param("propertiesKey", key))
          .andExpect(status().isBadRequest());
    }
    verifyNoInteractions(service);
  }

  @Test
  void validExchangeAndTopicBindingDelegateToService() throws Exception {
    mvc.perform(post("/api/rabbit/exchanges").with(admin()).contentType(MediaType.APPLICATION_JSON)
        .content("{\"name\":\"demo.topic\",\"type\":\"topic\"}"))
        .andExpect(status().isCreated());
    verify(service).createExchange(any());
    mvc.perform(post("/api/rabbit/bindings").with(admin()).contentType(MediaType.APPLICATION_JSON)
        .content("{\"exchange\":\"demo.topic\",\"queue\":\"demo.queue\",\"routingKey\":\"invoice.#\"}"))
        .andExpect(status().isCreated());
    verify(service).bind(any());
  }
}
