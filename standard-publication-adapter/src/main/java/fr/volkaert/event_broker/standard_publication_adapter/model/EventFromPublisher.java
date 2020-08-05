package fr.volkaert.event_broker.standard_publication_adapter.model;

import fr.volkaert.event_broker.model.InflightEvent;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class EventFromPublisher {

    private String businessId;
    private String publicationCode;
    private Object payload;
    private Long timeToLiveInSeconds;
    private String channel;

    public InflightEvent toInflightEvent() {
        InflightEvent inflightEvent = new InflightEvent();
        inflightEvent.setBusinessId(businessId);
        inflightEvent.setPublicationCode(publicationCode);
        inflightEvent.setPayload(payload);
        inflightEvent.setTimeToLiveInSeconds(timeToLiveInSeconds);
        inflightEvent.setChannel(channel);
        return inflightEvent;
    }
}