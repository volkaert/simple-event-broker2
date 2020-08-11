package fr.volkaert.event_broker.standard_subscription_adapter.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
public class EventToSubscriberWithoutPayload {

    // Attributes filled by the publisher
    protected String businessId;
    protected String publicationCode;
    //protected Object payload;
    protected Long timeToLiveInSeconds;
    protected String channel;

    // Attributes filled by the broker
    protected String id;
    protected Instant creationDate;
    protected Instant expirationDate;
    protected String eventTypeCode;
    protected String subscriptionCode;
    protected String secret;  // @Todo: to handle properly

   protected  boolean redelivered;    // @Todo: to handle properly
    protected int redeliveryCount;
}
