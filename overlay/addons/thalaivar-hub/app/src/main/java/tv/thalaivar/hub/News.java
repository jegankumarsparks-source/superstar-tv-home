package tv.thalaivar.hub;

import android.content.Context;
import android.net.Uri;
import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;

import java.io.StringReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Headlines from Google News RSS searches, the publishers' own titles and links. Fetched at most every
 * 30 minutes, kept on disk with their time, and shown from that copy when the TV is offline.
 */
final class News {
    static final int CONFIRMED = 0, REPORTED = 1, RUMOUR = 2;
    static final long MAX_AGE = 30L * 60 * 1000;

    /** Topics the engine follows: the label shown as a filter, and the search it runs. */
    static final String[][] TOPICS = {
            {"Rajinikanth", "Rajinikanth"},
            {"Jailer 2", "\"Jailer 2\""},
            {"Box office", "\"Jailer 2\" box office"},
            {"Dharman", "Rajinikanth Dharman"},
            {"KH x RK", "Kamal Haasan Rajinikanth film"},
    };

    static final class Item {
        String title, source, link, topic;
        long time;
        int kind;
    }

    static final class Feed {
        final List<Item> items;
        final long savedAt;
        final boolean fresh;

        Feed(List<Item> items, long savedAt, boolean fresh) {
            this.items = items;
            this.savedAt = savedAt;
            this.fresh = fresh;
        }
    }

    private static final Pattern RUMOUR_WORDS = Pattern.compile(
            "(?i)\\b(rumou?r|buzz|reportedly|speculat\\w*|likely|could|may|might|grapevine|unconfirmed|gossip|tipped|said to|in talks)\\b|வதந்தி|கிசுகிசு");
    private static final Pattern CONFIRMED_WORDS = Pattern.compile(
            "(?i)\\b(officially|official|announce[sd]?|confirm(s|ed)?|unveil(s|ed)?|first look|trailer (out|released)|release date|wraps up|launch(es|ed)?)\\b|அதிகாரப்பூர்வ|அறிவிப்பு");
    private static final Set<String> OFFICIAL_SOURCES = new HashSet<>();

    static {
        OFFICIAL_SOURCES.add("sun pictures");
        OFFICIAL_SOURCES.add("lyca productions");
        OFFICIAL_SOURCES.add("press information bureau");
    }

    /** Confirmed, reported or rumour, from the source and the headline's own wording. */
    static int classify(String title, String source) {
        String s = source == null ? "" : source.toLowerCase(Locale.ROOT);
        if (OFFICIAL_SOURCES.contains(s)) return CONFIRMED;
        if (RUMOUR_WORDS.matcher(title).find()) return RUMOUR;
        if (CONFIRMED_WORDS.matcher(title).find()) return CONFIRMED;
        return REPORTED;
    }

    static String url(String query) {
        boolean tamilQuery = query.codePoints().anyMatch(cp -> cp >= 0x0B80 && cp <= 0x0BFF);
        String hl = tamilQuery ? "ta" : "en-IN";
        String ceid = tamilQuery ? "IN:ta" : "IN:en";
        return "https://news.google.com/rss/search?q=" + Uri.encode(query + " when:30d") + "&hl=" + hl + "&gl=IN&ceid=" + ceid;
    }

    /** Every topic merged, newest first, duplicates dropped. force skips the 30-minute cache. */
    static Feed load(Context c, boolean force) {
        List<Item> all = new ArrayList<>();
        long saved = 0;
        boolean fresh = true;
        for (String[] t : TOPICS) {
            try {
                Net.Result r = Net.cached(c, "news:" + t[1], force ? 0 : MAX_AGE, url(t[1]), null);
                saved = Math.max(saved, r.savedAt);
                fresh &= r.fresh;
                for (Item i : parse(r.body)) {
                    i.topic = t[0];
                    all.add(i);
                }
            } catch (Exception e) {
                fresh = false;
            }
        }
        Set<String> seen = new HashSet<>();
        List<Item> out = new ArrayList<>();
        all.sort((a, b) -> Long.compare(b.time, a.time));
        for (Item i : all) {
            String k = i.title.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]", "");
            if (seen.add(k)) out.add(i);
        }
        return new Feed(out, saved, fresh);
    }

    static List<Item> parse(String xml) throws Exception {
        List<Item> l = new ArrayList<>();
        XmlPullParser p = Xml.newPullParser();
        p.setInput(new StringReader(xml));
        Item cur = null;
        String tag = null;
        SimpleDateFormat rfc = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.ENGLISH);
        for (int e = p.getEventType(); e != XmlPullParser.END_DOCUMENT; e = p.next()) {
            if (e == XmlPullParser.START_TAG) {
                tag = p.getName();
                if ("item".equals(tag)) cur = new Item();
            } else if (e == XmlPullParser.TEXT && cur != null && tag != null) {
                String v = p.getText().trim();
                if (v.isEmpty()) continue;
                switch (tag) {
                    case "title": cur.title = (cur.title == null ? "" : cur.title) + v; break;
                    case "link": cur.link = v; break;
                    case "source": cur.source = v; break;
                    case "pubDate":
                        try {
                            cur.time = rfc.parse(v).getTime();
                        } catch (Exception ignored) {
                            cur.time = 0;
                        }
                        break;
                    default: break;
                }
            } else if (e == XmlPullParser.END_TAG) {
                if ("item".equals(p.getName()) && cur != null && cur.title != null) {
                    // Google News titles end in " - Publisher"; the publisher is shown on its own line
                    if (cur.source != null && cur.title.endsWith(" - " + cur.source)) {
                        cur.title = cur.title.substring(0, cur.title.length() - cur.source.length() - 3);
                    }
                    cur.kind = classify(cur.title, cur.source);
                    l.add(cur);
                    cur = null;
                }
                tag = null;
            }
        }
        return l;
    }

    static String ago(long t) {
        if (t <= 0) return "";
        long m = (System.currentTimeMillis() - t) / 60000;
        if (m < 1) return Lang.tamil ? "இப்போது" : "just now";
        if (m < 60) return m + (Lang.tamil ? " நிமி. முன்" : " min ago");
        long h = m / 60;
        if (h < 24) return h + (Lang.tamil ? " மணி முன்" : " h ago");
        return (h / 24) + (Lang.tamil ? " நாள் முன்" : " d ago");
    }

    private News() {
    }
}
