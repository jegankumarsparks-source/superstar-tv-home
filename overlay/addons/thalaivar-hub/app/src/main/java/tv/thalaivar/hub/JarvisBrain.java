package tv.thalaivar.hub;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * J.A.R.V.I.S's brain, apart from any screen: the phone remote asks it without covering the TV (so "down",
 * "OK" or "pause" reach whatever is on screen), and the TV's own panel asks it too and shows the words. Plain
 * commands run on the TV at once; everything else goes to Gemini Live, which answers in the human voice and
 * controls the TV through tools: every remote button, volume, power, a sleep timer, apps, videos, films and the
 * dashboard's screens. The text models are only the fallback.
 */
final class JarvisBrain {
    /** What the panel shows; every method is called on the main thread. */
    interface Screen {
        void thinking();
        void words(String soFar);
        void done(String all);
    }

    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    /** J.A.R.V.I.S's latest words and when, for the phone remote's chat. */
    static volatile String lastSaid = "";
    static volatile long lastSaidAt;

    static void remember(String words) {
        lastSaid = words == null ? "" : words;
        lastSaidAt = System.currentTimeMillis();
    }

    static void ask(Context context, String text, Screen screen) {
        ask(context, text, null, screen);
    }

    /**
     * alternatives: other ways the phone's speech recognition heard a voice message ("Dharmam vellum" for
     * "Dharmam vekkam"), so Gemini can pick the one that makes sense.
     */
    static void ask(Context context, String text, String alternatives, Screen screen) {
        Context c = context.getApplicationContext();
        if (text == null || text.trim().isEmpty()) return;
        if (!Gemini.hasKey()) {
            remember("Add a Gemini key to secrets.properties to switch me on.");
            if (screen != null) screen.done(lastSaid);
            return;
        }
        boolean aloud = new Prefs(c).speakReplies();
        // plain commands happen at once, on the TV
        QuickCommand quick = QuickCommand.parse(text);
        // "open X" is done locally only for an installed app; "go to Thalaivar cinema" and the like go to Live
        if (quick != null && "open_app".equals(quick.action) && findApp(c, quick.query) == null) quick = null;
        if (quick != null && "play".equals(quick.action)) quick = null;
        if (quick != null) {
            QuickCommand cmd = quick;
            remember(cmd.say);
            if (screen != null) screen.done(cmd.say);
            MAIN.postDelayed(() -> act(c, cmd.action, cmd.query), 150);
            if (aloud) confirm(c, cmd);
            return;
        }
        if (screen != null) screen.thinking();
        String asked = text.trim();
        if (alternatives != null && !alternatives.trim().isEmpty()) {
            asked += "\n(This was a voice message; speech recognition may have misheard Tamil words. It could also have been: "
                    + alternatives.trim() + ". Use the meaning that makes sense.)";
        }
        LiveJarvis.ask(c, asked, LiveJarvis.appNames(c), aloud, new LiveJarvis.Listener() {
            @Override public void words(String soFar) {
                remember(soFar);
                if (screen != null) screen.words(soFar);
            }
            @Override public void done(String all) {
                if (!all.isEmpty()) remember(all);
                if (screen != null) screen.done(all);
            }
            @Override public void failed() {
                askText(c, text.trim(), screen);
            }
        });
    }

    /** A kept phrase plays at once; otherwise Live says the confirmation in the same human voice. */
    private static void confirm(Context c, QuickCommand quick) {
        Ui.WORK.execute(() -> {
            byte[] pcm = Voice.cached(c, quick.voice);
            if (pcm != null) {
                Voice.play(pcm);
                return;
            }
            LiveJarvis.ask(c, "The TV is already doing this, so do not call any tool: " + quick.say
                    + " Confirm it to Thalaiva in five words or fewer.", LiveJarvis.appNames(c), true, new LiveJarvis.Listener() {
                @Override public void words(String soFar) { }
                @Override public void done(String all) { }
                @Override public void failed() { speak(c, quick.voice); }
            });
        });
    }

    // ── the fallback: text models, then a voice ─────────────────────────────────────────────

    private static final List<String[]> HISTORY = new ArrayList<>();

    private static void askText(Context c, String text, Screen screen) {
        List<String[]> turn;
        synchronized (HISTORY) {
            HISTORY.add(new String[]{"user", text});
            while (HISTORY.size() > 8) HISTORY.remove(0);
            turn = new ArrayList<>(HISTORY);
        }
        Ui.WORK.execute(() -> {
            Gemini.Reply r;
            String film = FilmAnswer.answer(c, text);
            if (film != null) {
                r = new Gemini.Reply();
                r.say = film;
            } else {
                try {
                    r = Gemini.ask(c, turn, LiveJarvis.appNames(c));
                } catch (Exception e) {
                    r = new Gemini.Reply();
                    r.say = Net.online(c) ? "Sorry, I couldn't reach Gemini just now. Try again." : "The TV is offline right now.";
                }
            }
            Gemini.Reply reply = r;
            synchronized (HISTORY) {
                if (!reply.say.isEmpty()) HISTORY.add(new String[]{"model", reply.say});
            }
            MAIN.post(() -> {
                remember(reply.say);
                if (screen != null) screen.done(reply.say);
                if (!reply.say.isEmpty() && new Prefs(c).speakReplies()) speak(c, reply.say);
                MAIN.postDelayed(() -> act(c, reply.action, reply.query), "none".equals(reply.action) ? 0 : 1800);
            });
        });
    }

    private static TextToSpeech tts;
    private static boolean ttsReady;

    /** The human voice when it can be had (kept phrase or the voice models), else the TV's best voice at once. */
    static void speak(Context c, String text) {
        Ui.WORK.execute(() -> {
            byte[] pcm = Voice.natural(c, text);
            if (pcm != null) {
                Voice.play(pcm);
                return;
            }
            MAIN.post(() -> {
                if (tts == null) {
                    tts = new TextToSpeech(c, status -> {
                        ttsReady = status == TextToSpeech.SUCCESS;
                        if (ttsReady) say(text);
                    });
                } else if (ttsReady) {
                    say(text);
                }
            });
        });
    }

    private static void say(String text) {
        Locale l = text.codePoints().anyMatch(cp -> cp >= 0x0B80 && cp <= 0x0BFF) ? new Locale("ta", "IN") : new Locale("en", "IN");
        tts.setLanguage(l);
        Voice.pickBestVoice(tts, l);
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "jarvis");
    }

    // ── tools: what Gemini Live can do on the TV ────────────────────────────────────────────

    /** Runs one tool call (on the Live reader thread) and returns what to tell the model. */
    static String tool(Context c, String name, JSONObject a) {
        switch (name) {
            case "play_video":
                return playBest(c, a.optString("query"));
            case "search_videos":
                return TvControl.go(c, "tube", a.optString("query")) ? "Search results are on the screen." : "Couldn't open JK Tube.";
            case "open_app":
                return act(c, "open_app", a.optString("name")) ? "Opened." : a.optString("name") + " isn't installed on this TV.";
            case "open_film":
                return act(c, "film", a.optString("title")) ? "Opening the film's page." : "Couldn't find that film.";
            case "film_facts": {
                String f = FilmAnswer.facts(c, a.optString("title"));
                return f == null ? "That film isn't in the TV's database." : f;
            }
            case "open_screen":
                return TvControl.go(c, screen(a.optString("screen")), null) ? "Opened." : "That screen doesn't exist.";
            case "remote_key": {
                String k = key(a.optString("key"));
                int times = Math.max(1, Math.min(20, a.optInt("times", 1)));
                if (k == null) return "Unknown button.";
                for (int i = 0; i < times; i++) {
                    int n = i;
                    MAIN.postDelayed(() -> TvControl.key(c, k), n * 260L);
                }
                return JarvisKeyService.running == null && isNavigation(k)
                        ? "The THALAIVAR TV helper is off, so navigation buttons can't be pressed. Ask the owner to turn it on in Accessibility."
                        : "Pressed " + a.optString("key") + (times > 1 ? " " + times + " times." : ".");
            }
            case "set_volume": {
                int pct = Math.max(0, Math.min(100, a.optInt("percent", 20)));
                android.media.AudioManager am = (android.media.AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
                int max = am.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC);
                TvControl.setVolume(c, String.valueOf(Math.round(pct * max / 100f)));
                return "Volume set to " + pct + " percent.";
            }
            case "tv_power":
                // "off" only: the screen goes to standby (turning on is the remote's power button)
                return TvControl.key(c, "sleep") ? "The TV is going to standby." : "Standby needs the THALAIVAR TV helper switched on.";
            case "sleep_timer": {
                int min = Math.max(0, Math.min(240, a.optInt("minutes", 0)));
                TvControl.sleepTimer(c, min);
                return min == 0 ? "Sleep timer cancelled." : "The TV will go to standby in " + min + " minutes.";
            }
            case "tv_status":
                return TvControl.describe(c);
            default:
                return "Unknown tool.";
        }
    }

    private static boolean isNavigation(String k) {
        return k.matches("up|down|left|right|ok|back|home|recents|panel");
    }

    /** The model's button names to the remote's. */
    private static String key(String k) {
        switch (k.toLowerCase(Locale.ROOT).replace(' ', '_')) {
            case "up": return "up";
            case "down": return "down";
            case "left": return "left";
            case "right": return "right";
            case "ok": case "select": case "enter": return "ok";
            case "back": return "back";
            case "home": return "home";
            case "recents": case "recent_apps": return "recents";
            case "tv_menu": case "menu": return "panel";
            case "play_pause": return "playpause";
            case "play": return "play";
            case "pause": return "pause";
            case "stop": return "stop";
            case "next": return "next";
            case "previous": return "prev";
            case "rewind": return "rewind";
            case "fast_forward": return "ff";
            case "mute": return "mute";
            case "volume_up": return "volup";
            case "volume_down": return "voldown";
            default: return null;
        }
    }

    private static String screen(String s) {
        switch (s.toLowerCase(Locale.ROOT).replace(' ', '_')) {
            case "home": case "dashboard": return "home";
            case "cinema": case "thalaivar_cinema": return "cinema";
            case "my_movies": case "favourites": return "my";
            case "entertainment": return "ent";
            case "apps": return "apps";
            case "jk_tube": case "youtube": return "tube";
            case "film_search": return "films";
            case "news": return "news";
            case "photos": return "photos";
            case "moments": return "moments";
            case "wallpapers": return "wallpapers";
            case "screensaver": return "saver";
            case "settings": return "hubsettings";
            case "jarvis": return "jarvis";
            case "tv_settings": return "tvsettings";
            default: return s;
        }
    }

    /** A plain command or a fallback answer's action; false when there was nothing to do it with. */
    static boolean act(Context c, String action, String query) {
        String q = query == null ? "" : query.trim();
        switch (action == null ? "none" : action) {
            case "play":
                if (q.isEmpty()) return false;
                playFirst(c, q);
                return true;
            case "tube": return TvControl.go(c, "tube", null);
            case "film":
                if (q.isEmpty()) return false;
                openFilm(c, q);
                return true;
            case "open_app": return !q.isEmpty() && openApp(c, q);
            case "news": return TvControl.go(c, "news", null);
            case "photos": return TvControl.go(c, "photos", null);
            case "screensaver": return TvControl.go(c, "saver", null);
            default: return false;
        }
    }

    private static final java.util.Set<String> FILLER = new java.util.HashSet<>(java.util.Arrays.asList(
            "song", "songs", "video", "videos", "full", "official", "trailer", "teaser", "tamil", "movie", "film", "lyric",
            "lyrical", "the", "and", "podu", "play", "pannu", "from", "with", "audio", "jukebox", "hits", "best", "new"));

    /** The words that identify what was asked for, without "song", "video", "official"… */
    private static List<String> keyWords(String s) {
        List<String> out = new ArrayList<>();
        for (String w : s.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+")) {
            if (w.length() >= 3 && !FILLER.contains(w)) out.add(w);
        }
        return out;
    }

    /** How many of the asked-for words a video's title contains. */
    private static int score(List<String> want, String title) {
        String t = title.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", " ");
        int n = 0;
        for (String w : want) if (t.contains(w)) n++;
        return n;
    }

    /**
     * For Gemini's play_video: searches YouTube and plays only a video whose title holds most of the asked-for
     * words (songs: a queue of the matching ones). No clear match: the results go on screen and Gemini is told so,
     * instead of a random video playing. Runs on the Live reader thread, so the answer says what really plays.
     */
    static String playBest(Context c, String q) {
        if (q == null || q.trim().isEmpty()) return "Nothing to play.";
        if (!YtApi.hasKey()) {
            act(c, "play", q);
            return "Playing the best match in JK Tube.";
        }
        try {
            boolean songs = q.toLowerCase(Locale.ROOT).matches(".*\\b(songs?|paattu|paadal|music|hits|bgm|album)\\b.*");
            // a Tamil fan's TV: songs are searched in Tamil unless another language was named
            String sq = songs && !q.toLowerCase(Locale.ROOT).matches(".*\\b(tamil|telugu|hindi|malayalam|kannada|english)\\b.*") ? q + " tamil" : q;
            List<YtApi.Video> l = new ArrayList<>(songs ? YtApi.searchMusic(c, sq, 10) : YtApi.search(c, q, 8));
            if (songs && l.isEmpty()) l = new ArrayList<>(YtApi.search(c, sq, 8));
            List<String> want = keyWords(q);
            // one or two key words: all of them; more: at least 60 % of them
            int need = want.size() <= 2 ? want.size() : (int) Math.ceil(want.size() * 0.6);
            YtApi.Video best = null;
            int bestScore = -1;
            // YouTube Shorts are clips, never the song or scene that was asked for
            l.removeIf(v -> v.title.toLowerCase(Locale.ROOT).contains("#short"));
            for (YtApi.Video v : l) {
                int s = score(want, v.title);
                if (s > bestScore) {
                    bestScore = s;
                    best = v;
                }
            }
            if (best == null || bestScore < need) {
                MAIN.post(() -> TvControl.go(c, "tube", q));
                return "No video clearly matches \"" + q + "\". The search results are on the TV screen: tell the owner and "
                        + "ask which one, or try a corrected title.";
            }
            java.util.ArrayList<String> queue = new java.util.ArrayList<>();
            queue.add(best.id);
            // a playlist of the other matching songs after it; other videos only as backups if this one can't play
            for (YtApi.Video v : l) if (v != best && score(want, v.title) >= need) queue.add(v.id);
            String title = best.title;
            MAIN.post(() -> TubeActivity.playQueue(c, queue, title));
            return "Now playing: " + title + ".";
        } catch (Exception e) {
            MAIN.post(() -> TvControl.go(c, "tube", q));
            return "YouTube search failed; the JK Tube search is on screen.";
        }
    }

    /** With a YouTube key the top result plays straight away; songs as a queue of 10 from the Music category. */
    private static void playFirst(Context c, String q) {
        Ui.WORK.execute(() -> {
            try {
                if (!YtApi.hasKey()) {
                    YtApi.Video v = YtApi.filmTrailer(c, q);
                    if (v != null) {
                        MAIN.post(() -> TubeActivity.play(c, v.id, v.title));
                        return;
                    }
                } else {
                    boolean songs = q.toLowerCase(Locale.ROOT).matches(".*\\b(songs?|paattu|paadal|music|hits|bgm|album)\\b.*");
                    List<YtApi.Video> l = songs ? YtApi.searchMusic(c, q, 10) : YtApi.search(c, q, 5);
                    if (songs && l.isEmpty()) l = YtApi.search(c, q, 5);
                    if (!l.isEmpty()) {
                        java.util.ArrayList<String> queue = TubeActivity.ids(l);
                        String title = l.get(0).title;
                        MAIN.post(() -> TubeActivity.playQueue(c, queue, title));
                        return;
                    }
                }
            } catch (Exception ignored) {
                // fall through to JK Tube's search
            }
            MAIN.post(() -> TvControl.go(c, "tube", q));
        });
    }

    private static void openFilm(Context c, String title) {
        Ui.WORK.execute(() -> {
            try {
                String want = title.toLowerCase(Locale.ROOT);
                for (Films.Film f : Films.resolve(c, Films.curated())) {
                    if (f.id > 0 && (f.title.toLowerCase(Locale.ROOT).contains(want) || want.contains(f.title.toLowerCase(Locale.ROOT)))) {
                        MAIN.post(() -> c.startActivity(new Intent(c, MovieActivity.class).putExtra("id", f.id).putExtra("tamil", f.tamil)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)));
                        return;
                    }
                }
                for (Films.Film f : Films.all(c)) {
                    if (f.title.toLowerCase(Locale.ROOT).contains(want)) {
                        MAIN.post(() -> c.startActivity(new Intent(c, MovieActivity.class).putExtra("id", f.id)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)));
                        return;
                    }
                }
            } catch (Exception ignored) {
                // nothing found: the film search with the name typed in
            }
            MAIN.post(() -> TvControl.go(c, "films", title));
        });
    }

    private static boolean openApp(Context c, String name) {
        String pkg = findApp(c, name);
        if (pkg == null) return false;
        MAIN.post(() -> TvControl.app(c, pkg));
        return true;
    }

    /** The installed app meant by a name: exact first, then containing it, then named inside a longer phrase. */
    private static String findApp(Context c, String name) {
        if (name == null || name.trim().isEmpty()) return null;
        String want = name.toLowerCase(Locale.ROOT).trim();
        PackageManager pm = c.getPackageManager();
        List<ResolveInfo> apps = pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), 0);
        for (int pass = 0; pass < 3; pass++) {
            for (ResolveInfo r : apps) {
                String label = r.loadLabel(pm).toString().toLowerCase(Locale.ROOT).trim();
                boolean hit = pass == 0 ? label.equals(want) : pass == 1 ? label.contains(want) : label.length() >= 3 && want.contains(label);
                if (hit) return r.activityInfo.packageName;
            }
        }
        return null;
    }

    private JarvisBrain() {
    }
}
