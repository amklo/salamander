"""Stage 1: turn the original MSX map into terrain art, collision cells, teeth / tower / brick sprites.

Everything is cut at 1:1 (the map was ripped from the MSX at its native resolution, 168px tall playfield).
The crop starts at map x=594 so the brick maze (8px blocks from x=4162) lands exactly on the 8px collision grid.
"""
import numpy as np, struct, os, json
from PIL import Image
from scipy import ndimage
Image.MAX_IMAGE_PIXELS=None
# Run from the project folder:  python3 tools/stage1_build.py
# Needs numpy, scipy, pillow. Writes into tools/stage1_out/:
#   stage1_t*.png, brick_pattern.png, asteroid_*.png, fang*.png -> assets/sprites/
#   meta.json (teeth / asteroid positions, collision + brick cells) is used by tools/stage1_tmx.py
SRC='reference matterial/salamanderstage1.png'
OUT='tools/stage1_out'; os.makedirs(OUT,exist_ok=True)
full=np.array(Image.open(SRC).convert('RGB')).astype(np.int16)
X0,Y0,X1,Y1=594,564,4680,1116          # MSX map crop (map pixels); X0 puts the brick maze on the 8px grid
a=full[Y0:Y1,X0:X1]; Hm,Wm=a.shape[:2]
white=(a==255).all(axis=2)
black=(a[:,:,0]<=10)&(a[:,:,1]<=10)&(a[:,:,2]<=10)
content=~white&~black

# ---------- brick maze: 8px blocks (the brick picture repeats every 16px), wall from x 4162..4354, y 780..1084
BB=8
BX0,BY0,BX1,BY1=4162-X0,780-Y0,4354-X0,1084-Y0
bcols=(BX1-BX0)//BB; brows=(BY1-BY0)//BB
bricks=np.zeros((brows,bcols),bool)   # row 0 = top
for r in range(brows):
    for c in range(bcols):
        blk=content[BY0+r*BB:BY0+r*BB+BB, BX0+c*BB:BX0+c*BB+BB]
        bricks[r,c]=blk.mean()>0.35
inbrick=np.zeros_like(content); inbrick[BY0:BY1,BX0:BX1]=True

# ---------- separate terrain from sprites
lab,n=ndimage.label(content&~inbrick,structure=np.ones((3,3)))
sizes=ndimage.sum(content,lab,range(1,n+1))
objs=ndimage.find_objects(lab)
keep=np.zeros(n+1,bool)
for i,s in enumerate(sizes):
    sl=objs[i]; x0=sl[1].start+X0
    if s>1500: keep[i+1]=True
    if 2970<=x0<=3910 and s>=8:                          # fangs + blue tentacle walls (not asteroids)
        px=a[sl][lab[sl]==i+1]
        if np.mean((px[:,2]>200)&(px[:,0]<140))>0.3 or np.mean((px[:,0]>200)&(px[:,1]>180)&(px[:,2]<140))>0.3: keep[i+1]=True
# grow: pieces touching kept terrain (pillars / stalks)
for _ in range(6):
    km=keep[lab]
    near=ndimage.binary_dilation(km,iterations=2)
    newl=np.unique(lab[near&(lab>0)])
    changed=False
    for l in newl:
        if not keep[l] and 8<=sizes[l-1]<200:
            keep[l]=True; changed=True
    if not changed: break
# anything hugging the brick maze is its stone frame
nearb=ndimage.binary_dilation(inbrick,iterations=8)
for l in np.unique(lab[nearb&(lab>0)]): keep[l]=True
# explicit removals (map coords x,y,w,h): ground "ducks", boss + its arms
KEEP=[(2540,686,30,35)]
# stray pixels erased by hand in the tentacle passage
REMOVE=[(3037,838,6,9),(3085,838,6,9),(1795,652,72,46),(2478,628,24,20),(1926,681,24,24),(2894,775,24,20),(2590,884,24,20),(2774,884,24,20),(2942,884,24,20),(4400,940,280,176)]
terrain=keep[lab]&(lab>0)
for x,y,w,h in REMOVE:
    terrain[y-Y0:y-Y0+h, x-X0:x-X0+w]=False
# ---------- bio turrets: enemies, not rock. Cut their exact pixels out of the terrain (the sprites are
# assets/sprites/bio turret red/blue.png: frame 0 points left, frame 1 points up; ceiling ones are drawn flipped)
# (map x, map y of the 16x16 box, colour, hangs from the ceiling)
BIO_TURRETS=[(1930,684,'blue',False),(2050,580,'red',True),(2170,580,'red',True),(2226,684,'red',False),
             (2482,628,'blue',False),(2498,676,'red',True),(2594,884,'blue',False),(2754,772,'red',True),
             (2770,772,'red',True),(2778,884,'blue',False),(2898,780,'blue',True),(2946,884,'blue',False)]
turret_px=np.zeros_like(content)
for tx,ty,col,ceil in BIO_TURRETS:
    spr=np.array(Image.open(f'assets/sprites/bio turret {col}.png').convert('RGBA'))[:,:16,3]>0
    if ceil: spr=spr[::-1]
    turret_px[ty-Y0:ty-Y0+16, tx-X0:tx-X0+16]|=spr
terrain&=~turret_px
# ---------- the six moving teeth ("fangs"): cut them out of the terrain, export each as a sprite
FZ=(776,894)                                         # between ceiling rock and floor rock (map y)
FANGS=[(3500,3556,True),(3556,3615,False),(3615,3700,True),(3755,3808,True),(3808,3858,True),(3858,3910,False)]
fz=content[FZ[0]-Y0:FZ[1]-Y0, 3490-X0:3915-X0]
fl,fn=ndimage.label(fz,structure=np.ones((3,3)))
fangmasks=[np.zeros_like(content) for _ in FANGS]
for i,sl in enumerate(ndimage.find_objects(fl)):
    mm=fl==i+1; s_=mm.sum(); h_=sl[0].stop-sl[0].start; w_=sl[1].stop-sl[1].start
    if s_<20 or (h_<=9 and w_>=24): continue        # floor / ceiling bumps are not teeth
    cy,cx=ndimage.center_of_mass(mm); cx+=3490
    for k,(xa,xb,ceil) in enumerate(FANGS):
        if xa<=cx<xb:
            fangmasks[k][FZ[0]-Y0:FZ[1]-Y0, 3490-X0:3915-X0]|=mm
fang_meta=[]
for k,(xa,xb,ceil) in enumerate(FANGS):
    fm=fangmasks[k]; terrain&=~fm
    ys,xs=np.nonzero(fm); x0,x1,y0,y1=xs.min(),xs.max()+1,ys.min(),ys.max()+1
    sub=a[y0:y1,x0:x1]; mm=fm[y0:y1,x0:x1]
    r=np.zeros((y1-y0,x1-x0,4),np.uint8); r[mm,:3]=sub[mm]; r[mm,3]=255
    holes=ndimage.binary_fill_holes(mm)&~mm; r[holes,:3]=6; r[holes,3]=255    # dark gaps inside the tooth stay solid
    img=Image.fromarray(r,'RGBA'); img.save(f'{OUT}/fang{k}.png')
    fang_meta.append(dict(x=int(x0), y=int(Hm-y1), w=img.size[0], h=img.size[1], ceil=ceil))   # world, y-up
print('fangs',fang_meta)
# ---------- green-tipped towers: rock columns that grow out of the floor / ceiling. Each one becomes a sprite and is
# cut out of the terrain (the part outside the rock); in the game it rises out of the rock (drawn behind it).
# (column left x, tip row, hangs from the ceiling) in map coords; the column is 16 px wide
TOWERS=[(1714,611,True),(1954,619,True),(1346,676,False),(2002,684,False),(1610,692,False),(2546,715,True)]
TOWER_ROOT=8                                         # the sprite reaches this far into the rock, so it looks rooted
tower_meta=[]
tower_px=np.zeros_like(content)
for k,(tx,ty,ceil) in enumerate(TOWERS):
    x0=tx-X0; tip=ty-Y0; step=-1 if ceil else 1; yy=tip
    while 0<=yy<Hm:                                  # walk to where the rock on both sides starts
        if content[yy,x0-16:x0].mean()>0.6 and content[yy,x0+16:x0+32].mean()>0.6: break
        yy+=step
    surf=yy
    top,bot=(surf+1,tip+1) if ceil else (tip,surf)   # rows of the tower outside the rock
    s0,s1=(surf+1-TOWER_ROOT,tip+1) if ceil else (tip,surf+TOWER_ROOT)   # rows of the sprite
    sub=a[s0:s1,x0:x0+16]; m=content[s0:s1,x0:x0+16]
    m=ndimage.binary_fill_holes(m)
    r=np.zeros((s1-s0,16,4),np.uint8); r[m,:3]=np.where(content[s0:s1,x0:x0+16][m][:,None],sub[m],6); r[m,3]=255
    Image.fromarray(r,'RGBA').save(f'{OUT}/tower{k}.png')
    terrain[top:bot,x0:x0+16]=False
    tower_px[top:bot,x0:x0+16]=True
    tower_meta.append(dict(x=int(x0),y=int(Hm-s1),w=16,h=int(s1-s0),ceil=ceil,cells_rows=[int(top),int(bot)]))
print('towers',tower_meta)
for x,y,w,h in KEEP:
    sub=content[y-Y0:y-Y0+h, x-X0:x-X0+w]; terrain[y-Y0:y-Y0+h, x-X0:x-X0+w]|=sub
terrain&=~tower_px                                # (KEEP must not put a tower back into the rock)
terrain&=~turret_px
removed=content&~terrain&~inbrick&~tower_px&~turret_px   # (towers and turrets are sprites of their own, not asteroids)

# ---------- asteroids: removed clusters with yellowish rock colours
def isrock(px): return ((px[...,0]>190)&(px[...,1]<140)) | ((px[...,0]>200)&(px[...,1]>180)&(px[...,2]<140))
rl,rn=ndimage.label(ndimage.binary_dilation(removed,iterations=3),structure=np.ones((3,3)))
asteroids=[]
for i,sl in enumerate(ndimage.find_objects(rl)):
    m=(rl[sl]==i+1)&removed[sl]; px=a[sl][m]
    if m.sum()<60: continue
    blue=np.mean((px[:,2]>200)&(px[:,0]<140))
    rock=np.mean(isrock(px))
    x=sl[1].start; y=sl[0].start; w=sl[1].stop-x; h=sl[0].stop-y
    if X0+x>=4400: continue
    print('   removed cluster',X0+x,Y0+y,w,h,'blue',round(blue,2),'rock',round(rock,2))
    if blue<0.1 and rock>0.5 and w<=44 and h<=44 and not (2530<=X0+x<=2560 and 680<=Y0+y<=700):
        asteroids.append((x+w/2, y+h/2, max(w,h)))
print('asteroids',len(asteroids)); [print('  ',round(x+X0),round(y+Y0),s) for x,y,s in asteroids]

# ---------- world = map pixels (1:1), padded to whole 8px cells
CELL=8
Ww=(Wm+CELL-1)//CELL*CELL; Hw=(Hm+CELL-1)//CELL*CELL
def up(img):                      # pad on the right / top so the art fills whole cells (world y-up: pad at the top)
    out=Image.new('RGBA',(Ww,Hw),(0,0,0,0)); out.paste(img,(0,Hw-Hm)); return out
# unknown (white) parts of the map are normally filled with rock; these stay empty (map x0, y0, x1, y1)
NO_FILL=[(2976,564,3492,758),(596,730,1313,1116),(2976,921,3491,1116)]
for x0_,y0_,x1_,y1_ in NO_FILL:
    white[max(0,y0_-Y0):y1_-Y0, max(0,x0_-X0):x1_-X0]=False
# rock texture for unknown (white) areas: find a 16x16 solid rock patch
best=(4360-X0,816-Y0)
print('rock patch',best)
patch=a[best[1]:best[1]+16,best[0]:best[0]+16]
tiled=np.tile(patch,(Hm//16+2,Wm//16+2,1))[:Hm,:Wm]
rgba=np.zeros((Hm,Wm,4),np.uint8)
rgba[terrain,:3]=a[terrain]; rgba[terrain,3]=255
rgba[white,:3]=tiled[white]; rgba[white,3]=255
# dark crevices inside the rock are drawn opaque, so nothing (stars, retracted teeth) shows through them
sol=terrain|white
hl_,hn_=ndimage.label(~sol); hs_=ndimage.sum(~sol,hl_,range(1,hn_+1))
sm_=np.zeros(hn_+1,bool); sm_[1:]=hs_<40
fillpx=sm_[hl_]&~terrain&~white&~inbrick
rgba[fillpx,:3]=6; rgba[fillpx,3]=255
timg=up(Image.fromarray(rgba,'RGBA'))
CH=1024; chunks=(Ww+CH-1)//CH
for i in range(chunks):
    timg.crop((i*CH,0,min(Ww,(i+1)*CH),Hw)).save(f'{OUT}/stage1_t{i}.png')
# the brick picture repeats every 16px: cut one full 16x16 tile (four standing blocks, on the 16px grid of the wall)
br,bc=[(r,c) for r in range(0,brows-1,2) for c in range(0,bcols-1,2) if bricks[r:r+2,c:c+2].all()][0]
Image.fromarray(a[BY0+br*BB:BY0+br*BB+16, BX0+bc*BB:BX0+bc*BB+16].astype(np.uint8),'RGB').save(f'{OUT}/brick_pattern.png')
# asteroid sprites
def crop_sprite(x,y,w,h,name):
    sub=a[y-Y0:y-Y0+h, x-X0:x-X0+w]; m=removed[y-Y0:y-Y0+h, x-X0:x-X0+w]
    r=np.zeros((h,w,4),np.uint8); r[m,:3]=sub[m]; r[m,3]=255
    im=Image.fromarray(r,'RGBA'); im.save(f'{OUT}/{name}.png'); return im.size
print('big',crop_sprite(2394,573,22,23,'asteroid_big'))
print('small',crop_sprite(2442,773,14,14,'asteroid_small'))
# (the boss sprite, assets/sprites/boss0.png, is hand drawn now: tools/stage1_brain.py is no longer used)

# ---------- collision cells (8px), Tiled order: row 0 = top
solid=terrain|white
holes=~solid                       # close small holes inside rock (black crevices) but keep real tunnels
hl,hn=ndimage.label(holes)
hs=ndimage.sum(holes,hl,range(1,hn+1))
small=np.zeros(hn+1,bool); small[1:]=hs<40
solid=solid|small[hl]
solid[inbrick]=False
sw=np.zeros((Hw,Ww),bool); sw[Hw-Hm:,:Wm]=solid          # padded like the art
gw=Ww//CELL; gh=Hw//CELL
coll=(sw.reshape(gh,CELL,gw,CELL).mean(axis=(1,3))>=0.4).astype(int)
# bricks: one block per cell
brk=np.zeros((gh,gw),int)
bc0=(BX0)//CELL; br0=(Hw-Hm+BY0)//CELL
brk[br0:br0+brows, bc0:bc0+bcols]=bricks
assert BX0%16==0 and (Hw-Hm+BY0)%CELL==0, 'brick maze must sit on the cell grid (and the 16px brick picture)'
print('world',Ww,Hw,'cells',gw,gh,'chunks',chunks)
meta=dict(Ww=Ww,Hw=Hw,cell=CELL,chunks=chunks,cropX=X0,cropY=Y0,pad_top=Hw-Hm,fangs=fang_meta,towers=tower_meta,
  asteroids=[(round(x,1),round(Hm-y,1),s_) for x,y,s_ in asteroids],
  collision=coll.tolist(),bricks=brk.tolist())
json.dump(meta,open(f'{OUT}/meta.json','w'))
Image.fromarray((np.maximum(coll*255,brk*128)).astype(np.uint8)).save(f'{OUT}/cells_preview.png')
