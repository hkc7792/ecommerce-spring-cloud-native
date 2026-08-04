package com.ecommerce.order.config;

import com.ecommerce.commons.client.InventoryClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

@Configuration
public class ClientConfig {

    @Value("${inventory.service.url:http://inventory-service:8080}")
    private String inventoryServiceUrl;

    @Bean
    public InventoryClient inventoryClient(RestClient.Builder restClientBuilder) {
        RestClient restClient = restClientBuilder.baseUrl(inventoryServiceUrl).build();
        RestClientAdapter adapter = RestClientAdapter.create(restClient);
        HttpServiceProxyFactory factory = HttpServiceProxyFactory.builderFor(adapter).build();
        return factory.createClient(InventoryClient.class);
    }
}
