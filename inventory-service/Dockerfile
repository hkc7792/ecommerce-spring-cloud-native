# Stage 1: Build the application
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# Copy the parent pom from the root (needed for dependency versions)
COPY pom.xml ./
# Copy the specific service code
COPY inventory-service/pom.xml ./inventory-service/
COPY inventory-service/src ./inventory-service/src

# Build only the inventory service
RUN mvn clean package -pl inventory-service -am -DskipTests

# Stage 2: Run the application
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/inventory-service/target/*.jar app.jar

EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]