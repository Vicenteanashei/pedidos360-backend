package com.pedidos360.notify;

import java.io.*;
import java.nio.file.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.mail.enabled", havingValue = "true")
public class EmailSender {
  private final JavaMailSender mail;
  private final String from;

  public EmailSender(JavaMailSender m, @Value("${MAIL_FROM}") String from) {
    mail = m;
    this.from = from;
  }

  public void send(Path file) throws Exception {
    try (var input = Files.newInputStream(file)) {
      var msg = mail.createMimeMessage(input);
      msg.setFrom(from);
      mail.send(msg);
    }
  }
}
