/*
 * cosem-gateway - DLMS/COSEM push-receiver gateway
 * Copyright (C) 2026  STM Labs
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; version 2 of the License.
 */
package io.cosemgateway.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * In-memory representation of a device profile YAML file.
 *
 * <p>YAML structure:
 * <pre>
 * id: saiman_dala_3ph
 * manufacturer: SAIMAN
 * model: ДАЛА СА4(У)-Э720
 * objects:
 *   - obis: "1.0.1.8.0.255"
 *     class_id: 3
 *     field: active_energy_import_total
 *     unit: kWh
 *     scaler: -3          # raw value is Wh x 10^-3 -> kWh
 *     topic: meter.readings
 * </pre>
 */
@Getter
@Setter
@NoArgsConstructor
public class DeviceProfile {

    private String id;
    private String manufacturer;
    private String model;
    private List<ObjectMapping> objects;

    @Getter
    @Setter
    @NoArgsConstructor
    public static class ObjectMapping {
        /** DLMS OBIS code, e.g. "1.0.1.8.0.255". */
        private String obis;
        /** COSEM Interface Class ID (1=Data, 3=Register, 7=ProfileGeneric, ...). */
        private int class_id;
        /** Attribute index to read (default 2 = value). */
        private int attribute = 2;
        /** Output JSON field name. */
        private String field;
        /** Physical unit string for JSON output ("kWh", "V", "A", ...). */
        private String unit;
        /**
         * Power-of-10 scaler applied to the raw integer value.
         * finalValue = rawValue x 10^scaler
         * Set to 0 when no scaling needed (raw value already in target unit).
         */
        private int scaler;
        /** Kafka topic: meter.readings | meter.events | meter.alarms */
        private String topic;
        /** Whether this object is included in the periodic push notification order. */
        private boolean push;
        /** Optional human-readable description for documentation. */
        private String description;
    }
}
