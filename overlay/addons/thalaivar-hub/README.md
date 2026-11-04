# Thalaivar Hub

THALAIVAR TV's fan world as a module card: a cinematic hub for Superstar Rajinikanth's films, two
celebration zones (BAASHA – THE EVERGREEN EMPEROR and JAILER 2), iconic moments, a live news engine, an
apps hub, a wallpaper library and two screensavers. Unofficial fan app; English by default, Tamil optional.

## What it does

| Part | What you get |
|---|---|
| Hub | Logo reveal, Chennai clock and date, time-of-day welcome, a featured film whose backdrop crossfades every 12 s, rows for the films, special zones, news, your apps and the complete filmography |
| Films | 17 lead films (Thalapathi to Jailer 2) and every film TMDB lists for him. Each page: backdrop, 3D poster, year, runtime, genres, director, music director, cast, official trailers and clips (YouTube), songs (YouTube search), the streaming apps TMDB lists in India that are installed, favourite, add a still to the screensaver |
| BAASHA zone | Black-and-gold title, rotating stage-light rays and confetti (original drawing), fan wall, trailer, songs, details, the Baasha screensaver |
| JAILER 2 zone | Release date, box office with source, date and a "reported trade estimate" label, cast and crew, official videos, Jailer 2 news |
| Iconic moments | 11 categories (entry, style, punch dialogues, comedy, emotional, action, interviews, public appearances, audio launches, fan celebrations, historic celebrations), each a YouTube search |
| News | Google News RSS for Rajinikanth, Jailer 2, Jailer 2 box office, Dharman and the KH x RK film; publisher and age on every headline; a CONFIRMED / REPORTED / RUMOUR badge estimated from the source and wording; cached 30 min; offline shows the saved copy with its time |
| Apps | Every installed app sorted into Entertainment, Music, Live TV, Games and Tools; OK opens, hold OK pins it to the hub. Nothing is hidden, disabled or removed |
| Wallpapers and screensavers | Each film's stills, star the ones you like; "Thalaivar TV" and "Baasha – The Evergreen Emperor" screensavers (TV Settings > Screen saver): crossfade, slow zoom, clock and date overlay, no picture twice in a row, interval 1 to 30 min |
| Settings | English / Tamil, animation Auto / Low / Medium / High (Auto picks from the TV's memory), wallpaper interval, clear cache |

All artwork shown in the app (posters, backdrops, stills, portraits) is loaded from TMDB at run time and
is not part of this repository; videos open in the YouTube app. The app's own art (logo, banner, the
four offline backdrops in `assets/art/`) is original, drawn by `tools/thalaivar/make_art.py`.

## Build and install

It needs a free TMDB API Read Access Token (themoviedb.org/settings/api). Put it in
`addons/thalaivar-hub/secrets.properties`, which git ignores:

```properties
tmdb.token=eyJ...
region=IN
```

Then use the THALAIVAR TV installer, which also backs up the TV and pushes the theme
(see [docs/thalaivar-tv.md](../../docs/thalaivar-tv.md)):

```powershell
powershell -ExecutionPolicy Bypass -File tools\thalaivar\install.ps1 -TvHost 192.168.1.50
```

or build and install this add-on alone, as for any add-on (addons/README.md).

## Permissions

- `INTERNET`: TMDB (film facts and artwork) and Google News RSS (headlines). Nothing else is contacted,
  and no analytics or telemetry are sent.
- `ACCESS_NETWORK_STATE`: to show saved news with its time instead of trying while the TV is offline.
- Two screensavers (`BIND_DREAM_SERVICE`, held by the system, not a permission the app asks for).
- No background service, no start at boot, no accessibility service.

## Tested on

MOKA R3G Google TV stick, Android 14 (API 34), 1.5 GB RAM, 1080p output at 720p UI.

## Credits

Film data and images: TMDB. *This product uses the TMDB API but is not endorsed or certified by TMDB.*
Headlines: their publishers, via Google News RSS. Fonts: Cinzel, Bebas Neue, Montserrat (SIL Open Font
License, texts in `app/src/main/assets/`).
