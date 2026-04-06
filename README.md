# cosem-gateway

Multi-module Maven repository for a DLMS/COSEM TCP gateway and a live simulator.

## Modules

- `gateway/`: Spring Boot application that accepts DLMS/COSEM push notifications over TCP, maps OBIS values, and
  publishes normalized payloads to Kafka.
- `simulator/`: standalone DLMS/COSEM simulator that sends periodic Gurux `DataNotification` frames and behaves like a
  meter.

### Gateway configuration

These environment variables are the runtime contract for the `gateway` container in production.

| Variable                     | Required | Default          | Description                                                                                                    |
|------------------------------|----------|------------------|----------------------------------------------------------------------------------------------------------------|
| `KAFKA_BOOTSTRAP_SERVERS`    | Yes      | `localhost:9092` | Kafka bootstrap servers used by the gateway producer. In production this must point to the real Kafka cluster. |
| `COSEM_KAFKA_TOPIC_READINGS` | No       | `meter.readings` | Topic for normalized meter readings.                                                                           |
| `COSEM_KAFKA_TOPIC_EVENTS`   | No       | `meter.events`   | Reserved topic for event payloads.                                                                             |
| `COSEM_KAFKA_TOPIC_ALARMS`   | No       | `meter.alarms`   | Topic for alarm-oriented normalized fields.                                                                    |
| `COSEM_KAFKA_TOPIC_RAW`      | No       | `meter.raw`      | Topic for raw incoming TCP frames before DLMS decode.                                                          |
| `DLMS_TCP_PORT`              | No       | `4059`           | TCP port exposed by the gateway for incoming meter push connections.                                           |

Notes:

- `cosem.devices` is still configured
  in [application.yml](/D:/stm/pi/cosem-gateway/gateway/src/main/resources/application.yml). If you want
  device-to-profile mapping from environment variables, that should be added explicitly as a separate feature.
- The current Jib image for the gateway uses `eclipse-temurin:25-jre` as the base image.

## Build

```bash
mvn package
```

Artifacts:

- `gateway/target/gateway-0.1.0-SNAPSHOT.jar`
- `simulator/target/simulator-0.1.0-SNAPSHOT-jar-with-dependencies.jar`

Build the gateway container image with Jib:

```bash
mvn -pl gateway jib:dockerBuild
```

## Run

Start the gateway:

```bash
java -jar gateway/target/gateway-0.1.0-SNAPSHOT.jar
```

Start the simulator:

```bash
java -jar simulator/target/simulator-0.1.0-SNAPSHOT-jar-with-dependencies.jar --serial=SIM00000001 --interval-sec=10
```

Useful simulator options:

- `--count=1` send one notification and exit
- `--interval-sec=10` send every 10 seconds
- `--host=127.0.0.1`
- `--port=4059`
- `--client-address=16`
- `--server-address=1`

## Smoke test

1. Start the gateway.
2. Run the simulator once:

```bash
java -jar simulator/target/simulator-0.1.0-SNAPSHOT-jar-with-dependencies.jar --count=1 --interval-sec=1
```

3. Verify the gateway logs contain:

- `DLMS/COSEM TCP server listening on port 4059`
- `Meter connected`
- `Processing push from device=SIM00000001`

If Kafka is available and topics exist, the gateway will also publish to the configured topics under
`cosem.kafka.topics`.

Raw incoming DLMS frames are also published without parsing to `cosem.kafka.topics.raw` as JSON with:

- `timestamp`
- `remote_addr`
- `frame_length`
- `payload_hex`
- `payload_base64`

## Kafka topics

Configured in [application.yml](/D:/stm/pi/cosem-gateway/gateway/src/main/resources/application.yml) under
`cosem.kafka.topics`.

### `meter.readings`

- Purpose: normalized meter readings extracted from DLMS push notifications
- Kafka key: `device_id`
- Payload
  model: [MeterMessage.java](/D:/stm/pi/cosem-gateway/gateway/src/main/java/io/cosemgateway/model/MeterMessage.java)
- Reading value
  model: [MeterReading.java](/D:/stm/pi/cosem-gateway/gateway/src/main/java/io/cosemgateway/model/MeterReading.java)

Example payload shape:

```json
{
  "device_id": "SIM00000001",
  "timestamp": "2026-04-06T12:31:23Z",
  "profile": "saiman_dala_3ph",
  "readings": {
    "active_energy_import_total": {
      "value": 2480001.0,
      "unit": "Wh"
    },
    "voltage_l1": {
      "value": 229.98,
      "unit": "V"
    },
    "device_serial_number": {
      "stringValue": "SIM00000001"
    }
  },
  "alarms": []
}
```

### `meter.alarms`

- Purpose: normalized alarm and event-like values routed from the same incoming push
- Kafka key: `device_id`
- Payload
  model: [MeterMessage.java](/D:/stm/pi/cosem-gateway/gateway/src/main/java/io/cosemgateway/model/MeterMessage.java)
- Fields are selected by `topic: meter.alarms` inside the device profile YAML, for
  example [saiman_dala_3ph.yml](/D:/stm/pi/cosem-gateway/gateway/src/main/resources/device-profiles/saiman_dala_3ph.yml)

Example payload shape:

```json
{
  "device_id": "SIM00000001",
  "timestamp": "2026-04-06T12:31:23Z",
  "profile": "saiman_dala_3ph",
  "readings": {
    "current_alarm": {
      "value": 0.0,
      "unit": ""
    }
  },
  "alarms": []
}
```

### `meter.events`

- Purpose: reserved topic for event payloads
- Kafka key: `device_id`
- Current state: configured in [application.yml](/D:/stm/pi/cosem-gateway/gateway/src/main/resources/application.yml),
  but not actively populated by the current pipeline

### `meter.raw`

- Purpose: raw incoming TCP frames from devices, published before decode
- Kafka key: current remote address string
- Payload
  model: [RawFrameMessage.java](/D:/stm/pi/cosem-gateway/gateway/src/main/java/io/cosemgateway/model/RawFrameMessage.java)

Example payload shape:

```json
{
  "device_id": "/127.0.0.1:60541",
  "remote_addr": "/127.0.0.1:60541",
  "timestamp": "2026-04-06T12:31:23Z",
  "frame_length": 278,
  "payload_hex": "000100100001010e0f...",
  "payload_base64": "AAEAEAABAQ4P..."
}
```

## Temporary files

All temporary and investigation files must live under `.tmp/`.

Recommended layout:

- `.tmp/smoke/` for smoke-test logs
- `.tmp/extract/` for unpacked archives
- `.tmp/tools/` for one-off probe code

Do not place logs, extracted libraries, or scratch files in the repository root.
