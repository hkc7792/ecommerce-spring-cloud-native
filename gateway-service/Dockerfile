# Stage 1: Build
FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /app

# 1. Copy the Parent POM
COPY pom.xml .
COPY commons/pom.xml ./commons/
COPY inventory-service/pom.xml ./inventory-service/
COPY notification-service/pom.xml ./notification-service/
COPY order-service/pom.xml ./order-service/
COPY payment-service/pom.xml ./payment-service/
COPY user-service/pom.xml ./user-service/
COPY gateway-service/pom.xml ./gateway-service/

RUN mvn dependency:go-offline -B -pl gateway-service -am

# 4. Copy ONLY the source code for gateway-service
# gateway-service is routing-only: it does NOT depend on common-library,
# so no commons/src copy is required.
COPY gateway-service/src ./gateway-service/src

# 5. Build the service
RUN mvn clean package -pl gateway-service -am -DskipTests

# Stage 2: Runtime
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /app/gateway-service/target/gateway-service-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
