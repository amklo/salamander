package com.example.salamander;

import com.badlogic.gdx.math.MathUtils;

/** Environmental dangers: falling stalactites, floating bamdas, moving teeth, erupting volcanoes and piston crushers. */
public class Hazard {
    public enum Type { ROCK, VOLCANO, CRUSHER, BAMDA, FANG, ARM, TOWER }

    /** Rocks and bamdas can be shot to pieces and hurt the ship on contact. */
    public boolean shootable() { return type == Type.ROCK || type == Type.BAMDA; }

    public final Type type;
    public float x, y, w, h, vy, t, timer;
    public int hp;
    public boolean dead, falling, fromCeil;
    public float phase, minLen, maxLen, speed;
    // FANG (stage 1 teeth): position when fully out (CRUSHER: mount surface), start delay, cycle length, picture, exact shape
    public float baseY, delay, period;
    public boolean flipV;            // TOWER: picture (and mask) drawn upside down (flipped in Tiled)
    public String image;             // FANG / BAMDA picture
    public PixelMask mask;
    /** Hidden teeth sit this much deeper than their own length, so no tip peeks out of the rock. */
    public static final float FANG_TUCK = 10f;

    // BAMDA: pulses through the frames of sprites/bamda.png (big, medium, small, medium, ...) around a fixed centre;
    // its hit box follows the frame. Frame sizes are filled in by GameScreen from the picture.
    public static float[] bamdaW = {30f}, bamdaH = {29f};
    private static final int[] PULSE = {0, 1, 2, 1};
    public static final float PULSE_FRAME_TIME = 0.28f;
    /** Bamdas are tough, and shoot at the nearest ship like the walkers do. */
    public static final int BAMDA_HP = 25;
    public static final float BAMDA_SHOT_INTERVAL = 2.0f, BAMDA_SHOT_SPEED = 105f;
    public float cx, cy;
    public int frame;

    /** BAMDA: picks the pulse frame for the current time and fits the hit box around the centre. */
    public void pulse() {
        int n = bamdaW.length;
        int step = (int) ((t + phase) / PULSE_FRAME_TIME);
        frame = n >= 3 ? PULSE[step % PULSE.length] : step % n;
        frame = Math.min(frame, n - 1);
        w = bamdaW[frame]; h = bamdaH[frame];
        x = cx - w / 2f; y = cy - h / 2f;
    }

    // ARM (stage 1 claws): a chain of balls ending in a claw. Index 0 is the anchor end (fixed to the floor /
    // ceiling, or to the boss), the last index is the claw. The claw reaches for the nearest ship and the chain
    // follows. Stage arms: orange, orange, blue, claw - only the blue ball can be shot (ARM_HP).
    // Boss arms: 4 blue balls and a claw, indestructible, growing out of the boss in its second phase.
    public static final int ARM_HP = 12;
    /** How close a ship must be (to the anchor) before the claw goes for it, and how fast the claw moves. */
    public static final float ARM_SENSE = 120f, ARM_SPEED = 45f;
    /** Stage arm: where ball 0 is fixed. */
    public float anchorX, anchorY;
    /** Boss arms grow out at this speed (px/s along the arm). */
    public static final float ARM_GROW_SPEED = 40f;
    /** Ball centres, world coordinates (index 0 = anchor end, last = claw). */
    public float[] armX = new float[0], armY = new float[0];
    /** Distance between neighbouring balls. */
    public float armLink;
    /** Index of the ball that can be shot (-1 = none: indestructible). */
    public int armWeak = -1;
    /** Boss arm: all blue balls, moved by the boss; may reach in any direction. */
    public boolean bossArm;
    /** How far the arm has grown out (armLink * (balls - 1) = fully out). Stage arms start fully out. */
    public float armGrow;
    /** Direction from the anchor to the claw when resting (unit vector). */
    public float restDX, restDY;
    private float tipX, tipY;

    /** ARM: lays out a chain of n balls, link apart, from anchor (bx, by) along direction (dx, dy). */
    public void initArm(int n, float link, float bx, float by, float dx, float dy, boolean grownOut) {
        armX = new float[n]; armY = new float[n];
        armLink = link;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        restDX = dx / len; restDY = dy / len;
        armGrow = grownOut ? armReach() : 0f;
        placeArm(bx, by);
        tipX = armX[n - 1]; tipY = armY[n - 1];
    }

    public float armReach() { return armLink * (armX.length - 1); }
    public boolean armGrown() { return armGrow >= armReach(); }

    /** Is ball i out (while growing, the claw comes out first and the balls follow it from the anchor)? */
    public boolean armBallOut(int i) { return armGrow >= armLink * (armX.length - 1 - i); }

    /** Lays the balls out along the resting direction for the current growth. */
    private void placeArm(float bx, float by) {
        int n = armX.length;
        for (int i = 0; i < n; i++) {
            float d = Math.max(0f, armGrow - armLink * (n - 1 - i));
            armX[i] = bx + restDX * d;
            armY[i] = by + restDY * d;
        }
    }

    /** ARM: one frame. (bx, by) = anchor (where ball 0 sits). */
    public void updateArm(GameScreen g, float bx, float by, float dt) {
        if (!armGrown()) {                           // growing out of the boss: straight out along the rest direction
            armGrow = Math.min(armReach(), armGrow + ARM_GROW_SPEED * dt);
            placeArm(bx, by);
            tipX = armX[armX.length - 1]; tipY = armY[armY.length - 1];
            return;
        }
        float reach = armReach();
        // goal: the nearest ship if it comes close, otherwise the resting position
        float gx = bx + restDX * reach, gy = by + restDY * reach;
        Player p = g.target(bx, by);
        if (p != null) {
            float px = p.x + Player.W / 2f, py = p.y + Player.H / 2f;
            if ((px - bx) * (px - bx) + (py - by) * (py - by) < ARM_SENSE * ARM_SENSE) { gx = px; gy = py; }
        }
        reachToward(bx, by, gx, gy, dt);
    }

    /** ARM: moves the claw toward (gx, gy) for dt seconds and lets the chain follow; (bx, by) = anchor. */
    public void reachToward(float bx, float by, float gx, float gy, float dt) {
        float reach = armReach();
        // the claw creeps toward the goal...
        float dx = gx - tipX, dy = gy - tipY, d = (float) Math.sqrt(dx * dx + dy * dy), step = ARM_SPEED * dt;
        if (d > step) { tipX += dx / d * step; tipY += dy / d * step; } else { tipX = gx; tipY = gy; }
        // ...but stays within reach (stage arms: on the open side of their floor / ceiling)
        if (!bossArm) { if (fromCeil) tipY = Math.min(tipY, by + 4f); else tipY = Math.max(tipY, by - 4f); }
        float rx = tipX - bx, ry = tipY - by, r = (float) Math.sqrt(rx * rx + ry * ry);
        if (r > reach) { tipX = bx + rx / r * reach; tipY = by + ry / r * reach; }
        // the chain follows (FABRIK: pull from the claw end, then re-anchor)
        int n = armX.length - 1;
        armX[n] = tipX; armY[n] = tipY;
        for (int i = n - 1; i >= 0; i--) linkTo(i, i + 1);
        armX[0] = bx; armY[0] = by;
        for (int i = 1; i <= n; i++) linkTo(i, i - 1);
    }

    /** Moves ball i to exactly one link away from ball j, keeping its direction. */
    private void linkTo(int i, int j) {
        float dx = armX[i] - armX[j], dy = armY[i] - armY[j], d = (float) Math.sqrt(dx * dx + dy * dy);
        if (d < 0.001f) { dx = restDX; dy = restDY; d = 1f; }
        armX[i] = armX[j] + dx / d * armLink;
        armY[i] = armY[j] + dy / d * armLink;
    }

    /** ARM: index of a ball (that is out) the rectangle touches (12x12 core of each 16x16 ball), or -1. */
    public int armBallAt(float rx, float ry, float rw, float rh) {
        for (int i = armX.length - 1; i >= 0; i--) {
            if (!armBallOut(i)) continue;
            float bx = armX[i] - 6f, by = armY[i] - 6f;
            if (rx < bx + 12f && rx + rw > bx && ry < by + 12f && ry + rh > by) return i;
        }
        return -1;
    }

    public boolean armTouches(float rx, float ry, float rw, float rh) { return armBallAt(rx, ry, rw, rh) >= 0; }

    // TOWER (stage 1): a rock column with a green tip that grows out of the floor / ceiling once it is on screen,
    // then spits lava. Only the green tip can be shot (TOWER_HP). The picture reaches TOWER_ROOT px into the rock.
    public static final int TOWER_HP = 12;
    /** Seconds between two amoebas spat out by a fully grown tower. */
    public static final float TOWER_SPIT_MIN = 2.5f, TOWER_SPIT_MAX = 4f;
    /** Amoebas per spit. */
    public static final int TOWER_SPIT_COUNT = 4;
    public static final float TOWER_GROW_SPEED = 20f, TOWER_ROOT = 8f, TOWER_TIP = 8f;
    /** How far the tower has come out of the rock (h = fully grown; it starts completely inside). */
    public float grown;

    private void updateTower(GameScreen g, float dt) {
        float sx = g.scrollX;
        if (grown < h) {
            if (x < sx + SalamanderGame.W) grown = Math.min(h, grown + TOWER_GROW_SPEED * dt);   // grows once on screen
        } else {
            timer -= dt;                             // fully grown: spits out a spray of amoebas
            if (timer <= 0f && g.inView(x + w / 2f, fromCeil ? y : y + h, 0f)) {   // only while its tip is on screen
                timer = MathUtils.random(TOWER_SPIT_MIN, TOWER_SPIT_MAX);
                float tipY = fromCeil ? y - 16f : y + h;              // just beyond the green tip
                for (int i = 0; i < TOWER_SPIT_COUNT; i++) {          // fanned out left to right
                    float spread = (i - (TOWER_SPIT_COUNT - 1) / 2f) * 45f + MathUtils.random(-10f, 10f);
                    g.spawnAmoeba(x + w / 2f - 8f, tipY, spread, (fromCeil ? -1f : 1f) * MathUtils.random(60f, 110f));
                }
            }
        }
        y = baseY + (fromCeil ? 1f : -1f) * (h - grown);
    }

    /** TOWER: the green tip (its last TOWER_TIP px), once it is out of the rock. */
    public boolean towerTipHit(float rx, float ry, float rw, float rh) {
        if (grown < TOWER_ROOT + TOWER_TIP) return false;
        float ty = fromCeil ? y : y + h - TOWER_TIP;
        return rx < x + w && rx + rw > x && ry < ty + TOWER_TIP && ry + rh > ty;
    }

    public Hazard(Type type) { this.type = type; }

    public void update(GameScreen g, float dt) {
        t += dt;
        float sx = g.scrollX;
        switch (type) {
            case ROCK:
                if (!falling) {
                    // drops when a ship flies underneath
                    if (x > sx && x < sx + SalamanderGame.W) {
                        for (Player p : g.players) {
                            if (GameScreen.alive(p) && Math.abs((p.x + 16f) - (x + 8f)) < 58f) falling = true;
                        }
                    }
                } else {
                    vy -= 430f * dt;
                    y += vy * dt;
                    if (y < g.level.floorTop(x + 8f, y + 8f)) {
                        dead = true;
                        g.boom(x + 8f, y + 8f, 0.8f);
                    }
                }
                break;
            case VOLCANO:
                timer -= dt;
                if (timer <= 0f && g.inView(x, y, 0f)) {   // only spits while its mouth is on screen
                    timer = MathUtils.random(1.8f, 3.0f);
                    int n = MathUtils.random(3, 5);
                    for (int i = 0; i < n; i++) {
                        Bullet b = new Bullet(Bullet.Kind.LAVA, x - 4f, y, 8, 8);
                        b.vx = MathUtils.random(-75f, 75f);
                        b.vy = MathUtils.random(230f, 340f);
                        b.gravity = 420f;
                        g.eBullets.add(b);
                    }
                }
                break;
            case BAMDA:   // floats in place, pulsing, and fires an aimed shot every couple of seconds
                pulse();
                timer -= dt;
                if (timer <= 0f) {
                    timer = BAMDA_SHOT_INTERVAL + MathUtils.random(0f, 0.8f);
                    if (g.inView(cx, cy, 12f)) g.aimedShot(cx, cy, BAMDA_SHOT_SPEED, 0f);
                }
                break;
            case TOWER:
                updateTower(g, dt);
                break;
            case ARM:                                   // stage arm (boss arms are moved by the boss)
                if (!bossArm) updateArm(g, anchorX, anchorY, dt);
                break;
            case FANG:       // hidden in the ground / ceiling, then juts out, holds, and pulls back in
                y = baseY + (fromCeil ? 1f : -1f) * (1f - fangOut()) * (h + FANG_TUCK);
                break;
            default:
                updateCrusher();
                break;
        }
    }

    /** How far a tooth is out: 0 = hidden, 1 = fully out. */
    private float fangOut() {
        if (t < delay) return 0f;
        float u = ((t - delay) % period) / period;
        if (u < 0.2f) return smooth(u / 0.2f);              // jut out
        if (u < 0.45f) return 1f;                           // hold
        if (u < 0.65f) return 1f - smooth((u - 0.45f) / 0.2f);   // pull back
        return 0f;                                          // stay hidden
    }

    private static float smooth(float v) { return v * v * (3f - 2f * v); }

    /** FANG: exact overlap test against the tooth's shape. */
    public boolean touches(float rx, float ry, float rw, float rh) {
        if (rx > x + w || rx + rw < x || ry > y + h || ry + rh < y) return false;
        return mask == null || mask.overlaps(x, y, rx, ry, rw, rh);
    }

    /** Crusher: baseY is the surface it is mounted on (ceiling underside or floor top). */
    public void updateCrusher() {
        float len = minLen + (maxLen - minLen) * (0.5f + 0.5f * MathUtils.sin(t * speed + phase));
        h = len;
        y = fromCeil ? baseY - len : baseY;
    }
}
