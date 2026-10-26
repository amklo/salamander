package com.example.salamander;

import com.badlogic.gdx.math.MathUtils;
import java.util.ArrayList;

/** Environmental dangers: falling stalactites, floating bamdas, moving teeth, erupting volcanoes and piston crushers. */
public class Hazard {
    public enum Type { ROCK, VOLCANO, CRUSHER, BAMDA, FANG, ARM, TOWER, DOME, EYE, RUBBLE }

    /** Rocks and bamdas can be shot to pieces and hurt the ship on contact. */
    public boolean shootable() { return type == Type.ROCK || type == Type.BAMDA; }

    public final Type type;
    public float x, y, w, h, vy, t, timer;
    public int hp;
    /** VOLCANO: 0 = spits lava up (on the floor); -1 / +1 = a side volcano on a rock tip, spitting red rocks to the left / right. */
    public int spit;
    /** Side VOLCANO: rocks still to come in this burst, and the time to the next one. */
    public int burstLeft;
    public float burstT;
    public boolean dead, falling, fromCeil;
    public float phase, minLen, maxLen, speed;
    // FANG (stage 1 teeth): position when fully out (CRUSHER: mount surface), start delay, cycle length, picture, exact shape
    public float baseY, delay, period;
    public boolean flipV;            // TOWER: picture (and mask) drawn upside down (flipped in Tiled)
    public String image;             // FANG / BAMDA picture
    public PixelMask mask;
    /** Hidden teeth sit this much deeper than their own length, so no tip peeks out of the rock. */
    public static final float FANG_TUCK = 10f;
    /** FANG: how far it moves between hidden (inside the rock) and fully out (its tip on the opposite surface). */
    public float fangTravel;
    /**
     * FANG (as on the MSX, stage 1 recording): it slides out and back in at FANG_SPEED px/s (linearly), stays fully out
     * fangHoldOut seconds, and rests with fangRest px of its tip showing for the rest of its period. Fully out its tip
     * stops fangShort px short of the opposite rock (0 = it touches it). Starts resting; the first push comes after delay.
     */
    public static final float FANG_SPEED = 85f;
    public float fangHoldOut = 0.3f, fangRest = 8f, fangShort = 0f;
    /**
     * FANG with "switch" on (stage 1's 5th and 6th): it doesn't pull back. After touching the opposite rock (and
     * holding) it carries on until it has gone right into that rock, and lives there from then on: next time it comes
     * out of that side (drawn and hit upside down), and so on, back and forth. fangCeil / fangFloor = the passage.
     */
    public boolean fangSwitch;
    public float fangCeil, fangFloor;
    public PixelMask maskFlipped;
    /**
     * FANG with "sink" on (stage 1's 3rd, 5th and 6th): after touching the opposite rock (and holding) it carries on
     * until it has gone right into that rock, waits there fangSinkWait seconds, then comes back out of it and slides
     * all the way back home (the right way up), where it rests as usual.
     */
    public boolean fangSink;
    public float fangSinkWait = 0.5f;
    // 0 = not started, 1 = rests, 2 = slides out, 3 = holds, 4 = goes through, 5 = peeks out (switch),
    // 6 = waits in the opposite rock (sink), 7 = travels back home (sink)
    private int fangState;
    private float fangD, fangTimer;    // how far it is out of its hiding place (px); time left in the state

    /** FANG with "switch" or "sink" on: goes right into the opposite rock, then lives there (switch) or comes back (sink). */
    private void updateThroughFang(float dt) {
        float lo = Math.min(fangTravel, FANG_TUCK + fangRest), hi = Math.max(lo, fangTravel - fangShort);
        float end = fangTravel + h + FANG_TUCK;                  // all of it inside the opposite rock
        if (fangState == 0) { fangD = lo; fangTimer = delay; fangState = 1; }
        switch (fangState) {
            case 1: fangTimer -= dt; if (fangTimer <= 0f) fangState = 2; break;
            case 2: fangD += FANG_SPEED * dt; if (fangD >= hi) { fangD = hi; fangTimer = fangHoldOut; fangState = 3; } break;
            case 3: fangTimer -= dt; if (fangTimer <= 0f) fangState = 4; break;
            case 4:
                fangD += FANG_SPEED * dt;
                if (fangD >= end && fangSink) {              // all of it in the other rock: wait there
                    fangD = end; fangTimer = fangSinkWait; fangState = 6;
                } else if (fangD >= end) {                   // it is in the other rock now: that is its home
                    fromCeil = !fromCeil; flipV = !flipV;
                    PixelMask m = mask; mask = maskFlipped; maskFlipped = m;
                    baseY = fromCeil ? fangFloor : fangCeil - h;
                    fangD = 0f; fangState = 5;
                }
                break;
            case 6: fangTimer -= dt; if (fangTimer <= 0f) fangState = 7; break;
            case 7:                                          // comes back out of the other rock and slides home
                fangD -= FANG_SPEED * dt;
                if (fangD <= lo) {
                    fangD = lo; fangState = 1;
                    fangTimer = Math.max(0f, period - (end - lo) * 2f / FANG_SPEED - fangHoldOut - fangSinkWait);
                }
                break;
            default:                                         // peeks out to its resting place, then rests
                fangD += FANG_SPEED * dt;
                if (fangD >= lo) {
                    fangD = lo; fangState = 1;
                    fangTimer = Math.max(0f, period - (hi - lo + end - hi + lo) / FANG_SPEED - fangHoldOut);
                }
                break;
        }
        y = baseY + (fromCeil ? 1f : -1f) * (fangTravel - fangD);
    }

    // BAMDA: pulses through the frames of sprites/bamda.png (big, medium, small, medium, ...) around a fixed centre;
    // its hit box follows the frame. Frame sizes are filled in by GameScreen from the picture.
    public static float[] bamdaW = {30f}, bamdaH = {29f};
    private static final int[] PULSE = {0, 1, 2, 1};
    public static final float PULSE_FRAME_TIME = 0.28f;
    /** Bamdas are tough, and shoot at the nearest ship like the walkers do. */
    public static final int BAMDA_HP = 12;
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

    /**
     * Stage arms start tucked into the rock (only the claw shows, at the anchor) and grow out once their anchor is on
     * screen (ARM_GROW_SPEED): REMAKE along the resting direction, MSX straight out of the floor / ceiling.
     * MSX style (stage 1 recording): the chain stays straight and swings to point at the nearest ship, at most
     * ARM_MSX_MAX_LEAN degrees from straight out, turning at ARM_MSX_TURN degrees/s. The blue (weak) ball moves
     * along the chain with the lean: next to the anchor when it points straight out, one ball further out from
     * ARM_MSX_WEAK_STEP degrees, two balls further from twice that.
     */
    public static final float ARM_MSX_MAX_LEAN = 65f, ARM_MSX_TURN = 150f, ARM_MSX_WEAK_STEP = 22f;
    private int armState;              // 0 = not set up yet, 1 = tucked / growing, 2 = out
    private float armLean;             // MSX: degrees from straight out (+ = toward the right)

    private void updateStageArm(GameScreen g, float bx, float by, float dt) {
        boolean msx = g.level.msx();
        float up = fromCeil ? -1f : 1f;
        if (armState == 0) {
            if (msx) { restDX = 0f; restDY = up; }
            armGrow = 0f; placeArm(bx, by);
            tipX = armX[armX.length - 1]; tipY = armY[armY.length - 1];
            armState = 1;
            if (msx) armWeak = 0;
        }
        if (armState == 1) {
            if (!g.inView(bx, by, 0f)) return;
            armGrow = Math.min(armReach(), armGrow + ARM_GROW_SPEED * dt);
            placeArm(bx, by);
            tipX = armX[armX.length - 1]; tipY = armY[armY.length - 1];
            if (armGrown()) armState = 2;
            return;
        }
        if (!msx) { reachFor(g, bx, by, dt); return; }
        float want = armLean;
        Player p = g.target(bx, by);
        if (p != null) {
            float px = p.x + Player.W / 2f - bx, py = (p.y + Player.H / 2f - by) * up;   // py > 0: out from the rock
            want = MathUtils.atan2(px, Math.max(py, 0.001f)) * MathUtils.radDeg;
        }
        want = MathUtils.clamp(want, -ARM_MSX_MAX_LEAN, ARM_MSX_MAX_LEAN);
        float step = ARM_MSX_TURN * dt;
        armLean += MathUtils.clamp(want - armLean, -step, step);
        float dx = MathUtils.sinDeg(armLean), dy = MathUtils.cosDeg(armLean) * up;
        for (int i = 0; i < armX.length; i++) { armX[i] = bx + dx * armLink * i; armY[i] = by + dy * armLink * i; }
        tipX = armX[armX.length - 1]; tipY = armY[armY.length - 1];
        armWeak = Math.min(armX.length - 2, (int) (Math.abs(armLean) / ARM_MSX_WEAK_STEP));
    }

    /** ARM: one frame. (bx, by) = anchor (where ball 0 sits). */
    public void updateArm(GameScreen g, float bx, float by, float dt) {
        if (!bossArm) { updateStageArm(g, bx, by, dt); return; }
        if (!armGrown()) {                           // growing out of the boss: straight out along the rest direction
            armGrow = Math.min(armReach(), armGrow + ARM_GROW_SPEED * dt);
            placeArm(bx, by);
            tipX = armX[armX.length - 1]; tipY = armY[armY.length - 1];
            return;
        }
        reachFor(g, bx, by, dt);
    }

    /** REMAKE arm (and boss arms): the claw reaches for the nearest ship when it comes close, the chain follows. */
    private void reachFor(GameScreen g, float bx, float by, float dt) {
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
    /** MSX style: sprays come in bursts of TOWER_BURSTS, TOWER_BURST_GAP s apart (as in the stage 1 recording). */
    public static final int TOWER_BURSTS = 3;
    public static final float TOWER_BURST_GAP = 0.85f;
    private int burst;
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
                boolean msx = g.level.msx();                            // MSX: bursts of sprays in quick succession
                burst = msx ? (burst + 1) % TOWER_BURSTS : 0;
                timer = burst != 0 ? TOWER_BURST_GAP : MathUtils.random(TOWER_SPIT_MIN, TOWER_SPIT_MAX);
                int count = TOWER_SPIT_COUNT;
                float tipY = fromCeil ? y - 16f : y + h;              // just beyond the green tip
                int red = burst == 0 ? MathUtils.random(count - 1) : -1;   // one red per burst (it carries a capsule)
                for (int i = 0; i < count; i++) {                       // fanned out left to right
                    float spread = (i - (count - 1) / 2f) * 45f + MathUtils.random(-10f, 10f);
                    g.spawnAmoeba(x + w / 2f - 8f, tipY, spread, (fromCeil ? -1f : 1f) * MathUtils.random(60f, 110f), i == red);
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

    // DOME (stage 2): a dome on a side wall, 18x32 (sprites/dome.png: closed, open; the size comes from the picture). It starts closed, opens
    // DOME_OPEN_DELAY seconds after it comes on screen and stays open. A side-wall dome on the vertical stage (stage
    // 2) sends out waves of Enemy.DOME_FLYER_COUNT green flyers (Enemy.Type.DOME_FLYER), one after the other out of
    // its mouth: they fly a circle above the dome twice, then leave the screen (see Enemy). The next wave comes
    // DOME_WAVE_MIN..MAX seconds after the last flyer of the previous one is gone. Shooting the dome DOME_HP times
    // destroys it (DOME_SCORE points); the flyers already out fly on. flipV is unused; fromCeil = on the right wall
    // (facing left).
    // Stage 3 has the same domes on the floor and the ceiling (domeSide DOME_FLOOR / DOME_CEIL): sprites/dome.png turned
    // a quarter, so it faces up (down), spitting sprays of amoebas up (down) into the corridor like the stage 1 towers.
    public static final int DOME_LEFT = 0, DOME_RIGHT = 1, DOME_FLOOR = 2, DOME_CEIL = 3;
    public int domeSide = DOME_LEFT;
    public static final int DOME_HP = 12, DOME_SCORE = 500;
    public static final float DOME_OPEN_DELAY = 1f;
    /** Seconds from the end of one wave of flyers (the last one gone) to the next wave. */
    public static final float DOME_WAVE_MIN = 2.5f, DOME_WAVE_MAX = 4f;
    /** The circle stays at least this far inside the screen edges (px). */
    private static final float DOME_CIRCLE_MARGIN = 12f;
    public boolean open;
    /** The current wave of flyers: members sent out so far, and the shared path of the wave (screen coordinates). */
    private final ArrayList<Enemy> flyers = new ArrayList<>();
    private Enemy.Group flyGroup;
    private float flyCx, flyCy, flyLead;

    /**
     * Lavinia, both styles (Tiled dome_type2 property "gunhos"): a floor hatch that lets out gunHos Gun Hos instead of
     * spitting amoebas, as in the recording: one each time its left edge reaches GUNHO_HATCH_FIRST,
     * GUNHO_HATCH_FIRST - GUNHO_HATCH_STEP, ... on the screen (every 64 px of scrolling), 7 px in from its left edge.
     */
    public static final float GUNHO_HATCH_FIRST = 208f, GUNHO_HATCH_STEP = 64f;
    public int gunHos;
    private int gunHosOut;

    private void updateDome(GameScreen g, float dt) {
        if (gunHos > 0) {                                     // a Gun Ho hatch
            float sx = x - g.scrollX;
            if (gunHosOut < gunHos && sx <= GUNHO_HATCH_FIRST - gunHosOut * GUNHO_HATCH_STEP && sx > -8f) {
                g.hatchGunHo(x + 7f, y);
                gunHosOut++;
            }
            return;
        }
        if (!g.inView(x + w / 2f, y + h / 2f, 0f)) return;   // waits (closed, or between sprays) while off screen
        boolean sideWall = domeSide == DOME_LEFT || domeSide == DOME_RIGHT;
        if (sideWall && g.level.vertical) { updateFlyerDome(g, dt); return; }
        timer -= dt;
        if (timer > 0f) return;
        if (!open) { open = true; timer = 0.6f; return; }      // opens, and soon spits its first spray
        timer = MathUtils.random(TOWER_SPIT_MIN, TOWER_SPIT_MAX);
        boolean upDown = domeSide == DOME_FLOOR || domeSide == DOME_CEIL;
        float dir = domeSide == DOME_RIGHT || domeSide == DOME_CEIL ? -1f : 1f;
        float mx, my;                                         // out of the open mouth (amoebas are 16x16)
        if (domeSide == DOME_FLOOR) { mx = x + w / 2f - 8f; my = y + h - 4f; }
        else if (domeSide == DOME_CEIL) { mx = x + w / 2f - 8f; my = y - 12f; }
        else { mx = domeSide == DOME_RIGHT ? x - 12f : x + w - 4f; my = y + h / 2f - 8f; }
        int red = MathUtils.random(TOWER_SPIT_COUNT - 1);     // one of them is red (and carries a capsule)
        for (int i = 0; i < TOWER_SPIT_COUNT; i++) {          // fanned out across the mouth
            float spread = (i - (TOWER_SPIT_COUNT - 1) / 2f) * 45f + MathUtils.random(-10f, 10f);
            float out = dir * MathUtils.random(60f, 110f);
            if (upDown) g.spawnAmoeba(mx, my, spread, out, i == red);
            else g.spawnAmoeba(mx, my, out, spread, i == red);
        }
    }

    /** Stage 2 side-wall dome: sends out its waves of green flyers (see above). */
    private void updateFlyerDome(GameScreen g, float dt) {
        int dir = domeSide == DOME_RIGHT ? -1 : 1;
        // its mouth, on the screen (the flyers fly in screen coordinates)
        float sx = (dir > 0 ? x + w - 2f : x + 2f) - g.scrollX, sy = y + h / 2f - g.scrollY;
        if (flyGroup != null && flyers.size() < Enemy.DOME_FLYER_COUNT) {   // a wave is coming out
            timer -= dt;
            if (timer <= 0f) {
                timer += Enemy.DOME_FLYER_GAP;
                flyers.add(g.spawnDomeFlyer(sx, sy, flyCx, flyCy, flyLead, dir, flyGroup));
            }
            return;
        }
        if (flyGroup != null) {                               // all out: the next wave waits until they are all gone
            for (Enemy e : flyers) if (!e.dead) return;
            flyGroup = null;
            flyers.clear();
            timer = MathUtils.random(DOME_WAVE_MIN, DOME_WAVE_MAX);
            return;
        }
        timer -= dt;
        if (timer > 0f) return;
        if (!open) { open = true; timer = 0.6f; return; }      // opens, and soon sends out its first wave
        // a new wave: the circle sits above the mouth, its bottom DOME_FLYER_LEAD px out from the wall (kept on screen)
        float r = Enemy.DOME_FLYER_R, m = DOME_CIRCLE_MARGIN + 8f;
        flyCx = MathUtils.clamp(sx + dir * Enemy.DOME_FLYER_LEAD, r + m, SalamanderGame.W - r - m);
        flyCy = MathUtils.clamp(sy + r, r + m, SalamanderGame.H - r - m);
        flyLead = (float) Math.hypot(flyCx - sx, flyCy - r - sy) / Enemy.DOME_FLYER_SPEED;
        flyGroup = new Enemy.Group();                         // shoot down all of them: a power capsule
        flyGroup.size = Enemy.DOME_FLYER_COUNT;
        flyGroup.carrier = true;
        flyers.add(g.spawnDomeFlyer(sx, sy, flyCx, flyCy, flyLead, dir, flyGroup));
        timer = Enemy.DOME_FLYER_GAP;
    }

    // EYE (stage 2): a rock with a green eye, 32x32 (sprites/rock_eye.png; it is not part of the map art). The eye
    // shoots at the ships like a bamda. The rock is solid to the ships (its exact shape); shot EYE_HP times, the whole
    // rock is destroyed.
    public static final int EYE_HP = 3, EYE_SCORE = 300;

    // RUBBLE (stage 2): what a destroyed eye rock leaves behind (sprites/rubbles.png). Only drawn here: it is solid as a
    // Level.Obstacle, so it works like terrain (indestructible, ships crash into it, shots stop).

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
                if (spit != 0) {                         // side volcano (Eioneus): bursts of red rocks, one after another
                    if (burstLeft > 0) {
                        if ((burstT -= dt) <= 0f) { g.volcanoRock(x, y, spit); burstLeft--; burstT = Meteor.SIDE_GAP; }
                    } else if (timer <= 0f && g.inView(x, y, 0f)) {
                        timer = MathUtils.random(Meteor.SIDE_EVERY_MIN, Meteor.SIDE_EVERY_MAX);
                        burstLeft = MathUtils.random(Meteor.SIDE_BURST_MIN, Meteor.SIDE_BURST_MAX);
                        burstT = 0f;
                    }
                    break;
                }
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
            case DOME:
                updateDome(g, dt);
                break;
            case RUBBLE:                                // just lies there
                break;
            case EYE:                                   // shoots at the nearest ship, like a bamda
                timer -= dt;
                if (timer <= 0f) {
                    timer = BAMDA_SHOT_INTERVAL + MathUtils.random(0f, 0.8f);
                    if (g.inView(x + w / 2f, y + h / 2f, 12f)) g.aimedShotFromRock(x + w / 2f, y + h / 2f, BAMDA_SHOT_SPEED);
                }
                break;
            case ARM:                                   // stage arm (boss arms are moved by the boss)
                if (!bossArm) updateArm(g, anchorX, anchorY, dt);
                break;
            case FANG:       // hidden in the ground / ceiling, then juts out, holds, and pulls back in
                if (fangSwitch || fangSink) updateThroughFang(dt);
                else y = baseY + (fromCeil ? 1f : -1f) * (fangTravel - fangExtension());
                break;
            default:
                updateCrusher();
                break;
        }
    }

    /** How far a tooth is out of its hiding place (px, 0..fangTravel): resting, sliding out, held out, sliding back. */
    public float fangExtension() {
        float lo = Math.min(fangTravel, FANG_TUCK + fangRest), hi = Math.max(lo, fangTravel - fangShort);
        if (t < delay) return lo;
        float move = (hi - lo) / FANG_SPEED;
        float u = (t - delay) % Math.max(period, 2f * move + fangHoldOut);
        if (u < move) return lo + u * FANG_SPEED;                               // slides out
        if (u < move + fangHoldOut) return hi;                                  // holds
        if (u < 2f * move + fangHoldOut) return hi - (u - move - fangHoldOut) * FANG_SPEED;   // slides back
        return lo;                                                              // rests
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
