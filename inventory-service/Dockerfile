# Stage 1: Build stage
FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /app

# 1. Copy the Root POM
COPY pom.xml .
COPY commons/pom.xml ./commons/
COPY inventory-service/pom.xml ./inventory-service/
COPY order-service/pom.xml ./order-service/
COPY payment-service/pom.xml ./payment-service/
COPY user-service/pom.xml ./user-service/

RUN mvn dependency:go-offline -B -pl inventory-service -am

# 4. Copy ONLY the source code for the service you are building
COPY commons/src ./commons/src
COPY inventory-service/src ./inventory-service/src

# 5. Build the specific service
RUN mvn clean package -pl inventory-service -am -DskipTests

# Stage 2: Runtime stage
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /app/inventory-service/target/inventory-service-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
