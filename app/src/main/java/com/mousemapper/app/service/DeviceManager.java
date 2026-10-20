package com.mousemapper.app.service;

import com.mousemapper.app.model.Codes;

import java.io.File;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Finds mice and keyboards under /dev/input, reads them, and grabs them in game mode. */
public class DeviceManager {
    public interface Sink {
        void onKey(int code, int value);
        void onMouseMove(int dx, int dy);
        void onWheel(int steps);
        void onDeviceMessage(String message);
    }

    private static final String INPUT_DIR = "/dev/input";
    private static final long SCAN_INTERVAL_MS = 2000;

    private final Sink sink;
    private final Map<String, Device> devices = new ConcurrentHashMap<>();
    private final Set<String> ignored = new HashSet<>();
    private volatile boolean running;
    private volatile boolean grab;
    private Thread scanThread;
    private boolean reportedGrabError;
    private boolean reportedOpenError;

    private static final class Device {
        final String path;
        final int fd;
        final String name;
        final int caps;
        volatile boolean alive = true;

        Device(String path, int fd, String name, int caps) {
            this.path = path;
            this.fd = fd;
            this.name = name;
            this.caps = caps;
        }
    }

    public DeviceManager(Sink sink) {
        this.sink = sink;
    }

    public synchronized void start() {
        if (running) return;
        running = true;
        scanThread = new Thread(() -> {
            while (running) {
                try {
                    scan();
                } catch (Throwable t) {
                    sink.onDeviceMessage("خطأ أثناء البحث عن الأجهزة: " + t);
                }
                try {
                    Thread.sleep(SCAN_INTERVAL_MS);
                } catch (InterruptedException e) {
                    return;
                }
            }
        }, "input-scan");
        scanThread.setDaemon(true);
        scanThread.start();
    }

    public synchronized void stop() {
        running = false;
        if (scanThread != null) scanThread.interrupt();
        for (Device d : devices.values()) {
            NativeInput.nGrab(d.fd, false);
            d.alive = false;
        }
    }

    public void setGrab(boolean on) {
        grab = on;
        for (Device d : devices.values()) applyGrab(d, on);
    }

    private void applyGrab(Device d, boolean on) {
        int r = NativeInput.nGrab(d.fd, on);
        if (r < 0 && on && !reportedGrabError) {
            reportedGrabError = true;
            sink.onDeviceMessage("لم أستطع حجز \"" + d.name + "\" (خطأ " + (-r)
                    + "). سيعمل التحويل، لكن مؤشر الماوس قد يضغط على الشاشة أيضاً.");
        }
    }

    private synchronized void scan() {
        File dir = new File(INPUT_DIR);
        String[] names = dir.list();
        if (names == null) {
            if (!reportedOpenError) {
                reportedOpenError = true;
                sink.onDeviceMessage("لا أستطيع قراءة /dev/input. تأكد أن Shizuku يعمل.");
            }
            return;
        }
        Set<String> present = new HashSet<>();
        for (String n : names) {
            if (!n.startsWith("event")) continue;
            String path = INPUT_DIR + "/" + n;
            present.add(path);
            if (devices.containsKey(path) || ignored.contains(path)) continue;
            int fd = NativeInput.nOpen(path);
            if (fd < 0) {
                ignored.add(path);
                if (!reportedOpenError) {
                    reportedOpenError = true;
                    sink.onDeviceMessage("تعذّر فتح " + path + " (خطأ " + (-fd) + ").");
                }
                continue;
            }
            int caps = NativeInput.nCaps(fd);
            boolean useful = (caps & NativeInput.CAP_TOUCH) == 0
                    && (caps & (NativeInput.CAP_MOUSE | NativeInput.CAP_KEYBOARD)) != 0;
            if (!useful) {
                NativeInput.nClose(fd);
                ignored.add(path);
                continue;
            }
            Device d = new Device(path, fd, NativeInput.nName(fd), caps);
            devices.put(path, d);
            if (grab) applyGrab(d, true);
            Thread t = new Thread(() -> readLoop(d), "input-" + n);
            t.setDaemon(true);
            t.start();
            sink.onDeviceMessage("تم توصيل: " + d.name);
        }
        ignored.retainAll(present);
    }

    private void readLoop(Device d) {
        int[] buf = new int[64 * 3];
        int dx = 0;
        int dy = 0;
        try {
            while (running && d.alive) {
                int n = NativeInput.nRead(d.fd, buf, 200);
                if (n < 0) break;
                for (int i = 0; i < n; i++) {
                    int type = buf[i * 3];
                    int code = buf[i * 3 + 1];
                    int value = buf[i * 3 + 2];
                    if (type == Codes.EV_REL) {
                        if (code == Codes.REL_X) dx += value;
                        else if (code == Codes.REL_Y) dy += value;
                        else if (code == Codes.REL_WHEEL) sink.onWheel(value);
                    } else if (type == Codes.EV_KEY) {
                        sink.onKey(code, value);
                    } else if (type == Codes.EV_SYN && code == Codes.SYN_REPORT) {
                        if (dx != 0 || dy != 0) sink.onMouseMove(dx, dy);
                        dx = 0;
                        dy = 0;
                    }
                }
            }
        } catch (Throwable t) {
            sink.onDeviceMessage("خطأ في قراءة " + d.name + ": " + t);
        } finally {
            devices.remove(d.path);
            NativeInput.nGrab(d.fd, false);
            NativeInput.nClose(d.fd);
            if (running) sink.onDeviceMessage("تم فصل: " + d.name);
        }
    }

    public String describe() {
        StringBuilder sb = new StringBuilder();
        for (Device d : devices.values()) {
            sb.append(d.name).append(" — ");
            if ((d.caps & NativeInput.CAP_MOUSE) != 0) sb.append("ماوس ");
            if ((d.caps & NativeInput.CAP_KEYBOARD) != 0) sb.append("كيبورد");
            sb.append('\n');
        }
        return sb.length() == 0 ? "لا يوجد ماوس أو كيبورد متصل" : sb.toString().trim();
    }
}
