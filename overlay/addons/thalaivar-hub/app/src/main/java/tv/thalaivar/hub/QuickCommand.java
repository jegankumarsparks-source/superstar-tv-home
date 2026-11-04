package tv.thalaivar.hub;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The commands J.A.R.V.I.S understands on the TV itself, in English and Tanglish, so they happen at once
 * instead of waiting for Gemini: "open Netflix", "Netflix open pannu", "play Jailer 2 trailer",
 * "Anirudh songs podu", "news", "photos", "screensaver". Anything else is a question for Gemini.
 */
final class QuickCommand {
    final String action, query, say, voice;

    private QuickCommand(String action, String query, String say, String voice) {
        this.action = action;
        this.query = query;
        this.say = say;
        this.voice = voice;
    }

    /** The spoken confirmations: a fixed few, kept on the TV in the human voice, so they play the instant the command runs. */
    static final String NEWS = "Latest news, coming right up, Thalaiva!", PHOTOS = "Here are the photos, Thalaiva!",
            SAVER = "Screensaver on, Thalaiva!", TUBE = "JK Tube is ready, Thalaiva!", PLAY = "Playing it now, Thalaiva!",
            OPEN = "Opening it now, Thalaiva!";
    /** Said at once while a question goes to Gemini, so J.A.R.V.I.S never sits silent. */
    static final String THINKING = "One moment, Thalaiva!";
    static final String[] PHRASES = {NEWS, PHOTOS, SAVER, TUBE, PLAY, OPEN, THINKING};

    private static final String TAIL = "(?:\\s+(?:pannu|pannunga|panu|podu|pottu|poodu|please|now|da|machi|venum))*\\s*[.!?]*$";
    private static final Pattern OPEN_FIRST = Pattern.compile("^(?:please\\s+)?(?:open|launch|start|go to|show me|show)\\s+(?:the\\s+)?(.+?)(?:\\s+app)?" + TAIL);
    private static final Pattern OPEN_LAST = Pattern.compile("^(.+?)(?:\\s+app)?\\s+(?:open|launch|start|thira|thiranthu)" + TAIL);
    private static final Pattern PLAY_FIRST = Pattern.compile("^(?:please\\s+)?(?:play|put on|watch)\\s+(.+?)" + TAIL);
    private static final Pattern PLAY_LAST = Pattern.compile("^(.+?)\\s+(?:play|potu|podu|pottu)" + TAIL);

    static QuickCommand parse(String spoken) {
        if (spoken == null) return null;
        String s = spoken.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
        if (s.isEmpty() || s.endsWith("?") || s.startsWith("what") || s.startsWith("who") || s.startsWith("why")
                || s.startsWith("how") || s.startsWith("when") || s.startsWith("where") || s.startsWith("tell me")) return null;
        if (s.matches("(?:show |open )?(?:the )?(?:latest )?(?:thalaivar |rajini |rajinikanth )?news" + TAIL))
            return new QuickCommand("news", "", "Here's the latest news.", NEWS);
        if (s.matches("(?:show |open )?(?:the )?(?:thalaivar |rajini )?(?:photos?|pictures?|gallery)" + TAIL))
            return new QuickCommand("photos", "", "Opening the photos.", PHOTOS);
        if (s.matches("(?:start |open )?(?:the )?screen ?saver" + TAIL))
            return new QuickCommand("screensaver", "", "Starting the screensaver.", SAVER);
        if (s.matches("(?:open )?jk ?tube" + TAIL)) return new QuickCommand("tube", "", "Opening JK Tube.", TUBE);
        Matcher m = PLAY_FIRST.matcher(s);
        if (m.matches()) return new QuickCommand("play", m.group(1), "Playing " + m.group(1) + ".", PLAY);
        m = PLAY_LAST.matcher(s);
        if (m.matches()) return new QuickCommand("play", m.group(1), "Playing " + m.group(1) + ".", PLAY);
        m = OPEN_FIRST.matcher(s);
        if (m.matches()) return new QuickCommand("open_app", m.group(1), "Opening " + m.group(1) + ".", OPEN);
        m = OPEN_LAST.matcher(s);
        if (m.matches()) return new QuickCommand("open_app", m.group(1), "Opening " + m.group(1) + ".", OPEN);
        return null;
    }
}
