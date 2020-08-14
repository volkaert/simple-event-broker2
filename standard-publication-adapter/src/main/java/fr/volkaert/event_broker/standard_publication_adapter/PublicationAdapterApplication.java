package fr.volkaert.event_broker.standard_publication_adapter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class })
@ComponentScan("fr.volkaert")  // Required because some components/services are not in the same project !
public class PublicationAdapterApplication {

    @Autowired
    BrokerConfig config;

    private static final Logger LOGGER = LoggerFactory.getLogger(PublicationAdapterApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(PublicationAdapterApplication.class, args);
    }

    @Bean
    @Qualifier("RestTemplateForPublicationManager")
    public RestTemplate restTemplateForWebhooks(RestTemplateBuilder builder) {
        LOGGER.info("Timeouts for Publication Manager: connect={}, read={}",
                config.getConnectTimeoutInSecondsForPublicationManager(), config.getReadTimeoutInSecondsForPublicationManager());
        RestTemplate restTemplate = builder
            .setConnectTimeout(Duration.ofSeconds(config.getConnectTimeoutInSecondsForPublicationManager()))
            .setReadTimeout(Duration.ofSeconds(config.getReadTimeoutInSecondsForPublicationManager()))
            .build();
        restTemplate.getMessageConverters().add(0, new StringHttpMessageConverter(StandardCharsets.UTF_8));
        return restTemplate;
    }
}

