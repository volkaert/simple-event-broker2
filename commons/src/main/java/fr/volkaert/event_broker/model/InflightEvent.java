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

    private boolean redelivered;
    private int redeliveryCount;

    private String webhookUrl;
    private String webhookContentType = MediaType.APPLICATION_JSON_VALUE;
    private String webhookHeaders;  // format: header1:value1;header2=value2;header3=value3
    private int webhookHttpStatus;  // HTTP status code returned by the webhook
    private boolean webhookConnectionErrorOccurred;
    private boolean webhookReadTimeoutErrorOccurred;
    private boolean webhookServer5xxErrorOccurred;
    private boolean webhookClient4xxErrorOccurred;
    private boolean webhookAuth401r403ErrorOccurred;

    private String authMethod = "basicauth";    // allowed values are "basicauth" and "oauth2"; if null, "basicauth" is used by default
    private String authClientId;
    private String authClientSecret;
    private String authScope;   // may be useful if authMethod = "oauth2"

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
        clone.setRedelivered(redelivered);
        clone.setRedeliveryCount(redeliveryCount);
        clone.setWebhookUrl(webhookUrl);
        clone.setWebhookContentType(webhookContentType);
        clone.setWebhookHeaders(webhookHeaders);
        clone.setWebhookHttpStatus(webhookHttpStatus);
        clone.setWebhookConnectionErrorOccurred(webhookConnectionErrorOccurred);
        clone.setWebhookReadTimeoutErrorOccurred(webhookReadTimeoutErrorOccurred);
        clone.setWebhookServer5xxErrorOccurred(webhookServer5xxErrorOccurred);
        clone.setWebhookClient4xxErrorOccurred(webhookClient4xxErrorOccurred);
        clone.setWebhookAuth401r403ErrorOccurred(webhookAuth401r403ErrorOccurred);
        clone.setAuthMethod("*****"); // SENSITIVE DATA !
        clone.setAuthClientId("*****"); // SENSITIVE DATA !
        clone.setAuthClientSecret("*****"); // SENSITIVE DATA !
        clone.setAuthScope("*****"); // SENSITIVE DATA !
        return clone;
    }
}
