package com.example.salamander;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;

import java.util.ArrayList;

/**
 * Kierke's machine wall (stage 5, moved out of GameScreen): the wall picture and its parts, hits and drawing.
 */
final class KierkeBoss {
    private static final int W = SalamanderGame.W, H = SalamanderGame.H;
    private final GameScreen g;

    KierkeBoss(GameScreen g) { this.g = g; }

    // ---- Kierke's machine wall (stage 5; see Boss.kierke), from Albert's pictures (2026-10-28):
    // sprites/boss4_main.png (the main body with the giant core, drawn K_MAIN_X px right of the boss's x, behind the
    // wall), sprites/boss4_wall_without_shield.png (the wall, 89x320, wrapping), and its parts: boss4_wall_bar.png (11
    // per unit, at x 1 + 4 i, 7x16, overlapping), boss4_wall_bar_core.png (the middle unit's 2 core bars, x 49 and 57),
    // boss4_wall_main_orb.png (the middle orb, x 65) and boss4_wall_shield.png (x 0, over the middle unit's bars). The
    // wall's own pixels under the parts are taken out (kWallBase); the parts still standing are drawn back onto it
    // (kWallPm) whenever one breaks, and its hit mask is rebuilt. An outer orb is the ball already in the wall picture
    // (12 px round at x K_ORB_CX); destroyed, its pixels go. Black is see-through (and flown / shot through).
    static final int K_WALL_W = 89, K_MAIN_X = 89, K_BAR_X0 = 1, K_BAR_STEP = 4, K_BAR_W = 7, K_PART_H = 16;
    static final int[] K_CORE_BAR_X = {49, 57};
    static final int K_CORE_BAR_W = 8, K_MAIN_ORB_X = 65, K_MAIN_ORB_W = 17, K_SHIELD_W = 49;
    static final float K_ORB_CX = 56.5f, K_ORB_R = 6.5f;
    /** The giant core in boss4_main.png: x, y (down), w, h. */
    static final int K_CORE_X = 10, K_CORE_Y = 82, K_CORE_W = 24, K_CORE_H = 14;
    Pixmap kWallBase, kWallPm, kBarPm, kCoreBarPm, kMainOrbPm, kShieldPm;
    Texture kWallTex, kMainTex;
    TextureRegion kWallR, kMainR;
    PixelMask kWallMask, kMainMask;
    /** Scratch box (x, y, w, h). */
    final float[] kBx = new float[4];

    /** See-through: transparent, black, or the near-black (6,6,6) of the boss pictures. */
    static boolean kBlack(int rgba) {
        return (rgba & 0xff) == 0 || ((rgba >>> 24) <= 6 && ((rgba >>> 16) & 0xff) <= 6 && ((rgba >>> 8) & 0xff) <= 6);
    }

    static Pixmap kLoad(String name) {
        Pixmap src = new Pixmap(Gdx.files.internal("sprites/" + name + ".png"));
        Pixmap pm = new Pixmap(src.getWidth(), src.getHeight(), Pixmap.Format.RGBA8888);
        pm.setBlending(Pixmap.Blending.None);
        for (int y = 0; y < src.getHeight(); y++)
            for (int x = 0; x < src.getWidth(); x++) { int c = src.getPixel(x, y); pm.drawPixel(x, y, kBlack(c) ? 0 : c); }
        src.dispose();
        return pm;
    }

    /** Clears the wall base where the part picture has pixels, at (px, py). */
    void kCut(Pixmap part, int px, int py) {
        for (int y = 0; y < part.getHeight(); y++)
            for (int x = 0; x < part.getWidth(); x++) if ((part.getPixel(x, y) & 0xff) != 0) kWallBase.drawPixel(px + x, py + y, 0);
    }

    /** Draws the part picture's pixels onto the wall at (px, py). */
    void kPaste(Pixmap part, int px, int py) {
        for (int y = 0; y < part.getHeight(); y++)
            for (int x = 0; x < part.getWidth(); x++) { int c = part.getPixel(x, y); if ((c & 0xff) != 0) kWallPm.drawPixel(px + x, py + y, c); }
    }

    void loadKierkeBoss() {
        if (g.levelIdx != 4) return;
        for (String n : new String[]{"boss4_main", "boss4_wall_without_shield", "boss4_wall_bar", "boss4_wall_bar_core",
                "boss4_wall_main_orb", "boss4_wall_shield"})
            if (!Gdx.files.internal("sprites/" + n + ".png").exists()) return;
        kBarPm = kLoad("boss4_wall_bar"); kCoreBarPm = kLoad("boss4_wall_bar_core");
        kMainOrbPm = kLoad("boss4_wall_main_orb"); kShieldPm = kLoad("boss4_wall_shield");
        kResetWall();
        kWallPm = new Pixmap(K_WALL_W, Boss.K_STACK_H, Pixmap.Format.RGBA8888);
        kWallPm.setBlending(Pixmap.Blending.None);
        kWallTex = new Texture(kWallPm);
        kWallR = new TextureRegion(kWallTex);
        Pixmap main = kLoad("boss4_main");
        kMainTex = new Texture(main);
        kMainR = new TextureRegion(kMainTex);
        kMainMask = PixelMask.fromPixmap(main, 0, main.getWidth());
        main.dispose();
    }

    /** A fresh wall base (every boss fight starts with a whole wall): the picture with its parts taken out. */
    void kResetWall() {
        if (kWallBase != null) kWallBase.dispose();
        kWallBase = kLoad("boss4_wall_without_shield");
        for (int k = 0; k < Boss.K_UNITS; k++)
            for (int i = 0; i < Boss.K_BARS; i++) kCut(kBarPm, K_BAR_X0 + K_BAR_STEP * i, Boss.kUnitRow(k));
        int mid = Boss.kUnitRow(Boss.K_MIDDLE);
        for (int x : K_CORE_BAR_X) kCut(kCoreBarPm, x, mid);
        kCut(kMainOrbPm, K_MAIN_ORB_X, mid);
    }

    /** Puts the wall together from its base and the parts still standing, and rebuilds its hit mask. */
    void kCompose() {
        kWallPm.drawPixmap(kWallBase, 0, 0);
        int mid = Boss.kUnitRow(Boss.K_MIDDLE);
        for (int k = 0; k < Boss.K_UNITS; k++)
            for (int i = Boss.K_BARS - 1; i >= 0; i--)
                if (g.boss.kBarHp[k][i] > 0f) kPaste(kBarPm, K_BAR_X0 + K_BAR_STEP * i, Boss.kUnitRow(k));
        for (int i = Boss.K_CORE_BARS - 1; i >= 0; i--) if (g.boss.kCoreBarHp[i] > 0f) kPaste(kCoreBarPm, K_CORE_BAR_X[i], mid);
        if (!g.boss.kWayOpen()) kPaste(kMainOrbPm, K_MAIN_ORB_X, mid);
        if (g.boss.kShield) kPaste(kShieldPm, 0, mid);
        kWallTex.draw(kWallPm, 0, 0);
        kWallMask = PixelMask.fromPixmap(kWallPm, 0, K_WALL_W);
    }

    boolean kierkeBossNow() { return g.boss != null && g.boss.kierke && kWallPm != null; }

    /** Does the rectangle touch the wall (either copy on screen) or the main body (their drawn pixels)? */
    boolean kierkeTouches(float x, float y, float w, float h) {
        if (kMainMask.overlaps(g.boss.x + K_MAIN_X, g.boss.y, x, y, w, h)) return true;
        for (int n = 0; n < 2; n++)
            if (kWallMask.overlaps(g.boss.kColX(), g.boss.kRowY(Boss.K_STACK_H, n), x, y, w, h)) return true;
        return false;
    }

    /** World box of wall columns x0..x1 and rows r0..r1 (copy n) into kBx. */
    void kBox(float x0, float x1, float r0, float r1, int n) {
        kBx[0] = g.boss.kColX() + x0; kBx[1] = g.boss.kRowY(r1, n); kBx[2] = x1 - x0; kBx[3] = r1 - r0;
    }

    /** A part was destroyed: a blast, points, and the wall without it. */
    void kBreak(Player owner, int pts, float scale) {
        g.boom(kBx[0] + kBx[2] / 2f, kBx[1] + kBx[3] / 2f, scale);
        g.a.play("boom", 0.3f + 0.1f * scale);
        g.addScore(owner, pts);
        kCompose();
    }

    /**
     * A player shot against the machine wall or its main body. In a unit's row it hits the front part still standing:
     * the shield (middle unit, can't be hurt), the bars (leftmost first), the middle unit's core bars, then the orb.
     * Returns true if it hit something (the boss stops every shot).
     */
    boolean kierkeBossShot(Bullet b, float hx, float hy, float hw, float hh, boolean laser) {
        if (!kierkeBossNow() || g.boss.dying || !kierkeTouches(hx, hy, hw, hh)) return false;
        if (laser) { if (b.hits.contains(g.boss)) return true; b.hits.add(g.boss); } else { b.dead = true; g.missileBurst(b); }
        Player owner = b.owner;
        float mx = g.boss.x + K_MAIN_X;
        if (kMainMask.overlaps(mx, g.boss.y, hx, hy, hw, hh)) {   // the main body: only its giant core, once the way is open
            float cx = mx + K_CORE_X, cy = g.boss.y + g.boss.h - K_CORE_Y - K_CORE_H;
            boolean onCore = g.overlap(hx, hy, hw, hh, cx, cy, K_CORE_W, K_CORE_H);
            if (!onCore || !g.boss.kWayOpen() || g.boss.hitCooldown > 0f) {
                g.a.play("hit", 0.15f);
                g.hitSpark(b, onCore ? cx : mx, onCore ? cy : g.boss.y, onCore ? K_CORE_W : kMainR.getRegionWidth(), onCore ? K_CORE_H : g.boss.h);
                return true;
            }
            g.boss.hp = Math.max(0f, g.boss.hp - b.dmg);
            g.boss.hitCooldown = Boss.HIT_COOLDOWN;
            g.boss.flash = 0.08f;
            g.a.play("hit", 0.2f);
            if (g.boss.hp > 0f) { g.hitSpark(b, cx, cy, K_CORE_W, K_CORE_H); return true; }
            g.boss.dying = true;
            g.eBullets.clear();
            g.addScore(owner, 5000);
            return true;
        }
        float right = hx + hw - g.boss.kColX();                  // how far into the wall the shot reaches
        for (int n = 0; n < 2; n++)
            for (int k = 0; k < Boss.K_UNITS; k++) {
                int row = Boss.kUnitRow(k);
                kBox(0, K_WALL_W, row, row + K_PART_H, n);
                if (!g.overlap(hx, hy, hw, hh, kBx[0], kBx[1], kBx[2], kBx[3])) continue;
                boolean mid = k == Boss.K_MIDDLE;
                if (mid && g.boss.kShield) {                      // the shield: can't be hurt
                    kBox(0, K_SHIELD_W, row, row + K_PART_H, n);
                    g.a.play("hit", 0.15f); g.hitSpark(b, kBx[0], kBx[1], kBx[2], kBx[3]);
                    return true;
                }
                int bar = g.boss.kFrontBar(k);
                if (bar >= 0) {
                    float bx = K_BAR_X0 + K_BAR_STEP * bar;
                    kBox(bx, bx + K_BAR_W, row, row + K_PART_H, n);
                    if (right < bx) break;                      // (short of it: the wall around it took the shot)
                    g.boss.kBarHp[k][bar] -= b.dmg;
                    if (g.boss.kBarHp[k][bar] > 0f) { g.a.play("hit", 0.15f); g.hitSpark(b, kBx[0], kBx[1], kBx[2], kBx[3]); }
                    else kBreak(owner, g.barBosses.BAR_SCORE, 0.6f);
                    return true;
                }
                if (mid) {
                    int cb = g.boss.kFrontCoreBar();
                    if (cb >= 0) {
                        float bx = K_CORE_BAR_X[cb];
                        kBox(bx, bx + K_CORE_BAR_W, row, row + K_PART_H, n);
                        if (right < bx) break;
                        g.boss.kCoreBarHp[cb] -= b.dmg;
                        if (g.boss.kCoreBarHp[cb] > 0f) { g.a.play("hit", 0.15f); g.hitSpark(b, kBx[0], kBx[1], kBx[2], kBx[3]); }
                        else kBreak(owner, g.barBosses.BAR_SCORE, 0.7f);
                        return true;
                    }
                }
                if (g.boss.kOrbHp[k] > 0f) {
                    float ox = mid ? K_MAIN_ORB_X : K_ORB_CX - K_ORB_R, ow = mid ? K_MAIN_ORB_W : 2f * K_ORB_R;
                    kBox(ox, ox + ow, row, row + K_PART_H, n);
                    if (right < ox) break;
                    g.boss.kOrbHp[k] -= b.dmg;
                    if (g.boss.kOrbHp[k] > 0f) { g.a.play("hit", 0.15f); g.hitSpark(b, kBx[0], kBx[1], kBx[2], kBx[3]); return true; }
                    if (!mid) {                                 // an outer orb: its pixels go from the wall picture
                        float cy = row + K_PART_H / 2f;
                        for (int y = (int) (cy - 7f); y < cy + 7f; y++)
                            for (int x = (int) (K_ORB_CX - 7f); x < K_ORB_CX + 7f; x++)
                                if ((x + 0.5f - K_ORB_CX) * (x + 0.5f - K_ORB_CX) + (y + 0.5f - cy) * (y + 0.5f - cy) <= K_ORB_R * K_ORB_R)
                                    kWallBase.drawPixel(x, y, 0);
                    }
                    kBreak(owner, g.barBosses.BAR_ORB_SCORE, 1.2f);
                    if (!mid && g.boss.kShield && g.boss.kOuterOrbsGone()) {   // the four outer orbs gone: the shield goes
                        g.boss.kShield = false;
                        kBox(0, K_SHIELD_W, Boss.kUnitRow(Boss.K_MIDDLE), Boss.kUnitRow(Boss.K_MIDDLE) + K_PART_H, 0);
                        kBreak(null, 0, 1.6f);
                    }
                    return true;
                }
                break;
            }
        g.a.play("hit", 0.15f);                                // the wall's body: it just stops the shot
        g.hitSpark(b, g.boss.kColX(), g.boss.kRowY(Boss.K_STACK_H, 0), K_WALL_W, Boss.K_STACK_H);
        return true;
    }

    void drawKierkeBoss() {
        if (g.boss.flash > 0f) g.batch.setColor(1f, 0.55f, 0.55f, 1f);
        if (g.boss.dying && (int) (g.boss.dieT * 20f) % 2 == 0) g.batch.setColor(1f, 1f, 1f, 0.5f);
        g.batch.draw(kMainR, g.boss.x + K_MAIN_X, g.boss.y);       // (behind the wall)
        int off = Math.round(g.boss.kOffMod()) % Boss.K_STACK_H;
        int h0 = Math.min(H, Boss.K_STACK_H - off);          // the wall from row off down, then from its top again
        kWallR.setRegion(0, off, K_WALL_W, h0);
        g.batch.draw(kWallR, g.boss.kColX(), g.boss.kTop - h0, K_WALL_W, h0);
        if (h0 < H) {
            kWallR.setRegion(0, 0, K_WALL_W, H - h0);
            g.batch.draw(kWallR, g.boss.kColX(), g.boss.kTop - H, K_WALL_W, H - h0);
        }
        g.batch.setColor(Color.WHITE);
    }
}
