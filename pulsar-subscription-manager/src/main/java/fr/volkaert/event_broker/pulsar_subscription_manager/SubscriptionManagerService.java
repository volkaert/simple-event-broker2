package fr.volkaert.event_broker.pulsar_subscription_manager;

import fr.volkaert.event_broker.catalog_client.CatalogClient;
import fr.volkaert.event_broker.error.BrokerException;
import fr.volkaert.event_broker.metrics.MetricsService;
import fr.volkaert.event_broker.model.EventType;
import fr.volkaert.event_broker.model.InflightEvent;
import fr.volkaert.event_broker.model.Subscription;
import org.apache.pulsar.client.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Configuration
@EnableScheduling
public class SubscriptionManagerService {

    @Autowired
    BrokerConfig config;

    @Autowired
    CatalogClient catalog;

    @Autowired
    PulsarClient pulsar;

    @Autowired
    @Qualifier("RestTemplateForSubscriptionAdapter")
    RestTemplate restTemplate;

    Map<String, Consumer> subscriptionCodeToPulsarConsumerMap = new HashMap<>();
    Map<String, Producer> eventTypeCodeToPulsarProducerForDLQMap = new HashMap<>();

    @Autowired
    MetricsService metricsService;

    private static final Logger LOGGER = LoggerFactory.getLogger(SubscriptionManagerService.class);

    public void start() {
        LOGGER.info("Subscription service started");
        createPulsarConsumers();
    }

    @Scheduled(fixedDelay = 60000)
    // *** NEVER LET AN EXCEPTION BE RAISED/THROWN BY THIS OPERATION !!! ***
    public void createPulsarConsumers() {
        try {
            LOGGER.info("Loading subscriptions from the catalog...");
            List<Subscription> subscriptions = catalog.getSubscriptions();
            LOGGER.info("Subscriptions successfully loaded from the catalog...");

            if (subscriptions != null && ! subscriptions.isEmpty()) {
                LOGGER.info("Creating Pulsar consumers...");
                for (Subscription subscription : subscriptions) {
                    try {
                        getPulsarConsumer(subscription.getEventTypeCode(), subscription.getCode());
                    } catch (Exception ex) {  // if there is an issue with a subscription, continue with the others...
                        // No need to log the error since it has already been logged in getPulsarConsumer()
                    }
                }
                LOGGER.info("End of Pulsar consumers creation");
            }
        }
        catch (Exception ex) {
            LOGGER.error("Error while loading subscriptions from the catalog. Pulsar consumers may not have been successfully created.", ex);
        }
    }

    private synchronized Consumer<InflightEvent> getPulsarConsumer(String eventTypeCode, String subscriptionCode) {
        Consumer<InflightEvent> consumer = subscriptionCodeToPulsarConsumerMap.computeIfAbsent(subscriptionCode, x -> {
            try {
                LOGGER.info("Creating Pulsar consumer for eventTypeCode {} and subscriptionCode {}", eventTypeCode, subscriptionCode);
                Consumer<InflightEvent> c = pulsar.newConsumer(Schema.JSON(InflightEvent.class))
                        .topic(eventTypeCode)
                        .subscriptionName(subscriptionCode)
                        .subscriptionType(SubscriptionType.Failover)
                        .messageListener((cons, msg) ->  {
                            try {
                                handlePulsarMessageAndAck(cons, msg);
                            } catch (Exception ex) {
                                LOGGER.error("Error while handling Pulsar message", ex);
                            }
                        })
                        .subscribe();
                if (c != null) {
                    LOGGER.info("Pulsar consumer created for eventTypeCode {} and subscriptionCode {}", eventTypeCode, subscriptionCode);
                }
                return c;
            } catch (Exception ex) {
                String msg = String.format("Error while creating a Pulsar consumer for eventTypeCode %s and subscriptionCode %s",
                        eventTypeCode, subscriptionCode);
                LOGGER.error(msg, ex);
                return null;
            }
        });
        if (consumer == null) {
            // No Need to log the error since it has already been logged in subscriptionCodeToPulsarConsumerMap.computeIfAbsent
            String msg = String.format("Error while creating a Pulsar consumer for eventTypeCode %s and subscriptionCode %s",
                    eventTypeCode, subscriptionCode);
            throw new BrokerException(HttpStatus.INTERNAL_SERVER_ERROR, msg);
        }
        return consumer;
    }

    // *** NEVER LET AN EXCEPTION BE RAISED/THROWN BY THIS OPERATION !!! ***
    private void handlePulsarMessageAndAck(Consumer<InflightEvent> consumer, Message<InflightEvent> message) {
        InflightEvent inflightEvent = null;
        try {
            LOGGER.debug("Message received from Pulsar. Message is {}.", new String(message.getData()));

            inflightEvent = message.getValue();

            String subscriptionCode = consumer.getSubscription();
            inflightEvent.setSubscriptionCode(subscriptionCode);

            LOGGER.debug("Event received from Pulsar. Event is {}.", inflightEvent.cloneWithoutSensitiveData());

            Instant now = Instant.now();
            boolean eventExpired = now.isAfter(inflightEvent.getExpirationDate());
            if (eventExpired) {
                LOGGER.warn("Event expired. Event is {}.", inflightEvent.toShortLog());
                LOGGER.warn("Ack (due to expired event) for message {}. Event is {}.", message.getMessageId(), inflightEvent.toShortLog());
                consumer.acknowledge(message);
                recordEventInDLQ(inflightEvent);
                return; // *** PAY ATTENTION, THERE IS A RETURN HERE !!! ***
            }

            Subscription subscription = catalog.getSubscriptionOrThrowException(subscriptionCode);
            if (! subscription.isActive()) {
                LOGGER.warn("Inactive subscription {}. Event is {}.", subscriptionCode, inflightEvent.toShortLog());
                LOGGER.warn("Ack (due to inactive subscription) for message {}. Event is {}.", message.getMessageId(), inflightEvent.toShortLog());
                consumer.acknowledge(message);
                recordEventInDLQ(inflightEvent);
                return; // *** PAY ATTENTION, THERE IS A RETURN HERE !!! ***
            }

            String eventTypeCode = subscription.getEventTypeCode();
            EventType eventType = catalog.getEventTypeOrThrowException(eventTypeCode);
            if (! eventType.isActive()) {
                LOGGER.warn("Inactive event type {}. Event is {}.", eventTypeCode, inflightEvent.toShortLog());
                LOGGER.warn("Ack (due to inactive event type) for message {}. Event is {}.", message.getMessageId(), inflightEvent.toShortLog());
                consumer.acknowledge(message);
                recordEventInDLQ(inflightEvent);
                return; // *** PAY ATTENTION, THERE IS A RETURN HERE !!! ***
            }

            boolean channelOk = subscription.getChannel() == null || subscription.getChannel().equalsIgnoreCase(inflightEvent.getChannel());
            if (! channelOk) {
                LOGGER.warn("Not matching channel {}. Event is {}", inflightEvent.getChannel(), inflightEvent.toShortLog());
                LOGGER.warn("Ack (due to not matching channel) for message {}. Event is {}.", message.getMessageId(), inflightEvent.toShortLog());
                consumer.acknowledge(message);
                // DO NOT recordEventInDLQ(inflightEvent) for an unmatched channel !
                return; // *** PAY ATTENTION, THERE IS A RETURN HERE !!! ***
            }

            inflightEvent.setWebhookUrl(subscription.getWebhookUrl());
            inflightEvent.setWebhookContentType(subscription.getWebhookContentType());
            inflightEvent.setWebhookHeaders(subscription.getWebhookHeaders());
            inflightEvent.setAuthMethod(subscription.getAuthMethod());
            inflightEvent.setAuthClientId(subscription.getAuthClientId());
            inflightEvent.setAuthClientSecret(subscription.getAuthClientSecret());
            inflightEvent.setAuthScope(subscription.getAuthScope());
            inflightEvent.setSecret(subscription.getSecret());

            try {
                inflightEvent = callSubscriptionAdapter(inflightEvent);
            } catch (Exception ex) {
                // No Need to log the error since it has already been logged in callSubscriptionAdapter()
                LOGGER.warn("Negative ack (due to exception) for message {}. Event is {}.",
                        message.getMessageId(), inflightEvent.toShortLog());
                consumer.negativeAcknowledge(message);
                metricsService.registerFailedDeliveryAttempt(
                        inflightEvent.getSubscriptionCode(), inflightEvent.getEventTypeCode(), inflightEvent.getPublicationCode());
                return; // *** PAY ATTENTION, THERE IS A RETURN HERE !!! ***
            }

            HttpStatus httpStatusReturnedByWebhook = HttpStatus.INTERNAL_SERVER_ERROR;
            try {
                httpStatusReturnedByWebhook = HttpStatus.valueOf(inflightEvent.getWebhookHttpStatus());
            } catch (Exception ex) {
                LOGGER.error("Unknown http status code {}. Event is {}.",
                        inflightEvent.getWebhookHttpStatus(), inflightEvent.toShortLog(), ex);
                LOGGER.warn("Negative ack (due to unknown http status code) for message {}. Event is {}.",
                        message.getMessageId(), inflightEvent.toShortLog());
                consumer.negativeAcknowledge(message);
                metricsService.registerFailedDeliveryAttempt(
                        inflightEvent.getSubscriptionCode(), inflightEvent.getEventTypeCode(), inflightEvent.getPublicationCode());
                return; // *** PAY ATTENTION, THERE IS A RETURN HERE !!! ***
            }

            if (! httpStatusReturnedByWebhook.is2xxSuccessful()) {
                LOGGER.warn("Negative ack (due to unsuccessful http status code) for message {}. " +
                                "Http status code is {}. Event is {}.",
                        message.getMessageId(), httpStatusReturnedByWebhook.value(), inflightEvent.toShortLog());
                consumer.negativeAcknowledge(message);
                metricsService.registerFailedDeliveryAttempt(
                        inflightEvent.getSubscriptionCode(), inflightEvent.getEventTypeCode(), inflightEvent.getPublicationCode());
                return; // *** PAY ATTENTION, THERE IS A RETURN HERE !!! ***
            }

            // If we reached this line, everything seems fine, so we can ack the message
            LOGGER.debug("Ack for message {}. Event is {}.", message.getMessageId(), inflightEvent.toShortLog());
            consumer.acknowledge(message);
            metricsService.registerSuccessfulDelivery(
                    inflightEvent.getSubscriptionCode(), inflightEvent.getEventTypeCode(), inflightEvent.getPublicationCode());

        } catch (Exception ex) {
            LOGGER.error("Error while handling Pulsar message. Message id is {}. Event is {}", (message != null ?
                    message.getMessageId() : "null"), (inflightEvent != null ? inflightEvent.toShortLog() : "null"), ex);
            LOGGER.warn("Negative ack (due to exception) for message {}. Event is {}.", (message != null ?
                    message.getMessageId() : "null"), (inflightEvent != null ? inflightEvent.toShortLog() : "null"));
            consumer.negativeAcknowledge(message);
            if (inflightEvent != null) {
                metricsService.registerFailedDeliveryAttempt(
                        inflightEvent.getSubscriptionCode(), inflightEvent.getEventTypeCode(), inflightEvent.getPublicationCode());
            }
        }
    }

    // This operation can throw a BrokerException
    private InflightEvent callSubscriptionAdapter(InflightEvent inflightEvent) {
        ResponseEntity<InflightEvent> response = null;
        String subscriptionAdapterUrl = config.getSubscriptionAdapterUrl() + "/webhooks";

        try {
            HttpHeaders httpHeaders = new HttpHeaders();
            httpHeaders.setContentType(MediaType.APPLICATION_JSON);
            httpHeaders.setBasicAuth(
                    config.getAuthClientIdForSubscriptionAdapter(),
                    config.getAuthClientSecretForSubscriptionAdapter());
            // charset UTF8 has been defined during the creation of RestTemplate

            HttpEntity<InflightEvent> request = new HttpEntity<>(inflightEvent, httpHeaders);

            LOGGER.debug("Calling the Subscription Adapter at {}. Event is {}.",
                    subscriptionAdapterUrl, inflightEvent.cloneWithoutSensitiveData());
            response = restTemplate.exchange(subscriptionAdapterUrl, HttpMethod.POST, request, InflightEvent.class);
            LOGGER.debug("The Subscription Adapter returned the http status code {}. Event is {}.",
                    response.getStatusCode(), inflightEvent.toShortLog());

            if (! response.getStatusCode().is2xxSuccessful()) {
                LOGGER.error("The Subscription Adapter returned the unsuccessful http status code {}. Event is {}.",
                        response.getStatusCode(), inflightEvent.toShortLog());
            }

            inflightEvent.setWebhookHttpStatus(response.getStatusCodeValue());
            LOGGER.debug("Returning the event {}", inflightEvent.cloneWithoutSensitiveData());
            return inflightEvent;

        } catch (HttpClientErrorException ex) {
            String msg = String.format("Error while calling the subscription adapter at %s. Event is %s.",
                    subscriptionAdapterUrl, inflightEvent.toShortLog());
            LOGGER.error(msg, ex);
            throw new BrokerException(ex.getStatusCode(), msg, ex, subscriptionAdapterUrl);
        } catch (Exception ex) {
            String msg = String.format("Error while handling the call to the subscription adapter at %s. Event is %s.",
                    subscriptionAdapterUrl, inflightEvent.toShortLog());
            LOGGER.error(msg, ex);
            throw new BrokerException(HttpStatus.INTERNAL_SERVER_ERROR, msg, ex, subscriptionAdapterUrl);
        }
    }

    private void recordEventInDLQ(InflightEvent event) {
        if (event == null) return;
        try {
            event = event.cloneWithoutSensitiveData();
            LOGGER.warn("Recording event in the DLQ for eventTypeCode {}. Event is {}.",
                    event.getEventTypeCode(), event.toShortLog());
            Producer<InflightEvent> producer = getPulsarProducerForDLQ(event.getEventTypeCode());
            producer.send(event);
        } catch (Exception ex) {
            LOGGER.error("Error while recording an event in the DLQ for eventTypeCode {}. Event is {}.",
                    event.getEventTypeCode(), event.toShortLog(), ex);
        }
    }

    private synchronized Producer<InflightEvent> getPulsarProducerForDLQ(String eventTypeCode) {
        Producer<InflightEvent> producer = eventTypeCodeToPulsarProducerForDLQMap.computeIfAbsent(eventTypeCode, x -> {
            try {
                LOGGER.info("Creating Pulsar producer for DLQ for eventTypeCode {}", eventTypeCode);
                Producer<InflightEvent> p =  pulsar.newProducer(Schema.JSON(InflightEvent.class))
                        .topic(eventTypeCode + "_AppDLQ")
                        .create();
                if (p != null) {
                    LOGGER.info("Pulsar producer for DLQ created for eventTypeCode {}", eventTypeCode);
                }
                return p;
            } catch (Exception ex) {
                String msg = String.format("Error while creating a Pulsar producer for DLQ for eventTypeCode %s", eventTypeCode);
                LOGGER.error(msg, ex);
                return null;
            }
        });
        if (producer == null) {
            // No Need to log the error since it has already been logged in eventTypeCodeToPulsarProducerForDLQMap.computeIfAbsent
            String msg = String.format("Error while creating a Pulsar producer for DLQ for eventTypeCode %s", eventTypeCode);
            throw new BrokerException(HttpStatus.INTERNAL_SERVER_ERROR, msg);
        }
        return producer;
    }
}
