package fr.volkaert.event_broker_test_oauth2;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class TestPayload {

    private String message;
    private long timeToSleepInMillis;
}
