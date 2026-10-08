"""One-off: convert assets/maps/stage1.tmx from the old 1.62x-scaled layout to the 1:1 MSX scale.

Run from the project folder after tools/stage1_build.py:   python3 tools/stage1_tmx.py
- every object (enemies, asteroids, checkpoints, the camera path) keeps its place, scaled down by 168/272
- the teeth snap to the freshly cut tooth sprites (they must line up with the rock art exactly)
- the collision and bricks layers are rebuilt from tools/stage1_out/meta.json
- the art image layers point at the new stage1_t0..t3.png; objects.tsx gets the new sprite sizes
"""
import json
import xml.etree.ElementTree as ET

OLD_S = 272 / 168          # old world px per map px
OLD_X0, NEW_X0 = 600, 594  # map x where the crop started (old / new)
OLD_TOP_PAD = 2            # old art layers sat 2px below the map top (896 - 894)
MAP = 'assets/maps/stage1.tmx'
TSX = 'assets/maps/objects.tsx'
meta = json.load(open('tools/stage1_out/meta.json'))
pad_top = meta['pad_top']
Hw, Ww, CELL = meta['Hw'], meta['Ww'], meta['cell']


def nx(x): return round(x / OLD_S + (OLD_X0 - NEW_X0), 1)
def ny(y): return round((y - OLD_TOP_PAD) / OLD_S + pad_top, 1)
def fmt(v): return ('%.1f' % v).rstrip('0').rstrip('.')


tree = ET.parse(MAP)
root = tree.getroot()
if int(root.get('height')) * int(root.get('tileheight')) <= Hw:
    raise SystemExit(MAP + ' is already at 1:1 scale - nothing to do')
root.set('width', str(Ww // CELL))
root.set('height', str(Hw // CELL))
for p in root.find('properties'):
    if p.get('name') == 'speed':
        p.set('value', fmt(float(p.get('value')) / OLD_S))     # same pace through a 1.62x shorter stage
props = root.find('properties')
if not any(p.get('name') == 'lane' for p in props):
    ET.SubElement(props, 'property', name='lane', type='int', value='168')   # the stage fits the view: no drift

# tilesets: firstgid -> class / image size (from objects.tsx)
tsx = ET.parse(TSX)
first = int([t for t in root.findall('tileset') if t.get('source') == 'objects.tsx'][0].get('firstgid'))
tiles = {first + int(t.get('id')): t for t in tsx.getroot().findall('tile')}

# ---- image layers: 4 chunks now
art = [c for c in root if c.tag == 'imagelayer']
chunks = meta['chunks']
for i, il in enumerate(art):
    if i >= chunks:
        root.remove(il)
        continue
    img = il.find('image')
    w = min(1024, Ww - i * 1024)
    img.set('width', str(w)); img.set('height', str(Hw))
    il.set('offsetx', str(i * 1024)); il.set('offsety', '0')

# ---- tile layers
for layer in root.findall('layer'):
    name = layer.get('name')
    layer.set('width', str(Ww // CELL)); layer.set('height', str(Hw // CELL))
    cells = meta['collision'] if name == 'collision' else meta['bricks'] if name == 'bricks' else None
    if cells is None:
        raise SystemExit('unexpected tile layer ' + name)
    gid = 1 if name == 'collision' else 2
    rows = [','.join(str(gid if v else 0) for v in row) for row in cells]
    layer.find('data').text = '\n' + ',\n'.join(rows) + '\n'

# ---- objects
fangs = meta['fangs']
coll = meta['collision']                             # row 0 = top


def solid(cx, r):
    c = int(cx // CELL)
    return r < 0 or r >= len(coll) or c < 0 or c >= len(coll[0]) or coll[r][c] == 1


def snap(cx, y, h, ceil):
    """Tiled y (bottom edge) of a floor / ceiling unit, moved onto the nearest surface (if within 16px)."""
    if not ceil:                                     # bottom edge onto the top of the ground below its middle
        r = int((y - h / 2) // CELL)
        while not solid(cx, r): r += 1
        while solid(cx, r - 1): r -= 1               # started inside rock: climb out
        ny_ = r * CELL
    else:                                            # top edge against the underside of the ceiling
        r = int((y - h / 2) // CELL)
        while not solid(cx, r): r -= 1
        while solid(cx, r + 1): r += 1
        ny_ = (r + 1) * CELL + h
    return ny_ if abs(ny_ - y) <= 16 else y
for og in root.findall('objectgroup'):
    for o in og.findall('object'):
        x, y = float(o.get('x')), float(o.get('y'))
        w, h = float(o.get('width') or 0), float(o.get('height') or 0)
        pl = o.find('polyline')
        if pl is not None:                           # camera path: absolute points, re-anchored at the first one
            pts = [tuple(map(float, p.split(','))) for p in pl.get('points').split()]
            ab = [(nx(x + px), ny(y + py)) for px, py in pts]
            o.set('x', fmt(ab[0][0])); o.set('y', fmt(ab[0][1]))
            pl.set('points', ' '.join('%s,%s' % (fmt(ax - ab[0][0]), fmt(ay - ab[0][1])) for ax, ay in ab))
            continue
        graw = int(o.get('gid') or 0)
        gid = graw & 0x1fffffff
        flipv = bool(graw & 0x40000000)
        cls = tiles[gid].get('class') if gid in tiles else (o.get('type') or o.get('name') or '')
        if gid == 0:                                 # point / plain objects (checkpoints)
            o.set('x', fmt(nx(x))); o.set('y', fmt(ny(y)))
            continue
        top = y - h                                  # tile objects: y is the bottom edge in Tiled
        if cls == 'tooth':
            k = gid - (first + 9)                    # fang0..fang5
            f = fangs[k]
            o.set('x', fmt(f['x'])); o.set('y', fmt(Hw - f['y']))
            o.set('width', str(f['w'])); o.set('height', str(f['h']))
        elif cls == 'asteroid':                      # sizes scale with the map
            o.set('x', fmt(nx(x))); o.set('y', fmt(ny(y)))
            o.set('width', fmt(w / OLD_S)); o.set('height', fmt(h / OLD_S))
        elif cls in ('walker', 'turret'):            # keep them on their floor / ceiling, sprite size unchanged
            prop = {p.get('name'): p.get('value') for p in o.iter('property')}
            ceil = prop.get('ceiling', 'true' if flipv else 'false') == 'true'
            x2 = nx(x + w / 2) - w / 2
            y2 = ny(top) + h if ceil else ny(y)
            o.set('x', fmt(x2))
            o.set('y', fmt(snap(x2 + w / 2, y2, h, ceil)))
        else:                                        # flyers: keep the centre
            o.set('x', fmt(nx(x + w / 2) - w / 2))
            o.set('y', fmt(ny(y - h / 2) + h / 2))

# ---- objects.tsx: new sprite sizes (stage 1 is the only user of asteroids and teeth)
sizes = {'asteroid_big.png': (22, 23), 'asteroid_small.png': (14, 14)}
for k, f in enumerate(fangs):
    sizes['fang%d.png' % k] = (f['w'], f['h'])
for t in tsx.getroot().findall('tile'):
    img = t.find('image')
    name = img.get('source').split('/')[-1]
    if name in sizes:
        img.set('width', str(sizes[name][0])); img.set('height', str(sizes[name][1]))

ET.indent(tree, ' ')
tree.write(MAP, encoding='UTF-8', xml_declaration=True)
ET.indent(tsx, ' ')
tsx.write(TSX, encoding='UTF-8', xml_declaration=True)
print('wrote', MAP, 'and', TSX)
