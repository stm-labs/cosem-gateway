/*
 * cosem-gateway - DLMS/COSEM push-receiver gateway
 * Copyright (C) 2026  STM Labs
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; version 2 of the License.
 */
package io.cosemgateway.output;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.cosemgateway.model.MeterMessage;
import io.cosemgateway.model.RawFrameMessage;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Publishes normalised meter messages to Kafka topics.
 */
@Slf4j
@Getter
@Component
public class KafkaPublisher {

    private final KafkaTemplate<String, String> kafka;
    private final ObjectMapper json;
    private final String readingsTopic;
    private final String eventsTopic;
    private final String alarmsTopic;
    private final String rawTopic;

    public KafkaPublisher(
            KafkaTemplate<String, String> kafka,
            @Value("${cosem.kafka.topics.readings:meter.readings}") String readingsTopic,
            @Value("${cosem.kafka.topics.events:meter.events}") String eventsTopic,
            @Value("${cosem.kafka.topics.alarms:meter.alarms}") String alarmsTopic,
            @Value("${cosem.kafka.topics.raw:meter.raw}") String rawTopic
    ) {
        this.kafka = kafka;
        this.json = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        this.readingsTopic = readingsTopic;
        this.eventsTopic = eventsTopic;
        this.alarmsTopic = alarmsTopic;
        this.rawTopic = rawTopic;
    }

    public void publish(String topic, String key, MeterMessage message) {
        publishJson(topic, key, message);
    }

    public void publishRaw(String key, RawFrameMessage message) {
        publishJson(rawTopic, key, message);
    }

    private void publishJson(String topic, String key, Object message) {
        try {
            String payload = json.writeValueAsString(message);
            kafka.send(topic, key, payload)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.error("Failed to send to topic {}: {}", topic, ex.getMessage(), ex);
                        } else {
                            log.debug("Sent to {}:{} offset={}",
                                    topic, key,
                                    result.getRecordMetadata().offset());
                        }
                    });
        } catch (Exception e) {
            log.error("JSON serialisation error for device {}: {}", key, e.getMessage(), e);
        }
    }
}
