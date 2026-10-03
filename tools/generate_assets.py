#!/usr/bin/env python3
"""Regenerates every sprite / tile / background / sound in ../assets (needs Pillow).
Animated sprites are horizontal strips; see README.md for frame sizes."""
import math, os, random, struct, wave
from PIL import Image, ImageDraw

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'assets')
SPR = os.path.join(ROOT, 'sprites'); SFX = os.path.join(ROOT, 'sfx')
os.makedirs(SPR, exist_ok=True); os.makedirs(SFX, exist_ok=True)
rnd = random.Random(1234)

def new(w, h): return Image.new('RGBA', (w, h), (0, 0, 0, 0))
def save(im, name): im.save(os.path.join(SPR, name + '.png'))
def strip(frames):
    w, h = frames[0].size
    out = new(w * len(frames), h)
    for i, f in enumerate(frames): out.paste(f, (i * w, 0))
    return out

OUT = (20, 24, 50)

# ---------------------------------------------------------------- player (32x16 x3: level, up, down)
def player(tilt):
    im = new(32, 16); d = ImageDraw.Draw(im)
    o1 = 2 if tilt == 1 else 0
    o2 = 2 if tilt == 2 else 0
    d.polygon([(8, 6), (17, 6), (11, o1), (3, o1)], fill=(80, 120, 235), outline=OUT)
    d.polygon([(8, 10), (17, 10), (11, 15 - o2), (3, 15 - o2)], fill=(45, 75, 175), outline=OUT)
    d.polygon([(2, 6), (21, 5), (31, 8), (21, 11), (2, 10)], fill=(236, 241, 252), outline=OUT)
    d.rectangle([17, 6, 22, 8], fill=(90, 230, 255))
    d.line([(6, 8), (15, 8)], fill=(150, 160, 195))
    d.rectangle([0, 7, 2, 9], fill=(255, 170, 40))
    return im
save(strip([player(0), player(1), player(2)]), 'player')

# ---------------------------------------------------------------- options (12x12 x2)
def option(b):
    im = new(12, 12); d = ImageDraw.Draw(im)
    d.ellipse([0, 0, 11, 11], fill=(255, 140 + b * 40, 30), outline=(120, 40, 10))
    d.ellipse([3, 3, 8, 8], fill=(255, 235, 140 + b * 60))
    return im
save(strip([option(0), option(1)]), 'option')

# ---------------------------------------------------------------- enemies
def fan(f):
    im = new(16, 16); d = ImageDraw.Draw(im)
    d.ellipse([1, 1, 14, 14], fill=(60, 150, 230), outline=OUT)
    if f == 0:
        d.polygon([(1, 6), (14, 6), (14, 9), (1, 9)], fill=(190, 235, 255))
    else:
        d.polygon([(6, 1), (9, 1), (9, 14), (6, 14)], fill=(190, 235, 255))
    d.ellipse([6, 6, 9, 9], fill=(255, 220, 90))
    return im
save(strip([fan(0), fan(1)]), 'fan')

def rusher(f):
    im = new(18, 12); d = ImageDraw.Draw(im)
    d.polygon([(0, 6), (14, 0), (10, 6), (14, 11)], fill=(230, 80, 80), outline=(60, 10, 10))
    d.rectangle([9, 4, 12, 7], fill=(255, 210, 120))
    d.rectangle([14 + f, 4, 17, 7], fill=(255, 150, 40))
    return im
save(strip([rusher(0), rusher(1)]), 'rusher')

def walker(f):
    im = new(16, 16); d = ImageDraw.Draw(im)
    d.rectangle([3, 3, 12, 10], fill=(150, 160, 175), outline=OUT)
    d.rectangle([4, 4, 7, 6], fill=(255, 90, 80))
    if f == 0:
        d.line([(5, 11), (3, 15)], fill=(110, 120, 140), width=2); d.line([(10, 11), (12, 15)], fill=(110, 120, 140), width=2)
    else:
        d.line([(5, 11), (6, 15)], fill=(110, 120, 140), width=2); d.line([(10, 11), (9, 15)], fill=(110, 120, 140), width=2)
    return im
save(strip([walker(0), walker(1)]), 'walker')

im = new(16, 16); d = ImageDraw.Draw(im)
d.rectangle([2, 10, 13, 15], fill=(80, 88, 110), outline=OUT)
d.ellipse([3, 4, 12, 13], fill=(150, 160, 185), outline=OUT)
d.rectangle([7, 0, 9, 7], fill=(190, 195, 215), outline=OUT)
d.rectangle([7, 7, 8, 8], fill=(255, 90, 80))
save(im, 'turret')

def capsule(b):
    im = new(12, 12); d = ImageDraw.Draw(im)
    d.ellipse([0, 0, 11, 11], fill=(225 + b * 25, 40 + b * 40, 40), outline=(255, 205, 205))
    d.rectangle([3, 2, 5, 4], fill=(255, 255, 255))
    return im
save(strip([capsule(0), capsule(1)]), 'capsule')

# ---------------------------------------------------------------- projectiles
im = new(12, 4); d = ImageDraw.Draw(im)
d.rectangle([0, 0, 11, 3], fill=(255, 235, 110)); d.rectangle([6, 1, 11, 2], fill=(255, 255, 255)); save(im, 'shot')
im = new(40, 4); d = ImageDraw.Draw(im)
d.rectangle([0, 0, 39, 3], fill=(60, 200, 255)); d.rectangle([0, 1, 39, 2], fill=(210, 250, 255)); save(im, 'laser')
im = new(8, 6); d = ImageDraw.Draw(im)
d.rectangle([0, 1, 5, 4], fill=(235, 235, 245), outline=OUT); d.rectangle([5, 1, 7, 4], fill=(240, 70, 60)); save(im, 'missile')
im = new(6, 6); d = ImageDraw.Draw(im)
d.ellipse([0, 0, 5, 5], fill=(255, 90, 160)); d.rectangle([2, 2, 3, 3], fill=(255, 245, 200)); save(im, 'ebullet')
def lava(b):
    im = new(8, 8); d = ImageDraw.Draw(im)
    d.ellipse([0, 0, 7, 7], fill=(255, 110 + b * 60, 20)); d.rectangle([2, 2, 4, 4], fill=(255, 235, 120))
    return im
save(strip([lava(0), lava(1)]), 'lava')
im = new(40, 32); d = ImageDraw.Draw(im)
d.ellipse([0, 0, 39, 31], fill=(90, 220, 255, 55), outline=(150, 240, 255, 230)); save(im, 'shield')

# ---------------------------------------------------------------- explosion (24x24 x5)
def boom(i):
    im = new(24, 24); d = ImageDraw.Draw(im)
    r = [3, 7, 10, 11, 9][i]
    cols = [((255, 255, 210), None), ((255, 230, 100), (255, 150, 40)), ((255, 160, 50), (230, 70, 30)),
            ((230, 80, 40), (120, 30, 20)), (None, (90, 60, 60))][i]
    if cols[1]: d.ellipse([12 - r, 12 - r, 12 + r, 12 + r], fill=cols[1])
    if cols[0]:
        ri = max(1, r - 3 if i else r)
        d.ellipse([12 - ri, 12 - ri, 12 + ri, 12 + ri], fill=cols[0])
    if i == 4:
        d.ellipse([12 - 5, 12 - 5, 12 + 5, 12 + 5], fill=(0, 0, 0, 0))
    for _ in range(6 + i * 2):
        a = rnd.random() * 6.28; rr = r + rnd.randint(0, 2)
        x, y = 12 + math.cos(a) * rr, 12 + math.sin(a) * rr
        d.rectangle([x, y, x + 1, y + 1], fill=(255, 200 - i * 30, 80))
    return im
save(strip([boom(i) for i in range(5)]), 'explosion')

# ---------------------------------------------------------------- hazards
im = new(16, 16); d = ImageDraw.Draw(im)
d.polygon([(2, 0), (14, 0), (15, 6), (11, 15), (5, 15), (1, 7)], fill=(120, 105, 125), outline=(40, 30, 50))
d.rectangle([4, 3, 6, 5], fill=(160, 145, 165)); d.rectangle([9, 7, 11, 9], fill=(85, 72, 90)); save(im, 'rock')
im = new(32, 16); d = ImageDraw.Draw(im)
d.rectangle([0, 0, 31, 15], fill=(110, 120, 140), outline=OUT)
for x in range(-16, 32, 8): d.polygon([(x, 15), (x + 4, 15), (x + 12, 0), (x + 8, 0)], fill=(240, 200, 50))
d.rectangle([0, 0, 31, 15], outline=OUT); save(im, 'crusher_head')
im = new(16, 16); d = ImageDraw.Draw(im)
d.rectangle([4, 0, 11, 15], fill=(80, 90, 110), outline=OUT); d.line([(7, 0), (7, 15)], fill=(150, 160, 180)); save(im, 'crusher_shaft')

# ---------------------------------------------------------------- bosses (64x64, core glows at 22..42)
def core(d, c1, c2):
    d.ellipse([22, 22, 42, 42], fill=c1, outline=c2); d.ellipse([28, 28, 36, 36], fill=(255, 250, 200))

im = new(64, 64); d = ImageDraw.Draw(im)
d.polygon([(6, 32), (18, 10), (52, 6), (62, 22), (62, 42), (52, 58), (18, 54)], fill=(95, 102, 128), outline=(20, 24, 40))
d.polygon([(14, 32), (24, 16), (46, 14), (46, 50), (24, 48)], fill=(62, 68, 96), outline=(20, 24, 40))
d.polygon([(40, 6), (58, 0), (56, 12)], fill=(160, 40, 55)); d.polygon([(40, 58), (58, 63), (56, 52)], fill=(160, 40, 55))
d.rectangle([0, 28, 10, 36], fill=(150, 150, 172), outline=(20, 24, 40))
core(d, (255, 60, 50), (255, 220, 120)); save(im, 'boss0')

im = new(64, 64); d = ImageDraw.Draw(im)
d.polygon([(32, 0), (62, 32), (32, 63), (2, 32)], fill=(120, 70, 190), outline=(30, 10, 60))
d.polygon([(32, 8), (54, 32), (32, 55), (10, 32)], fill=(80, 45, 140), outline=(30, 10, 60))
for pts in ([(0, 20), (8, 26), (4, 32)], [(0, 44), (8, 38), (4, 32)], [(63, 20), (55, 26), (59, 32)], [(63, 44), (55, 38), (59, 32)]):
    d.polygon(pts, fill=(255, 150, 60))
core(d, (60, 230, 255), (220, 255, 255)); save(im, 'boss1')

im = new(64, 64); d = ImageDraw.Draw(im)
d.rectangle([8, 6, 62, 58], fill=(72, 88, 112), outline=(15, 22, 34))
d.rectangle([14, 12, 56, 52], fill=(48, 60, 82), outline=(15, 22, 34))
for y in (8, 28, 50): d.rectangle([0, y, 12, y + 5], fill=(170, 180, 200), outline=(15, 22, 34))
for y in (14, 44): d.rectangle([54, y, 62, y + 4], fill=(220, 190, 60))
core(d, (70, 255, 120), (210, 255, 220)); save(im, 'boss2')

# ---------------------------------------------------------------- tiles 48x48 (row per level: fill, surface, underside)
PAL = [((74, 58, 78), (110, 88, 112), (46, 36, 52), (160, 130, 165)),
       ((70, 30, 28), (130, 52, 36), (40, 16, 18), (255, 140, 40)),
       ((70, 84, 104), (120, 140, 168), (40, 50, 68), (190, 220, 245))]
tiles = new(48, 48)
trnd = random.Random(5)
for L, (base, light, dark, acc) in enumerate(PAL):
    for t in range(3):
        tile = Image.new('RGBA', (16, 16), base + (255,)); px = tile.load()
        for y in range(16):
            for x in range(16):
                n = trnd.random()
                if n < 0.12: px[x, y] = dark + (255,)
                elif n < 0.18: px[x, y] = light + (255,)
        if L == 2:
            for x in range(16): px[x, 7] = dark + (255,)
            for y in range(16): px[7, y] = dark + (255,)
            px[2, 2] = px[12, 12] = light + (255,)
        if t == 1:
            for x in range(16): px[x, 0] = acc + (255,); px[x, 1] = light + (255,); px[x, 2] = light + (255,)
        if t == 2:
            for x in range(16): px[x, 15] = acc + (255,); px[x, 14] = light + (255,); px[x, 13] = light + (255,)
        tiles.paste(tile, (t * 16, L * 16))
save(tiles, 'tiles')

# ---------------------------------------------------------------- backgrounds 480x272 (tile horizontally) + star layer
W, H = 480, 272
def lerp(a, b, t): return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))
def background(level):
    im = Image.new('RGB', (W, H)); d = ImageDraw.Draw(im)
    top, bot = [((10, 8, 24), (34, 22, 48)), ((26, 6, 6), (92, 28, 10)), ((8, 14, 26), (26, 40, 62))][level]
    for y in range(H): d.line([(0, y), (W, y)], fill=lerp(top, bot, y / (H - 1)))
    if level == 0:
        col = (22, 16, 36)
        for x in range(W):
            hb = 46 + 24 * math.sin(2 * math.pi * 3 * x / W) + 12 * math.sin(2 * math.pi * 8 * x / W + 1)
            ht = 40 + 20 * math.sin(2 * math.pi * 4 * x / W + 2) + 10 * math.sin(2 * math.pi * 9 * x / W)
            d.line([(x, H - hb), (x, H)], fill=col); d.line([(x, 0), (x, ht)], fill=col)
    elif level == 1:
        for x in range(W):
            m = (x * 3) % W / W
            h = 100 * (1 - abs(m * 2 - 1))
            d.line([(x, H - h), (x, H)], fill=(40, 12, 12))
            d.line([(x, H - h), (x, H - h + 2)], fill=(200, 80, 30))
    else:
        for x in range(0, W, 48): d.line([(x, 0), (x, H)], fill=(18, 28, 44))
        for y in range(0, H, 34): d.line([(0, y), (W, y)], fill=(18, 28, 44))
        for i in range(18):
            x = rnd.randrange(0, W - 40) // 48 * 48 + 4; y = rnd.randrange(0, H - 30) // 34 * 34 + 4
            d.rectangle([x, y, x + 38, y + 24], fill=lerp(top, bot, 0.15 + rnd.random() * 0.25))
    return im
for L in range(3): background(L).save(os.path.join(SPR, 'bg%d.png' % L))

stars = new(W, H); sp = stars.load()
for _ in range(190):
    x, y = rnd.randrange(W), rnd.randrange(H); a = rnd.choice([120, 180, 255])
    sp[x, y] = (255, 255, 255, a)
    if rnd.random() < 0.15: sp[min(W - 1, x + 1), y] = (255, 255, 255, a)
save(stars, 'stars')
px = new(1, 1); px.putpixel((0, 0), (255, 255, 255, 255)); save(px, 'pixel')

# ---------------------------------------------------------------- sound effects (22.05 kHz mono wav)
SR = 22050
def write(name, samples):
    with wave.open(os.path.join(SFX, name + '.wav'), 'wb') as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(SR)
        w.writeframes(b''.join(struct.pack('<h', int(max(-1, min(1, s)) * 32000)) for s in samples))
def tone(f0, f1, dur, vol=0.4, square=True):
    n = int(SR * dur); out = []; ph = 0.0
    for i in range(n):
        t = i / n; ph += (f0 + (f1 - f0) * t) / SR
        v = (1 if (ph % 1) < 0.5 else -1) if square else math.sin(2 * math.pi * ph)
        out.append(v * vol * (1 - t))
    return out
def noise(dur, vol=0.5, decay=2.5, smooth=0.6):
    n = int(SR * dur); out = []; last = 0.0
    for i in range(n):
        last = last * smooth + (rnd.random() * 2 - 1) * (1 - smooth)
        out.append(last * vol * 2 * (1 - i / n) ** decay)
    return out
write('shoot', tone(900, 300, 0.09, 0.25))
write('hit', noise(0.06, 0.5, 1, 0.3))
write('boom', noise(0.5, 0.9, 2.2, 0.85))
write('pickup', tone(700, 700, 0.06, 0.3) + tone(1100, 1400, 0.1, 0.3))
write('power', tone(500, 500, 0.06, 0.3) + tone(700, 700, 0.06, 0.3) + tone(1000, 1000, 0.1, 0.3))
print('assets written to', os.path.abspath(ROOT))
