package fr.volkaert.event_broker.pulsar_publication_manager;

import fr.volkaert.event_broker.catalog_client.CatalogClient;
import fr.volkaert.event_broker.error.BrokerException;
import fr.volkaert.event_broker.model.EventType;
import fr.volkaert.event_broker.model.InflightEvent;
import fr.volkaert.event_broker.model.Publication;
import fr.volkaert.event_broker.telemetry.TelemetryService;
import org.apache.pulsar.client.api.Producer;
import org.apache.pulsar.client.api.PulsarClient;
import org.apache.pulsar.client.api.PulsarClientException;
import org.apache.pulsar.client.api.Schema;
import org.apache.pulsar.client.api.schema.SchemaDefinition;
import org.apache.pulsar.client.internal.DefaultImplementation;
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
    TelemetryService telemetryService;

    private static final Logger LOGGER = LoggerFactory.getLogger(PublicationManagerService.class);

    public InflightEvent publish(InflightEvent inflightEvent) throws PulsarClientException {
        telemetryService.eventPublicationSubmitted(inflightEvent);

        Instant publicationStart = Instant.now();

        setTimeToLiveInSecondsIfMissingOrInvalid(inflightEvent);

        inflightEvent.setId(UUID.randomUUID().toString());
        inflightEvent.setCreationDate(publicationStart);
        inflightEvent.setExpirationDate(publicationStart.plusSeconds(inflightEvent.getTimeToLiveInSeconds()));

        String publicationCode = inflightEvent.getPublicationCode();
        if (publicationCode == null || publicationCode.trim().equals("")) {
            String msg = telemetryService.eventPublicationSubmittedWithMissingPublicationCode();
            throw new BrokerException(HttpStatus.BAD_REQUEST, msg);
        }

        Publication publication = catalog.getPublication(publicationCode);
        if (publication == null) {
            String msg = telemetryService.eventPublicationSubmittedWithInvalidPublicationCode(publicationCode);
            throw new BrokerException(HttpStatus.BAD_REQUEST, msg);
        }

        if (! publication.isActive()) {
            String msg = telemetryService.eventPublicationSubmittedOnInactivePublication(publicationCode);
            throw new BrokerException(HttpStatus.BAD_REQUEST, msg);
        }

        String eventTypeCode = publication.getEventTypeCode();
        EventType eventType = catalog.getEventTypeOrThrowException(eventTypeCode);
        inflightEvent.setEventTypeCode(eventTypeCode);

        telemetryService.beginOfPublication(publicationCode, eventTypeCode);
        try {
            telemetryService.eventPublicationAttempted(inflightEvent);

            Producer<InflightEvent> producer = getPulsarProducer(eventTypeCode);

            producer.send(inflightEvent);

            telemetryService.eventPublicationSucceeded(inflightEvent);

        } catch (Exception ex) {
            telemetryService.eventPublicationFailed(inflightEvent);

        } finally {
            telemetryService.endOfPublication(publicationCode, eventTypeCode, publicationStart);
            LOGGER.debug("Returning the event {}", inflightEvent);
            return inflightEvent;
        }
    }

    private synchronized Producer<InflightEvent> getPulsarProducer(String eventTypeCode) {
        Producer<InflightEvent> producer = eventTypeCodeToPulsarProducerMap.computeIfAbsent(eventTypeCode, x -> {
            try {
                LOGGER.info("Creating Pulsar producer for eventTypeCode {}", eventTypeCode);
                //Schema<InflightEvent> schema = Schema.JSON(InflightEvent.class);
                Schema<InflightEvent> schema = DefaultImplementation.newJSONSchema(SchemaDefinition.builder().withJSR310ConversionEnabled(true).withPojo(InflightEvent.class).build());
                Producer<InflightEvent> p =  pulsar.newProducer(schema)
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
