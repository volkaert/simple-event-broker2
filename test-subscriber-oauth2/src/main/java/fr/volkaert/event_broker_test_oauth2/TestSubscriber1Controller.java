package fr.volkaert.event_broker_test_oauth2;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

// See // See https://github.com/okta/samples-java-spring/blob/master/resource-server/src/main/java/com/okta/spring/example/ResourceServerExampleApplication.java
// @CrossOrigin(origins = "http://localhost:8080")
@RestController
@RequestMapping(value = "/tests/subscriber1", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
public class TestSubscriber1Controller {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestSubscriber1Controller.class);

    @PostMapping(value = "/nominal", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('SCOPE_test_subscriber_oauth2.webhooks')")
    public ResponseEntity<String> nominal(@RequestBody EventToSubscriberWithTestPayload event, @RequestHeader HttpHeaders httpHeaders) {
        String msg = "Webhook 'nominal' called with event " + event + " (headers are " + httpHeaders + ")";
        LOGGER.info(msg);
        return ResponseEntity.ok(msg);
    }
}
