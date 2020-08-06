package fr.volkaert.event_broker.standard_subscription_adapter;

import fr.volkaert.event_broker.error.BrokerException;
import fr.volkaert.event_broker.model.InflightEvent;
import fr.volkaert.event_broker.standard_subscription_adapter.model.EventToSubscriber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

@Service
public class SubscriptionAdapterService {

    @Autowired
    @Qualifier("RestTemplateForWebhooks")
    RestTemplate restTemplate;

    private static final Logger LOGGER = LoggerFactory.getLogger(SubscriptionAdapterService.class);

    public InflightEvent callWebhook(InflightEvent inflightEvent) {
        ResponseEntity<Void> response = null;
        try {
            LOGGER.debug("Event received. Event is {}.", inflightEvent.cloneWithoutSensitiveData());

            HttpHeaders httpHeaders = new HttpHeaders();

            httpHeaders.setContentType(MediaType.valueOf(inflightEvent.getWebhookContentType()));
            if (inflightEvent.getAuthMethod() == null || inflightEvent.getAuthMethod().equalsIgnoreCase("basicauth")) {
                if (inflightEvent.getAuthClientId() != null && inflightEvent.getAuthClientSecret() != null) {
                    httpHeaders.setBasicAuth(inflightEvent.getAuthClientId(), inflightEvent.getAuthClientSecret());
                }
            }

            String webhookHeadersAsString = inflightEvent.getWebhookHeaders();
            if (webhookHeadersAsString != null && ! webhookHeadersAsString.trim().isEmpty()) {
                String[] headersAndValuesAsString = webhookHeadersAsString.trim().split(";");
                for (String headerAndValueAsString : headersAndValuesAsString) {
                    String[] headerAndValue = headerAndValueAsString.trim().split(":");
                    httpHeaders.set(headerAndValue[0].trim(), headerAndValue[1].trim());
                }
            }

            // charset UTF8 has been defined during the creation of RestTemplate

            EventToSubscriber eventToSubscriber = EventToSubscriber.from(inflightEvent);
            HttpEntity<EventToSubscriber> request = new HttpEntity<>(eventToSubscriber, httpHeaders);

            LOGGER.debug("Calling the webhook at {}. Event is {}.",
                    inflightEvent.getWebhookUrl(), eventToSubscriber.cloneWithoutSensitiveData());
            response = restTemplate.exchange(inflightEvent.getWebhookUrl(), HttpMethod.POST, request, Void.class);
            LOGGER.debug("The Webhook returned the http status code {}. Event is {}.",
                    response.getStatusCode(), inflightEvent.toShortLog());

            if (! response.getStatusCode().is2xxSuccessful()) {
                LOGGER.error("The webhook returned the unsuccessful http status code {}. Event is {}.",
                        response.getStatusCode(), inflightEvent.toShortLog());
            }

            inflightEvent.setWebhookHttpStatus(response.getStatusCodeValue());
            LOGGER.debug("Returning the event {}", inflightEvent.cloneWithoutSensitiveData());
            return inflightEvent;

        } catch (HttpClientErrorException ex) {
            String msg = String.format("Error while calling the webhook at %s. Event is %s.",
                    inflightEvent.getWebhookUrl(), inflightEvent.toShortLog());
            LOGGER.error(msg, ex);
            throw new BrokerException(ex.getStatusCode(), msg, ex, inflightEvent.getWebhookUrl());
        } catch (Exception ex) {
            String msg = String.format("Error while handling the call to the webhook at %s. Event is %s.",
                    inflightEvent.getWebhookUrl(), inflightEvent.toShortLog());
            LOGGER.error(msg, ex);
            throw new BrokerException(HttpStatus.INTERNAL_SERVER_ERROR, msg, ex, inflightEvent.getWebhookUrl());
        }
    }
}
