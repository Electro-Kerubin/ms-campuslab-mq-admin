package org.campuslab.mqadmin.exception;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

/**
 * Convierte las excepciones en respuestas JSON con el mismo formato que el resto de los
 * microservicios: {"timestamp", "status", "message"} y, en validaciones, "errors" por campo.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // ---------- Validación de la entrada -> 400 ----------

    // Cuerpo JSON (@RequestBody @Valid) o parámetros de la URL (@Valid sobre un record)
    @ExceptionHandler(BindException.class)
    public ResponseEntity<Map<String, Object>> handleBind(BindException ex) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(e -> errors.merge(e.getField(), String.valueOf(e.getDefaultMessage()), GlobalExceptionHandler::join));
        ex.getBindingResult().getGlobalErrors()
                .forEach(e -> errors.merge(e.getObjectName(), String.valueOf(e.getDefaultMessage()), GlobalExceptionHandler::join));
        return validationError(errors);
    }

    // Variables de ruta con restricciones (@PathVariable @Pattern ...)
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<Map<String, Object>> handleMethodValidation(HandlerMethodValidationException ex) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getAllValidationResults().forEach(result -> {
            String parameter = result.getMethodParameter().getParameterName();
            result.getResolvableErrors().forEach(error ->
                    errors.merge(parameter == null ? "request" : parameter,
                            String.valueOf(error.getDefaultMessage()), GlobalExceptionHandler::join));
        });
        return validationError(errors);
    }

    // JSON mal formado, o valor fuera de un enum (type: "XYZ")
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleUnreadable(HttpMessageNotReadableException ex) {
        return build(HttpStatus.BAD_REQUEST,
                "El cuerpo de la petición no es un JSON válido o tiene un valor no permitido (por ejemplo un tipo desconocido)");
    }

    // ---------- Reglas del recurso ----------

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(ResourceNotFoundException ex) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(ResourceConflictException.class)
    public ResponseEntity<Map<String, Object>> handleConflict(ResourceConflictException ex) {
        return build(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(InvalidConfigurationException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidConfiguration(InvalidConfigurationException ex) {
        return build(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
    }

    // ---------- Broker ----------

    @ExceptionHandler(BrokerUnavailableException.class)
    public ResponseEntity<Map<String, Object>> handleUnavailable(BrokerUnavailableException ex) {
        log.error("RabbitMQ no disponible", ex);
        return build(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage());
    }

    @ExceptionHandler(BrokerOperationException.class)
    public ResponseEntity<Map<String, Object>> handleBrokerOperation(BrokerOperationException ex) {
        log.error("RabbitMQ rechazó la operación", ex);
        return build(HttpStatus.BAD_GATEWAY, ex.getMessage());
    }

    // ---------- Seguridad ----------

    // @PreAuthorize rechazó el rol -> 403 (sin este handler caería en el 500 genérico)
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDenied(AccessDeniedException ex) {
        return build(HttpStatus.FORBIDDEN, "No tienes permisos para administrar RabbitMQ");
    }

    // ---------- Cualquier otro error ----------

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneric(Exception ex) {
        log.error("Error inesperado", ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno del servidor");
    }

    private ResponseEntity<Map<String, Object>> validationError(Map<String, String> errors) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", LocalDateTime.now());
        body.put("status", HttpStatus.BAD_REQUEST.value());
        body.put("message", "La petición tiene datos inválidos");
        body.put("errors", errors);
        return ResponseEntity.badRequest().body(body);
    }

    private ResponseEntity<Map<String, Object>> build(HttpStatus status, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", LocalDateTime.now());
        body.put("status", status.value());
        body.put("message", message);
        return ResponseEntity.status(status).body(body);
    }

    // Un campo con varias reglas rotas (p. ej. vacío y con formato inválido) muestra todos los mensajes
    private static String join(String first, String second) {
        return first.equals(second) ? first : first + "; " + second;
    }
}
