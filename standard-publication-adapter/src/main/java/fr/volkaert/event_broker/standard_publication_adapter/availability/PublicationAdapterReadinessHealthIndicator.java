package fr.volkaert.event_broker.standard_publication_adapter.availability;

import fr.volkaert.event_broker.standard_publication_adapter.BrokerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.actuate.health.*;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class PublicationAdapterReadinessHealthIndicator implements CompositeHealthContributor {

    @Autowired
    BrokerConfig config;

    @Autowired
    @Qualifier("RestTemplateForPublicationManager")
    RestTemplate restTemplateForPublicationManager;

    private static final Logger LOGGER = LoggerFactory.getLogger(PublicationAdapterReadinessHealthIndicator.class);

    private Map<String, HealthContributor> contributors = new LinkedHashMap<>();

    public PublicationAdapterReadinessHealthIndicator() {
        this.contributors.put("publicationManagerReadiness", new PublicationManagerReadinessHealthIndicator());
    }

    @Override
    public HealthContributor getContributor(String name) {
        return contributors.get(name);
    }

    @Override
    public Iterator<NamedContributor<HealthContributor>> iterator() {
        return contributors.entrySet().stream()
                .map((entry) -> NamedContributor.of(entry.getKey(), entry.getValue())).iterator();
    }

    class PublicationManagerReadinessHealthIndicator implements HealthIndicator {
        @Override
        public Health health() {
            String readinessUrlForPublicationManager = config.getPublicationManagerUrl() + "/actuator/health/readiness";
            try {
                ResponseEntity<Void> response = restTemplateForPublicationManager.getForEntity(readinessUrlForPublicationManager, Void.class);
                if (response.getStatusCode().is2xxSuccessful())
                    return Health.up().build();
                else
                    return Health.down().withDetail("statusCode", response.getStatusCode()).build();
            } catch (Exception ex) {
                LOGGER.error("Exception while checking Publication Manager readiness state", ex);
                return Health.down().withDetail("exception", ex).build();
            }
        }
    }
}

