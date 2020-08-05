package fr.volkaert.event_broker.error;

import lombok.Data;
import org.springframework.http.HttpStatus;

import java.time.Instant;

@Data
public class BrokerExceptionResponse {
    private Instant timestamp;
    private int status;
    private String error;
    private String message;
    private String path;

    public BrokerExceptionResponse(BrokerException ex) {
        this.timestamp = ex.getTimestamp();
        this.status = ex.getHttpStatusCode();
        this.error = ex.getHttpStatusMessage();
        this.message = ex.getMessage();
        this.path = ex.getPath();
    }

    public BrokerExceptionResponse(Exception ex) {
        this.status = HttpStatus.INTERNAL_SERVER_ERROR.value();
        this.error = HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase();
        this.message = ex.getMessage();
    }
}
