"""Stage 1: turn the original MSX map into terrain art, a collision grid, teeth/boss sprites and object lists."""
import numpy as np, struct, os, json
from PIL import Image
from scipy import ndimage
Image.MAX_IMAGE_PIXELS=None
# Run from the project folder:  python3 tools/stage1_build.py  then  python3 tools/stage1_emit.py
# Needs numpy, scipy, pillow. Writes into tools/stage1_out/; copy the results to
#   stage1_t*.png, stage1_bricks.png, asteroid_*.png, fang*.png, boss0.png -> assets/sprites/
#   stage1.grid                                                          -> assets/maps/
#   Stage1Map.java                                                       -> core/src/main/java/com/example/salamander/
SRC='reference matterial/salamanderstage1.png'
OUT='tools/stage1_out'; os.makedirs(OUT,exist_ok=True)
full=np.array(Image.open(SRC).convert('RGB')).astype(np.int16)
X0,Y0,X1,Y1=600,564,4680,1116          # MSX map crop (map pixels)
a=full[Y0:Y1,X0:X1]; Hm,Wm=a.shape[:2]
white=(a==255).all(axis=2)
black=(a[:,:,0]<=10)&(a[:,:,1]<=10)&(a[:,:,2]<=10)
content=~white&~black

# ---------- brick maze (destructible blocks of 16 map px)
BX0,BY0,BX1,BY1=4164-X0,780-Y0,4356-X0,1084-Y0
bcols=(BX1-BX0)//16; brows=(BY1-BY0)//16
bricks=np.zeros((brows,bcols),bool)   # row 0 = top
for r in range(brows):
    for c in range(bcols):
        blk=content[BY0+r*16:BY0+r*16+16, BX0+c*16:BX0+c*16+16]
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
# ...except the thin strip of brick pattern on the maze's left edge: it is part of the brick wall
# (shootable), not stone, so it must not stay behind as solid terrain in front of the first bricks
EDGE=(4156,776,8,310)        # x, y, w, h in map pixels
# explicit removals (map coords x,y,w,h): ground "ducks", boss + its arms
KEEP=[(2540,686,30,35)]
REMOVE=[(1795,652,72,46),(2478,628,24,20),(1926,681,24,24),(2894,775,24,20),(2590,884,24,20),(2774,884,24,20),(2942,884,24,20),(4400,940,280,176)]
terrain=keep[lab]&(lab>0)
for x,y,w,h in REMOVE:
    terrain[y-Y0:y-Y0+h, x-X0:x-X0+w]=False
for yy in range(EDGE[1],EDGE[1]+EDGE[3]):     # keep it only where stone continues right next to it
    if not terrain[yy-Y0, EDGE[0]-X0-6:EDGE[0]-X0].any():
        terrain[yy-Y0, EDGE[0]-X0:EDGE[0]-X0+EDGE[2]]=False
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
    S_=272/168
    img=Image.fromarray(r,'RGBA').resize((round((x1-x0)*S_),round((y1-y0)*S_)),Image.NEAREST); img.save(f'{OUT}/fang{k}.png')
    fang_meta.append(dict(x=(x0)*S_, y=(Hm-y1)*S_, w=img.size[0], h=img.size[1], ceil=ceil))
print('fangs',fang_meta)
for x,y,w,h in KEEP:
    sub=content[y-Y0:y-Y0+h, x-X0:x-X0+w]; terrain[y-Y0:y-Y0+h, x-X0:x-X0+w]|=sub
removed=content&~terrain&~inbrick

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

# ---------- scale to world
S=272/168
Ww=int(round(Wm*S)); Hw=int(round(Hm*S))
def up(img,res=Image.NEAREST): return img.resize((Ww,Hw),res)
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
# bricks image (world-scaled)
bimg=Image.fromarray(a[BY0:BY1,BX0:BX1].astype(np.uint8),'RGB').convert('RGBA')
bw=int(round((BX1-BX0)*S)); bh=int(round((BY1-BY0)*S))
bimg=bimg.resize((bw,bh),Image.NEAREST); bimg.save(f'{OUT}/stage1_bricks.png')
# asteroid sprites
def crop_sprite(x,y,w,h,name):
    sub=a[y-Y0:y-Y0+h, x-X0:x-X0+w]; m=removed[y-Y0:y-Y0+h, x-X0:x-X0+w]
    r=np.zeros((h,w,4),np.uint8); r[m,:3]=sub[m]; r[m,3]=255
    im=Image.fromarray(r,'RGBA'); im=im.resize((int(round(w*S)),int(round(h*S))),Image.NEAREST); im.save(f'{OUT}/{name}.png'); return im.size
print('big',crop_sprite(2394,573,22,23,'asteroid_big'))
print('small',crop_sprite(2442,773,14,14,'asteroid_small'))
# the boss: the brain from the end of the map (sprite only; its arms are left out)
import sys; sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from stage1_brain import extract_brain
print('boss', extract_brain(full, S, f'{OUT}/boss0.png'))

# ---------- collision grid (cell = 4 world px), y-up rows
solid=terrain|white
# close small holes inside rock (black crevices) but keep real tunnels
holes=~solid
hl,hn=ndimage.label(holes)
hs=ndimage.sum(holes,hl,range(1,hn+1))
small=np.zeros(hn+1,bool); small[1:]=hs<40
solid=solid|small[hl]
solid[inbrick]=False
CELL=4
gw=Ww//CELL+1; gh=Hw//CELL+1
sw=np.array(Image.fromarray((solid*255).astype(np.uint8)).resize((Ww,Hw),Image.NEAREST))>127
grid=np.zeros((gh,gw),np.uint8)
for r in range(gh):
    y0w=Hw-(r+1)*CELL; y1w=Hw-r*CELL       # world row r (y-up) -> image rows
    ys=slice(max(0,y0w),max(0,y1w))
    for c in range(gw):
        blk=sw[ys, c*CELL:(c+1)*CELL]
        grid[r,c]=1 if blk.size and blk.mean()>=0.4 else 0
# beyond map edges count as solid
with open(f'{OUT}/stage1.grid','wb') as f:
    f.write(struct.pack('>iii',gw,gh,CELL)); f.write(grid.tobytes())
print('world',Ww,Hw,'grid',gw,gh,'chunks',chunks)

def wx(mx): return (mx-X0)*S
def wy(my): return (Y1-my)*S
meta=dict(S=S,fangs=fang_meta,Ww=Ww,Hw=Hw,chunks=chunks,
  brick=dict(x=wx(BX0+X0),y=wy(BY1+Y0),cols=bcols,rows=brows,block=16*S,alive=bricks[::-1].astype(int).tolist(),w=bw,h=bh),
  asteroids=[(round(wx(x+X0),1),round(wy(y+Y0),1),round(s*S,1)) for x,y,s in asteroids])
json.dump(meta,open(f'{OUT}/meta.json','w'))
Image.fromarray((grid[::-1]*255).astype(np.uint8)).save(f'{OUT}/grid_preview.png')
