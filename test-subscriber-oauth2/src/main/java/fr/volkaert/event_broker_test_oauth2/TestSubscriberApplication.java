package fr.volkaert.event_broker_test_oauth2;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;

@SpringBootApplication
@EnableGlobalMethodSecurity(prePostEnabled = true, securedEnabled = true)
public class TestSubscriberApplication {

    public static void main(String[] args) {
        SpringApplication.run(TestSubscriberApplication.class, args);
    }
}
