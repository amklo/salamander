package com.example.salamander;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Pixmap;

/** Which pixels of a sprite are solid, for exact collisions with oddly shaped things (teeth, the brain boss). */
public final class PixelMask {
    public final int w, h;
    private final boolean[] solid;   // row 0 = bottom

    public PixelMask(int w, int h, boolean[] solid) { this.w = w; this.h = h; this.solid = solid; }

    /** Builds the mask from a PNG's alpha channel (drawn 1:1, so mask pixels = world pixels). */
    public static PixelMask load(String internalPath) {
        Pixmap pm = new Pixmap(Gdx.files.internal(internalPath));
        int w = pm.getWidth(), h = pm.getHeight();
        boolean[] s = new boolean[w * h];
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++)
                s[(h - 1 - y) * w + x] = (pm.getPixel(x, y) & 0xff) > 0;
        pm.dispose();
        return new PixelMask(w, h, s);
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
