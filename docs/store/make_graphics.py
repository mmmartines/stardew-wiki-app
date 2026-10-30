"""Gera a imagem de destaque (1024x500) da ficha do Google Play em pt, en e es.

Uso: python3 docs/store/make_graphics.py [pasta_de_icones_local]
Sem argumento, baixa os ícones da Stardew Valley Wiki (uso autorizado pela ConcernedApe).
"""
import io
import os
import sys
import urllib.request

from PIL import Image, ImageDraw, ImageFont

ROOT = os.path.dirname(os.path.abspath(__file__))
FONT = os.path.join(ROOT, "..", "..", "app", "src", "main", "res", "font", "pixel.ttf")
OUT = ROOT
W, H = 1024, 500

ICONS = {
    "chicken": "2/22/White_Chicken.png",
    "parsnip": "d/db/Parsnip.png",
    "sardine": "0/04/Sardine.png",
    "pickaxe": "c/ca/Iridium_Pickaxe.png",
    "bundle": "3/3f/Bundle_Red.png",
    "pumpkin": "6/64/Pumpkin.png",
    "diamond": "e/ea/Diamond.png",
}

TEXT = {
    "pt": ("Guia do Vale", "A Stardew Valley Wiki no seu celular", "App de fã · não oficial · gratuito"),
    "en": ("Valley Guide", "The Stardew Valley Wiki on your phone", "Fan app · unofficial · free"),
    "es": ("Guía del Valle", "La Stardew Valley Wiki en tu móvil", "App de fans · no oficial · gratis"),
}

WOOD, WOOD_D, WOOD_L = (154, 90, 36), (90, 47, 14), (194, 122, 58)
CREAM, GOLD = (255, 233, 184), (255, 217, 138)


def icon(name, local):
    if local:
        im = Image.open(os.path.join(local, name + ".png"))
    else:
        url = "https://stardewvalleywiki.com/mediawiki/images/" + ICONS[name]
        req = urllib.request.Request(url, headers={"User-Agent": "GuiaDoVale-store-graphics/1.0"})
        im = Image.open(io.BytesIO(urllib.request.urlopen(req, timeout=30).read()))
    return im.convert("RGBA")


def big(im, size):
    """Amplia pixel art sem borrar."""
    return im.resize((size, size), Image.NEAREST)


def background():
    bg = Image.new("RGB", (W, H))
    d = ImageDraw.Draw(bg)
    top, bottom = (98, 182, 240), (191, 231, 255)
    sky_h = 380
    for y in range(sky_h):
        t = y / sky_h
        d.line([(0, y), (W, y)], fill=tuple(int(top[i] + (bottom[i] - top[i]) * t) for i in range(3)))
    # nuvens em blocos de pixel
    for cx, cy, s in [(120, 70, 1.0), (430, 45, 0.8), (700, 95, 0.7), (930, 55, 0.9)]:
        u = int(14 * s)
        for dx, dy, w, h in [(0, 0, 6, 2), (1, -1, 4, 1), (2, -2, 2, 1), (-1, 1, 8, 1)]:
            d.rectangle([cx + dx * u, cy + dy * u, cx + (dx + w) * u, cy + (dy + h) * u], fill=(255, 255, 255))
    # sol
    d.rectangle([W - 120, 30, W - 70, 80], fill=(255, 211, 77))
    d.rectangle([W - 126, 36, W - 64, 74], outline=(255, 230, 138), width=6)
    # grama
    d.rectangle([0, sky_h, W, H], fill=(95, 168, 52))
    d.rectangle([0, sky_h + 40, W, H], fill=(74, 138, 38))
    for x in range(0, W, 16):
        d.rectangle([x, sky_h - 8, x + 7, sky_h], fill=(134, 201, 78))
    d.rectangle([0, sky_h, W, sky_h + 6], fill=(134, 201, 78))
    return bg


def sign(img, x, y, w, h):
    d = ImageDraw.Draw(img)
    # postes
    for px in (x + 70, x + w - 86):
        d.rectangle([px, y + h - 10, px + 16, 404], fill=WOOD_D)
    d.rectangle([x + 6, y + 8, x + w + 6, y + h + 8], fill=(0, 0, 0, 60))
    d.rectangle([x, y, x + w, y + h], fill=WOOD_D)
    d.rectangle([x + 6, y + 6, x + w - 6, y + h - 6], fill=WOOD_L)
    d.rectangle([x + 10, y + 10, x + w - 10, y + h - 10], fill=WOOD)
    for yy in range(y + 24, y + h - 10, 22):
        d.line([(x + 10, yy), (x + w - 10, yy)], fill=(140, 80, 30), width=2)


def text_shadow(d, xy, s, font, fill, shadow=WOOD_D, off=3):
    d.text((xy[0] + off, xy[1] + off), s, font=font, fill=shadow)
    d.text(xy, s, font=font, fill=fill)


def fit(d, s, size, maxw):
    while size > 10:
        f = ImageFont.truetype(FONT, size)
        if d.textlength(s, font=f) <= maxw:
            return f
        size -= 2
    return ImageFont.truetype(FONT, size)


def render(lang, icons):
    title, tagline, note = TEXT[lang]
    img = background().convert("RGBA")
    sx, sy, sw, sh = 50, 70, 600, 250
    sign(img, sx, sy, sw, sh)
    d = ImageDraw.Draw(img)
    f_title = fit(d, title, 76, sw - 60)
    f_tag = fit(d, tagline, 30, sw - 60)
    tw = d.textlength(title, font=f_title)
    text_shadow(d, (sx + (sw - tw) / 2, sy + 34), title, f_title, CREAM, off=4)
    gw = d.textlength(tagline, font=f_tag)
    text_shadow(d, (sx + (sw - gw) / 2, sy + 132), tagline, f_tag, GOLD, off=2)
    # ícones em espaços de inventário
    names = ["parsnip", "sardine", "pickaxe", "bundle", "pumpkin", "diamond"]
    slot, gap = 64, 14
    total = len(names) * slot + (len(names) - 1) * gap
    x0, y0 = sx + (sw - total) // 2, sy + 176
    for i, n in enumerate(names):
        x = x0 + i * (slot + gap)
        d.rectangle([x, y0, x + slot, y0 + slot], fill=(201, 143, 69))
        d.rectangle([x + 4, y0 + 4, x + slot - 4, y0 + slot - 4], fill=(240, 196, 119))
        ic = big(icons[n], 48)
        img.alpha_composite(ic, (x + 8, y0 + 8))
    # galinha grande na grama
    ch = big(icons["chicken"], 288)
    img.alpha_composite(ch, (700, 150))
    # selo "não oficial"
    f_note = fit(d, note, 26, 600)
    nw = d.textlength(note, font=f_note)
    nx, ny = sx + (sw - nw) / 2, 424
    d.rectangle([nx - 18, ny - 10, nx + nw + 18, ny + 40], fill=(255, 241, 204))
    d.rectangle([nx - 18, ny - 10, nx + nw + 18, ny + 40], outline=WOOD_D, width=4)
    d.text((nx, ny), note, font=f_note, fill=(59, 36, 18))
    path = os.path.join(OUT, f"feature-{lang}.png")
    img.convert("RGB").save(path)
    print("salvo", path)


def main():
    local = sys.argv[1] if len(sys.argv) > 1 else None
    icons = {n: icon(n, local) for n in ICONS}
    for lang in TEXT:
        render(lang, icons)


if __name__ == "__main__":
    main()
