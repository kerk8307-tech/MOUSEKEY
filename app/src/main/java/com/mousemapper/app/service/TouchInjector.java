package com.mousemapper.app.service;

/** Delivers a full multi-touch snapshot to the system. */
public interface TouchInjector {
    /**
     * @param action    MotionEvent action including the pointer index bits
     * @param ids       pointer ids, valid for [0, count)
     * @param xs        x coordinates in display pixels
     * @param ys        y coordinates in display pixels
     * @param downTime  uptime (ms) when the first pointer of this gesture went down
     */
    void inject(int action, int[] ids, float[] xs, float[] ys, int count, long downTime);

    /** Current uptime in milliseconds (SystemClock.uptimeMillis on Android). */
    long now();
}
