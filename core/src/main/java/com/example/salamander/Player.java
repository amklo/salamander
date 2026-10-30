package com.example.salamander;

/** One ship plus its power-up state, lives and score. Position is the bottom-left corner in world pixels. */
public class Player {
    public static final float W = 32, H = 16;
    /** Collision box, centred on the sprite. */
    public static final float HIT_W = 4, HIT_H = 4;
    public static final float HIT_OX = (W - HIT_W) / 2f, HIT_OY = (H - HIT_H) / 2f;
    /** Where the ship's picture is inside its 32x16 frame (x 7..22, y 2..13 from the bottom): it may touch the screen edges. */
    public static final float ART_L = 7, ART_R = 23, ART_B = 2, ART_T = 14;
    public static final int START_LIVES = 3, START_LIVES_EASY = 5;

    /** Ships to start (or continue) with: 5 on EASY, 3 on NORMAL. */
    public static int startLives(boolean easy) { return easy ? START_LIVES_EASY : START_LIVES; }

    public float hitX() { return x + HIT_OX; }
    public float hitY() { return y + HIT_OY; }

    /** 0 = player 1 (keyboard + pad #1), 1 = player 2 (pad #2). */
    public final int index;
    public int lives = START_LIVES;  // spare ships; below 0 = out of the game until they press fire to continue
    public int score;
    public boolean out;              // no ships left

    public float x, y, fireCd, missCd, invuln;
    public float respawnT;           // > 0 while waiting to re-enter after being shot down (co-op)
    public float ffieldHitT;         // > 0 just after the F.FIELD bubble took a hit: the bubble blinks, not the ship
    public float ghostT;             // > 0 just after (re)entering: terrain and teeth can't hurt the ship
    public float trailAcc;
    public boolean aiming;           // holding AIM with DOUBLE equipped
    public int tilt;                 // 0 level, 1 up, 2 down (sprite frame); vertical stages: 0 level, TILT_LEFT, TILT_RIGHT
    public static final int TILT_LEFT = 1, TILT_RIGHT = 2;
    public boolean dead;

    // Power-up meter state
    public int meter;                // highlighted slot 1..6 (0 = none)
    public int speedLvl;             // 0..MAX_SPEED
    /** SPEED steps through 1..MAX_SPEED, then back to 0 (the starting speed). */
    public static final int MAX_SPEED = 5;

    /** The speed level the SPEED slot gives next: one faster, or back to 0 at top speed. */
    public int nextSpeed() { return speedLvl >= MAX_SPEED ? 0 : speedLvl + 1; }

    /** What the power meter's name box shows for a power-up (SPEED shows the level it gives: SPEED#1 .. SPEED#0). */
    public String powerLabel(int power) { return power == SPEED ? "SPEED#" + nextSpeed() : POWER_NAMES[power]; }
    public boolean missile;          // MISSILE, or 2-WAY / HOMING on ships whose meter has it (see twoWay(), homing())
    public int weapon;               // W_NORMAL, W_DOUBLE, W_LASER or W_RIPPLE
    /** LASER / RIPPLE aim (AIM held with either): 0 = straight ahead, then every 45 deg counter-clockwise, like doubleDir. */
    public int laserDir;
    public int doubleDir = 1;        // DOUBLE extra shot direction: 0 = right, then every 45 deg counter-clockwise (1 = up-right)
    public static final int MAX_OPTIONS = 4;
    /**
     * Option formation (Albert, 2026-10-28; GameScreen.updateOptions): OPT_TRAIL = the normal trail behind the ship,
     * OPT_ORBIT = circling the ship (R2), OPT_V = a V formation (L1), OPT_FREEZE = held where they were around the ship,
     * moving with it 1 to 1 (L2). optSX / optSY = where each option is (path-relative, like the trail); optLock = it has
     * reached its place in the formation (until then it glides there); optFX / optFY = OPT_FREEZE's offsets from the
     * ship's middle; optKnown = how many options have a place yet (a new one starts in its place).
     */
    public static final int OPT_TRAIL = 0, OPT_ORBIT = 1, OPT_V = 2, OPT_FREEZE = 3;
    public int optMode, optKnown;
    public boolean optReady;
    public float orbitAng;
    public final float[] optSX = new float[MAX_OPTIONS], optSY = new float[MAX_OPTIONS], optFX = new float[MAX_OPTIONS], optFY = new float[MAX_OPTIONS];
    public final boolean[] optLock = new boolean[MAX_OPTIONS];
    public int options;              // 0..MAX_OPTIONS
    public int ffield;               // F.FIELD (force field): the bubble around the ship, hits it can still take (0 = none)
    /** Hits a fresh F.FIELD bubble takes. */
    public static final int FFIELD_HITS = 5;
    public int shield;               // SHIELD: the guard in front of the nose, shots it can still block (0 = none)
    public float shieldHitT;         // > 0 just after the shield blocked a shot (it flickers)
    /** Shots a fresh SHIELD blocks before it is used up. */
    public static final int SHIELD_HITS = 10;
    /** With this many hits left or fewer, the SHIELD turns red (sprites/shield_red.png). */
    public static final int SHIELD_LOW = 3;

    // Ring buffer of screen-relative positions; options replay it with a delay
    private final float[] tx = new float[64], ty = new float[64];
    private int head;

    // ---- ship types: each one orders the power-up meter differently
    /** Power-ups, as stored in {@link #slots}. */
    public static final int SPEED = 1, MISSILE = 2, DOUBLE = 3, LASER = 4, OPTION = 5, FFIELD = 6, RIPPLE = 7, TWO_WAY = 8, EXTRA = 9, SHIELD = 10, HOMING = 11;
    /** Number of slots in the power-up meter (the 7th is "!" on every ship). */
    public static final int SLOT_COUNT = 7;
    public static final int MAX_LIVES = 9;
    public static final String[] POWER_NAMES = {"", "SPEED", "MISSILE", "DOUBLE", "LASER", "OPTION", "F.FIELD", "RIPPLE", "2-WAY", "!", "SHIELD", "HOMING"};
    /** Values of {@link #weapon}. */
    public static final int W_NORMAL = 0, W_DOUBLE = 1, W_LASER = 2, W_RIPPLE = 3;
    public static final int SHIP_TYPES = 4;
    /**
     * Meter order per ship type (index 0 = type 1). Slot 6 is the "?" slot: it holds the guard the player picks
     * after the ship type (SHIELD or F.FIELD; the one listed here is offered first).
     */
    private static final int[][] TYPE_SLOTS = {
            {SPEED, MISSILE, DOUBLE, LASER, OPTION, FFIELD, EXTRA},   // type 1
            {SPEED, TWO_WAY, DOUBLE, RIPPLE, OPTION, FFIELD, EXTRA},  // type 2
            {SPEED, HOMING, DOUBLE, RIPPLE, OPTION, FFIELD, EXTRA},   // type 3
            {SPEED, TWO_WAY, DOUBLE, LASER, OPTION, SHIELD, EXTRA},   // type 4
    };
    /** The classic order, for showing which slots a type moved. */
    public static final int[] CLASSIC_SLOTS = {SPEED, MISSILE, DOUBLE, LASER, OPTION, FFIELD, EXTRA};

    /** Meter order for ship type 0..SHIP_TYPES-1 with its own guard. */
    public static int[] slotsFor(int shipType) { return TYPE_SLOTS[shipType]; }

    /** The guard (SHIELD or F.FIELD) a ship type has in its meter unless the player picks the other one. */
    public static int defaultGuard(int shipType) { return SHIELD; }   // every type offers SHIELD first

    /** Meter order for a ship type, with the guard slot holding the chosen guard (SHIELD or FFIELD). */
    public static int[] slotsFor(int shipType, int guard) {
        int[] s = TYPE_SLOTS[shipType].clone();
        for (int i = 0; i < s.length; i++) if (s[i] == SHIELD || s[i] == FFIELD) s[i] = guard;
        return s;
    }

    /** 0..SHIP_TYPES-1 (shown to the player as TYPE 1..4). */
    public final int shipType;
    /** The guard picked after the ship type: SHIELD (in front of the nose) or FFIELD (bubble). */
    public final int guard;
    /** slots[i] = the power-up in meter slot i+1. */
    public final int[] slots;

    public Player(int index, int shipType) { this(index, shipType, defaultGuard(shipType)); }

    public Player(int index, int shipType, int guard) {
        this.index = index;
        this.shipType = shipType;
        this.guard = guard;
        this.slots = slotsFor(shipType, guard);
    }

    /** This ship's missiles are 2-WAY (one up, one down) instead of the normal downward missile. */
    public boolean twoWay() { return slotOf(TWO_WAY) > 0 || homing(); }

    /** This ship's missiles are HOMING: fired like 2-WAY, but they chase any enemy on screen and burst on contact. */
    public boolean homing() { return slotOf(HOMING) > 0; }

    /** The power-up the highlighted meter slot gives (0 = none highlighted). */
    public int selectedPower() { return meter == 0 ? 0 : slots[meter - 1]; }

    /** Meter slot (1..6) that holds this power-up. */
    public int slotOf(int power) {
        for (int i = 0; i < slots.length; i++) if (slots[i] == power) return i + 1;
        return 0;
    }

    /** SHIELD: the guard sits right in front of the ship's nose (8x16; 16x8 above the nose on a vertical stage). */
    public static final float SHIELD_W = 8, SHIELD_H = 16;
    // Vertical: the turned art fills only 15 of its 16 columns, so it starts at the ship's left edge (VART_L)
    // to centre it on the nose (x + 16), not one pixel to the left of it.
    public float shieldX() { return vertical ? x + VART_L : x + ART_R + 1f; }
    public float shieldY() { return vertical ? y + VART_T + 1f : y; }
    public static float shieldW() { return vertical ? SHIELD_H : SHIELD_W; }
    public static float shieldH() { return vertical ? SHIELD_W : SHIELD_H; }

    /**
     * The stage being played is vertical (set by GameScreen): the ship points up. Its picture
     * (sprites/player_blue_vertical.png, 15x15) sits at x 9..23, y 0..14 of the same 32x16 box, so the hit box
     * stays in the middle of the picture.
     */
    public static boolean vertical;
    public static final float VART_L = 9, VART_R = 24, VART_B = 0, VART_T = 15;

    /** In play right now (joined, has ships, not exploding). */
    public boolean alive() { return !dead && !out; }

    /** Debug loadout (toggled with D on the title screen): 4 options, missiles, F.FIELD / SHIELD, DOUBLE, LASER highlighted. */
    public void applyDebugLoadout() {
        options = MAX_OPTIONS;
        missile = true;
        if (slotOf(SHIELD) > 0) shield = SHIELD_HITS; else ffield = FFIELD_HITS;
        weapon = W_DOUBLE;
        meter = slotOf(LASER) > 0 ? slotOf(LASER) : slotOf(RIPPLE);   // LASER / RIPPLE highlighted: one press switches to it
    }

    public void resetPowerups() {
        meter = 0; speedLvl = 0; missile = false; weapon = 0; options = 0; ffield = 0; shield = 0; shieldHitT = 0f; doubleDir = 1; laserDir = 0;
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
