package fr.volkaert.event_broker.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;

@Data
@NoArgsConstructor
@Entity
@Table(name="subscription")
public class Subscription {

    @Id
    @GeneratedValue(strategy= GenerationType.IDENTITY)
    private Long id;

    private String code;
    private String name;
    private String eventTypeCode;
    private boolean active;

    private String channel;

    private String webhookUrl;
    private String webhookContentType;
    private String webhookHeaders;  // format: header1:value1;header2=value2;header3=value3

    private String authMethod = "basicauth";    // allowed values are "basicauth" and "oauth2"; if null, "basicauth" is used by default
    private String authClientId;
    private String authClientSecret;
    private String authScope;   // may be useful if authMethod = "oauth2"

    private String secret;

    private Long timeToLiveInSecondsForWebhookConnectionError;      // If null or 0, use the defaultTimeToLiveInSecondsForWebhookConnectionError in BrokerConfig
    private Long timeToLiveInSecondsForWebhookReadTimeoutError;     // If null or 0, use the defaultTimeToLiveInSecondsForWebhookReadTimeoutError in BrokerConfig
    private Long timeToLiveInSecondsForWebhookServer5xxError;       // If null or 0, use the defaultTimeToLiveInSecondsForWebhookServer5xxError in BrokerConfig
    private Long timeToLiveInSecondsForWebhookClient4xxError;       // If null or 0, use the defaultTimeToLiveInSecondsForWebhookClient4xxError in BrokerConfig
}
