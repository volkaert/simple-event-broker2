package fr.volkaert.event_broker_test;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping(value = "/tests/subscriber1", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
public class TestSubscriber1Controller {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestSubscriber1Controller.class);


    @PostMapping(value = "/nominal", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> nominal(@RequestBody EventToSubscriberWithTestPayload event, @RequestHeader HttpHeaders httpHeaders) {
        String msg = "Webhook 'nominal' called with event " + event + " (headers are " + httpHeaders + ")";
        LOGGER.info(msg);
        return ResponseEntity.ok(msg);
    }

    @PostMapping(value = "/failure401", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public  ResponseEntity<String> failure401(@RequestBody EventToSubscriberWithTestPayload event, @RequestHeader HttpHeaders httpHeaders) {
        String msg = "Webhook 'failure401' called with event " + event + " (headers are " + httpHeaders + ")";
        LOGGER.info(msg);
        return new ResponseEntity(msg, HttpStatus.UNAUTHORIZED);
    }

    @PostMapping(value = "/failure500", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity failure500(@RequestBody EventToSubscriberWithTestPayload event, @RequestHeader HttpHeaders httpHeaders) {
        String msg = "Webhook 'failure500' called with event " + event + " (headers are " + httpHeaders + ")";
        LOGGER.info(msg);
        return new ResponseEntity(msg, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @PostMapping(value = "/slow", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> slow(@RequestBody EventToSubscriberWithTestPayload event, @RequestHeader HttpHeaders httpHeaders) {
        String msg = "Webhook 'slow' called with event " + event + " (headers are " + httpHeaders + ")";
        LOGGER.info(msg);
        try {
            TimeUnit.MILLISECONDS.sleep(event.getPayload().getTimeToSleepInMillis());
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return ResponseEntity.ok(msg);
    }

    @PostMapping(value = "/complex-payload", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> complexPayload(@RequestBody EventToSubscriberWithComplexTestPayload event, @RequestHeader HttpHeaders httpHeaders) {
        String msg = "Webhook 'complexPayload' called with event " + event + " (headers are " + httpHeaders + ")";
        LOGGER.info(msg);
        return ResponseEntity.ok(msg);
    }
}
