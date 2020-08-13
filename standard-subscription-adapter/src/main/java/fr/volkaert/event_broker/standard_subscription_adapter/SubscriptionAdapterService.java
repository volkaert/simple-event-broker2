package fr.volkaert.event_broker.standard_subscription_adapter;

import fr.volkaert.event_broker.model.InflightEvent;
import fr.volkaert.event_broker.standard_subscription_adapter.model.EventToSubscriber;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SubscriptionAdapterService {

    @Autowired
    @Qualifier("RestTemplateForWebhooks")
    RestTemplate restTemplate;

    @Autowired
    @Qualifier("RestTemplateForOAuth2Issuer")
    RestTemplate restTemplateForOAuth2Issuer;

    @Autowired
    BrokerConfig config;

    private static final Logger LOGGER = LoggerFactory.getLogger(SubscriptionAdapterService.class);

    public InflightEvent callWebhook(InflightEvent inflightEvent) {
        ResponseEntity<Void> response = null;
        try {
            //LOGGER.debug("Event received. Event is {}.", inflightEvent);
            LOGGER.debug("Event received. Event is {}.", inflightEvent.cloneWithoutSensitiveData());

            HttpHeaders httpHeaders = new HttpHeaders();

            httpHeaders.setContentType(MediaType.valueOf(inflightEvent.getWebhookContentType()));

            if (inflightEvent.getAuthMethod() == null || inflightEvent.getAuthMethod().equalsIgnoreCase("basicauth")) {
                if (inflightEvent.getAuthClientId() != null && inflightEvent.getAuthClientSecret() != null) {
                    httpHeaders.setBasicAuth(inflightEvent.getAuthClientId(), inflightEvent.getAuthClientSecret());
                }
            }
            else if (inflightEvent.getAuthMethod() != null && inflightEvent.getAuthMethod().equalsIgnoreCase("oauth2")) {
                if (inflightEvent.getAuthScope() != null) {
                    httpHeaders.set(HttpHeaders.AUTHORIZATION, "Bearer " + getOAuth2Token(inflightEvent.getAuthScope()));
                    //httpHeaders.setBearerAuth(getOAuth2Token(inflightEvent.getAuthScope()));

                }
            }

            String webhookHeadersAsString = inflightEvent.getWebhookHeaders();
            if (webhookHeadersAsString != null && ! webhookHeadersAsString.trim().isEmpty()) {
                String[] headersAndValuesAsString = webhookHeadersAsString.trim().split(";");
                for (String headerAndValueAsString : headersAndValuesAsString) {
                    String[] headerAndValue = headerAndValueAsString.trim().split(":");
                    httpHeaders.set(headerAndValue[0].trim(), headerAndValue[1].trim());
                }
            }

            // charset UTF8 has been defined during the creation of RestTemplate

            EventToSubscriber eventToSubscriber = EventToSubscriber.from(inflightEvent);
            HttpEntity<EventToSubscriber> request = new HttpEntity<>(eventToSubscriber, httpHeaders);

            LOGGER.debug("Calling the webhook at {}. Event is {}.",
                    inflightEvent.getWebhookUrl(), eventToSubscriber.cloneWithoutSensitiveData());
            response = restTemplate.exchange(inflightEvent.getWebhookUrl(), HttpMethod.POST, request, Void.class);
            LOGGER.debug("The Webhook returned the http status code {}. Event is {}.",
                    response.getStatusCode(), inflightEvent.toShortLog());

            inflightEvent.setWebhookHttpStatus(response.getStatusCodeValue());
            LOGGER.debug("Returning the event {}", inflightEvent.cloneWithoutSensitiveData());
            return inflightEvent;

        } catch (HttpClientErrorException ex) {
            String msg = String.format("Client error %s while calling the webhook at %s. Event is %s.",
                    ex.getStatusCode(), inflightEvent.getWebhookUrl(), inflightEvent.toShortLog());
            LOGGER.error(msg, ex);

            inflightEvent.setWebhookClient4xxErrorOccurred(true);
            inflightEvent.setWebhookHttpStatus(ex.getStatusCode().value());
            LOGGER.debug("Returning the event {}", inflightEvent.cloneWithoutSensitiveData());
            return inflightEvent;

        } catch (HttpServerErrorException ex) {
            String msg = String.format("Server error %s while calling the webhook at %s. Event is %s.",
                    ex.getStatusCode(), inflightEvent.getWebhookUrl(), inflightEvent.toShortLog());
            LOGGER.error(msg, ex);

            inflightEvent.setWebhookServer5xxErrorOccurred(true);
            inflightEvent.setWebhookHttpStatus(ex.getStatusCode().value());
            LOGGER.debug("Returning the event {}", inflightEvent.cloneWithoutSensitiveData());
            return inflightEvent;

        } catch (Exception ex) {
            if (ex.getMessage().contains("Connection refused")) {
                String msg = String.format("Connection Refused error while handling the call to webhook at %s. Event is %s.",
                        inflightEvent.getWebhookUrl(), inflightEvent.toShortLog());
                LOGGER.error(msg, ex);

                inflightEvent.setWebhookConnectionErrorOccurred(true);
                inflightEvent.setWebhookHttpStatus(HttpStatus.BAD_GATEWAY.value());
                LOGGER.debug("Returning the event {}", inflightEvent.cloneWithoutSensitiveData());
                return inflightEvent;
            }

            else if (ex.getMessage().contains("Read timed out")) {
                String msg = String.format("Read Timeout error while handling the call to the webhook at %s. Event is %s.",
                        inflightEvent.getWebhookUrl(), inflightEvent.toShortLog());
                LOGGER.error(msg, ex);

                inflightEvent.setWebhookReadTimeoutErrorOccurred(true);
                inflightEvent.setWebhookHttpStatus(HttpStatus.GATEWAY_TIMEOUT.value());
                LOGGER.debug("Returning the event {}", inflightEvent.cloneWithoutSensitiveData());
                return inflightEvent;
            }

            else {
                String msg = String.format("Error while handling the call to the webhook at %s. Event is %s.",
                        inflightEvent.getWebhookUrl(), inflightEvent.toShortLog());
                LOGGER.error(msg, ex);

                inflightEvent.setWebhookHttpStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
                LOGGER.debug("Returning the event {}", inflightEvent.cloneWithoutSensitiveData());
                return inflightEvent;
            }
        }
    }

    private Map<String, Oauth2TokenResponseWithExpirationDate> oauth2TokenResponseWithExpirationDateCache = new ConcurrentHashMap<>();

    private synchronized String getOAuth2Token(String authScope) {
        String accessToken = null;
        Oauth2TokenResponseWithExpirationDate tokenResponseWithExpirationDate = oauth2TokenResponseWithExpirationDateCache.get(authScope);
        if (tokenResponseWithExpirationDate != null) {
            if (Instant.now().plusSeconds(10).isAfter(tokenResponseWithExpirationDate.getExpirationDate())) {   // 10 second safety margin before expiration
                oauth2TokenResponseWithExpirationDateCache.remove(authScope);
                tokenResponseWithExpirationDate = null;
            }
        }
        if (tokenResponseWithExpirationDate != null) {
            accessToken = tokenResponseWithExpirationDate.getTokenResponse().getAccess_token();
        }
        else {
            HttpHeaders httpHeaders = new HttpHeaders();
            httpHeaders.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            httpHeaders.setAccept(List.of(MediaType.APPLICATION_JSON));
            httpHeaders.setCacheControl("no-cache");
            httpHeaders.setBasicAuth(config.getOauth2ClientId(), config.getOauth2ClientSecret());

            String requestData = "grant_type=client_credentials&scope=" + authScope;
            HttpEntity<String> request = new HttpEntity<>(requestData, httpHeaders);

            ResponseEntity<Oauth2TokenResponse> response = null;
            response = restTemplateForOAuth2Issuer.exchange(config.getOauth2TokenEndpoint(),HttpMethod.POST, request, Oauth2TokenResponse.class);

            Oauth2TokenResponse tokenResponse = response.getBody();

            tokenResponseWithExpirationDate = new Oauth2TokenResponseWithExpirationDate();
            tokenResponseWithExpirationDate.setTokenResponse(tokenResponse);
            tokenResponseWithExpirationDate.setExpirationDate(Instant.now().plusSeconds(tokenResponse.getExpires_in()));
            oauth2TokenResponseWithExpirationDateCache.put(authScope, tokenResponseWithExpirationDate);

            accessToken = tokenResponse.getAccess_token();
        }

        LOGGER.debug("Access token for scope {} is {}", authScope, accessToken);
        return accessToken;
    }

    @Data
    @NoArgsConstructor
    private static class Oauth2TokenResponse {
        String token_type;
        long expires_in;
        String access_token;
        String scope;
    }
    @Data
    @NoArgsConstructor
    private static class Oauth2TokenResponseWithExpirationDate {
        Oauth2TokenResponse tokenResponse;
        Instant expirationDate;
    }
}
