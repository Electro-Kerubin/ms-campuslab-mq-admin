package org.campuslab.mqadmin.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.campuslab.mqadmin.dto.PurgeResponse;
import org.campuslab.mqadmin.dto.QueueInfoResponse;
import org.campuslab.mqadmin.dto.QueueRequest;
import org.campuslab.mqadmin.dto.QueueResponse;
import org.campuslab.mqadmin.dto.QueueType;
import org.campuslab.mqadmin.exception.BrokerUnavailableException;
import org.campuslab.mqadmin.exception.InvalidConfigurationException;
import org.campuslab.mqadmin.exception.ResourceConflictException;
import org.campuslab.mqadmin.exception.ResourceNotFoundException;
import org.campuslab.mqadmin.security.SecurityConfig;
import org.campuslab.mqadmin.service.RabbitAdminService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Capa web del recurso colas: rutas, verbos, códigos HTTP, seguridad y formato de los errores.
 * El servicio va mockeado: no hace falta RabbitMQ.
 */
@WebMvcTest(QueueController.class)
@Import({SecurityConfig.class, FakeJwtDecoderConfig.class})
class QueueControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RabbitAdminService service;

    private static RequestPostProcessor admin() {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    // ---------- POST /api/mq/queues ----------

    @Test
    void creaUnaColaYResponde201ConLocation() throws Exception {
        when(service.createQueue(any())).thenReturn(
                new QueueResponse("q.cmd.email", true, false, QueueType.CLASSIC, 30000, 1000, "cmd.dead.dlx", "email.dlq"));

        mockMvc.perform(post("/api/mq/queues").with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"q.cmd.email","messageTtlMs":30000,"maxLength":1000,
                                 "deadLetterExchange":"cmd.dead.dlx","deadLetterRoutingKey":"email.dlq"}"""))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/mq/queues/q.cmd.email"))
                .andExpect(jsonPath("$.name").value("q.cmd.email"))
                .andExpect(jsonPath("$.durable").value(true))
                .andExpect(jsonPath("$.type").value("CLASSIC"))
                .andExpect(jsonPath("$.messageTtlMs").value(30000));
    }

    @Test
    void soloElNombreEsObligatorioYElRestoTomaValoresPorDefectoSeguros() throws Exception {
        when(service.createQueue(any())).thenReturn(
                new QueueResponse("q.simple", true, false, QueueType.CLASSIC, null, null, null, null));

        mockMvc.perform(post("/api/mq/queues").with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"q.simple\"}"))
                .andExpect(status().isCreated());

        ArgumentCaptor<QueueRequest> sent = ArgumentCaptor.forClass(QueueRequest.class);
        verify(service).createQueue(sent.capture());
        org.assertj.core.api.Assertions.assertThat(sent.getValue().durable()).isTrue();
        org.assertj.core.api.Assertions.assertThat(sent.getValue().autoDelete()).isFalse();
        org.assertj.core.api.Assertions.assertThat(sent.getValue().type()).isEqualTo(QueueType.CLASSIC);
    }

    @Test
    void unNombreVacioSeRechazaConUnMensajeClaroYNoLlegaAlBroker() throws Exception {
        mockMvc.perform(post("/api/mq/queues").with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("La petición tiene datos inválidos"))
                .andExpect(jsonPath("$.errors.name").value(org.hamcrest.Matchers.containsString("obligatorio")));

        verifyNoInteractions(service);
    }

    @Test
    void sinNombreSeRechaza() throws Exception {
        mockMvc.perform(post("/api/mq/queues").with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name").exists());
    }

    @Test
    void unaConfiguracionInvalidaSeRechazaListandoCadaCampo() throws Exception {
        mockMvc.perform(post("/api/mq/queues").with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"mala cola","messageTtlMs":-5,"maxLength":0,"deadLetterRoutingKey":"dlq"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name").exists())
                .andExpect(jsonPath("$.errors.messageTtlMs").exists())
                .andExpect(jsonPath("$.errors.maxLength").exists())
                .andExpect(jsonPath("$.errors.deadLetterRoutingKeyWithExchange").exists());

        verifyNoInteractions(service);
    }

    @Test
    void unTipoDeColaDesconocidoSeRechaza() throws Exception {
        mockMvc.perform(post("/api/mq/queues").with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"q.x\",\"type\":\"XYZ\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }

    @Test
    void unJsonMalFormadoSeRechaza() throws Exception {
        mockMvc.perform(post("/api/mq/queues").with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content("{esto no es json"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unaColaQueYaExisteResponde409() throws Exception {
        when(service.createQueue(any())).thenThrow(new ResourceConflictException("La cola 'q.cmd.email' ya existe"));

        mockMvc.perform(post("/api/mq/queues").with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"q.cmd.email\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("La cola 'q.cmd.email' ya existe"));
    }

    @Test
    void unDeadLetterExchangeInexistenteResponde422() throws Exception {
        when(service.createQueue(any())).thenThrow(new InvalidConfigurationException("El dead letter exchange 'x' no existe"));

        mockMvc.perform(post("/api/mq/queues").with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"q.a\",\"deadLetterExchange\":\"x\"}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void siRabbitMqNoRespondeSeInformaServicioNoDisponible() throws Exception {
        when(service.createQueue(any())).thenThrow(new BrokerUnavailableException("RabbitMQ no está disponible", null));

        mockMvc.perform(post("/api/mq/queues").with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"q.a\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("RabbitMQ no está disponible"));
    }

    // ---------- GET /api/mq/queues/{name} ----------

    @Test
    void consultaElEstadoDeUnaCola() throws Exception {
        when(service.getQueue("q.cmd.email")).thenReturn(new QueueInfoResponse("q.cmd.email", 4, 1));

        mockMvc.perform(get("/api/mq/queues/q.cmd.email").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("q.cmd.email"))
                .andExpect(jsonPath("$.messageCount").value(4))
                .andExpect(jsonPath("$.consumerCount").value(1));
    }

    @Test
    void consultarUnaColaInexistenteResponde404() throws Exception {
        when(service.getQueue("nope")).thenThrow(new ResourceNotFoundException("La cola 'nope' no existe"));

        mockMvc.perform(get("/api/mq/queues/nope").with(admin()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("La cola 'nope' no existe"));
    }

    @Test
    void unNombreInvalidoEnLaRutaSeRechazaConUn400() throws Exception {
        // la variable de plantilla hace que MockMvc codifique el espacio una sola vez (%20)
        mockMvc.perform(get("/api/mq/queues/{name}", "nombre con espacios").with(admin()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name").exists());

        verifyNoInteractions(service);
    }

    // ---------- DELETE ----------

    @Test
    void eliminaUnaColaYResponde204() throws Exception {
        mockMvc.perform(delete("/api/mq/queues/q.vieja").with(admin()))
                .andExpect(status().isNoContent());

        verify(service).deleteQueue("q.vieja");
    }

    @Test
    void eliminarUnaColaInexistenteResponde404() throws Exception {
        doThrow(new ResourceNotFoundException("La cola 'nope' no existe")).when(service).deleteQueue("nope");

        mockMvc.perform(delete("/api/mq/queues/nope").with(admin()))
                .andExpect(status().isNotFound());
    }

    @Test
    void vaciaUnaColaSinEliminarlaYInformaCuantosMensajesBorro() throws Exception {
        when(service.purgeQueue("q.cmd.email")).thenReturn(new PurgeResponse("q.cmd.email", 12));

        mockMvc.perform(delete("/api/mq/queues/q.cmd.email/messages").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.queue").value("q.cmd.email"))
                .andExpect(jsonPath("$.purgedMessages").value(12));

        verify(service, never()).deleteQueue(any());
    }

    // ---------- Seguridad ----------

    @Test
    void sinTokenResponde401() throws Exception {
        mockMvc.perform(get("/api/mq/queues/q.cmd.email"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(service);
    }

    @Test
    void unTecnicoNoPuedeAdministrarElBroker() throws Exception {
        mockMvc.perform(post("/api/mq/queues")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_TECNICO")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"q.a\"}"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(service);
    }

    @Test
    void unTokenSinRolesTampocoPuede() throws Exception {
        mockMvc.perform(delete("/api/mq/queues/q.cmd.email").with(jwt()))
                .andExpect(status().isForbidden());

        verify(service, never()).deleteQueue(eq("q.cmd.email"));
    }

    // ---------- Errores del cliente que detecta Spring: no son 500 ----------

    @Test
    void unTipoDeContenidoQueNoEsJsonDevuelve415EnVezDe500() throws Exception {
        mockMvc.perform(post("/api/mq/queues").with(admin()).contentType(MediaType.TEXT_PLAIN).content("name=q.a"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.status").value(415))
                .andExpect(jsonPath("$.message").value("Tipo de contenido no soportado: la API recibe application/json"));

        verifyNoInteractions(service);
    }

    @Test
    void unVerboNoPermitidoDevuelve405EnVezDe500() throws Exception {
        mockMvc.perform(put("/api/mq/queues").with(admin()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.status").value(405));

        verifyNoInteractions(service);
    }

    @Test
    void unaRutaInexistenteDevuelve404EnVezDe500() throws Exception {
        mockMvc.perform(get("/api/mq/no-existe").with(admin()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Ruta no encontrada"));
    }
}
