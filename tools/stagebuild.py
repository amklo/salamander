"""Shared code for turning a cleaned-up original MSX stage map into game assets (used by tools/stages_build.py).

A stage is described by a dict (see stages_build.py). From it this writes:
  assets/sprites/stage<N>_t<i>.png    the terrain art, cut in strips of at most 1024 px (image layers)
  assets/sprites/stage<N>_bricks.png  destructible bricks (only if the stage has any), as big as the map
  assets/sprites/<sprite>.png         every enemy / boss cut from the map
  assets/maps/stage<N>.tmx            the map: art layers, collision + bricks layers, objects, camera path

Enemies are found by matching a sprite cut from one spot of the map (a template) against the whole map, also
flipped. Every match is taken out of the art and becomes an object in the map, so the game can draw and move it.
"""
import os
import numpy as np
from PIL import Image
from scipy import ndimage

CELL = 8
BG = (6 << 16) | (6 << 8) | 6
FH, FV = 0x80000000, 0x40000000
OBJ_FIRST = 39                       # objects.tsx firstgid in every map


def c(r, g, b): return (r << 16) | (g << 8) | b


def rgba(a, keep):
    out = np.zeros(a.shape + (4,), np.uint8)
    out[:, :, 0] = (a >> 16) & 255
    out[:, :, 1] = (a >> 8) & 255
    out[:, :, 2] = a & 255
    out[:, :, 3] = np.where(keep, 255, 0)
    return Image.fromarray(out, 'RGBA')


class Stage:
    def __init__(self, src_path):
        a = np.array(Image.open(src_path).convert('RGBA')).astype(np.int64)
        code = (a[:, :, 0] << 16) | (a[:, :, 1] << 8) | a[:, :, 2]
        code[a[:, :, 3] == 0] = BG
        h0, w0 = code.shape
        self.H = (h0 + CELL - 1) // CELL * CELL
        self.W = (w0 + CELL - 1) // CELL * CELL
        self.code = np.zeros((self.H, self.W), np.int64) + BG
        self.code[:h0, :w0] = code
        self.cleared = np.zeros((self.H, self.W), bool)     # taken out of the art (enemies, boss, beams...)
        self.brickpx = np.zeros((self.H, self.W), bool)     # pixels of destructible bricks
        self.decor = np.zeros((self.H, self.W), bool)       # drawn, but never solid
        self.objects = []                                   # dicts: kind, x, y (top), w, h, fh, fv, props
        self.sprites = {}                                   # name -> (code array, keep mask)

    # ------------------------------------------------------------------ helpers
    def fg(self):
        return self.code != BG

    def cut(self, box, mirror_h=False, mirror_v=False):
        x, y, w, h = box
        a = self.code[y:y + h, x:x + w].copy()
        if mirror_h: a = a[:, ::-1]
        if mirror_v: a = a[::-1, :]
        return a

    def find(self, tpl, min_share=0.85, gap=8, match=None, region=None):
        """Top-left corners where the template's pixels (only those of the colours in `match`, if given) match."""
        th, tw = tpl.shape
        keep = tpl != BG
        if match is not None: keep &= np.isin(tpl, list(match))
        pts = [(y, x, tpl[y, x]) for y in range(th) for x in range(tw) if keep[y, x]]
        code = self.code
        x0, y0, x1, y1 = (0, 0, self.W, self.H) if region is None else region
        sub = code[y0:y1, x0:x1]
        hh, ww = sub.shape[0] - th + 1, sub.shape[1] - tw + 1
        if hh <= 0 or ww <= 0: return []
        acc = np.zeros((hh, ww), np.int32)
        for y, x, col in pts:
            acc += sub[y:y + hh, x:x + ww] == col
        need = int(len(pts) * min_share)
        found = []
        ys, xs = np.nonzero(acc >= need)
        for y, x in sorted(zip(ys, xs), key=lambda p: -acc[p]):
            if all(abs(y + y0 - fy) >= gap or abs(x + x0 - fx) >= gap for fy, fx in found):
                found.append((int(y + y0), int(x + x0)))
        return found

    def add_sprite(self, name, a, keep=None):
        if keep is None: keep = a != BG
        self.sprites[name] = (a, keep)

    # ------------------------------------------------------------------ extraction
    def remove_box(self, box, colours=None):
        x, y, w, h = box
        m = self.code[y:y + h, x:x + w] != BG
        if colours is not None: m &= np.isin(self.code[y:y + h, x:x + w], list(colours))
        self.cleared[y:y + h, x:x + w] |= m

    def component_at(self, px, py, dil=2):
        f = self.fg() & ~self.cleared
        lab, _ = ndimage.label(ndimage.binary_dilation(f, np.ones((2 * dil + 1, 2 * dil + 1))), np.ones((3, 3)))
        L = lab[py, px]
        assert L, 'no picture at %d,%d' % (px, py)
        m = (lab == L) & f
        ys, xs = np.nonzero(m)
        return m, (int(xs.min()), int(ys.min()), int(xs.max() - xs.min() + 1), int(ys.max() - ys.min() + 1))

    def take_boss(self, name, px, py, dil=2, extra=()):
        """The boss: the picture around (px, py) (plus the boxes in extra) becomes sprite `name`; taken out of the art."""
        m, box = self.component_at(px, py, dil)
        for bx, by, bw, bh in extra:
            m[by:by + bh, bx:bx + bw] |= self.code[by:by + bh, bx:bx + bw] != BG
        ys, xs = np.nonzero(m)
        x, y = int(xs.min()), int(ys.min())
        w, h = int(xs.max()) - x + 1, int(ys.max()) - y + 1
        sub = self.code[y:y + h, x:x + w]
        self.add_sprite(name, sub.copy(), m[y:y + h, x:x + w])
        self.cleared |= m
        return x, y, w, h

    def take_box(self, name, box):
        """Everything drawn in the box becomes sprite `name` (cropped to what is drawn) and leaves the art."""
        x, y, w, h = box
        m = np.zeros((self.H, self.W), bool)
        m[y:y + h, x:x + w] = self.code[y:y + h, x:x + w] != BG
        m &= ~self.cleared
        ys, xs = np.nonzero(m)
        x, y = int(xs.min()), int(ys.min())
        w, h = int(xs.max()) - x + 1, int(ys.max()) - y + 1
        self.add_sprite(name, self.code[y:y + h, x:x + w].copy(), m[y:y + h, x:x + w])
        self.cleared |= m
        return x, y, w, h

    def templates(self, kind, box, flips=((False, False),), share=0.85, match=None, sprite=None, props=None,
                  keep_mask=None, region=None, brick=False, gap=8, clear_box=False):
        """Finds every copy of the picture in `box` (also flipped as listed). brick=True: they become bricks."""
        base = self.cut(box)
        if keep_mask is not None: base = np.where(keep_mask, base, BG)
        if sprite: self.add_sprite(sprite, base)
        n = 0
        taken = []
        for fh, fv in flips:
            t = base[:, ::-1] if fh else base
            t = t[::-1, :] if fv else t
            th, tw = t.shape
            for y, x in self.find(t, share, gap, match, region):
                if any(abs(x - tx) < gap and abs(y - ty) < gap for tx, ty in taken): continue
                if kind and any(o['kind'] == kind and abs(x + tw / 2 - o['x'] - o['w'] / 2) < max(tw, o['w']) / 2
                                and abs(y + th / 2 - o['y'] - o['h'] / 2) < max(th, o['h']) / 2 for o in self.objects):
                    continue                                         # (already found by another template)
                taken.append((x, y))
                hit = (self.code[y:y + th, x:x + tw] == t) & (t != BG)
                if brick:
                    self.brickpx[y:y + th, x:x + tw] |= hit
                else:
                    if clear_box: hit = self.code[y:y + th, x:x + tw] != BG    # (in the open: everything in the box)
                    self.cleared[y:y + th, x:x + tw] |= hit
                    self.objects.append(dict(kind=kind, x=x, y=y, w=tw, h=th, fh=fh, fv=fv, props=dict(props or {})))
                n += 1
        return n

    def remove_stars(self, max_area=3, radius=6, region=None):
        """Single dots of space far from anything else (the game draws its own star field)."""
        f = self.fg() & ~self.cleared
        lab, n = ndimage.label(f, np.ones((3, 3)))
        sizes = ndimage.sum(f, lab, range(1, n + 1))
        small = np.isin(lab, np.nonzero(sizes <= max_area)[0] + 1)
        big = f & ~small
        near = ndimage.binary_dilation(f, np.ones((2 * radius + 1, 2 * radius + 1))) if False else None
        # count other foreground pixels around each small dot
        cnt = ndimage.uniform_filter(f.astype(float), size=2 * radius + 1) * (2 * radius + 1) ** 2
        own = ndimage.uniform_filter(small.astype(float), size=3) * 9
        lonely = small & (cnt - own < 1.5) & ~ndimage.binary_dilation(big, np.ones((2 * radius + 1,) * 2))
        if region is not None:
            x0, y0, x1, y1 = region
            r = np.zeros_like(lonely); r[y0:y1, x0:x1] = True
            lonely &= r
        self.cleared |= lonely
        return int(lonely.sum())

    def thin_beam(self, x0, x1, y0, y1, colours, max_th=3):
        """Removes a horizontal beam: pixels of `colours` in the band that belong to runs at most max_th tall."""
        sub = np.isin(self.code[y0:y1, x0:x1], list(colours)) & ~self.cleared[y0:y1, x0:x1]
        thick = ndimage.binary_opening(sub, np.ones((max_th + 1, 1)))
        thick = ndimage.binary_dilation(thick, np.ones((3, 3))) & sub
        beam = sub & ~thick
        self.cleared[y0:y1, x0:x1] |= beam
        ys, xs = np.nonzero(beam)
        if not len(ys): return None
        return x0 + int(xs.min()), y0 + int(ys.min()), int(xs.max() - xs.min() + 1), int(ys.max() - ys.min() + 1)

    def mark_decor(self, max_area=20, colours=None):
        """Small loose bits (lights, rings, web threads...) stay in the art but are never solid."""
        f = self.fg() & ~self.cleared
        if colours is not None: f &= np.isin(self.code, list(colours))
        lab, n = ndimage.label(f, np.ones((3, 3)))
        sizes = ndimage.sum(f, lab, range(1, n + 1))
        self.decor |= np.isin(lab, np.nonzero(sizes <= max_area)[0] + 1)

    # ------------------------------------------------------------------ output
    def grids(self, solid_min=12, brick_min=12, decor=None):
        art = self.fg() & ~self.cleared
        solid_px = art & ~self.brickpx & ~self.decor
        if decor is not None: solid_px &= ~np.isin(self.code, list(decor))
        rows, cols = self.H // CELL, self.W // CELL
        sp = solid_px.reshape(rows, CELL, cols, CELL).sum(axis=(1, 3))
        bp = (self.brickpx & art).reshape(rows, CELL, cols, CELL).sum(axis=(1, 3))
        self.solid = sp >= solid_min
        self.brick = ~self.solid & (bp >= brick_min)
        brick_cells = np.kron(self.brick, np.ones((CELL, CELL), bool))
        self.brick_art = self.brickpx & art & brick_cells
        self.art = art & ~self.brick_art

    def save(self, n, spr_dir, vertical, chunk=1024):
        os.makedirs(spr_dir, exist_ok=True)
        self.art_layers = []
        L = self.H if vertical else self.W
        k = (L + chunk - 1) // chunk
        for i in range(k):
            a0, a1 = i * chunk, min(L, (i + 1) * chunk)
            if vertical:
                sub, keep, off = self.code[a0:a1], self.art[a0:a1], (0, a0)
            else:
                sub, keep, off = self.code[:, a0:a1], self.art[:, a0:a1], (a0, 0)
            name = 'stage%d_t%d.png' % (n, i)
            rgba(sub, keep).save(spr_dir + name)
            self.art_layers.append((name, off[0], off[1], sub.shape[1], sub.shape[0]))
        self.has_bricks = bool(self.brick.any())
        if self.has_bricks:
            rgba(self.code, self.brick_art).save(spr_dir + 'stage%d_bricks.png' % n)
        for name, (a, keep) in self.sprites.items():
            rgba(a, keep).save(spr_dir + name + '.png')

    def tmx(self, path, props, tile_ids, path_points, checkpoints, waves=(), speeds=None, extra_objects=()):
        """tile_ids: kind -> objects.tsx tile id (kinds missing here are left out). path_points: centre of the screen."""
        rows, cols = self.H // CELL, self.W // CELL
        lines, oid = [], 1
        for o in sorted(self.objects, key=lambda o: (o['x'], o['y'])) + list(extra_objects):
            if o['kind'] not in tile_ids: continue
            t = tile_ids[o['kind']]
            gid = OBJ_FIRST + (t if isinstance(t, int) else t[0]) + (FH if o['fh'] else 0) + (FV if o['fv'] else 0)
            pr = o.get('props') or {}
            body = ''
            if pr:
                body = '>\n   <properties>\n' + ''.join(
                    '    <property name="%s"%s value="%s"/>\n' % (k, (' type="bool"' if isinstance(v, bool) else
                                                                    ' type="int"' if isinstance(v, int) else
                                                                    ' type="float"' if isinstance(v, float) else ''),
                                                               str(v).lower() if isinstance(v, bool) else v)
                    for k, v in pr.items()) + '   </properties>\n  </object>'
            lines.append('  <object id="%d" gid="%d" x="%d" y="%d" width="%d" height="%d"%s' % (
                oid, gid, o['x'], o['y'] + o['h'], o['w'], o['h'], body or '/>'))
            oid += 1
        for kind, x, y, fv, pr in waves:            # flying waves: (kind, x, y of its middle, flipped, props)
            tid, tw, th = tile_ids[kind]
            gid = OBJ_FIRST + tid + (FV if fv else 0)
            body = '/>' if not pr else '>\n   <properties>\n' + ''.join(
                '    <property name="%s" type="%s" value="%s"/>\n' % (k, 'bool' if isinstance(v, bool) else 'int', str(v).lower())
                for k, v in pr.items()) + '   </properties>\n  </object>'
            lines.append('  <object id="%d" gid="%d" x="%d" y="%d" width="%d" height="%d"%s' % (oid, gid, x - tw // 2, y + th // 2, tw, th, body))
            oid += 1
        x0, y0 = path_points[0]
        pts = ' '.join('%g,%g' % (x - x0, y - y0) for x, y in path_points)
        sp = '' if not speeds else '\n   <properties>\n    <property name="speeds" value="%s"/>\n   </properties>' % ','.join('%g' % s for s in speeds)
        lines.append('  <object id="%d" name="path" x="%g" y="%g">%s\n   <polyline points="%s"/>\n  </object>' % (oid, x0, y0, sp, pts))
        oid += 1
        for cx, cy in checkpoints:
            lines.append('  <object id="%d" name="checkpoint" type="checkpoint" x="%g" y="%g" width="16" height="16"/>' % (oid, cx - 8, cy - 8))
            oid += 1

        def csv(cells, gid):
            return '\n' + ',\n'.join(','.join(str(gid) if v else '0' for v in row) for row in cells) + '\n'

        art = ''.join('  <imagelayer id="%d" name="art %d" offsetx="%d" offsety="%d">\n   <image source="../sprites/%s" width="%d" height="%d"/>\n  </imagelayer>\n'
                      % (i + 1, i, ox, oy, name, w, h) for i, (name, ox, oy, w, h) in enumerate(self.art_layers))
        nl = len(self.art_layers)
        pr = ''.join('  <property name="%s"%s value="%s"/>\n' % (k, (' type="bool"' if isinstance(v, bool) else
                                                                   ' type="int"' if isinstance(v, int) else
                                                                   ' type="float"' if isinstance(v, float) else ''),
                                                              str(v).lower() if isinstance(v, bool) else v)
                     for k, v in props.items())
        bricks = ''
        if self.has_bricks:
            bricks = ' <layer id="%d" name="bricks" width="%d" height="%d" opacity="0.6">\n  <data encoding="csv">%s</data>\n </layer>\n' % (
                nl + 2, cols, rows, csv(self.brick, 2))
        out = '''<?xml version="1.0" encoding="UTF-8"?>
<map version="1.10" tiledversion="1.10.2" orientation="orthogonal" renderorder="right-down" width="%d" height="%d" tilewidth="8" tileheight="8" infinite="0" nextlayerid="%d" nextobjectid="%d">
 <properties>
%s </properties>
 <tileset firstgid="1" source="collision.tsx"/>
 <tileset firstgid="2" source="bricks.tsx"/>
 <tileset firstgid="3" source="terrain.tsx"/>
 <tileset firstgid="%d" source="objects.tsx"/>
%s <layer id="%d" name="collision" width="%d" height="%d" opacity="0.6">
  <data encoding="csv">%s</data>
 </layer>
%s <objectgroup id="%d" name="objects">
%s
 </objectgroup>
</map>
''' % (cols, rows, nl + 4, oid, pr, OBJ_FIRST, art, nl + 1, cols, rows, csv(self.solid, 1), bricks, nl + 3, '\n'.join(lines))
        open(path, 'w').write(out)

    def preview(self, path, scale=1, path_points=()):
        """Check picture: art, bricks (yellow tint), solid cells (red outline), objects (boxes), path."""
        from PIL import ImageDraw
        im = np.array(rgba(np.where(self.art | self.brick_art, self.code, BG), self.art | self.brick_art).convert('RGB'))
        sol = np.kron(self.solid, np.ones((CELL, CELL), bool))
        im[sol] = (im[sol] * 0.6 + np.array([120, 0, 0])).astype(np.uint8)
        br = np.kron(self.brick, np.ones((CELL, CELL), bool))
        im[br] = (im[br] * 0.5 + np.array([120, 120, 0])).astype(np.uint8)
        img = Image.fromarray(im)
        d = ImageDraw.Draw(img)
        cols = {}
        for o in self.objects:
            col = cols.setdefault(o['kind'], tuple(int(v) for v in np.random.RandomState(abs(hash(o['kind'])) % 1000).randint(80, 255, 3)))
            d.rectangle([o['x'], o['y'], o['x'] + o['w'] - 1, o['y'] + o['h'] - 1], outline=col)
            d.text((o['x'], max(0, o['y'] - 9)), o['kind'][:6], fill=col)
        if path_points: d.line(path_points, fill=(255, 255, 0), width=1)
        if scale != 1: img = img.resize((img.width * scale, img.height * scale), Image.NEAREST)
        img.save(path)
