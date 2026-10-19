package org.campuslab.mqadmin;

import static org.assertj.core.api.Assumptions.assumeThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * La API completa contra un RabbitMQ REAL (lo que los mocks no pueden probar: cómo responde
 * de verdad el broker a existencia, borrado, bindings y argumentos).
 *
 * Se omite automáticamente si no hay un broker en RABBITMQ_HOST:RABBITMQ_PORT (por defecto
 * localhost:5672). Para correrlo: {@code docker compose up -d} en campuslab-infra/mq.
 * Usa nombres únicos por ejecución y limpia lo que crea.
 */
@SpringBootTest
@AutoConfigureMockMvc
class RabbitAdminBrokerTest {

    private static final String HOST = System.getenv().getOrDefault("RABBITMQ_HOST", "localhost");
    private static final int PORT = Integer.parseInt(System.getenv().getOrDefault("RABBITMQ_PORT", "5672"));

    private final String suffix = UUID.randomUUID().toString().substring(0, 8);
    private final String exchange = "it.x." + suffix;
    private final String queue = "it.q." + suffix;
    private final String quorumQueue = "it.quorum." + suffix;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private AmqpAdmin amqpAdmin;
    @Autowired
    private RabbitTemplate rabbitTemplate;

    @BeforeAll
    static void requireBroker() {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(HOST, PORT), 1000);
        } catch (Exception ex) {
            assumeThat(false).as("No hay RabbitMQ en %s:%d, se omiten los tests contra broker real", HOST, PORT).isTrue();
        }
    }

    @AfterEach
    void cleanUp() {
        amqpAdmin.deleteQueue(queue);
        amqpAdmin.deleteQueue(quorumQueue);
        amqpAdmin.deleteExchange(exchange);
    }

    private static RequestPostProcessor admin() {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    @Test
    void cicloDeVidaCompletoDeExchangeColaYBinding() throws Exception {
        // exchange
        mockMvc.perform(post("/api/mq/exchanges").with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + exchange + "\",\"type\":\"DIRECT\"}"))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/mq/exchanges").with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + exchange + "\",\"type\":\"DIRECT\"}"))
                .andExpect(status().isConflict());

        // cola con TTL, tope de largo y dead letter hacia ese exchange
        String queueBody = "{\"name\":\"" + queue + "\",\"messageTtlMs\":60000,\"maxLength\":100,"
                + "\"deadLetterExchange\":\"" + exchange + "\",\"deadLetterRoutingKey\":\"dead\"}";
        mockMvc.perform(post("/api/mq/queues").with(admin()).contentType(MediaType.APPLICATION_JSON).content(queueBody))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/mq/queues").with(admin()).contentType(MediaType.APPLICATION_JSON).content(queueBody))
                .andExpect(status().isConflict());

        // dead letter exchange inexistente -> 422
        mockMvc.perform(post("/api/mq/queues").with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + queue + ".otra\",\"deadLetterExchange\":\"no.existe." + suffix + "\"}"))
                .andExpect(status().isUnprocessableEntity());

        // binding (cola y exchange deben existir)
        String bindingBody = "{\"queue\":\"" + queue + "\",\"exchange\":\"" + exchange + "\",\"routingKey\":\"k\"}";
        mockMvc.perform(post("/api/mq/bindings").with(admin()).contentType(MediaType.APPLICATION_JSON).content(bindingBody))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/mq/bindings").with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"queue\":\"no.existe." + suffix + "\",\"exchange\":\"" + exchange + "\",\"routingKey\":\"k\"}"))
                .andExpect(status().isNotFound());

        // un mensaje publicado al exchange llega a la cola por el binding
        rabbitTemplate.convertAndSend(exchange, "k", "hola");
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                mockMvc.perform(get("/api/mq/queues/{name}", queue).with(admin()))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.messageCount").value(1))
                        .andExpect(jsonPath("$.consumerCount").value(0)));

        // vaciar la cola no la elimina
        mockMvc.perform(delete("/api/mq/queues/{name}/messages", queue).with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.purgedMessages").value(1));
        mockMvc.perform(get("/api/mq/queues/{name}", queue).with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messageCount").value(0));

        // desenlazar; RabbitMQ trata el unbind como idempotente, así que repetirlo también es 204
        mockMvc.perform(delete("/api/mq/bindings").with(admin())
                        .param("queue", queue).param("exchange", exchange).param("routingKey", "k"))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/mq/bindings").with(admin())
                        .param("queue", queue).param("exchange", exchange).param("routingKey", "k"))
                .andExpect(status().isNoContent());
        // ...pero con una cola inexistente sí es 404
        mockMvc.perform(delete("/api/mq/bindings").with(admin())
                        .param("queue", "no.existe." + suffix).param("exchange", exchange).param("routingKey", "k"))
                .andExpect(status().isNotFound());

        // eliminar cola y exchange
        mockMvc.perform(delete("/api/mq/queues/{name}", queue).with(admin())).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/mq/queues/{name}", queue).with(admin())).andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/mq/queues/{name}", queue).with(admin())).andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/mq/exchanges/{name}", exchange).with(admin())).andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/mq/exchanges/{name}", exchange).with(admin())).andExpect(status().isNotFound());
    }

    @Test
    void sePuedeCrearUnaColaQuorum() throws Exception {
        mockMvc.perform(post("/api/mq/queues").with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + quorumQueue + "\",\"type\":\"QUORUM\",\"maxLength\":50}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("QUORUM"));
        mockMvc.perform(get("/api/mq/queues/{name}", quorumQueue).with(admin()))
                .andExpect(status().isOk());
    }

    @Test
    void unExchangeDeFabricaSePuedeUsarComoDeadLetterPeroNoEliminar() throws Exception {
        mockMvc.perform(post("/api/mq/queues").with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + queue + "\",\"deadLetterExchange\":\"amq.direct\"}"))
                .andExpect(status().isCreated());
        mockMvc.perform(delete("/api/mq/exchanges/amq.direct").with(admin()))
                .andExpect(status().isBadRequest());
    }
}
