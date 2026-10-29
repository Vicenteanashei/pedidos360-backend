package com.pedidos360.notify;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class NotificationsControllerTest {
  @Autowired MockMvc mvc;
  @Autowired ReceiptRepository receipts;
  @Autowired DeliveryService delivery;
  @Autowired ObjectMapper json;
  final List<Path> createdFiles = new ArrayList<>();

  @BeforeEach
  void clean() {
    receipts.deleteAll();
  }

  @AfterEach
  void removeFixtures() throws Exception {
    for (Path file : createdFiles) Files.deleteIfExists(file);
    createdFiles.clear();
  }

  static SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor actor(String id, String role) {
    return SecurityMockMvcRequestPostProcessors.jwt()
        .jwt(j -> j.claim("tid", "tenant").claim("oid", id))
        .authorities(new SimpleGrantedAuthority("SCOPE_access_as_user"),
            new SimpleGrantedAuthority("ROLE_" + role));
  }

  Receipt receipt(String owner, String type, String extension) throws Exception {
    var r = new Receipt();
    r.eventId = UUID.randomUUID().toString();
    r.ownerId = "tenant:" + owner;
    r.orderId = 7L;
    r.storeId = 1L;
    r.type = type;
    r.processedAt = Instant.now();
    r.artifact = r.eventId + extension;
    Files.createDirectories(delivery.directory());
    Path file = delivery.directory().resolve(r.artifact);
    Files.writeString(file, extension.equals(".pdf") ? "%PDF-1.4\nfixture" : "Archivo interno de prueba");
    createdFiles.add(file);
    return receipts.saveAndFlush(r);
  }

  @Test
  void customerListHasOwnFriendlyNotificationsAndOnlyPdfDownload() throws Exception {
    receipt("client", "email.send", ".eml");
    receipt("client", "kitchen.ticket", ".txt");
    receipt("client", "invoice.gen", ".pdf");
    receipt("other", "invoice.gen", ".pdf");
    String body = mvc.perform(get("/api/notifications").with(actor("client", "Cliente")))
        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    var rows = json.readTree(body);
    assertEquals(3, rows.size());
    int downloads = 0;
    for (var row : rows) {
      assertFalse(row.has("type"));
      assertFalse(row.has("ownerId"));
      assertFalse(row.path("message").asText().isBlank());
      if (row.path("downloadable").asBoolean()) {
        downloads++;
        assertEquals("comprobante-pedido-7.pdf", row.path("artifact").asText());
      } else assertFalse(row.has("artifact"));
    }
    assertEquals(1, downloads);
    assertFalse(body.contains("email.send"));
    assertFalse(body.contains("kitchen.ticket"));
    assertFalse(body.contains(".eml"));
  }

  @Test
  void customerCanDownloadOwnInvoicePdfWithFriendlyFilename() throws Exception {
    var r = receipt("client", "invoice.gen", ".pdf");
    mvc.perform(get("/api/notifications/" + r.eventId + "/artifact").with(actor("client", "Cliente")))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_PDF))
        .andExpect(header().string("Content-Disposition", "attachment; filename=\"comprobante-pedido-7.pdf\""));
  }

  @Test
  void customerCannotDownloadInternalFilesEvenByDirectUrl() throws Exception {
    for (var entry : List.of(new String[] {"email.send", ".eml"},
        new String[] {"kitchen.ticket", ".txt"}, new String[] {"email.send", ".pdf"})) {
      var r = receipt("client", entry[0], entry[1]);
      mvc.perform(get("/api/notifications/" + r.eventId + "/artifact").with(actor("client", "Cliente")))
          .andExpect(status().isForbidden());
    }
  }

  @Test
  void customerCannotDownloadAnotherCustomersPdf() throws Exception {
    var r = receipt("other", "invoice.gen", ".pdf");
    mvc.perform(get("/api/notifications/" + r.eventId + "/artifact").with(actor("client", "Cliente")))
        .andExpect(status().isForbidden());
  }

  @Test
  void adminRetainsTechnicalMetadataAndInternalArtifacts() throws Exception {
    var mail = receipt("other", "email.send", ".eml");
    var kitchen = receipt("client", "kitchen.ticket", ".txt");
    mvc.perform(get("/api/notifications").with(actor("admin", "Admin")))
        .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2))
        .andExpect(jsonPath("$[0].type").exists()).andExpect(jsonPath("$[0].ownerId").exists());
    for (var r : List.of(mail, kitchen))
      mvc.perform(get("/api/notifications/" + r.eventId + "/artifact").with(actor("admin", "Admin")))
          .andExpect(status().isOk()).andExpect(content().contentType(MediaType.APPLICATION_OCTET_STREAM));
  }

  @Test
  void legacyNonPdfInvoiceRemainsTechnicalEvidenceForAdmin() throws Exception {
    var r = receipt("client", "invoice.gen", ".txt");
    mvc.perform(get("/api/notifications").with(actor("client", "Cliente")))
        .andExpect(status().isOk()).andExpect(jsonPath("$[0].downloadable").value(false))
        .andExpect(jsonPath("$[0].artifact").doesNotExist());
    mvc.perform(get("/api/notifications/" + r.eventId + "/artifact").with(actor("client", "Cliente")))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/notifications/" + r.eventId + "/artifact").with(actor("admin", "Admin")))
        .andExpect(status().isOk());
  }

  @Test
  void otherRolesAndAnonymousCannotReadOrDownloadNotifications() throws Exception {
    var r = receipt("client", "invoice.gen", ".pdf");
    for (String role : List.of("Operador", "Auditor")) {
      mvc.perform(get("/api/notifications").with(actor("client", role))).andExpect(status().isForbidden());
      mvc.perform(get("/api/notifications/" + r.eventId + "/artifact").with(actor("client", role)))
          .andExpect(status().isForbidden());
    }
    mvc.perform(get("/api/notifications")).andExpect(status().isUnauthorized());
    mvc.perform(get("/api/notifications/" + r.eventId + "/artifact")).andExpect(status().isUnauthorized());
  }

  @Test
  void missingArtifactReturnsNotFound() throws Exception {
    var r = receipt("client", "invoice.gen", ".pdf");
    Files.delete(delivery.directory().resolve(r.artifact));
    mvc.perform(get("/api/notifications/" + r.eventId + "/artifact").with(actor("client", "Cliente")))
        .andExpect(status().isNotFound());
  }

  @Test
  void artifactCannotEscapeConfiguredDirectory() throws Exception {
    var r = receipt("client", "invoice.gen", ".pdf");
    r.artifact = "../outside.pdf";
    receipts.saveAndFlush(r);
    for (String role : List.of("Cliente", "Admin"))
      mvc.perform(get("/api/notifications/" + r.eventId + "/artifact").with(actor("client", role)))
          .andExpect(status().isBadRequest());
  }
}
