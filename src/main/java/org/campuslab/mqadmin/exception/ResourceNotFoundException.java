package org.campuslab.mqadmin.exception;

/** La cola, el exchange o el binding pedido no existe en el broker. Se responde 404. */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
