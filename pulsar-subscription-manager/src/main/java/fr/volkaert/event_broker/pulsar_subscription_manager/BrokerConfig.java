package fr.volkaert.event_broker.pulsar_subscription_manager;

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

    private String pulsarServiceUrl;

    private String subscriptionAdapterUrl;
    private String authClientIdForSubscriptionAdapter;
    private String authClientSecretForSubscriptionAdapter;

    private long defaultTimeToLiveInSecondsForWebhookConnectionError;
    private long defaultTimeToLiveInSecondsForWebhookReadTimeoutError;
    private long defaultTimeToLiveInSecondsForWebhookServer5xxError;
    private long defaultTimeToLiveInSecondsForWebhookClient4xxError;
    private long defaultTimeToLiveInSecondsForWebhookAuth401Or403Error;
}
