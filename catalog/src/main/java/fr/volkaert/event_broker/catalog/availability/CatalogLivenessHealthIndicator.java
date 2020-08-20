package fr.volkaert.event_broker.catalog.availability;

import org.springframework.boot.actuate.health.*;
import org.springframework.stereotype.Component;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class CatalogLivenessHealthIndicator implements CompositeHealthContributor {

    private Map<String, HealthContributor> contributors = new LinkedHashMap<>();

    public CatalogLivenessHealthIndicator() {
        this.contributors.put("global", new MyGlobalLivenessHealthIndicator());
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

    class MyGlobalLivenessHealthIndicator implements HealthIndicator {

        @Override
        public Health health() {
            return Health.up().build();
        }
    }
}

