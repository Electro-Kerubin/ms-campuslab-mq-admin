package org.campuslab.mqadmin.exception;

/** El recurso ya existe, o existe con otra configuración que el broker no deja redeclarar. Se responde 409. */
public class ResourceConflictException extends RuntimeException {

    public ResourceConflictException(String message) {
        super(message);
    }
}
