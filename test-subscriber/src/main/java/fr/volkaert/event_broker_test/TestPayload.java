package fr.volkaert.event_broker_test;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class TestPayload {

    private String message;
    private long timeToSleepInMillis;
}
