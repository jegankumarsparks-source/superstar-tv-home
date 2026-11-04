package tv.thalaivar.hub;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The assistant's brain: Gemini with the owner's own key. Each turn returns a short spoken reply and at most
 * one action for the TV, as JSON, so the app can act on it without guessing.
 */
final class Gemini {
    /** Checked against this key's model list: the fastest model that answers it. */
    static final String MODEL = "gemini-flash-lite-latest";
    /** Google Search grounding needs quota the free tier may not have; after one refusal, answer without it. */
    private static volatile long groundingOffUntil;

    static final class Reply {
        String say = "", action = "none", query = "";
    }

    static boolean hasKey() {
        return !BuildConfig.GEMINI_KEY.isEmpty();
    }

    private static String system(List<String> apps) {
        return "You are JARVIS, the voice assistant of THALAIVAR TV, an Android TV in Chennai whose owner is a "
                + "Superstar Rajinikanth fan. Reply in the language the user spoke (English or Tamil), warm and brief: "
                + "one or two short sentences that sound natural when read aloud. For anything current (news, box office, "
                + "scores, weather, dates) use Google Search if you have it; if you cannot check, say your answer may be out "
                + "of date. Never recite song lyrics or film "
                + "dialogue; to hear a song, use the play action.\n"
                + "Choose at most one action:\n"
                + "- play: play a video; query = what to search on YouTube (e.g. 'Jailer 2 trailer', 'Anirudh hit songs')\n"
                + "- tube: open the JK Tube video app\n"
                + "- film: open a Rajinikanth film page; query = the film title\n"
                + "- open_app: open an installed app; query = its name, one of: " + String.join(", ", apps) + "\n"
                + "- news: show the latest Rajinikanth news\n"
                + "- photos: show the full-screen photo gallery\n"
                + "- screensaver: start the screensaver\n"
                + "- none: just answer\n"
                + "Answer only with JSON: {\"say\": \"...\", \"action\": \"...\", \"query\": \"...\"}.";
    }

    /** history: alternating user / model texts, oldest first, the newest user text last. */
    /** The search-quota refusal is remembered across restarts, so a reinstall doesn't pay the slow retry again. */
    static void restore(android.content.Context c) {
        groundingOffUntil = Math.max(groundingOffUntil,
                c.getSharedPreferences("thalaivar", android.content.Context.MODE_PRIVATE).getLong("grounding_off_until", 0));
    }

    private static void remember(android.content.Context c) {
        c.getSharedPreferences("thalaivar", android.content.Context.MODE_PRIVATE).edit()
                .putLong("grounding_off_until", groundingOffUntil).apply();
    }

    /** Fast on the free tier (about 5 s) but it sometimes answers 500; raced against the Gemini model. */
    static final String FAST_MODEL = "gemma-4-26b-a4b-it";
    /** A second Gemini model with its own quota, raced without search (search makes answers slower). */
    static final String QUICK_MODEL = "gemini-3.5-flash-lite";
    private static final java.util.concurrent.ExecutorService RACE = java.util.concurrent.Executors.newFixedThreadPool(3);

    /**
     * Asks Gemma and Gemini at the same time and returns whichever answers well first, so a reply arrives in
     * about 5 s instead of 10-25 s on the free tier; if one fails, the other still answers.
     */
    static Reply ask(android.content.Context ctx, List<String[]> history, List<String> apps) throws Exception {
        java.util.concurrent.ExecutorCompletionService<Reply> race = new java.util.concurrent.ExecutorCompletionService<>(RACE);
        java.util.List<java.util.concurrent.Future<Reply>> runs = new java.util.ArrayList<>();
        runs.add(race.submit(() -> askGemma(history, apps)));
        runs.add(race.submit(() -> askGemini(ctx, history, apps, MODEL, true)));
        runs.add(race.submit(() -> askGemini(ctx, history, apps, QUICK_MODEL, false)));
        Exception last = null;
        try {
            for (int i = 0; i < runs.size(); i++) {
                try {
                    Reply r = race.take().get();
                    if (r != null && !r.say.isEmpty()) return r;
                } catch (java.util.concurrent.ExecutionException e) {
                    last = e.getCause() instanceof Exception ? (Exception) e.getCause() : e;
                }
            }
        } finally {
            for (java.util.concurrent.Future<Reply> f : runs) f.cancel(true);
        }
        throw last != null ? last : new java.io.IOException("no answer");
    }

    /** Gemma takes no separate system instruction: the instructions lead the first message (5xx retried by Net). */
    private static Reply askGemma(List<String[]> history, List<String> apps) throws Exception {
        JSONArray contents = new JSONArray();
        for (int i = 0; i < history.size(); i++) {
            String[] h = history.get(i);
            String text = i == 0 ? system(apps) + "\n\nUser: " + h[1] : h[1];
            contents.put(new JSONObject().put("role", h[0]).put("parts", new JSONArray().put(new JSONObject().put("text", text))));
        }
        JSONObject body = new JSONObject().put("contents", contents)
                .put("generationConfig", new JSONObject().put("temperature", 0.4).put("maxOutputTokens", 400));
        Map<String, String> h = new HashMap<>();
        h.put("x-goog-api-key", BuildConfig.GEMINI_KEY);
        String url = "https://generativelanguage.googleapis.com/v1beta/models/" + FAST_MODEL + ":generateContent";
        // Gemma's free tier often answers 500 in under a second and then succeeds: retry fast (the Gemini run
        // races alongside, and whichever answers first wins)
        return parse(Net.postJson(url, h, body.toString(), 6, 150));
    }

    private static Reply askGemini(android.content.Context ctx, List<String[]> history, List<String> apps, String model,
                                   boolean search) throws Exception {
        restore(ctx);
        JSONObject body = new JSONObject();
        body.put("systemInstruction", new JSONObject().put("parts", new JSONArray().put(new JSONObject().put("text", system(apps)))));
        JSONArray contents = new JSONArray();
        for (String[] h : history) {
            contents.put(new JSONObject().put("role", h[0]).put("parts", new JSONArray().put(new JSONObject().put("text", h[1]))));
        }
        body.put("contents", contents);
        // JSON is asked for in the instructions and cut out of the text below, because Google Search grounding
        // cannot be combined with JSON mode.
        body.put("generationConfig", new JSONObject().put("temperature", 0.4).put("maxOutputTokens", 600));
        Map<String, String> h = new HashMap<>();
        h.put("x-goog-api-key", BuildConfig.GEMINI_KEY);
        String url = "https://generativelanguage.googleapis.com/v1beta/models/" + model + ":generateContent";
        String res;
        if (search && System.currentTimeMillis() >= groundingOffUntil) {
            body.put("tools", new JSONArray().put(new JSONObject().put("google_search", new JSONObject())));
            try {
                res = Net.postJson(url, h, body.toString());
            } catch (java.io.IOException e) {
                if (e.getMessage() == null || !e.getMessage().startsWith("HTTP 429")) throw e;
                // out of search quota (or a per-minute limit): answer without search, try search again in an hour
                groundingOffUntil = System.currentTimeMillis() + 3600_000L;
                remember(ctx);
                body.remove("tools");
                res = Net.postJson(url, h, body.toString());
            }
        } else {
            res = Net.postJson(url, h, body.toString());
        }
        return parse(res);
    }

    /** The model's text to a reply: the JSON object inside it, or the plain words if there is none. */
    static Reply parse(String res) throws Exception {
        JSONArray cands = new JSONObject(res).optJSONArray("candidates");
        JSONObject content = cands == null || cands.length() == 0 ? null : cands.getJSONObject(0).optJSONObject("content");
        JSONArray parts = content == null ? null : content.optJSONArray("parts");
        if (parts == null) {
            Reply blocked = new Reply();
            blocked.say = "Sorry, I can't answer that one.";
            return blocked;
        }
        StringBuilder all = new StringBuilder();
        // thinking parts (some models return them) are not the answer
        for (int i = 0; i < parts.length(); i++) {
            JSONObject part = parts.getJSONObject(i);
            if (!part.optBoolean("thought", false)) all.append(part.optString("text"));
        }
        String text = all.toString();
        int a = text.indexOf('{'), z = text.lastIndexOf('}');
        Reply r = new Reply();
        if (a < 0 || z <= a) {
            // the model answered in plain words: speak them, do nothing else
            r.say = text.trim();
            return r;
        }
        JSONObject j;
        try {
            j = new JSONObject(text.substring(a, z + 1));
        } catch (org.json.JSONException bad) {
            // cut off or not quite JSON: say what can be read, take no action
            r.say = text.replaceAll("[{}\"]|\\b(say|action|query)\\b\\s*:", " ").replaceAll("\\s+", " ").trim();
            return r;
        }
        r.say = j.optString("say", "");
        r.action = j.optString("action", "none");
        r.query = j.optString("query", "");
        return r;
    }

    private Gemini() {
    }
}
