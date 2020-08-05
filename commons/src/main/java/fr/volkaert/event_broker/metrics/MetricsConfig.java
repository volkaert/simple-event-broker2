package fr.volkaert.event_broker.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MetricsConfig {

    @Value("${broker.component-type-name}")
    String componentTypeName;

    @Value("${broker.component-instance-id}")
    String componentInstanceId;

    @Bean
    MeterRegistryCustomizer<MeterRegistry> metricsCommonTags() {
        return registry -> registry.config()
                .commonTags("component_type_name", componentTypeName)
                .commonTags("component_instance_id", componentInstanceId);
    }
}
