package fr.volkaert.event_broker_test_oauth2;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
public class EventToSubscriberWithoutPayload {

    // Attributes filled by the publisher
    private String businessId;
    private String publicationCode;
    //private Object payload;
    private Long timeToLiveInSeconds;
    private String channel;

    // Attributes filled by the broker
    private String id;
    private Instant creationDate;
    private Instant expirationDate;
    private String eventTypeCode;
    private String subscriptionCode;
    private String secret;  // @Todo: to handle properly

    private boolean redelivered;    // @Todo: to handle properly
    private int deliveryCount;
}
