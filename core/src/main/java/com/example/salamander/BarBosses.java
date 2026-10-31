package com.example.salamander;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;

import java.util.ArrayList;

/**
 * The bar-wall bosses (moved out of GameScreen): Eioneus (stage 4) and Op 4's gun units (stage 6).
 */
final class BarBosses {
    private static final int W = SalamanderGame.W, H = SalamanderGame.H;
    private final GameScreen g;

    BarBosses(GameScreen g) { this.g = g; }

    // ---- bar-wall bosses (Albert, 2026-10-27): Eioneus (stage 4, sprites/boss3.png) and Op 4's second boss
    // (stage 6, sprites/boss5b.png). Columns of bar walls (sprites/boss_bar.png, painted into the boss picture), each
    // guarding a coloured orb above it (the ships shoot up at it from below). A shot into a column hurts its lowest bar
    // left (BAR_HP each), and once the bars are gone, the orb (BAR_ORB_HP). The middle column is closed off by the core
    // shield (sprites/boss3_core_shield.png, also painted into the picture) until every other orb is destroyed: then
    // the shield is blown away. Destroying the middle orb beats the boss. The rest of the body only stops shots.
    // Positions are top-left in the boss picture (x right, y down), found by matching the pictures.
    /** One bar-wall boss: its picture, the boss part it is (-1 = the stage's only boss), bars, orbs, middle column, shield. */
    static final class BarBoss {
        final int level, part;
        final String file;
        /** Per column: its bars, lowest (first in line) first. */
        final int[][][] bars;
        /** Per column: its orb (top-left of a 10x10 box). */
        final int[][] orbs;
        final int mid, shieldX, shieldY;
        BarBoss(int level, int part, String file, int[][][] bars, int[][] orbs, int mid, int shieldX, int shieldY) {
            this.level = level; this.part = part; this.file = file; this.bars = bars; this.orbs = orbs;
            this.mid = mid; this.shieldX = shieldX; this.shieldY = shieldY;
        }
    }
    /** Five bars stacked 8 px apart, the lowest with its top at y. */
    static int[][] barStack(int x, int y) { return new int[][]{{x, y}, {x, y - 8}, {x, y - 16}, {x, y - 24}, {x, y - 32}}; }
    static final BarBoss[] BAR_BOSSES = {
            // Eioneus: blue, red (middle), grey
            new BarBoss(3, -1, "boss3", new int[][][]{barStack(55, 98), barStack(95, 82), barStack(135, 98)},
                    new int[][]{{56, 46}, {96, 30}, {136, 46}}, 1, 89, 89),
            // Op 4, the gun units (boss5b): blue, grey, red (middle), yellow, green
            new BarBoss(5, 1, "boss5b", new int[][][]{barStack(23, 105), barStack(63, 81), barStack(119, 137), barStack(175, 81), barStack(215, 105)},
                    new int[][]{{24, 53}, {64, 29}, {120, 85}, {176, 29}, {216, 53}}, 2, 113, 152),
    };
    static final int BAR_W = 12, BAR_H = 6, BAR_ORB = 10, BAR_PARTS = 6;   // per column: 5 bars, then the orb (index 5)
    static final float BAR_HP = 10f, BAR_ORB_HP = 30f;
    static final int BAR_SCORE = 100, BAR_ORB_SCORE = 1000;
    /** This stage's bar-wall boss (null: none). */
    BarBoss bb;
    /** The untouched picture, the one being shot to pieces (and its texture), the bar and shield pictures. */
    Pixmap barOrig, barPix, barPm, barShieldPm;
    Texture barTex;
    TextureRegion barR;
    /** Its exact shape, with the shot-away shield taken out. */
    PixelMask barMask;
    float[][] barHp;
    /** One object per part, so a laser hurts each part once. */
    Object[][] barKey;
    boolean barShield;

    /** Loads this stage's bar-wall boss picture as an editable copy, if the bar and shield pictures are there. */
    void loadBarBoss() {
        for (BarBoss c : BAR_BOSSES) if (c.level == g.levelIdx) bb = c;
        if (bb == null) return;
        String f = "sprites/" + bb.file + ".png";
        if (!Gdx.files.internal(f).exists() || !Gdx.files.internal("sprites/boss_bar.png").exists()
                || !Gdx.files.internal("sprites/boss3_core_shield.png").exists()) { bb = null; return; }
        barOrig = new Pixmap(Gdx.files.internal(f));
        barPix = new Pixmap(barOrig.getWidth(), barOrig.getHeight(), Pixmap.Format.RGBA8888);
        barPix.setBlending(Pixmap.Blending.None);
        barPix.drawPixmap(barOrig, 0, 0);
        barPm = new Pixmap(Gdx.files.internal("sprites/boss_bar.png"));
        barShieldPm = new Pixmap(Gdx.files.internal("sprites/boss3_core_shield.png"));
        barTex = new Texture(barPix);
        barR = new TextureRegion(barTex);
        int n = bb.bars.length;
        barHp = new float[n][BAR_PARTS];
        barKey = new Object[n][BAR_PARTS];
        for (int c = 0; c < n; c++) for (int k = 0; k < BAR_PARTS; k++) barKey[c][k] = new Object();
    }

    /** Is the boss being fought (or coming next) this stage's bar-wall boss? */
    boolean barBossNow() { return bb != null && (bb.part < 0 || g.bossPart == bb.part); }

    /** A new (or restarted) fight: every part back, full health; the health bar shows what is left of the parts. */
    void resetBarBoss() {
        if (!barBossNow() || g.boss == null) return;
        barPix.drawPixmap(barOrig, 0, 0);
        barTex.draw(barPix, 0, 0);
        barMask = PixelMask.load("sprites/" + bb.file + ".png");
        g.boss.mask = barMask;
        barShield = true;
        float total = 0f;
        for (int c = 0; c < barHp.length; c++)
            for (int k = 0; k < BAR_PARTS; k++) total += barHp[c][k] = k < BAR_PARTS - 1 ? BAR_HP : BAR_ORB_HP;
        g.boss.hp = g.boss.maxHp = total;
    }

    /** Column c's part still standing that is first in line (lowest bar, then the orb); -1 = nothing left. */
    int barTarget(int c) {
        for (int k = 0; k < BAR_PARTS; k++) if (barHp[c][k] > 0f) return k;
        return -1;
    }

    /** Part k of column c in the picture: x, y (top-left, y down), w, h into barBox. */
    final int[] barBox = new int[4];
    void barPartBox(int c, int k) {
        boolean orb = k == BAR_PARTS - 1;
        int[] p = orb ? bb.orbs[c] : bb.bars[c][k];
        barBox[0] = p[0]; barBox[1] = p[1];
        barBox[2] = orb ? BAR_ORB : BAR_W; barBox[3] = orb ? BAR_ORB : BAR_H;
    }

    /**
     * A ship's shot (on screen: hx, hy, hw, hh) against a bar-wall boss's columns: if it is in a column that still
     * has something standing, that part takes it. Returns true if it was dealt with here.
     */
    boolean barBossShot(Bullet b, float hx, float hy, float hw, float hh, boolean laser) {
        if (!barBossNow() || g.boss == null || g.boss.dying || g.boss.appearing) return false;
        for (int c = 0; c < barHp.length; c++) {
            if (c == bb.mid && barShield) continue;      // the middle column: behind the core shield
            int k = barTarget(c);
            if (k < 0) continue;
            // the column: from the top of its orb down to just below its lowest bar
            int[] low = bb.bars[c][0];
            float top = bb.orbs[c][1], bottom = low[1] + BAR_H + 3f;
            float cx = g.boss.x + low[0], cy = g.boss.y + g.boss.h - bottom;
            if (!g.overlap(hx, hy, hw, hh, cx, cy, BAR_W, bottom - top)) continue;
            Object key = barKey[c][k];
            if (laser) { if (b.hits.contains(key)) return true; b.hits.add(key); } else b.dead = true;
            barHp[c][k] -= b.dmg;
            g.boss.flash = 0.08f;
            barPartBox(c, k);
            float px = g.boss.x + barBox[0], py = g.boss.y + g.boss.h - barBox[1] - barBox[3];
            if (barHp[c][k] <= 0f) barPartDestroyed(c, k, b.owner, px, py);
            else { g.a.play("hit", 0.15f); g.hitSpark(b, px, py, barBox[2], barBox[3]); }
            float left = 0f;
            for (float[] col : barHp) for (float v : col) left += Math.max(0f, v);
            if (!g.boss.dying) g.boss.hp = left;
            if (b.dead && b.kind == Bullet.Kind.MISSILE) g.boom(b.x, b.y, 0.6f);
            return true;
        }
        return false;
    }

    /** Part k of column c is shot away (bottom-left on screen px, py): gone from the picture, points, and what follows. */
    void barPartDestroyed(int c, int k, Player owner, float px, float py) {
        barHp[c][k] = 0f;
        boolean orb = k == BAR_PARTS - 1;
        barPartBox(c, k);
        int black = 0x000000ff;                          // what is behind: the dark inside of the column
        for (int y = 0; y < barBox[3]; y++)
            for (int x = 0; x < barBox[2]; x++) {
                boolean part = orb ? (barOrig.getPixel(barBox[0] + x, barBox[1] + y) & 0xff) > 0
                        : (barPm.getPixel(x, y) & 0xff) > 0;
                if (part) barPix.drawPixel(barBox[0] + x, barBox[1] + y, black);
            }
        {   // the ships can fly up into a column as far as it is shot away (as on the MSX)
            int[] low = bb.bars[c][0];
            int ph2 = barPix.getHeight();
            for (int y = barBox[1]; y < ph2; y++)
                for (int x = low[0]; x < low[0] + BAR_W; x++) barMask.clear(x, ph2 - 1 - y);
        }
        g.boom(px + barBox[2] / 2f, py + barBox[3] / 2f, orb ? 1.2f : 0.5f);
        g.a.play("boom", orb ? 0.4f : 0.2f);
        if (bb.level != 3) g.addScore(owner, orb ? BAR_ORB_SCORE : BAR_SCORE);   // (Eioneus: no points for bars and orbs, as on the MSX)
        if (orb && c == bb.mid) {                        // the middle orb: the boss is beaten
            barTex.draw(barPix, 0, 0);
            g.boss.hp = 0f;
            g.boss.dying = true;
            g.boss.explodeArms(g);
            g.boss.explodeRings(g);
            g.eBullets.clear();
            g.addScore(owner, 5000);
            return;
        }
        boolean othersGone = true;
        for (int i = 0; i < barHp.length; i++) if (i != bb.mid && barHp[i][BAR_PARTS - 1] > 0f) othersGone = false;
        if (orb && barShield && othersGone) {           // every other orb gone
            barShield = false;                           // the core shield is blown away: the middle column is open
            int sh = barShieldPm.getHeight(), ph = barPix.getHeight();
            for (int y = 0; y < sh; y++)
                for (int x = 0; x < barShieldPm.getWidth(); x++) {
                    if ((barShieldPm.getPixel(x, y) & 0xff) == 0) continue;
                    barPix.drawPixel(bb.shieldX + x, bb.shieldY + y, 0);
                    barMask.clear(bb.shieldX + x, ph - 1 - (bb.shieldY + y));
                }
            // ...and the way up the middle column is clear: nothing left below its lowest bar
            int[] low = bb.bars[bb.mid][0];
            for (int y = low[1] + BAR_H; y < ph; y++)
                for (int x = low[0]; x < low[0] + BAR_W; x++) {
                    barPix.drawPixel(x, y, 0);
                    barMask.clear(x, ph - 1 - y);
                }
            float sx = g.boss.x + bb.shieldX + barShieldPm.getWidth() / 2f, sy = g.boss.y + g.boss.h - bb.shieldY - sh / 2f;
            g.boom(sx, sy + 8f, 1.6f);
            g.boom(sx, sy - 8f, 1.6f);
            g.a.play("boom", 0.5f);
        }
        barTex.draw(barPix, 0, 0);
    }
}
