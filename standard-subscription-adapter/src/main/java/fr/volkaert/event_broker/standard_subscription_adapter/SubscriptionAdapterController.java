package fr.volkaert.event_broker.standard_subscription_adapter;

import fr.volkaert.event_broker.error.BrokerException;
import fr.volkaert.event_broker.error.BrokerExceptionResponse;
import fr.volkaert.event_broker.model.InflightEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/webhooks")
public class SubscriptionAdapterController {

    @Autowired
    SubscriptionAdapterService service;

    private static final Logger LOGGER = LoggerFactory.getLogger(SubscriptionAdapterController.class);

    @PostMapping
    public ResponseEntity<Object> callWebhook(@RequestBody InflightEvent inflightEvent) {
        try {
            InflightEvent returnedInflightEvent = service.callWebhook(inflightEvent);
            return new ResponseEntity<Object>(returnedInflightEvent, HttpStatus.OK);
        } catch (BrokerException ex) {
            // If error is a BrokerException, the error should already have been logged
            //LOGGER.error(ex.getMessage(), ex);
            return new ResponseEntity<Object>(new BrokerExceptionResponse(ex), ex.getHttpStatus());
        } catch (Exception ex) {
            LOGGER.error(ex.getMessage(), ex);
            return new ResponseEntity<Object>(new BrokerExceptionResponse(ex), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}
