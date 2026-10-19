package org.campuslab.mqadmin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Identifica un binding (cola enlazada a un exchange con una routing key). Se usa en el cuerpo
 * al crearlo y como parámetros de la URL al eliminarlo. Cola y exchange deben existir.
 */
@Schema(description = "Binding entre un exchange y una cola")
public record BindingRequest(

        @Schema(description = "Cola destino", example = "q.cmd.email")
        @NotBlank(message = "La cola es obligatoria")
        @Size(max = NamePatterns.MAX_LENGTH, message = "El nombre de la cola no puede superar los 255 caracteres")
        @Pattern(regexp = NamePatterns.EXISTING_RESOURCE_NAME,
                message = "queue solo puede tener letras, números y . _ : -")
        String queue,

        @Schema(description = "Exchange origen (no se puede enlazar al exchange por defecto)", example = "cmd.direct")
        @NotBlank(message = "El exchange es obligatorio")
        @Size(max = NamePatterns.MAX_LENGTH, message = "El nombre del exchange no puede superar los 255 caracteres")
        @Pattern(regexp = NamePatterns.EXISTING_RESOURCE_NAME,
                message = "exchange solo puede tener letras, números y . _ : -")
        String exchange,

        @Schema(description = "Routing key; vacía para exchange fanout. Admite * y # en exchange topic",
                example = "email.send", defaultValue = "")
        @Size(max = NamePatterns.MAX_LENGTH, message = "routingKey no puede superar los 255 caracteres")
        @Pattern(regexp = NamePatterns.ROUTING_KEY,
                message = "routingKey solo puede tener letras, números y . _ : - * #")
        String routingKey
) {

    /** Una routing key ausente equivale a vacía (fanout). */
    public BindingRequest {
        routingKey = routingKey == null ? "" : routingKey;
    }
}
