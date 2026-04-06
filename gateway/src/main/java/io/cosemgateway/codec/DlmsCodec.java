/*
 * cosem-gateway - DLMS/COSEM push-receiver gateway
 * Copyright (C) 2026  STM Labs
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; version 2 of the License.
 */
package io.cosemgateway.codec;

import gurux.dlms.GXByteBuffer;
import gurux.dlms.GXDLMSNotify;
import gurux.dlms.GXReplyData;
import gurux.dlms.enums.InterfaceType;
import gurux.dlms.objects.GXDLMSObject;
import gurux.dlms.objects.GXDLMSObjectCollection;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Wraps Gurux DLMS library to decode incoming DLMS push frames.
 */
@Slf4j
@Component
public class DlmsCodec {

    /**
     * Decodes a raw DLMS TCP frame (WPDU header + xDLMS PDU).
     *
     * @param frame complete frame bytes as received from the TCP stream
     * @return list of COSEM objects parsed from the push notification
     * @throws Exception if the frame cannot be decoded
     */
    public DecodedPush decode(byte[] frame) throws Exception {
        GXDLMSNotify notify = new GXDLMSNotify(true, 1, 16, InterfaceType.WRAPPER);

        GXReplyData reply = new GXReplyData();
        GXByteBuffer data = new GXByteBuffer(frame);

        notify.getData(data, reply);
        while (reply.isMoreData() && data.position() < data.size()) {
            notify.getData(data, reply);
        }

        if (reply.getError() != 0) {
            throw new IllegalStateException("DLMS error code in reply: " + reply.getError());
        }

        GXDLMSObjectCollection objects = notify.getObjects();
        String systemTitle = extractSystemTitle(reply);
        List<?> rawValues = extractRawValues(reply.getValue());

        List<GXDLMSObject> parsed = new ArrayList<>(objects);
        log.debug("Decoded push frame: systemTitle={}, objects={}, rawValues={}",
                systemTitle, parsed.size(), rawValues == null ? 0 : rawValues.size());
        return new DecodedPush(systemTitle, parsed, rawValues);
    }

    private String extractSystemTitle(GXReplyData reply) {
        return "unknown-" + reply.getInvokeId();
    }

    private static List<?> extractRawValues(Object value) {
        if (value instanceof List<?> list) {
            return list;
        }
        return null;
    }

    public record DecodedPush(String systemTitle, List<GXDLMSObject> objects, List<?> rawValues) {}
}
