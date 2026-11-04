package tv.thalaivar.hub;

import android.app.ActivityManager;
import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Everything the owner chooses: language, animation level, favourites, pinned apps, rotation. */
final class Prefs {
    static final int LOW = 0, MEDIUM = 1, HIGH = 2;
    private static final String MOTION_AUTO = "auto";

    private final SharedPreferences p;
    private final Context c;

    Prefs(Context c) {
        this.c = c.getApplicationContext();
        this.p = c.getSharedPreferences("thalaivar", Context.MODE_PRIVATE);
        Lang.tamil = "ta".equals(p.getString("lang", "en"));
    }

    void setLanguage(String lang) {
        p.edit().putString("lang", lang).apply();
        Lang.tamil = "ta".equals(lang);
    }

    String motionChoice() {
        return p.getString("motion", MOTION_AUTO);
    }

    void setMotion(String v) {
        p.edit().putString("motion", v).apply();
    }

    /** LOW, MEDIUM or HIGH: the owner's choice, or what this TV's memory comfortably runs. */
    int motion() {
        switch (motionChoice()) {
            case "low": return LOW;
            case "medium": return MEDIUM;
            case "high": return HIGH;
            default: return autoMotion(c);
        }
    }

    static int autoMotion(Context c) {
        ActivityManager am = (ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE);
        if (am == null || am.isLowRamDevice()) return LOW;
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(mi);
        long gb = mi.totalMem / (1024L * 1024L * 1024L);
        // 1.5 GB Google TV sticks (MOKA R3G) run the 3D tilt and particles smoothly; below 1.2 GB they stay calm
        if (mi.totalMem < 1200L * 1024 * 1024) return LOW;
        return gb >= 3 ? HIGH : MEDIUM;
    }

    int rotateMinutes() {
        return p.getInt("rotate", 1);
    }

    void setRotateMinutes(int m) {
        p.edit().putInt("rotate", m).apply();
    }

    Set<String> set(String key) {
        return new HashSet<>(p.getStringSet(key, new HashSet<>()));
    }

    boolean toggle(String key, String value) {
        Set<String> s = set(key);
        boolean on = !s.remove(value);
        if (on) s.add(value);
        p.edit().putStringSet(key, s).apply();
        return on;
    }

    boolean isFavMovie(long id) {
        return set("fav_movies").contains(String.valueOf(id));
    }

    boolean toggleFavMovie(long id) {
        return toggle("fav_movies", String.valueOf(id));
    }

    /** Wallpapers (TMDB image paths) the owner starred for the screensaver. */
    Set<String> favWalls() {
        return set("fav_walls");
    }

    boolean toggleFavWall(String path) {
        return toggle("fav_walls", path);
    }

    /** Apps pinned to the hub's own row, in the order they were pinned. */
    List<String> pinned() {
        String s = p.getString("pinned", "");
        return s.isEmpty() ? new ArrayList<>() : new ArrayList<>(Arrays.asList(s.split(",")));
    }

    boolean togglePin(String pkg) {
        List<String> l = pinned();
        boolean on = !l.remove(pkg);
        if (on) l.add(pkg);
        p.edit().putString("pinned", String.join(",", l)).apply();
        return on;
    }

    /** SmartTube's package names (stable, beta, and the older liskovsoft ids). */
    static final String[] SMARTTUBE = {"org.smarttube.stable", "org.smarttube.beta",
            "com.liskovsoft.smarttubetv", "com.liskovsoft.smarttubetv.beta"};

    /**
     * Who plays every video: "auto" (SmartTube when it is installed, else JK Tube's player), "jktube",
     * "ask", or an app's package.
     */
    String player() {
        return p.getString("player", "auto");
    }

    /** "auto" resolved to what is installed now. */
    String resolvedPlayer() {
        String pl = player();
        if (!"auto".equals(pl)) return pl;
        for (String pkg : SMARTTUBE) {
            try {
                c.getPackageManager().getApplicationInfo(pkg, 0);
                return pkg;
            } catch (Exception ignored) {
                // not this one
            }
        }
        return "jktube";
    }

    void setPlayer(String v) {
        p.edit().putString("player", v).apply();
    }

    /** What the screensavers show: "all", "portraits", "stills" or "starred". */
    String saverSource() {
        return p.getString("saver_source", "all");
    }

    void setSaverSource(String v) {
        p.edit().putString("saver_source", v).apply();
    }

    boolean saverClock() {
        return p.getBoolean("saver_clock", true);
    }

    void setSaverClock(boolean on) {
        p.edit().putBoolean("saver_clock", on).apply();
    }

    /** The remote's search / voice button opens J.A.R.V.I.S (needs the key service on in TV Settings). */
    boolean jarvisButton() {
        return p.getBoolean("jarvis_button", true);
    }

    void setJarvisButton(boolean on) {
        p.edit().putBoolean("jarvis_button", on).apply();
    }

    /** Speech recognition language: "en-IN", "ta-IN", or "both" (English first, Tamil understood too). */
    String micLanguage() {
        return p.getString("mic_lang", "both");
    }

    void setMicLanguage(String v) {
        p.edit().putString("mic_lang", v).apply();
    }

    boolean speakReplies() {
        return p.getBoolean("speak", true);
    }

    void setSpeakReplies(boolean on) {
        p.edit().putBoolean("speak", on).apply();
    }

    String lastWall() {
        return p.getString("last_wall", "");
    }

    void setLastWall(String w) {
        p.edit().putString("last_wall", w).apply();
    }
}
