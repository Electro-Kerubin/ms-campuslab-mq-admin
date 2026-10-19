package org.campuslab.mqadmin.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Cola recién creada, con la configuración con la que quedó declarada. */
@Schema(description = "Cola creada")
public record QueueResponse(
        String name,
        boolean durable,
        boolean autoDelete,
        QueueType type,
        Integer messageTtlMs,
        Integer maxLength,
        String deadLetterExchange,
        String deadLetterRoutingKey
) {
}
