package org.campuslab.mqadmin.dto;

/** Expresiones regulares de validación de nombres, compartidas por todos los DTO de entrada. */
public final class NamePatterns {

    private NamePatterns() {
    }

    /**
     * Nombre de una cola o exchange NUEVO: empieza con letra o número, solo letras, números y . _ : -
     * y NO puede usar el prefijo "amq." (reservado por RabbitMQ, el broker rechaza declararlo).
     */
    public static final String NEW_RESOURCE_NAME = "^(?!amq\\.)[A-Za-z0-9][A-Za-z0-9._:-]*$";

    /** Nombre de un recurso que ya existe: igual, pero permite los de fábrica (amq.direct, amq.topic...). */
    public static final String EXISTING_RESOURCE_NAME = "^[A-Za-z0-9][A-Za-z0-9._:-]*$";

    /** Routing key: puede ser vacía (fanout) y admite los comodines * y # de los exchange topic. */
    public static final String ROUTING_KEY = "^[A-Za-z0-9._*#:-]*$";

    /** RabbitMQ limita los nombres y routing keys a 255 bytes. */
    public static final int MAX_LENGTH = 255;
}
