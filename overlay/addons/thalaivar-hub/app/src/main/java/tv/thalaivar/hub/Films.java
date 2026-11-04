package tv.thalaivar.hub;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** The Thalaivar collection: the films the hub leads with, matched to TMDB at run time. */
final class Films {
    static final class Film {
        final String title, tamil, stem;
        final int year;
        long id = -1;
        String poster, backdrop, overview = "";
        double rating;

        Film(String title, String tamil, int year, String stem) {
            this.title = title;
            this.tamil = tamil;
            this.year = year;
            this.stem = stem;
        }

        String name() {
            return Lang.tamil ? tamil : title;
        }
    }

    static List<Film> curated() {
        List<Film> l = new ArrayList<>();
        l.add(new Film("Jailer 2", "ஜெயிலர் 2", 2026, "jailer-2"));
        l.add(new Film("Coolie", "கூலி", 2025, "coolie"));
        l.add(new Film("Vettaiyan", "வேட்டையன்", 2024, "vettaiyan"));
        l.add(new Film("Lal Salaam", "லால் சலாம்", 2024, "lal-salaam"));
        l.add(new Film("Jailer", "ஜெயிலர்", 2023, "jailer"));
        l.add(new Film("Annaatthe", "அண்ணாத்த", 2021, "annaatthe"));
        l.add(new Film("Darbar", "தர்பார்", 2020, "darbar"));
        l.add(new Film("Petta", "பேட்ட", 2019, "petta"));
        l.add(new Film("Kaala", "காலா", 2018, "kaala"));
        l.add(new Film("Kabali", "கபாலி", 2016, "kabali"));
        l.add(new Film("Enthiran", "எந்திரன்", 2010, "enthiran"));
        l.add(new Film("Sivaji", "சிவாஜி", 2007, "sivaji"));
        l.add(new Film("Padayappa", "படையப்பா", 1999, "padayappa"));
        l.add(new Film("Baasha", "பாட்ஷா", 1995, "baasha"));
        l.add(new Film("Muthu", "முத்து", 1995, "muthu"));
        l.add(new Film("Annamalai", "அண்ணாமலை", 1992, "annamalai"));
        l.add(new Film("Thalapathi", "தளபதி", 1991, "thalapathi"));
        return l;
    }

    /** Fills ids and artwork from his TMDB credits; a title TMDB lacks keeps id -1 and shows as a text card. */
    static List<Film> resolve(Context c, List<Film> films) throws Exception {
        JSONArray credits = Tmdb.credits(c);
        for (Film f : films) {
            JSONObject hit = null;
            for (int i = 0; i < credits.length() && hit == null; i++) {
                JSONObject o = credits.getJSONObject(i);
                int y = year(o);
                boolean name = f.title.equalsIgnoreCase(o.optString("title")) || f.title.equalsIgnoreCase(o.optString("original_title"));
                if (name && (y == 0 || Math.abs(y - f.year) <= 1)) hit = o;
            }
            if (hit == null) {
                JSONArray r = Tmdb.searchMovie(c, f.title, f.year).optJSONArray("results");
                if (r != null && r.length() > 0) hit = r.getJSONObject(0);
            }
            if (hit != null) fill(f, hit);
        }
        return films;
    }

    /** His whole filmography as TMDB lists it, newest first, for the complete row. */
    static List<Film> all(Context c) throws Exception {
        JSONArray credits = Tmdb.credits(c);
        List<Film> l = new ArrayList<>();
        for (int i = 0; i < credits.length(); i++) {
            JSONObject o = credits.getJSONObject(i);
            if (o.optString("poster_path", "").isEmpty() || "null".equals(o.optString("poster_path"))) continue;
            Film f = new Film(o.optString("title"), o.optString("title"), year(o), "");
            fill(f, o);
            l.add(f);
        }
        l.sort((a, b) -> Integer.compare(b.year, a.year));
        return l;
    }

    private static void fill(Film f, JSONObject o) {
        f.id = o.optLong("id", -1);
        f.poster = o.optString("poster_path", null);
        f.backdrop = o.optString("backdrop_path", null);
        f.overview = o.optString("overview", "");
        f.rating = o.optDouble("vote_average", 0);
    }

    static int year(JSONObject o) {
        String d = o.optString("release_date", "");
        try {
            return d.length() >= 4 ? Integer.parseInt(d.substring(0, 4)) : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private Films() {
    }
}
