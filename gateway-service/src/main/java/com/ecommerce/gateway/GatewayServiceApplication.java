package com.ecommerce.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring Cloud Gateway entry point.
 *
 * <p>Single edge that exposes the whole platform to the frontend on port 8080:
 * routes {@code /api/*} to the backing microservices over the docker network
 * and {@code /ws} to the notification-service STOMP hub.</p>
 */
@SpringBootApplication(scanBasePackages = {"com.ecommerce"})
public class GatewayServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayServiceApplication.class, args);
    }
}
