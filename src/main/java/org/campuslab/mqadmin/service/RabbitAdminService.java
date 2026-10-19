package org.campuslab.mqadmin.service;

import java.util.function.Supplier;

import org.campuslab.mqadmin.dto.BindingRequest;
import org.campuslab.mqadmin.dto.BindingResponse;
import org.campuslab.mqadmin.dto.ExchangeRequest;
import org.campuslab.mqadmin.dto.ExchangeResponse;
import org.campuslab.mqadmin.dto.PurgeResponse;
import org.campuslab.mqadmin.dto.QueueInfoResponse;
import org.campuslab.mqadmin.dto.QueueRequest;
import org.campuslab.mqadmin.dto.QueueResponse;
import org.campuslab.mqadmin.dto.QueueType;
import org.campuslab.mqadmin.exception.InvalidConfigurationException;
import org.campuslab.mqadmin.exception.ResourceConflictException;
import org.campuslab.mqadmin.exception.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Binding.DestinationType;
import org.springframework.amqp.core.Exchange;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.QueueInformation;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

/**
 * Administra colas, exchanges y bindings de RabbitMQ. Es el único lugar del microservicio
 * que conoce {@link AmqpAdmin}: los controladores solo llaman a estos métodos de alto nivel y
 * reciben DTO o excepciones de dominio, nunca tipos ni errores de RabbitMQ.
 *
 * Qué comprueba antes de hablar con el broker (para dar errores claros en vez de un
 * "canal cerrado"): que el recurso no exista al crearlo (409), que exista al consultarlo,
 * borrarlo o enlazarlo (404) y que el dead letter exchange exista (422).
 */
@Service
public class RabbitAdminService {

    private static final Logger log = LoggerFactory.getLogger(RabbitAdminService.class);

    private final AmqpAdmin amqpAdmin;
    private final RabbitTemplate rabbitTemplate;

    public RabbitAdminService(AmqpAdmin amqpAdmin, RabbitTemplate rabbitTemplate) {
        this.amqpAdmin = amqpAdmin;
        this.rabbitTemplate = rabbitTemplate;
    }

    // ---------- Colas ----------

    public QueueResponse createQueue(QueueRequest request) {
        return execute(() -> {
            if (queueExists(request.name())) {
                throw new ResourceConflictException("La cola '" + request.name() + "' ya existe");
            }
            String deadLetterExchange = request.deadLetterExchange();
            if (deadLetterExchange != null && !exchangeExists(deadLetterExchange)) {
                throw new InvalidConfigurationException(
                        "El dead letter exchange '" + deadLetterExchange + "' no existe: créalo antes que la cola");
            }

            amqpAdmin.declareQueue(toQueue(request));
            log.info("Cola creada: {} (tipo={}, durable={})", request.name(), request.type(), request.durable());
            return new QueueResponse(request.name(), request.durable(), request.autoDelete(), request.type(),
                    request.messageTtlMs(), request.maxLength(), deadLetterExchange, request.deadLetterRoutingKey());
        });
    }

    public QueueInfoResponse getQueue(String name) {
        return execute(() -> {
            QueueInformation info = requireQueue(name);
            return new QueueInfoResponse(info.getName(), info.getMessageCount(), info.getConsumerCount());
        });
    }

    public void deleteQueue(String name) {
        execute(() -> {
            requireQueue(name);
            amqpAdmin.deleteQueue(name);
            log.info("Cola eliminada: {}", name);
            return null;
        });
    }

    /** Vacía la cola sin eliminarla. Devuelve cuántos mensajes se borraron. */
    public PurgeResponse purgeQueue(String name) {
        return execute(() -> {
            requireQueue(name);
            int purged = amqpAdmin.purgeQueue(name);
            log.info("Cola purgada: {} ({} mensajes)", name, purged);
            return new PurgeResponse(name, purged);
        });
    }

    // ---------- Exchanges ----------

    public ExchangeResponse createExchange(ExchangeRequest request) {
        return execute(() -> {
            if (exchangeExists(request.name())) {
                throw new ResourceConflictException("El exchange '" + request.name() + "' ya existe");
            }

            amqpAdmin.declareExchange(toExchange(request));
            log.info("Exchange creado: {} (tipo={})", request.name(), request.type());
            return new ExchangeResponse(request.name(), request.type(), request.durable(), request.autoDelete());
        });
    }

    public void deleteExchange(String name) {
        execute(() -> {
            requireExchange(name);
            amqpAdmin.deleteExchange(name);
            log.info("Exchange eliminado: {}", name);
            return null;
        });
    }

    // ---------- Bindings ----------

    /** Es idempotente: enlazar dos veces lo mismo no falla ni duplica el binding. */
    public BindingResponse createBinding(BindingRequest request) {
        return execute(() -> {
            requireQueue(request.queue());
            requireExchange(request.exchange());

            amqpAdmin.declareBinding(toBinding(request));
            log.info("Binding creado: {} -> {} ({})", request.exchange(), request.queue(), request.routingKey());
            return new BindingResponse(request.queue(), request.exchange(), request.routingKey());
        });
    }

    public void deleteBinding(BindingRequest request) {
        execute(() -> {
            requireQueue(request.queue());
            requireExchange(request.exchange());

            amqpAdmin.removeBinding(toBinding(request));
            log.info("Binding eliminado: {} -> {} ({})", request.exchange(), request.queue(), request.routingKey());
            return null;
        });
    }

    // ---------- Existencia ----------

    // getQueueInfo devuelve null si la cola no existe
    private boolean queueExists(String name) {
        return amqpAdmin.getQueueInfo(name) != null;
    }

    private QueueInformation requireQueue(String name) {
        QueueInformation info = amqpAdmin.getQueueInfo(name);
        if (info == null) {
            throw new ResourceNotFoundException("La cola '" + name + "' no existe");
        }
        return info;
    }

    private void requireExchange(String name) {
        if (!exchangeExists(name)) {
            throw new ResourceNotFoundException("El exchange '" + name + "' no existe");
        }
    }

    /**
     * AmqpAdmin no puede consultar exchanges, así que se hace una declaración "pasiva":
     * RabbitMQ responde OK si existe y cierra el canal con 404 si no (eso no es un error aquí).
     */
    private boolean exchangeExists(String name) {
        try {
            rabbitTemplate.execute(channel -> channel.exchangeDeclarePassive(name));
            return true;
        } catch (AmqpException ex) {
            BrokerErrorTranslator.BrokerReply reply = BrokerErrorTranslator.findBrokerReply(ex);
            if (reply != null && !reply.connectionLevel() && reply.code() == 404) {
                return false;
            }
            throw ex;
        }
    }

    // ---------- Construcción de objetos de Spring AMQP ----------

    // maxLength(int) está deprecado, pero se mantiene a propósito: RabbitMQ compara también el
    // TIPO de cada argumento al redeclarar, y notify/bookings declaran sus colas con enteros;
    // si este servicio recrea una de ellas con long, esos servicios recibirían PRECONDITION_FAILED.
    @SuppressWarnings("deprecation")
    private Queue toQueue(QueueRequest request) {
        QueueBuilder builder = Boolean.TRUE.equals(request.durable())
                ? QueueBuilder.durable(request.name())
                : QueueBuilder.nonDurable(request.name());

        if (Boolean.TRUE.equals(request.autoDelete())) {
            builder.autoDelete();
        }
        if (request.type() == QueueType.QUORUM) {
            builder.quorum();
        }
        if (request.messageTtlMs() != null) {
            builder.ttl(request.messageTtlMs());
        }
        if (request.maxLength() != null) {
            builder.maxLength(request.maxLength());
        }
        if (request.deadLetterExchange() != null) {
            builder.deadLetterExchange(request.deadLetterExchange());
        }
        if (request.deadLetterRoutingKey() != null) {
            builder.deadLetterRoutingKey(request.deadLetterRoutingKey());
        }
        return builder.build();
    }

    private Exchange toExchange(ExchangeRequest request) {
        ExchangeBuilder builder = switch (request.type()) {
            case DIRECT -> ExchangeBuilder.directExchange(request.name());
            case TOPIC -> ExchangeBuilder.topicExchange(request.name());
            case FANOUT -> ExchangeBuilder.fanoutExchange(request.name());
            case HEADERS -> ExchangeBuilder.headersExchange(request.name());
        };
        builder.durable(Boolean.TRUE.equals(request.durable()));
        if (Boolean.TRUE.equals(request.autoDelete())) {
            builder.autoDelete();
        }
        return builder.build();
    }

    private Binding toBinding(BindingRequest request) {
        return new Binding(request.queue(), DestinationType.QUEUE, request.exchange(), request.routingKey(), null);
    }

    // ---------- Errores del broker ----------

    /** Ejecuta la operación y traduce cualquier error del cliente AMQP a una excepción de dominio. */
    private <T> T execute(Supplier<T> operation) {
        try {
            return operation.get();
        } catch (AmqpException ex) {
            throw BrokerErrorTranslator.translate(ex);
        }
    }
}
