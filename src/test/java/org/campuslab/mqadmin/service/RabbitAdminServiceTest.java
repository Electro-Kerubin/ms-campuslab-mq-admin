package org.campuslab.mqadmin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;

import com.rabbitmq.client.ShutdownSignalException;
import com.rabbitmq.client.impl.AMQImpl;
import org.campuslab.mqadmin.dto.BindingRequest;
import org.campuslab.mqadmin.dto.ExchangeRequest;
import org.campuslab.mqadmin.dto.ExchangeType;
import org.campuslab.mqadmin.dto.PurgeResponse;
import org.campuslab.mqadmin.dto.QueueInfoResponse;
import org.campuslab.mqadmin.dto.QueueRequest;
import org.campuslab.mqadmin.dto.QueueResponse;
import org.campuslab.mqadmin.dto.QueueType;
import org.campuslab.mqadmin.exception.BrokerOperationException;
import org.campuslab.mqadmin.exception.BrokerUnavailableException;
import org.campuslab.mqadmin.exception.InvalidConfigurationException;
import org.campuslab.mqadmin.exception.ResourceConflictException;
import org.campuslab.mqadmin.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.AmqpIOException;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Binding.DestinationType;
import org.springframework.amqp.core.Exchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueInformation;
import org.springframework.amqp.rabbit.core.ChannelCallback;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

/** Toda la lógica de administración del broker, con AmqpAdmin y RabbitTemplate simulados (sin RabbitMQ). */
class RabbitAdminServiceTest {

    private final AmqpAdmin amqpAdmin = mock(AmqpAdmin.class);
    private final RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
    private RabbitAdminService service;

    @BeforeEach
    void setUp() {
        service = new RabbitAdminService(amqpAdmin, rabbitTemplate);
    }

    // ---------- Colas ----------

    @Test
    void creaUnaColaDurableConSusArgumentosDeRetencionYDeadLetter() {
        exchangeExists();

        QueueResponse created = service.createQueue(new QueueRequest(
                "q.cmd.email", null, null, null, 30000, 1000, "cmd.dead.dlx", "email.dlq"));

        Queue declared = declaredQueue();
        assertThat(declared.getName()).isEqualTo("q.cmd.email");
        assertThat(declared.isDurable()).isTrue();
        assertThat(declared.isAutoDelete()).isFalse();
        assertThat(declared.getArguments())
                .containsEntry("x-message-ttl", 30000)
                .containsEntry("x-max-length", 1000)
                .containsEntry("x-dead-letter-exchange", "cmd.dead.dlx")
                .containsEntry("x-dead-letter-routing-key", "email.dlq")
                .doesNotContainKey("x-queue-type");
        assertThat(created).isEqualTo(new QueueResponse("q.cmd.email", true, false, QueueType.CLASSIC,
                30000, 1000, "cmd.dead.dlx", "email.dlq"));
    }

    @Test
    void unaColaSimpleNoLlevaArgumentosExtra() {
        service.createQueue(new QueueRequest("q.simple", null, null, null, null, null, null, null));

        assertThat(declaredQueue().getArguments()).isEmpty();
    }

    @Test
    void unaColaQuorumSeDeclaraConSuTipo() {
        service.createQueue(new QueueRequest("q.ha", true, false, QueueType.QUORUM, null, null, null, null));

        assertThat(declaredQueue().getArguments()).containsEntry("x-queue-type", "quorum");
    }

    @Test
    void unaColaNoDurableYAutoDeleteSeRespeta() {
        service.createQueue(new QueueRequest("q.tmp", false, true, null, null, null, null, null));

        Queue declared = declaredQueue();
        assertThat(declared.isDurable()).isFalse();
        assertThat(declared.isAutoDelete()).isTrue();
    }

    @Test
    void noRecreaUnaColaQueYaExiste() {
        when(amqpAdmin.getQueueInfo("q.cmd.email")).thenReturn(new QueueInformation("q.cmd.email", 0, 0));

        assertThatThrownBy(() -> service.createQueue(simpleQueue("q.cmd.email")))
                .isInstanceOf(ResourceConflictException.class)
                .hasMessageContaining("q.cmd.email");
        verify(amqpAdmin, never()).declareQueue(any());
    }

    @Test
    void rechazaUnDeadLetterExchangeQueNoExiste() {
        exchangeMissing("no.existe");

        assertThatThrownBy(() -> service.createQueue(
                new QueueRequest("q.a", null, null, null, null, null, "no.existe", null)))
                .isInstanceOf(InvalidConfigurationException.class)
                .hasMessageContaining("no.existe");
        verify(amqpAdmin, never()).declareQueue(any());
    }

    @Test
    void consultaElEstadoDeUnaCola() {
        when(amqpAdmin.getQueueInfo("q.cmd.email")).thenReturn(new QueueInformation("q.cmd.email", 4, 2));

        assertThat(service.getQueue("q.cmd.email")).isEqualTo(new QueueInfoResponse("q.cmd.email", 4, 2));
    }

    @Test
    void consultarUnaColaInexistenteEsUn404() {
        assertThatThrownBy(() -> service.getQueue("nope")).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void eliminaUnaColaExistente() {
        queueExists("q.vieja");

        service.deleteQueue("q.vieja");

        verify(amqpAdmin).deleteQueue("q.vieja");
    }

    @Test
    void eliminarUnaColaInexistenteEsUn404SinTocarElBroker() {
        assertThatThrownBy(() -> service.deleteQueue("nope")).isInstanceOf(ResourceNotFoundException.class);
        verify(amqpAdmin, never()).deleteQueue(any());
    }

    @Test
    void purgaUnaColaYDevuelveCuantosMensajesBorro() {
        queueExists("q.cmd.email");
        when(amqpAdmin.purgeQueue("q.cmd.email")).thenReturn(7);

        assertThat(service.purgeQueue("q.cmd.email")).isEqualTo(new PurgeResponse("q.cmd.email", 7));
    }

    @Test
    void purgarUnaColaInexistenteEsUn404() {
        assertThatThrownBy(() -> service.purgeQueue("nope")).isInstanceOf(ResourceNotFoundException.class);
    }

    // ---------- Exchanges ----------

    @Test
    void creaExchangesDeCadaTipo() {
        exchangeMissing("x.nuevo");
        for (ExchangeType type : ExchangeType.values()) {
            service.createExchange(new ExchangeRequest("x." + type.name().toLowerCase(), type, null, null));
        }

        ArgumentCaptor<Exchange> declared = ArgumentCaptor.forClass(Exchange.class);
        verify(amqpAdmin, org.mockito.Mockito.times(4)).declareExchange(declared.capture());
        assertThat(declared.getAllValues()).extracting(Exchange::getType)
                .containsExactly("direct", "topic", "fanout", "headers");
        assertThat(declared.getAllValues()).allSatisfy(e -> {
            assertThat(e.isDurable()).isTrue();
            assertThat(e.isAutoDelete()).isFalse();
        });
    }

    @Test
    void noRecreaUnExchangeQueYaExiste() {
        exchangeExists();

        assertThatThrownBy(() -> service.createExchange(new ExchangeRequest("cmd.direct", ExchangeType.DIRECT, null, null)))
                .isInstanceOf(ResourceConflictException.class);
        verify(amqpAdmin, never()).declareExchange(any());
    }

    @Test
    void eliminaUnExchangeExistente() {
        exchangeExists();

        service.deleteExchange("cmd.viejo");

        verify(amqpAdmin).deleteExchange("cmd.viejo");
    }

    @Test
    void eliminarUnExchangeInexistenteEsUn404() {
        exchangeMissing("nope");

        assertThatThrownBy(() -> service.deleteExchange("nope")).isInstanceOf(ResourceNotFoundException.class);
        verify(amqpAdmin, never()).deleteExchange(any());
    }

    // ---------- Bindings ----------

    @Test
    void creaUnBindingEntreUnaColaYUnExchange() {
        queueExists("q.cmd.email");
        exchangeExists();

        service.createBinding(new BindingRequest("q.cmd.email", "cmd.direct", "email.send"));

        ArgumentCaptor<Binding> declared = ArgumentCaptor.forClass(Binding.class);
        verify(amqpAdmin).declareBinding(declared.capture());
        assertThat(declared.getValue().getDestination()).isEqualTo("q.cmd.email");
        assertThat(declared.getValue().getDestinationType()).isEqualTo(DestinationType.QUEUE);
        assertThat(declared.getValue().getExchange()).isEqualTo("cmd.direct");
        assertThat(declared.getValue().getRoutingKey()).isEqualTo("email.send");
    }

    @Test
    void noEnlazaSiLaColaNoExiste() {
        exchangeExists();

        assertThatThrownBy(() -> service.createBinding(new BindingRequest("nope", "cmd.direct", "k")))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("cola");
        verify(amqpAdmin, never()).declareBinding(any());
    }

    @Test
    void noEnlazaSiElExchangeNoExiste() {
        queueExists("q.a");
        exchangeMissing("nope");

        assertThatThrownBy(() -> service.createBinding(new BindingRequest("q.a", "nope", "k")))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("exchange");
        verify(amqpAdmin, never()).declareBinding(any());
    }

    @Test
    void eliminaUnBinding() {
        queueExists("q.cmd.email");
        exchangeExists();

        service.deleteBinding(new BindingRequest("q.cmd.email", "cmd.direct", "email.send"));

        ArgumentCaptor<Binding> removed = ArgumentCaptor.forClass(Binding.class);
        verify(amqpAdmin).removeBinding(removed.capture());
        assertThat(removed.getValue().getRoutingKey()).isEqualTo("email.send");
    }

    // ---------- Errores del broker -> excepciones de dominio ----------

    @Test
    void siElBrokerRechazaPorPrecondicionFallidaEsUnConflicto() {
        doOnDeclareQueueThrow(channelClosed(406, "PRECONDITION_FAILED - inequivalent arg 'x-message-ttl'"));

        assertThatThrownBy(() -> service.createQueue(simpleQueue("q.a")))
                .isInstanceOf(ResourceConflictException.class)
                .hasMessageContaining("inequivalent arg");
    }

    // Carrera: la cola existía al comprobarla pero otro cliente la borró antes de la operación
    @Test
    void siElBrokerDiceQueNoExisteEsUn404() {
        queueExists("q.a");
        exchangeExists();
        org.mockito.Mockito.doThrow(channelClosed(404, "NOT_FOUND - no queue 'q.a' in vhost '/'"))
                .when(amqpAdmin).removeBinding(any());

        assertThatThrownBy(() -> service.deleteBinding(new BindingRequest("q.a", "x.b", "k")))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("no queue");
    }

    @Test
    void siElBrokerRechazaPorOtroMotivoEsUn502() {
        doOnDeclareQueueThrow(channelClosed(403, "ACCESS_REFUSED - access to queue 'q.a' refused"));

        assertThatThrownBy(() -> service.createQueue(simpleQueue("q.a")))
                .isInstanceOf(BrokerOperationException.class)
                .hasMessageContaining("ACCESS_REFUSED");
    }

    @Test
    void siNoHayConexionConElBrokerEsUn503() {
        when(amqpAdmin.getQueueInfo("q.a")).thenThrow(new AmqpConnectException(new java.net.ConnectException("refused")));

        assertThatThrownBy(() -> service.getQueue("q.a")).isInstanceOf(BrokerUnavailableException.class);
    }

    @Test
    void siElBrokerCierraLaConexionEsUn503() {
        AmqpException closed = new AmqpIOException(new IOException(new ShutdownSignalException(
                true, false, new AMQImpl.Connection.Close(320, "CONNECTION_FORCED", 0, 0), null)));
        when(amqpAdmin.getQueueInfo("q.a")).thenThrow(closed);

        assertThatThrownBy(() -> service.getQueue("q.a")).isInstanceOf(BrokerUnavailableException.class);
    }

    @Test
    void siFallaLaVerificacionDeExistenciaDeUnExchangeNoSeConfundeConQueNoExiste() {
        when(rabbitTemplate.execute(any(ChannelCallback.class)))
                .thenThrow(new AmqpConnectException(new java.net.ConnectException("refused")));

        assertThatThrownBy(() -> service.deleteExchange("cmd.direct")).isInstanceOf(BrokerUnavailableException.class);
    }

    // ---------- helpers ----------

    private static QueueRequest simpleQueue(String name) {
        return new QueueRequest(name, null, null, null, null, null, null, null);
    }

    private Queue declaredQueue() {
        ArgumentCaptor<Queue> declared = ArgumentCaptor.forClass(Queue.class);
        verify(amqpAdmin).declareQueue(declared.capture());
        return declared.getValue();
    }

    private void queueExists(String name) {
        when(amqpAdmin.getQueueInfo(name)).thenReturn(new QueueInformation(name, 0, 0));
    }

    // la declaración pasiva responde OK cuando el exchange existe (el lambda oculta el nombre, no se puede filtrar por él)...
    private void exchangeExists() {
        when(rabbitTemplate.execute(any(ChannelCallback.class))).thenReturn(null);
    }

    // ...y RabbitMQ cierra el canal con 404 cuando no existe
    private void exchangeMissing(String name) {
        when(rabbitTemplate.execute(any(ChannelCallback.class)))
                .thenThrow(channelClosed(404, "NOT_FOUND - no exchange '" + name + "' in vhost '/'"));
    }

    private void doOnDeclareQueueThrow(AmqpException exception) {
        org.mockito.Mockito.doThrow(exception).when(amqpAdmin).declareQueue(any());
    }

    /** El error tal como lo arma el cliente AMQP: IOException con el cierre de canal del broker como causa. */
    private static AmqpException channelClosed(int code, String text) {
        return new AmqpIOException(new IOException(new ShutdownSignalException(
                false, false, new AMQImpl.Channel.Close(code, text, 0, 0), null)));
    }
}
