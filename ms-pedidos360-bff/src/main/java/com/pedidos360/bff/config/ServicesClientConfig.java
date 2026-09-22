package com.pedidos360.bff.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** Cliente HTTP hacia los microservicios internos (red de Docker, no expuestos afuera). */
@Configuration
public class ServicesClientConfig {

	@Bean
	RestClient servicesClient(RestClient.Builder builder) {
		SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
		factory.setConnectTimeout(3_000);
		factory.setReadTimeout(10_000);
		return builder.requestFactory(factory).build();
	}
}
