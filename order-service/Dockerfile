# Stage 1: Build stage using Maven and Java 17
FROM maven:3.8.4-openjdk-17-slim AS builder
WORKDIR /build

# 1. Copy the Parent POM from the root context
COPY pom.xml ./

# 2. Copy the order-service module files
COPY order-service/pom.xml ./order-service/
COPY order-service/src ./order-service/src

# 3. Build the order-service specifically
# -pl: project list, -am: also make dependencies
RUN mvn clean package -pl order-service -am -DskipTests

# Stage 2: Runtime stage using a lightweight JRE
FROM openjdk:17-jdk-slim
WORKDIR /app

# Copy only the built JAR from the builder stage
# Note: Ensure 'order-service' matches the artifactId in your pom.xml
COPY --from=builder /build/order-service/target/order-service-*.jar app.jar

# Expose the port defined in your application.yml
EXPOSE 8082

# Start the application
ENTRYPOINT ["java", "-jar", "app.jar"]