package com.example.salamander;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;

import java.util.ArrayList;

/**
 * Stage 1's brain boss (moved out of GameScreen): the eye (sprites/boss eye.png) and the core with its blocks.
 * The Boss itself is Boss.java; the world stays in GameScreen (g).
 */
final class BrainBoss {
    private static final int W = SalamanderGame.W, H = SalamanderGame.H;
    private final GameScreen g;

    BrainBoss(GameScreen g) { this.g = g; }

    // ---- boss eye: "sprites/boss eye.png" holds the frames side by side (closed -> open), separated by
    // empty columns. The boss sprite marks the eye's centre with a small pure green (0,255,0) square.
    TextureRegion[] eyeF;
    float eyeOX, eyeOY;
    static final String EYE_FILE = "sprites/boss eye.png";

    void loadBossEye(String bossPath) {
        eyeF = null;
        if (!Gdx.files.internal(EYE_FILE).exists()) return;
        Pixmap pm = new Pixmap(Gdx.files.internal(bossPath));
        int gx0 = Integer.MAX_VALUE, gy0 = Integer.MAX_VALUE, gx1 = -1, gy1 = -1;
        for (int y = 0; y < pm.getHeight(); y++)
            for (int x = 0; x < pm.getWidth(); x++) {
                int c = pm.getPixel(x, y);
                int r = (c >>> 24) & 0xff, g = (c >>> 16) & 0xff, b = (c >>> 8) & 0xff, al = c & 0xff;
                if (al > 200 && g > 200 && r < 60 && b < 60) { gx0 = Math.min(gx0, x); gy0 = Math.min(gy0, y); gx1 = Math.max(gx1, x); gy1 = Math.max(gy1, y); }
            }
        int bossH = pm.getHeight();
        pm.dispose();
        if (gx1 < 0) return;                        // no green marker: this boss has no eye

        Pixmap ep = new Pixmap(Gdx.files.internal(EYE_FILE));
        ArrayList<int[]> runs = new ArrayList<>();   // {x, width} of each frame
        int start = -1;
        for (int x = 0; x <= ep.getWidth(); x++) {
            boolean empty = true;
            if (x < ep.getWidth()) for (int y = 0; y < ep.getHeight() && empty; y++) if ((ep.getPixel(x, y) & 0xff) != 0) empty = false;
            if (!empty && start < 0) start = x;
            if (empty && start >= 0) { runs.add(new int[]{start, x - start}); start = -1; }
        }
        int eh = ep.getHeight();
        ep.dispose();
        if (runs.isEmpty()) return;
        Texture et = g.a.texFile(EYE_FILE);
        eyeF = new TextureRegion[runs.size()];
        for (int i = 0; i < eyeF.length; i++) eyeF[i] = new TextureRegion(et, runs.get(i)[0], 0, runs.get(i)[1], eh);
        // centre the eye on the marker (sprite rows run top-down, world y up)
        float cx = (gx0 + gx1 + 1) / 2f, cy = (gy0 + gy1 + 1) / 2f;
        eyeOX = Math.round(cx - eyeF[0].getRegionWidth() / 2f);
        eyeOY = bossH - Math.round(cy + eh / 2f);
    }

    /** sprites/bossN_core.png (null = this boss has no separate core), and where it sits on the body. */
    TextureRegion coreR;
    PixelMask coreMask;
    int coreOX, coreOY;

    /**
     * Loads the core picture and finds where it belongs: over the hole cut out of the body for it (the spot where the
     * most core pixels fall on see-through body pixels and the fewest on drawn ones).
     */
    void loadBossCore() {
        String file = "sprites/boss" + g.levelIdx + "_core.png";
        if (!Gdx.files.internal(file).exists()) return;
        coreR = g.a.one("boss" + g.levelIdx + "_core");
        coreMask = PixelMask.load(file);
        loadCoreBlocks();
        int best = Integer.MIN_VALUE;
        for (int oy = 0; oy <= g.bossMask.h - coreMask.h; oy++)
            for (int ox = 0; ox <= g.bossMask.w - coreMask.w; ox++) {
                int score = 0;
                for (int y = 0; y < coreMask.h; y++)
                    for (int x = 0; x < coreMask.w; x++)
                        if (coreMask.at(x, y)) score += g.bossMask.at(ox + x, oy + y) ? -1 : 1;
                if (score > best) { best = score; coreOX = ox; coreOY = oy; }
            }
    }

    /**
     * Tetran's core blocks (sprites/core_block.png): CORE_BLOCKS bars below the red core, drawn over the core picture.
     * Every time the boss loses 1/(CORE_BLOCKS+1) of its health (20%), the lowest block left disappears, showing the
     * dark inside of the core picture.
     */
    TextureRegion blockR;
    static final int CORE_BLOCKS = 4;
    /** Top row of each block in the core picture (counted from its top), top block first; x = its left column. */
    static final int[] CORE_BLOCK_ROWS = {26, 30, 34, 38};
    static final int CORE_BLOCK_X = 8, CORE_BLOCK_SPAN = 8;
    int coreBlocksShown = CORE_BLOCKS;
    /** Where a block's picture goes, so its drawn part sits in the middle of the bar's span. */
    int blockOffX;

    void loadCoreBlocks() {
        if (!Gdx.files.internal("sprites/core_block.png").exists()) return;
        blockR = g.a.one("core_block");
        PixelMask bm = PixelMask.load("sprites/core_block.png");   // centre the drawn part of the block
        int x0 = bm.w, x1 = -1;
        for (int x = 0; x < bm.w; x++) for (int y = 0; y < bm.h; y++) if (bm.at(x, y)) { x0 = Math.min(x0, x); x1 = Math.max(x1, x); }
        blockOffX = x1 < 0 ? 0 : Math.round((CORE_BLOCK_SPAN - (x1 - x0 + 1)) / 2f) - x0;
    }

    /** Draws the core blocks still left, at their spots in the core (each block's picture centred on its bar). */
    void drawCoreBlocks() {
        if (blockR == null) return;
        int ch = coreR.getRegionHeight();
        float bw = blockR.getRegionWidth(), bh = blockR.getRegionHeight();
        for (int i = 0; i < coreBlocksShown; i++) {         // the lowest ones go first
            int top = CORE_BLOCK_ROWS[i];
            float x = g.boss.x + g.boss.coreX + CORE_BLOCK_X + blockOffX;
            float y = g.boss.y + g.boss.coreY + ch - top - bh;
            g.batch.draw(blockR, x, y, bw, bh);
        }
    }

    /** Takes a block away for every 20% of health the boss has lost (with a small burst where it was). */
    void updateCoreBlocks() {
        if (blockR == null || g.boss == null || !g.boss.hasCore) return;
        int want = Math.max(0, Math.min(CORE_BLOCKS, (int) Math.ceil(g.boss.hp / g.boss.maxHp * (CORE_BLOCKS + 1)) - 1));
        while (coreBlocksShown > want) {
            coreBlocksShown--;
            int top = CORE_BLOCK_ROWS[coreBlocksShown];
            g.boom(g.boss.x + g.boss.coreX + CORE_BLOCK_X + CORE_BLOCK_SPAN / 2f, g.boss.y + g.boss.coreY + coreR.getRegionHeight() - top - 1f, 0.5f);
            g.a.play("hit", 0.3f);
        }
    }
}
