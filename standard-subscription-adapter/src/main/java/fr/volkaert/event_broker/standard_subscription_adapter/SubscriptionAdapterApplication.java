package fr.volkaert.event_broker.standard_subscription_adapter;

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
public class SubscriptionAdapterApplication {

    @Autowired
    BrokerConfig config;

    private static final Logger LOGGER = LoggerFactory.getLogger(SubscriptionAdapterApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(SubscriptionAdapterApplication.class, args);
    }

    @Bean
    @Qualifier("RestTemplateForWebhooks")
    public RestTemplate restTemplateForWebhooks(RestTemplateBuilder builder) {
        LOGGER.info("Timeouts for webhooks: connect={}, read={}",
                config.getConnectTimeoutInSecondsForWebhooks(), config.getReadTimeoutInSecondsForWebhooks());
        RestTemplate restTemplate = builder
            .setConnectTimeout(Duration.ofSeconds(config.getConnectTimeoutInSecondsForWebhooks()))
            .setReadTimeout(Duration.ofSeconds(config.getReadTimeoutInSecondsForWebhooks()))
            .build();
        restTemplate.getMessageConverters().add(0, new StringHttpMessageConverter(StandardCharsets.UTF_8));
        return restTemplate;
    }

    @Bean
    @Qualifier("RestTemplateForOAuth2Issuer")
    public RestTemplate restTemplateForOAuth2Issuer(RestTemplateBuilder builder) {
        LOGGER.info("Timeouts for OAuth2 issuer: connect={}, read={}",
                config.getConnectTimeoutInSecondsForOAuth2Issuer(), config.getReadTimeoutInSecondsForOAuth2Issuer());
        RestTemplate restTemplate = builder
                .setConnectTimeout(Duration.ofSeconds(config.getConnectTimeoutInSecondsForOAuth2Issuer()))
                .setReadTimeout(Duration.ofSeconds(config.getReadTimeoutInSecondsForOAuth2Issuer()))
                .build();
        restTemplate.getMessageConverters().add(0, new StringHttpMessageConverter(StandardCharsets.UTF_8));
        return restTemplate;
    }
}
