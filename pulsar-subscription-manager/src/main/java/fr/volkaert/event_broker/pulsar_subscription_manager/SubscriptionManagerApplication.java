package fr.volkaert.event_broker.pulsar_subscription_manager;

import org.apache.pulsar.client.api.PulsarClient;
import org.apache.pulsar.client.api.PulsarClientException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.event.ContextStartedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class })
@ComponentScan("fr.volkaert")  // Required because some components/services are not in the same project !
public class SubscriptionManagerApplication {

    @Autowired
    BrokerConfig config;

    @Autowired
    SubscriptionManagerService subscriptionManagerService;

    private static final Logger LOGGER = LoggerFactory.getLogger(SubscriptionManagerApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(SubscriptionManagerApplication.class, args);
    }

    @EventListener
    public void handleContextRefreshEvent(ContextStartedEvent ctxStartedEvt) {  subscriptionManagerService.start(); }

    @Bean
    PulsarClient createPulsarClient() throws PulsarClientException {
        PulsarClient pulsarClient = PulsarClient.builder()
                .serviceUrl(config.getPulsarServiceUrl())
                .listenerThreads(config.getPulsarListenerThreadCount())
                .build();
        return pulsarClient;
    }

    @Bean
    @Qualifier("RestTemplateForSubscriptionAdapter")
    @LoadBalanced
    public RestTemplate restTemplateForSubscriptionAdapter(RestTemplateBuilder builder) {
        LOGGER.info("Timeouts for Subscription Adapter: connect={}, read={}",
                config.getConnectTimeoutInSecondsForSubscriptionAdapter(), config.getReadTimeoutInSecondsForSubscriptionAdapter());
        RestTemplate restTemplate = builder
            .setConnectTimeout(Duration.ofSeconds(config.getConnectTimeoutInSecondsForSubscriptionAdapter()))
            .setReadTimeout(Duration.ofSeconds(config.getReadTimeoutInSecondsForSubscriptionAdapter()))
            .build();
        restTemplate.getMessageConverters().add(0, new StringHttpMessageConverter(StandardCharsets.UTF_8));
        return restTemplate;
    }
}
