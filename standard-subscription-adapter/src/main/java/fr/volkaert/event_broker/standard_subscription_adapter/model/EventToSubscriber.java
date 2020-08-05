package fr.volkaert.event_broker.standard_subscription_adapter.model;

import fr.volkaert.event_broker.model.InflightEvent;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class EventToSubscriber extends EventToSubscriberWithoutPayload {

    // Attributes filled by the publisher
    private Object payload;

    public static EventToSubscriber from(InflightEvent inflightEvent) {
        EventToSubscriber eventToSubscriber = new EventToSubscriber();
        eventToSubscriber.setBusinessId(inflightEvent.getBusinessId());
        eventToSubscriber.setPublicationCode(inflightEvent.getPublicationCode());
        eventToSubscriber.setPayload(inflightEvent.getPayload());
        eventToSubscriber.setTimeToLiveInSeconds(inflightEvent.getTimeToLiveInSeconds());
        eventToSubscriber.setChannel(inflightEvent.getChannel());
        eventToSubscriber.setId(inflightEvent.getId());
        eventToSubscriber.setCreationDate(inflightEvent.getCreationDate());
        eventToSubscriber.setExpirationDate(inflightEvent.getExpirationDate());
        eventToSubscriber.setEventTypeCode(inflightEvent.getEventTypeCode());
        eventToSubscriber.setSubscriptionCode(inflightEvent.getSubscriptionCode());
        eventToSubscriber.setSecret(inflightEvent.getSecret());
        //eventToSubscriber.setRedelivered(inflightEvent.isRedelivered());
        //eventToSubscriber.setDeliveryCount(inflightEvent.getDeliveryCount());
        return eventToSubscriber;
    }

    public EventToSubscriber cloneWithoutSensitiveData() {
        EventToSubscriber clone = new EventToSubscriber();
        clone.setBusinessId(businessId);
        clone.setPublicationCode(publicationCode);
        clone.setPayload(payload);
        clone.setTimeToLiveInSeconds(timeToLiveInSeconds);
        clone.setChannel(channel);
        clone.setId(id);
        clone.setCreationDate(creationDate);
        clone.setExpirationDate(expirationDate);
        clone.setEventTypeCode(eventTypeCode);
        clone.setSubscriptionCode(subscriptionCode);
        clone.setSecret("*****");   // SENSITIVE DATA !
        //clone.setRedelivered(isRedeliveered);
        //clone.setDeliveryCount(deliveryCount);
        return clone;
    }
}
