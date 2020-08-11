package fr.volkaert.event_broker.standard_publication_adapter.model;

import fr.volkaert.event_broker.model.InflightEvent;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
public class EventToPublisher {

    // Attributes filled by the publisher (but without the payload)
    private String businessId;
    private String publicationCode;
    // private Object payload;
    private Long timeToLiveInSeconds;
    private String channel;

    // Attributes filled by the broker
    private String id;
    private Instant creationDate;
    private Instant expirationDate;
    private String eventTypeCode;

    public static EventToPublisher from(InflightEvent inflightEvent) {
        if (inflightEvent == null) return null;

        EventToPublisher eventToPublisher = new EventToPublisher();
        eventToPublisher.setBusinessId(inflightEvent.getBusinessId());
        eventToPublisher.setPublicationCode(inflightEvent.getPublicationCode());
        eventToPublisher.setTimeToLiveInSeconds(inflightEvent.getTimeToLiveInSeconds());
        eventToPublisher.setChannel(inflightEvent.getChannel());

        eventToPublisher.setId(inflightEvent.getId());
        eventToPublisher.setCreationDate(inflightEvent.getCreationDate());
        eventToPublisher.setExpirationDate(inflightEvent.getExpirationDate());
        eventToPublisher.setEventTypeCode(inflightEvent.getEventTypeCode());

        return eventToPublisher;
    }
}
