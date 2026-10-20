package com.mousemapper.app.model;

/** Linux input codes used by the mapper (values from linux/input-event-codes.h). */
public final class Codes {
    private Codes() {}

    public static final int EV_SYN = 0;
    public static final int EV_KEY = 1;
    public static final int EV_REL = 2;
    public static final int SYN_REPORT = 0;
    public static final int SYN_DROPPED = 3;
    public static final int REL_X = 0;
    public static final int REL_Y = 1;
    public static final int REL_WHEEL = 8;

    public static final int BTN_LEFT = 0x110;
    public static final int BTN_RIGHT = 0x111;
    public static final int BTN_MIDDLE = 0x112;
    public static final int BTN_SIDE = 0x113;
    public static final int BTN_EXTRA = 0x114;
    /** Virtual codes for the scroll wheel (not real Linux codes). */
    public static final int WHEEL_UP = 0x7001;
    public static final int WHEEL_DOWN = 0x7002;

    public static final int KEY_ESC = 1;
    public static final int KEY_1 = 2;
    public static final int KEY_2 = 3;
    public static final int KEY_3 = 4;
    public static final int KEY_4 = 5;
    public static final int KEY_TAB = 15;
    public static final int KEY_Q = 16;
    public static final int KEY_W = 17;
    public static final int KEY_E = 18;
    public static final int KEY_R = 19;
    public static final int KEY_A = 30;
    public static final int KEY_S = 31;
    public static final int KEY_D = 32;
    public static final int KEY_F = 33;
    public static final int KEY_G = 34;
    public static final int KEY_GRAVE = 41;
    public static final int KEY_LEFTSHIFT = 42;
    public static final int KEY_Z = 44;
    public static final int KEY_X = 45;
    public static final int KEY_C = 46;
    public static final int KEY_V = 47;
    public static final int KEY_B = 48;
    public static final int KEY_M = 50;
    public static final int KEY_LEFTCTRL = 29;
    public static final int KEY_SPACE = 57;

    public static boolean isMouseButton(int code) {
        return (code >= BTN_LEFT && code <= BTN_EXTRA) || code == WHEEL_UP || code == WHEEL_DOWN;
    }

    /** Short label for codes that have no Android key name (mouse buttons, wheel). */
    public static String mouseLabel(int code) {
        switch (code) {
            case BTN_LEFT: return "🖱يسار";
            case BTN_RIGHT: return "🖱يمين";
            case BTN_MIDDLE: return "🖱وسط";
            case BTN_SIDE: return "🖱جانبي1";
            case BTN_EXTRA: return "🖱جانبي2";
            case WHEEL_UP: return "عجلة↑";
            case WHEEL_DOWN: return "عجلة↓";
            default: return null;
        }
    }
}
