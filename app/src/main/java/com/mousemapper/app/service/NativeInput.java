package com.mousemapper.app.service;

import java.io.File;

/** JNI bindings to libinputbridge.so (see src/main/cpp/input_bridge.c). */
public final class NativeInput {
    private NativeInput() {}

    public static final int CAP_MOUSE = 1;
    public static final int CAP_KEYBOARD = 2;
    public static final int CAP_TOUCH = 4;

    private static boolean loaded;

    /** Loads the library; the user service may not have the app's library path, so try it explicitly. */
    public static synchronized String load(String nativeLibraryDir) {
        if (loaded) return "";
        try {
            System.loadLibrary("inputbridge");
            loaded = true;
            return "";
        } catch (Throwable first) {
            if (nativeLibraryDir != null) {
                File f = new File(nativeLibraryDir, "libinputbridge.so");
                try {
                    System.load(f.getAbsolutePath());
                    loaded = true;
                    return "";
                } catch (Throwable second) {
                    return "تعذّر تحميل المكتبة الأصلية: " + second;
                }
            }
            return "تعذّر تحميل المكتبة الأصلية: " + first;
        }
    }

    static native int nOpen(String path);
    static native void nClose(int fd);
    static native int nGrab(int fd, boolean on);
    static native String nName(int fd);
    static native int nCaps(int fd);
    static native int nRead(int fd, int[] out, int timeoutMs);
}
