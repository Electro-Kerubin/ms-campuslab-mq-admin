package org.campuslab.mqadmin.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Binding creado. */
@Schema(description = "Binding creado")
public record BindingResponse(String queue, String exchange, String routingKey) {
}
