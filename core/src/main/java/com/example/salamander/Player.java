package com.example.salamander;

/** One ship plus its power-up state, lives and score. Position is the bottom-left corner in world pixels. */
public class Player {
    public static final float W = 32, H = 16;
    /** Collision box, centred on the sprite. */
    public static final float HIT_W = 4, HIT_H = 4;
    public static final float HIT_OX = (W - HIT_W) / 2f, HIT_OY = (H - HIT_H) / 2f;
    public static final int START_LIVES = 3;

    public float hitX() { return x + HIT_OX; }
    public float hitY() { return y + HIT_OY; }

    /** 0 = player 1 (keyboard + pad #1), 1 = player 2 (pad #2). */
    public final int index;
    public int lives = START_LIVES;  // spare ships; below 0 = out of the game until they press fire to continue
    public int score;
    public boolean out;              // no ships left

    public float x, y, fireCd, missCd, invuln;
    public float respawnT;           // > 0 while waiting to re-enter after being shot down (co-op)
    public float trailAcc;
    public boolean aiming;           // holding AIM with DOUBLE equipped
    public int tilt;                 // 0 level, 1 up, 2 down (sprite frame)
    public boolean dead;

    // Power-up meter state
    public int meter;                // highlighted slot 1..6 (0 = none)
    public int speedLvl;             // 0..5
    public boolean missile;
    public int weapon;               // 0 normal, 1 double, 2 laser
    public int doubleDir = 1;        // DOUBLE extra shot direction: 0 = right, then every 45 deg counter-clockwise (1 = up-right)
    public static final int MAX_OPTIONS = 4;
    public int options;              // 0..MAX_OPTIONS
    public int shield;               // remaining hits

    // Ring buffer of screen-relative positions; options replay it with a delay
    private final float[] tx = new float[64], ty = new float[64];
    private int head;

    public Player(int index) { this.index = index; }

    /** In play right now (joined, has ships, not exploding). */
    public boolean alive() { return !dead && !out; }

    /** Debug loadout (toggled with D on the title screen): 4 options, missiles, shield, DOUBLE, LASER highlighted. */
    public void applyDebugLoadout() {
        options = MAX_OPTIONS;
        missile = true;
        shield = 5;
        weapon = 1;
        meter = 4;                   // LASER slot highlighted: press power-up to switch straight to it
    }

    public void resetPowerups() {
        meter = 0; speedLvl = 0; missile = false; weapon = 0; options = 0; shield = 0; doubleDir = 1;
    }

    public void fillTrail(float sx, float sy) {
        for (int i = 0; i < 64; i++) { tx[i] = sx; ty[i] = sy; }
    }

    public void record(float sx, float sy) {
        head = (head + 1) & 63;
        tx[head] = sx;
        ty[head] = sy;
    }

    public float trailX(int delay) { return tx[(head - delay) & 63]; }
    public float trailY(int delay) { return ty[(head - delay) & 63]; }
}
