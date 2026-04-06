package io.cosemgateway.simulator;

import gurux.dlms.GXDLMSNotify;
import gurux.dlms.GXDateTime;
import gurux.dlms.enums.InterfaceType;
import gurux.dlms.enums.Unit;
import gurux.dlms.objects.GXDLMSClock;
import gurux.dlms.objects.GXDLMSData;
import gurux.dlms.objects.GXDLMSObject;
import gurux.dlms.objects.GXDLMSRegister;

import java.io.OutputStream;
import java.net.Socket;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Sends periodic DLMS DataNotification messages that emulate a live three-phase meter.
 */
public final class CosemSimulatorApp {

    private CosemSimulatorApp() {
    }

    public static void main(String[] args) throws Exception {
        Args parsed = Args.parse(args);
        if (parsed.help()) {
            printUsage();
            return;
        }

        MeterState state = new MeterState(parsed.serial(), parsed.intervalSeconds());
        long sent = 0;

        while (parsed.count() <= 0 || sent < parsed.count()) {
            try (Socket socket = new Socket(parsed.host(), parsed.port())) {
                socket.setSoTimeout((int) Duration.ofSeconds(5).toMillis());
                System.out.printf("Connected to %s:%d as serial=%s%n", parsed.host(), parsed.port(), parsed.serial());
                try (OutputStream out = socket.getOutputStream()) {
                    while (parsed.count() <= 0 || sent < parsed.count()) {
                        Snapshot snapshot = state.next();
                        byte[][] frames = buildFrames(parsed, snapshot);
                        for (byte[] frame : frames) {
                            out.write(frame);
                        }
                        out.flush();
                        sent++;
                        System.out.printf(
                                "Sent push #%d at %s A+=%.3f kWh U=[%.1f, %.1f, %.1f]V I=[%.3f, %.3f, %.3f]A%n",
                                sent,
                                snapshot.timestamp(),
                                snapshot.activeImportTotalWh() / 1000.0,
                                snapshot.voltageL1V(),
                                snapshot.voltageL2V(),
                                snapshot.voltageL3V(),
                                snapshot.currentL1A(),
                                snapshot.currentL2A(),
                                snapshot.currentL3A());
                        Thread.sleep(parsed.intervalSeconds() * 1000L);
                    }
                }
            } catch (Exception e) {
                System.err.printf("Simulator connection failed: %s%n", e.getMessage());
                Thread.sleep(parsed.reconnectDelaySeconds() * 1000L);
            }
        }
    }

    private static byte[][] buildFrames(Args args, Snapshot snapshot) throws Exception {
        GXDLMSNotify notify = new GXDLMSNotify(true, args.clientAddress(), args.serverAddress(), InterfaceType.WRAPPER);
        List<Map.Entry<GXDLMSObject, Integer>> items = new ArrayList<>();

        items.add(entry(clock("0.0.1.0.0.255", snapshot.timestamp())));
        items.add(entry(data("0.0.96.1.0.255", args.serial())));

        items.add(entry(register("1.0.1.8.0.255", snapshot.activeImportTotalWh(), 1e-3, Unit.ACTIVE_ENERGY)));
        items.add(entry(register("1.0.1.8.1.255", snapshot.activeImportT1Wh(), 1e-3, Unit.ACTIVE_ENERGY)));
        items.add(entry(register("1.0.1.8.2.255", snapshot.activeImportT2Wh(), 1e-3, Unit.ACTIVE_ENERGY)));
        items.add(entry(register("1.0.1.8.3.255", snapshot.activeImportT3Wh(), 1e-3, Unit.ACTIVE_ENERGY)));

        items.add(entry(register("1.0.2.8.0.255", snapshot.activeExportTotalWh(), 1e-3, Unit.ACTIVE_ENERGY)));
        items.add(entry(register("1.0.2.8.1.255", snapshot.activeExportT1Wh(), 1e-3, Unit.ACTIVE_ENERGY)));
        items.add(entry(register("1.0.2.8.2.255", snapshot.activeExportT2Wh(), 1e-3, Unit.ACTIVE_ENERGY)));
        items.add(entry(register("1.0.2.8.3.255", snapshot.activeExportT3Wh(), 1e-3, Unit.ACTIVE_ENERGY)));

        items.add(entry(register("1.0.3.8.0.255", snapshot.reactiveImportTotalVarh(), 1e-3, Unit.REACTIVE_ENERGY)));
        items.add(entry(register("1.0.3.8.1.255", snapshot.reactiveImportT1Varh(), 1e-3, Unit.REACTIVE_ENERGY)));
        items.add(entry(register("1.0.3.8.2.255", snapshot.reactiveImportT2Varh(), 1e-3, Unit.REACTIVE_ENERGY)));
        items.add(entry(register("1.0.3.8.3.255", snapshot.reactiveImportT3Varh(), 1e-3, Unit.REACTIVE_ENERGY)));

        items.add(entry(register("1.0.4.8.0.255", snapshot.reactiveExportTotalVarh(), 1e-3, Unit.REACTIVE_ENERGY)));
        items.add(entry(register("1.0.4.8.1.255", snapshot.reactiveExportT1Varh(), 1e-3, Unit.REACTIVE_ENERGY)));
        items.add(entry(register("1.0.4.8.2.255", snapshot.reactiveExportT2Varh(), 1e-3, Unit.REACTIVE_ENERGY)));
        items.add(entry(register("1.0.4.8.3.255", snapshot.reactiveExportT3Varh(), 1e-3, Unit.REACTIVE_ENERGY)));

        items.add(entry(register("1.0.32.7.0.255", Math.round(snapshot.voltageL1V() * 100.0), 1e-2, Unit.VOLTAGE)));
        items.add(entry(register("1.0.52.7.0.255", Math.round(snapshot.voltageL2V() * 100.0), 1e-2, Unit.VOLTAGE)));
        items.add(entry(register("1.0.72.7.0.255", Math.round(snapshot.voltageL3V() * 100.0), 1e-2, Unit.VOLTAGE)));

        items.add(entry(register("1.0.31.7.0.255", Math.round(snapshot.currentL1A() * 1000.0), 1e-3, Unit.CURRENT)));
        items.add(entry(register("1.0.51.7.0.255", Math.round(snapshot.currentL2A() * 1000.0), 1e-3, Unit.CURRENT)));
        items.add(entry(register("1.0.71.7.0.255", Math.round(snapshot.currentL3A() * 1000.0), 1e-3, Unit.CURRENT)));

        items.add(entry(register("1.0.33.7.0.255", Math.round(snapshot.pfL1() * 1000.0), 1e-3, Unit.NO_UNIT)));
        items.add(entry(register("1.0.53.7.0.255", Math.round(snapshot.pfL2() * 1000.0), 1e-3, Unit.NO_UNIT)));
        items.add(entry(register("1.0.73.7.0.255", Math.round(snapshot.pfL3() * 1000.0), 1e-3, Unit.NO_UNIT)));

        items.add(entry(register("1.0.21.7.0.255", Math.round(snapshot.activeImportPowerL1W()), 1.0, Unit.ACTIVE_POWER)));
        items.add(entry(register("1.0.41.7.0.255", Math.round(snapshot.activeImportPowerL2W()), 1.0, Unit.ACTIVE_POWER)));
        items.add(entry(register("1.0.61.7.0.255", Math.round(snapshot.activeImportPowerL3W()), 1.0, Unit.ACTIVE_POWER)));

        items.add(entry(register("1.0.22.7.0.255", Math.round(snapshot.activeExportPowerL1W()), 1.0, Unit.ACTIVE_POWER)));
        items.add(entry(register("1.0.42.7.0.255", Math.round(snapshot.activeExportPowerL2W()), 1.0, Unit.ACTIVE_POWER)));
        items.add(entry(register("1.0.62.7.0.255", Math.round(snapshot.activeExportPowerL3W()), 1.0, Unit.ACTIVE_POWER)));

        items.add(entry(register("1.0.23.7.0.255", Math.round(snapshot.reactiveImportPowerL1Var()), 1.0, Unit.REACTIVE_POWER)));
        items.add(entry(register("1.0.43.7.0.255", Math.round(snapshot.reactiveImportPowerL2Var()), 1.0, Unit.REACTIVE_POWER)));
        items.add(entry(register("1.0.63.7.0.255", Math.round(snapshot.reactiveImportPowerL3Var()), 1.0, Unit.REACTIVE_POWER)));

        items.add(entry(register("1.0.24.7.0.255", Math.round(snapshot.reactiveExportPowerL1Var()), 1.0, Unit.REACTIVE_POWER)));
        items.add(entry(register("1.0.44.7.0.255", Math.round(snapshot.reactiveExportPowerL2Var()), 1.0, Unit.REACTIVE_POWER)));
        items.add(entry(register("1.0.64.7.0.255", Math.round(snapshot.reactiveExportPowerL3Var()), 1.0, Unit.REACTIVE_POWER)));

        items.add(entry(register("1.0.29.7.0.255", Math.round(snapshot.apparentImportPowerL1Va()), 1.0, Unit.APPARENT_POWER)));
        items.add(entry(register("1.0.49.7.0.255", Math.round(snapshot.apparentImportPowerL2Va()), 1.0, Unit.APPARENT_POWER)));
        items.add(entry(register("1.0.69.7.0.255", Math.round(snapshot.apparentImportPowerL3Va()), 1.0, Unit.APPARENT_POWER)));

        items.add(entry(register("1.0.30.7.0.255", Math.round(snapshot.apparentExportPowerL1Va()), 1.0, Unit.APPARENT_POWER)));
        items.add(entry(register("1.0.50.7.0.255", Math.round(snapshot.apparentExportPowerL2Va()), 1.0, Unit.APPARENT_POWER)));
        items.add(entry(register("1.0.70.7.0.255", Math.round(snapshot.apparentExportPowerL3Va()), 1.0, Unit.APPARENT_POWER)));

        items.add(entry(register("1.0.14.7.0.255", Math.round(snapshot.frequencyHz() * 100.0), 1e-2, Unit.FREQUENCY)));
        items.add(entry(register("0.0.96.8.0.255", snapshot.workTimeSeconds(), 1.0, Unit.SECOND)));

        return notify.generateDataNotificationMessages(Date.from(snapshot.timestamp().atZone(ZoneId.systemDefault()).toInstant()), items);
    }

    private static Map.Entry<GXDLMSObject, Integer> entry(GXDLMSObject object) {
        return new SimpleEntry<>(object, 2);
    }

    private static GXDLMSData data(String logicalName, Object value) {
        GXDLMSData object = new GXDLMSData(logicalName);
        object.setValue(value);
        return object;
    }

    private static GXDLMSClock clock(String logicalName, LocalDateTime value) {
        GXDLMSClock object = new GXDLMSClock(logicalName);
        object.setTime(new GXDateTime(Date.from(value.atZone(ZoneId.systemDefault()).toInstant())));
        return object;
    }

    private static GXDLMSRegister register(String logicalName, long rawValue, double scaler, Unit unit) {
        GXDLMSRegister object = new GXDLMSRegister(logicalName);
        object.setScaler(scaler);
        object.setUnit(unit);
        object.setValue(rawValue);
        return object;
    }

    private static void printUsage() {
        System.out.println("Usage:");
        System.out.println("  java -jar simulator/target/simulator-0.1.0-SNAPSHOT.jar [--host=127.0.0.1] [--port=4059]");
        System.out.println("       [--interval-sec=10] [--count=0] [--serial=SIM00000001] [--client-address=16]");
        System.out.println("       [--server-address=1] [--reconnect-delay-sec=5]");
    }

    private record Args(
            String host,
            int port,
            int intervalSeconds,
            long count,
            String serial,
            int clientAddress,
            int serverAddress,
            int reconnectDelaySeconds,
            boolean help) {

        private static Args parse(String[] args) {
            String host = "127.0.0.1";
            int port = 4059;
            int intervalSeconds = 10;
            long count = 0;
            String serial = "SIM00000001";
            int clientAddress = 16;
            int serverAddress = 1;
            int reconnectDelaySeconds = 5;
            boolean help = false;

            for (String arg : args) {
                if ("--help".equals(arg) || "-h".equals(arg)) {
                    help = true;
                } else if (arg.startsWith("--host=")) {
                    host = arg.substring("--host=".length());
                } else if (arg.startsWith("--port=")) {
                    port = Integer.parseInt(arg.substring("--port=".length()));
                } else if (arg.startsWith("--interval-sec=")) {
                    intervalSeconds = Integer.parseInt(arg.substring("--interval-sec=".length()));
                } else if (arg.startsWith("--count=")) {
                    count = Long.parseLong(arg.substring("--count=".length()));
                } else if (arg.startsWith("--serial=")) {
                    serial = arg.substring("--serial=".length());
                } else if (arg.startsWith("--client-address=")) {
                    clientAddress = Integer.parseInt(arg.substring("--client-address=".length()));
                } else if (arg.startsWith("--server-address=")) {
                    serverAddress = Integer.parseInt(arg.substring("--server-address=".length()));
                } else if (arg.startsWith("--reconnect-delay-sec=")) {
                    reconnectDelaySeconds = Integer.parseInt(arg.substring("--reconnect-delay-sec=".length()));
                } else {
                    throw new IllegalArgumentException("Unknown argument: " + arg);
                }
            }

            if (intervalSeconds <= 0) {
                throw new IllegalArgumentException("--interval-sec must be > 0");
            }
            if (clientAddress <= 0 || serverAddress <= 0) {
                throw new IllegalArgumentException("DLMS wrapper addresses must be > 0");
            }
            return new Args(host, port, intervalSeconds, count, serial, clientAddress, serverAddress,
                    reconnectDelaySeconds, help);
        }
    }

    private static final class MeterState {
        private final String serial;
        private final int intervalSeconds;
        private final LocalDateTime startedAt = LocalDateTime.now();
        private long tick;
        private long activeImportT1Wh = 1_250_000;
        private long activeImportT2Wh = 820_000;
        private long activeImportT3Wh = 410_000;
        private long activeExportT1Wh = 7_500;
        private long activeExportT2Wh = 5_000;
        private long activeExportT3Wh = 2_000;
        private long reactiveImportT1Varh = 320_000;
        private long reactiveImportT2Varh = 210_000;
        private long reactiveImportT3Varh = 105_000;
        private long reactiveExportT1Varh = 9_000;
        private long reactiveExportT2Varh = 6_000;
        private long reactiveExportT3Varh = 3_000;
        private long workTimeSeconds = 180L * 24 * 3600;

        private MeterState(String serial, int intervalSeconds) {
            this.serial = serial;
            this.intervalSeconds = intervalSeconds;
        }

        private Snapshot next() {
            tick++;
            LocalDateTime now = LocalDateTime.now();
            double wave = Math.sin(tick / 6.0);
            double wave2 = Math.cos(tick / 7.0);

            double voltageL1V = 229.8 + wave * 1.2;
            double voltageL2V = 231.0 + wave2 * 1.0;
            double voltageL3V = 228.9 - wave * 1.1;

            double pfL1 = 0.982;
            double pfL2 = 0.978;
            double pfL3 = 0.985;

            double activeImportPowerL1W = 1350.0 + wave * 90.0;
            double activeImportPowerL2W = 1180.0 + wave2 * 70.0;
            double activeImportPowerL3W = 980.0 - wave * 60.0;

            double activeExportPowerL1W = 12.0;
            double activeExportPowerL2W = 8.0;
            double activeExportPowerL3W = 5.0;

            double reactiveImportPowerL1Var = 210.0 + wave * 15.0;
            double reactiveImportPowerL2Var = 165.0 + wave2 * 12.0;
            double reactiveImportPowerL3Var = 140.0 - wave * 10.0;

            double reactiveExportPowerL1Var = 4.0;
            double reactiveExportPowerL2Var = 3.0;
            double reactiveExportPowerL3Var = 2.0;

            double apparentImportPowerL1Va = Math.hypot(activeImportPowerL1W, reactiveImportPowerL1Var);
            double apparentImportPowerL2Va = Math.hypot(activeImportPowerL2W, reactiveImportPowerL2Var);
            double apparentImportPowerL3Va = Math.hypot(activeImportPowerL3W, reactiveImportPowerL3Var);

            double apparentExportPowerL1Va = Math.hypot(activeExportPowerL1W, reactiveExportPowerL1Var);
            double apparentExportPowerL2Va = Math.hypot(activeExportPowerL2W, reactiveExportPowerL2Var);
            double apparentExportPowerL3Va = Math.hypot(activeExportPowerL3W, reactiveExportPowerL3Var);

            double currentL1A = activeImportPowerL1W / (voltageL1V * pfL1);
            double currentL2A = activeImportPowerL2W / (voltageL2V * pfL2);
            double currentL3A = activeImportPowerL3W / (voltageL3V * pfL3);

            int tariff = tariffFor(now.getHour());
            long activeImportStepWh = Math.round((activeImportPowerL1W + activeImportPowerL2W + activeImportPowerL3W)
                    * intervalSeconds / 3600.0);
            long activeExportStepWh = Math.round((activeExportPowerL1W + activeExportPowerL2W + activeExportPowerL3W)
                    * intervalSeconds / 3600.0);
            long reactiveImportStepVarh = Math.round((reactiveImportPowerL1Var + reactiveImportPowerL2Var + reactiveImportPowerL3Var)
                    * intervalSeconds / 3600.0);
            long reactiveExportStepVarh = Math.round((reactiveExportPowerL1Var + reactiveExportPowerL2Var + reactiveExportPowerL3Var)
                    * intervalSeconds / 3600.0);

            addByTariff(tariff, activeImportStepWh, true, false);
            addByTariff(tariff, activeExportStepWh, false, false);
            addByTariff(tariff, reactiveImportStepVarh, true, true);
            addByTariff(tariff, reactiveExportStepVarh, false, true);
            workTimeSeconds += intervalSeconds;

            return new Snapshot(
                    serial,
                    now,
                    activeImportT1Wh + activeImportT2Wh + activeImportT3Wh,
                    activeImportT1Wh,
                    activeImportT2Wh,
                    activeImportT3Wh,
                    activeExportT1Wh + activeExportT2Wh + activeExportT3Wh,
                    activeExportT1Wh,
                    activeExportT2Wh,
                    activeExportT3Wh,
                    reactiveImportT1Varh + reactiveImportT2Varh + reactiveImportT3Varh,
                    reactiveImportT1Varh,
                    reactiveImportT2Varh,
                    reactiveImportT3Varh,
                    reactiveExportT1Varh + reactiveExportT2Varh + reactiveExportT3Varh,
                    reactiveExportT1Varh,
                    reactiveExportT2Varh,
                    reactiveExportT3Varh,
                    voltageL1V,
                    voltageL2V,
                    voltageL3V,
                    currentL1A,
                    currentL2A,
                    currentL3A,
                    pfL1,
                    pfL2,
                    pfL3,
                    activeImportPowerL1W,
                    activeImportPowerL2W,
                    activeImportPowerL3W,
                    activeExportPowerL1W,
                    activeExportPowerL2W,
                    activeExportPowerL3W,
                    reactiveImportPowerL1Var,
                    reactiveImportPowerL2Var,
                    reactiveImportPowerL3Var,
                    reactiveExportPowerL1Var,
                    reactiveExportPowerL2Var,
                    reactiveExportPowerL3Var,
                    apparentImportPowerL1Va,
                    apparentImportPowerL2Va,
                    apparentImportPowerL3Va,
                    apparentExportPowerL1Va,
                    apparentExportPowerL2Va,
                    apparentExportPowerL3Va,
                    50.02 + wave2 * 0.03,
                    workTimeSeconds);
        }

        private void addByTariff(int tariff, long delta, boolean importDirection, boolean reactive) {
            if (delta <= 0) {
                return;
            }
            if (reactive) {
                if (importDirection) {
                    if (tariff == 1) reactiveImportT1Varh += delta;
                    if (tariff == 2) reactiveImportT2Varh += delta;
                    if (tariff == 3) reactiveImportT3Varh += delta;
                } else {
                    if (tariff == 1) reactiveExportT1Varh += delta;
                    if (tariff == 2) reactiveExportT2Varh += delta;
                    if (tariff == 3) reactiveExportT3Varh += delta;
                }
                return;
            }
            if (importDirection) {
                if (tariff == 1) activeImportT1Wh += delta;
                if (tariff == 2) activeImportT2Wh += delta;
                if (tariff == 3) activeImportT3Wh += delta;
            } else {
                if (tariff == 1) activeExportT1Wh += delta;
                if (tariff == 2) activeExportT2Wh += delta;
                if (tariff == 3) activeExportT3Wh += delta;
            }
        }

        private static int tariffFor(int hour) {
            if (hour >= 7 && hour < 18) {
                return 1;
            }
            if (hour >= 18 && hour < 23) {
                return 2;
            }
            return 3;
        }
    }

    private record Snapshot(
            String serial,
            LocalDateTime timestamp,
            long activeImportTotalWh,
            long activeImportT1Wh,
            long activeImportT2Wh,
            long activeImportT3Wh,
            long activeExportTotalWh,
            long activeExportT1Wh,
            long activeExportT2Wh,
            long activeExportT3Wh,
            long reactiveImportTotalVarh,
            long reactiveImportT1Varh,
            long reactiveImportT2Varh,
            long reactiveImportT3Varh,
            long reactiveExportTotalVarh,
            long reactiveExportT1Varh,
            long reactiveExportT2Varh,
            long reactiveExportT3Varh,
            double voltageL1V,
            double voltageL2V,
            double voltageL3V,
            double currentL1A,
            double currentL2A,
            double currentL3A,
            double pfL1,
            double pfL2,
            double pfL3,
            double activeImportPowerL1W,
            double activeImportPowerL2W,
            double activeImportPowerL3W,
            double activeExportPowerL1W,
            double activeExportPowerL2W,
            double activeExportPowerL3W,
            double reactiveImportPowerL1Var,
            double reactiveImportPowerL2Var,
            double reactiveImportPowerL3Var,
            double reactiveExportPowerL1Var,
            double reactiveExportPowerL2Var,
            double reactiveExportPowerL3Var,
            double apparentImportPowerL1Va,
            double apparentImportPowerL2Va,
            double apparentImportPowerL3Va,
            double apparentExportPowerL1Va,
            double apparentExportPowerL2Va,
            double apparentExportPowerL3Va,
            double frequencyHz,
            long workTimeSeconds) {
    }
}