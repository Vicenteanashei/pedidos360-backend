package com.pedidos360.notify.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(ConsumerPolicy.class)
public class NotifyMessagingConfig {}
