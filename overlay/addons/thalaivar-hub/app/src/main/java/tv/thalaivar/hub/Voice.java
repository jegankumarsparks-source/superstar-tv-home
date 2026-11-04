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
    static final String MODEL = "gemini-2.5-flash-preview-tts";
    /** A warm, easy-going prebuilt voice. */
    static final String NAME = "Puck";
    private static final int RATE = 24000;
    private static AudioTrack track;

    /**
     * Blocking: the reply as 16-bit mono PCM at 24 kHz, or null. Each phrase is kept on the TV after the first
     * time, so "Opening Netflix" and the other everyday replies play at once in the human voice.
     */
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
        byte[] pcm = fetch(text);
        if (pcm != null && pcm.length < 2_000_000) {
            try {
                Net.write(cached, pcm);
            } catch (Exception ignored) {
                // still played, just not kept
            }
        }
        return pcm;
    }

    private static byte[] fetch(String text) {
        try {
            JSONObject body = new JSONObject();
            body.put("contents", new JSONArray().put(new JSONObject().put("parts",
                    new JSONArray().put(new JSONObject().put("text", "Say this warmly and naturally, like a friendly assistant: " + text)))));
            body.put("generationConfig", new JSONObject()
                    .put("responseModalities", new JSONArray().put("AUDIO"))
                    .put("speechConfig", new JSONObject().put("voiceConfig", new JSONObject()
                            .put("prebuiltVoiceConfig", new JSONObject().put("voiceName", NAME)))));
            Map<String, String> h = new HashMap<>();
            h.put("x-goog-api-key", BuildConfig.GEMINI_KEY);
            String res = Net.postJson("https://generativelanguage.googleapis.com/v1beta/models/" + MODEL + ":generateContent", h, body.toString());
            JSONObject part = new JSONObject(res).getJSONArray("candidates").getJSONObject(0)
                    .getJSONObject("content").getJSONArray("parts").getJSONObject(0);
            JSONObject data = part.optJSONObject("inlineData");
            return data == null ? null : Base64.decode(data.getString("data"), Base64.DEFAULT);
        } catch (Exception e) {
            return null;
        }
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
