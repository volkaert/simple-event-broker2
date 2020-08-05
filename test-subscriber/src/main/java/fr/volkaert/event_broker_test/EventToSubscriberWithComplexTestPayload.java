package fr.volkaert.event_broker_test;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class EventToSubscriberWithComplexTestPayload extends EventToSubscriberWithoutPayload {

    private ComplexTestPayload payload;
}
