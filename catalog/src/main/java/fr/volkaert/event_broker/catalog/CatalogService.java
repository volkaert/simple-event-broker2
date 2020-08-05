package fr.volkaert.event_broker.catalog;

import fr.volkaert.event_broker.model.EventType;
import fr.volkaert.event_broker.model.Publication;
import fr.volkaert.event_broker.model.Subscription;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;
import java.util.List;

@Service
@Transactional
public class CatalogService {

    @Autowired
    EventTypeRepository eventTypeRepository;

    @Autowired
    PublicationRepository publicationRepository;

    @Autowired
    SubscriptionRepository subscriptionRepository;

    public List<EventType> getEventTypes() {
        return eventTypeRepository.findAll();
    }

    public EventType getEventTypeByCode(String code) {
        return eventTypeRepository.findByCode(code);
    }

    public EventType saveEventType(EventType eventType) {
        return eventTypeRepository.save(eventType);
    }

    public void deleteEventTypeByCode(String code) {
        eventTypeRepository.deleteByCode(code);
    }

    public List<Publication> getPublications() {
        return publicationRepository.findAll();
    }

    public Publication getPublicationByCode(String code) {
        return publicationRepository.findByCode(code);
    }

    public Publication savePublication(Publication publication) {
        return publicationRepository.save(publication);
    }

    public void deletePublicationByCode(String code) {
        publicationRepository.deleteByCode(code);
    }

    public List<Subscription> getSubscriptions() {
        return subscriptionRepository.findAll();
    }

    public Subscription getSubscriptionByCode(String code) {
        return subscriptionRepository.findByCode(code);
    }

    public Subscription saveSubscription(Subscription subscription) {
        return subscriptionRepository.save(subscription);
    }

    public void deleteSubscriptionByCode(String code) {
        subscriptionRepository.deleteByCode(code);
    }
}
