/*
 * cosem-gateway - DLMS/COSEM push-receiver gateway
 * Copyright (C) 2026  STM Labs
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; version 2 of the License.
 */
package io.cosemgateway.model;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Loads device profiles from YAML files and maps device identifiers to profiles.
 *
 * <p>On startup, scans {@code classpath:device-profiles/*.yml} and loads all profiles.
 *
 * <p>Device-to-profile assignment is configured in {@code application.yml}:
 * <pre>
 * cosem:
 *   devices:
 *     "53414900AABBCCDD": saiman_dala_3ph   # systemTitle hex -> profile id
 *     "53414900EEFF0011": saiman_orman_1ph
 * </pre>
 *
 * <p>If a device is not explicitly mapped, the first loaded profile is used as default
 * (useful in single-model deployments).
 */
@Slf4j
@Component
public class DeviceRegistry {

    /** Map of profile id -> profile definition. */
    private final Map<String, DeviceProfile> profiles = new HashMap<>();

    /** Map of systemTitle (hex string) -> profile id. Configured externally. */
    @Value("#{${cosem.devices:{}}}")
    private Map<String, String> deviceProfileMap = Map.of();

    /** Cache: systemTitle -> resolved profile. */
    private final ConcurrentHashMap<String, DeviceProfile> cache = new ConcurrentHashMap<>();

    private DeviceProfile defaultProfile;

    @PostConstruct
    public void load() throws Exception {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource[] resources = resolver.getResources("classpath:device-profiles/*.yml");

        Yaml yaml = new Yaml(new Constructor(DeviceProfile.class, new LoaderOptions()));
        for (Resource r : resources) {
            try (InputStream is = r.getInputStream()) {
                DeviceProfile p = yaml.load(is);
                profiles.put(p.getId(), p);
                log.info("Loaded device profile '{}' ({}, {} objects)",
                        p.getId(), p.getModel(), p.getObjects() == null ? 0 : p.getObjects().size());
            }
        }

        if (profiles.isEmpty()) {
            log.warn("No device profiles found in classpath:device-profiles/");
            return;
        }

        defaultProfile = profiles.values().iterator().next();
        log.info("Default profile: {}", defaultProfile.getId());
    }

    /**
     * Resolves the device profile for the given system title.
     *
     * @param systemTitle hex string from the DLMS push header
     * @return matched profile, or the default profile if no mapping found
     */
    public DeviceProfile resolve(String systemTitle) {
        return cache.computeIfAbsent(systemTitle, st -> {
            String profileId = deviceProfileMap.get(st);
            if (profileId != null && profiles.containsKey(profileId)) {
                return profiles.get(profileId);
            }
            if (defaultProfile != null) {
                log.debug("No profile mapping for systemTitle={}, using default '{}'", st, defaultProfile.getId());
                return defaultProfile;
            }
            throw new IllegalStateException("No device profile found for systemTitle=" + st);
        });
    }

    /**
     * Derives a human-readable device ID from the system title.
     *
     * <p>SAIMAN system title is 8 bytes: manufacturer tag (3 bytes "SAI") + serial (5 bytes).
     * The resulting device_id is formatted as "SAI" + decimal representation of the serial bytes.
     */
    public String toDeviceId(String systemTitle) {
        if (systemTitle.startsWith("unknown")) {
            return systemTitle;
        }
        try {
            String manufacturer = hexToAscii(systemTitle.substring(0, 6));
            long serial = Long.parseLong(systemTitle.substring(6), 16);
            return "%s%010d".formatted(manufacturer, serial);
        } catch (Exception e) {
            return systemTitle;
        }
    }

    private static String hexToAscii(String hex) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < hex.length() - 1; i += 2) {
            sb.append((char) Integer.parseInt(hex.substring(i, i + 2), 16));
        }
        return sb.toString();
    }
}
