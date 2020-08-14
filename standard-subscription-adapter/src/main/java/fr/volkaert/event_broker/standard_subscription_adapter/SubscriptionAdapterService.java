package fr.volkaert.event_broker.standard_subscription_adapter;

import fr.volkaert.event_broker.error.BrokerException;
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
        LOGGER.debug("Event received. Event is {}.", inflightEvent.cloneWithoutSensitiveData());

        HttpHeaders httpHeaders = new HttpHeaders();

        String authMethod = inflightEvent.getAuthMethod() != null ? inflightEvent.getAuthMethod().trim() : "basicauth";
        if (authMethod.equalsIgnoreCase("basicauth")) {
            if (inflightEvent.getAuthClientId() != null && inflightEvent.getAuthClientSecret() != null) {
                httpHeaders.setBasicAuth(inflightEvent.getAuthClientId(), inflightEvent.getAuthClientSecret());
            }
            else {
                String msg = String.format("Missing BasicAuth credentials for subscriptionCode %s. Event is %s.",
                        inflightEvent.getSubscriptionCode(), inflightEvent.toShortLog());
                LOGGER.error(msg);
                throw new BrokerException(HttpStatus.INTERNAL_SERVER_ERROR, msg);
            }
        }
        else if (authMethod.equalsIgnoreCase("oauth2")) {
            if (inflightEvent.getAuthScope() != null) {
                try {
                    String accessToken = getOAuth2AccessToken(inflightEvent.getAuthScope());
                    httpHeaders.setBearerAuth(accessToken);
                } catch (Exception ex) {
                    String msg = String.format("Error while getting OAuth2 access token with scope %s for subscriptionCode %s. Event is %s.",
                            inflightEvent.getAuthScope(), inflightEvent.getSubscriptionCode(), inflightEvent.toShortLog());
                    LOGGER.error(msg, ex);
                    throw new BrokerException(HttpStatus.UNAUTHORIZED, msg, ex);
                }
            }
            else {
                String msg = String.format("OAuth2 scope is null for subscriptionCode %s. Event is %s.",
                        inflightEvent.getSubscriptionCode(), inflightEvent.toShortLog());
                LOGGER.error(msg);
                throw new BrokerException(HttpStatus.INTERNAL_SERVER_ERROR, msg);
            }
        }
        else {
            String msg = String.format("Invalid auth method %s for subscriptionCode %s. Event is %s.",
                    authMethod, inflightEvent.getSubscriptionCode(), inflightEvent.toShortLog());
            LOGGER.error(msg);
            throw new BrokerException(HttpStatus.INTERNAL_SERVER_ERROR, msg);
        }

        try {
            httpHeaders.setContentType(MediaType.valueOf(inflightEvent.getWebhookContentType()));
        } catch (Exception ex) {
            String msg = String.format("Error while setting Content-Type header %s for subscriptionCode %s. Event is %s.",
                    inflightEvent.getWebhookContentType(), inflightEvent.getSubscriptionCode(), inflightEvent.toShortLog());
            LOGGER.error(msg, ex);
            throw new BrokerException(HttpStatus.INTERNAL_SERVER_ERROR, msg, ex);
        }

        String webhookHeadersAsString = inflightEvent.getWebhookHeaders();
        if (webhookHeadersAsString != null && ! webhookHeadersAsString.trim().isEmpty()) {
            String[] headersAndValuesAsString = webhookHeadersAsString.trim().split(";");
            for (String headerAndValueAsString : headersAndValuesAsString) {
                String[] headerAndValue = headerAndValueAsString.trim().split(":");
                if (headerAndValue.length == 2) {
                    try {
                        httpHeaders.set(headerAndValue[0].trim(), headerAndValue[1].trim());
                    } catch (Exception ex) {
                        String msg = String.format("Error while setting headers for subscriptionCode %s. Event is %s.",
                                inflightEvent.getSubscriptionCode(), inflightEvent.toShortLog());
                        LOGGER.error(msg, ex);
                        throw new BrokerException(HttpStatus.INTERNAL_SERVER_ERROR, msg, ex);
                    }
                }
                else {
                    String msg = String.format("Error while parsing header %s for subscriptionCode %s. Event is %s.",
                            headerAndValueAsString, inflightEvent.getSubscriptionCode(), inflightEvent.toShortLog());
                    LOGGER.error(msg);
                    throw new BrokerException(HttpStatus.INTERNAL_SERVER_ERROR, msg);
                }
            }
        }

        // charset UTF8 has been defined during the creation of RestTemplate

        EventToSubscriber eventToSubscriber = EventToSubscriber.from(inflightEvent);
        HttpEntity<EventToSubscriber> request = new HttpEntity<>(eventToSubscriber, httpHeaders);

        try {

            LOGGER.debug("Calling the webhook at {}. Event is {}.",
                    inflightEvent.getWebhookUrl(), eventToSubscriber.cloneWithoutSensitiveData());
            ResponseEntity<Void> response = restTemplate.exchange(
                    inflightEvent.getWebhookUrl(), HttpMethod.POST, request, Void.class);
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
                String msg = String.format("Connection Refused error while calling the webhook at %s. Event is %s.",
                        inflightEvent.getWebhookUrl(), inflightEvent.toShortLog());
                LOGGER.error(msg, ex);

                inflightEvent.setWebhookConnectionErrorOccurred(true);
                inflightEvent.setWebhookHttpStatus(HttpStatus.BAD_GATEWAY.value());
                LOGGER.debug("Returning the event {}", inflightEvent.cloneWithoutSensitiveData());
                return inflightEvent;
            }

            else if (ex.getMessage().contains("Read timed out")) {
                String msg = String.format("Read Timeout error while calling the webhook at %s. Event is %s.",
                        inflightEvent.getWebhookUrl(), inflightEvent.toShortLog());
                LOGGER.error(msg, ex);

                inflightEvent.setWebhookReadTimeoutErrorOccurred(true);
                inflightEvent.setWebhookHttpStatus(HttpStatus.GATEWAY_TIMEOUT.value());
                LOGGER.debug("Returning the event {}", inflightEvent.cloneWithoutSensitiveData());
                return inflightEvent;
            }

            else {
                String msg = String.format("Error while calling the webhook at %s. Event is %s.",
                        inflightEvent.getWebhookUrl(), inflightEvent.toShortLog());
                LOGGER.error(msg, ex);

                inflightEvent.setWebhookHttpStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
                LOGGER.debug("Returning the event {}", inflightEvent.cloneWithoutSensitiveData());
                return inflightEvent;
            }
        }
    }


    //********** OAuth2 Management *************************************************************************************

    private final Map<String, OAuth2TokenResponseWithExpirationDate> oauth2TokenResponseWithExpirationDateCache = new ConcurrentHashMap<>();

    // This method is marked as synchronized for safety reason
    private synchronized String getOAuth2AccessToken(String authScope) {
        String accessToken = null;
        OAuth2TokenResponseWithExpirationDate tokenResponseWithExpirationDate = oauth2TokenResponseWithExpirationDateCache.get(authScope);
        if (tokenResponseWithExpirationDate != null) {
            if (Instant.now().plusSeconds(10).isAfter(tokenResponseWithExpirationDate.getExpirationDate())) {   // 10 second safety margin before expiration
                // token expired or close to expire
                oauth2TokenResponseWithExpirationDateCache.remove(authScope);
                tokenResponseWithExpirationDate = null;
            }
        }
        if (tokenResponseWithExpirationDate != null) {
            accessToken = tokenResponseWithExpirationDate.getTokenResponse().getAccess_token();
        }
        else {
            LOGGER.debug("Calling the OAuth2 issuer at {} to get token for scope {}", config.getOauth2TokenEndpoint(), authScope);

            HttpHeaders httpHeaders = new HttpHeaders();
            httpHeaders.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            httpHeaders.setAccept(List.of(MediaType.APPLICATION_JSON));
            httpHeaders.setCacheControl("no-cache");
            httpHeaders.setBasicAuth(config.getOauth2ClientId(), config.getOauth2ClientSecret());

            String requestData = "grant_type=client_credentials&scope=" + authScope;

            HttpEntity<String> request = new HttpEntity<>(requestData, httpHeaders);

            ResponseEntity<OAuth2TokenResponse> response = restTemplateForOAuth2Issuer.exchange(
                    config.getOauth2TokenEndpoint(), HttpMethod.POST, request, OAuth2TokenResponse.class);
            LOGGER.debug("The OAuth2 issuer returned the status code {}", response.getStatusCode());
            OAuth2TokenResponse tokenResponse = response.getBody();

            tokenResponseWithExpirationDate = new OAuth2TokenResponseWithExpirationDate();
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
    private static class OAuth2TokenResponse {
        String token_type;
        long expires_in;
        String access_token;
        String scope;
    }
    @Data
    @NoArgsConstructor
    private static class OAuth2TokenResponseWithExpirationDate {
        OAuth2TokenResponse tokenResponse;
        Instant expirationDate;
    }
}
