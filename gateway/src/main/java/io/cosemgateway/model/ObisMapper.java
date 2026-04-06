/*
 * cosem-gateway - DLMS/COSEM push-receiver gateway
 * Copyright (C) 2026  STM Labs
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; version 2 of the License.
 */
package io.cosemgateway.model;

import gurux.dlms.GXDateTime;
import gurux.dlms.objects.GXDLMSData;
import gurux.dlms.objects.GXDLMSDemandRegister;
import gurux.dlms.objects.GXDLMSDisconnectControl;
import gurux.dlms.objects.GXDLMSExtendedRegister;
import gurux.dlms.objects.GXDLMSClock;
import gurux.dlms.objects.GXDLMSObject;
import gurux.dlms.objects.GXDLMSRegister;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Maps COSEM objects from a decoded push frame to named JSON fields
 * using the device profile OBIS mapping table.
 */
@Slf4j
@Component
public class ObisMapper {

    public Map<String, Object> map(List<GXDLMSObject> objects, DeviceProfile profile) {
        Map<String, DeviceProfile.ObjectMapping> byObis = profile.getObjects().stream()
                .collect(Collectors.toMap(DeviceProfile.ObjectMapping::getObis, mapping -> mapping, (left, right) -> left));

        return objects.stream()
                .flatMap(obj -> {
                    String obis = obj.getLogicalName();
                    DeviceProfile.ObjectMapping mapping = byObis.get(obis);
                    if (mapping == null) {
                        log.trace("No mapping for OBIS {}, skipping", obis);
                        return java.util.stream.Stream.empty();
                    }
                    try {
                        Object reading = extractReading(obj, mapping);
                        if (reading == null) {
                            return java.util.stream.Stream.empty();
                        }
                        return java.util.stream.Stream.of(Map.entry(mapping.getField(), reading));
                    } catch (Exception e) {
                        log.warn("Failed to extract value for OBIS {} ({}): {}",
                                obis, mapping.getField(), e.getMessage());
                        return java.util.stream.Stream.empty();
                    }
                })
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                        (left, right) -> left, java.util.LinkedHashMap::new));
    }

    public Map<String, Object> mapPushValues(List<?> values, DeviceProfile profile) {
        List<DeviceProfile.ObjectMapping> pushMappings = profile.getObjects().stream()
                .filter(DeviceProfile.ObjectMapping::isPush)
                .toList();

        if (pushMappings.isEmpty()) {
            log.warn("Profile {} does not define push objects", profile.getId());
            return java.util.Collections.emptyMap();
        }

        if (values == null || values.isEmpty()) {
            return java.util.Collections.emptyMap();
        }

        if (values.size() != pushMappings.size()) {
            log.warn("Push value count {} does not match configured push object count {} for profile {}",
                    values.size(), pushMappings.size(), profile.getId());
        }

        int count = Math.min(values.size(), pushMappings.size());
        Map<String, Object> mapped = new java.util.LinkedHashMap<>(count);
        for (int i = 0; i < count; i++) {
            DeviceProfile.ObjectMapping mapping = pushMappings.get(i);
            Object reading = extractReading(values.get(i), mapping);
            if (reading != null) {
                mapped.put(mapping.getField(), reading);
            }
        }
        return mapped;
    }

    private Object extractReading(GXDLMSObject obj, DeviceProfile.ObjectMapping mapping) {
        Object rawValue = getRawValue(obj, mapping);
        return extractReading(rawValue, mapping);
    }

    private Object extractReading(Object rawValue, DeviceProfile.ObjectMapping mapping) {
        if (rawValue == null) {
            return null;
        }

        if (rawValue instanceof String s) {
            return MeterReading.string(s);
        }
        if (rawValue instanceof byte[] bytes) {
            return MeterReading.string(new String(bytes, StandardCharsets.UTF_8));
        }
        if (rawValue instanceof GXDateTime dateTime) {
            return MeterReading.string(dateTime.toFormatMeterString(Locale.ROOT));
        }

        double numeric = toDouble(rawValue);
        if (mapping.getScaler() != 0) {
            numeric *= Math.pow(10, mapping.getScaler());
        }
        return MeterReading.numeric(roundTo6(numeric), mapping.getUnit() != null ? mapping.getUnit() : "");
    }

    private Object getRawValue(GXDLMSObject obj, DeviceProfile.ObjectMapping mapping) {
        int attribute = mapping.getAttribute() > 0 ? mapping.getAttribute() : 2;
        if (obj instanceof GXDLMSDemandRegister demandRegister) {
            return attribute == 2 ? demandRegister.getCurrentAverageValue() : null;
        }
        if (obj instanceof GXDLMSExtendedRegister extendedRegister) {
            return attribute == 2 ? extendedRegister.getValue() : null;
        }
        if (obj instanceof GXDLMSRegister register) {
            return attribute == 2 ? register.getValue() : null;
        }
        if (obj instanceof GXDLMSData data) {
            return data.getValue();
        }
        if (obj instanceof GXDLMSClock clock) {
            return clock.getTime();
        }
        if (obj instanceof GXDLMSDisconnectControl disconnectControl) {
            return disconnectControl.getControlState().toString();
        }
        return obj.getLogicalName();
    }

    private static double toDouble(Object value) {
        return switch (value) {
            case Number number -> number.doubleValue();
            case String string -> Double.parseDouble(string.trim());
            default -> throw new IllegalArgumentException("Cannot convert to double: " + value.getClass());
        };
    }

    private static double roundTo6(double value) {
        return Math.round(value * 1_000_000.0) / 1_000_000.0;
    }
}
