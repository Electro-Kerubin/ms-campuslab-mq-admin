package org.campuslab.mqadmin.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Criterio de la rúbrica: la API no deja crear colas con nombres vacíos ni configuraciones
 * inválidas. Aquí se prueba cada regla de los DTO de entrada, sin levantar Spring.
 */
class RequestValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    // ---------- Nombre de la cola ----------

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "   "})
    void elNombreVacioONuloSeRechaza(String name) {
        assertThat(invalidFields(queue(name))).contains("name");
    }

    @ParameterizedTest
    @ValueSource(strings = {"mi cola", "cola/con/barra", "cola?", "ñandú", "-empieza-con-guion", ".punto-inicial",
            "amq.reservado", "amq.gen-abc", "tab\tulador", "emoji😀"})
    void losNombresConCaracteresNoPermitidosOReservadosSeRechazan(String name) {
        assertThat(invalidFields(queue(name))).contains("name");
    }

    @ParameterizedTest
    @ValueSource(strings = {"q", "q.cmd.email", "q.cmd.email.dlq", "Orders_Queue-1", "cmd:email", "a1", "9cola"})
    void losNombresValidosSeAceptan(String name) {
        assertThat(invalidFields(queue(name))).isEmpty();
    }

    @Test
    void elNombreNoPuedeSuperar255Caracteres() {
        assertThat(invalidFields(queue("a".repeat(255)))).isEmpty();
        assertThat(invalidFields(queue("a".repeat(256)))).contains("name");
    }

    // ---------- Configuración de la cola ----------

    @Test
    void elTtlYElTopeDeLargoDebenSerPositivos() {
        assertThat(invalidFields(new QueueRequest("q", null, null, null, 0, 0, null, null)))
                .contains("messageTtlMs", "maxLength");
        assertThat(invalidFields(new QueueRequest("q", null, null, null, -1, -1, null, null)))
                .contains("messageTtlMs", "maxLength");
        assertThat(invalidFields(new QueueRequest("q", null, null, null, 1, 1, null, null))).isEmpty();
    }

    @Test
    void laRoutingKeyDeDeadLetterSoloTieneSentidoConSuExchange() {
        assertThat(invalidFields(new QueueRequest("q", null, null, null, null, null, null, "dlq")))
                .contains("deadLetterRoutingKeyWithExchange");
        assertThat(invalidFields(new QueueRequest("q", null, null, null, null, null, "cmd.dead.dlx", "dlq"))).isEmpty();
        assertThat(invalidFields(new QueueRequest("q", null, null, null, null, null, "cmd.dead.dlx", null))).isEmpty();
    }

    @Test
    void elDeadLetterExchangePuedeSerUnoDeFabricaPeroNoTenerCaracteresInvalidos() {
        assertThat(invalidFields(new QueueRequest("q", null, null, null, null, null, "amq.direct", null))).isEmpty();
        assertThat(invalidFields(new QueueRequest("q", null, null, null, null, null, "mal exchange", null)))
                .contains("deadLetterExchange");
        assertThat(invalidFields(new QueueRequest("q", null, null, null, null, null, "", null)))
                .contains("deadLetterExchange");
    }

    @Test
    void unaColaQuorumDebeSerDurableYNoAutoDelete() {
        assertThat(invalidFields(new QueueRequest("q", true, false, QueueType.QUORUM, null, null, null, null))).isEmpty();
        assertThat(invalidFields(new QueueRequest("q", false, false, QueueType.QUORUM, null, null, null, null)))
                .contains("quorumDurableAndPermanent");
        assertThat(invalidFields(new QueueRequest("q", true, true, QueueType.QUORUM, null, null, null, null)))
                .contains("quorumDurableAndPermanent");
        // las clásicas sí pueden ser temporales
        assertThat(invalidFields(new QueueRequest("q", false, true, QueueType.CLASSIC, null, null, null, null))).isEmpty();
    }

    @Test
    void losValoresPorDefectoSonSeguros() {
        QueueRequest request = queue("q.a");

        assertThat(request.durable()).isTrue();
        assertThat(request.autoDelete()).isFalse();
        assertThat(request.type()).isEqualTo(QueueType.CLASSIC);
    }

    // ---------- Exchange ----------

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "amq.direct", "con espacio"})
    void elNombreDelExchangeSeValidaIgualQueElDeLaCola(String name) {
        assertThat(invalidFields(new ExchangeRequest(name, ExchangeType.DIRECT, null, null))).contains("name");
    }

    @Test
    void elTipoDeExchangeEsObligatorio() {
        assertThat(invalidFields(new ExchangeRequest("cmd.direct", null, null, null))).contains("type");
        assertThat(invalidFields(new ExchangeRequest("cmd.direct", ExchangeType.TOPIC, null, null))).isEmpty();
    }

    // ---------- Binding ----------

    @Test
    void elBindingExigeColaYExchange() {
        assertThat(invalidFields(new BindingRequest(null, null, "k"))).contains("queue", "exchange");
        assertThat(invalidFields(new BindingRequest(" ", " ", "k"))).contains("queue", "exchange");
        assertThat(invalidFields(new BindingRequest("q.a", "cmd.direct", "email.send"))).isEmpty();
    }

    @Test
    void laRoutingKeyAdmiteComodinesPeroNoEspaciosNiBarras() {
        assertThat(invalidFields(new BindingRequest("q", "x", "email.*"))).isEmpty();
        assertThat(invalidFields(new BindingRequest("q", "x", "prep.#"))).isEmpty();
        assertThat(invalidFields(new BindingRequest("q", "x", ""))).isEmpty();
        assertThat(invalidFields(new BindingRequest("q", "x", null))).isEmpty();   // fanout
        assertThat(invalidFields(new BindingRequest("q", "x", "con espacio"))).contains("routingKey");
        assertThat(invalidFields(new BindingRequest("q", "x", "a/b"))).contains("routingKey");
    }

    @Test
    void sePuedeEnlazarAUnExchangeDeFabrica() {
        assertThat(invalidFields(new BindingRequest("q.a", "amq.topic", "k"))).isEmpty();
    }

    // ---------- helpers ----------

    private static QueueRequest queue(String name) {
        return new QueueRequest(name, null, null, null, null, null, null, null);
    }

    private <T> List<String> invalidFields(T request) {
        Set<ConstraintViolation<T>> violations = validator.validate(request);
        return violations.stream().map(v -> v.getPropertyPath().toString()).collect(Collectors.toList());
    }
}
