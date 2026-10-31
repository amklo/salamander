package com.example.salamander;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;

import java.util.ArrayList;

/**
 * Eioneus's boss room (stage 4, moved out of GameScreen): the gun pods coming down, their bars, the bubbles.
 */
final class EioneusRoom {
    private static final int W = SalamanderGame.W, H = SalamanderGame.H;
    private final GameScreen g;

    EioneusRoom(GameScreen g) { this.g = g; }

    // =============================================================== Eioneus's boss room (stage 4)
    // From the MSX recording 216-266 s (Albert, 2026-10-27). The scrolling stops at the top of the cave; POD_WAIT s
    // later the three gun pods (map objects "gunpod", gunpod.png) come down out of the ceiling, POD_STEP_PX px every
    // POD_STEP s, until they hang in their places. Each has a column of POD_BARS bars (boss_bar.png, painted into
    // gunpod.png) in front of its red orb: a shot into the column hurts its lowest bar left (POD_BAR_HP each), and once
    // its bars are gone the pod is beaten (POD_SCORE points, the moment the bars are gone, as on the MSX; the orb
    // stays). The rest of a pod stops shots and lasers and hurts the ships; the inside of a column is free once its
    // bars are gone (the ship flies up into it in the recording). While the pods hang there, they let out bouncing
    // bubbles (Enemy.bubble): the first BUBBLE_FIRST s after they are in place, then one every BUBBLE_EVERY s while
    // there are fewer than BUBBLE_MAX. PODS_DONE_WAIT s after the last pod is beaten the bubbles go and everything on
    // the screen breaks up pixel by pixel (EIO_TERRAIN_TIME s, leaving black space), and EIO_BOSS_WAIT s later the
    // boss comes down (Boss.initEioneus).
    static final float POD_WAIT = 0f,   // (Albert, 2026-10-30: they come as soon as the scrolling stops; was 6 s)
            POD_STEP = 0.4f, POD_STEP_PX = 8f, POD_BAR_HP = 10f,
            BUBBLE_FIRST = 1.5f, BUBBLE_EVERY = 0.8f, PODS_DONE_WAIT = 2f, EIO_TERRAIN_TIME = 2f, EIO_BOSS_WAIT = 4f;
    static final int POD_BARS = 4, POD_SCORE = 10000, BUBBLE_MAX = 4;
    /** The bars in gunpod.png (top-left, y down), lowest (first in line) first; the top of its orb. */
    static final int[][] POD_BAR_POS = {{16, 58}, {16, 50}, {16, 42}, {16, 34}};
    static final int POD_ORB_TOP = 12;
    static final int EIO_NONE = 0, EIO_WAIT = 1, EIO_DESCEND = 2, EIO_FIGHT = 3, EIO_AFTER = 4, EIO_TERRAIN = 5, EIO_BOSS = 6;
    int eioState;
    float eioT, eioStepT, bubbleT;
    /** How far above their places the pods still are (px). */
    float podOff;
    float[][] podHp;
    boolean[] podBeaten;
    Object[][] podKey;
    /** gunpod.png with its bars taken out (they are drawn on their own while they stand), its solid (not dark) pixels. */
    Pixmap podPm;
    TextureRegion podBaseR, podBarR;
    PixelMask podMask;

    /** Is this Eioneus, with its gun pods before the boss? */
    boolean eioEnd() { return g.levelIdx == 3 && podBaseR != null; }

    /** Are the gun pods on the screen (coming down, or hanging there until the terrain breaks up)? */
    boolean podsShown() { return eioEnd() && eioState >= EIO_DESCEND && eioState < EIO_TERRAIN; }

    float podX(int i) { return g.level.pods.get(i)[0]; }
    float podY(int i) { return g.level.pods.get(i)[1] + podOff; }

    void loadPods() {
        if (g.levelIdx != 3 || g.level.pods.isEmpty() || !Gdx.files.internal("sprites/gunpod.png").exists()
                || !Gdx.files.internal("sprites/boss_bar.png").exists()) return;
        Pixmap src = new Pixmap(Gdx.files.internal("sprites/gunpod.png"));
        podPm = new Pixmap(src.getWidth(), src.getHeight(), Pixmap.Format.RGBA8888);
        podPm.setBlending(Pixmap.Blending.None);
        podPm.drawPixmap(src, 0, 0);
        src.dispose();
        Pixmap bar = new Pixmap(Gdx.files.internal("sprites/boss_bar.png"));
        for (int[] b : POD_BAR_POS)                      // (behind a bar: the dark inside of the column)
            for (int y = 0; y < bar.getHeight(); y++)
                for (int x = 0; x < bar.getWidth(); x++)
                    if ((bar.getPixel(x, y) & 0xff) != 0) podPm.drawPixel(b[0] + x, b[1] + y, 0x000000ff);
        bar.dispose();
        int w = podPm.getWidth(), h = podPm.getHeight();
        boolean[] solid = new boolean[w * h];
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                int c = podPm.getPixel(x, y);
                int r = c >>> 24, gg = (c >>> 16) & 0xff, bb2 = (c >>> 8) & 0xff;
                solid[(h - 1 - y) * w + x] = (c & 0xff) != 0 && Math.max(r, Math.max(gg, bb2)) >= 30;
            }
        podMask = new PixelMask(w, h, solid);
        podBaseR = new TextureRegion(new Texture(podPm));
        podBarR = new TextureRegion(new Texture(Gdx.files.internal("sprites/boss_bar.png")));
        int n = g.level.pods.size();
        podHp = new float[n][POD_BARS];
        podBeaten = new boolean[n];
        podKey = new Object[n][POD_BARS];
        for (int i = 0; i < n; i++) for (int k = 0; k < POD_BARS; k++) podKey[i][k] = new Object();
        resetPods();
    }

    /** The pod room starts over (a new try): pods whole and out of sight, no bubbles. */
    void resetPods() {
        if (podHp == null) return;
        eioState = EIO_NONE; eioT = 0f; podOff = 0f;
        for (int i = 0; i < podHp.length; i++) { podBeaten[i] = false; for (int k = 0; k < POD_BARS; k++) podHp[i][k] = POD_BAR_HP; }
    }

    void updateEioneusEnd(float dt) {
        if (g.scrollT < g.level.stopT) return;
        eioT += dt;
        switch (eioState) {
            case EIO_NONE: {                              // the scrolling has stopped: the pods wait out of sight
                float lowest = Float.MAX_VALUE;
                for (float[] pd : g.level.pods) lowest = Math.min(lowest, pd[1]);
                podOff = Math.max(0f, (float) Math.ceil((g.scrollY + H - lowest) / POD_STEP_PX) * POD_STEP_PX);
                eioState = EIO_WAIT; eioT = 0f;
                break;
            }
            case EIO_WAIT:
                if (eioT >= POD_WAIT) { eioState = EIO_DESCEND; eioT = 0f; eioStepT = POD_STEP; }
                break;
            case EIO_DESCEND:
                podOff = Math.max(0f, podOff - POD_STEP_PX / POD_STEP * dt);   // smoothly (Albert, 2026-10-30), 20 px/s
                if (podOff <= 0f) { eioState = EIO_FIGHT; eioT = 0f; bubbleT = BUBBLE_FIRST; }
                break;
            case EIO_FIGHT: {
                if ((bubbleT -= dt) <= 0f) { bubbleT = BUBBLE_EVERY; if (bubbles() < BUBBLE_MAX) spawnBubble(); }
                boolean all = true;
                for (boolean b : podBeaten) all &= b;
                if (all) { eioState = EIO_AFTER; eioT = 0f; }
                break;
            }
            case EIO_AFTER:
                if (eioT >= PODS_DONE_WAIT) {
                    for (Enemy e : g.enemies) if (e.bubble) e.dead = true;
                    g.op4.dissolveTerrain(EIO_TERRAIN_TIME);    // (the pods break up with it)
                    eioState = EIO_TERRAIN; eioT = 0f;
                }
                break;
            case EIO_TERRAIN:
                if (eioT >= EIO_TERRAIN_TIME + EIO_BOSS_WAIT && !g.bossSpawned) { eioState = EIO_BOSS; g.spawnBoss(); }
                break;
            default:
                break;
        }
    }

    int bubbles() {
        int n = 0;
        for (Enemy e : g.enemies) if (e.bubble && !e.dead) n++;
        return n;
    }

    /** A pod (at random) lets a bubble out from under its middle, heading down to the left or right. */
    void spawnBubble() {
        if (g.orbR == null) return;
        int i = MathUtils.random(g.level.pods.size() - 1);
        float line = Float.MAX_VALUE;
        for (int j = 0; j < g.level.pods.size(); j++) line = Math.min(line, podY(j));
        Enemy e = new Enemy(Enemy.Type.ORB, 0f, 0f, g.levelIdx);
        e.bubble = true;
        e.bubbleTop = line;
        e.x = podX(i) + podPm.getWidth() / 2f - e.w / 2f;
        e.y = line - e.h - 1f;
        e.vx = MathUtils.randomBoolean() ? -Enemy.BUBBLE_VX : Enemy.BUBBLE_VX;
        e.vy = -Enemy.BUBBLE_VY;
        g.enemies.add(e);
    }

    /** Lowest bar of pod i still standing (0 = the lowest), -1 = none left. */
    int podTarget(int i) {
        for (int k = 0; k < POD_BARS; k++) if (podHp[i][k] > 0f) return k;
        return -1;
    }

    /** Does the rectangle touch a gun pod: its solid pixels or a bar still standing? */
    boolean podTouches(float x, float y, float w, float h) {
        if (!podsShown()) return false;
        int ph = podPm.getHeight();
        for (int i = 0; i < g.level.pods.size(); i++) {
            float px = podX(i), py = podY(i);
            if (!g.overlap(x, y, w, h, px, py, podPm.getWidth(), ph)) continue;
            if (podMask.overlaps(px, py, x, y, w, h)) return true;
            for (int k = 0; k < POD_BARS; k++)
                if (podHp[i][k] > 0f && g.overlap(x, y, w, h, px + POD_BAR_POS[k][0], py + ph - POD_BAR_POS[k][1] - podBarR.getRegionHeight(),
                        podBarR.getRegionWidth(), podBarR.getRegionHeight())) return true;
        }
        return false;
    }

    /**
     * A ship's shot (on screen: hx, hy, hw, hh) against the gun pods: in a column with bars left, its lowest bar takes
     * it; anywhere else on a pod it just stops (a laser is stopped by laserBlocked). Returns true if dealt with here.
     */
    boolean podShot(Bullet b, float hx, float hy, float hw, float hh, boolean laser) {
        if (!podsShown()) return false;
        int ph = podPm.getHeight();
        float bw = podBarR.getRegionWidth(), bh = podBarR.getRegionHeight();
        for (int i = 0; i < g.level.pods.size(); i++) {
            float px = podX(i), py = podY(i);
            int k = podBeaten[i] ? -1 : podTarget(i);
            if (k >= 0) {
                int[] low = POD_BAR_POS[0];
                float top = py + ph - POD_ORB_TOP, bottom = py + ph - (low[1] + bh + 3f);
                if (g.overlap(hx, hy, hw, hh, px + low[0], bottom, bw, top - bottom)) {
                    Object key = podKey[i][k];
                    if (laser) { if (b.hits.contains(key)) return true; b.hits.add(key); } else b.dead = true;
                    podHp[i][k] -= b.dmg;
                    float bx = px + POD_BAR_POS[k][0], by = py + ph - POD_BAR_POS[k][1] - bh;
                    if (podHp[i][k] <= 0f) {
                        podHp[i][k] = 0f;
                        g.boom(bx + bw / 2f, by + bh / 2f, 0.5f);
                        g.a.play("boom", 0.2f);
                        // (no points for a pod's bar, as on the MSX; the pod gives POD_SCORE)
                        if (podTarget(i) < 0) {               // its last bar: the pod is beaten
                            podBeaten[i] = true;
                            g.addScore(b.owner, POD_SCORE);
                            g.boom(px + podPm.getWidth() / 2f, py + ph - POD_ORB_TOP - 6f, 1.4f);
                            g.a.play("boom", 0.5f);
                        }
                    } else { g.a.play("hit", 0.15f); g.hitSpark(b, bx, by, bw, bh); }
                    if (b.dead && b.kind == Bullet.Kind.MISSILE) g.boom(b.x, b.y, 0.6f);
                    return true;
                }
            }
            if (!laser && podMask.overlaps(px, py, hx, hy, hw, hh)) {
                b.dead = true;
                g.hitSpark(b, px, py, podPm.getWidth(), ph);
                if (b.kind == Bullet.Kind.MISSILE) g.boom(b.x, b.y, 0.6f);
                return true;
            }
        }
        return false;
    }

    void drawPods() {
        if (!podsShown()) return;
        int ph = podPm.getHeight();
        float bh = podBarR.getRegionHeight();
        for (int i = 0; i < g.level.pods.size(); i++) {
            float px = podX(i), py = podY(i);
            g.batch.draw(podBaseR, px, py);
            for (int k = 0; k < POD_BARS; k++)
                if (podHp[i][k] > 0f) g.batch.draw(podBarR, px + POD_BAR_POS[k][0], py + ph - POD_BAR_POS[k][1] - bh);
        }
    }
}
