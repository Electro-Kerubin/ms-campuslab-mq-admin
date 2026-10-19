package org.campuslab.mqadmin.controller;

import java.net.URI;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.campuslab.mqadmin.dto.NamePatterns;
import org.campuslab.mqadmin.dto.PurgeResponse;
import org.campuslab.mqadmin.dto.QueueInfoResponse;
import org.campuslab.mqadmin.dto.QueueRequest;
import org.campuslab.mqadmin.dto.QueueResponse;
import org.campuslab.mqadmin.service.RabbitAdminService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * Recurso "colas". Solo delega en {@link RabbitAdminService}: no conoce RabbitMQ.
 *
 * <pre>
 *  POST   /api/mq/queues                 crea una cola
 *  GET    /api/mq/queues/{name}          estado (mensajes y consumidores)
 *  DELETE /api/mq/queues/{name}          elimina la cola
 *  DELETE /api/mq/queues/{name}/messages vacía la cola (sin eliminarla)
 * </pre>
 */
@RestController
@RequestMapping("/api/mq/queues")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Colas", description = "Crear, consultar, vaciar y eliminar colas de RabbitMQ")
public class QueueController {

    private final RabbitAdminService adminService;

    public QueueController(RabbitAdminService adminService) {
        this.adminService = adminService;
    }

    @PostMapping
    @Operation(summary = "Crear una cola",
            description = "Solo el nombre es obligatorio. Si se indica deadLetterExchange, ese exchange debe existir.")
    @ApiResponse(responseCode = "201", description = "Cola creada")
    @ApiResponse(responseCode = "400", description = "Datos inválidos (nombre vacío o con caracteres no permitidos, TTL negativo...)")
    @ApiResponse(responseCode = "409", description = "La cola ya existe")
    @ApiResponse(responseCode = "422", description = "El dead letter exchange no existe")
    public ResponseEntity<QueueResponse> createQueue(@Valid @RequestBody QueueRequest request) {
        QueueResponse created = adminService.createQueue(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{name}").build(created.name());
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping("/{name}")
    @Operation(summary = "Consultar el estado de una cola")
    @ApiResponse(responseCode = "200", description = "Mensajes pendientes y consumidores conectados")
    @ApiResponse(responseCode = "404", description = "La cola no existe")
    public QueueInfoResponse getQueue(@PathVariable @Pattern(regexp = NamePatterns.EXISTING_RESOURCE_NAME,
            message = "Nombre de cola inválido") String name) {
        return adminService.getQueue(name);
    }

    @DeleteMapping("/{name}")
    @Operation(summary = "Eliminar una cola", description = "Elimina la cola y todos los mensajes que contenga.")
    @ApiResponse(responseCode = "204", description = "Cola eliminada")
    @ApiResponse(responseCode = "404", description = "La cola no existe")
    public ResponseEntity<Void> deleteQueue(@PathVariable @Pattern(regexp = NamePatterns.EXISTING_RESOURCE_NAME,
            message = "Nombre de cola inválido") String name) {
        adminService.deleteQueue(name);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{name}/messages")
    @Operation(summary = "Vaciar una cola", description = "Borra todos los mensajes pendientes pero conserva la cola.")
    @ApiResponse(responseCode = "200", description = "Cantidad de mensajes eliminados")
    @ApiResponse(responseCode = "404", description = "La cola no existe")
    public PurgeResponse purgeQueue(@PathVariable @Pattern(regexp = NamePatterns.EXISTING_RESOURCE_NAME,
            message = "Nombre de cola inválido") String name) {
        return adminService.purgeQueue(name);
    }
}
