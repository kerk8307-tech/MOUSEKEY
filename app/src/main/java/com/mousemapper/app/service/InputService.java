package com.mousemapper.app.service;

import android.os.Build;
import android.os.RemoteException;

import com.mousemapper.app.IInputService;
import com.mousemapper.app.IStateCallback;
import com.mousemapper.app.model.Profile;
import com.mousemapper.app.model.ProfileJson;

import org.json.JSONObject;
import org.lsposed.hiddenapibypass.HiddenApiBypass;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Runs in a separate process started by Shizuku with shell (adb) privileges.
 * Reads the physical mouse and keyboard and injects touches.
 */
public class InputService extends IInputService.Stub implements DeviceManager.Sink {
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final AndroidInjector injector = new AndroidInjector();
    private final Mapper mapper = new Mapper(injector, scheduler);
    private final DeviceManager devices = new DeviceManager(this);

    private volatile IStateCallback callback;
    private volatile boolean started;
    private volatile boolean gameMode;
    private volatile boolean paused;

    public InputService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                HiddenApiBypass.addHiddenApiExemptions("L");
            } catch (Throwable ignored) {
                // Not needed on every device.
            }
        }
    }

    @Override
    public void destroy() {
        stop();
        System.exit(0);
    }

    @Override
    public synchronized String start(String configJson, IStateCallback cb) {
        callback = cb;
        try {
            JSONObject o = new JSONObject(configJson);
            String err = NativeInput.load(o.optString("libDir", null));
            if (!err.isEmpty()) return err;
            err = injector.init();
            if (!err.isEmpty()) return err;
            applyConfig(o);
        } catch (Throwable t) {
            return "إعدادات غير صالحة: " + t;
        }
        if (!started) {
            started = true;
            devices.start();
        }
        setGameModeInternal(false);
        return "";
    }

    private void applyConfig(JSONObject o) throws Exception {
        Profile p = ProfileJson.fromJson(o.getJSONObject("profile"));
        mapper.setProfile(p, o.getInt("w"), o.getInt("h"));
        mapper.setActive(gameMode && !paused);
    }

    @Override
    public synchronized void stop() {
        setGameModeInternal(false);
        started = false;
        devices.stop();
        mapper.releaseAll();
    }

    @Override
    public void updateConfig(String configJson) {
        try {
            applyConfig(new JSONObject(configJson));
        } catch (Throwable t) {
            onDeviceMessage("إعدادات غير صالحة: " + t);
        }
    }

    @Override
    public void setGameMode(boolean on) {
        setGameModeInternal(on);
    }

    @Override
    public void setPaused(boolean p) {
        paused = p;
        boolean effective = gameMode && !p;
        mapper.setActive(effective);
        devices.setGrab(effective);
    }

    @Override
    public String listDevices() {
        return devices.describe();
    }

    @Override
    public boolean isGameMode() {
        return gameMode;
    }

    private synchronized void setGameModeInternal(boolean on) {
        gameMode = on;
        boolean effective = on && !paused && started;
        mapper.setActive(effective);
        devices.setGrab(effective);
        IStateCallback cb = callback;
        if (cb != null) {
            try {
                cb.onModeChanged(on);
            } catch (RemoteException ignored) {
                // App went away; nothing to notify.
            }
        }
    }

    // ---- DeviceManager.Sink ----

    @Override
    public void onKey(int code, int value) {
        if (code == mapper.toggleCode()) {
            if (value == 1 && !paused) setGameModeInternal(!gameMode);
            return;
        }
        mapper.onKey(code, value);
    }

    @Override
    public void onMouseMove(int dx, int dy) {
        mapper.onMouseMove(dx, dy);
    }

    @Override
    public void onWheel(int steps) {
        mapper.onWheel(steps);
    }

    @Override
    public void onDeviceMessage(String message) {
        IStateCallback cb = callback;
        if (cb != null) {
            try {
                cb.onMessage(message);
            } catch (RemoteException ignored) {
                // App went away.
            }
        }
    }
}
