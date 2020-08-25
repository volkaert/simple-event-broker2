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

    private long connectTimeoutInSecondsForSubscriptionAdapter;
    private long readTimeoutInSecondsForSubscriptionAdapter;

    private String pulsarServiceUrl;
    private int pulsarListenerThreadCount;

    private String subscriptionAdapterUrl;
    private String authClientIdForSubscriptionAdapter;
    private String authClientSecretForSubscriptionAdapter;

    private long defaultTimeToLiveInSecondsForWebhookConnectionError;
    private long defaultTimeToLiveInSecondsForWebhookReadTimeoutError;
    private long defaultTimeToLiveInSecondsForWebhookServer5xxError;
    private long defaultTimeToLiveInSecondsForWebhookClient4xxError;
    private long defaultTimeToLiveInSecondsForWebhookAuth401Or403Error;

    // Cluster size is the number of PulsarSubscriptionManager instances and Cluster index is the index of this
    // PulsarSubscriptionManager instance within the cluster.
    // Cluster index must  be ***UNIQUE*** within the cluster and must follow the sequence 0, 1... < Cluster size.
    // The PulsarSubscriptionManager instance in charge of the management of an event is the instance that meets the
    // criteria 'broker.cluster-index == (sum-of-the-ascii-codes-of-the-chars-of-event-type-code % broker.cluster-size)'.
    // For a given event type, only one instance of PulsarSubscriptionManager will manage the events.
    private int clusterSize;
    private int clusterIndex;
}
