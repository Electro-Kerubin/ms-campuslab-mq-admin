package org.campuslab.mqadmin.dto;

/** Tipos de exchange de RabbitMQ (ver skill rabbitmq-fundamentos-mensajeria). */
public enum ExchangeType {
    /** Routing key exacta. */
    DIRECT,
    /** Routing key con comodines * (una palabra) y # (cero o más). */
    TOPIC,
    /** Copia a todas las colas enlazadas, ignora la routing key. */
    FANOUT,
    /** Enruta por headers del mensaje en vez de la routing key. */
    HEADERS
}
