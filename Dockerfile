FROM eclipse-temurin:25-jre-jammy AS runtime

LABEL org.opencontainers.image.title="cosem-gateway"
LABEL org.opencontainers.image.description="DLMS/COSEM push-receiver gateway → Kafka"
LABEL org.opencontainers.image.licenses="GPL-2.0-only"

# Create non-root user
RUN groupadd -r cosem && useradd -r -g cosem cosem

WORKDIR /app

COPY gateway/target/gateway-*.jar app.jar

RUN chown cosem:cosem app.jar
USER cosem

# DLMS/COSEM TCP port (IEC 62056-47 default 4059)
EXPOSE 4059

ENV JAVA_OPTS="-XX:+UseG1GC -XX:MaxRAMPercentage=75"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
