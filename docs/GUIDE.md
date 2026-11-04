# Superstar TV Home – the complete guide

Everything from a fresh TV to a finished THALAIVAR TV, for every Android TV brand. Takes about 20 minutes the
first time (most of it is the first build downloading its tools).

- [What you need](#what-you-need)
- [1. Turn on debugging on your TV](#1-turn-on-debugging-on-your-tv)
- [2. Get your free TMDB token](#2-get-your-free-tmdb-token)
- [3. Prepare the PC](#3-prepare-the-pc)
- [4. Install](#4-install)
- [5. First run](#5-first-run)
- [6. Make it your Home (optional)](#6-make-it-your-home-optional)
- [7. Personalise](#7-personalise)
- [Undo / uninstall](#undo--uninstall)
- [Troubleshooting](#troubleshooting)
- [FAQ](#faq)

## What you need

| | |
|---|---|
| A TV | Android TV, Google TV or Fire TV, **Android 8.0 or newer**, on your home Wi-Fi |
| A PC | Windows 10 or 11 on the same Wi-Fi (macOS/Linux: see the FAQ) |
| Accounts | A free TMDB account for the film data. No Google account changes, no payments |

## 1. Turn on debugging on your TV

Debugging lets your PC install apps on the TV over Wi-Fi. You turn it on once.

**Google TV / Android TV** (Sony, TCL, Hisense, Philips, Xiaomi Mi TV, OnePlus, Realme, Vu, Acer, Nokia,
Kodak, Thomson, iFFALCON, Chromecast with Google TV, Google TV Streamer, NVIDIA Shield, Mi Box…)

1. **Settings → System → About** (on some sets **Settings → Device Preferences → About**).
2. Select **Android TV OS build** (or **Build**) **seven times** until it says *You are now a developer*.
3. Back to **Settings → System → Developer options** (or **Device Preferences → Developer options**).
4. Turn on **USB debugging**, and **Network debugging / ADB over network** if your TV has it.
5. Note the TV's **IP address**: **Settings → Network & Internet → your Wi-Fi**.

**Brand notes**

| Brand | Where it differs |
|---|---|
| Xiaomi / Mi TV (PatchWall) | Settings → Device Preferences → About → Build ×7; Developer options under Device Preferences |
| TCL / iFFALCON | Settings → System → About → Android TV OS build ×7 |
| Sony Bravia | Settings → System → About → Build ×7 (older: Device Preferences → About) |
| Hisense / Toshiba (Android/Google TV) | Settings → System → About → Build ×7 |
| NVIDIA Shield | Settings → Device Preferences → About → Build ×7; Developer options → Network debugging |
| Chromecast with Google TV / Google TV Streamer | Settings → System → About → Android TV OS build ×7 |

**Fire TV** (Fire TV Stick, Cube, Fire TV Edition / Omni TVs)

1. **Settings → My Fire TV** (newer: **Device & Software**) **→ About**.
2. Select your device's name **seven times** until it says *You are now a developer*.
3. Back one screen → **Developer Options → ADB debugging: On**.
4. IP address: **Settings → Network**, highlight your Wi-Fi and press **Play/Pause**.

> Fire OS never lets another app own the Home button. THALAIVAR TV works as an app there, and Clean Your TV's
> docs describe the Home-button helper if you want more.

## 2. Get your free TMDB token

1. Create a free account at [themoviedb.org](https://www.themoviedb.org/signup) and confirm your email.
2. Open [themoviedb.org/settings/api](https://www.themoviedb.org/settings/api) → **Create** → **personal use**.
3. Fill the short form (application name *Superstar TV Home*, URL `http://localhost`, a one-line summary,
   your own name and address) and submit.
4. Copy the long **API Read Access Token** (it starts with `eyJ`), not the short API key.

`setup.ps1` asks for it (input hidden) and stores it only on your PC, in a git-ignored file.

**Optional keys** (setup asks for each once; press Enter to skip):

| Key | Turns on | Where |
|---|---|---|
| Gemini API key | The J.A.R.V.I.S voice assistant: answers and the natural human voice | [aistudio.google.com](https://aistudio.google.com/apikey) (free tier; answers can take 10–20 s there, a paid key is faster) |
| YouTube Data API key | JK Tube's search and rows inside the app (without it, search opens in your video app) | Google Cloud console → APIs → YouTube Data API v3 → Credentials |

### Talking to J.A.R.V.I.S

- Open **J.A.R.V.I.S** from your apps, or press the remote's mic button (Settings → "J.A.R.V.I.S on the remote's mic button": On).
- **Hold the mic button the whole time you speak.** Voice remotes send sound only while the button is held.
- The remote's voice reaches the TV over **Bluetooth**: if the TV asks you to press Home + Back, do it once to pair the remote. Buttons work without Bluetooth, voice doesn't.
- On some TVs the firmware gives the remote's microphone only to Google's assistant; if J.A.R.V.I.S hears silence, type in its panel, or plug a USB microphone into the TV.
- Instant commands (no waiting): "open Netflix", "Netflix open pannu", "play Jailer 2 trailer", "Anirudh songs podu", "news", "photos", "screensaver".

## 3. Prepare the PC

Open **PowerShell** and install what's missing (one line each):

```powershell
winget install Git.Git
winget install Python.Python.3.12
winget install Microsoft.OpenJDK.17
winget install Google.AndroidStudio
```

Open Android Studio once and let it install the SDK (platform 34). If your SDK is not in the default place,
set `ANDROID_HOME` to it.

## 4. Install

```powershell
git clone https://github.com/jegankumarsparks-source/superstar-tv-home.git
cd superstar-tv-home
powershell -ExecutionPolicy Bypass -File setup.ps1 -TvHost <your TV's IP>
```

Options: `-Latitude 12.97 -Longitude 77.59` for your own city's sunrise and sunset (default Chennai),
`-Motion low` for slower TVs, `-BuildOnly` to build without a TV.

What it does: downloads Clean Your TV from its official repository (at the tested version), adds THALAIVAR TV,
builds both apps, makes your personal poster wallpapers from TMDB, **backs up your TV**, installs the launcher
and the hub **next to your current Home**, pushes the theme and checks that everything starts.

**Watch the TV the first time:** it asks *Allow USB debugging?* → tick **Always allow** → **OK**.

## 5. First run

- Open **Apps → Thalaivar TV**. Arrows move, **OK** opens, **Back** returns, hold **OK** on an app to pin it.
- Try the launcher without changing Home: in the hub's apps row open **Home** (Clean Your TV), or press Home
  after step 6.
- The launcher may ask **"New card from Thalaivar TV"**: choose **Show card** to put the hub on its home screen.
- Screensavers: **TV Settings → System → Ambient mode / Screen saver → Thalaivar TV** or **Baasha**.

## 6. Make it your Home (optional)

```powershell
powershell -ExecutionPolicy Bypass -File cleanyourtv\tools\thalaivar\make-home.ps1 -TvHost <your TV's IP>
```

It asks you to type **YES**, points Home at THALAIVAR TV and never disables your TV's own launcher.
On some Google TVs the setup wizard holds on to Home; the script tells you, and nothing changes.

## 7. Personalise

| Want | How |
|---|---|
| Your own photos in the wallpaper rotation | Put them in `cleanyourtv\themes-local\my-photos\`, run `python -I cleanyourtv\tools\thalaivar\poster_walls.py`, then setup again |
| Calmer or richer animation | Hub → Settings → Animation, or `setup.ps1 -Motion low/high` for the launcher |
| Tamil interface | Hub → Settings → Language → தமிழ் |
| Different wallpaper speed | Hub → Settings → Wallpaper change interval |

## Undo / uninstall

```powershell
powershell -ExecutionPolicy Bypass -File cleanyourtv\tools\thalaivar\restore.ps1 -TvHost <your TV's IP>
```

Puts Home, the screensaver and accessibility settings back as the backup recorded them, and removes only the
two apps THALAIVAR TV added. `-KeepApps` keeps them, `-DryRun` shows what it would do.

## Troubleshooting

| Problem | Fix |
|---|---|
| `could not reach <ip>:5555` | Debugging is off, the IP changed, or the PC is on another network. Turn Network debugging on and check the IP |
| The TV never asked "Allow debugging?" | Press Home on the TV (the dialog can hide behind an app) and run setup again |
| Build fails: SDK not found | Install Android Studio and open it once, or set `ANDROID_HOME` |
| Build fails: licences | Android Studio → SDK Manager → accept the licences |
| Hub shows no films | The TMDB token is missing or wrong: put it in `cleanyourtv\addons\thalaivar-hub\secrets.properties` (`tmdb.token=...`) and run setup again |
| Films or news load slowly / some are empty | Some networks drop connections to TMDB; the hub retries and caches. Open the page again |
| Home still opens the old launcher | That is by design until you run `make-home.ps1` |
| A remote button does nothing in the hub | Use the arrows and OK; tell us your TV model in an issue |

## FAQ

**Does it root or modify my TV?** No. It installs two ordinary apps and a theme, and backs up first.

**Will Netflix / Prime / Hotstar still work?** Yes. Nothing is disabled; DRM and certification are untouched.

**Is it free?** Yes, MIT-licensed. TMDB is free for personal use.

**macOS or Linux?** `setup.ps1` is for Windows today. On macOS/Linux, follow Clean Your TV's README to build
and install the launcher, build `overlay/addons/thalaivar-hub` like any Clean Your TV add-on, and push
`overlay/themes/thalaivar` with `tools/theme.sh`.

**Why are posters not in this repository?** They belong to the studios. The hub shows them on your TV from
TMDB, and `poster_walls.py` makes your personal poster wallpapers on your own PC, never committed or shared.
