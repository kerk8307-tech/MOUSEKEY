package com.mousemapper.app.service;

import com.mousemapper.app.model.Codes;
import com.mousemapper.app.model.Element;
import com.mousemapper.app.model.Profile;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Turns keyboard keys and mouse movement into virtual touches according to a profile.
 * All public methods are synchronized: events arrive from several reader threads.
 */
public class Mapper {
    private static final long JOY_SETTLE_MS = 25;
    private static final long WHEEL_TAP_MS = 50;
    private static final long LOOK_IDLE_RELEASE_MS = 600;

    private final TouchState touch;
    private final TouchInjector injector;
    private final ScheduledExecutorService scheduler;

    private Profile profile = new Profile();
    private int width = 1920;
    private int height = 1080;
    private boolean active;

    private final Map<Element, Integer> tapPointers = new HashMap<>();
    private final Set<Integer> heldKeys = new HashSet<>();

    private Element joystick;
    private int joyPointer = -1;

    private Element look;
    private int lookPointer = -1;
    private float lookX;
    private float lookY;
    private long lastLookMove;

    public Mapper(TouchInjector injector, ScheduledExecutorService scheduler) {
        this.injector = injector;
        this.touch = new TouchState(injector);
        this.scheduler = scheduler;
    }

    public synchronized void setProfile(Profile p, int w, int h) {
        releaseAll();
        profile = p;
        width = Math.max(w, h);
        height = Math.min(w, h);
        touch.setBounds(width, height);
        joystick = p.find(Element.JOYSTICK);
        look = p.find(Element.LOOK);
    }

    public synchronized int toggleCode() {
        return profile.toggleCode;
    }

    /** Active = game mode: keys and mouse are turned into touches. */
    public synchronized void setActive(boolean on) {
        if (!on) releaseAll();
        active = on;
    }

    public synchronized void releaseAll() {
        touch.releaseAll();
        tapPointers.clear();
        heldKeys.clear();
        joyPointer = -1;
        lookPointer = -1;
    }

    private float px(float nx) {
        return nx * width;
    }

    private float py(float ny) {
        return ny * height;
    }

    private float radiusPx(Element e) {
        return e.radius * height;
    }

    /** value: 1 = pressed, 0 = released, 2 = auto-repeat (ignored). */
    public synchronized void onKey(int code, int value) {
        if (!active || value == 2) return;
        boolean pressed = value == 1;
        if (pressed) {
            if (!heldKeys.add(code)) return;
        } else {
            if (!heldKeys.remove(code)) return;
        }
        for (Element e : profile.elements) {
            if (e.type == Element.TAP && e.code == code) {
                if (pressed) pressTap(e);
                else releaseTap(e);
            }
        }
        if (joystick != null && isJoystickKey(code)) updateJoystick();
    }

    private void pressTap(Element e) {
        Integer existing = tapPointers.get(e);
        if (existing != null && touch.isDown(existing)) return;
        int id = touch.down(px(e.x), py(e.y));
        if (id >= 0) tapPointers.put(e, id);
    }

    private void releaseTap(Element e) {
        Integer id = tapPointers.remove(e);
        if (id != null) touch.up(id);
    }

    public synchronized void onWheel(int steps) {
        if (!active || steps == 0) return;
        final int code = steps > 0 ? Codes.WHEEL_UP : Codes.WHEEL_DOWN;
        for (final Element e : profile.elements) {
            if (e.type != Element.TAP || e.code != code) continue;
            if (tapPointers.containsKey(e)) continue;
            pressTap(e);
            scheduler.schedule(() -> {
                synchronized (Mapper.this) {
                    releaseTap(e);
                }
            }, WHEEL_TAP_MS, TimeUnit.MILLISECONDS);
        }
    }

    private boolean isJoystickKey(int code) {
        for (int c : joystick.dirCodes) if (c == code && c != 0) return true;
        return false;
    }

    private void updateJoystick() {
        float dx = 0;
        float dy = 0;
        if (heldKeys.contains(joystick.dirCodes[0])) dy -= 1;
        if (heldKeys.contains(joystick.dirCodes[1])) dx -= 1;
        if (heldKeys.contains(joystick.dirCodes[2])) dy += 1;
        if (heldKeys.contains(joystick.dirCodes[3])) dx += 1;
        if (dx == 0 && dy == 0) {
            if (joyPointer >= 0) touch.up(joyPointer);
            joyPointer = -1;
            return;
        }
        if (joyPointer < 0 || !touch.isDown(joyPointer)) {
            joyPointer = touch.down(px(joystick.x), py(joystick.y));
            if (joyPointer < 0) return;
            // Let the game register the touch at the stick's center before moving it.
            scheduler.schedule(() -> {
                synchronized (Mapper.this) {
                    moveJoystick();
                }
            }, JOY_SETTLE_MS, TimeUnit.MILLISECONDS);
        } else {
            moveJoystick();
        }
    }

    private void moveJoystick() {
        if (joystick == null || joyPointer < 0 || !touch.isDown(joyPointer)) return;
        float dx = 0;
        float dy = 0;
        if (heldKeys.contains(joystick.dirCodes[0])) dy -= 1;
        if (heldKeys.contains(joystick.dirCodes[1])) dx -= 1;
        if (heldKeys.contains(joystick.dirCodes[2])) dy += 1;
        if (heldKeys.contains(joystick.dirCodes[3])) dx += 1;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len == 0) return;
        float r = radiusPx(joystick);
        touch.move(joyPointer, px(joystick.x) + dx / len * r, py(joystick.y) + dy / len * r);
    }

    public synchronized void onMouseMove(int dx, int dy) {
        if (!active || look == null || (dx == 0 && dy == 0)) return;
        float cx = px(look.x);
        float cy = py(look.y);
        float r = radiusPx(look);
        float mx = dx * look.sens;
        float my = dy * look.sens;
        if (lookPointer < 0 || !touch.isDown(lookPointer)) {
            lookPointer = touch.down(cx, cy);
            if (lookPointer < 0) return;
            lookX = cx;
            lookY = cy;
            scheduleLookRelease();
        }
        lookX += mx;
        lookY += my;
        boolean outside = Math.abs(lookX - cx) > r || Math.abs(lookY - cy) > r
                || lookX < 2 || lookY < 2 || lookX > width - 3 || lookY > height - 3;
        if (outside) {
            // Lift the finger and put it back in the middle of the look area.
            touch.up(lookPointer);
            lookPointer = touch.down(cx, cy);
            if (lookPointer < 0) return;
            lookX = cx + mx;
            lookY = cy + my;
        }
        touch.move(lookPointer, lookX, lookY);
        lastLookMove = injector.now();
    }

    private void scheduleLookRelease() {
        scheduler.schedule(new Runnable() {
            @Override
            public void run() {
                synchronized (Mapper.this) {
                    if (lookPointer < 0 || !touch.isDown(lookPointer)) return;
                    long idle = injector.now() - lastLookMove;
                    if (idle >= LOOK_IDLE_RELEASE_MS) {
                        touch.up(lookPointer);
                        lookPointer = -1;
                    } else {
                        scheduler.schedule(this, LOOK_IDLE_RELEASE_MS - idle + 5, TimeUnit.MILLISECONDS);
                    }
                }
            }
        }, LOOK_IDLE_RELEASE_MS, TimeUnit.MILLISECONDS);
    }
}
