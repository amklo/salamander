package com.example.salamander;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;

/**
 * The pixel-by-pixel break-up seen on the MSX (Op 4: the terrain at the end of the shaft, the brain sphere and the gun
 * units when they are beaten, and the gun units appearing): a picture loses its drawn pixels one by one, in a random
 * order, until nothing is left (or, made with appear(), gains them one by one until it is whole). Stage 3's dissolving
 * end-room walls work the same way.
 *
 * Usage: Dissolver d = Dissolver.of(pixmap) (or of(file, x, y, w, h)); each frame d.set(progress 0..1) and
 * d.draw(batch, x, y); d.dispose() when it is no longer needed. GameScreen.dissolveDeath(...) does all of that for a
 * picture that just stays where it is (the death animation of an enemy or boss).
 */
public final class Dissolver {
    private final Pixmap src, pix;
    private final Texture tex;
    private final TextureRegion region;
    private final int[] order;
    private final boolean appear;
    private int done;

    private Dissolver(Pixmap source, boolean appear) {
        this.appear = appear;
        int w = source.getWidth(), h = source.getHeight();
        src = new Pixmap(w, h, Pixmap.Format.RGBA8888);
        src.setBlending(Pixmap.Blending.None);
        src.drawPixmap(source, 0, 0);
        pix = new Pixmap(w, h, Pixmap.Format.RGBA8888);
        pix.setBlending(Pixmap.Blending.None);
        if (!appear) pix.drawPixmap(src, 0, 0);       // (appearing: starts empty)
        int[] o = new int[w * h];
        int k = 0;
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) if ((src.getPixel(x, y) & 0xff) != 0) o[k++] = y * w + x;
        order = java.util.Arrays.copyOf(o, k);
        for (int j = k - 1; j > 0; j--) { int r = MathUtils.random(j); int t = order[j]; order[j] = order[r]; order[r] = t; }
        tex = new Texture(pix);
        region = new TextureRegion(tex);
    }

    /** A dissolving copy of the picture (the picture itself is left alone). */
    public static Dissolver of(Pixmap picture) { return new Dissolver(picture, false); }

    /** A dissolving copy of the part (x, y, w, h; top-left, y down) of a picture file (e.g. one frame of a sprite strip). */
    public static Dissolver of(String file, int x, int y, int w, int h) {
        Pixmap all = new Pixmap(Gdx.files.internal(file));
        Pixmap part = new Pixmap(w, h, Pixmap.Format.RGBA8888);
        part.setBlending(Pixmap.Blending.None);
        part.drawPixmap(all, 0, 0, x, y, w, h);
        all.dispose();
        Dissolver d = new Dissolver(part, false);
        part.dispose();
        return d;
    }

    /** The picture appearing out of nothing, pixel by pixel (the break-up in reverse). */
    public static Dissolver appear(Pixmap picture) { return new Dissolver(picture, true); }

    /** Progress 0..1: how much of the picture is gone (appearing: how much is there). Only goes forward. */
    public void set(float f) {
        int want = Math.min(order.length, Math.round(order.length * MathUtils.clamp(f, 0f, 1f)));
        if (want <= done) return;
        int w = pix.getWidth();
        while (done < want) {
            int q = order[done++], x = q % w, y = q / w;
            pix.drawPixel(x, y, appear ? src.getPixel(x, y) : 0);
        }
        tex.draw(pix, 0, 0);
    }

    /** Has it finished (all gone, or all there)? */
    public boolean finished() { return done >= order.length; }

    public int width() { return pix.getWidth(); }
    public int height() { return pix.getHeight(); }

    /** Draws it with its bottom-left at (x, y). */
    public void draw(SpriteBatch batch, float x, float y) {
        if (!appear && finished()) return;
        batch.draw(region, x, y);
    }

    /** Draws it with its bottom-left at (x, y), w x h (negative w: mirrored). */
    public void draw(SpriteBatch batch, float x, float y, float w, float h) {
        if (!appear && finished()) return;
        batch.draw(region, x, y, w, h);
    }

    public void dispose() {
        src.dispose();
        pix.dispose();
        tex.dispose();
    }
}
