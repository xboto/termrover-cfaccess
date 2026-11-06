package io.github.xboto.termrovercfaccess;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

final class ThemeStore {
    static final String REMOTE_FILE = "themes.json";

    static final String PREFS = "settings";
    static final String KEY_ENABLED = "themes_enabled";
    static final String FORMAT = "termrover-themes";
    static final int VERSION = 1;
    static final int MAX_THEMES = 300;
    static final int MAX_BYTES = 1 << 20;

    static final String[] CHROME_KEYS = {
            "bgScreen", "bgChrome", "bgPanel", "bgElevated", "bgKey", "bgKeyPressed",
            "border", "hairline", "fgText", "fgMute", "fgFade",
            "green", "amber", "red", "cyan", "prefixInk"};

    private static final Pattern ID = Pattern.compile("[a-z0-9][a-z0-9-]{0,47}");

    static final class Theme {
        String id;
        String name;
        int foreground;
        int background;
        int cursor;
        final int[] ansi = new int[16];
        final int[] chrome = new int[16];
    }

    private ThemeStore() {
    }

    static List<Theme> parse(String json) {
        JSONObject root;
        try {
            root = new JSONObject(json);
        } catch (Exception e) {
            throw new IllegalArgumentException("invalid JSON: " + e.getMessage());
        }
        if (!FORMAT.equals(root.optString("format"))) {
            throw new IllegalArgumentException("\"format\" must be \"" + FORMAT + "\"");
        }
        int version = root.optInt("version", -1);
        if (version != VERSION) {
            throw new IllegalArgumentException("unsupported \"version\": " + version);
        }
        JSONArray arr = root.optJSONArray("themes");
        if (arr == null) {
            throw new IllegalArgumentException("missing \"themes\" array");
        }
        if (arr.length() > MAX_THEMES) {
            throw new IllegalArgumentException("at most " + MAX_THEMES + " themes allowed, found " + arr.length());
        }
        List<Theme> out = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < arr.length(); i++) {
            String where = "themes[" + i + "]";
            try {
                JSONObject o = arr.getJSONObject(i);
                Theme t = new Theme();
                t.id = o.getString("id");
                where = where + " (" + t.id + ")";
                if (!ID.matcher(t.id).matches()) {
                    throw new IllegalArgumentException("invalid id: use a-z, 0-9 and '-', up to 48 characters");
                }
                if (!ids.add(t.id)) {
                    throw new IllegalArgumentException("duplicate id");
                }
                t.name = o.getString("name").trim();
                if (t.name.isEmpty() || t.name.length() > 40) {
                    throw new IllegalArgumentException("name must be 1 to 40 characters");
                }
                JSONObject term = o.getJSONObject("terminal");
                t.foreground = color(term.getString("foreground"));
                t.background = color(term.getString("background"));
                t.cursor = color(term.getString("cursor"));
                JSONArray ansi = term.getJSONArray("ansi");
                if (ansi.length() != 16) {
                    throw new IllegalArgumentException("terminal.ansi must have 16 colors");
                }
                for (int k = 0; k < 16; k++) {
                    t.ansi[k] = color(ansi.getString(k));
                }
                JSONObject chrome = o.getJSONObject("chrome");
                for (int k = 0; k < 16; k++) {
                    if (!chrome.has(CHROME_KEYS[k])) {
                        throw new IllegalArgumentException("missing chrome." + CHROME_KEYS[k]);
                    }
                    t.chrome[k] = color(chrome.getString(CHROME_KEYS[k]));
                }
                out.add(t);
            } catch (Exception e) {
                throw new IllegalArgumentException(where + ": " + e.getMessage());
            }
        }
        return out;
    }

    static int color(String s) {
        String h = s.trim();
        if (!h.startsWith("#") || (h.length() != 7 && h.length() != 9)) {
            throw new IllegalArgumentException("invalid color: " + s);
        }
        long v = Long.parseLong(h.substring(1), 16);
        if (h.length() == 7) {
            v |= 0xFF000000L;
        }
        return (int) v;
    }

    static String hex(int argb) {
        return ((argb >>> 24) == 0xFF)
                ? String.format(Locale.ROOT, "#%06x", argb & 0xFFFFFF)
                : String.format(Locale.ROOT, "#%08x", argb);
    }
}
