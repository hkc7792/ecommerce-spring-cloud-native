# Stage 1: Build
FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /app

# 1. Copy the Parent POM
COPY pom.xml .

# 2. REPEAT: Copy ALL module POMs
# Even though we are building 'order-service',
# Maven needs to 'see' all modules mentioned in the parent pom.xml
COPY inventory-service/pom.xml ./inventory-service/
COPY order-service/pom.xml ./order-service/
# If you have a common-library, copy its pom too:
# COPY common-library/pom.xml ./common-library/

# 3. Cache dependencies (optional but recommended)
RUN mvn dependency:go-offline -B -pl order-service -am

# 4. Copy ONLY the source code for order-service
COPY order-service/src ./order-service/src
# IMPORTANT: If order-service depends on a common-library,
# you MUST copy the common-library/src too!

# 5. Build the service
RUN mvn clean package -pl order-service -am -DskipTests

# Stage 2: Runtime
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /app/order-service/target/order-service-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]