package com.example.salamander;

import com.badlogic.gdx.math.MathUtils;

/**
 * End-of-stage boss. Every weapon damages it wherever it hits. Its size comes from its sprite
 * (stage 1 is the big brain from the original map); the behaviour is the same for any size.
 */
public class Boss {
    public final float w, h;
    public PixelMask mask;   // exact shape for hits (null = whole rectangle)
    /** After every hit the boss can't lose HP again for this long (seconds). */
    public static final float HIT_COOLDOWN = 0.1f;
    public float hitCooldown;

    // ---- weak spot: an eye drawn over the green area of the boss sprite (only bosses whose sprite has one)
    public boolean hasEye;
    /** Eye rectangle, relative to the boss's bottom-left corner. */
    public float eyeX, eyeY, eyeW, eyeH;
    /** Animation frames, 0 = fully closed ... eyeFrames-1 = fully open. */
    public int eyeFrames = 1;
    public static final float EYE_FRAME_TIME = 0.06f, EYE_SHUT_TIME = 0.25f;
    private static final int EYE_WAIT = 0, EYE_OPENING = 1, EYE_OPEN = 2, EYE_CLOSING = 3, EYE_SHUT = 4;
    private int eyePhase = EYE_WAIT;   // closed until the boss has finished entering
    private float eyePos, eyeShutT;    // eyePos: 0 = closed .. eyeFrames-1 = open

    // ---- hatch (Lavinia warship; Albert's sprites/lavinia_boss_hatch.png: 3 frames of 16x30, closed / open / destroyed),
    // drawn over its nose. As in the MSX recording: the warship comes in from the LEFT edge (HATCH_ENTER_SPEED px/s) to
    // the right of the screen and then moves straight up and down between the top and the bottom (HATCH_MOVE px/s).
    // Every HATCH_PERIOD s the hatch opens for HATCH_OPEN s and a Rage comes out of it. Albert (2026-10-27): the hatch
    // has its own health (HATCH_HP) and can be hurt open or closed; the boss itself only takes damage while the
    // hatch is broken. Once its health is gone the hatch is broken for HATCH_DOWN s: nothing comes out, the boss rushes
    // up and down (HATCH_RAGE_MOVE px/s) and can be hurt anywhere; then the hatch is back, whole again.
    // Both speeds are 75% of what they were (Albert): 48 -> 36, 250 -> 187.5.
    public boolean hasHatch;
    public float hatchX, hatchY, hatchW, hatchH;
    public static final float HATCH_ENTER_SPEED = 40f, HATCH_MOVE = 36f, HATCH_RAGE_MOVE = 187.5f, HATCH_PERIOD = 1.33f,
            HATCH_OPEN = 0.4f, HATCH_DOWN = 10f, HATCH_HP = 30f, HATCH_RIGHT = 7f;
    public static final int HATCH_CLOSED = 0, HATCH_OPENED = 1, HATCH_BROKEN = 2;
    public int hatchState = HATCH_CLOSED;
    private float hatchT = 1f, hatchDownT, hatchDir = -1f;
    /** The hatch's own health (it regenerates to HATCH_HP when the hatch comes back). */
    public float hatchHp = HATCH_HP;

    /** Can the hatch be hurt now? */
    public boolean hatchOpen() { return hasHatch && hatchState == HATCH_OPENED; }

    /** Is the hatch broken (the only time the boss itself takes damage)? */
    public boolean hatchBroken() { return hasHatch && hatchState == HATCH_BROKEN; }

    /** A shot hit the hatch (open or closed). Returns true if that broke it (broken for HATCH_DOWN s). */
    public boolean damageHatch(float dmg) {
        hatchHp -= dmg;
        if (hatchHp > 0f) return false;
        hatchHp = 0f;
        hatchState = HATCH_BROKEN;
        hatchDownT = HATCH_DOWN;
        return true;
    }

    /** The boss lost health: once it is beaten the hatch stays broken. */
    public void onHatchHit() {
        if (hp <= 0f) hatchState = HATCH_BROKEN;
    }

    private void updateHatchBoss(GameScreen g, float dt) {
        float W = SalamanderGame.W, H = SalamanderGame.H, tx = g.scrollX + W - w - HATCH_RIGHT;
        if (entering) {                                  // from behind the ships, along the middle
            x += (g.scrollVX + HATCH_ENTER_SPEED) * dt;
            if (x >= tx) { x = tx; entering = false; }
            return;
        }
        x = tx;
        boolean raging = hatchState == HATCH_BROKEN;
        float lo = g.scrollY + 1f, hi = g.scrollY + H - h - 1f;
        y += hatchDir * (raging ? HATCH_RAGE_MOVE : HATCH_MOVE) * dt;
        if (y <= lo) { y = lo; hatchDir = 1f; }
        if (y >= hi) { y = hi; hatchDir = -1f; }
        if (raging) {
            hatchDownT -= dt;
            if (hatchDownT <= 0f && hp > 0f) { hatchState = HATCH_CLOSED; hatchHp = HATCH_HP; hatchT = HATCH_PERIOD - HATCH_OPEN; }   // whole again
            return;
        }
        hatchT -= dt;
        if (hatchT > 0f) return;
        if (hatchState == HATCH_CLOSED) {                 // opens, and a Rage comes out
            hatchState = HATCH_OPENED;
            hatchT = HATCH_OPEN;
            g.launchRage(x + hatchX - 15f, y + hatchY + hatchH / 2f - 7.5f);
        } else {
            hatchState = HATCH_CLOSED;
            hatchT = HATCH_PERIOD - HATCH_OPEN;
        }
    }

    // ---- core (stage 2, Tetran): a separate picture (sprites/boss1_core.png) drawn over a hole cut in the body. When a
    // boss has one, only the core takes damage; the rest of the body just stops shots.
    public boolean hasCore;
    /** Core's bottom-left relative to the boss's bottom-left, its size and exact shape. */
    public float coreX, coreY, coreW, coreH;
    public PixelMask coreMask;

    /** Does the rectangle touch the core's drawn pixels? */
    public boolean coreTouches(float rx, float ry, float rw, float rh) {
        if (!hasCore) return false;
        float cx = x + coreX, cy = y + coreY;
        return rx < cx + coreW && rx + rw > cx && ry < cy + coreH && ry + rh > cy && coreMask.overlaps(cx, cy, rx, ry, rw, rh);
    }

    // ---- arms (stage 1): in phase 1 the boss has none; below half HP (phase 2) two chains of 4 blue balls and a claw
    // grow out of it, and once they are fully out (phase 3) the boss chases the ships. Shooting any blue ball of an arm
    // damages it (ARM_HP); a destroyed arm grows back out after ARM_REGROW_TIME seconds.
    public static final int PHASE_NORMAL = 0, PHASE_GROWING = 1, PHASE_CHASE = 2;
    public int phase = PHASE_NORMAL;
    /** Per arm: anchor (centre of the first ball) relative to the boss's bottom-left, direction, ball spacing. */
    public float[][] armSpec;
    public Hazard[] arms;
    public static final int ARM_BALLS = 5;           // 4 blue balls + the claw
    /** Arms grow below this share of HP; then the boss chases the ships at this speed (px/s). */
    public static final float ARM_PHASE_HP = 0.5f, CHASE_SPEED = 32f;
    /** Seconds until a destroyed arm starts growing back. */
    public static final float ARM_REGROW_TIME = 1f;
    /** A boss arm is 5 times as tough as a stage arm. */
    public static final int ARM_HP = Hazard.ARM_HP * 5;
    private float[] armRegrowT;

    // ---- Tetran (stage 2, the vertical stage): four identical tentacles of 3 rings plus a bent tip ring turn slowly
    // (clockwise) around the body like a revolving door. The rings can't be hurt: they block shots and hurt the ships. Each tip
    // fires aimed shots. The body (sprite) takes the damage.
    public boolean tetran;
    public static final int TENTACLES = 4, RINGS = 4;
    /** Ring centres, world coordinates: [tentacle][ring], ring 3 = the tip. */
    public final float[][] ringX = new float[TENTACLES][RINGS], ringY = new float[TENTACLES][RINGS];
    /** Ring layout (from the original map), relative to the body centre along the tentacle / to its side. */
    private static final float[] RING_ALONG = {36f, 44f, 52f, 60f}, RING_SIDE = {20f, 20f, 20f, 12f};
    /** Turning speed (radians per second; the tentacles turn clockwise), tip shot interval and speed. */
    public static final float TETRAN_SPIN = 0.675f, TIP_SHOT_INTERVAL = 2.6f, TIP_SHOT_SPEED = 105f;   // (spin was 0.45, +50%)
    /** Tetran's flight: circles (counter-clockwise) around a point on the screen, starting from the top of the circle
     *  where it stops after coming down. Radius in px, speed in radians per second. */
    public static final float TETRAN_CIRCLE_R = 32f, TETRAN_CIRCLE_SPEED = 0.6f;
    private float circleA = MathUtils.HALF_PI;
    public static final float RING_SIZE = 8f;
    private float spinA;
    private final float[] tipShotT = new float[TENTACLES];

    /** Lays the tentacles out around the body for the current turn. */
    private void layoutRings() {
        float cx = x + w / 2f, cy = y + h / 2f;
        for (int k = 0; k < TENTACLES; k++) {
            float a = spinA + k * MathUtils.PI / 2f, dx = MathUtils.cos(a), dy = MathUtils.sin(a);
            float sx = dy, sy = -dx;                 // to the side: the tentacle's direction turned clockwise
            for (int i = 0; i < RINGS; i++) {
                ringX[k][i] = cx + dx * RING_ALONG[i] + sx * RING_SIDE[i];
                ringY[k][i] = cy + dy * RING_ALONG[i] + sy * RING_SIDE[i];
            }
        }
    }

    /** Does the rectangle touch a ring (its 6x6 core)? */
    public boolean ringTouches(float rx, float ry, float rw, float rh) {
        if (!tetran || dying) return false;
        for (int k = 0; k < TENTACLES; k++)
            for (int i = 0; i < RINGS; i++) {
                float bx = ringX[k][i] - 3f, by = ringY[k][i] - 3f;
                if (rx < bx + 6f && rx + rw > bx && ry < by + 6f && ry + rh > by) return true;
            }
        return false;
    }

    /** The boss is beaten: the rings burst. */
    public void explodeRings(GameScreen g) {
        if (!tetran) return;
        for (int k = 0; k < TENTACLES; k++) for (int i = 0; i < RINGS; i++) g.boom(ringX[k][i], ringY[k][i], 0.7f);
    }

    /** Vertical stage: comes down from the top into the upper part of the screen and sways from side to side. */
    private void updateVertical(GameScreen g, float dt) {
        float tx = g.scrollX + SalamanderGame.W / 2f - w / 2f + (tetran ? 0f : MathUtils.sin(t * 0.5f) * 40f);
        float ty = g.scrollY + SalamanderGame.H - 56f - h / 2f;
        x = tx;
        if (entering) {
            y -= 50f * dt;
            if (y <= ty) { y = ty; entering = false; }
        } else if (tetran) {                         // Tetran: flies round in circles
            circleA += TETRAN_CIRCLE_SPEED * dt;
            float ox = g.scrollX + SalamanderGame.W / 2f - w / 2f, oy = ty - TETRAN_CIRCLE_R;   // circle centre
            x = ox + MathUtils.cos(circleA) * TETRAN_CIRCLE_R;
            y = oy + MathUtils.sin(circleA) * TETRAN_CIRCLE_R;
            spinA -= TETRAN_SPIN * dt;            // (clockwise)
        } else {
            y = ty;
            spinA -= TETRAN_SPIN * dt;            // (clockwise)
        }
        if (tetran) {
            layoutRings();
            if (entering) return;
            for (int k = 0; k < TENTACLES; k++) {
                tipShotT[k] -= dt;
                if (tipShotT[k] > 0f) continue;
                tipShotT[k] = TIP_SHOT_INTERVAL + MathUtils.random(0f, 0.6f);
                g.aimedShot(ringX[k][RINGS - 1], ringY[k][RINGS - 1], TIP_SHOT_SPEED, 0f);
            }
        }
    }

    // ---- Kierke's machine wall (stage 5). Albert's pictures (2026-10-28): sprites/boss4_main.png, the main body with
    // the giant core, behind sprites/boss4_wall_without_shield.png, the wall: 320 px tall (taller than the screen), five
    // units 64 px apart (light blue, white, the middle one, yellow, green; unit k's row is wall rows 24 + 64 k .. +16).
    // Each unit has 11 bars (sprites/boss4_wall_bar.png, K_BAR_HP each) in front of an orb (K_ORB_HP). The middle unit's
    // bars are behind the shield (sprites/boss4_wall_shield.png): it can't be hurt, and it goes once the four other orbs
    // are destroyed; behind its bars are 2 core bars (boss4_wall_bar_core.png, K_CORE_BAR_HP each) and the middle orb
    // (boss4_wall_main_orb.png, K_ORB_HP). With the middle orb gone the middle row is open: the ships can fly through it
    // to the giant core (K_CORE_HP), the only part that beats the boss. Enemy shots can't pass through any of it.
    // Movement, measured from the MSX recording (3:05-4:51): the wall moves up and down in K_STEP px steps every K_STEP_T
    // s; each round it moves K_RANGE px (alternately up and down), waits K_PAUSE1 s, moves K_STOP_GRID..K_RANGE px either
    // way, waits K_PAUSE2 s, then rams: it slides K_DASH px left toward the ships in K_STEP px steps every K_DASH_STEP_T
    // s and straight back, and waits K_PAUSE3 s. The wall wraps around (Albert): it can scroll on forever.
    // Every pod (wall rows K_POD_ROWS, between the units) fires aimed shots (K_SHOT_SPEED px/s) every K_SHOT_T s, except
    // while ramming, when its tip is in one of the K_FIRE_BANDS (screen y from the top). (Albert, 2026-10-27: all pods;
    // in the recording only the two upper ones did, and only from y 14-62 and 94-126.)
    public boolean kierke;
    public static final int K_UNITS = 5, K_UNIT = 64, K_STACK_H = 320, K_RANGE = 152, K_STOP_GRID = 16,
            K_MIDDLE = 2, K_BARS = 11, K_CORE_BARS = 2;
    public static final float K_STEP = 8f, K_STEP_T = 17f / 59.92f, K_DASH = 96f, K_DASH_STEP_T = 8f / 59.92f,
            K_PAUSE1 = 2f, K_PAUSE2 = 1.7f, K_PAUSE3 = 0.45f, K_START = 0.3f, K_SHOT_T = 0.28f, K_SHOT_SPEED = 42f,
            K_BAR_HP = 10f, K_ORB_HP = 20f, K_CORE_BAR_HP = 10f, K_CORE_HP = 40f;
    /** Top wall row of unit k (its bars, orb and the middle unit's parts are 16 rows from there). */
    public static int kUnitRow(int k) { return 24 + K_UNIT * k; }
    public static final float[] K_POD_ROWS = {63.5f, 127.5f, 191.5f, 255.5f, 319.5f};
    /** Screen bands (y from the top) the pods fire from: anywhere on the screen (the recording: {14, 62}, {94, 126}). */
    public static final float[][] K_FIRE_BANDS = {{6f, 162f}};
    /** How far the wall has moved up (any value: it wraps every K_STACK_H px) and slid left (0..K_DASH). */
    public float kOff, kShift;
    /** Health: per unit its bars (front, leftmost, first) and orb (the middle unit's = the middle orb); the core bars. */
    public final float[][] kBarHp = new float[K_UNITS][K_BARS];
    public final float[] kOrbHp = new float[K_UNITS];
    public final float[] kCoreBarHp = new float[K_CORE_BARS];
    public boolean kShield = true;
    private static final int K_WAIT = 0, K_MOVE1 = 1, K_PAUSE_1 = 2, K_MOVE2 = 3, K_PAUSE_2 = 4, K_OUT = 5, K_BACK = 6, K_PAUSE_3 = 7;
    private int kState = K_WAIT;
    private float kT, kTarget, kShotT, kDir = 1f;
    /** Bullets fired in the current burst (K_BURST, then a rest of K_BURST_REST s). */
    private int kBurstShots;
    public static final int K_BURST = 7;
    public static final float K_BURST_REST = 2f;
    /** The top of the screen in the world (the map's top: Kierke doesn't scroll up and down). */
    public float kTop;

    public void initKierke(float top) {
        kierke = true;
        kTop = top;
        hp = maxHp = K_CORE_HP;
        kT = K_START;
        for (float[] b : kBarHp) java.util.Arrays.fill(b, K_BAR_HP);
        java.util.Arrays.fill(kOrbHp, K_ORB_HP);
        java.util.Arrays.fill(kCoreBarHp, K_CORE_BAR_HP);
    }

    /** The wall's offset in 0..K_STACK_H. */
    public float kOffMod() { float o = kOff % K_STACK_H; return o < 0f ? o + K_STACK_H : o; }
    /** Unit k's front bar still standing (-1: none left). */
    public int kFrontBar(int k) { for (int i = 0; i < K_BARS; i++) if (kBarHp[k][i] > 0f) return i; return -1; }
    /** The middle unit's front core bar still standing (-1: none). */
    public int kFrontCoreBar() { for (int i = 0; i < K_CORE_BARS; i++) if (kCoreBarHp[i] > 0f) return i; return -1; }
    /** Are the four outer orbs destroyed (the shield goes)? */
    public boolean kOuterOrbsGone() {
        for (int k = 0; k < K_UNITS; k++) if (k != K_MIDDLE && kOrbHp[k] > 0f) return false;
        return true;
    }
    /** Is the middle orb destroyed (the way to the giant core is open)? */
    public boolean kWayOpen() { return kOrbHp[K_MIDDLE] <= 0f; }

    /** World y of the top edge of wall row u in copy n (0: the copy starting at the screen top, 1: the one below it). */
    public float kRowY(float u, int n) { return kTop - (u - kOffMod() + K_STACK_H * n); }
    public float kColX() { return x - kShift; }

    private void kStartMove(int state, float dist) {
        kState = state;
        kTarget = kOff + dist;
        kT = K_STEP_T;
    }

    private void updateKierke(GameScreen g, float dt) {
        if (Math.abs(g.scrollVX) > 0.01f) return;        // waits until the scrolling has stopped
        kT -= dt;
        switch (kState) {
            case K_WAIT: case K_PAUSE_3:
                if (kT <= 0f) { kStartMove(K_MOVE1, kDir * K_RANGE); kDir = -kDir; }   // (first up, as in the recording)
                break;
            case K_MOVE1: case K_MOVE2: {   // smoothly, at the speed of the MSX's steps (Albert, 2026-10-30: no 8 px steps)
                float d = kTarget - kOff, v = K_STEP / K_STEP_T * dt;
                kOff = Math.abs(d) <= v ? kTarget : kOff + Math.signum(d) * v;
                if (kOff == kTarget) {
                    kState = kState == K_MOVE1 ? K_PAUSE_1 : K_PAUSE_2;
                    kT = kState == K_PAUSE_1 ? K_PAUSE1 : K_PAUSE2;
                }
                break;
            }
            case K_PAUSE_1:
                if (kT <= 0f) kStartMove(K_MOVE2, (MathUtils.randomBoolean() ? 1f : -1f)
                        * MathUtils.random(1, K_RANGE / K_STOP_GRID) * K_STOP_GRID);
                break;
            case K_PAUSE_2:
                if (kT <= 0f) { kState = K_OUT; kT = K_DASH_STEP_T; }
                break;
            case K_OUT: case K_BACK: {      // (smoothly too, at the dash steps' speed)
                float v = K_STEP / K_DASH_STEP_T * dt;
                kShift += kState == K_OUT ? v : -v;
                if (kState == K_OUT && kShift >= K_DASH) { kShift = K_DASH; kState = K_BACK; }
                else if (kState == K_BACK && kShift <= 0f) { kShift = 0f; kState = K_PAUSE_3; kT = K_PAUSE3; }
                break;
            }
        }
        // the pods' shots, from their tip (the wall's first pixels there are at x 9, so the wall doesn't stop them)
        // (Albert, 2026-10-30: in bursts of K_BURST bullets, then K_BURST_REST s without shooting)
        kShotT -= dt;
        if (kShotT <= 0f) {
            kShotT += K_SHOT_T;
            if (kState != K_OUT && kState != K_BACK) {
                float off = kOffMod();
                for (float row : K_POD_ROWS) {
                    for (int n = 0; n < 2 && kBurstShots < K_BURST; n++) {
                        float sy = row - off + K_STACK_H * n;   // screen y from the top
                        boolean band = false;
                        for (float[] b : K_FIRE_BANDS) if (sy >= b[0] && sy <= b[1]) band = true;
                        if (!band) continue;
                        int before = g.eBullets.size();
                        g.aimedShot(kColX() + 3f, kTop - sy, K_SHOT_SPEED, 0f);
                        kBurstShots += g.eBullets.size() - before;
                    }
                }
                if (kBurstShots >= K_BURST) { kBurstShots = 0; kShotT = K_BURST_REST; }
            }
        }
    }

    public final int kind;
    public float x, y, t, hp, maxHp, flash, shotT, spin, dieT, expT;
    public boolean entering = true, dying;
    /** Sits where the map has it (map object "boss" with "fixed" on): it does not move, it only shoots. */
    public boolean fixed;
    /**
     * Drifting and bouncing (was Op 4's brain sphere before it got its own movement, initSphere; kept for other
     * bosses): instead of sitting still it drifts diagonally and bounces off the screen edges (BOUNCE_VX / BOUNCE_VY px/s), its visible ball (bounceX0, bounceY0, bounceW, bounceH inside the
     * picture, y up) staying BOUNCE_EDGE px inside the screen.
     */
    public boolean bounce;
    public float bounceX0, bounceY0, bounceW, bounceH;
    private float bounceVX, bounceVY;
    public static final float BOUNCE_VX = 42f, BOUNCE_VY = 30f, BOUNCE_EDGE = 2f;

    /** Starts bouncing; the ball is the solid part of the picture's mask (the whole picture without a mask). */
    public void initBounce(PixelMask m) {
        bounce = true;
        bounceX0 = 0f; bounceY0 = 0f; bounceW = w; bounceH = h;
        if (m != null) {
            int x0 = m.w, y0 = m.h, x1 = -1, y1 = -1;
            for (int yy = 0; yy < m.h; yy++)
                for (int xx = 0; xx < m.w; xx++)
                    if (m.at(xx, yy)) { x0 = Math.min(x0, xx); x1 = Math.max(x1, xx); y0 = Math.min(y0, yy); y1 = Math.max(y1, yy); }
            if (x1 >= 0) { bounceX0 = x0; bounceY0 = y0; bounceW = x1 - x0 + 1; bounceH = y1 - y0 + 1; }
        }
        bounceVX = MathUtils.randomBoolean() ? BOUNCE_VX : -BOUNCE_VX;
        bounceVY = -BOUNCE_VY;                       // (first down, toward the ships)
    }

    private void updateBounce(float dt, float camX, float camY) {
        x += bounceVX * dt; y += bounceVY * dt;
        float l = camX + BOUNCE_EDGE - bounceX0, r = camX + SalamanderGame.W - BOUNCE_EDGE - bounceX0 - bounceW;
        float b = camY + BOUNCE_EDGE - bounceY0, t = camY + SalamanderGame.H - BOUNCE_EDGE - bounceY0 - bounceH;
        if (x < l) { x = l; bounceVX = Math.abs(bounceVX); } else if (x > r && r >= l) { x = r; bounceVX = -Math.abs(bounceVX); }
        if (y < b) { y = b; bounceVY = Math.abs(bounceVY); } else if (y > t && t >= b) { y = t; bounceVY = -Math.abs(bounceVY); }
    }
    private int burst;

    // ---- Op 4's end (stage 6), from the MSX recording 220-268 s (Albert, 2026-10-27)
    /**
     * The ships fly over it: touching it does them no harm, and shots only stop on its weak spots. (Was Op 4's
     * bosses; since 2026-10-29 no boss is a ghost: every boss body is solid, Albert. Kept for a boss that needs it.)
     */
    public boolean ghost;
    /** Its only weak spot (bottom-left inside the picture, y up; weakW 0 = none): shots anywhere else fly on. */
    public float weakX, weakY, weakW, weakH;
    /** Beaten, it breaks up pixel by pixel (GameScreen.dissolveDeath) instead of blowing up, for DISSOLVE_TIME s. */
    public boolean dissolve;
    public static final float DISSOLVE_TIME = 3f;

    public boolean weakTouches(float rx, float ry, float rw, float rh) {
        return weakW > 0f && rx < x + weakX + weakW && rx + rw > x + weakX && ry < y + weakY + weakH && ry + rh > y + weakY;
    }

    /**
     * The brain sphere (boss5a): it rises in through the bottom edge (SPHERE_RISE px/s) until its ball's top is at the
     * top edge, then bobs SPHERE_BOB px up and down there and slides sideways toward the nearest ship (SPHERE_SLIDE
     * px/s; its ball stays on the screen). It doesn't shoot (GameScreen sends option eaters while it is there).
     */
    public boolean sphere;
    public static final float SPHERE_RISE = 70f, SPHERE_SLIDE = 16f, SPHERE_BOB = 4f, SPHERE_BOB_SPEED = 1.6f;
    private float sphereBobT;

    public void initSphere(PixelMask m) {
        sphere = true;
        dissolve = true;
        ghost = false;                               // (Albert, 2026-10-29: every boss is solid)
        entering = true;
        initBounce(m);                               // (only for the size of its ball: bounceX0 .. bounceH)
        bounce = false;
    }

    private void updateSphere(GameScreen g, float dt) {
        float park = g.scrollY + SalamanderGame.H - bounceY0 - bounceH;   // the ball's top at the top edge
        if (entering) {
            y += SPHERE_RISE * dt;
            if (y >= park) { y = park; entering = false; }
            return;
        }
        sphereBobT += dt;
        y = park - SPHERE_BOB * (1f - MathUtils.cos(sphereBobT * SPHERE_BOB_SPEED));   // (down and back up)
        Player p = g.target(x + w / 2f, y + h / 2f);
        float ex = x + (weakW > 0f ? weakX + weakW / 2f : w / 2f);
        if (p != null) {
            float d = p.x + Player.W / 2f - ex;
            if (Math.abs(d) > 2f) x += Math.signum(d) * SPHERE_SLIDE * dt;
        }
        x = MathUtils.clamp(x, g.scrollX - bounceX0, Math.max(g.scrollX - bounceX0, g.scrollX + SalamanderGame.W - bounceX0 - bounceW));
    }

    /**
     * The gun units (boss5b): they appear where they are (GameScreen shows them coming together pixel by pixel for
     * GUNS_APPEAR s, their top GUNS_ABOVE px above the top edge) and sway GUNS_SWAY px left and right. Every now and
     * then (after a wait of GUNS_WAIT_MIN..MAX s) they move down or back up (Albert): down to about GUNS_DROP px lower
     * (the MSX shows the 56 px drop, which brings the upper orbs on screen), up to at most GUNS_UP_MAX px below where
     * they started, at GUNS_DROP_SPEED px/s.
     * Their attacks: every GUNS_LASER_EVERY s the dotted lasers out from under the columns, and every
     * GUNS_CREATURE_EVERY s a green creature out of the middle (GameScreen.gunLasers / gunCreature).
     */
    public boolean guns, appearing;
    public float appearT;
    public static final float GUNS_APPEAR = 2.2f, GUNS_ABOVE = 48f, GUNS_SWAY = 4f, GUNS_SWAY_SPEED = 1.3f,
            GUNS_DROP = 56f, GUNS_DROP_SPEED = 16f, GUNS_UP_MAX = 16f, GUNS_WAIT_MIN = 3f, GUNS_WAIT_MAX = 6f,
            GUNS_LASER_FIRST = 6f, GUNS_LASER_EVERY = 6f, GUNS_CREATURE_FIRST = 5f, GUNS_CREATURE_EVERY = 6f;
    private float gunsOff, gunsTarget, gunsWait = GUNS_WAIT_MAX, gunsLaserT, gunsCreatureT;

    public void initGuns(GameScreen g) {
        guns = true;
        dissolve = true;
        ghost = false;                               // (Albert, 2026-10-29: every boss is solid; not while it appears)
        appearing = true;
        appearT = 0f;
        entering = false;
        gunsLaserT = GUNS_LASER_FIRST;
        gunsCreatureT = GUNS_CREATURE_FIRST;
        placeGuns(g);
    }

    private void placeGuns(GameScreen g) {
        x = g.scrollX - 1f + MathUtils.sin(t * GUNS_SWAY_SPEED) * GUNS_SWAY;   // (top-left 1 px left of the screen, as on the MSX)
        y = g.scrollY + SalamanderGame.H + GUNS_ABOVE - h - gunsOff;
    }

    private void updateGuns(GameScreen g, float dt) {
        if (!appearing) {
            if (gunsOff != gunsTarget) {              // on the move, down or up
                float step = GUNS_DROP_SPEED * dt;
                gunsOff = Math.abs(gunsTarget - gunsOff) <= step ? gunsTarget : gunsOff + Math.signum(gunsTarget - gunsOff) * step;
            } else if ((gunsWait -= dt) <= 0f) {      // waited long enough: the next move (down if up, up if down)
                gunsWait = MathUtils.random(GUNS_WAIT_MIN, GUNS_WAIT_MAX);
                gunsTarget = gunsOff < GUNS_DROP / 2f ? MathUtils.random(GUNS_DROP - 16f, GUNS_DROP) : MathUtils.random(0f, GUNS_UP_MAX);
            }
        }
        placeGuns(g);
        if (appearing) {
            appearT += dt;
            if (appearT >= GUNS_APPEAR) appearing = false;
            return;
        }
        if ((gunsLaserT -= dt) <= 0f) { gunsLaserT = GUNS_LASER_EVERY; g.gunLasers(this); }
        if ((gunsCreatureT -= dt) <= 0f) { gunsCreatureT = GUNS_CREATURE_EVERY; g.gunCreature(this); }
    }

    /**
     * Eioneus (stage 4, boss3.png), from the MSX recording 264-292 s (Albert, 2026-10-27). It comes down through the
     * top edge (EIO_ENTER px/s) in the middle until its top is EIO_HIGH px above the top edge. Every EIO_STEP_T s it
     * steps EIO_STEP px sideways toward the nearest ship (its middle over the ship), staying between EIO_LEFT and
     * EIO_RIGHT (left edge, from the screen's left edge: it may hang off the side). EIO_DROP_AFTER s after it came,
     * it comes down to EIO_LOW px below the top edge (EIO_STEP px every EIO_DROP_T s), and from then on goes EIO_STEP
     * px up and back down every EIO_BOB_EVERY s. Every EIO_BEAM_EVERY s (the first EIO_BEAM_FIRST s after it came)
     * four dotted lasers come out of its leg tips (GameScreen.eioneusLasers). It is solid; beaten, it breaks up pixel
     * by pixel.
     */
    public boolean eioneus;
    public static final float EIO_ENTER = 72f, EIO_HIGH = 41f, EIO_LOW = 7f, EIO_STEP = 8f, EIO_STEP_T = 0.25f,
            EIO_DROP_T = 0.17f, EIO_DROP_AFTER = 8f, EIO_BOB_EVERY = 4f, EIO_LEFT = -35f, EIO_RIGHT = 89f,
            EIO_BEAM_FIRST = 5.6f, EIO_BEAM_EVERY = 11f;
    /** How far its top is below the screen's top edge (negative: above), where it is heading, and its clocks. */
    private float eioTop, eioWant, eioStepT, eioVT, eioArrT, eioBobT, eioBeamT;

    public void initEioneus(GameScreen g) {
        eioneus = true;
        dissolve = true;
        entering = true;
        x = g.scrollX + SalamanderGame.W / 2f - w / 2f;
        eioTop = -h;                                  // (just above the top edge)
        y = g.scrollY + SalamanderGame.H - eioTop - h;
    }

    private void updateEioneus(GameScreen g, float dt) {
        float H = SalamanderGame.H;
        if (entering) {
            eioTop = Math.min(-EIO_HIGH, eioTop + EIO_ENTER * dt);
            y = g.scrollY + H - eioTop - h;
            if (eioTop >= -EIO_HIGH) {
                entering = false;
                eioWant = -EIO_HIGH;
                eioStepT = EIO_STEP_T; eioVT = EIO_DROP_T; eioBeamT = EIO_BEAM_FIRST;
            }
            return;
        }
        eioArrT += dt;
        if ((eioStepT -= dt) <= 0f) {                 // sideways, in steps, toward the ship
            eioStepT += EIO_STEP_T;
            Player p = g.target(x + w / 2f, y + h / 2f);
            if (p != null) {
                float d = p.x + Player.W / 2f - (x + w / 2f);
                if (Math.abs(d) >= EIO_STEP) x += Math.signum(d) * EIO_STEP;
            }
            x = MathUtils.clamp(x, g.scrollX + EIO_LEFT, g.scrollX + EIO_RIGHT);
        }
        if (eioArrT >= EIO_DROP_AFTER) {              // down to its low place, then a step up and back now and then
            if (eioWant < EIO_LOW - EIO_STEP) eioWant = EIO_LOW;
            else if ((eioBobT += dt) >= EIO_BOB_EVERY) { eioBobT = 0f; eioWant = eioWant >= EIO_LOW ? EIO_LOW - EIO_STEP : EIO_LOW; }
        }
        if (eioTop != eioWant && (eioVT -= dt) <= 0f) {
            eioVT += EIO_DROP_T;
            eioTop = eioTop < eioWant ? Math.min(eioWant, eioTop + EIO_STEP) : Math.max(eioWant, eioTop - EIO_STEP);
        }
        y = g.scrollY + H - eioTop - h;
        if ((eioBeamT -= dt) <= 0f) { eioBeamT = EIO_BEAM_EVERY; g.eioneusLasers(this); }
    }

    public Boss(int kind, float x, int hp, float w, float h) {
        this.kind = kind; this.x = x; this.hp = hp; this.maxHp = hp;
        this.w = w; this.h = h;
        this.y = SalamanderGame.H / 2f - h / 2f;
        this.shotT = 1.5f;
        for (int k = 0; k < TENTACLES; k++) tipShotT[k] = 1f + k * 0.65f;   // the tips take turns
    }

    /** Tetran: places the rings around the body before the first frame is drawn. */
    public void initTetran() {
        tetran = true;
        layoutRings();
    }

    private void updateEye(float dt) {
        switch (eyePhase) {
            case EYE_WAIT:
                if (!entering) eyePhase = EYE_OPENING;   // opens once the boss is in place
                break;
            case EYE_OPENING:
                eyePos += dt / EYE_FRAME_TIME;
                if (eyePos >= eyeFrames - 1) { eyePos = eyeFrames - 1; eyePhase = EYE_OPEN; }
                break;
            case EYE_CLOSING:
                eyePos -= dt / EYE_FRAME_TIME;
                if (eyePos <= 0f) { eyePos = 0f; eyePhase = EYE_SHUT; eyeShutT = EYE_SHUT_TIME; }
                break;
            case EYE_SHUT:
                eyeShutT -= dt;
                if (eyeShutT <= 0f) eyePhase = EYE_OPENING;
                break;
            default:
                break;
        }
    }

    /** Animation frame to draw: 0 = closed ... eyeFrames-1 = open. */
    public int eyeFrame() { return Math.max(0, Math.min(eyeFrames - 1, Math.round(eyePos))); }

    /** Fully closed: the eye can't be hurt. */
    public boolean eyeShut() { return eyeFrame() == 0; }

    /** The eye took a hit: it closes, stays shut for EYE_SHUT_TIME, then opens again. */
    public void onEyeHit() {
        if (eyePhase == EYE_OPEN || eyePhase == EYE_OPENING) eyePhase = EYE_CLOSING;
    }

    /** Phase 2: both arms start growing out of their spots on the boss. */
    private void growArms(GameScreen g) {
        phase = PHASE_GROWING;
        arms = new Hazard[armSpec.length];
        armRegrowT = new float[armSpec.length];
        for (int k = 0; k < armSpec.length; k++) newArm(g, k);
        g.a.play("power", 0.4f);
    }

    /** Arm k grows out of its spot on the boss (again). */
    private void newArm(GameScreen g, int k) {
        float[] s = armSpec[k];
        Hazard a = new Hazard(Hazard.Type.ARM);
        a.bossArm = true;                            // all blue balls; any of them can be shot
        a.hp = ARM_HP;
        a.initArm(ARM_BALLS, s[4], x + s[0], y + s[1], s[2], s[3], false);
        arms[k] = a;
        g.addHazard(a);
    }

    /** Phase 3: drifts toward the nearest ship (staying on screen). */
    private void chase(GameScreen g, float dt) {
        Player p = g.target(x + w / 2f, y + h / 2f);
        if (p != null) {
            float dx = (p.x + Player.W / 2f) - (x + w / 2f), dy = (p.y + Player.H / 2f) - (y + h / 2f);
            float d = (float) Math.sqrt(dx * dx + dy * dy), step = CHASE_SPEED * dt;
            if (d > 1f) { x += dx / d * Math.min(step, d); y += dy / d * Math.min(step, d); }
        }
        x = MathUtils.clamp(x, g.scrollX, g.scrollX + SalamanderGame.W - w);
        y = MathUtils.clamp(y, g.scrollY, g.scrollY + SalamanderGame.H - h);
    }

    /** The boss is beaten: its arms burst too. */
    public void explodeArms(GameScreen g) {
        if (arms == null) return;
        for (Hazard a : arms) {
            if (a.dead) continue;                    // (already shot off)
            for (int i = 0; i < a.armX.length; i++) if (a.armBallOut(i)) g.boom(a.armX[i], a.armY[i], 0.9f);
            a.dead = true;
        }
        arms = null;                                 // (no regrowing once the boss is beaten)
    }

    public void update(GameScreen g, float dt) {
        t += dt;
        if (flash > 0f) flash -= dt;
        if (hitCooldown > 0f) hitCooldown -= dt;
        if (hasEye && !dying) updateEye(dt);
        if (dying && dissolve) {                     // (breaking up: GameScreen.dissolveDeath shows it)
            dieT += dt;
            if (dieT > DISSOLVE_TIME) g.bossDefeated();
            return;
        }
        if (dying) {
            dieT += dt;
            expT -= dt;
            if (expT <= 0f) {
                expT = 0.1f;
                g.boom(x + MathUtils.random(0f, w), y + MathUtils.random(0f, h), MathUtils.random(0.8f, 1.6f));
                g.a.play("boom", 0.2f);
            }
            if (dieT > 2.4f) g.bossDefeated();
            return;
        }
        if (kierke) {                                // (Kierke's machine wall: its own movement and shots)
            updateKierke(g, dt);
            return;
        }
        if (hasHatch) {                              // (Lavinia warship: its own movement, the Rages)
            updateHatchBoss(g, dt);
            return;
        }
        if (tetran) {                                // (Tetran: its own movement and shots)
            updateVertical(g, dt);
            return;
        }
        if (sphere) { updateSphere(g, dt); return; }  // (Op 4: the brain sphere, then the gun units)
        if (guns) { updateGuns(g, dt); return; }
        if (eioneus) { updateEioneus(g, dt); return; }
        if (fixed) {
            if (bounce) updateBounce(dt, g.scrollX, g.scrollY);
            shoot(g, dt);
            return;
        }
        if (g.level.vertical) {                      // other vertical stages: sways at the top of the screen
            updateVertical(g, dt);
            if (!entering) shoot(g, dt);
            return;
        }
        if (phase == PHASE_NORMAL && armSpec != null && !entering && hp <= maxHp * ARM_PHASE_HP) growArms(g);
        if (phase == PHASE_CHASE) {
            chase(g, dt);
        } else {
            float tx = g.scrollX + SalamanderGame.W - (w + 24f) + MathUtils.sin(t * 0.7f) * 12f;
            float swing = Math.min(78f, (SalamanderGame.H - h) / 2f - 4f);   // a bigger boss sways less
            y = g.scrollY + SalamanderGame.H / 2f - h / 2f + MathUtils.sin(t * (0.8f + 0.15f * kind)) * swing;
            if (entering) {
                x -= 90f * dt;
                if (x <= tx) entering = false;
                return;
            }
            x = tx;
        }
        if (arms != null) {                          // the arms hang on to the boss wherever it goes
            boolean allOut = true;
            for (int k = 0; k < arms.length; k++) {
                Hazard a = arms[k];
                if (a.dead) {                        // shot off: grows back after a while
                    allOut = false;
                    armRegrowT[k] += dt;
                    if (armRegrowT[k] >= ARM_REGROW_TIME) { armRegrowT[k] = 0f; newArm(g, k); }
                    continue;
                }
                a.x = x; a.y = y; a.w = w; a.h = h;  // (for drawing / culling)
                a.updateArm(g, x + armSpec[k][0], y + armSpec[k][1], dt);
                allOut &= a.armGrown();
            }
            if (phase == PHASE_GROWING && allOut) phase = PHASE_CHASE;
        }
        shoot(g, dt);
    }

    /** The boss's own shots (by kind; stages 3-7 use the default pattern for now). */
    private void shoot(GameScreen g, float dt) {
        shotT -= dt;
        float cx = x + w / 2f, cy = y + h / 2f, front = x + 12f;   // shots leave from the front (left) side
        if (hasEye) { front = x + eyeX + eyeW / 2f; cy = y + eyeY + eyeH / 2f; }   // ...or from the eye
        switch (kind) {
            case 0: // triple aimed spread
                if (shotT <= 0f) {
                    shotT = 1.15f;
                    for (int i = -1; i <= 1; i++) g.aimedShot(front, cy, 130f, i * 0.32f);
                }
                break;
            case 1: // rotating ring + aimed shot
                if (shotT <= 0f) {
                    shotT = 1.5f;
                    for (int i = 0; i < 10; i++) g.enemyBullet(cx, cy, i * MathUtils.PI2 / 10f + spin, 100f);
                    spin += 0.17f;
                    g.aimedShot(cx, cy, 150f, 0f);
                }
                break;
            default: { // sweeping stream + occasional aimed burst (forward: left, or down on a vertical stage)
                spin += dt * 3.2f;
                boolean down = g.level.vertical;
                float sx = down ? cx : front - 4f, sy = down ? y + 8f : cy;
                if (shotT <= 0f) {
                    shotT = 0.11f;
                    g.enemyBullet(sx, sy, (down ? -MathUtils.HALF_PI : MathUtils.PI) + MathUtils.sin(spin) * 0.9f, 140f);
                    if (++burst % 20 == 0) {
                        for (int i = -2; i <= 2; i++) g.aimedShot(sx, sy, 150f, i * 0.2f);
                    }
                }
                break;
            }
        }
    }
}
