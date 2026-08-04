# Stage 1: Build stage
FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /app

COPY pom.xml .
COPY commons/pom.xml ./commons/
COPY inventory-service/pom.xml ./inventory-service/
COPY order-service/pom.xml ./order-service/
COPY payment-service/pom.xml ./payment-service/
COPY user-service/pom.xml ./user-service/

RUN mvn dependency:go-offline -B -pl payment-service -am

COPY commons/src ./commons/src
COPY payment-service/src ./payment-service/src

RUN mvn clean package -pl payment-service -am -DskipTests

# Stage 2: Runtime stage
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /app/payment-service/target/payment-service-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]

