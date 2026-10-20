package com.mousemapper.app.model;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** JSON (de)serialization shared by the app and the shell-side service. */
public final class ProfileJson {
    private ProfileJson() {}

    public static JSONObject toJson(Profile p) throws JSONException {
        JSONObject o = new JSONObject();
        o.put("name", p.name);
        o.put("toggle", p.toggleCode);
        o.put("toggleLabel", p.toggleLabel);
        JSONArray arr = new JSONArray();
        for (Element e : p.elements) {
            JSONObject j = new JSONObject();
            j.put("type", e.type);
            j.put("x", (double) e.x);
            j.put("y", (double) e.y);
            j.put("r", (double) e.radius);
            j.put("sens", (double) e.sens);
            j.put("code", e.code);
            j.put("label", e.label);
            JSONArray dc = new JSONArray();
            JSONArray dl = new JSONArray();
            for (int i = 0; i < 4; i++) {
                dc.put(e.dirCodes[i]);
                dl.put(e.dirLabels[i]);
            }
            j.put("dirCodes", dc);
            j.put("dirLabels", dl);
            arr.put(j);
        }
        o.put("elements", arr);
        return o;
    }

    public static Profile fromJson(JSONObject o) throws JSONException {
        Profile p = new Profile();
        p.name = o.optString("name", "");
        p.toggleCode = o.optInt("toggle", Codes.KEY_GRAVE);
        p.toggleLabel = o.optString("toggleLabel", "`");
        JSONArray arr = o.optJSONArray("elements");
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                JSONObject j = arr.getJSONObject(i);
                Element e = new Element();
                e.type = j.optInt("type", Element.TAP);
                e.x = (float) j.optDouble("x", 0.5);
                e.y = (float) j.optDouble("y", 0.5);
                e.radius = (float) j.optDouble("r", 0.1);
                e.sens = (float) j.optDouble("sens", 1.0);
                e.code = j.optInt("code", 0);
                e.label = j.optString("label", "");
                JSONArray dc = j.optJSONArray("dirCodes");
                JSONArray dl = j.optJSONArray("dirLabels");
                for (int k = 0; k < 4; k++) {
                    if (dc != null && k < dc.length()) e.dirCodes[k] = dc.optInt(k);
                    if (dl != null && k < dl.length()) e.dirLabels[k] = dl.optString(k, "");
                }
                p.elements.add(e);
            }
        }
        return p;
    }

    /** Message sent to the service: the profile plus the landscape screen size in pixels. */
    public static String serviceConfig(Profile p, int width, int height, String libDir) throws JSONException {
        JSONObject o = new JSONObject();
        o.put("w", width);
        o.put("h", height);
        o.put("libDir", libDir);
        o.put("profile", toJson(p));
        return o.toString();
    }
}
