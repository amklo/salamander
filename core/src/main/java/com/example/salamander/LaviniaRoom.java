package com.example.salamander;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;

import java.util.ArrayList;

/**
 * Lavinia's last room (stage 3, moved out of GameScreen): the hold, the dissolving walls, the Laser Guards' volleys
 * and beams, and the Omen's laser.
 */
final class LaviniaRoom {
    private static final int W = SalamanderGame.W, H = SalamanderGame.H;
    private final GameScreen g;

    LaviniaRoom(GameScreen g) { this.g = g; }

    /** Stage 3's last room: hold, the Laser Guards' fight, their retreat, the walls dissolving. */
    void updateRoom(float dt) {
        if (g.level.holdT < 0f || g.roomState == g.ROOM_DONE) return;
        g.roomT += dt;
        switch (g.roomState) {
            case GameScreen.ROOM_NONE:
                if (g.scrollT >= g.level.holdT) { g.roomState = g.ROOM_FIGHT; g.roomT = 0f; }
                break;
            case GameScreen.ROOM_FIGHT:
                if (g.roomT >= g.level.holdTime) { g.roomState = g.ROOM_RETREAT; g.roomT = 0f; }
                break;
            case GameScreen.ROOM_RETREAT:
                if (g.roomT >= Enemy.GUARD_RETREAT_TIME + 0.4f) { g.roomState = g.ROOM_DEMAT; g.roomT = 0f; g.a.play("power", 0.4f); }
                break;
            default: {                                    // ROOM_DEMAT: the walls lose their pixels, faster and faster
                float f = Math.min(1f, g.roomT / g.WALL_DEMAT_TIME);
                dissolveWalls(f * f);
                if (f >= 1f) {
                    for (Level.DematWall dw : g.level.dematWalls) g.level.setWallGone(dw, true);
                    g.roomState = g.ROOM_DONE;
                }
                break;
            }
        }
    }

    /** (Re)loads the dematwall pictures and their collision: whole (gone = false) or already dissolved. */
    void resetWalls(boolean gone) {
        int n = g.level.dematWalls.size();
        if (g.wallPix == null) { g.wallPix = new Pixmap[n]; g.wallTex = new Texture[n]; g.wallOrder = new int[n][]; g.wallCleared = new int[n]; }
        for (int i = 0; i < n; i++) {
            Level.DematWall dw = g.level.dematWalls.get(i);
            g.level.setWallGone(dw, gone);
            if (g.wallPix[i] != null) g.wallPix[i].dispose();
            if (g.wallTex[i] != null) g.wallTex[i].dispose();
            g.wallPix[i] = new Pixmap(Gdx.files.internal(dw.image));
            g.wallPix[i].setBlending(Pixmap.Blending.None);
            g.wallTex[i] = new Texture(g.wallPix[i]);
            int pw = g.wallPix[i].getWidth(), ph = g.wallPix[i].getHeight(), k = 0;
            int[] order = new int[pw * ph];
            for (int y = 0; y < ph; y++)
                for (int x = 0; x < pw; x++) if ((g.wallPix[i].getPixel(x, y) & 0xff) != 0) order[k++] = y * pw + x;
            order = java.util.Arrays.copyOf(order, k);
            for (int j = k - 1; j > 0; j--) { int r = MathUtils.random(j); int tmp = order[j]; order[j] = order[r]; order[r] = tmp; }
            g.wallOrder[i] = order;
            g.wallCleared[i] = gone ? k : 0;
        }
    }

    /** Clears the walls' pixels (in random order) until the part f (0..1) of them is gone. */
    void dissolveWalls(float f) {
        for (int i = 0; i < g.wallPix.length; i++) {
            int[] order = g.wallOrder[i];
            int want = Math.min(order.length, Math.round(order.length * f));
            if (want <= g.wallCleared[i]) continue;
            int pw = g.wallPix[i].getWidth();
            while (g.wallCleared[i] < want) {
                int q = order[g.wallCleared[i]++];
                g.wallPix[i].drawPixel(q % pw, q / pw, 0);
            }
            g.wallTex[i].draw(g.wallPix[i], 0, 0);
        }
    }

    /** The dematwalls (not part of the stage art), until they have dissolved completely. */
    void drawWalls() {
        if (g.wallTex == null) return;
        for (int i = 0; i < g.wallTex.length; i++) {
            if (g.wallCleared[i] >= g.wallOrder[i].length) continue;
            Level.DematWall dw = g.level.dematWalls.get(i);
            g.batch.draw(g.wallTex[i], dw.x, dw.y, dw.w, dw.h);
        }
    }

    /** Seconds until the Laser Guards' next volley (they all fire together). */
    float guardVolleyT = Enemy.GUARD_FIRE_MAX;

    /**
     * The Laser Guards fire together: once none of their lasers is on screen any more, the next volley comes after
     * Enemy.GUARD_FIRE_MIN..MAX seconds, or as soon as one of them is lined up with a ship (but not straight after the
     * last one). A volley = every guard on screen stops for Enemy.GUARD_WIND_UP seconds, then fires.
     */
    void updateGuardVolley(float dt) {
        boolean any = false, lined = false;
        for (Enemy e : g.enemies) {
            if (e.dead || e.type != Enemy.Type.LASER_GUARD || !g.inView(e.x + e.w / 2f, e.y + e.h / 2f, 6f)) continue;
            if (e.windT > 0f || guardBeamOnScreen(e)) return;   // a volley is still going on
            any = true;
            lined |= e.lined;
        }
        if (!any || g.roomRetreating()) return;
        boolean chaser = false;
        for (Enemy e : g.enemies) if (!e.dead && e.type == Enemy.Type.LASER_GUARD && e.chasing) chaser = true;
        if (!chaser) pickGuardChasers();                 // (the first ones, before the first volley)
        guardVolleyT -= dt;
        if (guardVolleyT > 0f && !(lined && guardVolleyT < Enemy.GUARD_FIRE_MIN - 0.8f)) return;
        guardVolleyT = MathUtils.random(Enemy.GUARD_FIRE_MIN, Enemy.GUARD_FIRE_MAX);
        for (Enemy e : g.enemies) {
            if (e.dead || e.type != Enemy.Type.LASER_GUARD || !g.inView(e.x + e.w / 2f, e.y + e.h / 2f, 6f)) continue;
            e.windT = Enemy.GUARD_WIND_UP;               // all stop now, and fire together
        }
        pickGuardChasers();                              // ...and after the volley, new ones chase the ship
    }

    /**
     * Picks which Laser Guards chase the ship until the next volley: one at random, or sometimes
     * (Enemy.GUARD_TWO_CHASERS) two: the side-wall one (horizontal laser) and the ceiling or the floor one, never both.
     */
    void pickGuardChasers() {
        ArrayList<Enemy> gs = new ArrayList<>(), upDown = new ArrayList<>();
        Enemy wall = null;
        for (Enemy e : g.enemies) {
            if (e.dead || e.type != Enemy.Type.LASER_GUARD) continue;
            e.chasing = false;
            gs.add(e);
            if (e.guardSide == Enemy.GUARD_WALL) wall = e; else upDown.add(e);
        }
        if (gs.isEmpty()) return;
        if (wall != null && !upDown.isEmpty() && MathUtils.random() < Enemy.GUARD_TWO_CHASERS) {
            wall.chasing = true;
            upDown.get(MathUtils.random(upDown.size() - 1)).chasing = true;
        } else gs.get(MathUtils.random(gs.size() - 1)).chasing = true;
    }

    /** Is one of this Laser Guard's beams still on screen? (It stands still until it is gone.) */
    boolean guardBeamOnScreen(Enemy e) {
        for (Bullet b : g.eBullets)
            if (!b.dead && b.guard == e && b.x < g.scrollX + W && b.x + b.w > g.scrollX && b.y < g.scrollY + H && b.y + b.h > g.scrollY) return true;
        return false;
    }

    /** A Laser Guard fires: the beam starts at the gun and grows out of it (see steerGuardBeams). */
    void guardBeam(Enemy e) {
        e.guardMuzzle(muzzleTmp);
        if (!g.inView(muzzleTmp[0], muzzleTmp[1], 0f)) return;
        Bullet b = new Bullet(Bullet.Kind.ENEMY, muzzleTmp[0], muzzleTmp[1], 1f, 1f);
        b.guard = e;
        b.beamDir = e.guardSide;
        b.beamFront = b.beamTail = e.guardSide == Enemy.GUARD_WALL ? muzzleTmp[0] : muzzleTmp[1];
        b.beamAttached = true;
        b.beamSide = e.guardSide == Enemy.GUARD_WALL ? muzzleTmp[1] : muzzleTmp[0];
        b.terrainGrace = 9999f;                      // (rock is handled by steerGuardBeams)
        g.eBullets.add(b);
        placeGuardBeam(b);
    }
    final float[] muzzleTmp = new float[2];

    /**
     * Laser Guard beams behave like the ships' laser: they stay in line with the gun (sweeping along as the guard
     * slides); while attached (Enemy.GUARD_BEAM_TIME) the tail stays at the muzzle and the front grows; then the beam
     * lets go and flies on. The front stops at rock or the screen edge; the beam is gone when its tail gets there too.
     */
    void steerGuardBeams(float dt) {
        for (Bullet b : g.eBullets) {
            if (b.dead || b.guard == null) continue;
            Enemy e = b.guard;
            boolean gunThere = !e.dead && !g.roomRetreating();
            if (gunThere) e.guardMuzzle(muzzleTmp);
            if (b.beamAttached && (!gunThere || b.t >= Enemy.GUARD_BEAM_TIME)) b.beamAttached = false;
            float dir = b.beamDir == Enemy.GUARD_FLOOR ? 1f : -1f;   // up, or down / left
            boolean horiz = b.beamDir == Enemy.GUARD_WALL;
            if (gunThere) b.beamSide = horiz ? muzzleTmp[1] : muzzleTmp[0];
            float step = Enemy.GUARD_BEAM_SPEED * dt;
            if (b.beamAttached) b.beamTail = horiz ? muzzleTmp[0] : muzzleTmp[1];
            else b.beamTail += dir * step;
            float nf = b.beamFront + dir * step, th = g.omenLaserTex != null ? g.omenLaserTex[0].getHeight() : 5f;
            boolean blocked = horiz ? g.level.hits(Math.min(nf, b.beamFront), b.beamSide - th / 2f, step, th) || nf < g.scrollX
                    : g.level.hits(b.beamSide - th / 2f, Math.min(nf, b.beamFront), th, step) || nf < g.scrollY || nf > g.scrollY + H;
            if (!blocked) b.beamFront = nf;
            if ((b.beamTail - b.beamFront) * dir >= 0f && !b.beamAttached) b.dead = true;   // tail caught up: gone
            placeGuardBeam(b);
        }
    }

    /** The beam's hit box from its front, tail and line. */
    void placeGuardBeam(Bullet b) {
        float th = g.omenLaserTex != null ? g.omenLaserTex[0].getHeight() : 5f;
        float lo = Math.min(b.beamFront, b.beamTail), len = Math.max(1f, Math.abs(b.beamFront - b.beamTail));
        if (b.beamDir == Enemy.GUARD_WALL) { b.x = lo; b.w = len; b.y = b.beamSide - th / 2f; b.h = th; }
        else { b.y = lo; b.h = len; b.x = b.beamSide - th / 2f; b.w = th; }
    }

    /** Omen's laser: starts one pattern wide, centred on (cx, cy), and flies up (or down). */
    void omenLaser(float cx, float cy, boolean up) {
        if (!g.inView(cx, cy, 0f)) return;
        float bw = g.omenLaserTex != null ? g.omenLaserTex[0].getWidth() : 8f, bh = g.omenLaserTex != null ? g.omenLaserTex[0].getHeight() : 5f;
        Bullet b = new Bullet(Bullet.Kind.ENEMY, cx - bw / 2f, up ? cy : cy - bh, bw, bh);
        b.vy = up ? Enemy.OMEN_LASER_SPEED : -Enemy.OMEN_LASER_SPEED;
        b.omenLaser = true;
        b.anchorX = cx;
        b.startW = bw;
        b.terrainGrace = 0.15f;   // (it starts on top of the Omen, right against the rock)
        g.eBullets.add(b);
    }

    /**
     * Omen's laser widens as it flies, both ends moving apart, up to Enemy.OMEN_LASER_MAX_W. Like the Ripple on the
     * vertical stages: an end stops at rock and at the screen edge, and pulls back in a narrower passage.
     */
    void growOmenLaser(Bullet b, float dt) {
        float step = Enemy.OMEN_LASER_GROW * dt / 2f;
        float mid = b.anchorX, half = b.startW / 2f, maxHalf = Enemy.OMEN_LASER_MAX_W / 2f;
        float right = b.x + b.w, left = b.x;
        float y = b.y + 1f, h = b.h - 2f;
        if (right - 2f > mid + half && g.level.hits(right - 2f, y, 2f, h)) right = Math.max(mid + half, right - step);
        else if (right + step <= Math.min(g.scrollX + W, mid + maxHalf) && !g.level.hits(right, y, step, h)) right += step;
        if (left + 2f < mid - half && g.level.hits(left, y, 2f, h)) left = Math.min(mid - half, left + step);
        else if (left - step >= Math.max(g.scrollX, mid - maxHalf) && !g.level.hits(left - step, y, step, h)) left -= step;
        b.x = left;
        b.w = right - left;
    }
}
