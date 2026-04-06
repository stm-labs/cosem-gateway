/*
 * cosem-gateway - DLMS/COSEM push-receiver gateway
 * Copyright (C) 2026  STM Labs
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; version 2 of the License.
 */
package io.cosemgateway.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The normalised JSON payload published to Kafka.
 *
 * <p>Example (meter.readings topic):
 * <pre>{@code
 * {
 *   "device_id": "SAI0012345678",
 *   "timestamp": "2026-04-06T14:30:00Z",
 *   "profile": "saiman_dala_3ph",
 *   "readings": {
 *     "active_energy_import_total": {"value": 12345.67, "unit": "kWh"},
 *     "voltage_l1": {"value": 228.4, "unit": "V"}
 *   },
 *   "alarms": []
 * }
 * }</pre>
 */
@Getter
@Setter
@NoArgsConstructor
@JsonInclude(Include.NON_NULL)
public class MeterMessage {

    /** Logical device identifier (serial number or system title hex). */
    private String device_id;

    /** ISO-8601 timestamp when the data was received / measured. */
    private Instant timestamp;

    /** Device profile name (matches the YAML profile id). */
    private String profile;

    /** Named readings: field name -> value+unit. */
    private Map<String, Object> readings = new LinkedHashMap<>();

    /** Active alarms present in this push notification. */
    private List<String> alarms = new ArrayList<>();
}
