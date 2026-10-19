package org.campuslab.mqadmin.controller;

import java.net.URI;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.campuslab.mqadmin.dto.ExchangeRequest;
import org.campuslab.mqadmin.dto.ExchangeResponse;
import org.campuslab.mqadmin.dto.NamePatterns;
import org.campuslab.mqadmin.service.RabbitAdminService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * Recurso "exchanges". Solo delega en {@link RabbitAdminService}: no conoce RabbitMQ.
 *
 * <pre>
 *  POST   /api/mq/exchanges           crea un exchange
 *  DELETE /api/mq/exchanges/{name}    elimina el exchange
 * </pre>
 *
 * No hay GET: por AMQP, RabbitMQ no permite listar ni describir exchanges
 * (eso requeriría su API de management).
 */
@RestController
@RequestMapping("/api/mq/exchanges")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Exchanges", description = "Crear y eliminar exchanges de RabbitMQ")
public class ExchangeController {

    private final RabbitAdminService adminService;

    public ExchangeController(RabbitAdminService adminService) {
        this.adminService = adminService;
    }

    @PostMapping
    @Operation(summary = "Crear un exchange", description = "Nombre y tipo (DIRECT, TOPIC, FANOUT, HEADERS) son obligatorios.")
    @ApiResponse(responseCode = "201", description = "Exchange creado")
    @ApiResponse(responseCode = "400", description = "Datos inválidos (nombre vacío, tipo desconocido...)")
    @ApiResponse(responseCode = "409", description = "El exchange ya existe")
    public ResponseEntity<ExchangeResponse> createExchange(@Valid @RequestBody ExchangeRequest request) {
        ExchangeResponse created = adminService.createExchange(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{name}").build(created.name());
        return ResponseEntity.created(location).body(created);
    }

    @DeleteMapping("/{name}")
    @Operation(summary = "Eliminar un exchange", description = "No se pueden eliminar los exchanges de fábrica (amq.*).")
    @ApiResponse(responseCode = "204", description = "Exchange eliminado")
    @ApiResponse(responseCode = "400", description = "Nombre inválido o reservado")
    @ApiResponse(responseCode = "404", description = "El exchange no existe")
    public ResponseEntity<Void> deleteExchange(@PathVariable @Pattern(regexp = NamePatterns.NEW_RESOURCE_NAME,
            message = "Nombre de exchange inválido o reservado (los exchanges amq.* no se pueden eliminar)") String name) {
        adminService.deleteExchange(name);
        return ResponseEntity.noContent().build();
    }
}
