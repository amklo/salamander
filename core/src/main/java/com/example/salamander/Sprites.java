package com.example.salamander;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;

import java.util.ArrayList;

/**
 * Loading sprite pictures into frames: cutting strips into frames and making dark backgrounds see-through.
 * (Moved out of GameScreen; no game state here.)
 */
final class Sprites {
    private Sprites() {}

    /** A strip of square frames side by side (each as tall as the picture); null for null. */
    static TextureRegion[] squareFrames(TextureRegion r) {
        if (r == null) return null;
        int fh = r.getRegionHeight(), n = Math.max(1, r.getRegionWidth() / fh);
        TextureRegion[] f = new TextureRegion[n];
        for (int i = 0; i < n; i++) f[i] = new TextureRegion(r, i * fh, 0, fh, fh);
        return f;
    }

    /** A picture whose near-black pixels (its background box) are made see-through; null if the file is missing. */
    static TextureRegion keyedDark(String file) {
        if (!Gdx.files.internal(file).exists()) return null;
        com.badlogic.gdx.graphics.Pixmap src = new com.badlogic.gdx.graphics.Pixmap(Gdx.files.internal(file));
        com.badlogic.gdx.graphics.Pixmap pm = new com.badlogic.gdx.graphics.Pixmap(src.getWidth(), src.getHeight(), com.badlogic.gdx.graphics.Pixmap.Format.RGBA8888);
        pm.setBlending(com.badlogic.gdx.graphics.Pixmap.Blending.None);
        for (int yy = 0; yy < src.getHeight(); yy++) for (int xx = 0; xx < src.getWidth(); xx++) {
            int c = src.getPixel(xx, yy), r = c >>> 24 & 255, gg = c >>> 16 & 255, bb = c >>> 8 & 255, al = c & 255;
            pm.drawPixel(xx, yy, al == 0 || (r < 16 && gg < 16 && bb < 16) ? 0 : c);
        }
        TextureRegion t = new TextureRegion(new Texture(pm));
        pm.dispose(); src.dispose();
        return t;
    }

    /** A strip of frames (frameW wide) whose near-black pixels are made see-through; null if the file is missing. */
    static TextureRegion[] keyedFrames(String file, int frameW) {
        TextureRegion all = keyedDark(file);
        if (all == null) return null;
        int n = Math.max(1, all.getRegionWidth() / frameW);
        TextureRegion[] f = new TextureRegion[n];
        for (int i = 0; i < n; i++) f[i] = new TextureRegion(all, i * frameW, 0, frameW, all.getRegionHeight());
        return f;
    }

    /**
     * Frames of a fixed width from a picture drawn on solid black: the black around each figure (connected to the
     * frame's edge) becomes see-through, black enclosed by the figure stays. A picture that already has
     * transparency is used as it is.
     */
    static TextureRegion[] framesOuterBlackKeyed(String file, int frameW) {
        Pixmap pm = new Pixmap(Gdx.files.internal(file));
        if (pm.getFormat() != Pixmap.Format.RGBA8888) {
            Pixmap c = new Pixmap(pm.getWidth(), pm.getHeight(), Pixmap.Format.RGBA8888);
            c.setBlending(Pixmap.Blending.None);
            c.drawPixmap(pm, 0, 0);
            pm.dispose();
            pm = c;
        }
        pm.setBlending(Pixmap.Blending.None);
        int W = pm.getWidth(), H = pm.getHeight();
        boolean[] seen = new boolean[W * H];
        java.util.ArrayDeque<int[]> q = new java.util.ArrayDeque<>();
        for (int f = 0; f * frameW < W; f++) {
            int x0 = f * frameW, x1 = Math.min(W, x0 + frameW) - 1;
            for (int x = x0; x <= x1; x++) { q.add(new int[]{x, 0, x0, x1}); q.add(new int[]{x, H - 1, x0, x1}); }
            for (int y = 0; y < H; y++) { q.add(new int[]{x0, y, x0, x1}); q.add(new int[]{x1, y, x0, x1}); }
        }
        while (!q.isEmpty()) {                                  // flood the black from each frame's border
            int[] c = q.poll();
            int x = c[0], y = c[1];
            if (x < c[2] || x > c[3] || y < 0 || y >= H || seen[y * W + x]) continue;
            int px = pm.getPixel(x, y);
            boolean blackOrClear = (px & 0xff) == 0 || (px >>> 8) == 0;
            if (!blackOrClear) continue;
            seen[y * W + x] = true;
            pm.drawPixel(x, y, 0);
            q.add(new int[]{x + 1, y, c[2], c[3]}); q.add(new int[]{x - 1, y, c[2], c[3]});
            q.add(new int[]{x, y + 1, c[2], c[3]}); q.add(new int[]{x, y - 1, c[2], c[3]});
        }
        Texture tex = new Texture(pm);
        tex.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        pm.dispose();
        int n = Math.max(1, W / frameW);
        TextureRegion[] fr = new TextureRegion[n];
        for (int i = 0; i < n; i++) fr[i] = new TextureRegion(tex, i * frameW, 0, frameW, H);
        return fr;
    }

    /**
     * A single picture drawn on a solid dark background (like sprites/player_blue_vertical.png): the background colour
     * (the top-left pixel) connected to the edges becomes see-through. A picture with transparency is used as it is.
     */
    static TextureRegion keyedBackground(String file) { return keyedBackground(file, null); }

    /** keyedBackground, and maskOut[0] = the exact shape of the result (when maskOut is given). */
    static TextureRegion keyedBackground(String file, PixelMask[] maskOut) {
        Pixmap pm = new Pixmap(Gdx.files.internal(file));
        if (pm.getFormat() != Pixmap.Format.RGBA8888) {
            Pixmap c = new Pixmap(pm.getWidth(), pm.getHeight(), Pixmap.Format.RGBA8888);
            c.setBlending(Pixmap.Blending.None);
            c.drawPixmap(pm, 0, 0);
            pm.dispose();
            pm = c;
        }
        pm.setBlending(Pixmap.Blending.None);
        int W = pm.getWidth(), H = pm.getHeight(), bg = pm.getPixel(0, 0);
        boolean dark = (bg & 0xff) == 0xff && ((bg >>> 24) & 0xff) < 24 && ((bg >>> 16) & 0xff) < 24 && ((bg >>> 8) & 0xff) < 24;
        if (dark) {
            boolean[] seen = new boolean[W * H];
            java.util.ArrayDeque<int[]> q = new java.util.ArrayDeque<>();
            for (int x = 0; x < W; x++) { q.add(new int[]{x, 0}); q.add(new int[]{x, H - 1}); }
            for (int y = 0; y < H; y++) { q.add(new int[]{0, y}); q.add(new int[]{W - 1, y}); }
            while (!q.isEmpty()) {
                int[] c = q.poll();
                int x = c[0], y = c[1];
                if (x < 0 || x >= W || y < 0 || y >= H || seen[y * W + x] || pm.getPixel(x, y) != bg) continue;
                seen[y * W + x] = true;
                pm.drawPixel(x, y, 0);
                q.add(new int[]{x + 1, y}); q.add(new int[]{x - 1, y}); q.add(new int[]{x, y + 1}); q.add(new int[]{x, y - 1});
            }
        }
        if (maskOut != null) maskOut[0] = PixelMask.fromPixmap(pm, 0, W);
        Texture tex = new Texture(pm);
        tex.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        pm.dispose();
        return new TextureRegion(tex);
    }

    /**
     * The vertical ship's frames: three of equal width side by side (left, level, right). A picture no wider than it
     * is tall is a single frame, used for all three.
     */
    static TextureRegion[] verticalShipFrames(TextureRegion sheet) {
        int w = sheet.getRegionWidth(), h = sheet.getRegionHeight();
        if (w <= h) return new TextureRegion[]{sheet, sheet, sheet};
        int fw = w / 3;
        TextureRegion[] f = new TextureRegion[3];
        for (int i = 0; i < 3; i++) f[i] = new TextureRegion(sheet, i * fw, 0, fw, h);
        return f;
    }

    /** Frames side by side in one picture, separated by empty columns; each frame is trimmed to its own height. */
    static TextureRegion[] scanFrames(Assets a, String file) {
        Pixmap pm = new Pixmap(Gdx.files.internal(file));
        ArrayList<int[]> frames = new ArrayList<>();          // {x, y, w, h}
        int start = -1;
        for (int x = 0; x <= pm.getWidth(); x++) {
            boolean empty = true;
            if (x < pm.getWidth()) for (int y = 0; y < pm.getHeight() && empty; y++) if ((pm.getPixel(x, y) & 0xff) != 0) empty = false;
            if (!empty && start < 0) start = x;
            if (empty && start >= 0) {
                int top = pm.getHeight(), bottom = -1;
                for (int y = 0; y < pm.getHeight(); y++)
                    for (int xx = start; xx < x; xx++)
                        if ((pm.getPixel(xx, y) & 0xff) != 0) { top = Math.min(top, y); bottom = Math.max(bottom, y); }
                frames.add(new int[]{start, top, x - start, bottom - top + 1});
                start = -1;
            }
        }
        pm.dispose();
        Texture tex = a.texFile(file);
        TextureRegion[] f = new TextureRegion[frames.size()];
        for (int i = 0; i < f.length; i++) {
            int[] r = frames.get(i);
            f[i] = new TextureRegion(tex, r[0], r[1], r[2], r[3]);
        }
        return f;
    }
}
