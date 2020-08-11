package fr.volkaert.event_broker.metrics;

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
public class MetricsService {

    @Autowired
    MeterRegistry meterRegistry;

    private static final Logger LOGGER = LoggerFactory.getLogger(MetricsService.class);

    private final Map<String, AtomicLong> pendingPublicationGauges = new ConcurrentHashMap<>();
    //private final Map<String, AtomicLong> pendingDeliveriesGauges = new ConcurrentHashMap<>();

    public synchronized void registerPublicationWithMissingPublicationCode() {
        Counter counter = meterRegistry.counter("publications_with_missing_publication_code_total");
        counter.increment();
    }

    public synchronized void registerPublicationWithInvalidPublicationCode(String publicationCode) {
        // do NOT use publicationCode as tag because in vase of an unknown publicationCode, its potential values are unbounded !
        Counter counter = meterRegistry.counter("publications_with_invalid_publication_code_total");
        counter.increment();
    }

    public synchronized Instant registerBeginOfPublication(String publicationCode, String eventTypeCode) {
        Counter publicationsCounter = meterRegistry.counter("publications_total",
                Tags.of("publication_code", publicationCode,  "event_type_code", eventTypeCode));
        publicationsCounter.increment();

        AtomicLong pendingPublications = pendingPublicationGauges.computeIfAbsent(eventTypeCode + "/" + publicationCode, x -> {
            return meterRegistry.gauge("pending_publications",
                    Tags.of("publication_code", publicationCode, "event_type_code" , eventTypeCode),
                    new AtomicLong(0));
        });
        pendingPublications.incrementAndGet();

        Instant publicationStart = Instant.now();
        return publicationStart;
    }

    public synchronized void registerEndOfPublication(String publicationCode, String eventTypeCode, Instant publicationStart) {
        AtomicLong pendingPublications = pendingPublicationGauges.computeIfAbsent(eventTypeCode + "/" + publicationCode, x -> {
            return meterRegistry.gauge("pending_publications",
                    Tags.of("publication_code", publicationCode, "event_type_code" , eventTypeCode),
                    new AtomicLong(0));
        });
        pendingPublications.decrementAndGet();

        Instant publicationEnd = Instant.now();
        Timer publicationTimer = meterRegistry.timer("publication_duration",
                Tags.of("publication_code", publicationCode, "event_type_code", eventTypeCode));
        publicationTimer.record(Duration.between(publicationStart, publicationEnd).toMillis(), TimeUnit.MILLISECONDS);
    }

    /*
    public synchronized Instant registerBeginOfDelivery(String subscriptionCode, String eventTypeCode, String publicationCode) {
        AtomicLong pendingDeliveries = pendingDeliveriesGauges.computeIfAbsent(eventTypeCode + "/" + subscriptionCode, x -> {
            return meterRegistry.gauge("pending_deliveries",
                    Tags.of("subscription_code", subscriptionCode, "event_type_code" , eventTypeCode, "publication_code", publicationCode),
                    new AtomicLong(0));
        });
        pendingDeliveries.incrementAndGet();

        Instant deliveryStart = Instant.now();
        return deliveryStart;
    }
    */

    public synchronized void registerSuccessfulDelivery(String subscriptionCode, String eventTypeCode, String publicationCode) {
        Counter successfulDeliveriesCounter = meterRegistry.counter("successful_deliveries_total",
                Tags.of("subscription_code", subscriptionCode, "event_type_code" , eventTypeCode, "publication_code", publicationCode));
        successfulDeliveriesCounter.increment();

        Counter deliveryAttemptsCounter = meterRegistry.counter("delivery_attempts_total",
                Tags.of("subscription_code", subscriptionCode, "event_type_code" , eventTypeCode, "publication_code", publicationCode));
        deliveryAttemptsCounter.increment();
    }

    /*
    public synchronized void registerFailedDelivery(String subscriptionCode, String eventTypeCode, String publicationCode) {
        Counter failedDeliveriesCounter = meterRegistry.counter("failed_deliveries_total",
                Tags.of("subscription_code", subscriptionCode, "event_type_code" , eventTypeCode, "publication_code", publicationCode));
        failedDeliveriesCounter.increment();
    }
    */

    public synchronized void registerFailedDeliveryAttempt(String subscriptionCode, String eventTypeCode, String publicationCode) {
        Counter failedDeliveriesAttemptsCounter = meterRegistry.counter("failed_deliveries_attempts_total",
                Tags.of("subscription_code", subscriptionCode, "event_type_code" , eventTypeCode, "publication_code", publicationCode));
        failedDeliveriesAttemptsCounter.increment();

        Counter deliveryAttemptsCounter = meterRegistry.counter("delivery_attempts_total",
                Tags.of("subscription_code", subscriptionCode, "event_type_code" , eventTypeCode, "publication_code", publicationCode));
        deliveryAttemptsCounter.increment();
    }

    /*
    public synchronized void registerEndOfDelivery(String subscriptionCode, String eventTypeCode, String publicationCode, Instant deliveryStart) {
        AtomicLong pendingDeliveries = pendingDeliveriesGauges.computeIfAbsent(eventTypeCode + "/" + subscriptionCode, x -> {
            return meterRegistry.gauge("pending_deliveries",
                    Tags.of("subscription_code", subscriptionCode, "event_type_code" , eventTypeCode, "publication_code", publicationCode),
                    new AtomicLong(0));
        });
        pendingDeliveries.decrementAndGet();

        Instant deliveryEnd = Instant.now();
        Timer deliveryTimer = meterRegistry.timer("delivery_duration",
                Tags.of("subscription_code", subscriptionCode, "event_type_code" , eventTypeCode, "publication_code", publicationCode));
        deliveryTimer.record(Duration.between(deliveryStart, deliveryEnd).toMillis(), TimeUnit.MILLISECONDS);

        Counter deliveriesCounter = meterRegistry.counter("deliveries_total",
                Tags.of("subscription_code", subscriptionCode, "event_type_code" , eventTypeCode, "publication_code", publicationCode));
        deliveriesCounter.increment();
    }
    */
}
