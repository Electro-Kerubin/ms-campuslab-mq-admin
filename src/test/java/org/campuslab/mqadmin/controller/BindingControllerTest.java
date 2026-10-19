package org.campuslab.mqadmin.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.campuslab.mqadmin.dto.BindingRequest;
import org.campuslab.mqadmin.dto.BindingResponse;
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

@WebMvcTest(BindingController.class)
@Import({SecurityConfig.class, FakeJwtDecoderConfig.class})
class BindingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RabbitAdminService service;

    private static RequestPostProcessor admin() {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    @Test
    void creaUnBindingYResponde201() throws Exception {
        when(service.createBinding(any())).thenReturn(new BindingResponse("q.cmd.email", "cmd.direct", "email.send"));

        mockMvc.perform(post("/api/mq/bindings").with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"queue\":\"q.cmd.email\",\"exchange\":\"cmd.direct\",\"routingKey\":\"email.send\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.queue").value("q.cmd.email"))
                .andExpect(jsonPath("$.exchange").value("cmd.direct"))
                .andExpect(jsonPath("$.routingKey").value("email.send"));
    }

    @Test
    void unBindingSinColaNiExchangeSeRechaza() throws Exception {
        mockMvc.perform(post("/api/mq/bindings").with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"routingKey\":\"email.send\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.queue").exists())
                .andExpect(jsonPath("$.errors.exchange").exists());

        verifyNoInteractions(service);
    }

    @Test
    void unaRoutingKeyConCaracteresNoPermitidosSeRechaza() throws Exception {
        mockMvc.perform(post("/api/mq/bindings").with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"queue\":\"q.a\",\"exchange\":\"x.b\",\"routingKey\":\"con espacio/barra\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.routingKey").exists());
    }

    @Test
    void laRoutingKeyEsOpcionalParaExchangesFanout() throws Exception {
        when(service.createBinding(any())).thenReturn(new BindingResponse("q.a", "x.fan", ""));

        mockMvc.perform(post("/api/mq/bindings").with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"queue\":\"q.a\",\"exchange\":\"x.fan\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void enlazarAUnaColaInexistenteResponde404() throws Exception {
        when(service.createBinding(any())).thenThrow(new ResourceNotFoundException("La cola 'q.a' no existe"));

        mockMvc.perform(post("/api/mq/bindings").with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"queue\":\"q.a\",\"exchange\":\"x.b\",\"routingKey\":\"k\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void eliminaUnBindingIdentificadoPorParametrosDeLaUrl() throws Exception {
        mockMvc.perform(delete("/api/mq/bindings").with(admin())
                        .param("queue", "q.cmd.email").param("exchange", "cmd.direct").param("routingKey", "email.send"))
                .andExpect(status().isNoContent());

        verify(service).deleteBinding(new BindingRequest("q.cmd.email", "cmd.direct", "email.send"));
    }

    @Test
    void eliminarUnBindingSinIndicarColaYExchangeSeRechaza() throws Exception {
        mockMvc.perform(delete("/api/mq/bindings").with(admin()).param("routingKey", "email.send"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.queue").exists())
                .andExpect(jsonPath("$.errors.exchange").exists());

        verifyNoInteractions(service);
    }

    @Test
    void eliminarUnBindingInexistenteResponde404() throws Exception {
        doThrow(new ResourceNotFoundException("no binding")).when(service).deleteBinding(any());

        mockMvc.perform(delete("/api/mq/bindings").with(admin())
                        .param("queue", "q.a").param("exchange", "x.b").param("routingKey", "k"))
                .andExpect(status().isNotFound());
    }

    @Test
    void soloElAdminPuedeAdministrarBindings() throws Exception {
        mockMvc.perform(post("/api/mq/bindings")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_AUDITOR")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"queue\":\"q.a\",\"exchange\":\"x.b\"}"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(service);
    }
}
