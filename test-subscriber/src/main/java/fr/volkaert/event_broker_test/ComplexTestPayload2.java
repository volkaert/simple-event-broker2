package fr.volkaert.event_broker_test;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
public class ComplexTestPayload2 {

    private String message;
    private long timeToSleepInMillis;
    private String someStringParam;
    private List<Item2> items2;

    @Data
    @NoArgsConstructor
    public static class Item2 {
        private int p1;
        private int p2;
    }
}
