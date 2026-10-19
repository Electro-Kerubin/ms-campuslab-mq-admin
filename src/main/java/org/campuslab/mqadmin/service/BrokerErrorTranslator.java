package org.campuslab.mqadmin.service;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Method;
import com.rabbitmq.client.ShutdownSignalException;
import org.campuslab.mqadmin.exception.BrokerOperationException;
import org.campuslab.mqadmin.exception.BrokerUnavailableException;
import org.campuslab.mqadmin.exception.ResourceConflictException;
import org.campuslab.mqadmin.exception.ResourceNotFoundException;
import org.springframework.amqp.AmqpAuthenticationException;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.AmqpTimeoutException;

/**
 * Traduce los errores del cliente AMQP a las excepciones de dominio del servicio.
 *
 * Cuando RabbitMQ rechaza una operación cierra el canal con un código de respuesta AMQP
 * (404 no existe, 406 precondición fallida, 405 recurso bloqueado...). Ese código viaja
 * dentro de la cadena de causas de la excepción de Spring, por eso se busca ahí.
 */
final class BrokerErrorTranslator {

    private static final int NOT_FOUND = 404;
    private static final int RESOURCE_LOCKED = 405;
    private static final int PRECONDITION_FAILED = 406;

    private BrokerErrorTranslator() {
    }

    /** Respuesta de cierre del broker: código AMQP, texto y si fue un cierre de CONEXIÓN (no de canal). */
    record BrokerReply(int code, String text, boolean connectionLevel) {
    }

    static RuntimeException translate(AmqpException ex) {
        BrokerReply reply = findBrokerReply(ex);

        if (reply != null && !reply.connectionLevel()) {
            return switch (reply.code()) {
                case NOT_FOUND -> new ResourceNotFoundException(reply.text());
                case PRECONDITION_FAILED, RESOURCE_LOCKED ->
                        new ResourceConflictException("RabbitMQ rechazó la operación porque choca con el recurso existente: "
                                + reply.text());
                default -> new BrokerOperationException("RabbitMQ rechazó la operación: " + reply.text(), ex);
            };
        }
        if (reply != null || isConnectionProblem(ex)) {
            return new BrokerUnavailableException("RabbitMQ no está disponible", ex);
        }
        return new BrokerOperationException("Error al comunicarse con RabbitMQ: " + ex.getMessage(), ex);
    }

    /** Busca en la cadena de causas el cierre de canal/conexión con el que RabbitMQ explica el rechazo. */
    static BrokerReply findBrokerReply(Throwable throwable) {
        for (Throwable cause = throwable; cause != null; cause = cause.getCause()) {
            if (cause instanceof ShutdownSignalException signal) {
                Method reason = signal.getReason();
                if (reason instanceof AMQP.Channel.Close close) {
                    return new BrokerReply(close.getReplyCode(), close.getReplyText(), false);
                }
                if (reason instanceof AMQP.Connection.Close close) {
                    return new BrokerReply(close.getReplyCode(), close.getReplyText(), true);
                }
            }
            if (cause.getCause() == cause) {
                break;
            }
        }
        return null;
    }

    private static boolean isConnectionProblem(AmqpException ex) {
        return ex instanceof AmqpConnectException
                || ex instanceof AmqpAuthenticationException
                || ex instanceof AmqpTimeoutException;
    }
}
