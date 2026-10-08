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

    // ---- arms (stage 1): in phase 1 the boss has none; below half HP (phase 2) two chains of 4 blue balls and a claw
    // grow out of it, and once they are fully out (phase 3) the boss chases the ships. Shooting any blue ball of an arm
    // damages it (Hazard.ARM_HP, like the stage arms); a destroyed arm grows back out after ARM_REGROW_TIME seconds.
    public static final int PHASE_NORMAL = 0, PHASE_GROWING = 1, PHASE_CHASE = 2;
    public int phase = PHASE_NORMAL;
    /** Per arm: anchor (centre of the first ball) relative to the boss's bottom-left, direction, ball spacing. */
    public float[][] armSpec;
    public Hazard[] arms;
    public static final int ARM_BALLS = 5;           // 4 blue balls + the claw
    /** Arms grow below this share of HP; then the boss chases the ships at this speed (px/s). */
    public static final float ARM_PHASE_HP = 0.5f, CHASE_SPEED = 32f;
    /** Seconds until a destroyed arm starts growing back. */
    public static final float ARM_REGROW_TIME = 2f;
    private float[] armRegrowT;

    public final int kind;
    public float x, y, t, hp, maxHp, flash, shotT, spin, dieT, expT;
    public boolean entering = true, dying;
    private int burst;

    public Boss(int kind, float x, int hp, float w, float h) {
        this.kind = kind; this.x = x; this.hp = hp; this.maxHp = hp;
        this.w = w; this.h = h;
        this.y = SalamanderGame.H / 2f - h / 2f;
        this.shotT = 1.5f;
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
        a.hp = Hazard.ARM_HP;
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
            default: // sweeping stream + occasional aimed burst
                spin += dt * 3.2f;
                if (shotT <= 0f) {
                    shotT = 0.11f;
                    g.enemyBullet(front - 4f, cy, MathUtils.PI + MathUtils.sin(spin) * 0.9f, 140f);
                    if (++burst % 20 == 0) {
                        for (int i = -2; i <= 2; i++) g.aimedShot(front - 4f, cy, 150f, i * 0.2f);
                    }
                }
                break;
        }
    }
}
