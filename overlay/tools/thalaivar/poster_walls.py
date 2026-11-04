"""Personal-use 3D poster wallpapers for THALAIVAR TV, built on this computer from TMDB's official artwork.

The posters and backdrops belong to their studios. TMDB serves them for personal, non-commercial use with
attribution, so this script writes them only to a git-ignored folder (themes-local/thalaivar-posters/)
that is pushed to your own TV. Never commit or share its output.

    TMDB_TOKEN=... python -I tools/thalaivar/poster_walls.py        # or put the token in
                                                                    # addons/thalaivar-hub/secrets.properties
"""
import json
import os
import shutil
import sys
import time
import urllib.parse
import urllib.request

import numpy as np
from PIL import Image, ImageDraw, ImageEnhance, ImageFilter, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
FONTS = os.path.join(HERE, "fonts")
OUT = os.path.join(ROOT, "themes-local", "thalaivar-posters")
CACHE = os.path.join(ROOT, "themes-local", ".tmdb-cache")
THEME = os.path.join(ROOT, "themes", "thalaivar")
W, H = 1920, 1080
GOLD, GOLD_HI = (212, 175, 55), (255, 222, 120)

# The films, as the hub lists them: (title as TMDB knows it, year, file stem)
FILMS = [
    ("Baasha", 1995, "baasha"), ("Padayappa", 1999, "padayappa"), ("Muthu", 1995, "muthu"),
    ("Annamalai", 1992, "annamalai"), ("Thalapathi", 1991, "thalapathi"), ("Sivaji", 2007, "sivaji"),
    ("Enthiran", 2010, "enthiran"), ("Kabali", 2016, "kabali"), ("Kaala", 2018, "kaala"),
    ("Petta", 2019, "petta"), ("Darbar", 2020, "darbar"), ("Annaatthe", 2021, "annaatthe"),
    ("Jailer", 2023, "jailer"), ("Lal Salaam", 2024, "lal-salaam"), ("Vettaiyan", 2024, "vettaiyan"),
    ("Coolie", 2025, "coolie"), ("Jailer 2", 2026, "jailer-2"),
]


def token():
    t = os.environ.get("TMDB_TOKEN", "").strip()
    if t:
        return t
    for path in (os.path.join(ROOT, "addons", "thalaivar-hub", "secrets.properties"),
                 r"D:\halo-tv\tonight\secrets.properties"):
        if os.path.exists(path):
            for line in open(path, encoding="utf-8-sig"):
                if line.startswith("tmdb.token="):
                    return line.split("=", 1)[1].strip()
    sys.exit("No TMDB token: set TMDB_TOKEN or fill addons/thalaivar-hub/secrets.properties")


def fetch(url, headers=None, tries=6):
    """GET with retries: some Indian networks reset about half of the connections to TMDB."""
    last = None
    for i in range(tries):
        try:
            req = urllib.request.Request(url, headers=headers or {})
            with urllib.request.urlopen(req, timeout=25) as r:
                return r.read()
        except Exception as e:  # noqa: BLE001 - every network failure is retried the same way
            last = e
            time.sleep(1.5 * (i + 1))
    raise SystemExit(f"TMDB request failed after {tries} tries: {last}")


def api(path, tok):
    os.makedirs(CACHE, exist_ok=True)
    key = os.path.join(CACHE, urllib.parse.quote(path, safe="") + ".json")
    if os.path.exists(key) and time.time() - os.path.getmtime(key) < 7 * 86400:
        return json.load(open(key, encoding="utf-8"))
    data = json.loads(fetch("https://api.themoviedb.org/3" + path,
                            {"Authorization": "Bearer " + tok, "Accept": "application/json"}))
    json.dump(data, open(key, "w", encoding="utf-8"))
    return data


def image(path, size):
    os.makedirs(CACHE, exist_ok=True)
    key = os.path.join(CACHE, size + path.replace("/", "_"))
    if not os.path.exists(key):
        open(key, "wb").write(fetch(f"https://image.tmdb.org/t/p/{size}{path}"))
    return Image.open(key).convert("RGB")


def find_films(tok):
    people = api("/search/person?query=Rajinikanth", tok)["results"]
    person = max(people, key=lambda p: p.get("popularity", 0))
    credits = api(f"/person/{person['id']}/movie_credits", tok)["cast"]
    found = []
    for title, year, stem in FILMS:
        best = None
        for c in credits:
            names = {c.get("title", "").lower(), c.get("original_title", "").lower()}
            y = int((c.get("release_date") or "0")[:4] or 0)
            if title.lower() in names and (not y or abs(y - year) <= 1):
                best = c
                break
        if not best:
            hits = api("/search/movie?" + urllib.parse.urlencode({"query": title, "year": year}), tok)["results"]
            best = hits[0] if hits else None
        if best:
            found.append((title, year, stem, best["id"]))
        else:
            print(f"  not on TMDB yet: {title} ({year})")
    return found


def pick(images, kind, langs):
    items = [i for i in images.get(kind, []) if i.get("iso_639_1") in langs]
    items = items or images.get(kind, [])
    return max(items, key=lambda i: (i.get("vote_average", 0), i.get("width", 0)))["file_path"] if items else None


def cover(img, w, h):
    s = max(w / img.width, h / img.height)
    img = img.resize((int(img.width * s + 0.5), int(img.height * s + 0.5)), Image.Resampling.LANCZOS)
    x, y = (img.width - w) // 2, (img.height - h) // 2
    return img.crop((x, y, x + w, y + h))


def coeffs(dst, src):
    """PIL's PERSPECTIVE coefficients mapping the dst quad back onto the src quad."""
    m = []
    for (x, y), (u, v) in zip(dst, src):
        m.append([x, y, 1, 0, 0, 0, -u * x, -u * y])
        m.append([0, 0, 0, x, y, 1, -v * x, -v * y])
    a = np.array(m, dtype=np.float64)
    b = np.array([c for p in src for c in p], dtype=np.float64)
    return np.linalg.solve(a, b).tolist()


def tilt(poster, angle_px):
    """Turn the poster on its vertical axis: the right edge recedes, as if the card stands in space."""
    w, h = poster.size
    canvas_w = w + angle_px
    dst = [(0, 0), (w, angle_px * 0.55), (w, h - angle_px * 0.55), (0, h)]
    src = [(0, 0), (w, 0), (w, h), (0, h)]
    rgba = poster.convert("RGBA")
    out = rgba.transform((canvas_w, h), Image.Transform.PERSPECTIVE, coeffs(dst, src), Image.Resampling.BICUBIC)
    mask = Image.new("L", (canvas_w, h), 0)
    ImageDraw.Draw(mask).polygon(dst, fill=255)
    out.putalpha(mask)
    return out, dst


def compose(title, year, poster, backdrop):
    bg = cover(backdrop, W, H).filter(ImageFilter.GaussianBlur(2))
    bg = ImageEnhance.Color(bg).enhance(1.15)
    bg = ImageEnhance.Brightness(bg).enhance(0.62)
    shade = np.zeros((H, W, 4), np.uint8)
    x = np.linspace(0, 1, W)
    shade[..., 3] = (np.clip(1.15 - x * 1.3, 0, 1) * 215).astype(np.uint8)[None, :]
    shade[..., 3] = np.maximum(shade[..., 3], (np.linspace(0, 1, H) ** 3 * 200).astype(np.uint8)[:, None])
    img = Image.alpha_composite(bg.convert("RGBA"), Image.fromarray(shade, "RGBA"))

    ph = 780
    p = poster.resize((int(poster.width * ph / poster.height), ph), Image.Resampling.LANCZOS)
    card, quad = tilt(p, 120)
    px, py = W - card.width - 150, (H - ph) // 2 - 40
    # drop shadow, then a gold rim light, then the card and its floor reflection
    shadow = Image.new("RGBA", img.size, (0, 0, 0, 0))
    sm = Image.new("L", img.size, 0)
    ImageDraw.Draw(sm).polygon([(px + a + 40, py + b + 50) for a, b in quad], fill=230)
    shadow.putalpha(sm.filter(ImageFilter.GaussianBlur(40)))
    img = Image.alpha_composite(img, shadow)
    rim = Image.new("RGBA", img.size, GOLD + (0,))
    rm = Image.new("L", img.size, 0)
    ImageDraw.Draw(rm).polygon([(px + a, py + b) for a, b in quad], fill=255)
    rim.putalpha(rm.filter(ImageFilter.GaussianBlur(26)).point(lambda v: int(v * 0.8)))
    img = Image.alpha_composite(img, rim)
    img.alpha_composite(card, (px, py))
    refl = card.transpose(Image.Transpose.FLIP_TOP_BOTTOM)
    fade = np.linspace(0.38, 0, refl.height) ** 1.4
    ra = np.array(refl.getchannel("A"), np.float32) * fade[:, None]
    refl.putalpha(Image.fromarray(ra.astype(np.uint8), "L"))
    refl = refl.filter(ImageFilter.GaussianBlur(2))
    img.alpha_composite(refl.crop((0, 0, refl.width, min(refl.height, H - py - ph - 4))), (px, py + ph + 4))
    # a glossy sheen across the card
    sheen = Image.new("L", card.size, 0)
    ImageDraw.Draw(sheen).polygon([(0, 0), (card.width * 0.45, 0), (card.width * 0.1, card.height), (0, card.height)], fill=38)
    sheen = Image.composite(sheen, Image.new("L", card.size, 0), card.getchannel("A"))
    white = Image.new("RGBA", card.size, (255, 255, 255, 0))
    white.putalpha(sheen)
    img.alpha_composite(white, (px, py))

    d = ImageDraw.Draw(img)
    f_title = ImageFont.truetype(os.path.join(FONTS, "Cinzel.ttf"), 120 if len(title) < 10 else 96)
    try:
        f_title.set_variation_by_axes([800])
    except Exception:
        pass
    f_small = ImageFont.truetype(os.path.join(FONTS, "BebasNeue-Regular.ttf"), 46)
    f_tiny = ImageFont.truetype(os.path.join(FONTS, "BebasNeue-Regular.ttf"), 28)
    tx, ty = 130, 430
    glow = Image.new("RGBA", img.size, GOLD + (0,))
    gm = Image.new("L", img.size, 0)
    ImageDraw.Draw(gm).text((tx, ty), title.upper(), font=f_title, fill=255, anchor="ls")
    glow.putalpha(gm.filter(ImageFilter.GaussianBlur(16)))
    img = Image.alpha_composite(img, glow)
    d = ImageDraw.Draw(img)
    d.text((tx, ty), title.upper(), font=f_title, fill=GOLD_HI, anchor="ls")
    d.text((tx + 4, ty + 70), f"{year}  \u2022  SUPERSTAR RAJINIKANTH", font=f_small, fill=(240, 228, 200), anchor="ls")
    d.text((tx + 4, ty + 128), "THALAIVAR TV", font=f_small, fill=GOLD, anchor="ls")
    d.text((W - 40, H - 28), "Artwork: TMDB  \u2022  personal use", font=f_tiny, fill=(200, 200, 200), anchor="rs")
    return img.convert("RGB")


def main():
    tok = token()
    os.makedirs(os.path.join(OUT, "wallpapers"), exist_ok=True)
    films = find_films(tok)
    captions = []
    for title, year, stem, mid in films:
        imgs = api(f"/movie/{mid}/images?include_image_language=en,ta,null", tok)
        pp = pick(imgs, "posters", {"en", "ta"})
        bp = pick(imgs, "backdrops", {None}) or pick(imgs, "backdrops", {"en", "ta"})
        if not pp or not bp:
            print(f"  skipped {title}: no poster or backdrop on TMDB")
            continue
        art = compose(title, year, image(pp, "w780"), image(bp, "w1280"))
        name = f"p-{year}-{stem}.jpg"
        art.save(os.path.join(OUT, "wallpapers", name), "JPEG", quality=84, optimize=True, progressive=True)
        captions.append(f"{name}\t{title} ({year})")
        print(f"  {name}")
    # his own portraits, as TMDB holds them, framed the same way
    person = max(api("/search/person?query=Rajinikanth", tok)["results"], key=lambda p: p.get("popularity", 0))
    profiles = sorted(api(f"/person/{person['id']}/images", tok).get("profiles", []),
                      key=lambda i: (i.get("vote_average", 0), i.get("height", 0)), reverse=True)[:8]
    film_backdrops = [image(pick(api(f"/movie/{mid}/images?include_image_language=en,ta,null", tok), "backdrops", {None})
                            or "", "w1280") for _, _, _, mid in films[:8]
                      if pick(api(f"/movie/{mid}/images?include_image_language=en,ta,null", tok), "backdrops", {None})]
    for i, prof in enumerate(profiles):
        bg = film_backdrops[i % len(film_backdrops)] if film_backdrops else Image.new("RGB", (W, H), (20, 12, 4))
        art = compose("Superstar", "Rajinikanth", image(prof["file_path"], "h632"), bg)
        name = f"r-{i + 1:02d}-superstar.jpg"
        art.save(os.path.join(OUT, "wallpapers", name), "JPEG", quality=84, optimize=True, progressive=True)
        captions.append(f"{name}\tSuperstar Rajinikanth")
        print(f"  {name}")
    # photos the owner saved into themes-local/my-photos/ themselves: fitted to 16:9 over a blurred fill
    mine = os.path.join(ROOT, "themes-local", "my-photos")
    os.makedirs(mine, exist_ok=True)
    for i, f in enumerate(sorted(os.listdir(mine))):
        if not f.lower().endswith((".jpg", ".jpeg", ".png", ".webp")):
            continue
        src = Image.open(os.path.join(mine, f)).convert("RGB")
        fill = cover(src, W, H).filter(ImageFilter.GaussianBlur(30))
        fill = ImageEnhance.Brightness(fill).enhance(0.45)
        s = min(W / src.width, H / src.height)
        fg = src.resize((int(src.width * s), int(src.height * s)), Image.Resampling.LANCZOS)
        fill.paste(fg, ((W - fg.width) // 2, (H - fg.height) // 2))
        name = f"m-{i + 1:03d}.jpg"
        fill.save(os.path.join(OUT, "wallpapers", name), "JPEG", quality=86, optimize=True, progressive=True)
        captions.append(f"{name}\tThalaivar")
        print(f"  {name}  (from my-photos/{f})")
    # keep the original THALAIVAR TV art in the rotation too
    for f in sorted(os.listdir(os.path.join(THEME, "wallpapers"))):
        if f.endswith(".jpg"):
            shutil.copy(os.path.join(THEME, "wallpapers", f), os.path.join(OUT, "wallpapers", f))
    for line in open(os.path.join(THEME, "wallpapers", "captions.txt"), encoding="utf-8"):
        captions.append(line.rstrip("\n"))
    open(os.path.join(OUT, "wallpapers", "captions.txt"), "w", encoding="utf-8", newline="\n").write("\n".join(captions) + "\n")
    cfg = json.load(open(os.path.join(THEME, "config.json"), encoding="utf-8"))
    names = sorted(f for f in os.listdir(os.path.join(OUT, "wallpapers")) if f.endswith(".jpg"))
    posters = [n for n in names if n.startswith(("p-", "r-", "m-"))]
    cfg["wallpaper_moods"] = {
        "morning": posters + [n for n in names if n.startswith(("05-", "06-", "03-"))],
        "evening": posters + [n for n in names if n.startswith(("01-", "07-", "10-", "11-"))],
        "night": posters + [n for n in names if n.startswith(("02-", "04-", "08-", "09-", "12-"))],
    }
    json.dump(cfg, open(os.path.join(OUT, "config.json"), "w", encoding="utf-8"), ensure_ascii=False, indent=2)
    shutil.copy(os.path.join(THEME, "wallpaper.jpg"), os.path.join(OUT, "wallpaper.jpg"))
    print(f"{len(posters)} poster wallpapers + {len(names) - len(posters)} originals in {OUT}")


if __name__ == "__main__":
    main()
