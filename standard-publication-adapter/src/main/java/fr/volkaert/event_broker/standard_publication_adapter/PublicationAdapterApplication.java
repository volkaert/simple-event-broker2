package fr.volkaert.event_broker.standard_publication_adapter;

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

    public static void main(String[] args) {
        SpringApplication.run(PublicationAdapterApplication.class, args);
    }

    @Bean
    @Qualifier("RestTemplateForPublicationManager")
    public RestTemplate restTemplateForWebhooks(RestTemplateBuilder builder) {
        RestTemplate restTemplate = builder
            .setConnectTimeout(Duration.ofSeconds(config.getConnectTimeoutInSeconds()))
            .setReadTimeout(Duration.ofSeconds(config.getReadTimeoutInSeconds()))
            .build();
        restTemplate.getMessageConverters().add(0, new StringHttpMessageConverter(StandardCharsets.UTF_8));
        return restTemplate;
    }
}
