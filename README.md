# cosem-gateway

Multi-module Maven repository for a DLMS/COSEM TCP gateway and a live simulator.

## Modules

- `gateway/`: Spring Boot application that accepts DLMS/COSEM push notifications over TCP, maps OBIS values, and publishes normalized payloads to Kafka.
- `simulator/`: standalone DLMS/COSEM simulator that sends periodic Gurux `DataNotification` frames and behaves like a meter.

## Build

```bash
mvn package
```

Artifacts:

- `gateway/target/gateway-0.1.0-SNAPSHOT.jar`
- `simulator/target/simulator-0.1.0-SNAPSHOT-jar-with-dependencies.jar`

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

If Kafka is available and topics exist, the gateway will also publish to the configured topics under `cosem.kafka.topics`.

## Temporary files

All temporary and investigation files must live under `.tmp/`.

Recommended layout:

- `.tmp/smoke/` for smoke-test logs
- `.tmp/extract/` for unpacked archives
- `.tmp/tools/` for one-off probe code

Do not place logs, extracted libraries, or scratch files in the repository root.


