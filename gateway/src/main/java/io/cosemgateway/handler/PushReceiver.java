/*
 * cosem-gateway - DLMS/COSEM push-receiver gateway
 * Copyright (C) 2026  STM Labs
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; version 2 of the License.
 */
package io.cosemgateway.handler;

import io.cosemgateway.codec.DlmsCodec;
import io.cosemgateway.codec.DlmsCodec.DecodedPush;
import io.cosemgateway.model.DeviceProfile;
import io.cosemgateway.model.DeviceRegistry;
import io.cosemgateway.model.MeterMessage;
import io.cosemgateway.model.ObisMapper;
import io.cosemgateway.model.RawFrameMessage;
import io.cosemgateway.output.KafkaPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Orchestrates processing of a received DLMS push frame.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PushReceiver {

    private final DlmsCodec codec;
    private final DeviceRegistry registry;
    private final ObisMapper mapper;
    private final KafkaPublisher publisher;

    public void onFrame(byte[] frame, String remoteAddr) {
        publishRawFrame(frame, remoteAddr);
        try {
            DecodedPush decoded = codec.decode(frame);
            String systemTitle = decoded.systemTitle();

            DeviceProfile profile = registry.resolve(systemTitle);
            String deviceId = registry.toDeviceId(systemTitle);

            Map<String, Object> allReadings = decoded.rawValues() != null && !decoded.rawValues().isEmpty()
                    ? mapper.mapPushValues(decoded.rawValues(), profile)
                    : mapper.map(decoded.objects(), profile);
            deviceId = resolveDeviceId(deviceId, allReadings);
            log.info("Processing push from device={} profile={} objects={} rawValues={}",
                    deviceId, profile.getId(), decoded.objects().size(),
                    decoded.rawValues() == null ? 0 : decoded.rawValues().size());

            MeterMessage readingsMsg = new MeterMessage();
            readingsMsg.setDevice_id(deviceId);
            readingsMsg.setTimestamp(Instant.now());
            readingsMsg.setProfile(profile.getId());

            MeterMessage alarmsMsg = new MeterMessage();
            alarmsMsg.setDevice_id(deviceId);
            alarmsMsg.setTimestamp(readingsMsg.getTimestamp());
            alarmsMsg.setProfile(profile.getId());

            Map<String, String> fieldToTopic = profile.getObjects().stream()
                    .filter(o -> o.getField() != null && o.getTopic() != null)
                    .collect(Collectors.toMap(
                            DeviceProfile.ObjectMapping::getField,
                            DeviceProfile.ObjectMapping::getTopic,
                            (left, right) -> left));

            String readingsTopic = publisher.getReadingsTopic();
            String alarmsTopic = publisher.getAlarmsTopic();

            for (Map.Entry<String, Object> entry : allReadings.entrySet()) {
                String topic = fieldToTopic.getOrDefault(entry.getKey(), readingsTopic);
                if (alarmsTopic.equals(topic)) {
                    alarmsMsg.getReadings().put(entry.getKey(), entry.getValue());
                } else {
                    readingsMsg.getReadings().put(entry.getKey(), entry.getValue());
                }
            }

            if (!readingsMsg.getReadings().isEmpty()) {
                publisher.publish(readingsTopic, deviceId, readingsMsg);
            }
            if (!alarmsMsg.getReadings().isEmpty()) {
                publisher.publish(alarmsTopic, deviceId, alarmsMsg);
            }
        } catch (Exception e) {
            log.error("Failed to process DLMS frame from {}: {}", remoteAddr, e.getMessage(), e);
        }
    }

    private void publishRawFrame(byte[] frame, String remoteAddr) {
        RawFrameMessage raw = new RawFrameMessage();
        raw.setDevice_id(remoteAddr);
        raw.setRemote_addr(remoteAddr);
        raw.setTimestamp(Instant.now());
        raw.setFrame_length(frame.length);
        raw.setPayload_hex(toHex(frame));
        raw.setPayload_base64(Base64.getEncoder().encodeToString(frame));
        publisher.publishRaw(remoteAddr, raw);
    }

    private static String resolveDeviceId(String fallbackDeviceId, Map<String, Object> allReadings) {
        if (!fallbackDeviceId.startsWith("unknown-")) {
            return fallbackDeviceId;
        }
        Object serial = allReadings.get("device_serial_number");
        if (serial instanceof io.cosemgateway.model.MeterReading reading && reading.getStringValue() != null) {
            return reading.getStringValue();
        }
        return fallbackDeviceId;
    }

    private static String toHex(byte[] data) {
        StringBuilder sb = new StringBuilder(data.length * 2);
        for (byte b : data) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }
}
