package tv.thalaivar.hub;

import java.util.HashMap;
import java.util.Map;

/** Tamil first, English on request: every label the app shows, in both languages. */
final class Lang {
    private static final Map<String, String[]> T = new HashMap<>();

    private static void p(String key, String ta, String en) {
        T.put(key, new String[]{ta, en});
    }

    static {
        p("brand_tag", "சூப்பர்ஸ்டார் • தி லெஜண்ட் • கொண்டாட்டம்", "SUPERSTAR • THE LEGEND • THE CELEBRATION");
        p("welcome", "தலைவரின் உலகத்திற்கு வரவேற்கிறோம்", "Welcome to Thalaivar's world");
        p("morning", "காலை வணக்கம், தலைவா!", "Good morning, Thalaiva!");
        p("afternoon", "மதிய வணக்கம், தலைவா!", "Good afternoon, Thalaiva!");
        p("evening", "மாலை வணக்கம், தலைவா!", "Good evening, Thalaiva!");
        p("night", "இனிய இரவு, தலைவா!", "Good night, Thalaiva!");
        p("chennai", "சென்னை", "Chennai");
        p("world", "தலைவரின் உலகம்", "Thalaivar's world");
        p("movies", "தலைவரின் திரைப்படங்கள்", "Thalaivar movies");
        p("filmography", "முழு திரைப்பட பட்டியல்", "Complete filmography");
        p("moments", "மறக்க முடியாத தருணங்கள்", "Iconic moments");
        p("celebration", "ரசிகர்களின் கொண்டாட்டம்", "Fan celebration");
        p("news", "புதிய செய்திகள்", "Latest news");
        p("songs", "திரைப்படப் பாடல்கள்", "Film songs");
        p("dialogues", "தலைவரின் வசனங்கள்", "Thalaivar dialogues");
        p("favourites", "பிடித்தவை", "Favourites");
        p("special", "சிறப்பு மண்டலம்", "Special zones");
        p("apps", "அனைத்து செயலிகள்", "All apps");
        p("entertainment", "பொழுதுபோக்கு", "Entertainment");
        p("music", "இசை", "Music");
        p("live_tv", "நேரலை டிவி", "Live TV");
        p("games", "விளையாட்டுகள்", "Games");
        p("tools", "கருவிகள்", "Tools");
        p("wallpapers", "சுவர்ப்படங்கள்", "Wallpapers");
        p("screensaver", "திரைக்காப்பு", "Screensaver");
        p("settings", "அமைப்புகள்", "Settings");
        p("featured", "இன்றைய சிறப்பு", "Featured today");
        p("trailer", "டிரெய்லர்", "Trailer");
        p("watch_songs", "பாடல்கள்", "Songs");
        p("details", "விவரங்கள்", "Details");
        p("favourite", "பிடித்தது", "Favourite");
        p("unfavourite", "பிடித்ததிலிருந்து நீக்கு", "Remove favourite");
        p("set_wall", "திரைக்காப்பில் சேர்", "Add to screensaver");
        p("watch_on", "இதில் பார்க்க", "Watch on");
        p("not_streaming", "இந்தியாவில் ஸ்ட்ரீமிங் தகவல் இல்லை", "No streaming listing in India");
        p("director", "இயக்குநர்", "Director");
        p("music_by", "இசை", "Music");
        p("cast", "நடிகர்கள்", "Cast");
        p("released", "வெளியீடு", "Released");
        p("videos", "அதிகாரப்பூர்வ வீடியோக்கள்", "Official videos");
        p("stills", "திரைப்படக் காட்சிகள்", "Film stills");
        p("loading", "ஏற்றுகிறது…", "Loading…");
        p("offline", "இணைய இணைப்பு இல்லை", "No internet connection");
        p("no_token", "TMDB டோக்கன் இல்லை: secrets.properties-ஐ நிரப்பவும்", "No TMDB token: fill in secrets.properties");
        p("baasha_title", "பாட்ஷா", "BAASHA");
        p("baasha_sub", "என்றும் மாறாத சக்கரவர்த்தி", "THE EVERGREEN EMPEROR");
        p("baasha_saver", "பாட்ஷா திரைக்காப்பு", "Baasha screensaver");
        p("jailer2", "ஜெயிலர் 2", "JAILER 2");
        p("jailer2_sub", "முத்துவேல் பாண்டியன் திரும்பி வந்தார்", "MUTHUVEL PANDIAN RETURNS");
        p("box_office", "வசூல் நிலவரம்", "Box office");
        p("reported", "தகவல்", "REPORTED");
        p("confirmed", "உறுதி", "CONFIRMED");
        p("rumour", "வதந்தி", "RUMOUR");
        p("auto_label", "வகை தானாக மதிப்பிடப்பட்டது", "Category is estimated automatically");
        p("trade_estimate", "வணிக மதிப்பீடு – அதிகாரப்பூர்வ எண் அல்ல", "Trade estimate – not an official figure");
        p("source", "ஆதாரம்", "Source");
        p("updated", "புதுப்பிக்கப்பட்டது", "Updated");
        p("saved_news", "சேமித்த செய்திகள்", "Saved news from");
        p("refresh", "புதுப்பி", "Refresh");
        p("all", "அனைத்தும்", "All");
        p("fan_wall", "ரசிகர் சுவர்", "Fan wall");
        p("release_info", "வெளியீட்டு விவரம்", "Release information");
        p("opens_youtube", "அதிகாரப்பூர்வ YouTube தேடல்", "Opens an official YouTube search");
        p("language", "மொழி", "Language");
        p("animation", "அனிமேஷன்", "Animation");
        p("auto", "தானியங்கி", "Auto");
        p("low", "குறைவு", "Low");
        p("medium", "நடுத்தரம்", "Medium");
        p("high", "அதிகம்", "High");
        p("rotation", "சுவர்ப்பட மாற்ற இடைவெளி", "Wallpaper change interval");
        p("minutes", "நிமிடம்", "min");
        p("seconds", "விநாடி", "sec");
        p("clear_cache", "சேமிப்பை அழி", "Clear saved data");
        p("tv_saver_settings", "டிவி திரைக்காப்பு அமைப்பு", "TV screensaver settings");
        p("start_saver", "திரைக்காப்பை இப்போது தொடங்கு", "Start screensaver now");
        p("not_installed", "இந்த டிவியில் நிறுவப்படவில்லை", "isn't installed on this TV");
        p("pin", "முகப்பில் பொருத்து", "Pin to hub");
        p("unpin", "முகப்பிலிருந்து நீக்கு", "Unpin from hub");
        p("my_dock", "என் செயலிகள்", "My apps");
        p("attribution", "இந்த செயலி TMDB API-ஐப் பயன்படுத்துகிறது; TMDB அங்கீகரிக்கவில்லை.",
                "This product uses the TMDB API but is not endorsed or certified by TMDB.");
        p("news_attr", "செய்திகள்: Google News RSS வழியாக வெளியீட்டாளர்களிடமிருந்து", "Headlines: publishers via Google News RSS");
        p("favourite_hint", "சரி: பிடித்தது  •  நீண்ட அழுத்தம்: முகப்பு", "OK: favourite  •  hold OK: details");
        p("m_entry", "மாஸ் என்ட்ரி தருணங்கள்", "Iconic entry moments");
        p("m_style", "ஸ்டைல் & ஸ்வாக்", "Style and swag");
        p("m_punch", "பஞ்ச் வசனங்கள்", "Punch dialogues");
        p("m_comedy", "நகைச்சுவை தருணங்கள்", "Comedy moments");
        p("m_emotion", "உணர்ச்சிகரமான காட்சிகள்", "Emotional scenes");
        p("m_action", "ஆக்ஷன் தருணங்கள்", "Action moments");
        p("m_interview", "நேர்காணல்கள்", "Interview moments");
        p("m_public", "பொது நிகழ்வுகள்", "Public appearances");
        p("m_audio", "இசை வெளியீட்டு விழாக்கள்", "Audio launch moments");
        p("m_fans", "ரசிகர் கொண்டாட்டங்கள்", "Fan celebration moments");
        p("m_history", "வரலாற்று வெற்றிக் கொண்டாட்டங்கள்", "Historic movie celebrations");
    }

    /** English by default; Tamil only if the owner picks it in Settings. */
    static boolean tamil = false;

    static String t(String key) {
        String[] v = T.get(key);
        if (v == null) return key;
        return tamil ? v[0] : v[1];
    }

    /** A row heading in the current language only. */
    static String both(String key) {
        return t(key);
    }

    private Lang() {
    }
}
