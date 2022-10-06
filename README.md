# Mobile ID (MID) REST API proxy service 

A demo application for sharing background jobs using [Apache Ignite](https://ignite.apache.org/docs/latest/) cluster

#### Used features:
- [Topic-Based Messaging](https://ignite.apache.org/docs/latest/messaging) for publishing background job tasks
- [Distributed semaphore](https://ignite.apache.org/docs/latest/data-structures/semaphore) for locking background job for processing
- [Cache Queries](https://ignite.apache.org/docs/latest/key-value-api/using-cache-queries) for finding unprocessed business objects and returning only subset of properties (E.g. background job info)

## Prerequisites
- [mid-rest-mock docker image](https://github.com/aarmam/mid-rest-mock)

## Job sharing process

```mermaid
sequenceDiagram
    participant C as Client
    participant L as Loadbalancer
    participant P1 as MidProxy-01
    participant P2 as MidProxy-02
    participant I1 as Ignite-01
    participant I2 as Ignite-02
    participant M as MidRestApi
C->>L: POST /authentication
L->>+P1: POST /authentication
P1->>+M: POST /authentication
M-->>-P1: session_id
P1->>P1: set mid_request status to INITIATED
P1->>I1: store mid_request data
P1-->>I1: publish session_id to status polling topic
P1-->>-C: return session_id

par distributed lock for session_id
    I1-->>P1: session_id from status polling topic
    P1->>+I1: get mid_request for session_id
    I1-->>-P1: get mid_request for session_id
    P1->>I1: aquire semaphore for session_id
    alt semaphore aquired
        rect rgb(200, 200, 200)
            I1-->>+P1: sempahore for session_id        
            P1->>P1: set mid_request status to RUNNING
            P1->>I1: update mid_request data
            P1->>+M: GET /authentication/session/{session_id}
            alt success
                M-->>-P1: session status result
                P1->>P1: validate result
                P1-->>P1: set mid_request status to RESULT
                P1->>I1: update mid_request data
                P1-->>I1: release semaphore
            else exception
                P1->>P1: set mid_request status to EXCEPTION
                P1->>I1: update mid_request data
                P1-->>I1: release semaphore
            else ignite client node left topology
                activate I1
                I1->>I1: client node topology event
                I1->>I1: release semaphore for session_id
                deactivate I1
            end        
        end
    else semaphore declined
        P1-->-P1: do nothing
    end
and
    I1-->>P2: session_id from status polling topic
    Note right of P2: Same race to aquire semaphore as in MidProxy-01
end

par find unprocessed mid_reqest's
loop scheduled process every x seconds
       P1->>+I1: find mid_requests where status == INITIATED or (status == RUNNING && status_time + max_polling_time < now)
       I1-->>-P1: list of unprocessed session_id's
       loop for each
        P1-->>P1: aquire semaphore for session_id and start polling process
       end
end
and
    P1->>I1: find mid_requests where...
    Note right of P2: Same process
end
```

## Build docker image

```shell
./mvnw spring-boot:build-image
```
## Run docker containers

```shell
docker compose up
```

## Running performance test

```shell
./mvnw gatling:test -DmidRestProxyUrl=http://localhost:8080
```

## Metrics

- [Grafana](http://gafana.localhost:3000/)
- [Prometheus](http://prometheus.localhost:9090/targets)