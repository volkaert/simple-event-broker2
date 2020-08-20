package fr.volkaert.event_broker.catalog_client;

import fr.volkaert.event_broker.error.BrokerException;
import fr.volkaert.event_broker.model.EventType;
import fr.volkaert.event_broker.model.Publication;
import fr.volkaert.event_broker.model.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@Service
@Configuration
@EnableCaching
@EnableScheduling
public class CatalogClient {

    @Value("${broker.catalog-url}")
    String catalogUrl;

    @Autowired
    @Qualifier("RestTemplateForCatalogClient")
    RestTemplate restTemplate;

    @Autowired
    CacheManager cacheManager;

    private static final Logger LOGGER = LoggerFactory.getLogger(CatalogClient.class);

    @Scheduled(fixedDelay = 60000)
    public void clearCaches() {
        LOGGER.info("Clearing catalog caches");
        cacheManager.getCache("event-types") .clear();
        cacheManager.getCache("publications") .clear();
        cacheManager.getCache("subscriptions") .clear();
    }

    @Cacheable(value = "event-types")
    public List<EventType> getEventTypes() {
        ResponseEntity<List<EventType>> responseEntity = restTemplate.exchange(catalogUrl + "/catalog/event-types", HttpMethod.GET, null,
            new ParameterizedTypeReference<List<EventType>>() {});
        return responseEntity.getBody();
    }

    @Cacheable(value = "event-types")
    public EventType getEventType(String code) {
        ResponseEntity<EventType> responseEntity = restTemplate.getForEntity(catalogUrl + "/catalog/event-types/" + code, EventType.class);
        return responseEntity.getBody();
    }
    @Cacheable(value = "event-types")
    public EventType getEventTypeOrThrowException(String code) {
        EventType eventType = getEventType(code);
        if (eventType == null) {
            String msg = String.format("Invalid event type code '%s'", code);
            LOGGER.error(msg);
            throw new BrokerException(HttpStatus.INTERNAL_SERVER_ERROR, msg);
        }
        return eventType;
    }

    @Cacheable(value = "publications")
    public List<Publication> getPublications() {
        ResponseEntity<List<Publication>> responseEntity = restTemplate.exchange(catalogUrl + "/catalog/publications", HttpMethod.GET, null,
                new ParameterizedTypeReference<List<Publication>>() {});
        return responseEntity.getBody();
    }

    @Cacheable(value = "publications")
    public Publication getPublication(String code) {
        ResponseEntity<Publication> responseEntity = restTemplate.getForEntity(catalogUrl + "/catalog/publications/" + code, Publication.class);
        return responseEntity.getBody();
    }

    @Cacheable(value = "publications")
    public Publication getPublicationOrThrowException(String code) {
        Publication publication = getPublication(code);
        if (publication == null) {
            String msg = String.format("Invalid publication code '%s'", code);
            LOGGER.error(msg);
            throw new BrokerException(HttpStatus.INTERNAL_SERVER_ERROR, msg);
        }
        return publication;
    }

    @Cacheable(value = "subscriptions")
    public List<Subscription> getSubscriptions() {
        ResponseEntity<List<Subscription>> responseEntity = restTemplate.exchange(catalogUrl + "/catalog/subscriptions", HttpMethod.GET, null,
                new ParameterizedTypeReference<List<Subscription>>() {});
        return responseEntity.getBody();
    }

    @Cacheable(value = "subscriptions")
    public Subscription getSubscription(String code) {
        ResponseEntity<Subscription> responseEntity = restTemplate.getForEntity(catalogUrl + "/catalog/subscriptions/" + code, Subscription.class);
        return responseEntity.getBody();
    }

    @Cacheable(value = "subscriptions")
    public Subscription getSubscriptionOrThrowException(String code) {
        Subscription subscription = getSubscription(code);
        if (subscription == null) {
            String msg = String.format("Invalid subscription code '%s'", code);
            LOGGER.error(msg);
            throw new BrokerException(HttpStatus.INTERNAL_SERVER_ERROR, msg);
        }
        return subscription;
    }
}
