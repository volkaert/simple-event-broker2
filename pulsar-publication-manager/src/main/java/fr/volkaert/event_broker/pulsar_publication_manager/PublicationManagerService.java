package fr.volkaert.event_broker.pulsar_publication_manager;

import fr.volkaert.event_broker.catalog_client.CatalogClient;
import fr.volkaert.event_broker.error.BrokerException;
import fr.volkaert.event_broker.metrics.MetricsService;
import fr.volkaert.event_broker.model.EventType;
import fr.volkaert.event_broker.model.InflightEvent;
import fr.volkaert.event_broker.model.Publication;
import org.apache.pulsar.client.api.Producer;
import org.apache.pulsar.client.api.PulsarClient;
import org.apache.pulsar.client.api.PulsarClientException;
import org.apache.pulsar.client.api.Schema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class PublicationManagerService {

    @Autowired
    BrokerConfig config;

    @Autowired
    CatalogClient catalog;

    @Autowired
    PulsarClient pulsar;

    Map<String, Producer> eventTypeCodeToPulsarProducerMap = new HashMap<>();

    @Autowired
    MetricsService metricsService;

    private static final Logger LOGGER = LoggerFactory.getLogger(PublicationManagerService.class);

    public InflightEvent publish(InflightEvent inflightEvent) throws PulsarClientException {
        LOGGER.debug("Event received. Event is {}.", inflightEvent);

        Instant publicationStart = Instant.now();

        setTimeToLiveInSecondsIfMissingOrInvalid(inflightEvent);

        inflightEvent.setId(UUID.randomUUID().toString());
        inflightEvent.setCreationDate(publicationStart);
        inflightEvent.setExpirationDate(publicationStart.plusSeconds(inflightEvent.getTimeToLiveInSeconds()));

        String publicationCode = inflightEvent.getPublicationCode();
        if (publicationCode == null || publicationCode.trim().equals("")) {
            metricsService.registerPublicationWithMissingPublicationCode();
            throw new BrokerException(HttpStatus.BAD_REQUEST, "Publication code is missing");
        }
        Publication publication = catalog.getPublication(publicationCode);
        if (publication == null) {
            metricsService.registerPublicationWithInvalidPublicationCode(publicationCode);
                throw new BrokerException(HttpStatus.BAD_REQUEST,
                        String.format("Invalid publication code '%s'", publicationCode));
        }
        if (! publication.isActive()) {
            throw new BrokerException(HttpStatus.BAD_REQUEST,
                    String.format("Inactive publication '%s'", publicationCode));
        }

        String eventTypeCode = publication.getEventTypeCode();
        EventType eventType = catalog.getEventTypeOrThrowException(eventTypeCode);
        inflightEvent.setEventTypeCode(eventTypeCode);

        boolean beginOfPublicationRegistered = false;
        try {
            metricsService.registerBeginOfPublication(publicationCode, eventTypeCode);
            beginOfPublicationRegistered = true;

            Producer<InflightEvent> producer = getPulsarProducer(eventTypeCode);

            LOGGER.debug("Sending event to Pulsar. Events is {}.", inflightEvent);
            producer.send(inflightEvent);
            LOGGER.debug("Event sent successfully to Pulsar. Event is {}.", inflightEvent.toShortLog());

            LOGGER.debug("Returning the event {}", inflightEvent);
            return inflightEvent;
        } finally {
            if (beginOfPublicationRegistered) {
                metricsService.registerEndOfPublication(publicationCode, eventTypeCode, publicationStart);
            }
        }
    }

    private synchronized Producer<InflightEvent> getPulsarProducer(String eventTypeCode) {
        Producer<InflightEvent> producer = eventTypeCodeToPulsarProducerMap.computeIfAbsent(eventTypeCode, x -> {
            try {
                LOGGER.info("Creating Pulsar producer for eventTypeCode {}", eventTypeCode);
                Producer<InflightEvent> p =  pulsar.newProducer(Schema.JSON(InflightEvent.class))
                        .topic(eventTypeCode)
                        .create();
                if (p != null) {
                    LOGGER.info("Pulsar producer created for eventTypeCode {}", eventTypeCode);
                }
                return p;
            } catch (Exception ex) {
                String msg = String.format("Error while creating a Pulsar producer for eventTypeCode %s", eventTypeCode);
                LOGGER.error(msg, ex);
                return null;
            }
        });
        if (producer == null) {
            // No Need to log the error since it has already been logged in eventTypeCodeToPulsarProducerMap.computeIfAbsent
            String msg = String.format("Error while creating a Pulsar producer for eventTypeCode %s", eventTypeCode);
            throw new BrokerException(HttpStatus.INTERNAL_SERVER_ERROR, msg);
        }
        return producer;
    }

    private void setTimeToLiveInSecondsIfMissingOrInvalid(InflightEvent eventFromPublisher) {
        if (eventFromPublisher.getTimeToLiveInSeconds() == null) {
            eventFromPublisher.setTimeToLiveInSeconds(config.getDefaultTimeToLiveInSeconds());
        }
        if (eventFromPublisher.getTimeToLiveInSeconds() < 0) {
            eventFromPublisher.setTimeToLiveInSeconds(config.getDefaultTimeToLiveInSeconds());
        }
        if (eventFromPublisher.getTimeToLiveInSeconds() > config.getMaxTimeToLiveInSeconds()) {
            eventFromPublisher.setTimeToLiveInSeconds(config.getMaxTimeToLiveInSeconds());
        }
    }
}
