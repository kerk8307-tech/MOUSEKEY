package com.mousemapper.app;

import com.mousemapper.app.IStateCallback;

interface IInputService {
    // Reserved by Shizuku: called when the user service should be destroyed.
    void destroy() = 16777114;

    // Starts reading the mouse/keyboard and mapping. Returns "" on success or an error message.
    String start(String configJson, IStateCallback callback) = 1;
    void stop() = 2;
    void updateConfig(String configJson) = 3;
    void setGameMode(boolean on) = 4;
    // While paused (layout editor open) nothing is grabbed or injected.
    void setPaused(boolean paused) = 5;
    String listDevices() = 6;
    boolean isGameMode() = 7;
}
