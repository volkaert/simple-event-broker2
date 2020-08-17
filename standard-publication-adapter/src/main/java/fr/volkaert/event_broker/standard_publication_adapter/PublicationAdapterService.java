package fr.volkaert.event_broker.standard_publication_adapter;

import fr.volkaert.event_broker.error.BrokerException;
import fr.volkaert.event_broker.model.InflightEvent;
import fr.volkaert.event_broker.standard_publication_adapter.model.EventFromPublisher;
import fr.volkaert.event_broker.standard_publication_adapter.model.EventToPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

@Service
public class PublicationAdapterService {

    @Autowired
    BrokerConfig config;

    @Autowired
    @Qualifier("RestTemplateForPublicationManager")
    RestTemplate restTemplate;

    private static final Logger LOGGER = LoggerFactory.getLogger(PublicationAdapterService.class);

    public EventToPublisher publish(EventFromPublisher eventFromPublisher) {
        LOGGER.debug("Event received. Event is {}.", eventFromPublisher);

        String publicationManagerUrl = config.getPublicationManagerUrl() + "/events";

        HttpHeaders httpHeaders = new HttpHeaders();

        httpHeaders.setContentType(MediaType.APPLICATION_JSON);

        if (!StringUtils.isEmpty(config.getAuthClientIdForPublicationManager()) && !StringUtils.isEmpty(config.getAuthClientSecretForPublicationManager())) {
            httpHeaders.setBasicAuth(
                    config.getAuthClientIdForPublicationManager(),
                    config.getAuthClientSecretForPublicationManager());
        } else {
            LOGGER.warn("No Basic Auth credentials provided to access the Publication Manager");
        }

        // charset UTF8 has been defined during the creation of RestTemplate

        InflightEvent inflightEvent = eventFromPublisher.toInflightEvent();
        HttpEntity<InflightEvent> request = new HttpEntity<>(inflightEvent, httpHeaders);

        try {
            LOGGER.debug("Calling the Publication Manager at {}. Event is {}.", publicationManagerUrl, inflightEvent);
            ResponseEntity<InflightEvent> response = restTemplate.exchange(publicationManagerUrl, HttpMethod.POST, request, InflightEvent.class);
            LOGGER.debug("The Publication Manager returned the http status code {}. Event is {}.",
                    response.getStatusCode(), inflightEvent.toShortLog());

            InflightEvent returnedInflighEvent = response.getBody();
            LOGGER.debug("The Publication Manager returned the event {}", returnedInflighEvent);
            EventToPublisher eventToPublisher = EventToPublisher.from(returnedInflighEvent);
            LOGGER.debug("Returning the event {}", eventToPublisher);
            return eventToPublisher;

        } catch (HttpClientErrorException ex) {
            String msg = String.format("Client error %s while calling the Publication Manager at %s. Event is %s.",
                    ex.getStatusCode(), publicationManagerUrl, inflightEvent.toShortLog());
            LOGGER.error(msg, ex);
            throw new BrokerException(ex.getStatusCode(), msg, ex, publicationManagerUrl);

        } catch (HttpServerErrorException ex) {
            String msg = String.format("Server error %s while calling the Publication Manager at %s. Event is %s.",
                    ex.getStatusCode(), publicationManagerUrl, inflightEvent.toShortLog());
            LOGGER.error(msg, ex);
            throw new BrokerException(ex.getStatusCode(), msg, ex, publicationManagerUrl);

        } catch (Exception ex) {
            if (ex.getMessage().contains("Connection refused")) {
                String msg = String.format("Connection Refused error while calling the Publication Manager at %s. Event is %s.",
                        publicationManagerUrl, inflightEvent.toShortLog());
                LOGGER.error(msg, ex);
                throw new BrokerException(HttpStatus.BAD_GATEWAY, msg, ex, publicationManagerUrl);
            }

            else if (ex.getMessage().contains("Read timed out")) {
                String msg = String.format("Read Timeout error while calling the Publication Manager at %s. Event is %s.",
                        inflightEvent.getWebhookUrl(), inflightEvent.toShortLog());
                LOGGER.error(msg, ex);
                throw new BrokerException(HttpStatus.GATEWAY_TIMEOUT, msg, ex, publicationManagerUrl);
            }

            else {
                String msg = String.format("Error while calling Publication Manager at %s. Event is %s.",
                        publicationManagerUrl, inflightEvent.toShortLog());
                LOGGER.error(msg, ex);
                throw new BrokerException(HttpStatus.INTERNAL_SERVER_ERROR, msg, ex, publicationManagerUrl);
            }
        }
    }
}
