package fr.volkaert.event_broker.standard_publication_adapter;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "broker")
@Data
public class BrokerConfig {

    private String componentTypeName;   // Useful for metrics (to group them by component type)
    private String componentInstanceId; // Useful for metrics (to distinguish instances of the same component type)

    private long connectTimeoutInSecondsForPublicationManager;
    private long readTimeoutInSecondsForPublicationManager;

    private String publicationManagerUrl;
    private String authClientIdForPublicationManager;
    private String authClientSecretForPublicationManager;
}
