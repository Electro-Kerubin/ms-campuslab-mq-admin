package org.campuslab.mqadmin.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.campuslab.mqadmin.dto.ExchangeResponse;
import org.campuslab.mqadmin.dto.ExchangeType;
import org.campuslab.mqadmin.exception.ResourceConflictException;
import org.campuslab.mqadmin.exception.ResourceNotFoundException;
import org.campuslab.mqadmin.security.SecurityConfig;
import org.campuslab.mqadmin.service.RabbitAdminService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(ExchangeController.class)
@Import({SecurityConfig.class, FakeJwtDecoderConfig.class})
class ExchangeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RabbitAdminService service;

    private static RequestPostProcessor admin() {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    @Test
    void creaUnExchangeYResponde201ConLocation() throws Exception {
        when(service.createExchange(any())).thenReturn(new ExchangeResponse("cmd.direct", ExchangeType.DIRECT, true, false));

        mockMvc.perform(post("/api/mq/exchanges").with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"cmd.direct\",\"type\":\"DIRECT\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/mq/exchanges/cmd.direct"))
                .andExpect(jsonPath("$.type").value("DIRECT"))
                .andExpect(jsonPath("$.durable").value(true));
    }

    @Test
    void elTipoEsObligatorio() throws Exception {
        mockMvc.perform(post("/api/mq/exchanges").with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"cmd.direct\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.type").exists());

        verifyNoInteractions(service);
    }

    @Test
    void unTipoDeExchangeDesconocidoSeRechaza() throws Exception {
        mockMvc.perform(post("/api/mq/exchanges").with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"cmd.x\",\"type\":\"INVENTADO\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void noSePuedeCrearUnExchangeConElPrefijoReservadoAmq() throws Exception {
        mockMvc.perform(post("/api/mq/exchanges").with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"amq.mio\",\"type\":\"DIRECT\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name").exists());
    }

    @Test
    void unExchangeQueYaExisteResponde409() throws Exception {
        when(service.createExchange(any())).thenThrow(new ResourceConflictException("El exchange 'cmd.direct' ya existe"));

        mockMvc.perform(post("/api/mq/exchanges").with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"cmd.direct\",\"type\":\"DIRECT\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void eliminaUnExchangeYResponde204() throws Exception {
        mockMvc.perform(delete("/api/mq/exchanges/cmd.viejo").with(admin()))
                .andExpect(status().isNoContent());

        verify(service).deleteExchange("cmd.viejo");
    }

    @Test
    void eliminarUnExchangeInexistenteResponde404() throws Exception {
        doThrow(new ResourceNotFoundException("El exchange 'nope' no existe")).when(service).deleteExchange("nope");

        mockMvc.perform(delete("/api/mq/exchanges/nope").with(admin()))
                .andExpect(status().isNotFound());
    }

    @Test
    void losExchangesDeFabricaNoSePuedenEliminar() throws Exception {
        mockMvc.perform(delete("/api/mq/exchanges/amq.direct").with(admin()))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }

    @Test
    void soloElAdminPuedeAdministrarExchanges() throws Exception {
        mockMvc.perform(delete("/api/mq/exchanges/cmd.direct")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ESTUDIANTE"))))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/mq/exchanges/cmd.direct"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(service);
    }
}
