package fr.volkaert.event_broker.telemetry;

import fr.volkaert.event_broker.model.InflightEvent;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class TelemetryService {

    @Autowired
    MeterRegistry meterRegistry;

    private static final Logger LOGGER = LoggerFactory.getLogger(TelemetryService.class);

    private final Map<String, AtomicLong> pendingPublicationGauges = new ConcurrentHashMap<>();
    //private final Map<String, AtomicLong> pendingDeliveriesGauges = new ConcurrentHashMap<>();


    // PUBLICATION /////////////////////////////////////////////////////////////////////////////////////////////////////


    public synchronized String eventPublicationRequested(InflightEvent event) {
        String msg = "";
        try {
            msg = String.format("Event publication requested. Event is %s.", event);
            LOGGER.debug(msg);
        } catch (Exception ex) {
            LOGGER.error("Error while recording log for eventPublicationRequested", ex);
        }
        try {
            Counter counter1 = meterRegistry.counter("event_publications_requested_total");
            counter1.increment();
        } catch (Exception ex) {
            LOGGER.error("Error while recording metrics for eventPublicationRequested", ex);
        }
        return msg;
    }

    public synchronized String eventPublicationRejectedDueToMissingPublicationCode(InflightEvent event) {
        String msg = "";
        try {
            msg = String.format("Event publication rejected due to missing publication code. Event is %s.", event.toShortLog());
            LOGGER.error(msg);
        } catch (Exception ex) {
            LOGGER.error("Error while recording log for eventPublicationRejectedDueToMissingPublicationCode", ex);
        }
        try {
            Counter counter1 = meterRegistry.counter("event_publications_rejected_total");
            counter1.increment();
            Counter counter2 = meterRegistry.counter("event_publications_rejected_due_to_missing_publication_code_total");
            counter2.increment();
        } catch (Exception ex) {
            LOGGER.error("Error while recording metrics for eventPublicationRejectedDueToMissingPublicationCode", ex);
        }
        return msg;
    }

    public synchronized String eventPublicationRejectedDueToInvalidPublicationCode(InflightEvent event) {
        String msg = "";
        try {
            String publicationCode = event.getPublicationCode();
            msg = String.format("Event publication rejected due to invalid publication code '%s'. Event is %s.", publicationCode, event.toShortLog());
            LOGGER.error(msg);
        } catch (Exception ex) {
            LOGGER.error("Error while recording log for eventPublicationRejectedDueToInvalidPublicationCode", ex);
        }
        try {
            Counter counter1 = meterRegistry.counter("event_publications_rejected_total");
            counter1.increment();
            // do NOT use publicationCode as tag because in case of an unknown publicationCode, its potential values are unbounded !
            Counter counter2 = meterRegistry.counter("event_publications_rejected_due_to_invalid_publication_code_total");
            counter2.increment();
        } catch (Exception ex) {
            LOGGER.error("Error while recording metrics for eventPublicationRejectedDueToInvalidPublicationCode", ex);
        }
        return msg;
    }

    public synchronized String eventPublicationRejectedDueToInactivePublication(InflightEvent event) {
        String msg = "";
        try {
            String publicationCode = event.getPublicationCode();
            msg = String.format("Inactive publication '%s'. Event is %s.", publicationCode, event.toShortLog());
            LOGGER.warn(msg); // WARNING and not ERROR !!
        } catch (Exception ex) {
            LOGGER.error("Error while recording log for eventPublicationRejectedDueToInactivePublication", ex);
        }
        try {
            String publicationCode = event.getPublicationCode();
            Counter counter1 = meterRegistry.counter("event_publications_rejected_total");
            counter1.increment();
            Counter counter2 = meterRegistry.counter("event_publications_rejected_due_to_inactive_publication_total",
                    Tags.of("publication_code", publicationCode));
            counter2.increment();
        } catch (Exception ex) {
            LOGGER.error("Error while recording metrics for eventPublicationRejectedDueToInactivePublication", ex);
        }
        return msg;
    }

    public synchronized String eventPublicationRejectedDueToInvalidEventTypeCode(InflightEvent event) {
        String msg = "";
        try {
            String eventTypeCode = event.getEventTypeCode();
            msg = String.format("Event publication rejected due to invalid event type code '%s'. Event is %s.", eventTypeCode, event.toShortLog());
            LOGGER.error(msg);
        } catch (Exception ex) {
            LOGGER.error("Error while recording log for eventPublicationRejectedDueToInvalidEventTypeCode", ex);
        }
        try {
            Counter counter1 = meterRegistry.counter("event_publications_rejected_total");
            counter1.increment();
            // do NOT use eventTypeCode as tag because in case of an unknown eventTypeCode, its potential values are unbounded !
            Counter counter2 = meterRegistry.counter("event_publications_rejected_due_to_invalid_event_type_code_total");
            counter2.increment();
        } catch (Exception ex) {
            LOGGER.error("Error while recording metrics for eventPublicationRejectedDueToInvalidEventTypeCode", ex);
        }
        return msg;
    }

    public synchronized String eventPublicationAttempted(InflightEvent event) {
        String msg = "";
        try {
            msg = String.format("Event publication attempted. Event is %s.", event);
            LOGGER.debug(msg);
        } catch (Exception ex) {
            LOGGER.error("Error while recording log for eventPublicationAttempted", ex);
        }
        try {
            String publicationCode = event.getPublicationCode();
            String eventTypeCode = event.getEventTypeCode();

            Counter counter1 = meterRegistry.counter("event_publications_attempted_total",
                    Tags.of("publication_code", publicationCode, "event_type_code", eventTypeCode));
            counter1.increment();

            AtomicLong pendingPublications = pendingPublicationGauges.computeIfAbsent(eventTypeCode + "/" + publicationCode, x -> {
                return meterRegistry.gauge("pending_event_publications",
                        Tags.of("publication_code", publicationCode, "event_type_code", eventTypeCode),
                        new AtomicLong(0));
            });
            pendingPublications.incrementAndGet();
        } catch (Exception ex) {
            LOGGER.error("Error while recording metric for eventPublicationAttempted", ex);
        }
        return msg;
    }

    public synchronized String eventPublicationSucceeded(InflightEvent event, Instant publicationStart) {
        String msg = "";
        try {
            msg = String.format("Event publication succeeded. Event is %s.", event.toShortLog());
            LOGGER.debug(msg);
        } catch (Exception ex) {
            LOGGER.error("Error while recording log for eventPublicationSucceeded", ex);
        }
        try {
            String publicationCode = event.getPublicationCode();
            String eventTypeCode = event.getEventTypeCode();

            Counter counter1 = meterRegistry.counter("event_publications_succeeded_total",
                    Tags.of("publication_code", publicationCode, "event_type_code", eventTypeCode));
            counter1.increment();

            AtomicLong pendingPublications = pendingPublicationGauges.computeIfAbsent(eventTypeCode + "/" + publicationCode, x -> {
                return meterRegistry.gauge("pending_event_publications",
                        Tags.of("publication_code", publicationCode, "event_type_code" , eventTypeCode),
                        new AtomicLong(0));
            });
            pendingPublications.decrementAndGet();

            Instant publicationEnd = Instant.now();
            Timer publicationTimer = meterRegistry.timer("event_publication_duration",
                    Tags.of("publication_code", publicationCode, "event_type_code", eventTypeCode));
            publicationTimer.record(Duration.between(publicationStart, publicationEnd).toMillis(), TimeUnit.MILLISECONDS);
        } catch (Exception ex) {
            LOGGER.error("Error while recording metric for eventPublicationSucceeded", ex);
        }
        return msg;
    }

    public synchronized String eventPublicationFailed(InflightEvent event, Exception exception, Instant publicationStart) {
        String msg = "";
        try {
            msg = String.format("Event publication failed. Exception is `%s`. Event is %s.",
                    (exception != null ? exception.getMessage() : ""),  event.toShortLog());
            LOGGER.error(msg, exception);
        } catch (Exception ex) {
            LOGGER.error("Error while recording log for eventPublicationFailed", ex);
        }
        try {
            String publicationCode = event.getPublicationCode();
            String eventTypeCode = event.getEventTypeCode();

            Counter counter1 = meterRegistry.counter("event_publications_failed_total",
                    Tags.of("publication_code", publicationCode, "event_type_code", eventTypeCode));
            counter1.increment();

            AtomicLong pendingPublications = pendingPublicationGauges.computeIfAbsent(eventTypeCode + "/" + publicationCode, x -> {
                return meterRegistry.gauge("pending_event_publications",
                        Tags.of("publication_code", publicationCode, "event_type_code" , eventTypeCode),
                        new AtomicLong(0));
            });
            pendingPublications.decrementAndGet();

            Instant publicationEnd = Instant.now();
            Timer publicationTimer = meterRegistry.timer("event_publication_duration",
                    Tags.of("publication_code", publicationCode, "event_type_code", eventTypeCode));
            publicationTimer.record(Duration.between(publicationStart, publicationEnd).toMillis(), TimeUnit.MILLISECONDS);
        } catch (Exception ex) {
            LOGGER.error("Error while recording metric for eventPublicationFailed", ex);
        }
        return msg;
    }


    // DELIVERY ////////////////////////////////////////////////////////////////////////////////////////////////////////


    public synchronized String eventDeliveryRequested(InflightEvent event) {
        String msg = "";
        try {
            msg = String.format("Event delivery requested. Event is %s.", event);
            LOGGER.debug(msg);
        } catch (Exception ex) {
            LOGGER.error("Error while recording log for eventDeliveryRequested", ex);
        }
        try {
            String subscriptionCode = event.getSubscriptionCode();
            String eventTypeCode = event.getEventTypeCode();
            String publicationCode = event.getPublicationCode();
            Counter counter1 = meterRegistry.counter("event_deliveries_requested_total",
                    Tags.of("subscription_code", subscriptionCode, "event_type_code", eventTypeCode, "publication_code", publicationCode));
            counter1.increment();
        } catch (Exception ex) {
            LOGGER.error("Error while recording metrics for eventDeliveryRequested", ex);
        }
        return msg;
    }

    public synchronized String eventDeliveryAbortedDueToExpiredEvent(InflightEvent event) {
        String msg = "";
        try {
            msg = String.format("Event delivery aborted due to expired event. Event is %s.", event.toShortLog());
            LOGGER.warn(msg);   // WARN and not ERROR !
        } catch (Exception ex) {
            LOGGER.error("Error while recording log for eventDeliveryAbortedDueToExpiredEvent", ex);
        }
        try {
            String subscriptionCode = event.getSubscriptionCode();
            String eventTypeCode = event.getEventTypeCode();
            String publicationCode = event.getPublicationCode();
            Counter counter1 = meterRegistry.counter("event_deliveries_aborted_total",
                    Tags.of("subscription_code", subscriptionCode, "event_type_code", eventTypeCode, "publication_code", publicationCode));
            counter1.increment();
            Counter counter2 = meterRegistry.counter("event_deliveries_aborted_due_to_expired_event_total",
                    Tags.of("subscription_code", subscriptionCode, "event_type_code", eventTypeCode, "publication_code", publicationCode));
            counter2.increment();
        } catch (Exception ex) {
            LOGGER.error("Error while recording metrics for eventDeliveryAbortedDueToExpiredEvent", ex);
        }
        return msg;
    }

    public synchronized String eventDeliveryAbortedDueToInvalidSubscriptionCode(InflightEvent event) {
        String msg = "";
        try {
            String subscriptionCode = event.getSubscriptionCode();
            msg = String.format("Event delivery aborted due to invalid subscription code '%s'. Event is %s.", subscriptionCode, event.toShortLog());
            LOGGER.error(msg);
        } catch (Exception ex) {
            LOGGER.error("Error while recording log for eventDeliveryAbortedDueToInvalidSubscriptionCode", ex);
        }
        try {
            String subscriptionCode = event.getSubscriptionCode();
            String eventTypeCode = event.getEventTypeCode();
            String publicationCode = event.getPublicationCode();
            Counter counter1 = meterRegistry.counter("event_deliveries_aborted_total",
                    Tags.of("subscription_code", subscriptionCode, "event_type_code", eventTypeCode, "publication_code", publicationCode));
            counter1.increment();
            Counter counter2 = meterRegistry.counter("event_deliveries_aborted_due_to_invalid_subscription_code_total",
                    Tags.of("subscription_code", subscriptionCode, "event_type_code", eventTypeCode, "publication_code", publicationCode));
            counter2.increment();
        } catch (Exception ex) {
            LOGGER.error("Error while recording metrics for eventDeliveryAbortedDueToInvalidSubscriptionCode", ex);
        }
        return msg;
    }

    public synchronized String eventDeliveryAbortedDueToInactiveSubscription(InflightEvent event) {
        String msg = "";
        try {
            String subscriptionCode = event.getSubscriptionCode();
            msg = String.format("Event delivery aborted due to inactive subscription '%s'. Event is %s.", subscriptionCode, event.toShortLog());
            LOGGER.warn(msg);   // WARN and not ERROR !
        } catch (Exception ex) {
            LOGGER.error("Error while recording log for eventDeliveryAbortedDueToInactiveSubscription", ex);
        }
        try {
            String subscriptionCode = event.getSubscriptionCode();
            String eventTypeCode = event.getEventTypeCode();
            String publicationCode = event.getPublicationCode();
            Counter counter1 = meterRegistry.counter("event_deliveries_aborted_total",
                    Tags.of("subscription_code", subscriptionCode, "event_type_code", eventTypeCode, "publication_code", publicationCode));
            counter1.increment();
            Counter counter2 = meterRegistry.counter("event_deliveries_aborted_due_to_inactive_subscription_total",
                    Tags.of("subscription_code", subscriptionCode, "event_type_code", eventTypeCode, "publication_code", publicationCode));
            counter2.increment();
        } catch (Exception ex) {
            LOGGER.error("Error while recording metrics for eventDeliveryAbortedDueToInactiveSubscription", ex);
        }
        return msg;
    }

    public synchronized String eventDeliveryAbortedDueToInvalidEventTypeCode(InflightEvent event) {
        String msg = "";
        try {
            String eventTypeCode = event.getEventTypeCode();
            msg = String.format("Event delivery aborted due to invalid event type code '%s'. Event is %s.", eventTypeCode, event.toShortLog());
            LOGGER.error(msg);
        } catch (Exception ex) {
            LOGGER.error("Error while recording log for eventDeliveryAbortedDueToInvalidEventTypeCode", ex);
        }
        try {
            String subscriptionCode = event.getSubscriptionCode();
            String eventTypeCode = event.getEventTypeCode();
            String publicationCode = event.getPublicationCode();
            Counter counter1 = meterRegistry.counter("event_deliveries_aborted_total",
                    Tags.of("subscription_code", subscriptionCode, "event_type_code", eventTypeCode, "publication_code", publicationCode));
            counter1.increment();
            Counter counter2 = meterRegistry.counter("event_deliveries_aborted_due_to_invalid_event_type_code_total",
                    Tags.of("subscription_code", subscriptionCode, "event_type_code", eventTypeCode, "publication_code", publicationCode));
            counter2.increment();
        } catch (Exception ex) {
            LOGGER.error("Error while recording metrics for eventDeliveryAbortedDueToInvalidEventTypeCode", ex);
        }
        return msg;
    }

    public synchronized String eventDeliveryAbortedDueToInactiveEventType(InflightEvent event) {
        String msg = "";
        try {
            String eventTypeCode = event.getEventTypeCode();
            msg = String.format("Event delivery aborted due to inactive event type '%s'. Event is %s.", eventTypeCode, event.toShortLog());
            LOGGER.warn(msg);   // WARN and not ERROR !
        } catch (Exception ex) {
            LOGGER.error("Error while recording log for eventDeliveryAbortedDueToInactiveEventType", ex);
        }
        try {
            String subscriptionCode = event.getSubscriptionCode();
            String eventTypeCode = event.getEventTypeCode();
            String publicationCode = event.getPublicationCode();
            Counter counter1 = meterRegistry.counter("event_deliveries_aborted_total",
                    Tags.of("subscription_code", subscriptionCode, "event_type_code", eventTypeCode, "publication_code", publicationCode));
            counter1.increment();
            Counter counter2 = meterRegistry.counter("event_deliveries_aborted_due_to_inactive_event_type_total",
                    Tags.of("subscription_code", subscriptionCode, "event_type_code", eventTypeCode, "publication_code", publicationCode));
            counter2.increment();
        } catch (Exception ex) {
            LOGGER.error("Error while recording metrics for eventDeliveryAbortedDueToInactiveEventType", ex);
        }
        return msg;
    }

    public synchronized String eventDeliveryAbortedDueToNotMatchingChannel(InflightEvent event) {
        String msg = "";
        try {
            String channel = event.getChannel();
            msg = String.format("Event delivery aborted due to not matching channel '%s'. Event is %s.", channel, event.toShortLog());
            LOGGER.warn(msg);   // WARN and not ERROR !
        } catch (Exception ex) {
            LOGGER.error("Error while recording log for eventDeliveryAbortedDueToNotMatchingChannel", ex);
        }
        try {
            String subscriptionCode = event.getSubscriptionCode();
            String eventTypeCode = event.getEventTypeCode();
            String publicationCode = event.getPublicationCode();
            Counter counter1 = meterRegistry.counter("event_deliveries_aborted_total",
                    Tags.of("subscription_code", subscriptionCode, "event_type_code", eventTypeCode, "publication_code", publicationCode));
            counter1.increment();
            Counter counter2 = meterRegistry.counter("event_deliveries_aborted_due_to_not_matching_channel_total",
                    Tags.of("subscription_code", subscriptionCode, "event_type_code", eventTypeCode, "publication_code", publicationCode));
            counter2.increment();
        } catch (Exception ex) {
            LOGGER.error("Error while recording metrics for eventDeliveryAbortedDueToNotMatchingChannel", ex);
        }
        return msg;
    }

    public synchronized String eventDeliveryAttempted(InflightEvent event) {
        String msg = "";
        try {
            msg = String.format("Event delivery attempted. Event is %s.", event);
            LOGGER.debug(msg);
        } catch (Exception ex) {
            LOGGER.error("Error while recording log for eventDeliveryAttempted", ex);
        }

        try {
            String subscriptionCode = event.getSubscriptionCode();
            String eventTypeCode = event.getEventTypeCode();
            String publicationCode = event.getPublicationCode();
            Counter counter1 = meterRegistry.counter("event_deliveries_attempted_total",
                    Tags.of("subscription_code", subscriptionCode, "event_type_code", eventTypeCode, "publication_code", publicationCode));
            counter1.increment();
        } catch (Exception ex) {
            LOGGER.error("Error while recording metric for eventDeliveryAttempted", ex);
        }

        return msg;
    }

    public synchronized String eventDeliverySucceeded(InflightEvent event, Instant deliveryStart) {
        String msg = "";
        try {
            msg = String.format("Event delivery succeeded. Event is %s.", event.toShortLog());
            LOGGER.debug(msg);
        } catch (Exception ex) {
            LOGGER.error("Error while recording log for eventDeliverySucceeded", ex);
        }
        try {
            String subscriptionCode = event.getSubscriptionCode();
            String eventTypeCode = event.getEventTypeCode();
            String publicationCode = event.getPublicationCode();

            Counter counter1 = meterRegistry.counter("event_deliveries_succeeded_total",
                    Tags.of("subscription_code", subscriptionCode, "event_type_code", eventTypeCode, "publication_code", publicationCode));
            counter1.increment();

            Instant deliveryEnd = Instant.now();
            Timer deliveryTimer = meterRegistry.timer("event_delivery_duration",
                    Tags.of("subscription_code", subscriptionCode, "event_type_code", eventTypeCode, "publication_code", publicationCode));
            deliveryTimer.record(Duration.between(deliveryStart, deliveryEnd).toMillis(), TimeUnit.MILLISECONDS);
        } catch (Exception ex) {
            LOGGER.error("Error while recording metric for eventDeliverySucceeded", ex);
        }
        return msg;
    }

    public synchronized String eventDeliveryFailed(InflightEvent event, Exception exception, Instant deliveryStart) {
        String msg = "";
        try {
            msg = String.format("Event delivery failed. Exception is `%s`. Event is %s.",
                    (exception != null ? exception.getMessage() : ""),  event.toShortLog());
            LOGGER.error(msg, exception);
        } catch (Exception ex) {
            LOGGER.error("Error while recording log for eventDeliveryFailed", ex);
        }
        try {
            String subscriptionCode = event.getSubscriptionCode();
            String eventTypeCode = event.getEventTypeCode();
            String publicationCode = event.getPublicationCode();

            Counter counter1 = meterRegistry.counter("event_deliveries_failed_total",
                    Tags.of("subscription_code", subscriptionCode, "event_type_code", eventTypeCode, "publication_code", publicationCode));
            counter1.increment();

            Instant deliveryEnd = Instant.now();
            Timer deliveryTimer = meterRegistry.timer("event_delivery_duration",
                    Tags.of("subscription_code", subscriptionCode, "event_type_code", eventTypeCode, "publication_code", publicationCode));
            deliveryTimer.record(Duration.between(deliveryStart, deliveryEnd).toMillis(), TimeUnit.MILLISECONDS);
        } catch (Exception ex) {
            LOGGER.error("Error while recording metric for eventDeliveryFailed", ex);
        }
        return msg;
    }
}
