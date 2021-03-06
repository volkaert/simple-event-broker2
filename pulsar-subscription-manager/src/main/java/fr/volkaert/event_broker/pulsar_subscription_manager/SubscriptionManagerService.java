package fr.volkaert.event_broker.pulsar_subscription_manager;

import fr.volkaert.event_broker.catalog_client.CatalogClient;
import fr.volkaert.event_broker.error.BrokerException;
import fr.volkaert.event_broker.model.EventType;
import fr.volkaert.event_broker.model.InflightEvent;
import fr.volkaert.event_broker.model.Subscription;
import fr.volkaert.event_broker.telemetry.TelemetryService;
import org.apache.pulsar.client.api.*;
import org.apache.pulsar.client.api.schema.SchemaDefinition;
import org.apache.pulsar.client.internal.DefaultImplementation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
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
    TelemetryService telemetryService;

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
                        String eventTypeCode = subscription.getEventTypeCode();
                        if (shouldTheEventBeManagedByThisInstanceOfSubscriptionManager(eventTypeCode)) {
                            getPulsarConsumer(eventTypeCode, subscription.getCode());
                        }
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
                //Schema<InflightEvent> schema = Schema.JSON(InflightEvent.class);
                Schema<InflightEvent> schema = DefaultImplementation.newJSONSchema(SchemaDefinition.builder().withJSR310ConversionEnabled(true).withPojo(InflightEvent.class).build());
                Consumer<InflightEvent> c = pulsar.newConsumer(schema)
                        .topic(eventTypeCode)
                        .subscriptionName(subscriptionCode)
                        .subscriptionType(SubscriptionType.Failover)
                        //.ackTimeout(config.getReadTimeoutInSecondsForSubscriptionAdapter(), TimeUnit.SECONDS)
                        .messageListener((cons, msg) ->  {
                            try {
                                handlePulsarMessageAndAck(cons, msg);
                            } catch (Exception ex) {    // should never happen...
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
        Instant deliveryStart = Instant.now();

        InflightEvent inflightEvent = null;

        try {
            LOGGER.debug("Message received from Pulsar. Message is {}.", new String(message.getData()));

            inflightEvent = message.getValue();

            String subscriptionCode = consumer.getSubscription();
            inflightEvent.setSubscriptionCode(subscriptionCode);

            telemetryService.eventDeliveryRequested(inflightEvent);

            // Strange: message.getRedeliveryCount() is always 0 !!!
            LOGGER.warn("********** message.getRedeliveryCount() is always 0 !!! **********: {}", message.getRedeliveryCount());
            inflightEvent.setRedelivered(message.getRedeliveryCount() >= 1);
            inflightEvent.setRedeliveryCount(message.getRedeliveryCount());

            LOGGER.debug("Event received from Pulsar. Event is {}.", inflightEvent.cloneWithoutSensitiveData());

            boolean shouldContinue = checkConditionsForEventDeliveryAreMetOrAbort(inflightEvent, consumer, message, deliveryStart);
            if (! shouldContinue) {
                return; // *** PAY ATTENTION, THERE IS A RETURN HERE !!! ***
            }

            Subscription subscription = catalog.getSubscription(subscriptionCode);

            inflightEvent.setWebhookUrl(subscription.getWebhookUrl());
            inflightEvent.setWebhookContentType(subscription.getWebhookContentType());
            inflightEvent.setWebhookHeaders(subscription.getWebhookHeaders());
            inflightEvent.setAuthMethod(subscription.getAuthMethod());
            inflightEvent.setAuthClientId(subscription.getAuthClientId());
            inflightEvent.setAuthClientSecret(subscription.getAuthClientSecret());
            inflightEvent.setAuthScope(subscription.getAuthScope());
            inflightEvent.setSecret(subscription.getSecret());

            telemetryService.eventDeliveryAttempted(inflightEvent);
            try {
                inflightEvent = callSubscriptionAdapter(inflightEvent);
            } catch (Exception ex) {
                // No Need to log the error since it has already been logged in callSubscriptionAdapter()
                LOGGER.warn("Negative ack (due to exception while calling the Subscription Adapter) for message {}. Event is {}.",
                        message.getMessageId(), inflightEvent.toShortLog());
                consumer.negativeAcknowledge(message);
                telemetryService.eventDeliveryFailed(inflightEvent, ex, deliveryStart);
                return; // *** PAY ATTENTION, THERE IS A RETURN HERE !!! ***
            }

            if (inflightEvent.isWebhookConnectionErrorOccurred() ||
                    inflightEvent.isWebhookReadTimeoutErrorOccurred() ||
                    inflightEvent.isWebhookServer5xxErrorOccurred() ||
                    inflightEvent.isWebhookClient4xxErrorOccurred()) {
                try {
                    handleWebhookErrorOccurred(inflightEvent, consumer, message, deliveryStart, subscription);
                } catch (Exception ex) {
                    LOGGER.error("Error while handling a webhook error", ex);
                }
                telemetryService.eventDeliveryFailed(inflightEvent, null, deliveryStart);
                return; // *** PAY ATTENTION, THERE IS A RETURN HERE !!! ***
            }

            // The following lines handle the special case where the webhook returned a 3xx HTTP status code (Redirect)
            // for example because the webhook returned a redirect to a login page (it can happen with default config
            // of some security modules)
            if (! (inflightEvent.getWebhookHttpStatus() >= 200 && inflightEvent.getWebhookHttpStatus() < 300)) {
                LOGGER.warn("Negative ack (due to unsuccessful http status {} returned by the webhook) for message {}. Event is {}.",
                        inflightEvent.getWebhookHttpStatus(), message.getMessageId(), inflightEvent.toShortLog());
                consumer.negativeAcknowledge(message);
                telemetryService.eventDeliveryFailed(inflightEvent, null, deliveryStart);
                return; // *** PAY ATTENTION, THERE IS A RETURN HERE !!! ***
            }

            // If we reached this line, everything seems fine, so we can ack the message
            LOGGER.debug("Ack for message {}. Event is {}.", message.getMessageId(), inflightEvent.toShortLog());
            try {
                consumer.acknowledge(message);
            } catch (PulsarClientException ex) {
                LOGGER.error("Error while acknowledging Pulsar message", ex);
            }
            telemetryService.eventDeliverySucceeded(inflightEvent, deliveryStart);

        } catch (Exception ex) {    // a global catch is mandatory because no exception should be raised/thrown by this operation !
            LOGGER.error("Error while handling Pulsar message. Message id is {}. Event is {}", (message != null ?
                    message.getMessageId() : "null"), (inflightEvent != null ? inflightEvent.toShortLog() : "null"), ex);
            LOGGER.warn("Negative ack (due to exception) for message {}. Event is {}.", (message != null ?
                    message.getMessageId() : "null"), (inflightEvent != null ? inflightEvent.toShortLog() : "null"));
            consumer.negativeAcknowledge(message);
            if (inflightEvent != null) {
                telemetryService.eventDeliveryFailed(inflightEvent, null, deliveryStart);
            }
        }
    }

    private boolean checkConditionsForEventDeliveryAreMetOrAbort(InflightEvent inflightEvent, Consumer<InflightEvent> consumer,
                                                                 Message<InflightEvent> message, Instant deliveryStart) {
        String subscriptionCode = inflightEvent.getSubscriptionCode();
        boolean eventExpired = deliveryStart.isAfter(inflightEvent.getExpirationDate());
        if (eventExpired) {
            telemetryService.eventDeliveryAbortedDueToExpiredEvent(inflightEvent);
            LOGGER.warn("Ack (due to expired event) for message {}. Event is {}.", message.getMessageId(), inflightEvent.toShortLog());
            try {
                consumer.acknowledge(message);
            } catch (PulsarClientException ex) {
                LOGGER.error("Error while acknowledging Pulsar message", ex);
            }
            recordEventInDLQ(inflightEvent);
            return false; // *** PAY ATTENTION, THERE IS A RETURN HERE !!! ***
        }

        Subscription subscription = catalog.getSubscription(subscriptionCode);
        if (subscription == null) {
            String msg = telemetryService.eventDeliveryAbortedDueToInvalidSubscriptionCode(inflightEvent);
            throw new BrokerException(HttpStatus.INTERNAL_SERVER_ERROR, msg);
        }

        if (! subscription.isActive()) {
            telemetryService.eventDeliveryAbortedDueToInactiveSubscription(inflightEvent);
            LOGGER.warn("Ack (due to inactive subscription) for message {}. Event is {}.", message.getMessageId(), inflightEvent.toShortLog());
            try {
                consumer.acknowledge(message);
            } catch (PulsarClientException ex) {
                LOGGER.error("Error while acknowledging Pulsar message", ex);
            }
            recordEventInDLQ(inflightEvent);
            return false; // *** PAY ATTENTION, THERE IS A RETURN HERE !!! ***
        }

        String eventTypeCode = subscription.getEventTypeCode();
        EventType eventType = catalog.getEventType(eventTypeCode);
        if (eventType == null) {
            String msg = telemetryService.eventDeliveryAbortedDueToInvalidEventTypeCode(inflightEvent);
            throw new BrokerException(HttpStatus.INTERNAL_SERVER_ERROR, msg);
        }

        if (! eventType.isActive()) {
            telemetryService.eventDeliveryAbortedDueToInactiveEventType(inflightEvent);
            LOGGER.warn("Ack (due to inactive event type) for message {}. Event is {}.", message.getMessageId(), inflightEvent.toShortLog());
            try {
                consumer.acknowledge(message);
            } catch (PulsarClientException ex) {
                LOGGER.error("Error while acknowledging Pulsar message", ex);
            }
            recordEventInDLQ(inflightEvent);
            return false; // *** PAY ATTENTION, THERE IS A RETURN HERE !!! ***
        }

        boolean channelOk = subscription.getChannel() == null || subscription.getChannel().equalsIgnoreCase(inflightEvent.getChannel());
        if (! channelOk) {
            telemetryService.eventDeliveryAbortedDueToNotMatchingChannel(inflightEvent);
            LOGGER.warn("Ack (due to not matching channel) for message {}. Event is {}.", message.getMessageId(), inflightEvent.toShortLog());
            try {
                consumer.acknowledge(message);
            } catch (PulsarClientException ex) {
                LOGGER.error("Error while acknowledging Pulsar message", ex);
            }
            // DO NOT recordEventInDLQ(inflightEvent) for an unmatched channel !
            return false; // *** PAY ATTENTION, THERE IS A RETURN HERE !!! ***
        }

        return true; // true means the caller should continue its code flow
    }

    private void handleWebhookErrorOccurred(InflightEvent inflightEvent, Consumer<InflightEvent> consumer,
                                               Message<InflightEvent> message, Instant deliveryStart, Subscription subscription) {
        boolean eventExpiredDueToTimeToLiveForWebhookError = false;
        String eventExpirationReason = null;

        if (inflightEvent.isWebhookConnectionErrorOccurred()) {
            eventExpiredDueToTimeToLiveForWebhookError = isEventExpiredDueToTimeToLiveForWebhookError(inflightEvent, deliveryStart,
                    config.getDefaultTimeToLiveInSecondsForWebhookConnectionError(),
                    subscription.getTimeToLiveInSecondsForWebhookConnectionError());
            eventExpirationReason = "connection";
        }
        else if (inflightEvent.isWebhookReadTimeoutErrorOccurred()) {
            eventExpiredDueToTimeToLiveForWebhookError = isEventExpiredDueToTimeToLiveForWebhookError(inflightEvent, deliveryStart,
                    config.getDefaultTimeToLiveInSecondsForWebhookReadTimeoutError(),
                    subscription.getTimeToLiveInSecondsForWebhookReadTimeoutError());
            eventExpirationReason = "read timeout";
        }
        else if (inflightEvent.isWebhookServer5xxErrorOccurred()) {
            eventExpiredDueToTimeToLiveForWebhookError = isEventExpiredDueToTimeToLiveForWebhookError(inflightEvent, deliveryStart,
                    config.getDefaultTimeToLiveInSecondsForWebhookServer5xxError(),
                    subscription.getTimeToLiveInSecondsForWebhookServer5xxError());
            eventExpirationReason = "server 5xx";
        }
        else if (inflightEvent.isWebhookClient4xxErrorOccurred()) {
            if (inflightEvent.getWebhookHttpStatus() == HttpStatus.UNAUTHORIZED.value() ||
                    inflightEvent.getWebhookHttpStatus() == HttpStatus.FORBIDDEN.value()) {
                eventExpiredDueToTimeToLiveForWebhookError = isEventExpiredDueToTimeToLiveForWebhookError(inflightEvent, deliveryStart,
                        config.getDefaultTimeToLiveInSecondsForWebhookAuth401Or403Error(),
                        subscription.getTimeToLiveInSecondsForWebhookAuth401Or403Error());
                eventExpirationReason = "auth 401 or 403";
            }
            else {
                eventExpiredDueToTimeToLiveForWebhookError = isEventExpiredDueToTimeToLiveForWebhookError(inflightEvent, deliveryStart,
                        config.getDefaultTimeToLiveInSecondsForWebhookClient4xxError(),
                        subscription.getTimeToLiveInSecondsForWebhookClient4xxError());
                eventExpirationReason = "client 4xx";
            }
        }

        if (eventExpiredDueToTimeToLiveForWebhookError) {
            LOGGER.warn("Event expired before delivery due to time to live expiration because of a webhook {} error. Event is {}.",
                    eventExpirationReason, inflightEvent.toShortLog());
            LOGGER.warn("Ack (due to expired event) for message {}. Event is {}.", message.getMessageId(), inflightEvent.toShortLog());
            try {
                consumer.acknowledge(message);
            } catch (PulsarClientException ex) {
                LOGGER.error("Error while acknowledging Pulsar message", ex);
            }
            recordEventInDLQ(inflightEvent);
        }
        else {
            LOGGER.warn("Negative ack (due to webhook error) for message {}. Event is {}.",
                    message.getMessageId(), inflightEvent.toShortLog());
            consumer.negativeAcknowledge(message);
        }
    }

    // This operation can throw a BrokerException
    private InflightEvent callSubscriptionAdapter(InflightEvent inflightEvent) {
        String subscriptionAdapterUrl = config.getSubscriptionAdapterUrl() + "/webhooks";

        HttpHeaders httpHeaders = new HttpHeaders();

        httpHeaders.setContentType(MediaType.APPLICATION_JSON);

        if (!StringUtils.isEmpty(config.getAuthClientIdForSubscriptionAdapter()) && !StringUtils.isEmpty(config.getAuthClientSecretForSubscriptionAdapter())) {
            httpHeaders.setBasicAuth(
                    config.getAuthClientIdForSubscriptionAdapter(),
                    config.getAuthClientSecretForSubscriptionAdapter());
        } else {
            LOGGER.warn("No Basic Auth credentials provided to access the Subscription Adapter");
        }

        // charset UTF8 has been defined during the creation of RestTemplate

        HttpEntity<InflightEvent> request = new HttpEntity<>(inflightEvent, httpHeaders);

        try {

            LOGGER.debug("Calling the Subscription Adapter at {}. Event is {}.",
                    subscriptionAdapterUrl, inflightEvent.cloneWithoutSensitiveData());
            ResponseEntity<InflightEvent> response = restTemplate.exchange(
                    subscriptionAdapterUrl, HttpMethod.POST, request, InflightEvent.class);
            LOGGER.debug("The Subscription Adapter returned the http status code {}. Event is {}.",
                    response.getStatusCode(), inflightEvent.toShortLog());

            InflightEvent returnedInflightEvent = response.getBody();
            LOGGER.debug("Returning the event {}", returnedInflightEvent != null ? returnedInflightEvent.cloneWithoutSensitiveData() : null);
            return returnedInflightEvent;

        } catch (HttpClientErrorException ex) {
            String msg = String.format("Client error %s while calling the Subscription Adapter at %s. Event is %s.",
                    ex.getStatusCode(), subscriptionAdapterUrl, inflightEvent.toShortLog());
            LOGGER.error(msg, ex);
            throw new BrokerException(ex.getStatusCode(), msg, ex, subscriptionAdapterUrl);

        } catch (HttpServerErrorException ex) {
            String msg = String.format("Server error %s while calling the Subscription Adapter at %s. Event is %s.",
                    ex.getStatusCode(), subscriptionAdapterUrl, inflightEvent.toShortLog());
            LOGGER.error(msg, ex);
            throw new BrokerException(ex.getStatusCode(), msg, ex, subscriptionAdapterUrl);

        } catch (Exception ex) {
            if (ex.getMessage().contains("Connection refused")) {
                String msg = String.format("Connection Refused error while calling the Subscription Adapter at %s. Event is %s.",
                        subscriptionAdapterUrl, inflightEvent.toShortLog());
                LOGGER.error(msg, ex);
                throw new BrokerException(HttpStatus.BAD_GATEWAY, msg, ex, subscriptionAdapterUrl);
            }

            else if (ex.getMessage().contains("Read timed out")) {
                String msg = String.format("Read Timeout error while calling the Subscription Adapter at %s. Event is %s.",
                        subscriptionAdapterUrl, inflightEvent.toShortLog());
                LOGGER.error(msg, ex);
                throw new BrokerException(HttpStatus.GATEWAY_TIMEOUT, msg, ex, subscriptionAdapterUrl);
            }

            else {
                String msg = String.format("Error while calling the Subscription Adapter at %s. Event is %s.",
                        subscriptionAdapterUrl, inflightEvent.toShortLog());
                LOGGER.error(msg, ex);
                throw new BrokerException(HttpStatus.INTERNAL_SERVER_ERROR, msg, ex, subscriptionAdapterUrl);
            }
        }
    }

    private void recordEventInDLQ(InflightEvent event) {
        if (event == null) return;
        try {
            event = event.cloneWithoutSensitiveData();
            LOGGER.warn("Recording event in the DLQ for eventTypeCode {} and subscriptionCode {}. Event is {}.",
                    event.getEventTypeCode(), event.getSubscriptionCode(), event.toShortLog());
            Producer<InflightEvent> producer = getPulsarProducerForDLQ(event.getEventTypeCode(), event.getSubscriptionCode());
            producer.send(event);
        } catch (Exception ex) {
            LOGGER.error("Error while recording an event in the DLQ for eventTypeCode {} and subscriptionCode {}. Event is {}.",
                    event.getEventTypeCode(), event.getSubscriptionCode(), event.toShortLog(), ex);
        }
    }

    private synchronized Producer<InflightEvent> getPulsarProducerForDLQ(String eventTypeCode, String subscriptionCode) {
        Producer<InflightEvent> producer = eventTypeCodeToPulsarProducerForDLQMap.computeIfAbsent(eventTypeCode, x -> {
            try {
                LOGGER.info("Creating Pulsar producer for DLQ for eventTypeCode {} and subscriptionCode {}",
                        eventTypeCode, subscriptionCode);
                //Schema<InflightEvent> schema = Schema.JSON(InflightEvent.class);
                Schema<InflightEvent> schema = DefaultImplementation.newJSONSchema(SchemaDefinition.builder().withJSR310ConversionEnabled(true).withPojo(InflightEvent.class).build());
                Producer<InflightEvent> p =  pulsar.newProducer(schema)
                        .topic(eventTypeCode + "_" + subscriptionCode + "_AppDLQ")
                        .create();
                if (p != null) {
                    LOGGER.info("Pulsar producer for DLQ created for eventTypeCode {} and subscriptionCode {}",
                            eventTypeCode, subscriptionCode);
                }
                return p;
            } catch (Exception ex) {
                String msg = String.format("Error while creating a Pulsar producer for DLQ for eventTypeCode %s and subscriptionCode %s",
                        eventTypeCode, subscriptionCode);
                LOGGER.error(msg, ex);
                return null;
            }
        });
        if (producer == null) {
            // No Need to log the error since it has already been logged in eventTypeCodeToPulsarProducerForDLQMap.computeIfAbsent
            String msg = String.format("Error while creating a Pulsar producer for DLQ for eventTypeCode %s ans subscriptionCode %s", eventTypeCode, subscriptionCode);
            throw new BrokerException(HttpStatus.INTERNAL_SERVER_ERROR, msg);
        }
        return producer;
    }

    private boolean isEventExpiredDueToTimeToLiveForWebhookError(InflightEvent event, Instant now, long defaultTimeToLiveForWebhookError, Long timeToLiveForWebhookErrorInSubscription) {
        long timeToLiveInSecondsToUse = defaultTimeToLiveForWebhookError;
        if (timeToLiveForWebhookErrorInSubscription != null && timeToLiveForWebhookErrorInSubscription > 0) {
            timeToLiveInSecondsToUse = timeToLiveForWebhookErrorInSubscription;
        }
        return now.isAfter(event.getCreationDate().plusSeconds(timeToLiveInSecondsToUse));
    }

    private boolean shouldTheEventBeManagedByThisInstanceOfSubscriptionManager(String eventTypeCode) {
        int sumOfAsciiCodesOfCharsOfEventTypeCode = 0;
        int eventTypeCodeLength = eventTypeCode.length();
        for (int i = 0; i < eventTypeCodeLength; i++) {
            sumOfAsciiCodesOfCharsOfEventTypeCode += eventTypeCode.charAt(i);
        }
        int indexOfTheInstanceOfTheInstanceOfSubscriptionManagerThatShouldManageThisEvent = sumOfAsciiCodesOfCharsOfEventTypeCode % config.getClusterSize();
        return indexOfTheInstanceOfTheInstanceOfSubscriptionManagerThatShouldManageThisEvent == config.getClusterIndex();
    }
}
