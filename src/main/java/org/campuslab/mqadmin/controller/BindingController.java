package org.campuslab.mqadmin.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.campuslab.mqadmin.dto.BindingRequest;
import org.campuslab.mqadmin.dto.BindingResponse;
import org.campuslab.mqadmin.service.RabbitAdminService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Recurso "bindings" (enlace exchange -> cola con una routing key). Solo delega en
 * {@link RabbitAdminService}: no conoce RabbitMQ.
 *
 * <pre>
 *  POST   /api/mq/bindings                                 crea el binding (cuerpo JSON)
 *  DELETE /api/mq/bindings?queue=&exchange=&routingKey=    elimina el binding (parámetros en la URL)
 * </pre>
 *
 * Un binding no tiene id propio: queda identificado por la terna cola + exchange + routing key,
 * por eso al eliminarlo se envía en la URL (algunos clientes y proxies descartan el cuerpo de un DELETE).
 */
@RestController
@RequestMapping("/api/mq/bindings")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Bindings", description = "Enlazar y desenlazar colas de exchanges")
public class BindingController {

    private final RabbitAdminService adminService;

    public BindingController(RabbitAdminService adminService) {
        this.adminService = adminService;
    }

    @PostMapping
    @Operation(summary = "Crear un binding", description = "La cola y el exchange deben existir. Es idempotente.")
    @ApiResponse(responseCode = "201", description = "Binding creado")
    @ApiResponse(responseCode = "400", description = "Datos inválidos (cola o exchange vacíos, routing key con caracteres no permitidos...)")
    @ApiResponse(responseCode = "404", description = "La cola o el exchange no existen")
    public ResponseEntity<BindingResponse> createBinding(@Valid @RequestBody BindingRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(adminService.createBinding(request));
    }

    @DeleteMapping
    @Operation(summary = "Eliminar un binding",
            description = "Se identifica por queue + exchange + routingKey en la URL. Es idempotente: "
                    + "si el binding ya no existe, RabbitMQ lo da por eliminado.")
    @ApiResponse(responseCode = "204", description = "Binding eliminado (o ya no existía)")
    @ApiResponse(responseCode = "400", description = "Faltan parámetros o son inválidos")
    @ApiResponse(responseCode = "404", description = "La cola o el exchange no existen")
    public ResponseEntity<Void> deleteBinding(@Valid BindingRequest request) {
        adminService.deleteBinding(request);
        return ResponseEntity.noContent().build();
    }
}
