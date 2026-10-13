"""Stages 3-7: turn the cleaned-up original MSX maps into game assets.

Run from the project folder:   python3 tools/stages_build.py [3 4 5 6 7]   (no numbers = all)
Needs numpy, scipy and pillow. Reads 'reference material/salamander_stage_*.png' and writes, per stage N:
  assets/sprites/stageN_t*.png, stageN_bricks.png, the enemy and boss sprites, and assets/maps/stageN.tmx
(overwrites the map: waves, path and checkpoints are listed below). See tools/stagebuild.py.

  stage 3  Lavinia  (salamander_stage_3_lavinia.png)  side-scrolling base, drops to a lower corridor
  stage 4  Eioneus  (salamander_stage_3_eioneus.png)  vertical, red rock
  stage 5  Kierke   (salamander_stage_3_kierke.png)   side-scrolling, blue organic cave
  stage 6  Op 4     (salamander_stage_4.png)          vertical, rock then blue pipes
  stage 7  Op X     (salamander_stage_x.png)          side-scrolling fortress
"""
import sys
import numpy as np
from scipy import ndimage
from stagebuild import Stage, c, BG

REF = 'reference material/'
SPR = 'assets/sprites/'
MAPS = 'assets/maps/'
BOSS_MUSIC = '08. Poison of Snake.mp3'

# objects.tsx tile ids of the object kinds (existing ones, and the new ones added for stages 3-7)
TILES = {
    'fan': (0, 16, 16), 'rusher': (1, 18, 12), 'frost': (25, 13, 15), 'rugal': (27, 16, 16), 'legacy': (32, 16, 16),
    'rugal_red': (33, 16, 16), 'link': (26, 16, 16),
    'dome': 30, 'wallturret_green': 28, 'wallturret_red': 29, 'eye': 31,
    # new for stages 3-7 (the game does not use them yet: their behaviour comes stage by stage)
    'boss': 34, 'hatch': 35, 'pod': 36, 'jumper': 37, 'dart': 38, 'dart_side': 39, 'spark': 40, 'laser': 41,
    'spider': 42, 'nest': 43, 'chain': 44, 'seed': 45, 'bluedome': 46, 'cluster': 47, 'mine': 48, 'crab': 49,
    'gunpod': 50, 'orb': 51, 'vent': 52, 'coregate': 53,
}

ALL_FLIPS = ((False, False), (True, False), (False, True), (True, True))
VFLIP = ((False, False), (False, True))
HFLIP = ((False, False), (True, False))


def finish(s, n, props, path, checkpoints, waves, speeds=None, vertical=False, solid_min=12, decor=None, extra=()):
    s.grids(solid_min=solid_min, decor=decor)
    s.save(n, SPR, vertical)
    s.tmx(MAPS + 'stage%d.tmx' % n, props, TILES, path, checkpoints, waves, speeds, extra)
    kinds = {}
    for o in s.objects: kinds[o['kind']] = kinds.get(o['kind'], 0) + 1
    print('stage %d: %dx%d' % (n, s.W, s.H), kinds, '| solid', int(s.solid.sum()), '| bricks', int(s.brick.sum()))
    return s


# =====================================================================================================================
def stage3():
    """Lavinia: space, then a green base. At x 3380 the way drops down into a lower corridor; the boss waits in space."""
    s = Stage(REF + 'salamander_stage_3_lavinia.png')
    s.remove_box((76, 58, 24, 18))                                  # the player's ship drawn at the start
    s.remove_box((4474, 226, 20, 14))                              # a robot's shot
    # laser beams (the emitters stay part of the walls): {x0, x1, y}
    beams = [(3384, 3434, 146, 'red'), (3384, 3434, 202, 'red'), (3384, 3434, 242, 'blue'),
             (3520, 3575, 146, 'red'), (3520, 3575, 202, 'red'), (3520, 3575, 242, 'blue'),
             (4564, 4589, 242, 'blue'), (4564, 4589, 290, 'red')]
    beam_cols = [c(247, 32, 32), c(176, 32, 32), c(247, 104, 104), c(72, 104, 247), c(72, 216, 247), c(32, 32, 247)]
    for x0, x1, y, col in beams:
        s.remove_box((x0, y - 1, x1 - x0 + 1, 7), beam_cols)
        s.objects.append(dict(kind='laser', x=x0, y=y, w=x1 - x0 + 1, h=5, fh=False, fv=False, props={'blue': col == 'blue'}))
    bx, by, bw, bh = s.take_boss('boss2', 5060, 280)
    s.objects.append(dict(kind='boss', x=bx, y=by, w=bw, h=bh, fh=False, fv=False, props={}))
    # enemies
    s.templates('hatch', (1248, 128, 32, 16), VFLIP, sprite='hatch')            # orange-topped launcher
    s.templates('pod', (1440, 128, 32, 16), VFLIP, sprite='pod')                # closed blue dome
    s.templates('pod', (1728, 128, 32, 16), VFLIP, sprite='pod_open', props={'open': True})   # open blue dome
    s.templates('jumper', (1463, 71, 26, 34), HFLIP, share=0.8, sprite='jumper', keep_mask=None)
    s.templates('dart', (4724, 216, 22, 19), VFLIP, share=0.8, sprite='dart')
    s.templates('dart_side', (4832, 298, 22, 18), HFLIP, share=0.8, sprite='dart_side')
    s.templates('spark', (2304, 32, 16, 16), ALL_FLIPS, share=0.8, sprite='spark')
    # destructible: the blue spools, the blue X blocks and the red gates in the lower corridor
    s.templates(None, (2088, 32, 32, 16), HFLIP, brick=True)
    s.templates(None, (3272, 64, 32, 32), HFLIP, brick=True)
    s.templates(None, (3784, 296, 32, 24), VFLIP, share=0.8, brick=True)
    s.remove_stars()
    s.mark_decor(20)
    # camera path (centre of the screen): along the upper lane, down the shaft at x 3480, along the lower corridor
    path = [(128, 84), (3480, 84), (3480, 276), (s.W - 128, 276)]
    cps = [(1300, 84), (2300, 84), (3480, 276), (4300, 276)]
    waves = [('rugal', 300, 60, False, {}), ('rugal', 324, 70, False, {}), ('rugal', 348, 60, False, {}),
             ('fan', 520, 110, False, {}), ('fan', 700, 40, False, {}),
             ('rugal_red', 860, 90, False, {'drop': True}), ('rugal', 884, 80, False, {}), ('rugal', 884, 100, False, {}),
             ('frost', 1050, 60, False, {}), ('frost', 1600, 70, True, {}),
             ('rusher', 2600, 80, False, {}), ('frost', 3000, 64, False, {}),
             ('fan', 3700, 240, False, {}), ('frost', 4100, 250, True, {}), ('rusher', 4400, 260, False, {})]
    props = {'name': 'OPERATION THREE', 'speed': 28.4, 'lane': 168, 'music': '04. Combat.mp3', 'bossMusic': BOSS_MUSIC,
             'brickArt': 'sprites/stage3_bricks.png', 'brickRegrow': 0.0}
    return finish(s, 3, props, path, cps, waves, speeds=[1, 0.7, 1])


def vertical_path(s, top_y, x=None):
    """Camera path of a vertical stage: the centre of the screen from the bottom up to top_y (centre of the last screen)."""
    x = s.W // 2 if x is None else x
    return [(x, s.H - 84), (x, top_y)]


# =====================================================================================================================
def stage4():
    """Eioneus: vertical, red rock. Seeds (red eye pods), wall domes, beam emitters, blue bubble bricks.
    The boss is drawn above the map in the original (rows 0-113); its three gun pods hang from the rock ceiling."""
    s = Stage(REF + 'salamander_stage_3_eioneus.png')
    ROCK = [c(247, 104, 104), c(176, 32, 32), c(247, 32, 32)]
    BLUE = [c(72, 216, 247), c(32, 32, 247), c(72, 104, 247), c(247, 247, 247), c(176, 176, 176)]
    s.remove_box((76, 4152, 24, 24))                               # the player's ship at the start
    s.take_boss('boss3', 160, 40, dil=3)
    s.templates('gunpod', (39, 167, 42, 74), sprite='gunpod')
    s.templates('orb', (193, 248, 18, 18), sprite='orb')
    # beams: horizontal ones from the emitters on the walls, vertical chains between two emitters
    for ex, ey, ew, eh, right in [(30, 454, 28, 20, True), (14, 974, 28, 20, True), (118, 1254, 28, 20, False),
                                  (30, 2430, 28, 20, True)]:          # right: the beam goes to the right of the emitter
        x0, x1 = (ex + ew, s.W) if right else (0, ex)
        bx, by, bw, bh = s.thin_beam(x0, x1, ey, ey + eh, ROCK) or (x0, ey + eh // 2 - 2, x1 - x0, 4)   # (beam off)
        s.objects.append(dict(kind='laser', x=bx, y=by, w=bw, h=bh, fh=not right, fv=False, props={}))
    for (tx, ty, tw, th), (bx, by, bw, bh) in [((158, 2270, 36, 20), (142, 2358, 36, 20)), ((94, 2974, 36, 20), (70, 3078, 36, 20))]:
        x0, x1 = min(tx, bx), max(tx + tw, bx + bw)
        s.remove_box((x0, ty + th, x1 - x0, by - ty - th), BLUE)
        s.objects.append(dict(kind='laser', x=x0, y=ty + th, w=x1 - x0, h=by - ty - th, fh=False, fv=False, props={'vertical': True}))
    s.templates('seed', (88, 1046, 40, 42), sprite='seed_open', props={'open': True}, match=[c(216, 216, 32), c(216, 216, 144)], share=0.9)
    s.templates('seed', (46, 380, 44, 48), sprite='seed')
    s.templates('mine', (208, 1406, 16, 18), sprite='mine', share=0.8)
    s.templates('bluedome', (31, 3237, 18, 38), HFLIP, sprite='bluedome_open', share=0.8, props={'open': True})
    s.templates('bluedome', (15, 1413, 14, 38), HFLIP, sprite='bluedome', share=0.8)
    s.remove_stars()
    s.brickpx |= np.isin(s.code, BLUE) & ~s.cleared
    s.mark_decor(12)
    path = vertical_path(s, 244)
    cps = [(128, 3300), (128, 2500), (128, 1600), (128, 800)]
    waves = [('legacy', 128, 3900, False, {}), ('frost', 60, 3800, False, {}), ('frost', 196, 3700, True, {}),
             ('legacy', 128, 3600, False, {}), ('frost', 60, 3500, False, {}), ('legacy', 100, 3380, False, {}),
             ('legacy', 128, 2000, False, {}), ('frost', 196, 1500, True, {}), ('legacy', 128, 700, False, {})]
    props = {'name': 'OPERATION FOUR', 'vertical': True, 'speed': 26.0, 'lane': 168, 'music': '05. Starfield.mp3',
             'bossMusic': BOSS_MUSIC, 'brickArt': 'sprites/stage4_bricks.png', 'brickRegrow': 0.0}
    return finish(s, 4, props, path, cps, waves, vertical=True)



# =====================================================================================================================
def stage5():
    """Kierke: a blue organic cave full of web threads. Spiders on the walls and the webs, nests (pods), chains of green
    balls. The boss is the machine wall at the end."""
    s = Stage(REF + 'salamander_stage_3_kierke.png')
    GREEN = [c(32, 216, 32), c(32, 144, 32), c(104, 247, 104)]
    SPIDER = GREEN + [c(216, 216, 32), c(216, 216, 144)]
    s.remove_box((76, 56, 26, 20))                                  # the player's ship
    bx, by, bw, bh = s.take_boss('boss4', 3740, 84, dil=3)
    s.objects.append(dict(kind='boss', x=bx, y=by, w=bw, h=bh, fh=False, fv=False, props={'fixed': True}))
    t = s.cut((1554, 6, 38, 22))
    s.templates('nest', (1554, 6, 38, 22), VFLIP, share=0.8, match=GREEN + [c(247, 247, 247)],
                keep_mask=np.isin(t, GREEN + [c(247, 247, 247)]), sprite='nest')
    t = s.cut((1378, 37, 22, 22))
    s.templates('spider', (1378, 37, 22, 22), ALL_FLIPS, share=0.75, match=SPIDER, keep_mask=np.isin(t, SPIDER), sprite='spider')
    # chains of green balls: whatever green is left in the open
    ball = np.isin(s.code, GREEN + [c(216, 216, 144)]) & ~s.cleared
    lab, n = ndimage.label(ndimage.binary_dilation(ball, np.ones((3, 3))), np.ones((3, 3)))
    for i, sl in enumerate(ndimage.find_objects(lab)):
        m = (lab[sl] == i + 1) & ball[sl]
        if m.sum() < 80 or max(sl[0].stop - sl[0].start, sl[1].stop - sl[1].start) < 30: continue   # (green pustules in the walls stay)
        y, x = sl[0].start, sl[1].start
        h, w = sl[0].stop - y, sl[1].stop - x
        s.cleared[sl] |= m
        diag = h > 24 and w > 24
        s.objects.append(dict(kind='chain', x=x, y=y, w=w, h=h, fh=False, fv=False,
                              props={'count': int(round(max(w, h) / (8.0 if diag else 12.0))), 'diag': diag}))
        if 'ball' not in s.sprites:                                 # one ball of the first chain
            s.add_sprite('ball', s.code[y:y + 12, x:x + 12].copy(), np.isin(s.code[y:y + 12, x:x + 12], GREEN + [c(216, 216, 144)]))
    s.remove_stars()
    path = [(128, 84), (s.W - 128, 84)]
    cps = [(1300, 84), (2200, 84), (3000, 84)]
    waves = [('rugal', 300, 60, False, {}), ('rugal', 324, 84, False, {}), ('rugal', 348, 108, False, {}),
             ('fan', 560, 50, False, {}), ('fan', 760, 110, False, {}), ('rugal_red', 900, 84, False, {'drop': True}),
             ('frost', 1300, 60, False, {}), ('frost', 1900, 70, True, {}), ('rusher', 2500, 84, False, {}),
             ('frost', 3000, 64, False, {})]
    props = {'name': 'OPERATION FIVE', 'speed': 28.4, 'lane': 168, 'music': '06. Burn the Wind.mp3', 'bossMusic': BOSS_MUSIC}
    return finish(s, 5, props, path, cps, waves, solid_min=24)



# =====================================================================================================================
def stage6():
    """Op 4 (Crush Salamander): vertical. Rock with blue clusters, then a shaft of blue pipes (x 256-512) with mines,
    gun ports, beam barriers and red crabs. A side room opens to the left (x 0-256). The boss: four gun units with
    coloured eyes and the sphere below them."""
    s = Stage(REF + 'salamander_stage_4.png')
    RED = [c(247, 32, 32), c(176, 32, 32), c(247, 104, 104)]
    BLUE = [c(32, 32, 247), c(72, 104, 247), c(72, 216, 247)]
    s.remove_box((328, 4596, 34, 34))                               # the player's ship
    bx, by, bw, bh = s.take_boss('boss5', 396, 90, dil=3, extra=[(308, 226, 140, 140)])
    s.objects.append(dict(kind='boss', x=bx, y=by, w=bw, h=bh, fh=False, fv=False, props={'fixed': True}))
    for y in (1606, 1670, 1734):                                    # beam barriers left and right of the pillar
        for x0, x1 in ((290, 346), (404, 474)):
            b = s.thin_beam(x0, x1, y, y + 8, RED + BLUE)
            bx, by, bw, bh = b or (x0, y + 2, x1 - x0, 3)
            s.objects.append(dict(kind='laser', x=bx, y=by, w=bw, h=bh, fh=False, fv=False, props={}))
    s.templates('crab', (311, 799, 50, 36), HFLIP, share=0.75, sprite='crab', clear_box=True)
    s.templates('cluster', (312, 3960, 32, 31), ALL_FLIPS, share=0.8, sprite='cluster', clear_box=True)
    s.templates('mine', (336, 2593, 24, 24), share=0.85, sprite='mine6')
    s.templates('mine', (417, 3073, 22, 22), share=0.85, sprite='mine6_open', props={'open': True})
    s.templates('bluedome', (272, 4096, 16, 32), HFLIP, share=0.85, sprite='bluedome6')
    s.remove_stars()
    path = vertical_path(s, 84, x=384)
    cps = [(384, 3600), (384, 2800), (384, 2000), (384, 1100)]
    waves = [('legacy', 384, 4300, False, {}), ('frost', 310, 4200, False, {}), ('frost', 456, 4100, True, {}),
             ('legacy', 384, 3950, False, {}), ('frost', 310, 3700, False, {}), ('legacy', 384, 3000, False, {}),
             ('frost', 456, 2400, True, {}), ('legacy', 384, 1300, False, {})]
    props = {'name': 'OPERATION SIX', 'vertical': True, 'speed': 26.0, 'lane': 168, 'music': '07. Destroy Them All.mp3',
             'bossMusic': BOSS_MUSIC}
    return finish(s, 6, props, path, cps, waves, vertical=True, solid_min=20)



# =====================================================================================================================
def stage7():
    """Op X: the fortress. Green walls and pillars, pink brick walls that have to be shot away, flame vents on the
    floor and ceiling (red and blue), a core gate between two pillars and the boss on the far wall."""
    s = Stage(REF + 'salamander_stage_x.png')
    s.remove_box((76, 56, 26, 20))                                  # the player's ship
    bx, by, bw, bh = s.take_box('boss6', (3752, 30, 60, 115))
    s.objects.append(dict(kind='boss', x=bx, y=by, w=bw, h=bh, fh=False, fv=False, props={'fixed': True}))
    gx, gy, gw, gh = s.take_box('coregate', (3490, 62, 66, 51))
    s.objects.append(dict(kind='coregate', x=gx, y=gy, w=gw, h=gh, fh=False, fv=False, props={}))
    s.templates('vent', (1090, 140, 16, 10), VFLIP, share=0.85, sprite='vent_red', props={'red': True})
    s.templates('vent', (996, 23, 16, 10), VFLIP, share=0.85, sprite='vent_blue')
    s.brickpx |= np.isin(s.code, [c(247, 120, 120), c(208, 80, 72)]) & ~s.cleared
    s.remove_stars()
    path = [(128, 84), (s.W - 128, 84)]
    cps = [(1200, 84), (2000, 84), (2900, 84)]
    waves = [('rugal', 200, 50, False, {}), ('rugal', 224, 84, False, {}), ('rugal', 248, 118, False, {}),
             ('fan', 900, 84, False, {}), ('rusher', 1300, 84, False, {}), ('rugal_red', 1600, 84, False, {'drop': True}),
             ('frost', 2000, 60, False, {}), ('fan', 2500, 84, False, {}), ('frost', 3100, 70, True, {})]
    props = {'name': 'OPERATION X', 'speed': 28.4, 'lane': 168, 'music': '09. Prophet Fire.mp3', 'bossMusic': BOSS_MUSIC,
             'brickArt': 'sprites/stage7_bricks.png', 'brickRegrow': 0.0}
    return finish(s, 7, props, path, cps, waves)


STAGES = {3: stage3, 4: stage4, 5: stage5, 6: stage6, 7: stage7}

# objects.tsx tiles for the new kinds: (kind, picture relative to assets/maps)
NEW_TILES = [('boss', 'icons/boss.png'), ('hatch', '../sprites/hatch.png'), ('pod', '../sprites/pod.png'),
             ('jumper', '../sprites/jumper.png'), ('dart', '../sprites/dart.png'), ('dart_side', '../sprites/dart_side.png'),
             ('spark', '../sprites/spark.png'), ('laser', 'icons/laser.png'), ('spider', '../sprites/spider.png'),
             ('nest', '../sprites/nest.png'), ('chain', '../sprites/ball.png'), ('seed', '../sprites/seed.png'),
             ('bluedome', '../sprites/bluedome.png'), ('cluster', '../sprites/cluster.png'), ('mine', '../sprites/mine.png'),
             ('crab', '../sprites/crab.png'), ('gunpod', '../sprites/gunpod.png'), ('orb', '../sprites/orb.png'),
             ('vent', '../sprites/vent_red.png'), ('coregate', '../sprites/coregate.png')]


def update_tileset():
    """Adds (or refreshes) the tiles of the new kinds in objects.tsx, and draws the boss / laser marker icons."""
    from PIL import Image, ImageDraw
    import re
    icons = MAPS + 'icons/'
    im = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.rectangle([0, 0, 15, 15], outline=(255, 64, 64, 255))
    d.text((5, 2), 'B', fill=(255, 64, 64, 255))
    im.save(icons + 'boss.png')
    im = Image.new('RGBA', (16, 4), (0, 0, 0, 0))
    ImageDraw.Draw(im).rectangle([0, 1, 15, 2], fill=(247, 32, 32, 255))
    im.save(icons + 'laser.png')
    t = open(MAPS + 'objects.tsx').read()
    t = re.sub(r' <tile id="(3[4-9]|[4-9]\d)" .*?</tile>\n', '', t, flags=re.S)       # (the old copies of the new tiles)
    add = ''
    for kind, pic in NEW_TILES:
        w, h = Image.open(MAPS + pic).size
        add += ' <tile id="%d" class="%s">\n  <image source="%s" width="%d" height="%d"/>\n </tile>\n' % (TILES[kind], kind, pic, w, h)
    t = t.replace('</tileset>', add + '</tileset>')
    t = re.sub(r'tilecount="\d+"', 'tilecount="%d"' % (34 + len(NEW_TILES)), t)
    open(MAPS + 'objects.tsx', 'w').write(t)


if __name__ == '__main__':
    want = [int(a) for a in sys.argv[1:] if a.isdigit()] or sorted(STAGES)
    for n in want:
        st = STAGES[n]()
        if '--preview' in sys.argv: st.preview('preview_stage%d.png' % n)
    update_tileset()
