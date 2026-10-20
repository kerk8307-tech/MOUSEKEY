package com.mousemapper.app;

oneway interface IStateCallback {
    void onModeChanged(boolean gameMode);
    void onMessage(String message);
}
