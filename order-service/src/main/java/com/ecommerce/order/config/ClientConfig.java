package com.ecommerce.order.config;


import com.ecommerce.commons.client.InventoryClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientHttpServiceGroupConfigurer;
import org.springframework.web.service.registry.ImportHttpServices;
import io.micrometer.observation.ObservationRegistry;

@Configuration
@ImportHttpServices(
        group = "inventory",
        types = InventoryClient.class
)
public class ClientConfig {

    @Bean
    public RestClientHttpServiceGroupConfigurer inventoryGroupConfigurer(
            @Value("${inventory.service.url:http://inventory-service:8080}") String url,
            ObservationRegistry observationRegistry) {

        return groups -> groups.filterByName("inventory")
                .forEachClient((name, builder) -> {
                    builder.baseUrl(url)
                            .observationRegistry(observationRegistry);
                });
    }
}
