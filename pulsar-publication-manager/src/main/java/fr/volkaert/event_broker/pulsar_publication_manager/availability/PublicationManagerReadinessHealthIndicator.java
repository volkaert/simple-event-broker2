package fr.volkaert.event_broker.pulsar_publication_manager.availability;

import fr.volkaert.event_broker.pulsar_publication_manager.BrokerConfig;
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
public class PublicationManagerReadinessHealthIndicator implements CompositeHealthContributor {

    @Autowired
    BrokerConfig config;

    @Autowired
    @Qualifier("RestTemplateForCatalogClient")
    RestTemplate restTemplateForCatalogClient;

    private static final Logger LOGGER = LoggerFactory.getLogger(PublicationManagerReadinessHealthIndicator.class);

    private Map<String, HealthContributor> contributors = new LinkedHashMap<>();

    public PublicationManagerReadinessHealthIndicator() {
        this.contributors.put("catalogReadiness", new CatalogReadinessHealthIndicator());
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

    class CatalogReadinessHealthIndicator implements HealthIndicator {
        @Override
        public Health health() {
            LOGGER.debug("Checking Catalog readiness state");
            String readinessUrlForCatalog = config.getCatalogUrl() + "/actuator/health/readiness";
            try {
                ResponseEntity<Void> response = restTemplateForCatalogClient.getForEntity(readinessUrlForCatalog, Void.class);
                if (response.getStatusCode().is2xxSuccessful())
                    return Health.up().build();
                else
                    return Health.down().withDetail("statusCode", response.getStatusCode()).build();
            } catch (Exception ex) {
                LOGGER.error("Exception while checking Catalog readiness state", ex);
                return Health.down().withDetail("exception", ex).build();
            }
        }
    }
}

