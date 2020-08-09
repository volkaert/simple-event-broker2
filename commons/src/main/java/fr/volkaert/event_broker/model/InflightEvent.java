package fr.volkaert.event_broker.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import fr.volkaert.event_broker.util.MyCustomInstantDeserializer;
import fr.volkaert.event_broker.util.MyCustomInstantSerializer;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.http.MediaType;

import java.time.Instant;

@Data
@NoArgsConstructor
public class InflightEvent {

    private String businessId;

    private String eventTypeCode;
    private String publicationCode;
    private String subscriptionCode;

    private Object payload;
    private Long timeToLiveInSeconds;
    private String channel;

    // Attributes filled by the broker
    private String id;
    @JsonSerialize(using = MyCustomInstantSerializer.class)     // Mandatory because Apache Pulsar does NOT how to manage the Instant type
    @JsonDeserialize(using = MyCustomInstantDeserializer.class) // Mandatory because Apache Pulsar does NOT how to manage the Instant type
    private Instant creationDate;
    @JsonSerialize(using = MyCustomInstantSerializer.class)     // Mandatory because Apache Pulsar does NOT how to manage the Instant type
    @JsonDeserialize(using = MyCustomInstantDeserializer.class) // Mandatory because Apache Pulsar does NOT how to manage the Instant type
    private Instant expirationDate;

    private String secret;

    //private boolean redelivered;
    //private int deliveryCount;

    private String webhookUrl;
    private String webhookContentType = MediaType.APPLICATION_JSON_VALUE;
    private String webhookHeaders;  // format: header1:value1;header2=value2;header3=value3
    private int webhookHttpStatus;  // HTTP status code returned by the webhook
    private boolean webhookConnectionErrorOccurred;
    private boolean webhookReadTimeoutErrorOccurred;
    private boolean webhookClientErrorOccurred;
    private boolean webhookServerErrorOccurred;

    private String authMethod = "basicauth";    // allowed values are "basicauth" and "oauth2"; if null, "basicauth" is used by default
    private String authClientId;
    private String authClientSecret;
    private String authScope;   // may be useful if authMethod = "oauth2"

    private Long timeToLiveInSecondsForWebhookConnectionError;      // If null or 0, use the defaultTimeToLiveInSecondsForWebhookConnectionError in BrokerConfig
    private Long timeToLiveInSecondsForWebhookReadTimeoutError;     // If null or 0, use the defaultTimeToLiveInSecondsForWebhookReadTimeoutError in BrokerConfig
    private Long timeToLiveInSecondsForWebhookServer5xxError;       // If null or 0, use the defaultTimeToLiveInSecondsForWebhookServer5xxError in BrokerConfig
    private Long timeToLiveInSecondsForWebhookClient4xxError;       // If null or 0, use the defaultTimeToLiveInSecondsForWebhookClient4xxError in BrokerConfig

    public String toShortLog() {
        return String.format("{ id: %s, businessId: %s, eventTypeCode: %s, publicationCode: %s, subscriptionCode: %s }",
                id, businessId, eventTypeCode, publicationCode, subscriptionCode);
    }

    public InflightEvent cloneWithoutSensitiveData() {
        InflightEvent clone = new InflightEvent();
        clone.setBusinessId(businessId);
        clone.setEventTypeCode(eventTypeCode);
        clone.setPublicationCode(publicationCode);
        clone.setSubscriptionCode(subscriptionCode);
        clone.setPayload(payload);
        clone.setTimeToLiveInSeconds(timeToLiveInSeconds);
        clone.setChannel(channel);
        clone.setId(id);
        clone.setCreationDate(creationDate);
        clone.setExpirationDate(expirationDate);
        clone.setSecret("*****");   // SENSITIVE DATA !
        //clone.setRedelivered(isRedelivered());
        //clone.setDeliveryCount(deliveryCount);
        clone.setWebhookUrl(webhookUrl);
        clone.setWebhookContentType(webhookContentType);
        clone.setWebhookHeaders(webhookHeaders);
        clone.setWebhookHttpStatus(webhookHttpStatus);
        clone.setWebhookConnectionErrorOccurred(webhookConnectionErrorOccurred);
        clone.setWebhookReadTimeoutErrorOccurred(webhookReadTimeoutErrorOccurred);
        clone.setWebhookClientErrorOccurred(webhookClientErrorOccurred);
        clone.setWebhookServerErrorOccurred(webhookServerErrorOccurred);
        clone.setAuthMethod("*****"); // SENSITIVE DATA !
        clone.setAuthClientId("*****"); // SENSITIVE DATA !
        clone.setAuthClientSecret("*****"); // SENSITIVE DATA !
        clone.setAuthScope("*****"); // SENSITIVE DATA !
        clone.setTimeToLiveInSecondsForWebhookConnectionError(timeToLiveInSecondsForWebhookConnectionError);
        clone.setTimeToLiveInSecondsForWebhookReadTimeoutError(timeToLiveInSecondsForWebhookReadTimeoutError);
        clone.setTimeToLiveInSecondsForWebhookServer5xxError(timeToLiveInSecondsForWebhookServer5xxError);
        clone.setTimeToLiveInSecondsForWebhookClient4xxError(timeToLiveInSecondsForWebhookClient4xxError);
        return clone;
    }
}
