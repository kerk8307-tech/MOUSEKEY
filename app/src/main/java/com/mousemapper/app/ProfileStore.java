package com.mousemapper.app;

import android.content.Context;
import android.content.SharedPreferences;

import com.mousemapper.app.model.Profile;
import com.mousemapper.app.model.ProfileJson;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Saves each profile as a JSON file in the app's private storage. */
public final class ProfileStore {
    private static final String PREFS = "settings";
    private static final String KEY_ACTIVE = "active_profile";
    public static final String DEFAULT_NAME = "ألعاب التصويب";

    private ProfileStore() {}

    private static File dir(Context c) {
        File d = new File(c.getFilesDir(), "profiles");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    private static File file(Context c, String name) {
        String safe = name.replaceAll("[\\\\/:*?\"<>|]", "_");
        return new File(dir(c), safe + ".json");
    }

    public static List<String> names(Context c) {
        ensureDefault(c);
        List<String> out = new ArrayList<>();
        File[] files = dir(c).listFiles();
        if (files != null) {
            for (File f : files) {
                String n = f.getName();
                if (n.endsWith(".json")) {
                    Profile p = readFile(f);
                    out.add(p != null && !p.name.isEmpty() ? p.name : n.substring(0, n.length() - 5));
                }
            }
        }
        Collections.sort(out);
        return out;
    }

    private static void ensureDefault(Context c) {
        File[] files = dir(c).listFiles();
        if (files == null || files.length == 0) save(c, Profile.defaultShooter(DEFAULT_NAME));
    }

    public static String activeName(Context c) {
        SharedPreferences sp = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String n = sp.getString(KEY_ACTIVE, DEFAULT_NAME);
        if (!file(c, n).exists()) {
            List<String> all = names(c);
            n = all.isEmpty() ? DEFAULT_NAME : all.get(0);
        }
        return n;
    }

    public static void setActive(Context c, String name) {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_ACTIVE, name).apply();
    }

    public static Profile active(Context c) {
        ensureDefault(c);
        Profile p = load(c, activeName(c));
        return p != null ? p : Profile.defaultShooter(DEFAULT_NAME);
    }

    public static Profile load(Context c, String name) {
        return readFile(file(c, name));
    }

    private static Profile readFile(File f) {
        if (!f.exists()) return null;
        try (FileInputStream in = new FileInputStream(f)) {
            byte[] data = new byte[(int) f.length()];
            int off = 0;
            while (off < data.length) {
                int r = in.read(data, off, data.length - off);
                if (r < 0) break;
                off += r;
            }
            return ProfileJson.fromJson(new JSONObject(new String(data, 0, off, StandardCharsets.UTF_8)));
        } catch (Exception e) {
            return null;
        }
    }

    public static boolean save(Context c, Profile p) {
        try (FileOutputStream out = new FileOutputStream(file(c, p.name))) {
            out.write(ProfileJson.toJson(p).toString(2).getBytes(StandardCharsets.UTF_8));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean exists(Context c, String name) {
        return file(c, name).exists();
    }

    public static void delete(Context c, String name) {
        //noinspection ResultOfMethodCallIgnored
        file(c, name).delete();
    }
}
