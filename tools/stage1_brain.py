"""Stage 1 boss: cut the brain (without its arms) out of the original map."""
import numpy as np
from PIL import Image
from scipy import ndimage
Image.MAX_IMAGE_PIXELS=None
def extract_brain(im, S, out):
    Y0,Y1,X0,X1=948,1075,4470,4590
    a=im[Y0:Y1,X0:X1]
    content=~((a<=10).all(axis=2))
    lab,n=ndimage.label(content,structure=np.ones((3,3)))
    blue=(a[:,:,2]>200)&(a[:,:,0]<140)
    cyan=(a[:,:,1]>200)&(a[:,:,2]>200)&(a[:,:,0]<100)
    white=(a>230).all(axis=2)
    armish=ndimage.binary_dilation(blue|cyan,iterations=2)
    m=content&~blue&~cyan&~(white&armish)
    for l in range(1,n+1):                       # whole arm-chain segments go
        mm=lab==l
        if (blue&mm).sum()/max(1,mm.sum())>0.5: m&=~mm
    l2,n2=ndimage.label(m,structure=np.ones((3,3)))
    sz=ndimage.sum(m,l2,range(1,n2+1)); big=np.argmax(sz)+1
    near=ndimage.binary_dilation(l2==big,iterations=10)
    final=np.zeros_like(m)
    for l in range(1,n2+1):
        if (near&(l2==l)).any() and sz[l-1]>=4: final|=l2==l
    ys,xs=np.nonzero(final)
    sub=a[ys.min():ys.max()+1, xs.min():xs.max()+1]; fm=final[ys.min():ys.max()+1, xs.min():xs.max()+1]
    r=np.zeros(sub.shape[:2]+(4,),np.uint8); r[fm,:3]=sub[fm]; r[fm,3]=255
    holes=ndimage.binary_fill_holes(fm)&~fm; r[holes,:3]=6; r[holes,3]=255     # dark gaps inside stay solid
    img=Image.fromarray(r,'RGBA')
    img=img.resize((round(img.size[0]*S),round(img.size[1]*S)),Image.NEAREST); img.save(out)
    return img.size
