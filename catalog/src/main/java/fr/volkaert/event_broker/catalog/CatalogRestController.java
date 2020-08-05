package fr.volkaert.event_broker.catalog;

import fr.volkaert.event_broker.error.BrokerExceptionResponse;
import fr.volkaert.event_broker.model.EventType;
import fr.volkaert.event_broker.model.Publication;
import fr.volkaert.event_broker.model.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping(value = "/catalog")
public class CatalogRestController {

    @Autowired
    CatalogService catalogService;

    private static final Logger LOGGER = LoggerFactory.getLogger(CatalogRestController.class);

    @GetMapping(value="/event-types", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<EventType>> getEventTypes() {
        LOGGER.info("GET /catalog/event-types called");
        try {
            return ResponseEntity.ok(catalogService.getEventTypes());
        } catch (Exception ex) {
            LOGGER.error(ex.getMessage(), ex);
            return new ResponseEntity(new BrokerExceptionResponse(ex), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping(value="/event-types/{code}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<EventType> getEventTypeByCode(@PathVariable String code) {
        LOGGER.info("GET /catalog/event-types/{} called", code);
        try {
            return ResponseEntity.ok(catalogService.getEventTypeByCode(code));
        } catch (Exception ex) {
            LOGGER.error(ex.getMessage(), ex);
            return new ResponseEntity(new BrokerExceptionResponse(ex), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @PostMapping(value="/event-types", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<EventType> insertEventType(@RequestBody EventType eventType) {
        LOGGER.info("POST /catalog/event-types called");
        if (eventType.getCode() != null)
            return new ResponseEntity("Code must be null for a POST(=create) operation", HttpStatus.BAD_REQUEST);
        try {
            return ResponseEntity.ok(catalogService.saveEventType(eventType));
        } catch (Exception ex) {
            LOGGER.error(ex.getMessage(), ex);
            return new ResponseEntity(new BrokerExceptionResponse(ex), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @PutMapping(value="/event-types/{code}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<EventType> updateEventType(@PathVariable String code, @RequestBody EventType eventType) {
        LOGGER.info("PUT /catalog/event-types/{} called", code);
        if (eventType.getCode() == null)
            return new ResponseEntity("Code must not be null for a PUT(=update) operation", HttpStatus.BAD_REQUEST);
        if (! eventType.getCode().equalsIgnoreCase(code))
            return new ResponseEntity("Inconsistent codes between code in path param and code in the body", HttpStatus.BAD_REQUEST);
        try {
            return ResponseEntity.ok(catalogService.saveEventType(eventType));
        } catch (Exception ex) {
            LOGGER.error(ex.getMessage(), ex);
            return new ResponseEntity(new BrokerExceptionResponse(ex), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @DeleteMapping(value="/event-types/{code}")
    public ResponseEntity<Void> deleteEventTypeByCode(@PathVariable String code) {
        LOGGER.info("DELETE /catalog/event-types/{} called", code);
        try {
            catalogService.deleteEventTypeByCode(code);
            return ResponseEntity.noContent().build();
        } catch (Exception ex) {
            LOGGER.error(ex.getMessage(), ex);
            return new ResponseEntity(new BrokerExceptionResponse(ex), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping(value="/publications", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<Publication>> getPublications() {
        LOGGER.info("GET /catalog/publications called");
        try {
            return ResponseEntity.ok(catalogService.getPublications());
        } catch (Exception ex) {
            LOGGER.error(ex.getMessage(), ex);
            return new ResponseEntity(new BrokerExceptionResponse(ex), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping(value="/publications/{code}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Publication> getPublicationByCode(@PathVariable String code) {
        LOGGER.info("GET /catalog/publications/{} called", code);
        try {
            return ResponseEntity.ok(catalogService.getPublicationByCode(code));
        } catch (Exception ex) {
            LOGGER.error(ex.getMessage(), ex);
            return new ResponseEntity(new BrokerExceptionResponse(ex), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @PostMapping(value="/publications", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Publication> insertPublication(@RequestBody Publication publication) {
        LOGGER.info("POST /catalog/publications called");
        if (publication.getCode() != null)
            return new ResponseEntity("Code must be null for a POST(=create) operation", HttpStatus.BAD_REQUEST);
        try {
            return ResponseEntity.ok(catalogService.savePublication(publication));
        } catch (Exception ex) {
            LOGGER.error(ex.getMessage(), ex);
            return new ResponseEntity(new BrokerExceptionResponse(ex), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @PutMapping(value="/publications/{code}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Publication> updatePublication(@PathVariable String code, @RequestBody Publication publication) {
        LOGGER.info("PUT /catalog/publications/{} called", code);
        if (publication.getCode() == null)
            return new ResponseEntity("Code must not be null for a PUT(=update) operation", HttpStatus.BAD_REQUEST);
        if (! publication.getCode().equalsIgnoreCase(code))
            return new ResponseEntity("Inconsistent codes between code in path param and code in the body", HttpStatus.BAD_REQUEST);
        try {
            return ResponseEntity.ok(catalogService.savePublication(publication));
        } catch (Exception ex) {
            LOGGER.error(ex.getMessage(), ex);
            return new ResponseEntity(new BrokerExceptionResponse(ex), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @DeleteMapping(value="/publications/{code}")
    public ResponseEntity<Void> deletePublicationByCode(@PathVariable String code) {
        LOGGER.info("DELETE /catalog/publications/{} called", code);
        try {
            catalogService.deletePublicationByCode(code);
            return ResponseEntity.noContent().build();
        } catch (Exception ex) {
            LOGGER.error(ex.getMessage(), ex);
            return new ResponseEntity(new BrokerExceptionResponse(ex), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping(value="/subscriptions", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<Subscription>> getSubscriptions() {
        LOGGER.info("GET /catalog/subscriptions called");
        try {
            return ResponseEntity.ok(catalogService.getSubscriptions());
        } catch (Exception ex) {
            LOGGER.error(ex.getMessage(), ex);
            return new ResponseEntity(new BrokerExceptionResponse(ex), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping(value="/subscriptions/{code}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Subscription> getSubscriptionByCode(@PathVariable String code) {
        LOGGER.info("GET /catalog/subscriptions/{} called", code);
        try {
            return ResponseEntity.ok(catalogService.getSubscriptionByCode(code));
        } catch (Exception ex) {
            LOGGER.error(ex.getMessage(), ex);
            return new ResponseEntity(new BrokerExceptionResponse(ex), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @PostMapping(value="/subscriptions", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Subscription> insertSubscription(@RequestBody Subscription subscription) {
        LOGGER.info("POST /catalog/subscriptions called");
        if (subscription.getCode() != null)
            return new ResponseEntity("Code must be null for a POST(=create) operation", HttpStatus.BAD_REQUEST);
        try {
            return ResponseEntity.ok(catalogService.saveSubscription(subscription));
        } catch (Exception ex) {
            LOGGER.error(ex.getMessage(), ex);
            return new ResponseEntity(new BrokerExceptionResponse(ex), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @PutMapping(value="/subscriptions/{code}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Subscription> updateSubscription(@PathVariable String code, @RequestBody Subscription subscription) {
        LOGGER.info("PUT /catalog/subscriptions/{} called", code);
        if (subscription.getCode() == null)
            return new ResponseEntity("Code must not be null for a PUT(=update) operation", HttpStatus.BAD_REQUEST);
        if (! subscription.getCode().equalsIgnoreCase(code))
            return new ResponseEntity("Inconsistent codes between code in path param and code in the body", HttpStatus.BAD_REQUEST);
        try {
            return ResponseEntity.ok(catalogService.saveSubscription(subscription));
        } catch (Exception ex) {
            LOGGER.error(ex.getMessage(), ex);
            return new ResponseEntity(new BrokerExceptionResponse(ex), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @DeleteMapping(value="/subscriptions/{code}")
    public ResponseEntity<Void> deleteSubscriptionByCode(@PathVariable String code) {
        LOGGER.info("DELETE /catalog/subscriptions/{} called", code);
        try {
            catalogService.deleteSubscriptionByCode(code);
            return ResponseEntity.noContent().build();
        } catch (Exception ex) {
            LOGGER.error(ex.getMessage(), ex);
            return new ResponseEntity(new BrokerExceptionResponse(ex), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}
