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
import org.springframework.web.client.HttpClientErrorException;
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

        InflightEvent inflightEvent = eventFromPublisher.toInflightEvent();
        ResponseEntity<InflightEvent> response = null;
        String publicationManagerUrl = config.getPublicationManagerUrl() + "/events";

        try {
            HttpHeaders httpHeaders = new HttpHeaders();
            httpHeaders.setContentType(MediaType.APPLICATION_JSON);
            httpHeaders.setBasicAuth(
                    config.getAuthClientIdForPublicationManager(),
                    config.getAuthClientSecretForPublicationManager());
            // charset UTF8 has been defined during the creation of RestTemplate

            HttpEntity<InflightEvent> request = new HttpEntity<>(inflightEvent, httpHeaders);

            LOGGER.debug("Calling the Publication Manager at {}. Event is {}.", publicationManagerUrl, inflightEvent);
            response = restTemplate.exchange(publicationManagerUrl, HttpMethod.POST, request, InflightEvent.class);
            LOGGER.debug("The Publication Manager returned the http status code {}. Event is {}.",
                    response.getStatusCode(), inflightEvent.toShortLog());

            if (! response.getStatusCode().is2xxSuccessful()) {
                String msg = String.format("The Publication Manager returned the unsuccessful http status code %s. Event is %s.",
                        response.getStatusCode(), inflightEvent.toShortLog());
                LOGGER.error(msg);
                BrokerException bex = new BrokerException(response.getStatusCode(), msg, publicationManagerUrl);
                throw bex;
            }

            InflightEvent returnedInflighEvent = response.getBody();
            LOGGER.debug("The Publication Manager returned the event {}", returnedInflighEvent);
            EventToPublisher eventToPublisher = EventToPublisher.from(returnedInflighEvent);
            LOGGER.debug("Returning the event {}", eventToPublisher);
            return eventToPublisher;

        } catch (HttpClientErrorException ex) {
            String msg = String.format("Error while calling the Publication Manager at %s. Event is %s.",
                    publicationManagerUrl, inflightEvent.toShortLog());
            LOGGER.error(msg, ex);
            throw new BrokerException(ex.getStatusCode(), msg, ex, publicationManagerUrl);
        } catch (Exception ex) {
            String msg = String.format("Error while handling the call to the Publication Manager at %s. Event is %s.",
                    publicationManagerUrl, inflightEvent.toShortLog());
            LOGGER.error(msg, ex);
            throw new BrokerException(HttpStatus.INTERNAL_SERVER_ERROR, msg, ex, publicationManagerUrl);
        }
    }
}
