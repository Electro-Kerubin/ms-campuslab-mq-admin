package org.campuslab.mqadmin.security;

import java.util.Collection;
import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Configuración de seguridad del microservicio (mismo esquema que bookings y catalog).
 *
 * - Valida el JWT de Azure AD (issuer, firma, expiración) vía issuer-uri en application.yml.
 * - Convierte el claim "roles" del token en roles de Spring (ROLE_ADMIN...).
 * - Sin sesiones: cada request debe traer su Bearer token.
 * - Administrar el broker es una operación sensible: los controladores exigen el rol ADMIN.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity // activa los @PreAuthorize de los controladores
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        // La documentación de la API es pública (describe rutas, no expone datos del broker)
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        // La API de administración requiere estar autenticado (el rol ADMIN lo exige @PreAuthorize)
                        .requestMatchers("/api/mq/**").authenticated()
                        .anyRequest().denyAll())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthConverter())))
                .csrf(csrf -> csrf.disable()); // sin cookies de sesión, CSRF no aplica

        return http.build();
    }

    /**
     * Azure AD entrega los roles en el claim "roles" (ej: ["ADMIN","TECNICO"]).
     * Este converter los transforma en ROLE_ADMIN, ROLE_TECNICO, etc.
     * para que @PreAuthorize("hasRole('ADMIN')") funcione.
     */
    private JwtAuthenticationConverter jwtAuthConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            Collection<String> roles = jwt.getClaimAsStringList("roles");
            if (roles == null) return List.of();

            return roles.stream()
                    .map((String rol) -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + rol.toUpperCase()))
                    .toList();
        });
        return converter;
    }
}
