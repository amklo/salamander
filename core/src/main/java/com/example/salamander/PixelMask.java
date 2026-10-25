package com.example.salamander;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Pixmap;

/** Which pixels of a sprite are solid, for exact collisions with oddly shaped things (teeth, the brain boss, domes). */
public final class PixelMask {
    public final int w, h;
    private final boolean[] solid;   // row 0 = bottom

    public PixelMask(int w, int h, boolean[] solid) { this.w = w; this.h = h; this.solid = solid; }

    /** Builds the mask from a PNG's alpha channel (drawn 1:1, so mask pixels = world pixels). */
    public static PixelMask load(String internalPath) {
        Pixmap pm = new Pixmap(Gdx.files.internal(internalPath));
        PixelMask m = fromPixmap(pm, 0, pm.getWidth());
        pm.dispose();
        return m;
    }

    /** Masks of the frames of a horizontal strip, each frameW wide (frame 0 = leftmost). */
    public static PixelMask[] loadFrames(String internalPath, int frameW) {
        Pixmap pm = new Pixmap(Gdx.files.internal(internalPath));
        PixelMask[] f = new PixelMask[Math.max(1, pm.getWidth() / frameW)];
        for (int i = 0; i < f.length; i++) f[i] = fromPixmap(pm, i * frameW, frameW);
        pm.dispose();
        return f;
    }

    /** Mask of the columns x0 .. x0+w-1 of a picture (its alpha channel). */
    public static PixelMask fromPixmap(Pixmap pm, int x0, int w) {
        int h = pm.getHeight();
        boolean[] s = new boolean[w * h];
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++)
                s[(h - 1 - y) * w + x] = (pm.getPixel(x0 + x, y) & 0xff) > 0;
        return new PixelMask(w, h, s);
    }

    /** Is pixel (x, y) solid (y counted from the bottom)? Outside the mask is not. */
    public boolean at(int x, int y) { return x >= 0 && y >= 0 && x < w && y < h && solid[y * w + x]; }

    /** The same mask mirrored left to right (for sprites drawn flipped horizontally). */
    public PixelMask flippedH() {
        boolean[] s = new boolean[w * h];
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) s[y * w + (w - 1 - x)] = solid[y * w + x];
        return new PixelMask(w, h, s);
    }

    /** The same mask upside down (for sprites drawn flipped vertically). */
    public PixelMask flippedV() {
        boolean[] s = new boolean[w * h];
        for (int y = 0; y < h; y++) System.arraycopy(solid, y * w, s, (h - 1 - y) * w, w);
        return new PixelMask(w, h, s);
    }

    /** The same mask turned a quarter counter-clockwise (what faced right now faces up), as SpriteBatch rotation 90. */
    public PixelMask rotatedCCW() {
        boolean[] s = new boolean[w * h];
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) s[x * h + (h - 1 - y)] = solid[y * w + x];   // (x, y) -> (h-1-y, x)
        return new PixelMask(h, w, s);
    }

    /** The same mask turned a quarter clockwise (what faced right now faces down), as SpriteBatch rotation -90. */
    public PixelMask rotatedCW() {
        boolean[] s = new boolean[w * h];
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) s[(w - 1 - x) * h + y] = solid[y * w + x];   // (x, y) -> (y, w-1-x)
        return new PixelMask(h, w, s);
    }

    /** Does the rectangle (rx, ry, rw, rh) touch a solid pixel of the mask drawn with its bottom-left at (mx, my)? */
    public boolean overlaps(float mx, float my, float rx, float ry, float rw, float rh) {
        int x0 = Math.max(0, (int) Math.floor(rx - mx)), x1 = Math.min(w - 1, (int) Math.ceil(rx + rw - mx) - 1);
        int y0 = Math.max(0, (int) Math.floor(ry - my)), y1 = Math.min(h - 1, (int) Math.ceil(ry + rh - my) - 1);
        for (int y = y0; y <= y1; y++)
            for (int x = x0; x <= x1; x++)
                if (solid[y * w + x]) return true;
        return false;
    }
}
