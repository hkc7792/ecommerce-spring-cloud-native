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

RUN mvn dependency:go-offline -B -pl notification-service -am

# 4. Copy ONLY the source code for notification-service
COPY commons/src ./commons/src
COPY notification-service/src ./notification-service/src
# IMPORTANT: If notification-service depends on a common-library,
# you MUST copy the common-library/src too!

# 5. Build the service
RUN mvn clean package -pl notification-service -am -DskipTests

# Stage 2: Runtime
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /app/notification-service/target/notification-service-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
