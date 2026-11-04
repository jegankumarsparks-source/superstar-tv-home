"""Draws THALAIVAR TV's original artwork: the logo, the banner and the theme's 16:9 wallpapers.

Everything here is generated from gradients, light and type, so the repository ships no film stills,
posters or photographs. Fonts are OFL (Cinzel, Bebas Neue), see tools/thalaivar/fonts/.

    python -I tools/thalaivar/make_art.py            # writes themes/thalaivar/ and the add-on's art
"""
import math
import os
import random
import sys

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
FONTS = os.path.join(HERE, "fonts")
THEME = os.path.join(ROOT, "themes", "thalaivar")
WALLS = os.path.join(THEME, "wallpapers")
ADDON_RES = os.path.join(ROOT, "addons", "thalaivar-hub", "app", "src", "main", "res")
W, H = 1920, 1080

GOLD = (212, 175, 55)
GOLD_HI = (255, 222, 120)
RED = (150, 12, 24)
PURPLE = (74, 20, 140)
BLUE = (30, 136, 255)


def font(name, size):
    return ImageFont.truetype(os.path.join(FONTS, name), size)


def cinzel(size, weight=800):
    f = font("Cinzel.ttf", size)
    try:
        f.set_variation_by_axes([weight])
    except Exception:
        pass
    return f


def bebas(size):
    return font("BebasNeue-Regular.ttf", size)


def grid():
    y, x = np.mgrid[0:H, 0:W].astype(np.float32)
    return x / W, y / H


def lerp(a, b, t):
    t = np.clip(t, 0, 1)[..., None]
    return np.array(a, np.float32) * (1 - t) + np.array(b, np.float32) * t


def radial(cx, cy, r, colour, strength=1.0, aspect=W / H):
    x, y = grid()
    d = np.sqrt(((x - cx) * aspect) ** 2 + (y - cy) ** 2) / r
    a = np.clip(1 - d, 0, 1) ** 2 * strength
    return np.array(colour, np.float32) * a[..., None]


def rays(cx, cy, count, colour, strength, seed, spread=1.0):
    """Soft light rays fanning out from (cx, cy), like stage lights behind a hero."""
    x, y = grid()
    ang = np.arctan2((y - cy), (x - cx) * W / H)
    rnd = random.Random(seed)
    acc = np.zeros((H, W), np.float32)
    for _ in range(count):
        a0 = rnd.uniform(-math.pi, math.pi) * spread
        width = rnd.uniform(0.015, 0.06)
        acc += np.exp(-((np.angle(np.exp(1j * (ang - a0)))) ** 2) / (2 * width ** 2)) * rnd.uniform(0.3, 1)
    dist = np.sqrt(((x - cx) * W / H) ** 2 + (y - cy) ** 2)
    acc = np.clip(acc / max(1e-6, float(acc.max())), 0, 1)
    acc *= np.clip(1.1 - dist, 0, 1) ** 1.5
    # strength is the peak brightness (0-255) a ray reaches at its brightest point
    return np.array(colour, np.float32) / 255.0 * (acc * strength)[..., None]


def to_img(arr):
    return Image.fromarray(np.clip(arr, 0, 255).astype(np.uint8), "RGB")


def particles(img, seed, n, colour, rmax=4.0, blur=True, region=(0, 0, W, H), bokeh_n=None):
    """Gold dust and bokeh: small sharp sparks plus a few large soft discs."""
    rnd = random.Random(seed)
    layer = Image.new("RGBA", img.size, (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    for _ in range(n):
        x = rnd.uniform(region[0], region[2])
        y = rnd.uniform(region[1], region[3])
        r = rnd.uniform(0.6, rmax)
        a = int(rnd.uniform(90, 255))
        d.ellipse((x - r, y - r, x + r, y + r), fill=colour + (a,))
    bokeh = Image.new("RGBA", img.size, (0, 0, 0, 0))
    db = ImageDraw.Draw(bokeh)
    for _ in range(n // 12 if bokeh_n is None else bokeh_n):
        x = rnd.uniform(region[0], region[2])
        y = rnd.uniform(region[1], region[3])
        r = rnd.uniform(14, 60)
        db.ellipse((x - r, y - r, x + r, y + r), fill=colour + (int(rnd.uniform(20, 60)),))
    if blur:
        bokeh = bokeh.filter(ImageFilter.GaussianBlur(10))
        glow = layer.filter(ImageFilter.GaussianBlur(3))
        img = Image.alpha_composite(img.convert("RGBA"), glow)
    img = Image.alpha_composite(img.convert("RGBA"), bokeh)
    return Image.alpha_composite(img, layer).convert("RGB")


def gold_text(img, xy, text, f, anchor="mm", glow=18, colours=(GOLD_HI, GOLD), stroke=0):
    """Metallic gold lettering: a vertical gold gradient through the glyphs and a warm glow behind."""
    mask = Image.new("L", img.size, 0)
    ImageDraw.Draw(mask).text(xy, text, font=f, fill=255, anchor=anchor, stroke_width=stroke, stroke_fill=255)
    box = mask.getbbox()
    if not box:
        return img
    glow_layer = Image.new("RGBA", img.size, colours[1] + (0,))
    glow_layer.putalpha(mask.filter(ImageFilter.GaussianBlur(glow)).point(lambda v: int(v * 0.85)))
    img = Image.alpha_composite(img.convert("RGBA"), glow_layer)
    top, bottom = box[1], box[3]
    grad = np.zeros((H, W, 3), np.float32)
    t = (np.arange(H, dtype=np.float32) - top) / max(1, bottom - top)
    # bright top, a darker band two thirds down, bright again at the foot: a polished metal look
    band = 0.55 + 0.45 * np.cos(np.clip(t, 0, 1) * math.pi * 1.6)
    col = lerp(colours[1], colours[0], band)
    grad[:] = col[:, None, :] if col.ndim == 2 else col
    fill = Image.fromarray(grad.astype(np.uint8), "RGB").convert("RGBA")
    fill.putalpha(mask)
    return Image.alpha_composite(img, fill).convert("RGB")


def plain_text(img, xy, text, f, colour, anchor="mm", alpha=255, spacing=0):
    layer = Image.new("RGBA", img.size, (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    if spacing:
        widths = [d.textlength(c, font=f) + spacing for c in text]
        total = sum(widths) - spacing
        x = xy[0] - total / 2 if anchor[0] == "m" else xy[0]
        for c, w in zip(text, widths):
            d.text((x, xy[1]), c, font=f, fill=colour + (alpha,), anchor="l" + anchor[1])
            x += w
    else:
        d.text(xy, text, font=f, fill=colour + (alpha,), anchor=anchor)
    return Image.alpha_composite(img.convert("RGBA"), layer).convert("RGB")


def vignette(arr, strength=0.75):
    x, y = grid()
    d = np.sqrt(((x - 0.5) * 1.6) ** 2 + ((y - 0.5) * 1.2) ** 2)
    return arr * (1 - np.clip(d - 0.35, 0, 1) * strength)[..., None]


def skyline(img, colour, base_y, seed):
    """A generic coastal city silhouette with a lighthouse, drawn from rectangles: no real photo."""
    rnd = random.Random(seed)
    d = ImageDraw.Draw(img)
    x = 0
    while x < W:
        w = rnd.randint(40, 130)
        h = rnd.randint(40, 220)
        d.rectangle((x, base_y - h, x + w, H), fill=colour)
        if rnd.random() < 0.3:
            d.rectangle((x + w // 3, base_y - h - rnd.randint(20, 60), x + w // 3 + 8, base_y - h), fill=colour)
        x += w + rnd.randint(0, 12)
    lx = int(W * 0.78)
    d.polygon([(lx - 26, base_y - 40), (lx + 26, base_y - 40), (lx + 14, base_y - 330), (lx - 14, base_y - 330)], fill=colour)
    d.rectangle((lx - 22, base_y - 360, lx + 22, base_y - 330), fill=colour)
    d.rectangle((0, base_y, W, H), fill=colour)
    return img


def tagline(img, y=820):
    img = plain_text(img, (W // 2, y), "SUPERSTAR  •  THE LEGEND  •  THE CELEBRATION", bebas(46), (240, 230, 210),
                     alpha=200, spacing=6)
    return img


# ------------------------------------------------------------------ wallpapers
def wall_emperor_gold():
    a = np.zeros((H, W, 3), np.float32) + 6
    a += rays(0.5, 0.42, 26, GOLD, 120, 1)
    a += radial(0.5, 0.42, 0.75, (120, 80, 10), 1.0)
    a += radial(0.5, 0.45, 0.25, GOLD_HI, 0.9)
    img = to_img(vignette(a))
    img = particles(img, 2, 900, GOLD_HI, 3)
    img = gold_text(img, (W // 2, 470), "THALAIVAR", cinzel(230), glow=26)
    img = tagline(img, 640)
    return img


def wall_baasha_night():
    a = np.zeros((H, W, 3), np.float32) + 3
    a += rays(0.5, 1.05, 34, GOLD, 90, 3, spread=0.5)
    a += radial(0.5, 1.0, 0.9, (90, 60, 8), 1.0)
    img = to_img(vignette(a, 0.9))
    img = particles(img, 4, 700, GOLD, 2.6)
    img = gold_text(img, (W // 2, 420), "BAASHA", cinzel(260), glow=30)
    img = plain_text(img, (W // 2, 600), "THE EVERGREEN EMPEROR", bebas(78), (245, 225, 170), alpha=235, spacing=14)
    img = plain_text(img, (W // 2, 690), "1995  •  A FAN CELEBRATION", bebas(40), (200, 180, 130), alpha=170, spacing=8)
    return img


def wall_crimson():
    x, y = grid()
    a = lerp((40, 0, 8), RED, 1 - y) * 0.9
    a += radial(0.25, 0.3, 0.6, (220, 40, 60), 0.6)
    a += radial(0.8, 0.75, 0.6, PURPLE, 0.8)
    a += rays(0.5, -0.1, 18, (255, 120, 90), 50, 5, spread=0.4)
    img = to_img(vignette(a))
    img = particles(img, 6, 1100, (255, 200, 120), 3.5)
    img = gold_text(img, (W // 2, 500), "SUPERSTAR", cinzel(200), glow=24)
    img = plain_text(img, (W // 2, 660), "CELEBRATE THE LEGEND", bebas(60), (255, 236, 220), alpha=220, spacing=12)
    return img


def wall_royal_purple():
    x, y = grid()
    a = lerp((12, 4, 30), PURPLE, np.sin(x * math.pi) * (1 - y * 0.6))
    a += radial(0.7, 0.35, 0.5, BLUE, 0.55)
    a += radial(0.2, 0.8, 0.5, (160, 40, 200), 0.5)
    img = to_img(vignette(a))
    img = particles(img, 7, 800, (200, 180, 255), 3)
    img = gold_text(img, (W // 2, 520), "THALAIVAR TV", cinzel(150), glow=20)
    return img


def wall_electric_blue():
    x, y = grid()
    a = lerp((2, 8, 30), (6, 40, 110), 1 - y)
    a += radial(0.5, 0.95, 0.9, BLUE, 0.9)
    a += radial(0.5, 1.0, 0.35, GOLD, 0.8)
    # horizon line of light
    a += np.array(GOLD_HI, np.float32) * (np.exp(-((y - 0.86) ** 2) / 0.00008) * 0.9)[..., None]
    img = to_img(vignette(a))
    img = particles(img, 8, 700, (150, 210, 255), 2.6, region=(0, 0, W, H * 0.8))
    img = gold_text(img, (W // 2, 400), "THE LEGEND", cinzel(170), glow=22)
    return img


def wall_chennai_dawn():
    x, y = grid()
    a = lerp((255, 196, 120), (40, 30, 90), y * 1.2)
    a += radial(0.32, 0.72, 0.35, (255, 230, 160), 1.0)
    img = to_img(a)
    img = particles(img, 9, 200, (255, 245, 210), 2, region=(0, 0, W, H * 0.6))
    img = skyline(img, (34, 20, 40), int(H * 0.86), 10)
    img = plain_text(img, (W // 2, 230), "VANAKKAM CHENNAI", bebas(110), (255, 250, 235), alpha=235, spacing=16)
    return img


def wall_sunset_gold():
    x, y = grid()
    a = lerp((255, 150, 40), (90, 10, 50), y * 1.1)
    a += radial(0.65, 0.78, 0.4, (255, 220, 120), 1.0)
    a += rays(0.65, 0.8, 20, (255, 200, 120), 40, 11, spread=0.6)
    img = to_img(a)
    img = skyline(img, (26, 8, 26), int(H * 0.88), 12)
    img = gold_text(img, (W // 2, 330), "MAGIZHCHI", cinzel(170), glow=22, colours=((255, 245, 200), (230, 170, 40)))
    return img


def wall_midnight_star():
    a = np.zeros((H, W, 3), np.float32)
    x, y = grid()
    a += lerp((2, 2, 12), (14, 10, 40), y)
    a += radial(0.5, 0.45, 0.12, GOLD_HI, 1.0)
    a += radial(0.5, 0.45, 0.45, (100, 70, 20), 0.6)
    img = to_img(a)
    img = particles(img, 13, 1600, (255, 255, 255), 1.6, blur=False, bokeh_n=12)
    # a five-point star of light, the "superstar" mark
    d = ImageDraw.Draw(img)
    cx, cy, r1, r2 = W // 2, int(H * 0.45), 70, 28
    pts = []
    for i in range(10):
        r = r1 if i % 2 == 0 else r2
        ang = -math.pi / 2 + i * math.pi / 5
        pts.append((cx + r * math.cos(ang), cy + r * math.sin(ang)))
    d.polygon(pts, fill=GOLD_HI)
    img = img.filter(ImageFilter.GaussianBlur(0.6))
    img = plain_text(img, (W // 2, 700), "GOOD NIGHT  •  SUPERSTAR DREAMS", bebas(54), (230, 220, 190), alpha=200, spacing=10)
    return img


def wall_jailer_steel():
    x, y = grid()
    a = lerp((6, 14, 18), (18, 44, 52), 1 - y)
    a += radial(0.78, 0.4, 0.45, (255, 110, 20), 0.75)
    a += radial(0.2, 0.7, 0.5, (20, 90, 100), 0.6)
    # prison-bar light shafts
    for i in range(9):
        cx = 0.1 + i * 0.1
        a += np.array((255, 160, 70), np.float32) * (np.exp(-((x - cx) ** 2) / 0.00012) * 0.35 * (1 - y))[..., None]
    img = to_img(vignette(a))
    img = particles(img, 14, 500, (255, 170, 80), 2.4)
    img = gold_text(img, (W // 2, 440), "JAILER 2", cinzel(220), glow=26, colours=((255, 230, 190), (220, 120, 30)))
    img = plain_text(img, (W // 2, 600), "MUTHUVEL PANDIAN RETURNS", bebas(64), (235, 230, 220), alpha=225, spacing=12)
    img = plain_text(img, (W // 2, 680), "IN CINEMAS  •  15 OCTOBER 2026", bebas(40), (200, 190, 175), alpha=170, spacing=8)
    return img


def wall_coolie_gold():
    x, y = grid()
    a = lerp((30, 6, 4), (120, 40, 6), np.abs(np.sin((x + y) * 6)) * 0.7)
    a += radial(0.5, 0.5, 0.6, GOLD, 0.6)
    img = to_img(vignette(a))
    img = particles(img, 15, 900, GOLD_HI, 3)
    img = gold_text(img, (W // 2, 500), "COOLIE", cinzel(240), glow=28)
    img = plain_text(img, (W // 2, 660), "2025", bebas(60), (250, 230, 200), alpha=200, spacing=20)
    return img


def wall_padayappa():
    x, y = grid()
    a = lerp((60, 4, 14), (10, 2, 4), y)
    a += radial(0.5, 0.35, 0.6, (230, 60, 30), 0.7)
    a += rays(0.5, 0.35, 24, (255, 150, 60), 60, 16)
    img = to_img(vignette(a))
    img = particles(img, 17, 800, (255, 190, 90), 3)
    img = gold_text(img, (W // 2, 470), "PADAYAPPA", cinzel(200), glow=24)
    img = plain_text(img, (W // 2, 630), "1999", bebas(60), (250, 225, 190), alpha=200, spacing=20)
    return img


def wall_enthiran():
    x, y = grid()
    a = lerp((0, 6, 16), (0, 30, 60), 1 - y)
    for i in range(0, 40):
        gx = i / 40
        a += np.array((40, 160, 255), np.float32) * (np.exp(-((x - gx) ** 2) / 0.000004) * 0.25)[..., None]
        a += np.array((40, 160, 255), np.float32) * (np.exp(-((y - gx) ** 2) / 0.000004) * 0.2)[..., None]
    a += radial(0.5, 0.45, 0.4, (0, 180, 255), 0.6)
    img = to_img(vignette(a))
    img = particles(img, 18, 600, (140, 220, 255), 2.4)
    img = gold_text(img, (W // 2, 470), "ENTHIRAN", cinzel(210), glow=24, colours=((220, 245, 255), (60, 170, 255)))
    img = plain_text(img, (W // 2, 630), "2010", bebas(60), (220, 240, 255), alpha=200, spacing=20)
    return img


WALLPAPERS = [
    ("01-emperor-gold.jpg", wall_emperor_gold, "Emperor gold"),
    ("02-baasha-night.jpg", wall_baasha_night, "Baasha - The Evergreen Emperor"),
    ("03-crimson-celebration.jpg", wall_crimson, "Crimson celebration"),
    ("04-royal-purple.jpg", wall_royal_purple, "Royal purple"),
    ("05-electric-blue.jpg", wall_electric_blue, "Electric blue"),
    ("06-chennai-dawn.jpg", wall_chennai_dawn, "Vanakkam Chennai"),
    ("07-sunset-gold.jpg", wall_sunset_gold, "Sunset gold"),
    ("08-midnight-star.jpg", wall_midnight_star, "Midnight superstar"),
    ("09-jailer2-steel.jpg", wall_jailer_steel, "Jailer 2 - Muthuvel Pandian"),
    ("10-coolie-gold.jpg", wall_coolie_gold, "Coolie"),
    ("11-padayappa-fire.jpg", wall_padayappa, "Padayappa"),
    ("12-enthiran-grid.jpg", wall_enthiran, "Enthiran"),
]


def save_jpeg(img, path, max_bytes=300_000):
    for q in (86, 82, 78, 74, 70, 65):
        img.save(path, "JPEG", quality=q, optimize=True, progressive=True)
        if os.path.getsize(path) <= max_bytes:
            return q
    return q


def logo(size=(1280, 720)):
    """The THALAIVAR TV mark: a gold star over a crown line, the wordmark and the tagline."""
    w, h = size
    global W, H
    W0, H0 = W, H
    W, H = w, h
    try:
        a = np.zeros((h, w, 3), np.float32) + 4
        a += rays(0.5, 0.36, 22, GOLD, 70, 21)
        a += radial(0.5, 0.36, 0.5, (110, 70, 8), 1.0)
        img = to_img(vignette(a, 0.9))
        img = particles(img, 22, 300, GOLD_HI, 2.4)
        d = ImageDraw.Draw(img)
        cx, cy, r1, r2 = w // 2, int(h * 0.27), int(h * 0.11), int(h * 0.045)
        pts = []
        for i in range(10):
            r = r1 if i % 2 == 0 else r2
            ang = -math.pi / 2 + i * math.pi / 5
            pts.append((cx + r * math.cos(ang), cy + r * math.sin(ang)))
        star = Image.new("L", img.size, 0)
        ImageDraw.Draw(star).polygon(pts, fill=255)
        glow = Image.new("RGBA", img.size, GOLD + (0,))
        glow.putalpha(star.filter(ImageFilter.GaussianBlur(18)))
        img = Image.alpha_composite(img.convert("RGBA"), glow).convert("RGB")
        ImageDraw.Draw(img).polygon(pts, fill=GOLD_HI)
        img = gold_text(img, (w // 2, int(h * 0.56)), "THALAIVAR", cinzel(int(h * 0.19)), glow=16)
        img = plain_text(img, (w // 2, int(h * 0.70)), "T V", bebas(int(h * 0.09)), (245, 230, 190), alpha=230, spacing=24)
        img = plain_text(img, (w // 2, int(h * 0.82)), "SUPERSTAR • THE LEGEND • THE CELEBRATION", bebas(int(h * 0.045)),
                         (220, 205, 170), alpha=200, spacing=4)
        return img
    finally:
        W, H = W0, H0


def main():
    os.makedirs(WALLS, exist_ok=True)
    only = set(sys.argv[1:])
    captions = []
    for name, fn, caption in WALLPAPERS:
        if only and name not in only:
            continue
        img = fn()
        q = save_jpeg(img, os.path.join(WALLS, name))
        captions.append(f"{name}\t{caption}")
        print(f"{name}  q{q}  {os.path.getsize(os.path.join(WALLS, name)) // 1024} KB")
    if captions and not only:
        with open(os.path.join(WALLS, "captions.txt"), "w", encoding="utf-8", newline="\n") as f:
            f.write("\n".join(captions) + "\n")
    # the theme's single wallpaper, for launchers that show one picture
    save_jpeg(wall_emperor_gold(), os.path.join(THEME, "wallpaper.jpg"))
    # the add-on's logo, splash backdrop and TV banner
    nodpi = os.path.join(ADDON_RES, "drawable-nodpi")
    os.makedirs(nodpi, exist_ok=True)
    save_jpeg(logo((1280, 720)), os.path.join(nodpi, "logo_splash.jpg"), 200_000)
    logo((320, 180)).save(os.path.join(nodpi, "banner_art.png"))
    print("logo + banner written")


if __name__ == "__main__":
    main()
