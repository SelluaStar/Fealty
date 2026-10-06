#!/usr/bin/env python3
"""
Generates Fealty's placeholder textures and Blockbench sources.

Everything here is original pixel art painted onto Minecraft's standard UV layouts (villager, player skin,
armour, 16x16 items), so any texture can be replaced by dropping a new PNG at the same path. Re-run after
editing:

    pip install pillow
    python3 tools/art/generate_art.py

Outputs:
    src/main/resources/assets/fealty/textures/...   textures used by the mod
    src/main/resources/fealty_logo.png               mod list logo
    tools/art/bbmodel/*.bbmodel                      Blockbench projects for the custom block models
"""
import base64
import io
import json
import math
import os
import random
import uuid

from PIL import Image, ImageDraw

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
TEX = os.path.join(ROOT, "src", "main", "resources", "assets", "fealty", "textures")
BB = os.path.join(ROOT, "tools", "art", "bbmodel")


def hexc(value, alpha=255):
    value = value.lstrip("#")
    return (int(value[0:2], 16), int(value[2:4], 16), int(value[4:6], 16), alpha)


def shade(color, factor):
    r, g, b, a = color
    return (max(0, min(255, int(r * factor))), max(0, min(255, int(g * factor))), max(0, min(255, int(b * factor))), a)


def save(img, *parts):
    path = os.path.join(TEX, *parts)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)
    return path


class Painter:
    """Fills cube faces of a texture laid out with Minecraft's box UV unwrap."""

    def __init__(self, width, height, seed):
        self.img = Image.new("RGBA", (width, height), (0, 0, 0, 0))
        self.px = self.img.load()
        self.rand = random.Random(seed)

    def set(self, x, y, color):
        if 0 <= x < self.img.width and 0 <= y < self.img.height and color is not None:
            self.px[x, y] = color

    def rect(self, x0, y0, w, h, fn):
        for y in range(h):
            for x in range(w):
                self.set(x0 + x, y0 + y, fn(x, y, w, h))

    def box(self, u, v, w, h, d, **faces):
        """faces: top, bottom, right, front, left, back -> fn(x, y, w, h) returning a colour (or None)."""
        regions = {
            "top": (u + d, v, w, d),
            "bottom": (u + d + w, v, w, d),
            "right": (u, v + d, d, h),
            "front": (u + d, v + d, w, h),
            "left": (u + d + w, v + d, d, h),
            "back": (u + 2 * d + w, v + d, w, h),
        }
        default = faces.get("all")
        for name, (x0, y0, fw, fh) in regions.items():
            fn = faces.get(name, faces.get("sides") if name in ("right", "left", "back") else None) or default
            if fn is not None:
                self.rect(x0, y0, fw, fh, fn)

    def noise(self, color, amount=0.08):
        def fn(x, y, w, h):
            return shade(color, 1.0 + self.rand.uniform(-amount, amount))
        return fn


def solid(color):
    return lambda x, y, w, h: color


def cloth(painter, base, trim=None, trim_rows=(), trim_cols=(), amount=0.07):
    """Woven-looking cloth with optional trim rows/columns."""
    def fn(x, y, w, h):
        c = base
        if trim is not None and ((y in trim_rows or (y - h) in trim_rows) or (x in trim_cols or (x - w) in trim_cols)):
            c = trim
        weave = 0.96 if (x + y) % 2 == 0 else 1.04
        return shade(c, weave * (1.0 + painter.rand.uniform(-amount, amount)))
    return fn


# ------------------------------------------------------------------------------------------------ villagers

def villager_texture(seed, robe, trim, sash, skin, hair, beard, hood=None, eye="#2E7D32"):
    p = Painter(64, 64, seed)
    skin_c, robe_c, trim_c, sash_c = hexc(skin), hexc(robe), hexc(trim), hexc(sash)
    hair_c = hexc(hair)
    beard_c = hexc(beard) if beard else None
    eye_c = hexc(eye)

    def face(x, y, w, h):
        c = shade(skin_c, 1.0 + p.rand.uniform(-0.04, 0.04))
        if y <= 1:
            return shade(hair_c, 0.95 + 0.1 * p.rand.random())
        if y == 2 and x in (1, 2, 5, 6):
            return shade(hair_c, 0.8)  # brows
        if y == 3:
            if x in (1, 6):
                return hexc("#F2F2F2")
            if x in (2, 5):
                return eye_c
        if beard_c is not None and y >= 6:
            if y >= 7 or x <= 1 or x >= 6:
                return shade(beard_c, 0.95 + 0.1 * p.rand.random())
        return c

    def head_side(x, y, w, h):
        if y <= 2:
            return shade(hair_c, 0.9 + 0.1 * p.rand.random())
        if beard_c is not None and y >= 6 and x >= w - 3:
            return shade(beard_c, 0.95)
        return shade(skin_c, 0.92 + 0.06 * p.rand.random())

    p.box(0, 0, 8, 10, 8, top=p.noise(hair_c), bottom=p.noise(shade(skin_c, 0.8)), front=face, right=head_side,
          left=lambda x, y, w, h: head_side(w - 1 - x, y, w, h), back=lambda x, y, w, h: shade(hair_c, 0.9 + 0.1 * p.rand.random()))
    # nose
    p.box(24, 0, 2, 4, 2, all=p.noise(shade(skin_c, 0.95), 0.03))
    # hood / hat overlay
    if hood:
        hood_c = hexc(hood)

        def hood_front(x, y, w, h):
            if 1 <= x <= w - 2 and 2 <= y <= h - 1:
                return None  # face opening
            return cloth(p, hood_c)(x, y, w, h)
        p.box(32, 0, 8, 10, 8, top=cloth(p, hood_c), front=hood_front, sides=cloth(p, hood_c, trim_c, trim_rows=(-1,)))
    # body
    p.box(16, 20, 8, 12, 6, all=cloth(p, robe_c, trim_c, trim_cols=(3, 4)), top=cloth(p, robe_c))
    # robe overlay (long jacket)
    def robe(x, y, w, h):
        c = robe_c
        if y in (8, 9):
            c = sash_c
        if x in (3, 4) and y > 9:
            c = trim_c
        if y >= h - 2:
            c = trim_c
        return shade(c, (0.96 if (x + y) % 2 == 0 else 1.04) * (1.0 + p.rand.uniform(-0.05, 0.05)))
    p.box(0, 38, 8, 20, 6, front=robe, back=robe, sides=lambda x, y, w, h: robe(0 if x < w // 2 else 7, y, w, h),
          top=cloth(p, robe_c), bottom=cloth(p, trim_c))
    # arms (crossed)
    sleeve = cloth(p, shade(robe_c, 0.9), trim_c, trim_rows=(-1,))
    p.box(44, 22, 4, 8, 4, all=sleeve)
    p.box(40, 38, 8, 4, 4, all=sleeve, front=lambda x, y, w, h: shade(skin_c, 0.95) if x in (0, 1, 6, 7) else sleeve(x, y, w, h))
    # legs
    p.box(0, 22, 4, 12, 4, all=cloth(p, shade(robe_c, 0.75)), bottom=solid(hexc("#3B2A1A")))
    return p.img


# ------------------------------------------------------------------------------------------------ humanoids

def humanoid_texture(seed, skin, hair, shirt, coat, trousers, boots, belt, hood=None, mask=None, eye="#3B3B3B",
                     beard=None, scar=False, gold=None, cape=None):
    p = Painter(64, 64, seed)
    S, H, SH, CO, TR, BO, BE = (hexc(c) for c in (skin, hair, shirt, coat, trousers, boots, belt))
    EYE = hexc(eye)
    BEARD = hexc(beard) if beard else None
    GOLD = hexc(gold) if gold else None

    def face(x, y, w, h):
        if y <= 1:
            return shade(H, 0.9 + 0.15 * p.rand.random())
        if y == 2 and x in (0, 7):
            return shade(H, 0.85)
        if y == 3:
            if x in (1, 5):
                return hexc("#F2F2F2")
            if x in (2, 6):
                return EYE
        if scar and x == 5 and 2 <= y <= 5:
            return hexc("#8E4B4B")
        if BEARD is not None and y >= 5 and not (y == 5 and 2 <= x <= 5):
            return shade(BEARD, 0.9 + 0.15 * p.rand.random())
        if y == 6 and 2 <= x <= 5:
            return shade(S, 0.75)  # mouth
        return shade(S, 1.0 + p.rand.uniform(-0.04, 0.04))

    def head_side(x, y, w, h):
        if y <= 2 or (y <= 4 and x >= w - 2):
            return shade(H, 0.9 + 0.1 * p.rand.random())
        if BEARD is not None and y >= 5:
            return shade(BEARD, 0.9)
        return shade(S, 0.93 + 0.05 * p.rand.random())

    p.box(0, 0, 8, 8, 8, top=p.noise(H), bottom=p.noise(shade(S, 0.8)), front=face, right=head_side,
          left=lambda x, y, w, h: head_side(w - 1 - x, y, w, h), back=lambda x, y, w, h: shade(H, 0.85 + 0.15 * p.rand.random()))

    # Head overlay: hood and/or mask
    if hood or mask:
        HO = hexc(hood) if hood else None
        MA = hexc(mask) if mask else None

        def over_front(x, y, w, h):
            if MA is not None and y >= 5:
                return shade(MA, 0.95 + 0.1 * p.rand.random())
            if HO is not None and (y <= 1 or x == 0 or x == w - 1):
                return cloth(p, HO)(x, y, w, h)
            return None

        def over_side(x, y, w, h):
            if MA is not None and y >= 5:
                return shade(MA, 0.9)
            if HO is not None:
                return cloth(p, HO)(x, y, w, h)
            return None
        p.box(32, 0, 8, 8, 8, top=(cloth(p, HO) if HO else None), front=over_front, sides=over_side)

    # Body: shirt under a coat with a belt
    def torso(x, y, w, h):
        if y in (8, 9):
            c = BE
            if GOLD is not None and y == 8 and x in (3, 4):
                c = GOLD
            return shade(c, 0.95 + 0.1 * p.rand.random())
        if 2 <= x <= 5 and y < 8:
            return cloth(p, SH)(x, y, w, h)
        return cloth(p, CO)(x, y, w, h)
    p.box(16, 16, 8, 12, 4, front=torso, back=cloth(p, CO), sides=cloth(p, CO), top=cloth(p, CO), bottom=cloth(p, TR))
    # Jacket overlay: collar and coat skirt
    def jacket(x, y, w, h):
        if y >= 10 and not (2 <= x <= 5):
            return cloth(p, shade(CO, 0.9))(x, y, w, h)
        if y == 0:
            return cloth(p, shade(CO, 1.1))(x, y, w, h)
        return None
    p.box(16, 32, 8, 12, 4, front=jacket, sides=lambda x, y, w, h: cloth(p, shade(CO, 0.9))(x, y, w, h) if y >= 10 else None,
          back=lambda x, y, w, h: cloth(p, shade(CO, 0.9))(x, y, w, h) if y >= 6 else None)
    if cape:
        CA = hexc(cape)
        p.box(16, 32, 8, 12, 4, back=lambda x, y, w, h: cloth(p, CA, GOLD, trim_rows=(-1,))(x, y, w, h))

    # Arms: coat sleeves, bare hands at the bottom
    def arm(x, y, w, h):
        if y >= h - 2:
            return shade(S, 0.95 + 0.05 * p.rand.random())
        if y == h - 3:
            return shade(CO, 0.75)
        return cloth(p, CO)(x, y, w, h)
    for u, v in ((40, 16), (32, 48)):
        p.box(u, v, 4, 12, 4, front=arm, back=arm, sides=arm, top=cloth(p, CO), bottom=solid(shade(S, 0.9)))

    # Legs: trousers into boots
    def leg(x, y, w, h):
        if y >= h - 4:
            return shade(BO, 0.9 + 0.15 * p.rand.random())
        return cloth(p, TR)(x, y, w, h)
    for u, v in ((0, 16), (16, 48)):
        p.box(u, v, 4, 12, 4, front=leg, back=leg, sides=leg, top=cloth(p, TR), bottom=solid(shade(BO, 0.7)))
    return p.img


# ------------------------------------------------------------------------------------------------ village guards

GUARD_STEEL = "#8A8F96"


def mail(p, base, amount=0.06):
    """Riveted mail: rings in alternating rows."""
    def fn(x, y, w, h):
        ring = 1.12 if (x + (y % 2)) % 2 == 0 else 0.86
        return shade(base, ring * (1.0 + p.rand.uniform(-amount, amount)))
    return fn


def guard_texture(seed, rank, skin, hair, eye="#3B3B3B", beard=None):
    """A guard of the village watch on the player skin layout. The tabard is a separate, tinted layer; the chest
    emblem here shows through the hole the tabard leaves for it."""
    p = Painter(64, 64, seed)
    S, H, EYE = hexc(skin), hexc(hair), hexc(eye)
    STEEL, DARK, LEATHER, GOLD = hexc(GUARD_STEEL), hexc("#4A4E55"), hexc("#5A4128"), hexc("#D4AF37")
    BEARD = hexc(beard) if beard else None
    archer = rank == "archer"
    sergeant = rank == "sergeant"
    HOOD = hexc("#4E5B31")
    GAMBESON = hexc("#B8A27C")

    def face(x, y, w, h):
        if y <= 1:
            return shade(H, 0.9 + 0.15 * p.rand.random())
        if y == 3:
            if x in (1, 5):
                return hexc("#F2F2F2")
            if x in (2, 6):
                return EYE
        if BEARD is not None and y >= 5 and not (y == 5 and 2 <= x <= 5):
            return shade(BEARD, 0.9 + 0.15 * p.rand.random())
        if y == 6 and 2 <= x <= 5:
            return shade(S, 0.75)
        return shade(S, 1.0 + p.rand.uniform(-0.04, 0.04))

    def head_side(x, y, w, h):
        if y <= 2 or (y <= 4 and x >= w - 2):
            return shade(H, 0.9 + 0.1 * p.rand.random())
        return shade(S, 0.93 + 0.05 * p.rand.random())

    p.box(0, 0, 8, 8, 8, top=p.noise(H), bottom=p.noise(shade(S, 0.8)), front=face, right=head_side,
          left=lambda x, y, w, h: head_side(w - 1 - x, y, w, h), back=lambda x, y, w, h: shade(H, 0.85 + 0.15 * p.rand.random()))

    # Helmet (hat layer): kettle hat and mail coif; the archer wears a hood; the sergeant's helm has a gold band.
    if archer:
        def hood_front(x, y, w, h):
            if y <= 1 or x == 0 or x == w - 1:
                return cloth(p, HOOD)(x, y, w, h)
            return None
        p.box(32, 0, 8, 8, 8, top=cloth(p, HOOD), front=hood_front, sides=cloth(p, HOOD))
    else:
        def helm_front(x, y, w, h):
            if y == 0:
                return shade(STEEL, 1.15)
            if y == 1:
                return shade(GOLD, 0.95) if sergeant else shade(STEEL, 0.95)
            if x == 0 or x == w - 1:
                return mail(p, DARK)(x, y, w, h) if y >= 4 else shade(STEEL, 0.85)
            if sergeant and y == 2 and x in (3, 4):
                return shade(STEEL, 0.7)  # nasal guard
            return None

        def helm_side(x, y, w, h):
            if y <= 1:
                return shade(STEEL, 1.05 if y == 0 else 0.95) if not (sergeant and y == 1) else shade(GOLD, 0.9)
            return mail(p, DARK)(x, y, w, h) if y >= 2 else None
        p.box(32, 0, 8, 8, 8, top=lambda x, y, w, h: shade(STEEL, 1.0 + 0.12 * ((x + y) % 2)), front=helm_front, sides=helm_side)

    # Body: mail (gambeson for archers) with a leather belt; the emblem sits on the chest.
    body = GAMBESON if archer else STEEL

    def torso(x, y, w, h):
        if y == 8:
            return shade(GOLD, 1.0) if x in (3, 4) else shade(LEATHER, 0.95 + 0.1 * p.rand.random())
        if 3 <= y <= 6 and 2 <= x <= 5:
            # a crown-and-shield emblem, gold on white
            if y == 3 and x in (2, 3, 4, 5) and (x + y) % 2 == 1:
                return GOLD
            if y >= 4 and (x in (2, 5) or y == 6):
                return shade(GOLD, 0.9)
            return hexc("#F4EAD0")
        return (cloth(p, body) if archer else mail(p, body))(x, y, w, h)
    p.box(16, 16, 8, 12, 4, front=torso, back=lambda x, y, w, h: (cloth(p, body) if archer else mail(p, body))(x, y, w, h),
          sides=(cloth(p, body) if archer else mail(p, body)), top=mail(p, body), bottom=cloth(p, hexc("#3E3A35")))

    # Arms: mail or padded sleeves, leather gloves; the sergeant wears steel pauldrons on the sleeve layer.
    def arm(x, y, w, h):
        if y >= h - 3:
            return shade(LEATHER, 0.9 + 0.1 * p.rand.random())
        if archer and y >= h - 5:
            return shade(LEATHER, 0.8)  # bracers
        return (cloth(p, GAMBESON) if archer else mail(p, STEEL))(x, y, w, h)
    for u, v in ((40, 16), (32, 48)):
        p.box(u, v, 4, 12, 4, front=arm, back=arm, sides=arm, top=mail(p, STEEL), bottom=solid(shade(LEATHER, 0.8)))
    if sergeant:
        def pauldron(x, y, w, h):
            if y <= 2:
                return shade(STEEL, 1.15 - 0.1 * y) if not (y == 2 and x % 2 == 0) else shade(GOLD, 0.9)
            return None
        for u, v in ((40, 32), (48, 48)):
            p.box(u, v, 4, 12, 4, front=pauldron, back=pauldron, sides=pauldron, top=lambda x, y, w, h: shade(STEEL, 1.15))

    # Legs: dark hose (mail chausses for the sergeant) and boots.
    hose = hexc("#3E3A35")

    def leg(x, y, w, h):
        if y >= h - 4:
            return shade(hexc("#2B1D12"), 0.9 + 0.15 * p.rand.random())
        return mail(p, DARK)(x, y, w, h) if sergeant else cloth(p, hose)(x, y, w, h)
    for u, v in ((0, 16), (16, 48)):
        p.box(u, v, 4, 12, 4, front=leg, back=leg, sides=leg, top=cloth(p, hose), bottom=solid(shade(hexc("#2B1D12"), 0.7)))
    # A quiver strap across the archer's back
    if archer:
        p.box(16, 32, 8, 12, 4, back=lambda x, y, w, h: shade(LEATHER, 0.85) if abs((x - 1) - y * 0.6) < 0.8 else None)
    return p.img


def tabard_texture():
    """White tabard, tinted at runtime with the village's colour. Shading and a darker hem survive the tint; the belt
    and the chest emblem stay uncovered."""
    p = Painter(64, 64, 4242)
    WHITE = hexc("#FFFFFF")

    def front(x, y, w, h):
        if x == 0 or x == w - 1 or y == 8:
            return None  # mail at the sides, the belt across the middle
        if 3 <= y <= 6 and 2 <= x <= 5:
            return None  # the emblem
        c = shade(WHITE, 0.78) if x in (1, w - 2) or y >= h - 1 else WHITE
        weave = 0.94 if (x + y) % 2 == 0 else 1.0
        return shade(c, weave * (1.0 - p.rand.uniform(0, 0.05)))

    def back(x, y, w, h):
        if x == 0 or x == w - 1 or y == 8:
            return None
        c = shade(WHITE, 0.78) if x in (1, w - 2) or y >= h - 1 else WHITE
        return shade(c, (0.94 if (x + y) % 2 == 0 else 1.0) * (1.0 - p.rand.uniform(0, 0.05)))
    p.box(16, 16, 8, 12, 4, front=front, back=back)

    # The skirt of the tabard hangs over the hips on the jacket layer.
    def skirt(x, y, w, h):
        if y < 9 or x == 0 or x == w - 1:
            return None
        return shade(WHITE, (0.8 if y == h - 1 else 0.97) * (1.0 - p.rand.uniform(0, 0.05)))
    p.box(16, 32, 8, 12, 4, front=skirt, back=skirt)
    return p.img


def guard_textures():
    save(guard_texture(21, "swordsman", skin="#C68E6B", hair="#4E342E", beard="#4E342E"), "entity", "guard", "swordsman.png")
    save(guard_texture(22, "archer", skin="#D1A07E", hair="#8D6E63", eye="#2E7D32"), "entity", "guard", "archer.png")
    save(guard_texture(23, "sergeant", skin="#B97A57", hair="#1B1B1B", beard="#212121"), "entity", "guard", "sergeant.png")
    save(tabard_texture(), "entity", "guard", "tabard.png")


# ------------------------------------------------------------------------------------------------ armour layers

def armor_layer(kind):
    p = Painter(64, 32, hash(kind) & 0xFFFF)
    if kind == "rogue_1":
        HO, CL, ST = hexc("#2F3A33"), hexc("#26262B"), hexc("#5A4128")

        def hood_front(x, y, w, h):
            if 1 <= x <= w - 2 and y >= 2:
                return None
            return cloth(p, HO)(x, y, w, h)
        p.box(0, 0, 8, 8, 8, top=cloth(p, HO), front=hood_front, sides=cloth(p, HO))
        def cloak(x, y, w, h):
            if x in (1, 2) and y % 3 != 2:
                return shade(ST, 0.95)
            return cloth(p, CL)(x, y, w, h)
        p.box(16, 16, 8, 12, 4, all=cloak)
        p.box(40, 16, 4, 12, 4, all=cloth(p, CL, ST, trim_rows=(-1,)))
        # boots occupy the bottom of the legs
        p.box(0, 16, 4, 12, 4, all=lambda x, y, w, h: shade(hexc("#3B2A1A"), 0.9 + 0.15 * p.rand.random()) if y >= h - 4 else None)
    elif kind == "rogue_2":
        TR = hexc("#2B2F2A")
        p.box(16, 16, 8, 12, 4, all=lambda x, y, w, h: cloth(p, TR)(x, y, w, h) if y >= 8 else None)
        p.box(0, 16, 4, 12, 4, all=cloth(p, TR))
    elif kind == "tyrant_1":
        GO, GE, RU = hexc("#D4AF37"), hexc("#B8860B"), hexc("#B71C1C")

        def band(x, y, w, h):
            if y <= 1:
                return RU if x % 3 == 1 and y == 1 else GO
            if y == 2:
                return GE
            return None
        p.box(0, 0, 8, 8, 8, top=lambda x, y, w, h: GO if (x in (0, w - 1) or y in (0, h - 1)) else None, front=band, sides=band)
        # spikes on the overlay layer
        def spikes(x, y, w, h):
            if y == 0 and x % 2 == 0:
                return GO
            if y == 1:
                return GO if x % 4 != 3 else RU
            return None
        p.box(32, 0, 8, 8, 8, front=spikes, sides=spikes)
    elif kind == "tyrant_2":
        pass
    return p.img


# ------------------------------------------------------------------------------------------------ items (ASCII art)

ITEMS = {
    "ledger": ("""
................
...bbbbbbbbbbb..
..bBBBBBBBBBBBb.
..bBpppppppppBb.
..bBpLLLLLLLpBb.
..bBpppppppppBb.
..bBpLLLLLLppBb.
..bBpppppppppBb.
..bBppppGGppBBb.
..bBpppGEEGpBBb.
..bBppppGGpppBb.
..bBpLLLLLLLpBb.
..bBpppppppppBb.
..bBBBBBBBBBBBb.
...bbbbbbbbbbb..
................""", {"b": "#3B2A1A", "B": "#6D4C2F", "p": "#E8D9B0", "L": "#9C8A68", "G": "#D4AF37", "E": "#2EB872"}),
    "sealed_letter": ("""
................
................
.oooooooooooooo.
.oppppppppppppo.
.oPpppppppppPpo.
.opPpppppppPppo.
.oppPppppppPppo.
.opppPppRRPpppo.
.ppppPPRrrRpppo.
.oppppPRrrRpppo.
.opppppRRRppppo.
.oppppppppppppo.
.oooooooooooooo.
................
................
................""", {"o": "#8D7B5A", "p": "#EFE3C2", "P": "#C9B78F", "R": "#8E1B1B", "r": "#C62828"}),
    "elders_mantle": ("""
................
................
...GGGGGGGGGG...
..GPPPPPPPPPPG..
..GPpPPPPPPpPG..
..GPPPPPPPPPPG..
..GPPPpPPpPPPG..
..GPPPPPPPPPPG..
..GPPPPPPPPPPG..
..GPpPPPPPPpPG..
..GPPPPPPPPPPG..
..GGGGGGGGGGGG..
...gggggggggg...
................
................
................""", {"G": "#D4AF37", "g": "#9C7A1E", "P": "#5B3A7A", "p": "#7E57A2"}),
    "signet_of_the_old_crown": ("""
................
................
......GGGG......
.....GgggGG.....
....GgRRRRgG....
....GgRrrRgG....
....GgRrrRgG....
....GgRRRRgG....
.....GggggG.....
.....GG..GG.....
....GG....GG....
....G......G....
....GG....GG....
.....GGGGGG.....
................
................""", {"G": "#D4AF37", "g": "#B8860B", "R": "#7B1F1F", "r": "#C0392B"}),
    "keepers_charter": ("""
................
..wwwwwwwwwwww..
.wWppppppppppWw.
..wppppppppppw..
..wpLLLLLLLLpw..
..wppppppppppw..
..wpLLLLLLppppw.
..wppppppppppw..
..wpLLLLLLLLpw..
..wppppGGppppw..
..wpppGggGpppw..
..wppppGGppppw..
.wWppppNNppppWw.
..wwwwwNNwwwww..
.......N.N......
................""", {"w": "#8D7B5A", "W": "#6D5B3A", "p": "#EFE3C2", "L": "#9C8A68", "G": "#2E7D32", "g": "#66BB6A",
                     "N": "#1B5E20"}),
    "royal_writ": ("""
................
..wwwwwwwwwwww..
.wWppppppppppWw.
..wppppppppppw..
..wpLLLLLLLLpw..
..wppppppppppw..
..wpLLLLLLLLpw..
..wppppppppppw..
..wpLLLLLLppppw.
..wppppGGppppw..
..wpppGRRGpppw..
..wppppGGppppw..
.wWppppPPppppWw.
..wwwwwPPwwwww..
.......P.P......
................""", {"w": "#8D7B5A", "W": "#6D5B3A", "p": "#F5EAC8", "L": "#9C8A68", "G": "#D4AF37", "R": "#B71C1C",
                     "P": "#5B3A7A"}),
    "lords_horn": ("""
................
................
...........hh...
..........hHHh..
.........hHHh...
........hHHh....
.......GGGh.....
......hHHGh.....
.....hHHHh......
....hHHGGh......
...hHHHGh.......
..hHHHHh........
.hHHHHh.........
.hHhhh..........
..h.............
................""", {"h": "#5D4037", "H": "#D7CCC8", "G": "#D4AF37"}),
    "rogue_hood": ("""
................
................
.....HHHHHH.....
....HHHHHHHH....
...HHHhhhhHHH...
...HHh....hHH...
...Hh......hH...
...Hh......hH...
...Hh......hH...
...HHh....hHH...
....HHHHHHHH....
.....HHHHHH.....
......HHHH......
................
................
................""", {"H": "#2F3A33", "h": "#1C231F"}),
    "rogue_cloak": ("""
................
....CCC..CCC....
...CCCCSSCCCC...
...CCCCSSCCCC...
..CCCCCSSCCCCC..
..CCCCCCCCCCCC..
..CCCCCCCCCCCC..
..CCCBBBBBBCCC..
..CCCCCCCCCCCC..
..CCCCCCCCCCCC..
..CCCCCCCCCCCC..
...CCCCCCCCCC...
...CCCCCCCCCC...
................
................
................""", {"C": "#26262B", "S": "#5A4128", "B": "#3E2B1C"}),
    "rogue_leggings": ("""
................
................
....TTTTTTTT....
....TTBBBBTT....
....TTTTTTTT....
....TTT..TTT....
....TTT..TTT....
....TTT..TTT....
....TTT..TTT....
....TTT..TTT....
....TTT..TTT....
....ttt..ttt....
................
................
................
................""", {"T": "#2B2F2A", "t": "#1B1E1A", "B": "#5A4128"}),
    "rogue_boots": ("""
................
................
................
................
................
....bbb..bbb....
....bbb..bbb....
....bbb..bbb....
....bbb..bbb....
...bbbb..bbbb...
...BBBB..BBBB...
................
................
................
................
................""", {"b": "#3B2A1A", "B": "#22180F"}),
    "lockpick": ("""
................
................
................
............ii..
...........iI...
..........iI....
.........iI.....
........iI......
.......iI.......
......iI........
.....wwI........
....wWWw........
...wWWw.........
...www..........
................
................""", {"i": "#9E9E9E", "I": "#E0E0E0", "w": "#5D4037", "W": "#8D6E63"}),
    "smoke_bomb": ("""
................
..........f.....
.........f......
........sF......
.......ssss.....
.....gggggggg...
....gGGgggggggg.
....gGgggggggg..
....gggggggggg..
....gggggggggg..
....ggggggggdg..
.....ggggggdd...
......gggggg....
................
................
................""", {"f": "#FF8F00", "F": "#6D4C41", "s": "#9E9E9E", "g": "#455A64", "G": "#78909C", "d": "#263238"}),
    "bounty_notice": ("""
................
..pppppppppppp..
..pPPPPPPPPPPp..
..pPkkkkkkkkPp..
..pPPPPPPPPPPp..
..pPPPsssPPPPp..
..pPPsSSSsPPPp..
..pPPsSSSsPPPp..
..pPPPsssPPPPp..
..pPPRPPPPRPPp..
..pPPPRPPRPPPp..
..pPPPPRRPPPPp..
..pPPPRPPRPPPp..
..pPkkkkkkkkPp..
..pppppppppppp..
................""", {"p": "#C9B78F", "P": "#EFE3C2", "k": "#3B2A1A", "s": "#8D6E63", "S": "#A1887F", "R": "#B71C1C"}),
    "tyrant_crown": ("""
................
................
..G...G..G...G..
..GG..GG.GG..GG.
..GGGGGGGGGGGG..
..GRGGGRGGGRGG..
..GgGGGgGGGgGG..
..GGGGGGGGGGGG..
..gggggggggggg..
................
................
................
................
................
................
................""", {"G": "#D4AF37", "g": "#9C7A1E", "R": "#C62828"}),
}


def ascii_item(art, palette):
    rows = [r for r in art.strip("\n").split("\n")]
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    for y, row in enumerate(rows[:16]):
        for x, ch in enumerate(row[:16]):
            if ch in palette:
                px[x, y] = hexc(palette[ch])
    return img


# ------------------------------------------------------------------------------------------------ blocks

def planks(seed, base, dark):
    p = Painter(16, 16, seed)
    b, d = hexc(base), hexc(dark)
    for y in range(16):
        for x in range(16):
            c = shade(b, 1.0 + p.rand.uniform(-0.06, 0.06))
            if y % 4 == 3:
                c = shade(d, 1.0)
            if (x + (y // 4) * 5) % 16 == 0 and y % 4 != 3:
                c = shade(d, 1.1)
            p.set(x, y, c)
    return p


def coffer_textures():
    iron, rivet = hexc("#5F6368"), hexc("#B0BEC5")
    side = planks(11, "#6D4C2F", "#3E2B1C")
    for y in range(16):
        for x in (0, 15):
            side.set(x, y, iron)
        if y in (0, 1, 14, 15):
            for x in range(16):
                side.set(x, y, iron)
    for (x, y) in ((1, 1), (14, 1), (1, 14), (14, 14)):
        side.set(x, y, rivet)
    front = Painter(16, 16, 12)
    front.img.paste(side.img)
    front.px = front.img.load()
    for y in range(5, 11):
        for x in range(6, 10):
            front.set(x, y, hexc("#D4AF37") if (x in (6, 9) or y in (5, 10)) else hexc("#9C7A1E"))
    front.set(7, 7, hexc("#212121"))
    front.set(8, 7, hexc("#212121"))
    front.set(7, 8, hexc("#212121"))
    top = planks(13, "#7A5634", "#3E2B1C")
    for x in range(16):
        top.set(x, 7, iron)
        top.set(x, 8, iron)
    for y in range(16):
        top.set(0, y, iron)
        top.set(15, y, iron)
    return side.img, front.img, top.img


def rubble_texture():
    p = Painter(16, 16, 21)
    stones = ["#7A7A7A", "#8C8C8C", "#6B6B6B", "#9E9A8F", "#6E5B4B"]
    for y in range(16):
        for x in range(16):
            p.set(x, y, shade(hexc("#4E4A44"), 1.0 + p.rand.uniform(-0.1, 0.1)))
    for _ in range(14):
        cx, cy, r = p.rand.randrange(16), p.rand.randrange(16), p.rand.choice((1, 2, 2, 3))
        c = hexc(p.rand.choice(stones))
        for y in range(cy - r, cy + r + 1):
            for x in range(cx - r, cx + r + 1):
                if (x - cx) ** 2 + (y - cy) ** 2 <= r * r:
                    p.set(x % 16, y % 16, shade(c, 1.0 + p.rand.uniform(-0.08, 0.08)) if not (y == cy + r) else shade(c, 0.7))
    return p.img


def standard_textures():
    pole = Painter(16, 16, 31)
    for y in range(16):
        for x in range(16):
            pole.set(x, y, shade(hexc("#4E342E"), 0.9 + 0.2 * pole.rand.random() if x % 4 else 0.8))
    cloth_p = Painter(16, 32, 32)
    red, dark, bone = hexc("#8E1B1B"), hexc("#4A0E0E"), hexc("#E0D8C8")
    for y in range(32):
        for x in range(16):
            c = red if (x + y) % 2 else shade(red, 0.94)
            if x in (0, 15) or y >= 30:
                c = dark
            cloth_p.set(x, y, shade(c, 1.0 + cloth_p.rand.uniform(-0.05, 0.05)))
    skull = [
        "..XXXX..",
        ".XXXXXX.",
        "XX.XX.XX",
        "XX.XX.XX",
        "XXXXXXXX",
        ".XX..XX.",
        "..XXXX..",
        "..X.X.X.",
    ]
    for y, row in enumerate(skull):
        for x, ch in enumerate(row):
            if ch == "X":
                cloth_p.set(4 + x, 8 + y, bone)
    for i in range(10):
        cloth_p.set(3 + i, 18 + i // 2 if i < 10 else 0, bone)
        cloth_p.set(12 - i, 18 + i // 2, bone)
    # tattered bottom edge
    for x in range(1, 15, 3):
        cloth_p.set(x, 29, (0, 0, 0, 0))
        cloth_p.set(x, 28, (0, 0, 0, 0))
    return pole.img, cloth_p.img


def mailbox_textures():
    """The mailbox: a painted box on a wooden post, with a door on the front and a red flag."""
    rand = random.Random(77)
    PAINT, PAINT_DARK, BRASS = hexc("#2F4A6D"), hexc("#22364F"), hexc("#C8A050")
    WOOD, WOOD_DARK = hexc("#8B6A42"), hexc("#5E4528")

    def painted(seed):
        img = Image.new("RGBA", (16, 16))
        px = img.load()
        r = random.Random(seed)
        for y in range(16):
            for x in range(16):
                c = PAINT_DARK if y in (0, 15) or x in (0, 15) else PAINT
                px[x, y] = shade(c, 1.0 + r.uniform(-0.05, 0.05))
        return img, px

    side, sp = painted(1)
    for x, y in ((3, 6), (12, 6), (3, 10), (12, 10)):
        sp[x, y] = shade(PAINT, 1.35)  # rivets
    front, fp = painted(2)
    # the door: an outline in the middle of the face, a brass handle and the letter slot
    for x in range(4, 12):
        fp[x, 6] = shade(PAINT_DARK, 0.85)
        fp[x, 10] = shade(PAINT_DARK, 0.85)
    for y in range(6, 11):
        fp[4, y] = shade(PAINT_DARK, 0.85)
        fp[11, y] = shade(PAINT_DARK, 0.85)
    for x in range(6, 10):
        fp[x, 7] = hexc("#141414")
    fp[10, 8] = BRASS
    fp[10, 9] = shade(BRASS, 0.8)
    top, tp = painted(3)
    for x in range(16):
        tp[x, 7] = shade(PAINT, 1.15)
        tp[x, 8] = shade(PAINT, 1.1)
    post = Image.new("RGBA", (16, 16))
    pp = post.load()
    for y in range(16):
        for x in range(16):
            grain = 0.9 if (x * 3 + y // 4) % 5 == 0 else 1.0
            pp[x, y] = shade(WOOD_DARK if x % 8 in (0, 7) else WOOD, grain * (1.0 + rand.uniform(-0.05, 0.05)))
    flag = Image.new("RGBA", (16, 16))
    gp = flag.load()
    for y in range(16):
        for x in range(16):
            edge = x in (0, 15) or y in (0, 15)
            gp[x, y] = shade(hexc("#B71C1C"), (0.75 if edge else 1.0) * (1.0 + rand.uniform(-0.04, 0.04)))
    save(side, "block", "mailbox_side.png")
    save(front, "block", "mailbox_front.png")
    save(top, "block", "mailbox_top.png")
    save(post, "block", "mailbox_post.png")
    save(flag, "block", "mailbox_flag.png")


# ------------------------------------------------------------------------------------------------ GUIs

def lockpick_gui():
    """The lockpicking screen sheet (256x128):
    iron lock plate 112x112 at (0,0); brass cylinder 64x64 at (128,0); slider knob 18x18 at (192,0) and its lit
    twin at (212,0); the pick 84x10 at (0,112), tip at the left; the tension wrench 48x8 at (96,112)."""
    sheet = Image.new("RGBA", (256, 128), (0, 0, 0, 0))
    rand = random.Random(91)
    IRON, IRON_DARK, BRASS = hexc("#5A5F66"), hexc("#2B2F34"), hexc("#B8903A")

    def lit(x, y, c):
        # light from the upper left
        return 1.12 - 0.22 * (((x - c) + (y - c)) / (2 * c) + 0.5)

    plate = Image.new("RGBA", (112, 112), (0, 0, 0, 0))
    pp = plate.load()
    c = 55.5
    for y in range(112):
        for x in range(112):
            d = ((x - c) ** 2 + (y - c) ** 2) ** 0.5
            if d > 55.5:
                continue
            if d > 53:
                col = shade(IRON_DARK, 0.7)
            elif d > 50.5:
                col = shade(IRON, 1.25 if (x + y) < 2 * c else 0.85)
            elif d > 47 and d < 48:
                col = shade(IRON, 0.7)  # engraved ring
            elif d > 34:
                ang = math.atan2(y - c, x - c)
                col = shade(IRON, (0.96 + 0.03 * math.sin(ang * 24)) * lit(x, y, c))  # brushed
            elif d > 32:
                col = shade(IRON_DARK, 0.6)
            else:
                continue  # the cylinder sits in the hole
            pp[x, y] = shade(col, 1.0 + rand.uniform(-0.04, 0.04))
    for angle in range(0, 360, 45):
        rx = c + 41.5 * math.cos(math.radians(angle + 22.5))
        ry = c + 41.5 * math.sin(math.radians(angle + 22.5))
        for y in range(int(ry) - 3, int(ry) + 4):
            for x in range(int(rx) - 3, int(rx) + 4):
                d = ((x - rx) ** 2 + (y - ry) ** 2) ** 0.5
                if d <= 2.6:
                    hl = 1.5 if (x - rx) + (y - ry) < -1 else 1.1 if d < 1.6 else 0.8
                    pp[x, y] = shade(IRON, hl)
                elif d <= 3.4:
                    pp[x, y] = shade(IRON_DARK, 0.8)
    sheet.paste(plate, (0, 0))

    cyl = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    cp = cyl.load()
    c = 31.5
    for y in range(64):
        for x in range(64):
            d = ((x - c) ** 2 + (y - c) ** 2) ** 0.5
            if d > 31.5:
                continue
            if d > 29.5:
                col = shade(BRASS, 0.62)
            elif d > 27.5:
                col = shade(BRASS, 1.3 if (x + y) < 2 * c else 0.85)
            else:
                col = shade(BRASS, lit(x, y, c) * (1.08 - 0.006 * d))
            # the keyhole: a round top and a slot below it, with a lit lower lip
            hole = ((x - c) ** 2 + (y - 25) ** 2) ** 0.5 <= 5.2 or (abs(x - c) <= 2.6 and 25 <= y <= 42)
            lip = not hole and (((x - c) ** 2 + (y - 26) ** 2) ** 0.5 <= 6.4 or (abs(x - c) <= 3.8 and 25 <= y <= 43.5))
            if hole:
                col = hexc("#0E0B08")
            elif lip:
                col = shade(BRASS, 0.7 if y < 25 else 1.35)
            cp[x, y] = shade(col, 1.0 + rand.uniform(-0.03, 0.03))
    sheet.paste(cyl, (128, 0))

    for i, glow in enumerate((False, True)):
        knob = Image.new("RGBA", (18, 18), (0, 0, 0, 0))
        kp = knob.load()
        c = 8.5
        for y in range(18):
            for x in range(18):
                d = ((x - c) ** 2 + (y - c) ** 2) ** 0.5
                if d > 8.6:
                    continue
                base = hexc("#F2D675") if glow else BRASS
                if d > 7.4:
                    col = shade(hexc("#3B2A1A"), 1.0)
                elif d > 5.6:
                    ang = math.atan2(y - c, x - c)
                    col = shade(base, (0.85 + 0.25 * (math.sin(ang * 8) > 0)) * lit(x, y, c))  # knurled rim
                else:
                    col = shade(base, lit(x, y, c) * (1.15 - 0.03 * d))
                kp[x, y] = col
        sheet.paste(knob, (192 + 20 * i, 0))

    # The pick, tip at the left: hook, shaft, then a leather-wrapped grip.
    pick = Image.new("RGBA", (84, 10), (0, 0, 0, 0))
    kp = pick.load()
    STEEL, STEEL_DARK, LEATHER = hexc("#C3C9D0"), hexc("#3A3F45"), hexc("#6D4C2F")
    for x in range(2, 56):
        kp[x, 4] = shade(STEEL_DARK, 1.0)
        kp[x, 5] = shade(STEEL, 1.15)
        kp[x, 6] = shade(STEEL, 0.8)
        kp[x, 7] = shade(STEEL_DARK, 1.0)
    for y in range(1, 6):  # the hook
        kp[1, y] = STEEL_DARK
        kp[2, y] = shade(STEEL, 1.1)
        kp[3, y] = STEEL_DARK
    kp[2, 0] = STEEL_DARK
    for x in range(54, 84):
        for y in range(1, 10):
            edge = y in (1, 9) or x in (54, 83)
            wrap = (x + y) % 5 == 0
            col = shade(LEATHER, 0.55) if edge else shade(LEATHER, 0.8 if wrap else 1.05 - 0.04 * abs(y - 5))
            kp[x, y] = col
    for y in range(2, 9):  # brass ferrule
        kp[55, y] = shade(BRASS, 1.2)
        kp[56, y] = shade(BRASS, 0.85)
    sheet.paste(pick, (0, 112))

    # The tension wrench: an L of dark steel, the short leg at the left.
    wrench = Image.new("RGBA", (48, 8), (0, 0, 0, 0))
    wp = wrench.load()
    for x in range(0, 48):
        wp[x, 2] = STEEL_DARK
        wp[x, 3] = shade(STEEL, 1.1)
        wp[x, 4] = shade(STEEL, 0.75)
        wp[x, 5] = STEEL_DARK
    for y in range(0, 8):
        wp[0, y] = STEEL_DARK
        wp[1, y] = shade(STEEL, 1.0)
        wp[2, y] = STEEL_DARK
    sheet.paste(wrench, (96, 112))
    save(sheet, "gui", "lockpick.png")


def parchment(w, h, seed, border="#5D4037"):
    p = Painter(w, h, seed)
    base = hexc("#E8D9B0")
    for y in range(h):
        for x in range(w):
            edge = min(x, y, w - 1 - x, h - 1 - y)
            f = 1.0 + p.rand.uniform(-0.035, 0.035)
            if edge < 10:
                f *= 0.86 + 0.014 * edge
            p.set(x, y, shade(base, f))
    b = hexc(border)
    for i in range(3):
        for x in range(i, w - i):
            p.set(x, i, shade(b, 1.0 - 0.1 * i))
            p.set(x, h - 1 - i, shade(b, 1.0 - 0.1 * i))
        for y in range(i, h - i):
            p.set(i, y, shade(b, 1.0 - 0.1 * i))
            p.set(w - 1 - i, y, shade(b, 1.0 - 0.1 * i))
    return p


def ledger_gui():
    canvas = Image.new("RGBA", (256, 256), (0, 0, 0, 0))
    page = parchment(248, 200, 41)
    d = ImageDraw.Draw(page.img)
    d.line([(14, 22), (234, 22)], fill=hexc("#9C8A68"), width=1)
    d.line([(14, 50), (234, 50)], fill=hexc("#9C8A68"), width=1)
    canvas.paste(page.img, (0, 0))
    return canvas


def quest_book_gui():
    canvas = Image.new("RGBA", (512, 256), (0, 0, 0, 0))
    w, h = 300, 210
    p = Painter(w, h, 51)
    leather = hexc("#5D3A1F")
    for y in range(h):
        for x in range(w):
            p.set(x, y, shade(leather, 1.0 + p.rand.uniform(-0.06, 0.06)))
    pages = parchment(w - 12, h - 12, 52, border="#8D6E63")
    p.img.paste(pages.img, (6, 6))
    d = ImageDraw.Draw(p.img)
    seam = hexc("#8D6E63")
    d.line([(122, 78), (122, h - 10)], fill=seam, width=1)
    d.line([(14, 76), (w - 14, 76)], fill=hexc("#B8A27C"), width=1)
    for (x, y) in ((3, 3), (w - 4, 3), (3, h - 4), (w - 4, h - 4)):
        d.rectangle([x - 2, y - 2, x + 2, y + 2], fill=hexc("#D4AF37"))
    canvas.paste(p.img, (0, 0))
    return canvas


def logo():
    img = Image.new("RGBA", (128, 128), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.polygon([(64, 120), (16, 92), (16, 22), (64, 10), (112, 22), (112, 92)], fill=hexc("#5B3A7A"), outline=hexc("#D4AF37"))
    d.polygon([(64, 112), (24, 88), (24, 28), (64, 18), (104, 28), (104, 88)], outline=hexc("#D4AF37"))
    crown = [(34, 72), (34, 44), (48, 58), (64, 36), (80, 58), (94, 44), (94, 72)]
    d.polygon(crown, fill=hexc("#D4AF37"), outline=hexc("#9C7A1E"))
    d.rectangle([34, 74, 94, 82], fill=hexc("#D4AF37"), outline=hexc("#9C7A1E"))
    for x in (46, 64, 82):
        d.ellipse([x - 3, 75, x + 3, 81], fill=hexc("#C62828"))
    return img.resize((256, 256), Image.NEAREST)


# ------------------------------------------------------------------------------------------------ Blockbench

def png_data_uri(img):
    buf = io.BytesIO()
    img.save(buf, format="PNG")
    return "data:image/png;base64," + base64.b64encode(buf.getvalue()).decode()


def bbmodel(name, elements, textures):
    tex_entries = []
    for tex_name, img in textures:
        tex_entries.append({"path": "", "name": tex_name + ".png", "folder": "block", "namespace": "fealty",
                            "id": str(len(tex_entries)), "width": img.width, "height": img.height, "uv_width": 16,
                            "uv_height": 16, "particle": False, "visible": True, "mode": "bitmap", "saved": True,
                            "uuid": str(uuid.uuid4()), "source": png_data_uri(img)})
    els = []
    for e in elements:
        faces = {}
        for face, (uv, tex) in e["faces"].items():
            faces[face] = {"uv": uv, "texture": tex}
        els.append({"name": e["name"], "box_uv": False, "rescale": False, "locked": False, "from": e["from"], "to": e["to"],
                    "autouv": 0, "color": 0, "origin": [8, 8, 8], "faces": faces, "type": "cube", "uuid": str(uuid.uuid4())})
    model = {
        "meta": {"format_version": "4.10", "model_format": "java_block", "box_uv": False},
        "name": name, "parent": "", "ambientocclusion": True, "front_gui_light": False,
        "visible_box": [1, 1, 0], "variable_placeholders": "", "variable_placeholder_buttons": [],
        "unhandled_root_fields": {}, "resolution": {"width": 16, "height": 16},
        "elements": els, "outliner": [e["uuid"] for e in els], "textures": tex_entries,
    }
    os.makedirs(BB, exist_ok=True)
    with open(os.path.join(BB, name + ".bbmodel"), "w") as f:
        json.dump(model, f, indent=1)


def main():
    # Entities
    save(villager_texture(1, robe="#5B3A7A", trim="#D4AF37", sash="#9C7A1E", skin="#B88A6E", hair="#E0E0E0", beard="#ECEFF1",
                          hood="#4A2E63"), "entity", "village_elder.png")
    save(villager_texture(2, robe="#1F4D3A", trim="#B8D4C0", sash="#5D4037", skin="#A97C62", hair="#9E9E9E", beard="#BDBDBD",
                          hood="#173B2C", eye="#455A64"), "entity", "keeper.png")
    save(humanoid_texture(3, skin="#C68E6B", hair="#3E2723", shirt="#8D6E63", coat="#5A4632", trousers="#4E4234", boots="#2B1D12",
                          belt="#3B2A1A", hood="#4A3B2A", mask="#3E2C22"), "entity", "bandit.png")
    save(humanoid_texture(4, skin="#B97A57", hair="#1B1B1B", shirt="#6D4C41", coat="#7B1F1F", trousers="#3E2C22", boots="#1B120B",
                          belt="#2B1D12", beard="#1B1B1B", scar=True, gold="#D4AF37"), "entity", "bandit_captain.png")
    save(humanoid_texture(5, skin="#D1A07E", hair="#5D4037", shirt="#BCAAA4", coat="#2B2B2B", trousers="#3E3A35", boots="#1E1A16",
                          belt="#7A5C2E", hood="#1E1E22", gold="#D4AF37"), "entity", "black_marketeer.png")
    save(humanoid_texture(6, skin="#C9A27E", hair="#212121", shirt="#424242", coat="#1E1E2A", trousers="#26262B", boots="#121212",
                          belt="#3B2A1A", hood="#14141C", mask="#14141C", eye="#9E9E9E"), "entity", "guild_fence.png")
    save(humanoid_texture(7, skin="#B07A55", hair="#4E342E", shirt="#A1887F", coat="#3C3226", trousers="#4A3F33", boots="#2B1D12",
                          belt="#C8A050", beard="#4E342E", scar=True, gold="#C8A050"), "entity", "bounty_hunter.png")
    save(humanoid_texture(8, skin="#C7987A", hair="#1B1B1B", shirt="#4A0E0E", coat="#2A1A1A", trousers="#1B1B1B", boots="#0E0E0E",
                          belt="#D4AF37", beard="#212121", gold="#D4AF37", cape="#6A1010"), "entity", "tyrant_lord.png")
    guard_textures()
    # Armour layers
    save(armor_layer("rogue_1"), "models", "armor", "rogue_layer_1.png")
    save(armor_layer("rogue_2"), "models", "armor", "rogue_layer_2.png")
    save(armor_layer("tyrant_1"), "models", "armor", "tyrant_layer_1.png")
    save(armor_layer("tyrant_2"), "models", "armor", "tyrant_layer_2.png")
    # Items
    for name, (art, palette) in ITEMS.items():
        save(ascii_item(art, palette), "item", name + ".png")
    # Blocks
    side, front, top = coffer_textures()
    save(side, "block", "village_coffer_side.png")
    save(front, "block", "village_coffer_front.png")
    save(top, "block", "village_coffer_top.png")
    save(rubble_texture(), "block", "rubble.png")
    mailbox_textures()
    lockpick_gui()
    pole, banner = standard_textures()
    save(pole, "block", "bandit_standard_pole.png")
    save(banner, "block", "bandit_standard_cloth.png")
    # GUIs and logo
    save(ledger_gui(), "gui", "ledger.png")
    save(quest_book_gui(), "gui", "quest_book.png")
    logo().save(os.path.join(ROOT, "src", "main", "resources", "fealty_logo.png"))
    # Blockbench projects for the custom block models
    bbmodel("village_coffer", [
        {"name": "box", "from": [1, 0, 1], "to": [15, 10, 15], "faces": {
            "north": ([0, 6, 16, 16], 1), "south": ([0, 6, 16, 16], 0), "east": ([0, 6, 16, 16], 0),
            "west": ([0, 6, 16, 16], 0), "down": ([0, 0, 16, 16], 2)}},
        {"name": "lid", "from": [1, 10, 1], "to": [15, 14, 15], "faces": {
            "north": ([0, 0, 16, 4], 0), "south": ([0, 0, 16, 4], 0), "east": ([0, 0, 16, 4], 0),
            "west": ([0, 0, 16, 4], 0), "up": ([0, 0, 16, 16], 2)}},
    ], [("village_coffer_side", side), ("village_coffer_front", front), ("village_coffer_top", top)])
    bbmodel("bandit_standard", [
        {"name": "pole", "from": [7, 0, 7], "to": [9, 16, 9], "faces": {
            "north": ([0, 0, 2, 16], 0), "south": ([0, 0, 2, 16], 0), "east": ([0, 0, 2, 16], 0), "west": ([0, 0, 2, 16], 0),
            "up": ([0, 0, 2, 2], 0)}},
        {"name": "crossbar", "from": [2, 14, 7.5], "to": [14, 15, 8.5], "faces": {
            "north": ([0, 0, 12, 1], 0), "south": ([0, 0, 12, 1], 0), "up": ([0, 0, 12, 1], 0), "down": ([0, 0, 12, 1], 0)}},
        {"name": "cloth", "from": [3, 1, 8.6], "to": [13, 14, 8.6], "faces": {
            "north": ([3, 0, 13, 13], 1), "south": ([3, 0, 13, 13], 1)}},
    ], [("bandit_standard_pole", pole), ("bandit_standard_cloth", banner)])
    print("art written to", TEX)


if __name__ == "__main__":
    import sys
    if sys.argv[1:] == ["guards"]:
        guard_textures()  # just the guards, leaving the other textures as they are
    elif sys.argv[1:] == ["mailbox"]:
        mailbox_textures()
    elif sys.argv[1:] == ["lockpick"]:
        lockpick_gui()
    else:
        main()
