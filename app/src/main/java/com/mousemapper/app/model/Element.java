package com.mousemapper.app.model;

/**
 * One on-screen control. Positions are normalized (0..1) to the landscape screen;
 * radius is a fraction of the screen's short side.
 */
public class Element {
    public static final int TAP = 0;
    public static final int JOYSTICK = 1;
    public static final int LOOK = 2;

    public int type;
    public float x;
    public float y;
    public float radius;
    /** Sensitivity for LOOK (screen pixels per mouse count). */
    public float sens = 1f;

    /** TAP: the key or mouse button. */
    public int code;
    public String label = "";

    /** JOYSTICK: up, left, down, right. */
    public int[] dirCodes = new int[4];
    public String[] dirLabels = new String[] {"", "", "", ""};

    public static Element tap(float x, float y, int code, String label) {
        Element e = new Element();
        e.type = TAP;
        e.x = x;
        e.y = y;
        e.code = code;
        e.label = label;
        return e;
    }

    public static Element joystick(float x, float y, float radius) {
        Element e = new Element();
        e.type = JOYSTICK;
        e.x = x;
        e.y = y;
        e.radius = radius;
        e.dirCodes = new int[] {Codes.KEY_W, Codes.KEY_A, Codes.KEY_S, Codes.KEY_D};
        e.dirLabels = new String[] {"W", "A", "S", "D"};
        return e;
    }

    public static Element look(float x, float y, float radius, float sens) {
        Element e = new Element();
        e.type = LOOK;
        e.x = x;
        e.y = y;
        e.radius = radius;
        e.sens = sens;
        return e;
    }
}
