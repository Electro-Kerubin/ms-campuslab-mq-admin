package org.campuslab.mqadmin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * MQ Admin Service Application
 *
 * Microservicio administrador de RabbitMQ.
 *
 * Responsabilidades:
 * - Crear, consultar y eliminar colas, exchanges y bindings por API REST (/api/mq/*)
 * - Purgar los mensajes de una cola
 * - Validar la entrada antes de tocar el broker (nombres, TTL, dead letter, tipos)
 *
 * No consume ni publica mensajes de negocio, y solo lo opera el rol ADMIN.
 */
@SpringBootApplication
public class MqAdminServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(MqAdminServiceApplication.class, args);
    }

}
