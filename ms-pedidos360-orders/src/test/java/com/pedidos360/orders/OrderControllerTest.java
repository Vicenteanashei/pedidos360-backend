package com.pedidos360.orders;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class OrderControllerTest {

	@Autowired
	MockMvc mvc;

	private static final String ORDER = """
			{"storeId":1,"customerName":"Ana Rojas","customerEmail":"ana@mail.com",
			 "deliveryAddress":"Av. Siempre Viva 123","notes":"Sin azucar",
			 "items":[{"productId":10,"quantity":2,"unitPrice":1500},{"productId":11,"quantity":1,"unitPrice":2500}]}
			""";

	@Test
	void flujoCompletoYReglaNoDespacharSinAceptar() throws Exception {
		mvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON).content(ORDER))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").value(1))
				.andExpect(jsonPath("$.status").value("CREADO"))
				.andExpect(jsonPath("$.total").value(5500));

		// Regla del caso: no se puede despachar sin aceptar
		changeStatus(1, "DESPACHADO").andExpect(status().isConflict());

		changeStatus(1, "ACEPTADO").andExpect(status().isOk()).andExpect(jsonPath("$.acceptedAt").exists());
		// Ya aceptado no se puede editar
		mvc.perform(put("/api/orders/1").contentType(MediaType.APPLICATION_JSON).content(ORDER))
				.andExpect(status().isConflict());
		changeStatus(1, "EN_PREPARACION").andExpect(status().isOk());
		changeStatus(1, "DESPACHADO").andExpect(status().isOk()).andExpect(jsonPath("$.dispatchedAt").exists());
		changeStatus(1, "ENTREGADO").andExpect(status().isOk()).andExpect(jsonPath("$.deliveredAt").exists());
		changeStatus(1, "CANCELADO").andExpect(status().isConflict());

		mvc.perform(get("/api/orders").param("status", "ENTREGADO"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1));

		mvc.perform(get("/api/orders/999")).andExpect(status().isNotFound());
	}

	@Test
	void pedidoSinItemsEsInvalido() throws Exception {
		mvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON)
						.content("{\"storeId\":1,\"customerName\":\"X\",\"customerEmail\":\"x@mail.com\",\"items\":[]}"))
				.andExpect(status().isBadRequest());
	}

	private ResultActions changeStatus(long id, String status) throws Exception {
		return mvc.perform(put("/api/orders/" + id + "/status")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"status\":\"" + status + "\"}"));
	}
}
