#!/usr/bin/env python
"""Generate Hermes Android launcher icons (adaptive + legacy) from hermes.png."""
import os, sys
from PIL import Image, ImageDraw, ImageFilter

ROOT = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.join(ROOT, "android/app/src/main/assets/hermes.png")
OUT = os.path.join(ROOT, "android/app/src/main/res")

def radial_vignette(size, center, inner, outer, inner_a, outer_a):
    """Smooth darkened border to focus the center; returns RGBA black-alpha layer."""
    w, h = size
    cx, cy = center
    # sample radius = half the shorter dimension
    r = min(w, h) / 2.0
    ys = [int(cy - r + i * 2 * r / (255 - 1)) for i in range(256)]
    xs = [int(cx - r + i * 2 * r / (255 - 1)) for i in range(256)]
    grad = Image.new("L", (256, 256))
    gd = grad.load()
    for y in range(256):
        for x in range(256):
            d = ((x - 128) ** 2 + (y - 128) ** 2) ** 0.5 / 128.0
            d = max(0.0, min(1.0, d))
            a = int(round(inner_a + (outer_a - inner_a) * d))
            gd[x, y] = a
    grad = grad.resize(size, Image.BICUBIC)
    # convert to alpha: 0 (inner) -> inner_a, 1 (outer) -> outer_a
    a2 = Image.eval(grad, lambda v: int(round(inner_a + (outer_a - inner_a) * v)))
    black = Image.new("RGBA", size, (8, 10, 24, 255))
    black.putalpha(a2)
    return black

def bg(size):
    """Deep indigo->blue vertical gradient with a centered radial glow + soft vignette + subtle rim."""
    if isinstance(size, int):
        size = (size, size)
    w, h = size
    top = (30, 34, 96)      # indigo (brighter for dark-wallpaper visibility)
    mid = (52, 66, 138)
    bot = (22, 26, 68)      # darker indigo
    grad = Image.new("RGB", (1, h))
    gd = grad.load()
    for y in range(h):
        t = y / h
        if t < 0.5:
            u = t / 0.5
            c = tuple(int(top[i] + (mid[i] - top[i]) * u) for i in range(3))
        else:
            u = (t - 0.5) / 0.5
            c = tuple(int(mid[i] + (bot[i] - mid[i]) * u) for i in range(3))
        gd[0, y] = c
    img = grad.resize((w, h), Image.BILINEAR).convert("RGBA")
    # centered radial glow to lift the figure
    glow = Image.new("L", size, 0)
    gd2 = glow.load()
    cx, cy = w / 2, h / 2
    r = min(w, h) * 0.48
    for y in range(h):
        for x in range(w):
            d = ((x - cx) ** 2 + (y - cy) ** 2) ** 0.5 / r
            if d < 1:
                gd2[x, y] = int(round(180 * (1 - d) ** 1.6))
    glow = glow.filter(ImageFilter.GaussianBlur(max(4, size[0] // 16)))
    light = Image.new("RGBA", size, (110, 140, 235, 255))
    light.putalpha(glow)
    img = Image.alpha_composite(img, light)
    # soft vignette to focus center (mild)
    vig = radial_vignette(size, (w / 2, h / 2), top, (10, 12, 30), 0, 90)
    img = Image.alpha_composite(img, vig)
    # subtle bright rim so the icon keeps a crisp edge on dark wallpapers
    rim = Image.new("RGBA", size, (0, 0, 0, 0))
    ImageDraw.Draw(rim).rounded_rectangle((1, 1, w - 2, h - 2), max(4, int(min(w, h) * 0.20)), outline=(140, 160, 230, 70), width=max(1, min(w, h) // 128))
    img = Image.alpha_composite(img, rim)
    return img

def rounded_mask(size, radius):
    if isinstance(size, int):
        size = (size, size)
    m = Image.new("L", size, 0)
    ImageDraw.Draw(m).rounded_rectangle((0, 0, size[0] - 1, size[1] - 1), radius, fill=255)
    return m

def place_foreground(bg_img, hero, pad_frac=0.10):
    """Center hero (transparent) on bg, sized to (1-pad) of the shorter side, no upscaling."""
    w, h = bg_img.size
    max_dim = int(min(w, h) * (1 - pad_frac))
    hw, hh = hero.size
    scale = min(max_dim / hw, max_dim / hh)
    if scale < 1:
        hw2, hh2 = int(hw * scale), int(hh * scale)
        hero_r = hero.resize((hw2, hh2), Image.LANCZOS)
    else:
        hero_r = hero.copy()
    out = bg_img.copy()
    out.alpha_composite(hero_r, ((w - hero_r.width) // 2, (h - hero_r.height) // 2))
    return out

def write(img, path):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path, "PNG", optimize=True)

def main():
    hero_full = Image.open(SRC).convert("RGBA")
    # Auto-crop to the actual sprite content (the source has ~250px of transparent
    # padding on every side) so the figure can fill the icon instead of floating.
    hero = hero_full.crop(hero_full.getchannel("A").getbbox())

    # ---- Adaptive icons (API 26+): full-bleed 108dp layers, safe zone ~66dp ----
    adaptive = {}
    adaptive_sizes = {
        "mipmap-mdpi": 108, "mipmap-hdpi": 162, "mipmap-xhdpi": 216,
        "mipmap-xxhdpi": 324, "mipmap-xxxhdpi": 432,
    }
    for d, s in adaptive_sizes.items():
        b = bg(s)
        # foreground sized to 66/108 of the canvas = 0.611
        fg = place_foreground(Image.new("RGBA", (s, s), (0, 0, 0, 0)), hero, pad_frac=0.39)
        fg = fg.crop((0, 0, s, s))  # keep full canvas, transparent bg
        write(b, os.path.join(OUT, d, f"ic_launcher_background_{s}.png"))
        write(fg, os.path.join(OUT, d, f"ic_launcher_foreground_{s}.png"))
    # 512 for Play/adaptive high-res
    s = 512
    write(bg(s), os.path.join(OUT, "mipmap-mdpi", "ic_launcher_background_512.png"))
    write(place_foreground(Image.new("RGBA", (s, s), (0, 0, 0, 0)), hero, pad_frac=0.39), os.path.join(OUT, "mipmap-mdpi", "ic_launcher_foreground_512.png"))

    # ---- Legacy square (all API levels) ----
    legacy = {
        "mipmap-mdpi": 48, "mipmap-hdpi": 72, "mipmap-xhdpi": 96,
        "mipmap-xxhdpi": 144, "mipmap-xxxhdpi": 192, "mipmap-512": 512,
    }
    for d, s in legacy.items():
        b = bg(s)
        img = place_foreground(b, hero, pad_frac=0.07)
        mask = rounded_mask(s, max(4, int(s * 0.20)))
        final = Image.new("RGBA", (s, s), (0, 0, 0, 0))
        final.paste(img, (0, 0), mask)
        write(final, os.path.join(OUT, d, f"ic_launcher_legacy_{s}.png"))

    # ---- Monochrome for dark theme launcher (uses foreground alpha) ----
    mono_sizes = {
        "mipmap-mdpi": 108, "mipmap-hdpi": 162, "mipmap-xhdpi": 216,
        "mipmap-xxhdpi": 324, "mipmap-xxxhdpi": 432,
    }
    for d, s in mono_sizes.items():
        fg = place_foreground(Image.new("RGBA", (s, s), (0, 0, 0, 0)), hero, pad_frac=0.39)
        # monochrome = white silhouette from alpha
        white = Image.new("RGBA", (s, s), (255, 255, 255, 255))
        white.putalpha(fg.split()[3])
        write(white, os.path.join(OUT, d, f"ic_launcher_monochrome_{s}.png"))

    # ---- Preview: 240px legacy + a 120px "home screen" mock ----
    b = bg(240)
    prev = place_foreground(b, hero, pad_frac=0.07)
    mask = rounded_mask(240, 48)
    final = Image.new("RGBA", (240, 240), (0, 0, 0, 0))
    final.paste(prev, (0, 0), mask)
    write(final, os.path.join(ROOT, "icon-preview.png"))

    print("Generated icons under", OUT)
    for d in sorted(os.listdir(OUT)):
        p = os.path.join(OUT, d)
        if os.path.isdir(p):
            files = sorted(os.listdir(p))
            print(f"  {d}: {len(files)} files -> {files[:3]}{'...' if len(files)>3 else ''}")

if __name__ == "__main__":
    main()
