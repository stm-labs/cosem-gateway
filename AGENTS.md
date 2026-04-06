# AGENTS

## Repository layout

- `gateway/`: Spring Boot DLMS/COSEM push receiver that decodes incoming frames and publishes normalized messages to Kafka.
- `simulator/`: standalone Maven module reserved for the DLMS/COSEM simulator.
- `pom.xml`: parent aggregator for all Maven modules.
- `Dockerfile`: runtime image definition for the `gateway` module artifact.

## Build and run

- Build all modules: `mvn package`
- Build gateway only: `mvn -pl gateway package`
- Build simulator only: `mvn -pl simulator package`
- Gateway jar output: `gateway/target/gateway-*.jar`
- Simulator runnable jar output: `simulator/target/simulator-*-jar-with-dependencies.jar`
- Run gateway: `java -jar gateway/target/gateway-0.1.0-SNAPSHOT.jar`
- Run simulator: `java -jar simulator/target/simulator-0.1.0-SNAPSHOT-jar-with-dependencies.jar --serial=SIM00000001 --interval-sec=10`
- Smoke test: start gateway first, then run simulator with `--count=1` and verify the gateway logs `Processing push from device=... rawValues=47`

## Temporary files

- All temporary files must be created under repo-local `.tmp/`.
- Do not create extracted archives, probe sources, copied jars, or smoke-test logs in the repository root.
- Preferred locations:
- `.tmp/smoke/` for gateway and simulator logs
- `.tmp/extract/` for unpacked third-party archives
- `.tmp/tools/` for one-off probe code or local investigation artifacts
- `.tmp/` is gitignored and is the only approved scratch area for this repository.

## Configuration notes

- Gateway runtime settings live in `gateway/src/main/resources/application.yml`.
- Kafka topic names are configured under `cosem.kafka.topics`.
- Device-to-profile mapping is configured under `cosem.devices`.
- Device profile YAML files are expected under `gateway/src/main/resources/device-profiles/`.

## Development notes

- All text source files must be saved as UTF-8 without BOM.
- Do not use UTF-8 with BOM for Java, XML, YAML, Markdown, or properties files.
- Keep shared protocol or payload classes intentionally separated from simulator-specific logic unless a dedicated shared module is introduced.
- When adding simulator functionality, keep it independent from Spring Boot unless there is a concrete need to reuse the gateway runtime model.
- If more reusable code appears across `gateway` and `simulator`, introduce a separate shared Maven module instead of coupling them directly.
