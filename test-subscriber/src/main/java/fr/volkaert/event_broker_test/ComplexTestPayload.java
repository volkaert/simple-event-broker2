package fr.volkaert.event_broker_test;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
public class ComplexTestPayload {

    private String message;
    private long timeToSleepInMillis;
    private List<Item> items;

    @Data
    @NoArgsConstructor
    public static class Item {
        private String param1;
        private String param2;
    }
}
