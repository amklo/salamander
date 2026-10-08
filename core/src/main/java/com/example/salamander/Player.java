package com.example.salamander;

/** One ship plus its power-up state, lives and score. Position is the bottom-left corner in world pixels. */
public class Player {
    public static final float W = 32, H = 16;
    /** Collision box, centred on the sprite. */
    public static final float HIT_W = 4, HIT_H = 4;
    public static final float HIT_OX = (W - HIT_W) / 2f, HIT_OY = (H - HIT_H) / 2f;
    /** Where the ship's picture is inside its 32x16 frame (x 7..22, y 2..13 from the bottom): it may touch the screen edges. */
    public static final float ART_L = 7, ART_R = 23, ART_B = 2, ART_T = 14;
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
    public float shieldHitT;         // > 0 just after the shield took a hit: the shield blinks, not the ship
    public float ghostT;             // > 0 just after (re)entering: terrain, teeth and crushers can't hurt the ship
    public float trailAcc;
    public boolean aiming;           // holding AIM with DOUBLE equipped
    public int tilt;                 // 0 level, 1 up, 2 down (sprite frame)
    public boolean dead;

    // Power-up meter state
    public int meter;                // highlighted slot 1..6 (0 = none)
    public int speedLvl;             // 0..5
    public boolean missile;          // MISSILE, or 2-WAY on ships whose meter has it (see twoWay())
    public int weapon;               // W_NORMAL, W_DOUBLE, W_LASER or W_RIPPLE
    public int doubleDir = 1;        // DOUBLE extra shot direction: 0 = right, then every 45 deg counter-clockwise (1 = up-right)
    public static final int MAX_OPTIONS = 4;
    public int options;              // 0..MAX_OPTIONS
    public int shield;               // remaining hits
    public int ffield;               // F.FIELD: enemy shots it can still block (0 = none)
    public float ffieldHitT;         // > 0 just after the force field blocked a shot (it flickers)
    /** Shots an F.FIELD blocks before it is used up. */
    public static final int FFIELD_HITS = 10;
    /** With this many hits left or fewer, the F.FIELD turns red (sprites/ffield_red.png). */
    public static final int FFIELD_LOW = 3;

    // Ring buffer of screen-relative positions; options replay it with a delay
    private final float[] tx = new float[64], ty = new float[64];
    private int head;

    // ---- ship types: each one orders the power-up meter differently
    /** Power-ups, as stored in {@link #slots}. */
    public static final int SPEED = 1, MISSILE = 2, DOUBLE = 3, LASER = 4, OPTION = 5, SHIELD = 6, RIPPLE = 7, TWO_WAY = 8, EXTRA = 9, FFIELD = 10;
    /** Number of slots in the power-up meter (the 7th is "!" on every ship). */
    public static final int SLOT_COUNT = 7;
    public static final int MAX_LIVES = 9;
    public static final String[] POWER_NAMES = {"", "SPEED", "MISSILE", "DOUBLE", "LASER", "OPTION", "SHIELD", "RIPPLE", "2-WAY", "!", "F.FIELD"};
    /** Values of {@link #weapon}. */
    public static final int W_NORMAL = 0, W_DOUBLE = 1, W_LASER = 2, W_RIPPLE = 3;
    public static final int SHIP_TYPES = 4;
    /** Meter order per ship type (index 0 = type 1). Each type swaps two slots of the classic order. */
    private static final int[][] TYPE_SLOTS = {
            {SPEED, MISSILE, LASER, DOUBLE, OPTION, SHIELD, EXTRA},   // type 1: DOUBLE <-> LASER
            {SPEED, OPTION, RIPPLE, LASER, TWO_WAY, SHIELD, EXTRA},   // type 2: MISSILE <-> OPTION, RIPPLE instead of DOUBLE, 2-WAY instead of MISSILE
            {SPEED, LASER, DOUBLE, MISSILE, OPTION, SHIELD, EXTRA},   // type 3: MISSILE <-> LASER
            {SPEED, MISSILE, FFIELD, LASER, OPTION, RIPPLE, EXTRA},   // type 4: DOUBLE <-> SHIELD, RIPPLE instead of DOUBLE, F.FIELD instead of SHIELD
    };
    /** The classic order, for showing which slots a type moved. */
    public static final int[] CLASSIC_SLOTS = {SPEED, MISSILE, DOUBLE, LASER, OPTION, SHIELD, EXTRA};

    /** Meter order for ship type 0..SHIP_TYPES-1. */
    public static int[] slotsFor(int shipType) { return TYPE_SLOTS[shipType]; }

    /** 0..SHIP_TYPES-1 (shown to the player as TYPE 1..4). */
    public final int shipType;
    /** slots[i] = the power-up in meter slot i+1. */
    public final int[] slots;

    public Player(int index, int shipType) {
        this.index = index;
        this.shipType = shipType;
        this.slots = TYPE_SLOTS[shipType];
    }

    /** This ship's missiles are 2-WAY (one up, one down) instead of the normal downward missile. */
    public boolean twoWay() { return slotOf(TWO_WAY) > 0; }

    /** The power-up the highlighted meter slot gives (0 = none highlighted). */
    public int selectedPower() { return meter == 0 ? 0 : slots[meter - 1]; }

    /** Meter slot (1..6) that holds this power-up. */
    public int slotOf(int power) {
        for (int i = 0; i < slots.length; i++) if (slots[i] == power) return i + 1;
        return 0;
    }

    /** F.FIELD: the force field sits right in front of the ship's nose (8x16). */
    public static final float FFIELD_W = 8, FFIELD_H = 16;
    public float ffieldX() { return x + ART_R + 1f; }
    public float ffieldY() { return y; }

    /** In play right now (joined, has ships, not exploding). */
    public boolean alive() { return !dead && !out; }

    /** Debug loadout (toggled with D on the title screen): 4 options, missiles, shield, DOUBLE, LASER highlighted. */
    public void applyDebugLoadout() {
        options = MAX_OPTIONS;
        missile = true;
        if (slotOf(FFIELD) > 0) ffield = FFIELD_HITS; else shield = 5;
        weapon = slotOf(DOUBLE) > 0 ? W_DOUBLE : W_RIPPLE;   // ripple ships get RIPPLE instead
        meter = slotOf(LASER);       // LASER slot highlighted: press power-up to switch straight to it
    }

    public void resetPowerups() {
        meter = 0; speedLvl = 0; missile = false; weapon = 0; options = 0; shield = 0; ffield = 0; ffieldHitT = 0f; doubleDir = 1;
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
