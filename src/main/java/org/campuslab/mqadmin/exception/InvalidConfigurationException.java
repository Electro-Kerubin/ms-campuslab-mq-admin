package org.campuslab.mqadmin.exception;

/**
 * La petición está bien formada pero su configuración no es válida para el broker
 * (por ejemplo, un dead letter exchange que no existe). Se responde 422.
 */
public class InvalidConfigurationException extends RuntimeException {

    public InvalidConfigurationException(String message) {
        super(message);
    }
}
