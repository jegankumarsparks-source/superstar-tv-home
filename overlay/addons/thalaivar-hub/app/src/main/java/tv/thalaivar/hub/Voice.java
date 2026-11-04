package tv.thalaivar.hub;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.speech.tts.TextToSpeech;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * J.A.R.V.I.S's voice. First choice: Gemini's speech model, which reads the reply in a natural human voice
 * (24 kHz PCM). If that is slow or unavailable, the best voice the TV's own text-to-speech has, instead of
 * its default robotic one.
 */
final class Voice {
    /**
     * The voice models, newest first. Each has its own free daily quota, so when one runs out (HTTP 429) the next
     * speaks instead, in the same voice, and the spent one rests for an hour.
     */
    static final String[] MODELS = {"gemini-3.8-flash-lite-tts", "gemini-3.8-flash-tts", "gemini-3.1-flash-tts-preview",
            "gemini-2.5-flash-preview-tts"};
    private static final long[] RESTING = new long[MODELS.length];
    /** A warm, easy-going prebuilt voice. */
    static final String NAME = "Puck";
    private static final int RATE = 24000;
    private static AudioTrack track;

    /**
     * Blocking: the reply as 16-bit mono PCM at 24 kHz, or null. Each phrase is kept on the TV after the first
     * time, so "Opening Netflix" and the other everyday replies play at once in the human voice.
     */
    /** A phrase already kept on the TV, or null; never goes to the network. */
    static byte[] cached(android.content.Context c, String text) {
        java.io.File f = new java.io.File(new java.io.File(c.getCacheDir(), "voice"), Net.hash(NAME + "|" + text.trim()) + ".pcm");
        if (!f.exists()) return null;
        try (java.io.FileInputStream in = new java.io.FileInputStream(f)) {
            return Net.readAll(in);
        } catch (Exception e) {
            return null;
        }
    }

    static byte[] natural(android.content.Context c, String text) {
        if (!Gemini.hasKey() || text == null || text.trim().isEmpty()) return null;
        java.io.File cached = new java.io.File(new java.io.File(c.getCacheDir(), "voice"), Net.hash(NAME + "|" + text.trim()) + ".pcm");
        if (cached.exists()) {
            try (java.io.FileInputStream in = new java.io.FileInputStream(cached)) {
                return Net.readAll(in);
            } catch (Exception ignored) {
                // fetch it again below
            }
        }
        byte[] pcm = fetch(c, text);
        if (pcm != null && pcm.length < 2_000_000) {
            try {
                Net.write(cached, pcm);
            } catch (Exception ignored) {
                // still played, just not kept
            }
        }
        return pcm;
    }

    /** Background: records J.A.R.V.I.S's everyday confirmations once, so even the first "play" or "open" answers instantly. */
    static void warm(android.content.Context c) {
        if (!Gemini.hasKey()) return;
        Ui.WORK.execute(() -> {
            for (String p : QuickCommand.PHRASES) natural(c, p);
        });
    }

    private static byte[] fetch(android.content.Context c, String text) {
        android.content.SharedPreferences p = c.getSharedPreferences("thalaivar", android.content.Context.MODE_PRIVATE);
        for (int i = 0; i < MODELS.length; i++) {
            // a model out of its daily quota is skipped until Google says it is back (kept across restarts)
            if (System.currentTimeMillis() < Math.max(RESTING[i], p.getLong("voice_rest_" + MODELS[i], 0))) continue;
            try {
                byte[] pcm = fetch(MODELS[i], text);
                if (pcm != null) return pcm;
            } catch (java.io.IOException e) {
                String m = e.getMessage() == null ? "" : e.getMessage();
                android.util.Log.w("JarvisVoice", MODELS[i] + ": " + m.split("\n", 2)[0]);
                if (m.startsWith("HTTP 429")) {
                    java.util.regex.Matcher wait = java.util.regex.Pattern.compile("\"retryDelay\":\\s*\"(\\d+)s\"").matcher(m);
                    long ms = wait.find() ? Long.parseLong(wait.group(1)) * 1000L : 3600_000L;
                    RESTING[i] = System.currentTimeMillis() + ms;
                    p.edit().putLong("voice_rest_" + MODELS[i], RESTING[i]).apply();
                }
            } catch (Exception e) {
                android.util.Log.w("JarvisVoice", MODELS[i] + ": " + e);
                // a reply without audio: the next model
            }
        }
        return null;
    }

    /** 16-bit mono PCM at 24 kHz from one model; newer models wrap it in a WAV header, which is cut off. */
    private static byte[] fetch(String model, String text) throws Exception {
        {
            JSONObject body = new JSONObject();
            body.put("contents", new JSONArray().put(new JSONObject().put("parts",
                    new JSONArray().put(new JSONObject().put("text", "Say this warmly and naturally, with the cheerful energy of a Chennai Superstar fan, like a friendly assistant: " + text)))));
            body.put("generationConfig", new JSONObject()
                    .put("responseModalities", new JSONArray().put("AUDIO"))
                    .put("speechConfig", new JSONObject().put("voiceConfig", new JSONObject()
                            .put("prebuiltVoiceConfig", new JSONObject().put("voiceName", NAME)))));
            Map<String, String> h = new HashMap<>();
            h.put("x-goog-api-key", BuildConfig.GEMINI_KEY);
            String res = Net.postJson("https://generativelanguage.googleapis.com/v1beta/models/" + model + ":generateContent", h, body.toString(), 1, 0);
            JSONObject part = new JSONObject(res).getJSONArray("candidates").getJSONObject(0)
                    .getJSONObject("content").getJSONArray("parts").getJSONObject(0);
            JSONObject data = part.optJSONObject("inlineData");
            if (data == null) return null;
            byte[] audio = Base64.decode(data.getString("data"), Base64.DEFAULT);
            return audio.length > 44 && audio[0] == 'R' && audio[1] == 'I' && audio[2] == 'F' && audio[3] == 'F' ? wavData(audio) : audio;
        }
    }

    /** The samples of a WAV file (the "data" chunk). */
    private static byte[] wavData(byte[] wav) {
        int i = 12;
        while (i + 8 <= wav.length) {
            int size = (wav[i + 4] & 0xFF) | (wav[i + 5] & 0xFF) << 8 | (wav[i + 6] & 0xFF) << 16 | (wav[i + 7] & 0xFF) << 24;
            if (wav[i] == 'd' && wav[i + 1] == 'a' && wav[i + 2] == 't' && wav[i + 3] == 'a') {
                int from = i + 8, len = Math.min(size < 0 ? Integer.MAX_VALUE : size, wav.length - from);
                return java.util.Arrays.copyOfRange(wav, from, from + len);
            }
            i += 8 + size + (size & 1);
        }
        return java.util.Arrays.copyOfRange(wav, 44, wav.length);
    }

    /** Plays the PCM; returns its length in milliseconds (so the face can stop talking on time). */
    static synchronized long play(byte[] pcm) {
        stop();
        track = new AudioTrack.Builder()
                .setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANT)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                .setAudioFormat(new AudioFormat.Builder().setSampleRate(RATE).setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(pcm.length)
                .build();
        track.write(pcm, 0, pcm.length);
        track.play();
        return pcm.length * 1000L / (RATE * 2L);
    }

    static synchronized void stop() {
        if (track != null) {
            try {
                track.stop();
            } catch (IllegalStateException ignored) {
                // never started
            }
            track.release();
            track = null;
        }
    }

    /** The fallback: the TV's best voice for the language, preferring high-quality and network voices. */
    static void pickBestVoice(TextToSpeech tts, Locale want) {
        try {
            Set<android.speech.tts.Voice> voices = tts.getVoices();
            if (voices == null) return;
            android.speech.tts.Voice best = null;
            int bestScore = -1;
            for (android.speech.tts.Voice v : voices) {
                Locale l = v.getLocale();
                if (!l.getLanguage().equals(want.getLanguage())) continue;
                if (v.getFeatures() != null && v.getFeatures().contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED)) continue;
                int score = v.getQuality();
                if (want.getCountry().equals(l.getCountry())) score += 200;
                if (v.isNetworkConnectionRequired()) score += 50;
                if (v.getName().contains("legacy")) score -= 300;
                if (score > bestScore) {
                    bestScore = score;
                    best = v;
                }
            }
            android.util.Log.i("JarvisVoice", "TV voice: " + (best == null ? "default" : best.getName()));
            if (best != null) tts.setVoice(best);
            tts.setSpeechRate(0.97f);
            tts.setPitch(1.02f);
        } catch (Exception ignored) {
            // keep the engine's default
        }
    }

    private Voice() {
    }
}
