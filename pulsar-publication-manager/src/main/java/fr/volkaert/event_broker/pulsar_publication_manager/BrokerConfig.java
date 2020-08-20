package fr.volkaert.event_broker.pulsar_publication_manager;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "broker")
@Data
public class BrokerConfig {

    private String catalogUrl;
    private String pulsarServiceUrl;

    private String componentTypeName;   // Useful for metrics (to group them by component type)
    private String componentInstanceId; // Useful for metrics (to distinguish instances of the same component type)

    private long defaultTimeToLiveInSeconds;
    private long maxTimeToLiveInSeconds;
}
