package org.campuslab.mqadmin.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Exchange recién creado. */
@Schema(description = "Exchange creado")
public record ExchangeResponse(String name, ExchangeType type, boolean durable, boolean autoDelete) {
}
