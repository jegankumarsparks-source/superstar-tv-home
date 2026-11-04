# THALAIVAR TV – Superstar Edition

SUPERSTAR • THE LEGEND • THE CELEBRATION. A Rajinikanth fan experience built on Clean Your TV: the launcher
with a THALAIVAR theme, plus the Thalaivar Hub add-on for everything live. This is a launcher and
experience transformation; the TV's operating system, firmware, Netflix certification, DRM, Play Store
and system settings are untouched, and nothing is disabled or uninstalled.

## How it is built (and why)

| Need | Where it lives | Why there |
|---|---|---|
| Gold/black look, greeting, dock, focus, clock screensaver, time-of-day wallpapers | `themes/thalaivar/` (config keys only) | The launcher reads themes as local files; no rebuild |
| Films, trailers, news, zones, apps hub, wallpaper library, screensavers | `addons/thalaivar-hub/` (module card app) | The launcher has no internet permission by design, so live features must be an add-on |
| Tamil interface for the launcher (optional) | `launcher/app/src/main/res/values-ta/` | A launcher translation, as the translation workflow describes |
| Chennai sunrise/sunset for the wallpaper moods | `launcher/.../values/config.xml` `latitude`, `longitude` | Only config.xml carries the location |
| Original artwork | `tools/thalaivar/make_art.py` → `themes/thalaivar/` | Generated, so the repo ships no film stills or photos |
| Personal 3D poster and portrait wallpapers | `tools/thalaivar/poster_walls.py` → `themes-local/` (git-ignored) | Studio artwork from TMDB, for your own TV only |
| Install, backup, restore, make Home | `tools/thalaivar/*.ps1` | Reversible, Home never changed without you |

## Install

Needs: Windows with Python 3, JDK 17+ and the Android SDK (platform 34), the TV on the same network with
Network/ADB debugging on, and a free TMDB API Read Access Token in `addons/thalaivar-hub/secrets.properties`.

```powershell
# optional: your personal 3D poster wallpapers (saved photos of your own in themes-local\my-photos\ are added too)
python -I tools\thalaivar\poster_walls.py

powershell -ExecutionPolicy Bypass -File tools\thalaivar\install.ps1 -TvHost 192.168.1.50
#   -Motion low|medium|high   launcher animation level      -OriginalArtOnly   skip the poster wallpapers
```

The installer: checks the tools, probes the TV (model, Android version, API 26+), **backs up** Home, the
package and disabled lists and the screensaver/accessibility settings to `backup\thalaivar-<time>\`, builds,
installs both apps **alongside** your current Home (and puts Home back if Android changed it), pushes the
theme, and verifies that both apps are installed and the hub starts.

Make THALAIVAR TV your Home only after trying it (it asks you to type YES, and never disables the stock
launcher):

```powershell
powershell -ExecutionPolicy Bypass -File tools\thalaivar\make-home.ps1 -TvHost 192.168.1.50
```

## Restore / uninstall

```powershell
powershell -ExecutionPolicy Bypass -File tools\thalaivar\restore.ps1 -TvHost 192.168.1.50            # everything back
powershell -ExecutionPolicy Bypass -File tools\thalaivar\restore.ps1 -TvHost 192.168.1.50 -KeepApps  # Home and settings only
powershell -ExecutionPolicy Bypass -File tools\thalaivar\restore.ps1 -TvHost 192.168.1.50 -DryRun    # show what it would do
```

It points Home back to the recorded launcher, restores the screensaver and accessibility settings, and
uninstalls only `tv.thalaivar.hub` and `com.gathin.tvhome` (unless they were on the TV before). The theme
alone: `tools\theme.ps1 -TvHost <ip> -Reset`.

## Features

See [addons/thalaivar-hub/README.md](../addons/thalaivar-hub/README.md) for the hub and
[themes/thalaivar/README.md](../themes/thalaivar/README.md) for the theme. In short: logo reveal, Chennai clock,
welcome, featured film, 17 lead films plus the full filmography, film pages with director / music / cast /
official videos / songs / streaming apps / favourites, the BAASHA and JAILER 2 zones, 11 iconic-moment
categories, live news with source, time and confirmed/reported/rumour badges, the apps hub, the wallpaper
library, two screensavers, and Low / Medium / High animation.

Content rules the app follows: artwork comes from TMDB at run time with attribution, videos open in the
YouTube app from official uploads or YouTube searches, news comes from publishers' RSS headlines, and the
box-office figure is shown with its source, its date and a "reported trade estimate" label. Nothing is
downloaded and redistributed.

## Device compatibility

| | |
|---|---|
| Android | 8.0 (API 26) or newer: Android TV, Google TV, Fire TV (launcher Home rules differ on Fire TV, see docs/launcher.md) |
| Memory | Auto animation: Low under 1.2 GB or on low-RAM devices, Medium up to 3 GB, High above. Choose in the hub's Settings |
| Screen | Laid out in dp for 720p and 1080p UI; artwork is 1920×1080. No 4K or HDR claim: pictures are 1080p |
| Network | Hub works offline with saved news and its own art; films need TMDB once to cache |
| Tested | MOKA R3G (Google TV, Android 14, 1.5 GB, 1080p output with a 720p UI) |

## Test report (4 November 2026, MOKA R3G, Android 14)

Keys were injected over ADB (`input keyevent`); a few features need the real remote or more time.

| Check | Result | Evidence / note |
|---|---|---|
| Theme installs | PASS | `theme.ps1` "applied"; launcher showed GOOD AFTERNOON, THALAIVA and the rotating poster wallpaper with its caption |
| Existing apps remain available | PASS | Nothing disabled or removed; Google TV home and every app still there; Home unchanged after install |
| Home screen renders | PASS | Hub: header, Chennai clock and date, featured backdrop, poster row, special zones, news row; launcher with theme |
| Startup animation | PASS | Logo reveal with the gold star and shine on opening the hub |
| Remote navigation | PASS (injected keys) | D-pad and OK/Back through hub → film page → back → Baasha zone → Jailer 2 zone → moments → settings → news, focus restored on Back. Real-remote check: DEVICE REQUIRED |
| Wallpaper rotation | PARTIAL | Collection and captions on the TV; a change over the 20-minute interval was not watched |
| Screensaver | PASS | Both open from the hub: film still, Chennai clock and date, gold particles; Baasha adds rays and confetti. Both are registered as TV screensavers. Picture change over time not seen within 50 s (Baasha has few stills on TMDB). Choosing one in TV Settings > Screen saver: DEVICE REQUIRED |
| Movie sections | PASS | Film pages with director, music director, cast, runtime, genres, rating, poster and backdrop |
| News sources | PASS | Live headlines from NewsBytes, Udayavani, The Times of India and others, each with source, age and badge |
| Video embeds open | NOT TESTED | No trailer was opened (Jailer 2 has none on TMDB yet); songs/moments open YouTube search |
| Animations responsive | PASS (observed) | No lag seen while walking the rows; no benchmark run |
| Text renders correctly | PASS | English (Cinzel, Bebas Neue, Montserrat) by default; Tamil checked on an earlier build |
| Restore process | PARTIAL | `restore.ps1 -DryRun` lists the right steps; not run for real (it would remove the apps) |
| No critical crashes | PASS | No crash during the walks |
| Performance acceptable | PASS (observed) | Auto chose Low at first on 1.5 GB; threshold lowered to 1.2 GB so this TV gets Medium (3D tilt on) |

Not done in this pass: the theme's required `screenshot.jpg` (needs a capture of the original-art theme on
the TV), and a real-remote press of each key.
