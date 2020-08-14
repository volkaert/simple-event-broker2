# simple-event-broker2 project

The v2 version uses SpringBoot (and not Quarkus) and Apache Pulsar (and not a custom standalone memory implementation).


## The various modules/components
- Apache Pulsar. In dev mode, it uses port 8080 and 6650.
- Publication Gateway. In dev mode, it uses port 8081.
- Standard Publication Adapter. In dev mode, it uses port 8082.
- Pulsar Publication Manager. In dev mode, it uses port 8083.
- Pulsar Subscription Manager. In dev mode, it uses port 8084.
- Subscription Gateway (optional). In dev mode, it uses port 8085.
- Standard Subscription Adapter. In dev mode, it uses port 8086.
- Catalog. In dev mode, it uses port 8089.
- Test/Fake Subscriber. In dev mode, it uses port 8099.

`Apache Pulsar` (https://pulsar.apache.org) is an open-source distributed pub-sub messaging system originally created 
at Yahoo and now part of the Apache Software Foundation. This Simple Event Broker project uses Apache Pulsar as the 
underlying broker system instead of more classic RabbitMQ or Apache Kafka. This choice was made just to learn a new broker
solution and not for its intrinsic qualities.

A `Manager` (`PublicationManager` and `SubscriptionManager`) contains the logic of publication/subscription, the management
of acknowledgment of message/event (either positive or negative acknowledgment), the interaction with the underlying 
broker system (here it is Apache Pulsar but Apache Kafka or RabbitMQ could also be used - but it would require to rewrite 
some portions of the code).

An `Adapter` (`PublicationAdapter` and `SubscriptionAdapter`) contains dummy/boilerplate code to adapt the interactions 
between the broker and its ecosystem which may vary a lot. For example, in some contexts, it is ok to secure the call to 
the webhooks with just BasicAuth. In other contexts, OAuth2 may be required. Or some other security mechanism specialized
for an organization. The Adapters are independent of the underlying broker technology (Pulsar, Kafka, RabbitMQ...).

The roles and responsibilities of managers and adapters are clearly defined to allow the seamless replacement of a
component by another.

The `Catalog` is in charge of the management of the objects `EventType`, `Publication` and `Subscription`. 
Most of the other components require access to the Catalog to operate.
 
The `Publication Gateway` is the entry point to publish an event. It is based on a `Spring Cloud Gateway`. It offers 
automatic retry feature to ensure no interruption service even in the case of the release of a new version of 
a `PublicationAdapter`.
 

## Event flow
1. Event Publisher 
2. Publication Gateway 
3. Standard Publication Adapter 
4. Pulsar Publication Manager
5. Apache Pulsar
6. Pulsar Subscription Manager
7. Subscription Gateway (optional)
8. Standard Subscription Adapter
9. Event Subscriber


## Event expiration

An event will expire if one of the following condition is met:
- the `timeToLiveInSeconds` of the published event has been reached (attribute timeToLiveInSeconds of the event sent to the 
`/events` endpoint exposed by the Publication Gateway or the Publication Adapter)
- the webhook returned a 401 (UNAUTHORIZED) or 403 (FORBIDDEN) HTTP status code and the 
`broker.default-time-to-live-in-seconds-for-webhook-auth401Or403-error` (set in the SubscriptionManager config) has been reached.
- the webhook returned a 4xx (but not 401 nor 403) HTTP status code and the 
`broker.default-time-to-live-in-seconds-for-webhook-client4xx-error` (set in the SubscriptionManager config) has been reached.
- the webhook returned a 5xx HTTP status code and the `broker.default-time-to-live-in-seconds-for-webhook-sever5xx-error` 
(set in the SubscriptionManager config) has been reached.
- the webhook was unreachable (due to a wrong URL of the webhook, a network route not open...) and the 
`broker.default-time-to-live-in-seconds-for-webhook-connection-error` (set in
the SubscriptionManager config) has been reached.
- the webhook was reached but never returned a response before the `webhookReadTimeoutInSeconds` timeout (set in the 
SubscriptionAdapter and the SubscriptionManager config) was triggered and the 
`broker.default-time-to-live-in-seconds-for-webhook-read-timeout-error` (set in
the SubscriptionManager config) has been reached.

Those `broker.default-time-to-live-in-seconds-for-webhook-XXX-error` default values can be overridden for a given 
subscription using the attributes `timeToLiveInSecondsForWebhookXXXError` of the Subscription object (in the `common` 
module).

When an event expires, it is sent to a `DeadLetterQueue (DLQ)` for analysis of the cause of a failed delivery of the 
event to a subscriber. The DeadLetterQueue in Apache Pulsar uses the topic name `{eventTypeCode}_{subscriptionCode}_AppDLQ`
(notice the suffix *App*DLQ to avoid conflict with native Apache Pulsar DLQ which use the prefix DLQ).
 
 
## Predefined Event Types samples

The `Catalog` module contains some predefined event types samples. Those predefined event types (and the related
publications and subscriptions) are declared in the `resources/import.sql` file of the `Catalog` module. 

Those predefined event types samples are:
- NominalTest-EVT
- Failure401Test-EVT
- Failure500Test-EVT
- SlowTest-EVT
- ComplexPayloadTest-EVT
- ComplexPayload2Test-EVT
- TimeToLiveTest-EVT
- OAuth2Test-EVT

For each predefined event types samples, there is a webhook endpoint exposed by the `TestSubscriber1Controller` class 
in the `test-subscriber` module (exception for the `OAuth2Test-EVT` Event Type which uses an endpoint exposed by the 
`TestSubscriber1Controller` class in the `test-subscriber-oauth2` module).

To publish an event for one of those predefined event types samples named `XxxxxTest-EVT`, use the publication code
`XxxxxTest-PUB` when publishing the event on the endpoint `/events`.


## Install and run Apache Pulsar

```
$ wget https://archive.apache.org/dist/pulsar/pulsar-2.6.0/apache-pulsar-2.6.0-bin.tar.gz
$ tar xvfz apache-pulsar-2.6.0-bin.tar.gz
$ cd apache-pulsar-2.6.0
$ bin/pulsar standalone
```

## Run the modules/components

Start the modules in the order below.

### Run Apache Pulsar
```
cd apache-pulsar-2.6.0
bin/pulsar standalone
```
### Run the Catalog
```
cd catalog
../mvnw clean spring-boot:run
```
### Run the Standard Subscription Adapter

The Subscription Adapter may require to get OAuth2 Access Tokens from an AuthorizationServer if some webhooks are secured 
using OAuth2. In that case, the Subscription Adapter must be declared in the AuthorizationServer to get its clientId
and clientSecret to authenticate itself.

Create a file `set-credentials.sh` in the `standard-subscription-adapter` directory with the following lines:
```
export OAUTH2_CLIENT_ID=<PUT_HERE_YOUR_OWN_OAUTH2_CLIENT_ID>
export OAUTH2_CLIENT_SECRET=<PUT_HERE_YOUR_OWN_OAUTH2_CLIENT_SCERET>
```
Of course, a file with such sensitive data is not committed in the source code repository so you have to create your own
version locally.

Once the `set-credentials.sh`file contains the right credentials, then run the Subscription Adapter:
```
cd standard-subscription-adapter
source set-credentials.sh
../mvnw clean spring-boot:run
```
### Run the Pulsar Subscription Manager
```
cd pulsar-subscription-manager
../mvnw clean spring-boot:run
```
### Run the Pulsar Publication Manager
```
cd pulsar-publication-manager
../mvnw clean spring-boot:run
```
### Run the Standard Publication Adapter
```
cd standard-publication-adapter
../mvnw clean spring-boot:run
```
### Run the Publication Gateway
```
cd publication-gateway
../mvnw clean spring-boot:run
```
### Run the Test/fake Subscriber
```
cd test-subscriber
../mvnw clean spring-boot:run
```


## Test

### Nominal test
In this scenario, there are 2 up & healthy subscriptions, so for each published event, there are 2 successful deliveries.

```
curl --header "Content-Type: application/json" \
  --request POST \
  --data '{"publicationCode": "NominalTest-PUB","payload": { "message": "NominalTest" }, "timeToLiveInSeconds": 30 }' \
  http://localhost:8081/events
```

### Test with a faulty (HTTP status code 500) subscription
In this scenario, there are 1 up & healthy subscription but 1 up & faulty subscription which returns a HTTP status code 500 (interval server error), 
so for each published event, there are 1 successful delivery and 1 failed delivery.
```
curl --header "Content-Type: application/json" \
  --request POST \
  --data '{"publicationCode": "Failure500Test-PUB","payload": { "message": "Failure500Test" }, "timeToLiveInSeconds": 30 }' \
  http://localhost:8081/events
```

### Test with a slow subscription (2s to process the event)
In this scenario, there are 1 up & healthy subscription but 1 up & slow subscription which processes the event in 2 seconds, 
so for each published event, there are 2 successful deliveries, but one delivery is slow.

If necessary, use the `broker.webhook-read-timeout-in-seconds` property (default is 10) to set an appropriate timeout.
```
curl --header "Content-Type: application/json" \
  --request POST \
  --data '{"publicationCode": "SlowTest-PUB","payload": { "message": "SlowTest", "timeToSleepInMillis": 2000 }}' \
  http://localhost:8081/events
```

### Test with a too slow subscription (60s to process the event so a timeout is triggered)
In this scenario, there are 1 up & healthy subscription but 1 up & too slow subscription which processes the event in 60 seconds, 
so for each published event, there are 1 successful delivery and 1 failed delivery (failed because of the timeout).

If necessary, use the `broker.webhook-read-timeout-in-seconds` property (default is 10) to set an appropriate timeout.
```
curl --header "Content-Type: application/json" \
  --request POST \
  --data '{"publicationCode": "SlowTest-PUB","payload": { "message": "SlowTest", "timeToSleepInMillis": 60000 }}' \
  http://localhost:8081/events
```

### Test with a complex payload 
```
curl --header "Content-Type: application/json" \
  --request POST \
  --data '{"publicationCode": "ComplexPayloadTest-PUB","payload": { "message": "ComplexPayloadTest", "timeToSleepInMillis": 2000, "items": [{ "param1": "hello", "param2": "world" }]}, "timeToLiveInSeconds": 30 }' \
  http://localhost:8081/events
```
```
curl --header "Content-Type: application/json" \
  --request POST \
  --data '{"publicationCode": "ComplexPayload2Test-PUB","payload": { "message": "ComplexPayload2Test", "timeToSleepInMillis": 2000, "someStringParam": "hello world", "items2": [{ "p1": 1, "p2": 2 }]}, "timeToLiveInSeconds": 30 }' \
  http://localhost:8081/events
```

### Test with subscriber webhooks secured using OAuth2 

```
curl --header "Content-Type: application/json" \
  --request POST \
  --data '{"publicationCode": "OAuth2Test-PUB","payload": { "message": "NominalTest with OAuth2" }, "timeToLiveInSeconds": 30 }' \
  http://localhost:8081/events
```


## Troubleshooting
To kill a process that runs on a given port:
```
kill $(lsof -t -i:<port>)
```

To know the list of topics in Apache Pulsar:
```
cd apache-pulsar-2.6.0
bin/pulsar-admin topics list public/default
```
>`public` is the name of the tenant and `default` is the name of the namespace

To know the list of *persistent* topics in Apache Pulsar:
```
cd apache-pulsar-2.6.0
bin/pulsar-admin persistent list public/default
```
>`public` is the name of the tenant and `default` is the name of the namespace

To purge a topic:
```
cd apache-pulsar-2.6.0
bin/pulsar-admin topics delete --deleteSchema persistent://public/default/<topic>
```
>`public` is the name of the tenant and `default` is the name of the namespace


## Misc

Online Bcrypt Hash Generator and Checker(Bcrypt Calculator): https://www.devglan.com/online-tools/bcrypt-hash-generator

bin/pulsar-admin namespaces set-is-allow-auto-update-schema --enable public/default
bin/pulsar-admin namespaces set-schema-compatibility-strategy --compatibility ALWAYS_COMPATIBLE public/default


## Notes about Apache Pulsar

So far, I encountered some issues with Apache Pulsar. I'm not sure if I'm faulty, or if there are few bugs in Pulsar,
or if the Pulsar documentation is not clear enough... but I faced the following issues:

- Message.getRedeliveryCount() returns always 0 even if the message was previously negatively acknowledged. It is 
important for me to know if this is the first time is message is delivered or not, because I want to increment a counter 
only the first time (and of course I do not want to store the ids of previous messages in some database or remote cache 
and to make a check for each incoming message; the remote call would kill the performance of the system).

- It is difficult to tune the embedded Jackson (for JSON data). In my Maven dependencies, I replaced the pulsar-client 
dependency (which embeds Jackson in a shadow package) by the pulsar-client-original dependency (which does not embed
its own version of Jackson). But even with the pulsar-client-original dependency, I could not find a way to customize
the Jackson ObjectMapper used by Pulsar.

- By default, Pulsar does not activate the JSR310 compatibility and does not know how to serialize/deserialize Instant 
objects (useful for timestamps). I had to annotate all my Instant attributes with @JsonSerialize and @JsonDeserialize 
annotations and to provide my own Instant serializer/deserializer code. It is not very convenient.

- To activate JSR310 support (for >= Java 8 dates), I had to look at the code inside Schema.JSON to know how to activate
JSR310 support. Instead of Schema.JSON, I now use DefaultImplementation.newJSONSchema(SchemaDefinition.builder().withJSR310ConversionEnabled(true).withPojo(MySuperClass.class).build()).
But DefaultImplementation is an internal class of Pulsar (in the internal package), and it is awkward to have to use
a private class of a library.

- The number of listener threads on the message consumer side is set to 1 by default. With such setting, if a message 
listener takes some time to process a message, all the next messages are waiting, even if those messages are on 
different topics (and thus are not related, so they could be delivered concurrently; the delivery order must be guaranteed
for a given topic but not among all the topics). We have powerful multi-core computers, but with this default setting 
all the messages are processed sequentially which is not efficient. In this project, I set the number of listener threads
to 25 and now slow consumers on topic A do not block fast consumers on topic B. Why such a default value of 1 ?
