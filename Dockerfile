# Imagen del microservicio administrador de RabbitMQ
# Build:  docker build -t ms-campuslab-mq-admin:latest .
# Run:    docker run -p 8085:8085 -e RABBITMQ_HOST=rabbitmq ms-campuslab-mq-admin:latest

# 1) Compilamos con Maven y JDK 21
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
# Descarga dependencias primero para aprovechar la caché de capas
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn clean package -DskipTests -B

# 2) Imagen final liviana: solo el JRE y el jar
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar

ENV SERVER_PORT=8085

EXPOSE 8085
ENTRYPOINT ["java", "-jar", "app.jar"]
