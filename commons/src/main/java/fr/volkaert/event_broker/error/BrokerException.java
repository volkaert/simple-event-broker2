package fr.volkaert.event_broker.error;

import org.springframework.http.HttpStatus;

import java.time.Instant;

public class BrokerException extends  RuntimeException {

    private Instant timestamp = Instant.now();
    public Instant getTimestamp() {
        return timestamp;
    }

    private HttpStatus httpStatus = HttpStatus.INTERNAL_SERVER_ERROR;
    public HttpStatus getHttpStatus() {
        return httpStatus;
    }

    public int getHttpStatusCode() {
        return httpStatus != null ? httpStatus.value() : HttpStatus.INTERNAL_SERVER_ERROR.value();
    }
    public String getHttpStatusMessage() {
        return httpStatus != null ? httpStatus.getReasonPhrase() : HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase();
    }

    private String path;
    public String getPath() {
        return path;
    }

    private boolean isWebhookConnectionError;
    public void setWebhookConnectionError(boolean isWebhookConnectionError) { this.isWebhookConnectionError = isWebhookConnectionError; }
    public boolean isWebhookConnectionError() { return isWebhookConnectionError; }

    private boolean isWebhookReadTimeoutError;
    public void setWebhookReadTimeoutError(boolean isWebhookReadTimeoutError) { this.isWebhookReadTimeoutError = isWebhookReadTimeoutError; }
    public boolean isWebhookReadTimeoutError() { return isWebhookReadTimeoutError; }

    private boolean isWebhookServer5xxError;
    public void setWebhookServer5xxError(boolean isWebhookServer5xxError) { this.isWebhookServer5xxError = isWebhookServer5xxError; }
    public boolean isWebhookServer5xxError() { return isWebhookServer5xxError; }

    private boolean isWebhookClient4xxError;
    public void setWebhookClient4xxError(boolean isWebhookClient4xxError) { this.isWebhookClient4xxError = isWebhookClient4xxError; }
    public boolean isWebhookClient4xxError() { return isWebhookClient4xxError; }

    public BrokerException(HttpStatus httpStatus, String message) {
        super(message);
        this.httpStatus = httpStatus;
    }

    public BrokerException(HttpStatus httpStatus, String message, String path) {
        super(message);
        this.httpStatus = httpStatus;
        this.path = path;
    }

    public BrokerException(HttpStatus httpStatus, String message, Throwable cause) {
        super(message, cause);
        this.httpStatus = httpStatus;
    }

    public BrokerException(HttpStatus httpStatus, String message, Throwable cause, String path) {
        super(message, cause);
        this.httpStatus = httpStatus;
        this.path = path;
    }

    public BrokerException(HttpStatus httpStatus, String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
        this.httpStatus = httpStatus;
    }

    public BrokerException(HttpStatus httpStatus, String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace, String path) {
        super(message, cause, enableSuppression, writableStackTrace);
        this.httpStatus = httpStatus;
        this.path = path;
    }
}
