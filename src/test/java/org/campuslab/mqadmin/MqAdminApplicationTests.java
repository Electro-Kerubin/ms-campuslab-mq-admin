package org.campuslab.mqadmin;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Arranca la aplicación completa (seguridad, validación, springdoc y los beans de RabbitMQ).
 * No necesita broker: la conexión de Spring AMQP es perezosa y nadie la usa en estos tests.
 */
@SpringBootTest
@AutoConfigureMockMvc
class MqAdminApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void laDocumentacionOpenApiDescribeTodosLosEndpointsDeAdministracion() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("CampusLab - Administrador de RabbitMQ"))
                .andExpect(jsonPath("$.paths['/api/mq/queues'].post").exists())
                .andExpect(jsonPath("$.paths['/api/mq/queues/{name}'].get").exists())
                .andExpect(jsonPath("$.paths['/api/mq/queues/{name}'].delete").exists())
                .andExpect(jsonPath("$.paths['/api/mq/queues/{name}/messages'].delete").exists())
                .andExpect(jsonPath("$.paths['/api/mq/exchanges'].post").exists())
                .andExpect(jsonPath("$.paths['/api/mq/exchanges/{name}'].delete").exists())
                .andExpect(jsonPath("$.paths['/api/mq/bindings'].post").exists())
                .andExpect(jsonPath("$.paths['/api/mq/bindings'].delete").exists())
                // los métodos auxiliares de validación no se exponen como campos del cuerpo
                .andExpect(jsonPath("$.components.schemas.QueueRequest.properties.name").exists())
                .andExpect(jsonPath("$.components.schemas.QueueRequest.properties.deadLetterRoutingKeyWithExchange").doesNotExist());
    }

    @Test
    void laApiDeAdministracionNoEsPublica() throws Exception {
        mockMvc.perform(get("/api/mq/queues/q.cmd.email"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void cualquierOtraRutaEstaBloqueada() throws Exception {
        mockMvc.perform(get("/api/otra-cosa")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isForbidden());
    }
}
