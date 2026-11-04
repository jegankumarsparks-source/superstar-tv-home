package tv.thalaivar.hub;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.List;

/**
 * J.A.R.V.I.S's voice and brain through the Gemini Live API, the way the laptop J.A.R.V.I.S works: one open
 * connection, a question goes in as text, and the answer comes back as a human voice that starts playing within
 * about a second, while it is still being spoken, with the words alongside. The Live models are free on the free
 * tier without the 10-a-day limit of the separate voice models. Play / open requests come back as tool calls.
 * The connection stays open between questions, so follow-ups keep their context.
 */
final class LiveJarvis {
    interface Listener {
        /** The words spoken so far in this answer. */
        void words(String soFar);
        /** A TV action the model asked for. */
        void tool(String name, JSONObject args);
        void done(String said);
        /** No answer could be had this way: the caller falls back to the plain text model. */
        void failed();
    }

    private static final String TAG = "JarvisLive";
    private static final String HOST = "generativelanguage.googleapis.com";
    /** The laptop J.A.R.V.I.S's pair: the 3.1 Live model, and 2.5 native audio when it is unavailable. */
    private static final String[] MODELS = {"gemini-3.1-flash-live-preview", "gemini-2.5-flash-native-audio-preview-12-2025"};
    private static final String VOICE = "Puck";
    private static final int RATE = 24000;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private static Socket socket;
    private static OutputStream out;
    private static volatile boolean ready;
    private static int modelIndex;
    private static volatile Listener current;
    private static volatile boolean speakAloud = true;
    /** True while an answer is being made; a new question during it skips the old one's remaining audio. */
    private static volatile boolean answering, skipOld;
    private static final StringBuilder said = new StringBuilder();
    private static AudioTrack track;
    private static final Object AUDIO = new Object(), WRITE = new Object();
    private static long sentAt;
    /** For the film database (film_facts) while answering. */
    private static volatile Context appContext;

    /** Asks a question; the listener hears back on the main thread. Never blocks the caller. */
    static void ask(Context c, String text, List<String> apps, boolean aloud, Listener l) {
        Context app = c.getApplicationContext();
        appContext = app;
        Ui.WORK.execute(() -> {
            synchronized (LiveJarvis.class) {
                if (answering) skipOld = true;
                current = l;
                speakAloud = aloud;
                said.setLength(0);
                flushAudio();
                try {
                    if (!ready) connect(app, apps);
                    send(new JSONObject().put("clientContent", new JSONObject()
                            .put("turns", new JSONArray().put(new JSONObject().put("role", "user")
                                    .put("parts", new JSONArray().put(new JSONObject().put("text", text)))))
                            .put("turnComplete", true)));
                    answering = true;
                    sentAt = System.currentTimeMillis();
                    Log.i(TAG, "asked (skipOld=" + skipOld + ")");
                } catch (Exception e) {
                    Log.w(TAG, "ask failed: " + e);
                    close();
                    MAIN.post(l::failed);
                    return;
                }
            }
            // nothing at all within 8 s: give up on this answer and let the text model try
            MAIN.postDelayed(() -> {
                if (current == l && answering && said.length() == 0 && System.currentTimeMillis() - sentAt >= 7900) {
                    Log.w(TAG, "no answer in 8 s");
                    current = null;
                    answering = false;
                    l.failed();
                }
            }, 8000);
        });
    }

    private static long warmedAt;

    /**
     * Opens the connection ahead of the first question (the J.A.R.V.I.S panel or the phone remote is open), so
     * even the first answer starts speaking at once. At most once every 4 minutes.
     */
    static void warm(Context c) {
        if (ready || !Gemini.hasKey() || System.currentTimeMillis() - warmedAt < 240_000L) return;
        warmedAt = System.currentTimeMillis();
        Context app = c.getApplicationContext();
        appContext = app;
        Ui.WORK.execute(() -> {
            synchronized (LiveJarvis.class) {
                if (ready) return;
                try {
                    connect(app, appNames(app));
                } catch (Exception e) {
                    Log.w(TAG, "warm failed: " + e.getMessage());
                }
            }
        });
    }

    /** The names of the TV's apps, for the instructions. */
    static List<String> appNames(Context c) {
        List<String> names = new java.util.ArrayList<>();
        android.content.pm.PackageManager pm = c.getPackageManager();
        for (android.content.pm.ResolveInfo r : pm.queryIntentActivities(new android.content.Intent(android.content.Intent.ACTION_MAIN)
                .addCategory(android.content.Intent.CATEGORY_LEANBACK_LAUNCHER), 0)) names.add(r.loadLabel(pm).toString());
        return names;
    }

    /** Silences the voice now (Back pressed, another sound starting). */
    static void stopAudio() {
        Ui.WORK.execute(LiveJarvis::flushAudio);
    }

    // ── connection ────────────────────────────────────────────────────────────────────────────

    private static void connect(Context c, List<String> apps) throws Exception {
        Exception last = null;
        for (int tries = 0; tries < MODELS.length; tries++) {
            String model = MODELS[(modelIndex + tries) % MODELS.length];
            try {
                open();
                send(setup(model, apps));
                // the first message must be setupComplete; anything else (or a close) means this model can't be used
                String first = read(new DataInputStream(socket.getInputStream()));
                if (!new JSONObject(first).has("setupComplete")) throw new IOException("setup: " + first);
                modelIndex = (modelIndex + tries) % MODELS.length;
                ready = true;
                Socket mine = socket;
                Thread t = new Thread(() -> listen(mine), "jarvis-live");
                t.setDaemon(true);
                t.start();
                Log.i(TAG, "connected: " + model);
                return;
            } catch (Exception e) {
                last = e;
                Log.w(TAG, model + ": " + e.getMessage());
                close();
            }
        }
        throw last;
    }

    private static JSONObject setup(String model, List<String> apps) throws Exception {
        JSONArray fns = new JSONArray()
                .put(fn("play_video", "Play a song, songs, a trailer, a scene or any video on the TV, full screen.", "query",
                        "What to play, e.g. \"Anirudh songs\" or \"Jailer 2 trailer\"."))
                .put(fn("open_app", "Open an app installed on the TV.", "name", "The app's name, e.g. Netflix."))
                .put(fn("open_film", "Open one of Rajinikanth's films in the TV's film guide.", "title", "The film's title."))
                .put(fn("show", "Open one of the TV's own screens.", "screen", "One of: news, photos, screensaver, jktube."))
                .put(fn("film_facts", "Exact facts about one of Rajinikanth's films from the TV's film database: release, "
                        + "director, music, full cast with their roles (heroine, villain…), runtime and story.", "title", "The film's title."));
        JSONObject setup = new JSONObject()
                .put("model", "models/" + model)
                .put("generationConfig", new JSONObject()
                        .put("responseModalities", new JSONArray().put("AUDIO"))
                        .put("speechConfig", new JSONObject().put("voiceConfig", new JSONObject()
                                .put("prebuiltVoiceConfig", new JSONObject().put("voiceName", VOICE)))))
                .put("systemInstruction", new JSONObject().put("parts", new JSONArray().put(new JSONObject().put("text", system(apps)))))
                .put("tools", new JSONArray().put(new JSONObject().put("functionDeclarations", fns))
                        .put(new JSONObject().put("googleSearch", new JSONObject())))
                .put("outputAudioTranscription", new JSONObject())
                .put("contextWindowCompression", new JSONObject().put("slidingWindow", new JSONObject()));
        return new JSONObject().put("setup", setup);
    }

    private static JSONObject fn(String name, String what, String arg, String argWhat) throws Exception {
        return new JSONObject().put("name", name).put("description", what)
                .put("parameters", new JSONObject().put("type", "OBJECT")
                        .put("properties", new JSONObject().put(arg, new JSONObject().put("type", "STRING").put("description", argWhat)))
                        .put("required", new JSONArray().put(arg)));
    }

    private static String system(List<String> apps) {
        String now = new java.text.SimpleDateFormat("EEEE d MMMM yyyy, h:mm a", java.util.Locale.ENGLISH).format(new java.util.Date());
        return "You are J.A.R.V.I.S, the voice assistant of THALAIVAR TV, an Android TV in Chennai owned by a big fan of "
                + "Superstar Rajinikanth. Call the owner \"Thalaiva\" now and then, warmly, like a fellow fan.\n"
                + "It is " + now + " in India.\n"
                + "RULES:\n"
                + "1. Answer exactly the question that was asked, first and directly, in one or two short spoken sentences. "
                + "No small talk, no unrelated facts, no follow-up questions unless something is unclear.\n"
                + "2. Asked for a dialogue, a fact or an opinion: say it. Never play or open something instead of answering.\n"
                + "3. Use a tool only when the owner asks to play, watch, listen to, open or show something. Then call the tool "
                + "and confirm in a few words.\n"
                + "4. Reply in the owner's language: English gets English; Tamil or Tanglish gets natural Chennai Tanglish.\n"
                + "5. Any question about a Rajinikanth film (director, music, heroine, villain, cast, roles, release, story): "
                + "call film_facts first and answer only from what it returns. Do not web-search for film facts.\n"
                + "6. Search the web only for news, box office, scores or anything recent. Never guess a fact.\n"
                + "Apps on this TV: " + String.join(", ", apps) + ".";
    }

    /** Reads every message of one connection until it closes. */
    private static void listen(Socket mine) {
        try {
            DataInputStream in = new DataInputStream(mine.getInputStream());
            while (true) handle(new JSONObject(read(in)));
        } catch (Exception e) {
            Log.i(TAG, "connection ended: " + e.getMessage());
        }
        synchronized (LiveJarvis.class) {
            if (socket != mine) return;
            close();
            Listener l = current;
            if (answering && l != null) {
                answering = false;
                current = null;
                String soFar = said.toString().trim();
                MAIN.post(() -> {
                    if (soFar.isEmpty()) l.failed();
                    else l.done(soFar);
                });
            }
        }
    }

    private static void handle(JSONObject m) throws Exception {
        Listener l = current;
        JSONObject tc = m.optJSONObject("toolCall");
        if (tc != null) {
            JSONArray calls = tc.optJSONArray("functionCalls");
            JSONArray answers = new JSONArray();
            for (int i = 0; calls != null && i < calls.length(); i++) {
                JSONObject call = calls.getJSONObject(i);
                String name = call.optString("name");
                JSONObject args = call.optJSONObject("args") == null ? new JSONObject() : call.optJSONObject("args");
                String result = "Done on the TV.";
                if ("film_facts".equals(name)) {
                    String f = appContext == null ? null : FilmAnswer.facts(appContext, args.optString("title"));
                    result = f == null ? "That film isn't in the TV's database." : f;
                } else if (l != null && !skipOld) {
                    MAIN.post(() -> l.tool(name, args));
                }
                answers.put(new JSONObject().put("id", call.optString("id")).put("name", name)
                        .put("response", new JSONObject().put("result", result)));
            }
            send(new JSONObject().put("toolResponse", new JSONObject().put("functionResponses", answers)));
            return;
        }
        if (m.has("goAway")) {
            // the server will close this connection soon: the next question opens a fresh one
            ready = false;
            return;
        }
        JSONObject sc = m.optJSONObject("serverContent");
        if (sc == null) return;
        if (sc.optBoolean("interrupted")) {
            // the old answer was cut off by the new question: what follows belongs to the new one
            skipOld = false;
            flushAudio();
            return;
        }
        JSONObject turn = sc.optJSONObject("modelTurn");
        JSONArray parts = turn == null ? null : turn.optJSONArray("parts");
        for (int i = 0; parts != null && i < parts.length(); i++) {
            JSONObject data = parts.getJSONObject(i).optJSONObject("inlineData");
            if (data != null && !skipOld && speakAloud) play(Base64.decode(data.optString("data"), Base64.DEFAULT));
        }
        JSONObject tr = sc.optJSONObject("outputTranscription");
        if (tr != null && !skipOld) {
            said.append(tr.optString("text"));
            String soFar = said.toString().trim();
            if (l != null) MAIN.post(() -> l.words(soFar));
        }
        if (sc.optBoolean("turnComplete")) {
            if (skipOld) {
                // the old answer finished before the new question was taken up
                skipOld = false;
                return;
            }
            answering = false;
            String all = said.toString().trim();
            if (l != null) MAIN.post(() -> l.done(all));
        }
    }

    // ── audio ─────────────────────────────────────────────────────────────────────────────────

    private static void play(byte[] pcm) {
        synchronized (AUDIO) {
        if (track == null) {
            track = new AudioTrack.Builder()
                    .setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANT)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                    .setAudioFormat(new AudioFormat.Builder().setSampleRate(RATE).setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .setBufferSizeInBytes(Math.max(AudioTrack.getMinBufferSize(RATE, AudioFormat.CHANNEL_OUT_MONO,
                            AudioFormat.ENCODING_PCM_16BIT), RATE))
                    .build();
        }
        if (track.getPlayState() != AudioTrack.PLAYSTATE_PLAYING) track.play();
        // the other voice (a kept phrase) never talks over this one
        Voice.stop();
        track.write(pcm, 0, pcm.length);
        }
    }

    private static void flushAudio() {
        synchronized (AUDIO) {
            if (track == null) return;
            try {
                track.pause();
                track.flush();
            } catch (IllegalStateException ignored) {
                // not started yet
            }
        }
    }

    // ── a minimal WebSocket client (RFC 6455), so the app needs no extra library ──────────────

    private static void open() throws IOException {
        socket = javax.net.ssl.SSLSocketFactory.getDefault().createSocket(HOST, 443);
        socket.setSoTimeout(0);
        out = socket.getOutputStream();
        byte[] nonce = new byte[16];
        new SecureRandom().nextBytes(nonce);
        String req = "GET /ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent?key="
                + BuildConfig.GEMINI_KEY + " HTTP/1.1\r\nHost: " + HOST + "\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n"
                + "Sec-WebSocket-Key: " + Base64.encodeToString(nonce, Base64.NO_WRAP) + "\r\nSec-WebSocket-Version: 13\r\n\r\n";
        out.write(req.getBytes(StandardCharsets.US_ASCII));
        out.flush();
        // the reply's header lines, byte by byte, so no frame data is read past them
        java.io.InputStream in = socket.getInputStream();
        StringBuilder head = new StringBuilder();
        while (!head.toString().endsWith("\r\n\r\n")) {
            int b = in.read();
            if (b < 0 || head.length() > 8192) throw new EOFException("no handshake");
            head.append((char) b);
        }
        if (!head.toString().startsWith("HTTP/1.1 101")) throw new IOException("handshake: " + head.toString().split("\r\n", 2)[0]);
    }

    private static void send(JSONObject msg) throws IOException {
        byte[] data = msg.toString().getBytes(StandardCharsets.UTF_8);
        sendFrame(0x1, data);
    }

    private static void sendFrame(int opcode, byte[] data) throws IOException {
        OutputStream o = out;
        if (o == null) throw new IOException("not connected");
        ByteArrayOutputStream f = new ByteArrayOutputStream(data.length + 14);
        f.write(0x80 | opcode);
        if (data.length < 126) {
            f.write(0x80 | data.length);
        } else if (data.length < 65536) {
            f.write(0x80 | 126);
            f.write(data.length >> 8);
            f.write(data.length);
        } else {
            f.write(0x80 | 127);
            for (int i = 7; i >= 0; i--) f.write((int) ((long) data.length >> (8 * i)));
        }
        byte[] mask = new byte[4];
        new SecureRandom().nextBytes(mask);
        f.write(mask, 0, 4);
        for (int i = 0; i < data.length; i++) f.write(data[i] ^ mask[i & 3]);
        synchronized (WRITE) {
            o.write(f.toByteArray());
            o.flush();
        }
    }

    /** One whole message (text or binary frames, joined), answering pings on the way. */
    private static String read(DataInputStream in) throws IOException {
        ByteArrayOutputStream msg = new ByteArrayOutputStream();
        while (true) {
            int b0 = in.readUnsignedByte(), b1 = in.readUnsignedByte();
            long len = b1 & 0x7F;
            if (len == 126) len = in.readUnsignedShort();
            else if (len == 127) len = in.readLong();
            if (len > 16_000_000) throw new IOException("frame too large");
            byte[] mask = null;
            if ((b1 & 0x80) != 0) {
                mask = new byte[4];
                in.readFully(mask);
            }
            byte[] data = new byte[(int) len];
            in.readFully(data);
            if (mask != null) for (int i = 0; i < data.length; i++) data[i] ^= mask[i & 3];
            int op = b0 & 0x0F;
            if (op == 0x8) {
                String why = data.length > 2 ? new String(data, 2, data.length - 2, StandardCharsets.UTF_8) : "";
                throw new EOFException("closed " + (data.length >= 2 ? ((data[0] & 0xFF) << 8 | (data[1] & 0xFF)) : 0) + " " + why);
            }
            if (op == 0x9) {
                sendFrame(0xA, data);
                continue;
            }
            if (op == 0xA) continue;
            msg.write(data, 0, data.length);
            if ((b0 & 0x80) != 0) return msg.toString("UTF-8");
        }
    }

    private static void close() {
        ready = false;
        try {
            if (socket != null) socket.close();
        } catch (IOException ignored) {
            // already closed
        }
        socket = null;
        out = null;
    }

    private LiveJarvis() {
    }
}
