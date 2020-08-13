package fr.volkaert.event_broker.standard_subscription_adapter;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "broker")
@Data
public class BrokerConfig {

    private String componentTypeName;   // Useful for metrics (to group them by component type)
    private String componentInstanceId; // Useful for metrics (to distinguish instances of the same component type)

    private long webhookConnectTimeoutInSeconds;
    private long webhookReadTimeoutInSeconds;

    private String oauth2TokenEndpoint;
    private String oauth2ClientId;
    private String oauth2ClientSecret;
    private long oauth2IssuerConnectTimeoutInSeconds;
    private long oauth2IssuerReadTimeoutInSeconds;

}