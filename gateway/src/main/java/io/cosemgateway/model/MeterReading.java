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
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A single scalar reading: value + unit of measurement.
 *
 * <p>Scaler is applied during mapping (e.g. Wh -> kWh).
 * Raw string values (relay status, firmware version) use {@link #stringValue}.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@JsonInclude(Include.NON_NULL)
public class MeterReading {

    /** Numeric reading scaled to the display unit. Null for string readings. */
    private Double value;

    /** Unit of measurement ("kWh", "V", "A", "W", "Hz", "", ...). */
    private String unit;

    /** Non-numeric reading (relay status, firmware version, IMEI, ...). */
    private String stringValue;

    public static MeterReading numeric(double value, String unit) {
        return new MeterReading(value, unit, null);
    }

    public static MeterReading string(String value) {
        return new MeterReading(null, null, value);
    }
}
