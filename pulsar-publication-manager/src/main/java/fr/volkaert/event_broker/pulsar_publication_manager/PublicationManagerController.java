package fr.volkaert.event_broker.pulsar_publication_manager;

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
@RequestMapping("/events")
public class PublicationManagerController {

    @Autowired
    BrokerConfig config;

    @Autowired
    PublicationManagerService publicationManagerService;

    private static final Logger LOGGER = LoggerFactory.getLogger(PublicationManagerController.class);

    @PostMapping
    public ResponseEntity<InflightEvent> publish(@RequestBody InflightEvent inflightEvent) {
        try {
            inflightEvent = publicationManagerService.publish(inflightEvent);
            return new ResponseEntity(inflightEvent, HttpStatus.CREATED);
        } catch (BrokerException ex) {
            LOGGER.error(ex.getMessage(), ex);
            return new ResponseEntity(new BrokerExceptionResponse(ex), ex.getHttpStatus());
        } catch (Exception ex) {
            LOGGER.error(ex.getMessage(), ex);
            return new ResponseEntity(new BrokerExceptionResponse(ex), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}
