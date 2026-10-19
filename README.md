# ms-campuslab-mq-admin

Microservicio **administrador de RabbitMQ** de CampusLab. Expone una API REST para crear, consultar y eliminar **colas, exchanges y bindings** sin entrar a la Management UI. No consume ni publica mensajes de negocio.

- Spring Boot 3.3.3 · Java 21 · Spring AMQP (`AmqpAdmin`)
- Seguridad: JWT de Azure AD, **solo rol `ADMIN`** (igual que `bookings` y `catalog`)
- Documentación interactiva: **http://localhost:8085/swagger-ui.html** (OpenAPI en `/v3/api-docs`)

## Estructura

```
controller/   QueueController · ExchangeController · BindingController   (REST, no conocen RabbitMQ)
service/      RabbitAdminService                                        (único que usa AmqpAdmin)
              BrokerErrorTranslator                                     (errores AMQP -> excepciones de dominio)
dto/          QueueRequest · ExchangeRequest · BindingRequest · *Response (validación con Bean Validation)
exception/    ResourceNotFound · ResourceConflict · InvalidConfiguration · BrokerUnavailable · BrokerOperation
              GlobalExceptionHandler                                    (formato de error único)
security/     SecurityConfig                                            (JWT + roles)
```

## Endpoints

Base: `/api/mq` · todos requieren `Authorization: Bearer <JWT>` con el rol `ADMIN`.

| Verbo | Ruta | Qué hace | Éxito | Errores |
|---|---|---|---|---|
| `POST` | `/queues` | Crea una cola | `201` + `Location` | `400` datos inválidos · `409` ya existe · `422` DLX inexistente |
| `GET` | `/queues/{name}` | Mensajes pendientes y consumidores | `200` | `404` |
| `DELETE` | `/queues/{name}` | Elimina la cola | `204` | `404` |
| `DELETE` | `/queues/{name}/messages` | Vacía la cola sin eliminarla | `200` (`purgedMessages`) | `404` |
| `POST` | `/exchanges` | Crea un exchange (`DIRECT`, `TOPIC`, `FANOUT`, `HEADERS`) | `201` + `Location` | `400` · `409` |
| `DELETE` | `/exchanges/{name}` | Elimina el exchange (no los `amq.*`) | `204` | `400` · `404` |
| `POST` | `/bindings` | Enlaza cola ↔ exchange con una routing key (idempotente) | `201` | `400` · `404` cola/exchange inexistente |
| `DELETE` | `/bindings?queue=&exchange=&routingKey=` | Elimina el binding (idempotente: si ya no existe, también `204`) | `204` | `400` · `404` cola/exchange inexistente |

Sin token `401`; con token sin rol `ADMIN` `403`; RabbitMQ caído `503`.

> Por AMQP RabbitMQ **no permite listar** colas ni exchanges ni leer su configuración (solo el estado de una cola concreta). Para eso existe la Management UI (`:15672`).

### Ejemplos

```bash
TOKEN=<JWT con rol ADMIN>
H="Authorization: Bearer $TOKEN"

# Crear la cola principal con TTL y dead letter (el exchange de DLX debe existir antes)
curl -X POST localhost:8085/api/mq/exchanges -H "$H" -H 'Content-Type: application/json' \
     -d '{"name":"cmd.dead.dlx","type":"DIRECT"}'
curl -X POST localhost:8085/api/mq/queues -H "$H" -H 'Content-Type: application/json' \
     -d '{"name":"q.cmd.email","messageTtlMs":30000,"maxLength":1000,
          "deadLetterExchange":"cmd.dead.dlx","deadLetterRoutingKey":"email.dlq"}'

# Enlazarla, consultarla y vaciarla
curl -X POST localhost:8085/api/mq/bindings -H "$H" -H 'Content-Type: application/json' \
     -d '{"queue":"q.cmd.email","exchange":"cmd.direct","routingKey":"email.send"}'
curl localhost:8085/api/mq/queues/q.cmd.email -H "$H"
curl -X DELETE localhost:8085/api/mq/queues/q.cmd.email/messages -H "$H"

# Desenlazar y eliminar
curl -X DELETE "localhost:8085/api/mq/bindings?queue=q.cmd.email&exchange=cmd.direct&routingKey=email.send" -H "$H"
curl -X DELETE localhost:8085/api/mq/queues/q.cmd.email -H "$H"
```

## Validación de la entrada

Nada llega al broker sin pasar por estas reglas (respuesta `400` con el detalle por campo):

| Campo | Regla |
|---|---|
| `name` (cola/exchange) | Obligatorio, ≤ 255, solo letras, números y `. _ : -`, empieza con letra o número, **no** puede usar el prefijo reservado `amq.` |
| `messageTtlMs`, `maxLength` | Si se envían, mayores que 0 |
| `deadLetterExchange` | Solo caracteres válidos, y **debe existir** (si no, `422`) |
| `deadLetterRoutingKey` | Solo se acepta junto con `deadLetterExchange` |
| `type` (cola) | `CLASSIC` (por defecto) o `QUORUM`; una `QUORUM` debe ser durable y no `autoDelete` |
| `type` (exchange) | Obligatorio: `DIRECT`, `TOPIC`, `FANOUT` o `HEADERS` |
| `queue`, `exchange` (binding) | Obligatorios; la routing key admite `*` y `#` y puede ir vacía (fanout) |

Valores por defecto seguros: cola durable, sin `autoDelete`, clásica, sin TTL ni tope.

Formato de error (igual al del resto de los microservicios):

```json
{ "timestamp": "2026-10-18T20:00:00", "status": 400, "message": "La petición tiene datos inválidos",
  "errors": { "name": "El nombre de la cola es obligatorio" } }
```

## Ejecutar

Requiere un RabbitMQ accesible (`campuslab-infra/mq`: `docker compose up -d`).

```bash
mvn spring-boot:run
```

| Variable | Por defecto | Uso |
|---|---|---|
| `RABBITMQ_HOST` / `RABBITMQ_PORT` | `localhost` / `5672` | Broker |
| `RABBITMQ_USER` / `RABBITMQ_PASS` | `campuslab` / `campuslab` | Credenciales |
| `SPRING_RABBITMQ_ADDRESSES` | — | Clúster: `localhost:5672,localhost:5673` (reemplaza host/port) |
| `TENANT_ID` | `changeme` | Tenant de Azure AD que emite el JWT |
| `SERVER_PORT` | `8085` | Puerto HTTP |

Docker: `docker build -t ms-campuslab-mq-admin .` y `docker run -p 8085:8085 -e RABBITMQ_HOST=<host> ms-campuslab-mq-admin`.

## Tests

```bash
mvn test
```

Los tests unitarios y de capa web no necesitan broker. `RabbitAdminBrokerTest` prueba la API contra un RabbitMQ real y se omite solo si no hay uno en `localhost:5672` (`docker compose up -d` en `campuslab-infra/mq`).
# ms-campuslab-mq-admin
