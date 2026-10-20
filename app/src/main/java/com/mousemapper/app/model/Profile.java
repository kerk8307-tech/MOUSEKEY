package com.mousemapper.app.model;

import java.util.ArrayList;
import java.util.List;

/** A layout of controls for one game. */
public class Profile {
    public String name = "";
    /** Key or mouse button that switches between game mode and normal (cursor) mode. */
    public int toggleCode = Codes.KEY_GRAVE;
    public String toggleLabel = "`";
    public final List<Element> elements = new ArrayList<>();

    public Element find(int type) {
        for (Element e : elements) if (e.type == type) return e;
        return null;
    }

    /** A starting layout for typical mobile shooters; the user drags everything into place. */
    public static Profile defaultShooter(String name) {
        Profile p = new Profile();
        p.name = name;
        p.elements.add(Element.look(0.68f, 0.42f, 0.22f, 1.0f));
        p.elements.add(Element.joystick(0.16f, 0.70f, 0.11f));
        p.elements.add(Element.tap(0.88f, 0.58f, Codes.BTN_LEFT, Codes.mouseLabel(Codes.BTN_LEFT)));
        p.elements.add(Element.tap(0.95f, 0.40f, Codes.BTN_RIGHT, Codes.mouseLabel(Codes.BTN_RIGHT)));
        p.elements.add(Element.tap(0.94f, 0.76f, Codes.KEY_SPACE, "Space"));
        p.elements.add(Element.tap(0.87f, 0.90f, Codes.KEY_C, "C"));
        p.elements.add(Element.tap(0.78f, 0.90f, Codes.KEY_R, "R"));
        p.elements.add(Element.tap(0.60f, 0.60f, Codes.KEY_F, "F"));
        p.elements.add(Element.tap(0.06f, 0.92f, Codes.KEY_TAB, "Tab"));
        p.elements.add(Element.tap(0.95f, 0.07f, Codes.KEY_M, "M"));
        return p;
    }
}
