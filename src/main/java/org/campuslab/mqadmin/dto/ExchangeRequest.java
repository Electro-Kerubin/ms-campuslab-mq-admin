package org.campuslab.mqadmin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Datos para crear un exchange. El nombre y el tipo son obligatorios. */
@Schema(description = "Configuración del exchange a crear")
public record ExchangeRequest(

        @Schema(description = "Nombre del exchange (letras, números y . _ : -; no puede empezar con 'amq.')",
                example = "cmd.direct")
        @NotBlank(message = "El nombre del exchange es obligatorio")
        @Size(max = NamePatterns.MAX_LENGTH, message = "El nombre del exchange no puede superar los 255 caracteres")
        @Pattern(regexp = NamePatterns.NEW_RESOURCE_NAME,
                message = "El nombre solo puede tener letras, números y . _ : - , empezar con letra o número y no usar el prefijo 'amq.'")
        String name,

        @Schema(description = "Tipo de exchange", example = "DIRECT")
        @NotNull(message = "El tipo de exchange es obligatorio (DIRECT, TOPIC, FANOUT o HEADERS)")
        ExchangeType type,

        @Schema(description = "Sobrevive al reinicio del broker", defaultValue = "true")
        Boolean durable,

        @Schema(description = "Se elimina cuando se desenlaza su última cola", defaultValue = "false")
        Boolean autoDelete
) {

    /** Valores por defecto para lo que el cliente no envía. */
    public ExchangeRequest {
        durable = durable == null ? Boolean.TRUE : durable;
        autoDelete = autoDelete == null ? Boolean.FALSE : autoDelete;
    }
}
