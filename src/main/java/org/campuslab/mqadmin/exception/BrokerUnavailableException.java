package org.campuslab.mqadmin.exception;

/** No se pudo hablar con RabbitMQ (caído, sin red o credenciales rechazadas). Se responde 503. */
public class BrokerUnavailableException extends RuntimeException {

    public BrokerUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
