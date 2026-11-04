package tv.thalaivar.hub;

import android.content.Context;

import org.json.JSONObject;

import java.util.List;
import java.util.Locale;

/**
 * Questions about his films answered on the TV from TMDB (cached), with no AI wait: who directed it, who
 * did the music, when it released, who is in it. Anything else returns null and goes to Gemini.
 */
final class FilmAnswer {
    private enum Ask { DIRECTOR, MUSIC, RELEASE, CAST }

    /** Blocking (reads the cached film data); call off the main thread. */
    static String answer(Context c, String spoken) {
        if (!Tmdb.hasToken() || spoken == null) return null;
        String s = spoken.toLowerCase(Locale.ROOT);
        Ask ask = s.matches(".*\\b(direct(ed|or)?|iyakkunar)\\b.*") ? Ask.DIRECTOR
                : s.matches(".*\\b(music|composer|composed|songs? by|isai)\\b.*") ? Ask.MUSIC
                : s.matches(".*\\b(release[sd]?|came out|which year|what year|when (was|did))\\b.*") ? Ask.RELEASE
                : s.matches(".*\\b(cast|acted|actors?|heroine|hero|starring|stars? in)\\b.*") ? Ask.CAST
                : null;
        if (ask == null) return null;
        try {
            Films.Film film = find(c, s);
            if (film == null) return null;
            JSONObject m = Tmdb.movie(c, film.id);
            String title = m.optString("title", film.title);
            String date = m.optString("release_date", "");
            switch (ask) {
                case DIRECTOR: {
                    String d = Tmdb.director(m);
                    return d.isEmpty() ? null : title + " was directed by " + d + ".";
                }
                case MUSIC: {
                    String mu = Tmdb.music(m);
                    return mu.isEmpty() ? null : "The music for " + title + " is by " + mu + ".";
                }
                case RELEASE:
                    return date.isEmpty() ? null : title + " released on " + spokenDate(date) + ".";
                case CAST: {
                    String cast = Tmdb.cast(m, 6);
                    return cast.isEmpty() ? null : title + " stars " + cast + ".";
                }
                default:
                    return null;
            }
        } catch (Exception e) {
            return null;
        }
    }

    /** The lead films first (their English or Tamil names), then his whole filmography; longest name wins. */
    private static Films.Film find(Context c, String s) throws Exception {
        Films.Film best = null;
        List<Films.Film> lead = Films.resolve(c, Films.curated());
        for (Films.Film f : lead) {
            if (f.id < 0) continue;
            String t = f.title.toLowerCase(Locale.ROOT);
            if ((s.contains(t) || s.contains(f.tamil)) && (best == null || t.length() > best.title.length())) best = f;
        }
        if (best != null) return best;
        for (Films.Film f : Films.all(c)) {
            String t = f.title.toLowerCase(Locale.ROOT);
            if (t.length() >= 4 && s.contains(t) && (best == null || t.length() > best.title.length())) best = f;
        }
        return best;
    }

    private static String spokenDate(String iso) {
        try {
            java.text.SimpleDateFormat in = new java.text.SimpleDateFormat("yyyy-MM-dd", Locale.ROOT);
            return new java.text.SimpleDateFormat("d MMMM yyyy", Locale.ENGLISH).format(in.parse(iso));
        } catch (Exception e) {
            return iso;
        }
    }

    private FilmAnswer() {
    }
}
