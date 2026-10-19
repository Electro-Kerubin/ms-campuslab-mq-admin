package org.campuslab.mqadmin.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Resultado de vaciar una cola. */
@Schema(description = "Resultado de purgar una cola")
public record PurgeResponse(String queue, @Schema(description = "Mensajes eliminados") int purgedMessages) {
}
