package fr.volkaert.event_broker.catalog.availability;

import fr.volkaert.event_broker.catalog.CatalogService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.*;
import org.springframework.stereotype.Component;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class CatalogReadinessHealthIndicator implements CompositeHealthContributor {

    @Autowired
    CatalogService catalogService;

    private static final Logger LOGGER = LoggerFactory.getLogger(CatalogReadinessHealthIndicator.class);

    private Map<String, HealthContributor> contributors = new LinkedHashMap<>();

    public CatalogReadinessHealthIndicator() {
        this.contributors.put("catalogService", new CatalogServiceHealthIndicator());
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

    class CatalogServiceHealthIndicator implements HealthIndicator {
        @Override
        public Health health() {
            try {
                catalogService.getEventTypes();
                return Health.up().build();
            } catch (Exception ex) {
                LOGGER.error("Exception while checking CatalogService readiness state", ex);
                return Health.down().withDetail("exception", ex).build();
            }
        }
    }
}
