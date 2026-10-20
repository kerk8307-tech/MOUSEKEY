package com.mousemapper.app.service;

/** Tracks the virtual fingers on the screen and emits consistent multi-touch events. */
public class TouchState {
    static final int ACTION_DOWN = 0;
    static final int ACTION_UP = 1;
    static final int ACTION_MOVE = 2;
    static final int ACTION_POINTER_DOWN = 5;
    static final int ACTION_POINTER_UP = 6;
    static final int POINTER_INDEX_SHIFT = 8;
    static final int MAX = 10;

    private final TouchInjector injector;
    private final int[] ids = new int[MAX];
    private final float[] xs = new float[MAX];
    private final float[] ys = new float[MAX];
    private final boolean[] used = new boolean[MAX];
    private int count;
    private long downTime;
    private float width = 1920;
    private float height = 1080;

    public TouchState(TouchInjector injector) {
        this.injector = injector;
    }

    public void setBounds(int w, int h) {
        width = w;
        height = h;
    }

    public int count() {
        return count;
    }

    private float cx(float x) {
        return Math.max(1f, Math.min(width - 2f, x));
    }

    private float cy(float y) {
        return Math.max(1f, Math.min(height - 2f, y));
    }

    private int indexOf(int id) {
        for (int i = 0; i < count; i++) if (ids[i] == id) return i;
        return -1;
    }

    /** Puts a finger down. Returns its id, or -1 if all fingers are in use. */
    public int down(float x, float y) {
        int id = -1;
        for (int i = 0; i < MAX; i++) {
            if (!used[i]) {
                id = i;
                break;
            }
        }
        if (id < 0) return -1;
        used[id] = true;
        int index = count;
        ids[index] = id;
        xs[index] = cx(x);
        ys[index] = cy(y);
        count++;
        int action;
        if (count == 1) {
            downTime = injector.now();
            action = ACTION_DOWN;
        } else {
            action = ACTION_POINTER_DOWN | (index << POINTER_INDEX_SHIFT);
        }
        injector.inject(action, ids, xs, ys, count, downTime);
        return id;
    }

    public void move(int id, float x, float y) {
        int index = indexOf(id);
        if (index < 0) return;
        xs[index] = cx(x);
        ys[index] = cy(y);
        injector.inject(ACTION_MOVE, ids, xs, ys, count, downTime);
    }

    public void up(int id) {
        int index = indexOf(id);
        if (index < 0) return;
        int action = count == 1 ? ACTION_UP : ACTION_POINTER_UP | (index << POINTER_INDEX_SHIFT);
        injector.inject(action, ids, xs, ys, count, downTime);
        for (int i = index; i < count - 1; i++) {
            ids[i] = ids[i + 1];
            xs[i] = xs[i + 1];
            ys[i] = ys[i + 1];
        }
        count--;
        used[id] = false;
    }

    public boolean isDown(int id) {
        return id >= 0 && indexOf(id) >= 0;
    }

    public void releaseAll() {
        while (count > 0) up(ids[count - 1]);
    }
}
