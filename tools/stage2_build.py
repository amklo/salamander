"""Stage 2 (OPERATION TWO, the vertical stage): turn the original MSX map into game assets.

Run from the project folder:   python3 tools/stage2_build.py
Needs numpy, scipy and pillow. Reads 'reference material/salamander_stage_2.png' (256 x 4127, scrolls upward) and writes:
  assets/maps/stage2_t0..t3.png        orange rock art (image layers, 1032 px tall each)
  assets/maps/stage2_bricks.png        the blue bubble rocks (destructible, drawn per 8x8 cell, no regrow)
  assets/sprites/wall turret red.png   wall turrets: frame 0 = sideways, frame 1 = diagonal (pointing down)
  assets/sprites/wall turret green.png    (both face right, mounted on a wall to their left)
  assets/sprites/boss1.png             Tetran's body (64 x 64)
  assets/sprites/tetran ring.png       one ring of Tetran's tentacles (8 x 8)
  assets/maps/icons/*.png              Tiled icons for the new objects
  assets/maps/stage2.tmx               the map: art layers, collision + bricks layers, objects, camera path
Positions of turrets, domes and eye rocks are found by matching the cut sprites against the map; the flying
waves (fans, Celtic Frost) are listed in WAVES.
"""
import os
import numpy as np
from PIL import Image
from scipy import ndimage

SRC = 'reference material/salamander_stage_2.png'
SPR = 'assets/sprites/'
MAPS = 'assets/maps/'
CELL = 8
src = np.array(Image.open(SRC).convert('RGB')).astype(np.int32)
H0, W = src.shape[:2]
H = (H0 + CELL - 1) // CELL * CELL                      # 4128: one row of space added at the bottom
img = np.zeros((H, W, 3), np.int32) + 6
img[:H0] = src
code = (img[:, :, 0] << 16) | (img[:, :, 1] << 8) | img[:, :, 2]


def c(r, g, b): return (r << 16) | (g << 8) | b


BG = c(6, 6, 6)
ROCK = [c(208, 80, 72), c(247, 120, 120), c(208, 192, 80), c(224, 200, 128), c(247, 80, 80), c(0, 0, 0)]
BLUE = [c(120, 112, 247), c(80, 80, 232), c(64, 232, 240)]
LIGHT = [c(247, 247, 247), c(200, 200, 200)]
GREEN = [c(32, 176, 56), c(32, 200, 64), c(88, 216, 120)]

# ---------------------------------------------------------------- sprites cut from the map (x0, y0, w, h, mirror)
CUTS = {
    'red_side': (176, 2071, 16, 16, False),
    'red_diag': (16, 240, 16, 16, True),       # this one faces left: mirrored
    'green_side': (80, 3127, 16, 16, True),
    'green_diag': (112, 656, 16, 16, False),
    'dome': (16, 400, 12, 32, False),
    'eye': (168, 808, 16, 16, False),
}


def cut(name):
    x0, y0, w, h, mirror = CUTS[name]
    a = code[y0:y0 + h, x0:x0 + w].copy()
    return a[:, ::-1] if mirror else a


def rgba(a, keep):
    """code array -> RGBA image; pixels where keep is False are see-through."""
    out = np.zeros(a.shape + (4,), np.uint8)
    out[:, :, 0] = (a >> 16) & 255
    out[:, :, 1] = (a >> 8) & 255
    out[:, :, 2] = a & 255
    out[:, :, 3] = np.where(keep, 255, 0)
    return Image.fromarray(out, 'RGBA')


# ---------------------------------------------------------------- matching
def find(tpl, min_share=0.85, gap=8):
    """Top-left corners where the template's non-background pixels match the map (exact colours)."""
    th, tw = tpl.shape
    pts = [(y, x, tpl[y, x]) for y in range(th) for x in range(tw) if tpl[y, x] != BG]
    acc = np.zeros((H - th + 1, W - tw + 1), np.int32)
    for y, x, col in pts:
        acc += code[y:y + H - th + 1, x:x + W - tw + 1] == col
    need = int(len(pts) * min_share)
    found = []
    for y, x in sorted(zip(*np.nonzero(acc >= need)), key=lambda p: -acc[p]):
        if all(abs(y - fy) >= gap or abs(x - fx) >= gap for fy, fx, _ in found):
            found.append((int(y), int(x), int(acc[y, x])))
    return found


cleared = np.zeros((H, W), bool)                    # pixels taken out of the terrain art
objects = []                                        # (kind, x, y_top, w, h, flipH, flipV, props)

for colour in ('red', 'green'):
    for kind in ('side', 'diag'):
        base = cut(colour + '_' + kind)
        for fh in (False, True):
            for fv in ((False, True) if kind == 'diag' else (False,)):
                t = base[:, ::-1] if fh else base
                t = t[::-1, :] if fv else t
                for y, x, _ in find(t):
                    if y < 130:
                        continue
                    if any(o[0] == 'wallturret' and abs(o[1] - x) < 8 and abs(o[2] - y) < 8 for o in objects):
                        continue
                    objects.append(('wallturret', x, y, 16, 16, fh, fv, {'drop': colour == 'red', 'diag': kind == 'diag'}))
                    cleared[y:y + 16, x:x + 16] |= t != BG

DOME_W = 18
dome = cut('dome')
for fh in (False, True):
    t = dome[:, ::-1] if fh else dome
    for y, x, _ in find(t, 0.8):
        # sprites/dome.png (drawn by hand: closed + open, 18 x 32 each) is wider than the dome in the map: on the
        # right wall it reaches 6 px further out to the left
        objects.append(('dome', x - (DOME_W - 12) if fh else x, y, DOME_W, 32, fh, False, {}))
        cleared[y:y + 32, x:x + 12] |= t != BG

# eye rocks: match the green ring only
eye = cut('eye')
ring = np.where(np.isin(eye, GREEN), eye, BG)
yy, xx = np.mgrid[0:16, 0:16]
inner = (xx - 7.5) ** 2 + (yy - 7.5) ** 2 <= 7.5 ** 2   # the eye: ring + its dark middle
eyes = [(y, x) for y, x, _ in find(ring, 0.9)]
for y, x in eyes:                                   # sprites/rock_eye.png (hand-drawn, 32x32): the whole rock
    objects.append(('eye', x - 8, y - 8, 32, 32, False, False, {}))

# ---------------------------------------------------------------- terrain / bricks
is_rock = np.isin(code, ROCK) & ~cleared
is_blue = np.isin(code, BLUE) & ~cleared
is_light = np.isin(code, LIGHT) & ~cleared
near_blue = ndimage.binary_dilation(is_blue, np.ones((5, 5)))
near_rock = ndimage.binary_dilation(is_rock, np.ones((5, 5)))
blue = is_blue | (is_light & near_blue)
rock = is_rock | (is_light & near_rock & ~near_blue)
rock |= np.isin(code, GREEN) & ~cleared & near_rock             # (the eyes' rings stay in the art until healed below)
boss_rows = 130                                     # Tetran (drawn by the game) lives up here
blue[:boss_rows] = False
rock[:boss_rows] = False
# the player's ship drawn at the start of the map
lab, n = ndimage.label(blue | rock, np.ones((3, 3)))
for i, sl in enumerate(ndimage.find_objects(lab)):
    if sl[0].start > H - 200 and (sl[0].stop - sl[0].start) < 24:
        blue[sl] &= lab[sl] != i + 1
        rock[sl] &= lab[sl] != i + 1

# eye rocks are sprites of their own (sprites/rock_eye.png): take the whole rock out of the art and the collision
lab, n = ndimage.label(rock, np.ones((3, 3)))
for y, x in eyes:
    box = np.zeros(rock.shape, bool)
    box[y - 8:y + 24, x - 8:x + 24] = True
    for L in set(np.unique(lab[y:y + 16, x:x + 16])) - {0}:
        rock &= ~((lab == L) & box)

rows, cols = H // CELL, W // CELL
rk = rock.reshape(rows, CELL, cols, CELL).sum(axis=(1, 3))
bl = blue.reshape(rows, CELL, cols, CELL).sum(axis=(1, 3))
solid = rk >= 12
brick = ~solid & (bl >= 12)
brick_px = blue & np.kron(brick, np.ones((CELL, CELL), bool))
art_px = (rock | blue) & ~brick_px

os.makedirs(SPR, exist_ok=True)
CH = H // 4                                         # 1032
for i in range(4):
    rgba(code[i * CH:(i + 1) * CH], art_px[i * CH:(i + 1) * CH]).save(MAPS + 'stage2_t%d.png' % i)
rgba(code, brick_px).save(MAPS + 'stage2_bricks.png')

# ---------------------------------------------------------------- sprites
def sheet(frames, keep):
    w = sum(f.shape[1] for f in frames)
    a = np.zeros((frames[0].shape[0], w), np.int32) + BG
    k = np.zeros(a.shape, bool)
    x = 0
    for f, kk in zip(frames, keep):
        a[:, x:x + f.shape[1]] = f
        k[:, x:x + f.shape[1]] = kk
        x += f.shape[1]
    return rgba(a, k)


for colour in ('red', 'green'):
    s, d = cut(colour + '_side'), cut(colour + '_diag')
    sheet([s, d], [s != BG, d != BG]).save(SPR + 'wall turret %s.png' % colour)


body = code[24:88, 144:208]
rgba(body, body != BG).save(SPR + 'boss1.png')
ring_spr = code[0:8, 192:200]
rgba(ring_spr, ring_spr != BG).save(SPR + 'tetran ring.png')

icons = MAPS + 'icons/'
os.makedirs(icons, exist_ok=True)
for colour in ('red', 'green'):
    s = cut(colour + '_diag')
    rgba(s, s != BG).save(icons + 'wall_turret_%s.png' % colour)
Image.open(SPR + 'dome.png').crop((0, 0, DOME_W, 32)).save(icons + 'dome.png')   # its closed frame
re_ = np.array(Image.open(SPR + 'rock_eye.png').convert('RGBA'))   # icon: the eye rock, dark background see-through
bgm = (re_[:, :, :3] == re_[0, 0, :3]).all(-1)
bl, _ = ndimage.label(bgm)
edge = (set(bl[0]) | set(bl[-1]) | set(bl[:, 0]) | set(bl[:, -1])) - {0}
re_[np.isin(bl, list(edge))] = 0
Image.fromarray(re_, 'RGBA').save(icons + 'rock_eye.png')

# ---------------------------------------------------------------- map
OBJ_FIRST = 39                                      # objects.tsx firstgid (as in stage1.tmx)
TILE_IDS = {'wallturret_green': 28, 'wallturret_red': 29, 'dome': 30, 'eye': 31}   # see objects.tsx
FH, FV = 0x80000000, 0x40000000
lines = []
oid = 1
for kind, x, y, w, h, fh, fv, props in sorted(objects, key=lambda o: -o[2]):
    key = kind + ('_red' if props.get('drop') else '_green') if kind == 'wallturret' else kind
    gid = OBJ_FIRST + TILE_IDS[key] + (FH if fh else 0) + (FV if fv else 0)
    extra = '\n   <properties>\n    <property name="diag" type="bool" value="true"/>\n   </properties>\n  </object>' if props.get('diag') else None
    lines.append('  <object id="%d" gid="%d" x="%d" y="%d" width="%d" height="%d"%s' % (oid, gid, x, y + h, w, h, '>' + extra if extra else '/>'))
    oid += 1
# flying waves in the open parts of the stage (fans, Celtic Frost, Legacy): (kind, x of the column, world y where the top of the screen triggers it,
# mirrored). Fans weave down around their column; a Celtic Frost wave comes down its column (10-42% of the screen
# width from the left edge, or from the right edge when mirrored) and flies its "Z" turned a quarter.
WAVES = [   # (as laid out in Tiled; the fans became Legacies)
    ('legacy', 128, 202, False),
    ('frost', 54, 202, False),
    ('frost', 196, 202, True),
    ('legacy', 128, 280, False),
    ('frost', 196, 280, True),
    ('frost', 54, 282, False),
    ('frost', 52, 380, False),
    ('legacy', 128, 382, False),
    ('frost', 196, 382, True),
    ('legacy', 128, 470, False),
    ('frost', 54, 470, False),
    ('frost', 186, 470, False),
    ('legacy', 128, 560, False),
    ('legacy', 192, 2100, False),
    ('legacy', 64, 2400, False),
    ('frost', 52, 2550, False),
    ('frost', 194, 2552, True),
    ('legacy', 128, 2680, False),
    ('frost', 198, 2680, True),
    ('frost', 54, 2680, False),
]
WAVE_TILES = {'fan': (0, 16, 16), 'frost': (25, 13, 15), 'legacy': (32, 16, 16)}   # objects.tsx tile id, icon size
for kind, wx, wy, mirrored in WAVES:
    tid, tw, th = WAVE_TILES[kind]
    gid = OBJ_FIRST + tid + (FV if mirrored else 0)
    lines.append('  <object id="%d" gid="%d" x="%d" y="%d" width="%d" height="%d"/>' % (oid, gid, wx - tw // 2, H - wy, tw, th))
    oid += 1
cx, top_y, bottom_y = W // 2, 84, H - 84            # camera path: screen centre, from the bottom up to the boss
lines.append('  <object id="%d" name="path" x="%d" y="%d">\n   <polyline points="0,0 0,%d"/>\n  </object>'
             % (oid, cx, bottom_y, top_y - bottom_y))
oid += 1
for cy in (3080, 2160, 1250):                       # checkpoints (map y of the screen centre)
    lines.append('  <object id="%d" type="checkpoint" x="%d" y="%d" width="16" height="16"/>' % (oid, cx - 8, cy - 8))
    oid += 1
# asteroid storm (objects.tsx tile 54, "meteors"): starts when the top of the screen reaches the bottom of the icon,
# where the scenery starts; lasts its "time" property (15 s by default)
lines.append('  <object id="%d" name="asteroid storm" gid="%d" x="113" y="3600" width="30" height="27"/>' % (oid, OBJ_FIRST + 54))
oid += 1


def csv(cells, gid):
    return '\n' + ',\n'.join(','.join(str(gid) if v else '0' for v in row) for row in cells) + '\n'


art = ''.join('  <imagelayer id="%d" name="art %d" offsetx="0" offsety="%d">\n   <image source="stage2_t%d.png" width="%d" height="%d"/>\n  </imagelayer>\n'
              % (i + 1, i, i * CH, i, W, CH) for i in range(4))
tmx = '''<?xml version="1.0" encoding="UTF-8"?>
<map version="1.10" tiledversion="1.10.2" orientation="orthogonal" renderorder="right-down" width="%d" height="%d" tilewidth="8" tileheight="8" infinite="0" nextlayerid="8" nextobjectid="%d">
 <properties>
  <property name="name" value="OPERATION TWO"/>
  <property name="vertical" type="bool" value="true"/>
  <property name="speed" type="float" value="26"/>
  <property name="lane" type="int" value="168"/>
  <property name="music" value="stg2.mid"/>
  <property name="bossMusic" value="boss.mid"/>
  <property name="brickArt" value="maps/stage2_bricks.png"/>
  <property name="brickRegrow" type="float" value="0"/>
 </properties>
 <tileset firstgid="1" source="collision.tsx"/>
 <tileset firstgid="2" source="bricks.tsx"/>
 <tileset firstgid="3" source="terrain.tsx"/>
 <tileset firstgid="%d" source="objects.tsx"/>
%s <layer id="5" name="collision" width="%d" height="%d" opacity="0.6">
  <data encoding="csv">%s</data>
 </layer>
 <layer id="6" name="bricks" width="%d" height="%d" opacity="0.6">
  <data encoding="csv">%s</data>
 </layer>
 <objectgroup id="7" name="objects">
%s
 </objectgroup>
</map>
''' % (cols, rows, oid, OBJ_FIRST, art, cols, rows, csv(solid, 1), cols, rows, csv(brick, 2), '\n'.join(lines))
open(MAPS + 'stage2.tmx', 'w').write(tmx)

kinds = {}
for o in objects:
    k = o[0] + (' red' if o[7].get('drop') else '') + (' diag' if o[7].get('diag') else '')
    kinds[k] = kinds.get(k, 0) + 1
print('objects:', kinds, '| solid cells', int(solid.sum()), '| brick cells', int(brick.sum()))
