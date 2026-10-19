package org.campuslab.mqadmin.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Datos para crear una cola. Solo el nombre es obligatorio; el resto tiene valores por defecto
 * seguros (durable, no autoDelete, clásica, sin TTL ni tope de largo, sin dead letter).
 */
@Schema(description = "Configuración de la cola a crear")
public record QueueRequest(

        @Schema(description = "Nombre de la cola (letras, números y . _ : -; no puede empezar con 'amq.')",
                example = "q.cmd.email")
        @NotBlank(message = "El nombre de la cola es obligatorio")
        @Size(max = NamePatterns.MAX_LENGTH, message = "El nombre de la cola no puede superar los 255 caracteres")
        @Pattern(regexp = NamePatterns.NEW_RESOURCE_NAME,
                message = "El nombre solo puede tener letras, números y . _ : - , empezar con letra o número y no usar el prefijo 'amq.'")
        String name,

        @Schema(description = "Sobrevive al reinicio del broker", defaultValue = "true")
        Boolean durable,

        @Schema(description = "Se elimina cuando se desconecta su último consumidor", defaultValue = "false")
        Boolean autoDelete,

        @Schema(description = "CLASSIC (un nodo) o QUORUM (replicada en el clúster)", defaultValue = "CLASSIC")
        QueueType type,

        @Schema(description = "Tiempo de vida de cada mensaje en milisegundos (x-message-ttl)", example = "30000")
        @Positive(message = "messageTtlMs debe ser mayor que 0")
        Integer messageTtlMs,

        @Schema(description = "Máximo de mensajes en la cola (x-max-length)", example = "1000")
        @Positive(message = "maxLength debe ser mayor que 0")
        Integer maxLength,

        @Schema(description = "Exchange (que debe existir) al que van los mensajes rechazados o expirados", example = "cmd.dead.dlx")
        @Size(max = NamePatterns.MAX_LENGTH, message = "deadLetterExchange no puede superar los 255 caracteres")
        @Pattern(regexp = NamePatterns.EXISTING_RESOURCE_NAME,
                message = "deadLetterExchange solo puede tener letras, números y . _ : -")
        String deadLetterExchange,

        @Schema(description = "Routing key con la que se reenvían los mensajes muertos", example = "email.dlq")
        @Size(max = NamePatterns.MAX_LENGTH, message = "deadLetterRoutingKey no puede superar los 255 caracteres")
        @Pattern(regexp = NamePatterns.ROUTING_KEY,
                message = "deadLetterRoutingKey solo puede tener letras, números y . _ : - * #")
        String deadLetterRoutingKey
) {

    /** Valores por defecto para lo que el cliente no envía. */
    public QueueRequest {
        durable = durable == null ? Boolean.TRUE : durable;
        autoDelete = autoDelete == null ? Boolean.FALSE : autoDelete;
        type = type == null ? QueueType.CLASSIC : type;
    }

    // ---- Reglas entre campos (la validación campo a campo no las puede expresar) ----

    @JsonIgnore
    @Schema(hidden = true)
    @AssertTrue(message = "deadLetterRoutingKey solo se puede indicar junto con deadLetterExchange")
    public boolean isDeadLetterRoutingKeyWithExchange() {
        return deadLetterRoutingKey == null || deadLetterExchange != null;
    }

    @JsonIgnore
    @Schema(hidden = true)
    @AssertTrue(message = "Una cola QUORUM debe ser durable y no puede ser autoDelete")
    public boolean isQuorumDurableAndPermanent() {
        return type != QueueType.QUORUM || (durable && !autoDelete);
    }
}
