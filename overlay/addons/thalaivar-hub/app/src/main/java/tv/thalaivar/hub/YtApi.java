package tv.thalaivar.hub;

import android.content.Context;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * JK Tube's catalogue: the official YouTube Data API v3 with the owner's own free key (search, trending,
 * related), cached like everything else. Without a key, JK Tube still has the official trailers TMDB lists
 * for each film, and search is handed to the YouTube app.
 */
final class YtApi {
    static final class Video {
        String id, title, channel, published;
        String thumb() {
            return "https://i.ytimg.com/vi/" + id + "/hqdefault.jpg";
        }
    }

    private static final String API = "https://www.googleapis.com/youtube/v3";
    private static final long HOUR = 3600L * 1000;

    static boolean hasKey() {
        return !BuildConfig.YT_KEY.isEmpty();
    }

    static List<Video> search(Context c, String q, int max) throws Exception {
        // videoEmbeddable: only videos the embedded player is allowed to play
        String path = "/search?part=snippet&type=video&videoEmbeddable=true&safeSearch=moderate&regionCode=IN&maxResults=" + max
                + "&q=" + Uri.encode(q) + "&key=" + BuildConfig.YT_KEY;
        return parse(Net.cached(c, "yt:" + q + max, 6 * HOUR, API + path, null).body, true);
    }

    static List<Video> trending(Context c, int max) throws Exception {
        String path = "/videos?part=snippet&chart=mostPopular&regionCode=IN&maxResults=" + max + "&key=" + BuildConfig.YT_KEY;
        return parse(Net.cached(c, "yt:trending" + max, 3 * HOUR, API + path, null).body, false);
    }

    private static List<Video> parse(String body, boolean searchShape) throws Exception {
        JSONArray items = new JSONObject(body).optJSONArray("items");
        List<Video> l = new ArrayList<>();
        if (items == null) return l;
        for (int i = 0; i < items.length(); i++) {
            JSONObject it = items.getJSONObject(i);
            Video v = new Video();
            v.id = searchShape ? it.optJSONObject("id").optString("videoId") : it.optString("id");
            JSONObject s = it.optJSONObject("snippet");
            if (v.id == null || v.id.isEmpty() || s == null) continue;
            v.title = android.text.Html.fromHtml(s.optString("title"), android.text.Html.FROM_HTML_MODE_LEGACY).toString();
            v.channel = s.optString("channelTitle");
            v.published = s.optString("publishedAt", "");
            l.add(v);
        }
        return l;
    }

    /**
     * Without a YouTube key: when the request names one of his films ("Baasha trailer", "play Jailer 2"),
     * that film's official trailer from TMDB, ready to play. Blocking; call off the main thread.
     */
    static Video filmTrailer(Context c, String request) {
        try {
            String want = request.toLowerCase(java.util.Locale.ROOT);
            Films.Film hit = null;
            for (Films.Film f : Films.resolve(c, Films.curated())) {
                String t = f.title.toLowerCase(java.util.Locale.ROOT);
                if (f.id > 0 && (want.contains(t) || want.contains(f.tamil)) && (hit == null || t.length() > hit.title.length())) hit = f;
            }
            if (hit == null) return null;
            String key = MovieActivity.bestVideo(Tmdb.movie(c, hit.id), "Trailer");
            if (key == null) return null;
            Video v = new Video();
            v.id = key;
            v.title = hit.title + " · Official trailer";
            return v;
        } catch (Exception e) {
            return null;
        }
    }

    /** Official trailers and clips TMDB lists for the lead films: a keyless JK Tube row. */
    static List<Video> thalaivarVideos(Context c) {
        List<Video> l = new ArrayList<>();
        try {
            for (Films.Film f : Films.resolve(c, Films.curated())) {
                if (f.id < 0) continue;
                JSONObject vids = Tmdb.movie(c, f.id).optJSONObject("videos");
                JSONArray r = vids == null ? null : vids.optJSONArray("results");
                if (r == null) continue;
                for (int i = 0; i < Math.min(3, r.length()); i++) {
                    JSONObject o = r.getJSONObject(i);
                    if (!"YouTube".equals(o.optString("site"))) continue;
                    Video v = new Video();
                    v.id = o.optString("key");
                    v.title = f.title + " · " + o.optString("name");
                    v.channel = o.optString("type");
                    l.add(v);
                }
            }
        } catch (Exception ignored) {
            // offline: the row stays empty
        }
        return l;
    }

    private YtApi() {
    }
}
