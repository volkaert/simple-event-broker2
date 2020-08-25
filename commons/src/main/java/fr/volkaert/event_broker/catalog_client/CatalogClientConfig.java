package fr.volkaert.event_broker.catalog_client;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class CatalogClientConfig {

    @Value("${broker.auth-client-id-for-catalog}")
    String authClientIdForCatalog;

    @Value("${broker.auth-client-secret-for-catalog}")
    String authClientSecretForCatalog;

    @Bean
    @Qualifier("RestTemplateForCatalogClient")
    @LoadBalanced
    public RestTemplate restTemplateForCatalogClient(RestTemplateBuilder builder) {
        return builder
                .basicAuthentication(authClientIdForCatalog, authClientSecretForCatalog)
                .build();
    }
}
