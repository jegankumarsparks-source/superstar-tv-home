package tv.thalaivar.hub;

import android.content.Context;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/** TMDB, the only source of film facts and artwork: details, cast and crew, official videos, stills, providers. */
final class Tmdb {
    private static final String API = "https://api.themoviedb.org/3";
    private static final long DAY = 24L * 3600 * 1000;

    static boolean hasToken() {
        return !BuildConfig.TMDB_TOKEN.isEmpty();
    }

    private static Map<String, String> headers() {
        Map<String, String> h = new HashMap<>();
        h.put("Authorization", "Bearer " + BuildConfig.TMDB_TOKEN);
        h.put("Accept", "application/json");
        return h;
    }

    static JSONObject get(Context c, String path, long maxAgeMs) throws IOException, JSONException {
        if (!hasToken()) throw new IOException("no token");
        Net.Result r = Net.cached(c, "tmdb:" + path, maxAgeMs, API + path, headers());
        return new JSONObject(r.body);
    }

    /** Rajinikanth's TMDB person id: the most popular match for his name. */
    static long personId(Context c) throws IOException, JSONException {
        JSONArray res = get(c, "/search/person?query=Rajinikanth", 30 * DAY).getJSONArray("results");
        long best = -1;
        double pop = -1;
        for (int i = 0; i < res.length(); i++) {
            JSONObject o = res.getJSONObject(i);
            if (o.optDouble("popularity", 0) > pop) {
                pop = o.optDouble("popularity", 0);
                best = o.getLong("id");
            }
        }
        if (best < 0) throw new IOException("person not found");
        return best;
    }

    static JSONArray credits(Context c) throws IOException, JSONException {
        return get(c, "/person/" + personId(c) + "/movie_credits", 3 * DAY).getJSONArray("cast");
    }

    static JSONArray portraits(Context c) throws IOException, JSONException {
        return get(c, "/person/" + personId(c) + "/images", 7 * DAY).optJSONArray("profiles");
    }

    static JSONObject movie(Context c, long id) throws IOException, JSONException {
        return get(c, "/movie/" + id + "?language=en-IN&append_to_response=credits,videos,watch/providers,release_dates", 2 * DAY);
    }

    static JSONObject images(Context c, long id) throws IOException, JSONException {
        return get(c, "/movie/" + id + "/images?include_image_language=en,ta,null", 7 * DAY);
    }

    static JSONObject searchMovie(Context c, String title, int year) throws IOException, JSONException {
        return get(c, "/search/movie?query=" + Uri.encode(title) + "&year=" + year, 7 * DAY);
    }

    /** w342 posters on cards, w780 on the detail page, w1280 backdrops on a 1080p TV. */
    static String img(String path, String size) {
        if (path == null || path.isEmpty() || "null".equals(path)) return null;
        return "https://image.tmdb.org/t/p/" + size + path;
    }

    static String director(JSONObject movie) {
        return crew(movie, "Director");
    }

    static String music(JSONObject movie) {
        String m = crew(movie, "Original Music Composer");
        return m.isEmpty() ? crew(movie, "Music") : m;
    }

    private static String crew(JSONObject movie, String job) {
        JSONObject cr = movie.optJSONObject("credits");
        JSONArray crew = cr == null ? null : cr.optJSONArray("crew");
        StringBuilder b = new StringBuilder();
        if (crew != null) for (int i = 0; i < crew.length(); i++) {
            JSONObject o = crew.optJSONObject(i);
            if (o != null && job.equals(o.optString("job"))) {
                String n = o.optString("name");
                if (b.indexOf(n) < 0) b.append(b.length() == 0 ? "" : ", ").append(n);
            }
        }
        return b.toString();
    }

    static String cast(JSONObject movie, int max) {
        JSONObject cr = movie.optJSONObject("credits");
        JSONArray cast = cr == null ? null : cr.optJSONArray("cast");
        StringBuilder b = new StringBuilder();
        if (cast != null) for (int i = 0; i < Math.min(max, cast.length()); i++) {
            JSONObject o = cast.optJSONObject(i);
            if (o == null) continue;
            b.append(b.length() == 0 ? "" : ", ").append(o.optString("name"));
        }
        return b.toString();
    }

    private Tmdb() {
    }
}
