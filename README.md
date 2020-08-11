# simple-event-broker2 project

The v2 version uses SpringBoot (and not Quarkus) and Apache Pulsar (and not a custom standalone memory implementation).


## The various modules/components
- Apache Pulsar. In dev mode, it uses port 8080 and 6650.
- Publication Gateway. In dev mode, it uses port 8081.
- Standard Publication Adapter. In dev mode, it uses port 8082.
- Pulsar Publication Manager. In dev mode, it uses port 8083.
- Pulsar Subscription Manager. In dev mode, it uses port 8084.
- Subscription Gateway. In dev mode, it uses port 8085.
- Standard Subscription Adapter. In dev mode, it uses port 8086.
- Catalog. In dev mode, it uses port 8089.
- Test/Fake Subscriber. In dev mode, it uses port 8099.


## Event flow
1. Event Publisher 
2. Publication Gateway 
3. Standard Publication Adapter 
4. Pulsar Publication Manager
5. Apache Pulsar
6. Pulsar Subscription Manager
7. Subscription Gateway
8. Standard Subscription Adapter
9. Event Subscriber


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
```
cd standard-subscription-adapter
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
