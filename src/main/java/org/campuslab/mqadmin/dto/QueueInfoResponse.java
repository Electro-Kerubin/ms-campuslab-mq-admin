package org.campuslab.mqadmin.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Estado en vivo de una cola existente (RabbitMQ solo informa estos dos contadores por AMQP). */
@Schema(description = "Estado actual de una cola")
public record QueueInfoResponse(
        String name,
        @Schema(description = "Mensajes listos para ser entregados") int messageCount,
        @Schema(description = "Consumidores conectados") int consumerCount
) {
}
