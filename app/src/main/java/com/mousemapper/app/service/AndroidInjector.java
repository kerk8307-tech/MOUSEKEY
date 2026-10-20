package com.mousemapper.app.service;

import android.os.SystemClock;
import android.view.InputDevice;
import android.view.InputEvent;
import android.view.MotionEvent;

import java.lang.reflect.Method;

/** Injects touch events through the hidden InputManager API (allowed for the shell uid). */
public class AndroidInjector implements TouchInjector {
    private static final int INJECT_MODE_ASYNC = 0;

    private Object inputManager;
    private Method injectMethod;
    private final MotionEvent.PointerProperties[] props = new MotionEvent.PointerProperties[TouchState.MAX];
    private final MotionEvent.PointerCoords[] coords = new MotionEvent.PointerCoords[TouchState.MAX];

    public AndroidInjector() {
        for (int i = 0; i < TouchState.MAX; i++) {
            props[i] = new MotionEvent.PointerProperties();
            coords[i] = new MotionEvent.PointerCoords();
        }
    }

    /** Returns "" on success or an error description. */
    public String init() {
        try {
            try {
                Class<?> global = Class.forName("android.hardware.input.InputManagerGlobal");
                inputManager = global.getMethod("getInstance").invoke(null);
            } catch (ClassNotFoundException e) {
                Class<?> legacy = Class.forName("android.hardware.input.InputManager");
                inputManager = legacy.getMethod("getInstance").invoke(null);
            }
            injectMethod = inputManager.getClass().getMethod("injectInputEvent", InputEvent.class, int.class);
            return "";
        } catch (Throwable t) {
            return "تعذّر الوصول إلى نظام اللمس: " + t;
        }
    }

    @Override
    public long now() {
        return SystemClock.uptimeMillis();
    }

    @Override
    public void inject(int action, int[] ids, float[] xs, float[] ys, int count, long downTime) {
        if (injectMethod == null || count <= 0) return;
        MotionEvent.PointerProperties[] p = new MotionEvent.PointerProperties[count];
        MotionEvent.PointerCoords[] c = new MotionEvent.PointerCoords[count];
        for (int i = 0; i < count; i++) {
            props[i].clear();
            props[i].id = ids[i];
            props[i].toolType = MotionEvent.TOOL_TYPE_FINGER;
            coords[i].clear();
            coords[i].x = xs[i];
            coords[i].y = ys[i];
            coords[i].pressure = 1f;
            coords[i].size = 0.05f;
            coords[i].touchMajor = 10f;
            coords[i].touchMinor = 10f;
            p[i] = props[i];
            c[i] = coords[i];
        }
        MotionEvent ev = MotionEvent.obtain(downTime, now(), action, count, p, c,
                0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0);
        try {
            injectMethod.invoke(inputManager, ev, INJECT_MODE_ASYNC);
        } catch (Throwable ignored) {
            // A single dropped event is harmless; the next one carries the full state.
        } finally {
            ev.recycle();
        }
    }
}
