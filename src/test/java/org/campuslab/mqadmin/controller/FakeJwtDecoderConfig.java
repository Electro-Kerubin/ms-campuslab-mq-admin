package org.campuslab.mqadmin.controller;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.jwt.JwtDecoder;

/**
 * La SecurityConfig exige un JwtDecoder para armar la cadena de filtros. En estos tests nunca se
 * invoca de verdad (jwt() de spring-security-test inyecta la autenticación directamente), así que
 * basta con un decoder que falle si alguien lo llama.
 */
@TestConfiguration
class FakeJwtDecoderConfig {

    @Bean
    JwtDecoder jwtDecoder() {
        return token -> {
            throw new IllegalStateException("Los tests deben autenticarse con jwt()");
        };
    }
}
