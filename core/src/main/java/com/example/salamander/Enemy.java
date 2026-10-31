package com.example.salamander;

import com.badlogic.gdx.math.MathUtils;

public class Enemy {
    public enum Type { WALKER, TURRET_FLOOR, TURRET_CEIL, FROST, LINK, AMOEBA, RUGAL, TURRET_WALL, LEGACY, GUN_HO, OMEN, LASER_GUARD, DOME_FLYER, POSSESSED, WHIPLASH, BATTLEAXE, CHAIN, ABERRANT, NEST, CREEPING_DEATH, UG, GAMMA, BLUE_DOME, GREEN_FLYER, LASER_BATTERY, GREMLIN, SEED, FLYING_MINE,
        EXODUS, ARTILLERY, BLUE_ASTEROID, MOAI, ORB, HEATHEN, BLOCK_SMALL, BLOCK_BIG, RAGE, LED_ZEP, LASER_GUN, GREEN_MINE, OPTION_EATER }

    /** Red "carrier" waves drop a power-up capsule when every member is shot down. */
    public static class Group {
        public int size, killed;
        public boolean carrier, lost;
    }

    public final Type type;
    public float x, y, w = 16, h = 16, vx, vy, baseY, t, shootT, phase;
    /**
     * Vertical stages: the enemy flies the vertical version of its path (a fly-by Legacy weaves down a column around
     * baseX from the top of the screen; Celtic Frost flies its "Z" turned a quarter: in from the top, down, diagonally back up,
     * down and out at the bottom).
     */
    public boolean vertical;
    public float baseX;
    public int hp, score;
    public boolean dead;
    /** Walkers: FloorCrawler.LEVEL / CLIMBING / DROPPING (drawn tilted 45 degrees). */
    public int climb;
    /** Walkers: hangs upside down from the ceiling. */
    public boolean ceiling;
    /** Walkers: seconds spent chasing since it came on screen (-1 = not on screen yet). */
    public float chaseT = -1f;
    /** Walker speeds once on screen, relative to the screen (like the ships): independent of the scrolling. */
    public static final float CHASE_TIME = 10f, CHASE_SPEED = 60f, LEAVE_SPEED = 30f;
    /** Walkers stand still this long (aiming) before every shot. */
    public static final float WALKER_AIM_TIME = 1f;
    /**
     * MSX style (stage 1 recording): while chasing, a walker now and then leaps toward the ship: it jumps off its
     * floor (ceiling walkers: drops off the ceiling) about half a screen high, drifting toward the ship, and comes back
     * down onto the floor it lands on (gravity pulls it back to the side it walks on; a ceiling stops it on the way up).
     * Jump height (px), sideways speed (px/s, relative to the screen), gravity (px/s^2) and the wait between leaps (s).
     */
    public static final float WALKER_JUMP_MIN_H = 48f, WALKER_JUMP_MAX_H = 84f, WALKER_JUMP_DRIFT = 70f,
            WALKER_JUMP_GRAVITY = 300f, WALKER_JUMP_WAIT_MIN = 1.5f, WALKER_JUMP_WAIT_MAX = 3.5f;
    private boolean jumping;
    private float jumpWait = -1f, jumpV;

    /**
     * Kierke walkers ("leapers"; the stage 1 bio walkers, red and cyan), measured from the MSX recording (77-126 s):
     * - They come in pairs from a screen edge (LEAP_IN_LEFT / LEAP_IN_RIGHT) at mid height (one heading for the floor, one for the ceiling), moving
     *   inward at LEAP_IN_SPEED px/s relative to the screen. The ceiling one "falls" up at once; the floor one glides
     *   level for LEAP_GLIDE_MIN..MAX s first. Both fall with LEAP_GRAVITY (px/s^2) and land on their surface.
     * - Once down, a walker farther than LEAP_APPROACH_DIST px (sideways) from the nearest ship first walks toward it
     *   at LEAP_APPROACH_SPEED (relative to the screen) until it is that close (the recording: those that came in on the
     *   right walked left to the middle of the screen). This only happens once.
     * - Then, over and over: it walks away from the ship at LEAP_WALK_MIN..MAX px/s (relative to the screen) for
     *   LEAP_WALK_TIME_MIN..MAX s, and hops toward it: LEAP_HOP_MIN_H..MAX_H px high (higher when the ship is farther up
     *   / down: up to about its height), drifting LEAP_HOP_DRIFT px/s sideways. Now and then (LEAP_HOP_AWAY) it hops
     *   the other way. A hop that reaches the ceiling (floor) grabs it: it walks on there from then on.
     * - Walking off a screen edge, it is gone. They don't shoot (none did in the recording).
     * The recording's ship stayed at the left edge, so "toward the ship" and "to the left" look the same there.
     */
    public static final float LEAP_IN_SPEED = 40f, LEAP_GRAVITY = 88f, LEAP_GLIDE_MIN = 0.2f, LEAP_GLIDE_MAX = 0.8f,
            LEAP_APPROACH_SPEED = 40f, LEAP_APPROACH_DIST = 112f, LEAP_WALK_MIN = 16f, LEAP_WALK_MAX = 40f,
            LEAP_WALK_TIME_MIN = 0.9f, LEAP_WALK_TIME_MAX = 3f, LEAP_HOP_MIN_H = 32f, LEAP_HOP_MAX_H = 56f,
            LEAP_HOP_DRIFT = 55f, LEAP_HOP_AWAY = 1f / 6f;
    /** Kierke leaper: where it appears (screen x): 2 px off the left edge, or with its last 12 px on the right edge. */
    public static final float LEAP_IN_LEFT = 2f, LEAP_IN_RIGHT = SalamanderGame.W - 12f;
    private static final int LEAP_IN = 1, LEAP_APPROACH = 2, LEAP_WALK = 3, LEAP_HOP = 4;
    /** Kierke leaper: LEAP_* state (0 = an ordinary walker). */
    private int leapState;
    /** Kierke leaper: entry direction (1 = in from the left), timer, sideways direction / speed of the current move. */
    private int leapDir, leapSide;
    private float leapT, leapSpeed;
    private boolean leapSeen;
    private final float[] pos = new float[2];
    public Group group;
    /**
     * Turrets: where the barrel points. -1 = diagonally left, 0 = straight up (down on the ceiling), 1 = diagonally
     * right. The bio turret sprites have a "left" and an "up" frame; right is the left frame mirrored.
     */
    public int aim = -1;

    /**
     * Stage 2 wall turret (16x16, sprites "wall turret red/green.png": sideways frame and diagonal frame). It is
     * mounted on a rock wall to its left (wallRight: to its right, so it faces left) and turns its barrel toward the
     * nearest ship: aim = WALL_SIDE (straight out), WALL_DOWN or WALL_UP (diagonally). Red ones carry a capsule.
     */
    public boolean wallRight;
    public static final int WALL_SIDE = 0, WALL_DOWN = 1, WALL_UP = 2;

    /**
     * Celtic Frost: flies a zigzag across the screen, independent of the scrolling (as on the MSX, from the stage 1
     * recording): in from the right edge low on the screen, left along the bottom bar, diagonally up and back to the
     * right to the middle line, left along the middle line, diagonally up and back again to the top bar, then right
     * along the top bar and out the right edge. "upper" ones fly the same path mirrored top to bottom. The six of a
     * group follow each other on the same path, FROST_GAP seconds apart (t starts negative for the ones still waiting).
     * Vertical stages fly the stage 2 recording's path (see FROST_V_TURN).
     */
    /** Missing Link: drifts left on its own (one at a time), bobbing up and down, and shoots at the ships. */
    public static final float LINK_SPEED = 40f, LINK_BOB = 50f, LINK_BOB_SPEED = 2.6f, LINK_FPS = 12f;
    /**
     * MSX Missing Link (Kierke; Tiled "swing" > 0), from the recording: it comes in at the right edge (LINK_MSX_IN px of
     * it showing) at the top (or, "up", the bottom) of its swing and drifts left at LINK_MSX_SPEED px/s relative to the
     * screen, swinging linkSwing px up and down, at up to LINK_MSX_VMAX px/s (a bigger swing takes longer; the recording's
     * 72 px take ~2.4 s; in the cave the bottom of the swing dips just off the screen). The picture follows the swing: frame 0 at
     * the top and bottom, 1, 2, then 3 half-way. LINK_MSX_FIRE_MIN..MAX s after it appears it fires linkBurst aimed shots
     * in a line (LINK_MSX_BURST_GAP s apart, LINK_MSX_SHOT px/s relative to the screen), once.
     */
    public static final float LINK_MSX_SPEED = 40f, LINK_MSX_VMAX = 94f, LINK_MSX_IN = 14f, LINK_MSX_FIRE_MIN = 1.3f,
            LINK_MSX_FIRE_MAX = 2f, LINK_MSX_BURST_GAP = 0.17f, LINK_MSX_SHOT = 60f;
    /** MSX Missing Link: full swing height (0 = the old link), starts at the bottom, shots in its burst. */
    public float linkSwing;
    public boolean linkUp;
    public int linkBurst = 1;
    private float linkFireAt = -1f;
    private int linkFired;

    /**
     * Green mine (Kierke opening, Albert's sprites/green_mine.png: 2 frames of 16x16, swapped every GMINE_FRAME s). From
     * the recording: it comes in at the right edge (GMINE_IN px showing) and homes in on the nearest ship at GMINE_SPEED
     * px/s relative to the screen, turning toward it at up to GMINE_TURN rad/s (its heading stayed within a few degrees
     * of the ship's direction). It passes through rock and doesn't shoot.
     * Vertical stages (Op 4 recording, 41-55 s): the same, but it comes in at the top edge (or, gmFromBottom, the
     * bottom edge) at its object's x, and moves relative to the screen.
     */
    public boolean gmFromBottom;
    private float gmSx = Float.NaN, gmSy;
    public static final float GMINE_SPEED = 33f, GMINE_TURN = 3f, GMINE_FRAME = 0.21f, GMINE_IN = 11f;
    private float gmAng = Float.NaN;

    /**
     * Amoeba: spat out by the stage 1 towers. It is thrown out of the tip, slows down, then just floats around in
     * the cave (it stays in place while the screen scrolls), drifting lazily (through rock too), blocking the way.
     */
    public static final float AMOEBA_DRAG = 2.2f, AMOEBA_DRIFT = 14f, AMOEBA_FPS = 6f;

    /**
     * Rugal: comes in from the right edge and flies left across the screen, steering up / down toward the
     * nearest ship. Speeds are relative to the screen. It only shoots once it has flown past a ship (the ship is to its right).
     * Its picture tilts (rugal.png frame 1 = down, 2 = up) while it climbs / dives at 45 degrees or more.
     */
    public static final float RUGAL_SPEED = 60f, RUGAL_CLIMB = 90f, RUGAL_SHOT_INTERVAL = 1.6f;

    /**
     * Legacy (stage 2): weaves along a sine path (fly-by) or hovers, and only shoots once it has flown past a ship (vertical stage: once
     * it is below the ship; side-scrolling: once it is left of it). sprites/Legacy.png: 4 frames of 16x16 played
     * forward, then backward (0 1 2 3 2 1 ...), drawn green.
     */
    public static final float LEGACY_FPS = 10f, LEGACY_SHOT_INTERVAL = 1.6f;
    /**
     * Hovering Legacy (stage 2 recording): it hangs in the world (scrolls with the scenery) and drifts or darts
     * sideways toward the nearest ship (picking a new speed every LEGACY_PICK_MIN..MAX s, up to LEGACY_DART px/s);
     * when it is above (vertical stage) / beside the ship it may dive toward it at LEGACY_DIVE px/s for a moment. It
     * doesn't shoot. sprites/legacy.png: 7 frames of 16x16, frame 3 = straight on, 0..2 leaning left, 4..6 right;
     * the lean follows its sideways speed (LEGACY_LEAN px/s per frame). Drawn white, tinted with the MSX colour
     * cycle (see GameScreen.LEGACY_COLOURS).
     */
    public boolean hover;

    /**
     * Possessed (stage 2 recording): a group comes in from the top edge one after another (POSSESSED_GAP s apart),
     * each flying to its own spot of a small cluster near the top of the screen, hovers there POSSESSED_HOVER s
     * shooting aimed shots at the nearest ship (every POSSESSED_SHOT..+0.3 s), then flies back up and leaves through
     * the top. Screen-relative (it keeps its place on the screen while it scrolls). Horizontal stages: the same,
     * turned (in from the right, out to the right). Red or cyan (possRed); sprites/possessed.png is white with a red
     * eye: the body is tinted, the eye stays red.
     */
    public static final float POSSESSED_GAP = 0.3f, POSSESSED_SPEED = 60f, POSSESSED_LEAVE = 70f, POSSESSED_HOVER = 2.2f,
            POSSESSED_SHOT = 0.6f, POSSESSED_SHOT_SPEED = 110f;
    /** Cluster spots (across, down from the top edge), member 0 first. */
    private static final float[][] POSSESSED_SPOTS = {{0, 12}, {-15, 26}, {15, 26}, {-30, 38}, {0, 40}, {30, 38}};
    public boolean possRed;
    /**
     * A Possessed "row" group (Eioneus recording, Tiled property row): member k comes in at the top edge
     * POSS_ROW_IN px beside its place, flies diagonally (45°, POSS_ROW_SPEED px/s on each axis) down to its place in
     * a row POSS_ROW_DOWN px below the top edge (the members POSS_ROW_GAP px apart, possX = the first one's place),
     * stops there for POSS_ROW_HOLD s shooting, then flies on diagonally up and out through the top edge. The row
     * runs toward the side the nearest ship was on when the group came (possSide).
     * Horizontal stages (Lavinia recording): the same, also in from the top edge; the row is possRowDown px below the
     * top edge, its members possRowGap px apart, and its side can be set in Tiled (property "side").
     */
    public static final float POSS_ROW_IN = 53f, POSS_ROW_DOWN = 45f, POSS_ROW_GAP = 16f, POSS_ROW_SPEED = 80f,
            POSS_ROW_HOLD = 0.6f, POSS_ROW_DELAY = 0.25f;
    public boolean possRow;
    /** Row groups: how far below the top edge the row is (centre), and how far apart its places are. */
    public float possRowDown = POSS_ROW_DOWN, possRowGap = POSS_ROW_GAP;
    public float possSide = -1f;   // row groups: -1 = toward the left, +1 = toward the right
    private float possDown = Float.NaN;
    public int possMember;
    public float possX;            // the cluster's centre across the screen (px)
    private int possState;         // 0 = coming in, 1 = hovering, 2 = leaving
    private float possTimer;

    private void updatePossessed(GameScreen g, float dt) {
        float W = SalamanderGame.W, H = SalamanderGame.H;
        if (possRow) {                                         // Eioneus / Lavinia: in diagonally, a moment in the row, out again
            float place = possX + possSide * possRowGap * possMember, down = possRowDown;
            if (Float.isNaN(possDown)) { possDown = -h / 2f; possTimer = 0f; }
            if (t < 0f) { setScreen(g, place - possSide * (down + h / 2f), H + h); return; }   // still waiting off screen
            float step = POSS_ROW_SPEED * dt;
            float across = place - possSide * (down - possDown) ;   // on its diagonal
            if (possState == 0) {
                possDown += step;
                if (possDown >= down) { possDown = down; possState = 1; possTimer = POSS_ROW_HOLD; shootT = MathUtils.random(0.1f, 0.4f); }
                across = place - possSide * (down - possDown);
            } else if (possState == 1) {
                across = place;
                possTimer -= dt;
                shootT -= dt;
                if (shootT <= 0f) {
                    shootT = POSSESSED_SHOT + MathUtils.random(0f, 0.3f);
                    if (canShoot(g)) g.aimedShot(place + g.scrollX, H - possDown + g.scrollY, POSSESSED_SHOT_SPEED, 0f);
                }
                if (possTimer <= 0f) { possState = 2; possTimer = 0f; }
            } else {
                possTimer += step;
                possDown -= step;
                across = place + possSide * possTimer;
            }
            setScreen(g, across, H - possDown);
            return;
        }
        float[] spot = POSSESSED_SPOTS[possMember % POSSESSED_SPOTS.length];
        float across = MathUtils.clamp(possX + spot[0], 10f, (vertical ? W : H) - 10f), down = spot[1] + (possMember / POSSESSED_SPOTS.length) * 14f;
        // screen position of the centre now (cx, cy) and of its spot (sx, sy)
        float cx = x - g.scrollX + w / 2f, cy = y - g.scrollY + h / 2f, sx, sy;
        if (vertical) { sx = across; sy = H - down; } else { sx = W - down; sy = across; }
        if (t < 0f) { setScreen(g, vertical ? across : W + 10f, vertical ? H + 10f : across); return; }   // still waiting off screen
        if (possState == 0) {                                  // fly in to the spot
            float dx = sx - cx, dy = sy - cy, step = POSSESSED_SPEED * dt;
            cx += MathUtils.clamp(dx, -step, step); cy += MathUtils.clamp(dy, -step, step);
            if (Math.abs(dx) <= step && Math.abs(dy) <= step) { possState = 1; possTimer = POSSESSED_HOVER; shootT = MathUtils.random(0.2f, 0.6f); }
        } else if (possState == 1) {                           // hover and shoot
            possTimer -= dt;
            cx = sx; cy = sy + MathUtils.sin(t * 3f + possMember) * 1.5f;
            shootT -= dt;
            if (shootT <= 0f) {
                shootT = POSSESSED_SHOT + MathUtils.random(0f, 0.3f);
                if (canShoot(g)) g.aimedShot(cx + g.scrollX, cy + g.scrollY, POSSESSED_SHOT_SPEED, 0f);
            }
            if (possTimer <= 0f) possState = 2;
        } else {                                               // back out where it came from
            if (vertical) cy += POSSESSED_LEAVE * dt; else cx += POSSESSED_LEAVE * dt;
        }
        setScreen(g, cx, cy);
    }

    /**
     * Whiplash (stage 2 recording): slides in from the top edge (or, with "fromBottom", up from the bottom edge) at
     * WHIP_IN px/s until it is WHIP_DEPTH px inside the screen, stops for WHIP_HOLD s, then darts off diagonally: on
     * in the same direction at WHIP_DART_ALONG px/s and sideways toward the nearest ship at WHIP_DART_ACROSS px/s,
     * until it has left the screen. Screen-relative. As it stops it fires one expanding laser like the Omen's
     * (vertical stages: toward the far edge). sprites/whiplash.png is drawn as it is.
     * Horizontal stages: the same turned a quarter (in from the right edge, or the left one with "fromBottom").
     */
    public static final float WHIP_IN = 28f, WHIP_DEPTH = 42f, WHIP_HOLD = 0.6f, WHIP_DART_ALONG = 40f, WHIP_DART_ACROSS = 24f;
    public boolean whipFromBottom;
    public float whipAcross;       // its column (vertical stage: x on the screen) / row (horizontal: y on the screen)
    private int whipState;         // 0 = sliding in, 1 = stopped, 2 = darting off
    private float whipTimer, whipSide;
    private float whipAlong = Float.NaN; // how far it is inside the screen from its own edge (px, sprite centre; NaN = not placed yet)

    private void updateWhiplash(GameScreen g, float dt) {
        float W = SalamanderGame.W, H = SalamanderGame.H, len = vertical ? H : W;
        if (Float.isNaN(whipAlong)) whipAlong = -h / 2f - 2f;   // just off its edge
        if (whipState == 0) {
            whipAlong += WHIP_IN * dt;
            if (whipAlong >= WHIP_DEPTH) {
                whipAlong = WHIP_DEPTH; whipState = 1; whipTimer = WHIP_HOLD;
                // as it stops: one expanding laser (like the Omen's) toward the far side of the screen
                if (vertical && canShoot(g)) g.laviniaRoom.omenLaser(x + w / 2f, whipFromBottom ? y + h : y, whipFromBottom);
            }
        } else if (whipState == 1) {
            whipTimer -= dt;
            if (whipTimer <= 0f) {
                whipState = 2;
                Player p = g.target(x + w / 2f, y + h / 2f);
                float d = p == null ? -1f : vertical ? (p.x + Player.W / 2f) - (x + w / 2f) : (p.y + Player.H / 2f) - (y + h / 2f);
                whipSide = d < 0f ? -1f : 1f;
            }
        } else {
            whipAlong += WHIP_DART_ALONG * dt;
            whipAcross += whipSide * WHIP_DART_ACROSS * dt;
        }
        float cx, cy;   // screen centre
        if (vertical) { cx = whipAcross; cy = whipFromBottom ? whipAlong : H - whipAlong; }
        else { cy = whipAcross; cx = whipFromBottom ? whipAlong : W - whipAlong; }
        setScreen(g, cx, cy);
    }

    /** Whiplash that has darted off the screen (it is removed). */
    public boolean whipGone() { return whipState == 2 && whipAlong > (vertical ? SalamanderGame.H : SalamanderGame.W) + h; }

    /**
     * Battleaxe (Kierke opening, as in the MSX recording): a rigid row of BATTLEAXE_COUNT side by side
     * (BATTLEAXE_GAP px apart) that moves as one. A low row (centre BATTLEAXE_LOW_Y above the bottom of the playfield)
     * comes in from the right edge at BATTLEAXE_SPEED px/s; when its first place reaches BATTLEAXE_TURN1 (centre x on
     * the screen) the whole row goes straight up at BATTLEAXE_CLIMB px/s to BATTLEAXE_TOP, left again to
     * BATTLEAXE_TURN2, straight down to BATTLEAXE_BOTTOM and out through the left edge. A high row (BATTLEAXE_HIGH_Y)
     * flies the mirror image (down, left, up). The row keeps its shape when members are shot (each one flies its own
     * place). Screen-relative; it doesn't shoot. sprites/battleaxe.png is drawn as it is.
     */
    public static final int BATTLEAXE_COUNT = 6;
    public static final float BATTLEAXE_GAP = 16f, BATTLEAXE_SPEED = 40f, BATTLEAXE_CLIMB = 80f,
            BATTLEAXE_START = SalamanderGame.W + 8f, BATTLEAXE_TURN1 = 136f, BATTLEAXE_TURN2 = 16f,
            BATTLEAXE_LOW_Y = 32f, BATTLEAXE_HIGH_Y = 128f, BATTLEAXE_TOP = 144f, BATTLEAXE_BOTTOM = 16f;
    public int axeSlot;          // its place in the row (0 = the front, on the left)
    public boolean axeHigh;      // a high row (flies down first)

    private void updateBattleaxe(GameScreen g) {
        float a = axeHigh ? BATTLEAXE_HIGH_Y : BATTLEAXE_LOW_Y, b = axeHigh ? BATTLEAXE_BOTTOM : BATTLEAXE_TOP,
                c = axeHigh ? BATTLEAXE_TOP : BATTLEAXE_BOTTOM;
        float t1 = (BATTLEAXE_START - BATTLEAXE_TURN1) / BATTLEAXE_SPEED, t2 = t1 + Math.abs(b - a) / BATTLEAXE_CLIMB,
                t3 = t2 + (BATTLEAXE_TURN1 - BATTLEAXE_TURN2) / BATTLEAXE_SPEED, t4 = t3 + Math.abs(c - b) / BATTLEAXE_CLIMB;
        float cx, cy, tt = Math.max(0f, t);
        if (tt < t1) { cx = BATTLEAXE_START - BATTLEAXE_SPEED * tt; cy = a; }
        else if (tt < t2) { cx = BATTLEAXE_TURN1; cy = a + Math.signum(b - a) * BATTLEAXE_CLIMB * (tt - t1); }
        else if (tt < t3) { cx = BATTLEAXE_TURN1 - BATTLEAXE_SPEED * (tt - t2); cy = b; }
        else if (tt < t4) { cx = BATTLEAXE_TURN2; cy = b + Math.signum(c - b) * BATTLEAXE_CLIMB * (tt - t3); }
        else { cx = BATTLEAXE_TURN2 - BATTLEAXE_SPEED * (tt - t4); cy = c; }
        setScreen(g, cx + axeSlot * BATTLEAXE_GAP, cy);
    }

    /**
     * Kierke chains: when a chain's start comes to CHAIN_START_X on the screen it grows a ball every CHAIN_STEP s
     * (each one growing 4, 6, 8 px over CHAIN_GROW s; sprites/chain_balls.png), up to its "count". Each new ball is one
     * ball (CHAIN_BALL px) toward the nearest ship: sideways and up or down (diagonally when both); where rock is in
     * the way it steps only sideways or only up / down, and it stops at a wall or once it has reached the ship. The
     * balls stay put in the world, can't be destroyed, stop shots (a laser too) and hurt a ship.
     */
    public static final float CHAIN_BALL = 8f, CHAIN_STEP = 0.25f, CHAIN_GROW = 0.1f, CHAIN_START_X = 216f;

    /**
     * Aberrant (Kierke): the plant at the root of a chain (GameScreen.growChains: a chain only grows while its
     * aberrant is alive). It stays where it is placed and doesn't shoot. Shot down, it withers (the blue frame of
     * sprites/aberrant.png) and stays: shots pass through it, but a ship still crashes into it. The chain balls it
     * already grew stay too.
     */
    public boolean withered;

    /**
     * Nest (Kierke, sprites/nest.png): stays where it is placed and lets enemies out of its mouth
     * (GameScreen.releaseNestBugs): Ugs (see UG_*) or Creeping Death.
     */
    public static final int NEST_HP = 36;   // (Albert, 2026-10-29: everything that lets enemies out is three times as tough)
    public boolean nestDown;     // a nest: its mouth faces down (hangs from the ceiling); Creeping Death / Ug: came out going down
    /**
     * A nest: what comes out of it (NEST_UGS, NEST_CREEPING; NEST_MINES = the flying mines of the four web nests
     * (mineKierke); NEST_NONE = nothing), how many it has left (Ugs) or in this burst (Creeping Death). */
    public int nestKind;
    /** A spawner (nest, blue dome, ground base): the waves it lets out (GameScreen.releaseNestBugs, Wave). */
    public Wave wave;
    public static final int NEST_UGS = 0, NEST_CREEPING = 1, NEST_MINES = 2, NEST_NONE = 3;
    public Enemy nest;           // the nest it came out of

    /**
     * Creeping Death (Kierke; sprites/creeping death.png, 4 frames, white; any black pixels are made see-through). As in the
     * recording, its nest lets them out in bursts of CREEP_BURST (CREEP_BURST_GAP s apart, a burst every CREEP_PAUSE s)
     * while the nest's middle is between x 16 and CREEP_START_X on the screen. Each one rises out of the mouth at
     * CREEP_RISE px/s for CREEP_RISE_T s (drifting a little sideways), then homes in on the nearest ship at CREEP_SPEED
     * px/s, aiming again every CREEP_TURN s (up to CREEP_WOBBLE degrees off). It stays in the world (doesn't scroll
     * with the screen) and doesn't shoot. Drawn: the frames back and forth every CREEP_FRAME s, flickering between two
     * colours every CREEP_FLASH s: cyan / light green, or red / dark green (every other one).
     */
    public static final int CREEP_BURST = 4;
    /** Creeping Death out of an Op 4 wall dome: it first flies out sideways (+1 = to the right), not up / down. */
    public int creepOut;
    public static final float CREEP_BURST_GAP = 0.15f, CREEP_PAUSE = 2.5f, CREEP_START_X = 200f, CREEP_RISE = 50f,
            CREEP_RISE_T = 1.3f, CREEP_DRIFT = 15f, CREEP_SPEED = 45f, CREEP_TURN = 0.4f, CREEP_WOBBLE = 15f,
            CREEP_FRAME = 0.2f, CREEP_FLASH = 0.1f;
    public boolean creepRed;     // the red / dark green kind
    /** Op 4 Creeping Death dome: which one of the current burst is the red one (it leaves a capsule; Albert). */

    private float bugTurnT;

    private void updateNestBug(GameScreen g, float dt) {
        if (t < CREEP_RISE_T) {
            if (t <= dt) {
                if (creepOut != 0) { vx = creepOut * CREEP_RISE; vy = MathUtils.random(-CREEP_DRIFT, CREEP_DRIFT); }   // out of a wall dome's mouth
                else { vx = MathUtils.random(-CREEP_DRIFT, CREEP_DRIFT); vy = nestDown ? -CREEP_RISE : CREEP_RISE; }
            }
        } else if ((bugTurnT -= dt) <= 0f) {
            bugTurnT = CREEP_TURN;
            Player p = g.target(x + w / 2f, y + h / 2f);
            float ang = p == null ? (nestDown ? 90f : 270f)
                    : MathUtils.atan2((p.y + Player.H / 2f) - (y + h / 2f), (p.x + Player.W / 2f) - (x + w / 2f)) * MathUtils.radDeg;
            ang += MathUtils.random(-CREEP_WOBBLE, CREEP_WOBBLE);
            vx = MathUtils.cosDeg(ang) * CREEP_SPEED; vy = MathUtils.sinDeg(ang) * CREEP_SPEED;
        }
        x += vx * dt; y += vy * dt;
    }

    /**
     * Ug (Kierke, the yellow lattice balls; sprites/ug.png, 3 frames of 16x16 cycled every UG_FRAME s). As in the
     * recording, a nest lets out UG_COUNT of them, UG_GAP s apart, once its middle comes to UG_START_X on the screen.
     * Each one flies diagonally (45°) out of the nest's mouth to its lane (UG_LANE from the ceiling / floor), left
     * along it at UG_SPEED px/s to UG_TURN_X, straight toward the middle to its row (UG_ROW_TOP / UG_ROW_BOTTOM above
     * the bottom of the playfield: the ones from a ceiling nest take the upper row), then right at UG_BACK px/s and out
     * through the right edge. Screen-relative; it doesn't shoot.
     */
    public static final int UG_COUNT = 6;
    public static final float UG_GAP = 0.2f, UG_START_X = 232f, UG_FRAME = 0.1f, UG_SPEED = 90f, UG_BACK = 70f,
            UG_LANE = 40f, UG_TURN_X = 64f, UG_ROW_TOP = 91f, UG_ROW_BOTTOM = 76f;
    public float ugX, ugY;       // its centre on the screen
    private int ugState;

    private void updateUg(GameScreen g, float dt) {
        float H = SalamanderGame.H, lane = nestDown ? H - UG_LANE : UG_LANE, row = nestDown ? UG_ROW_TOP : UG_ROW_BOTTOM;
        float d = UG_SPEED * dt;
        switch (ugState) {
            case 0: {                                    // out of the mouth, diagonally to its lane
                float dy = lane - ugY;
                ugX -= d * 0.7071f;
                if (Math.abs(dy) <= d * 0.7071f) { ugY = lane; ugState = 1; } else ugY += Math.signum(dy) * d * 0.7071f;
                break;
            }
            case 1:                                      // left along the lane
                ugX -= d;
                if (ugX <= UG_TURN_X) { ugX = UG_TURN_X; ugState = 2; }
                break;
            case 2: {                                    // toward the middle, to its row
                float dy = row - ugY;
                if (Math.abs(dy) <= d) { ugY = row; ugState = 3; } else ugY += Math.signum(dy) * d;
                break;
            }
            default:                                     // and back out to the right
                ugX += UG_BACK * dt;
        }
        setScreen(g, ugX, ugY);
    }

    /**
     * Gamma (Kierke, the blue balls; sprites/gamma.png, 4 frames of 16x16, shown in the order GAMMA_ORDER every
     * GAMMA_FRAME s, as in the recording). As in the recording it comes in at the right edge (at its object's height)
     * and drifts left through the world at GAMMA_VX px/s, bouncing slowly on the floor (or, a ceiling one, "falling"
     * up and bouncing on the ceiling): pulled down at GAMMA_GRAVITY px/s², it bounces back up at GAMMA_BOUNCE px/s
     * (about 35 px high, a bounce every ~2.3 s), and rides up over rock it runs into. It doesn't shoot.
     */
    public static final float GAMMA_VX = -16f, GAMMA_GRAVITY = 53f, GAMMA_BOUNCE = 61f, GAMMA_FRAME = 0.2f;
    public static final int[] GAMMA_ORDER = {0, 2, 3, 1};
    public boolean gammaCeil;    // bounces on the ceiling
    public boolean gammaRed;     // sprites/gamma_red.png (it leaves a power capsule)
    /** Lavinia: comes in at the LEFT edge and drifts right through the world at GAMMA_VX_LEFT px/s (as in the recording). */
    public boolean gammaLeft;
    public static final float GAMMA_VX_LEFT = 56f;

    private void updateGamma(GameScreen g, float dt) {
        if (t <= dt) vy = (gammaCeil ? -GAMMA_BOUNCE : GAMMA_BOUNCE) * 0.85f;   // comes in just after a bounce
        Level lv = g.level;
        x += (gammaLeft ? GAMMA_VX_LEFT : GAMMA_VX) * dt;
        if (lv.hits(x + 3f, y + 3f, w - 6f, h - 6f)) {   // ran into rock: over it
            if (gammaCeil) { float c = lv.ceilBottom(x + w / 2f, y + h / 2f); if (c < Level.NO_CEILING) y = c - h; }
            else { float f = lv.floorTop(x + w / 2f, y + h / 2f); if (f > Level.NO_FLOOR) y = f; }
        }
        vy += (gammaCeil ? GAMMA_GRAVITY : -GAMMA_GRAVITY) * dt;
        y += vy * dt;
        if (!gammaCeil && vy < 0f && lv.hits(x + 3f, y, w - 6f, 1f)) {
            float f = lv.floorTop(x + w / 2f, y + 1f);
            if (f > Level.NO_FLOOR) y = f;
            vy = GAMMA_BOUNCE;
        } else if (gammaCeil && vy > 0f && lv.hits(x + 3f, y + h - 1f, w - 6f, 1f)) {
            float c = lv.ceilBottom(x + w / 2f, y + h - 1f);
            if (c < Level.NO_CEILING) y = c - h;
            vy = -GAMMA_BOUNCE;
        }
        // now and then a burst of GAMMA_BURST shots at the nearest ship, GAMMA_BURST_GAP s apart (Albert, 2026-10-30)
        if (gammaShotT < 0f) gammaShotT = MathUtils.random(GAMMA_FIRST_MIN, GAMMA_FIRST_MAX);
        if ((gammaShotT -= dt) > 0f) return;
        if (gammaBurstLeft <= 0) gammaBurstLeft = GAMMA_BURST;
        gammaBurstLeft--;
        gammaShotT = gammaBurstLeft > 0 ? GAMMA_BURST_GAP : MathUtils.random(GAMMA_EVERY_MIN, GAMMA_EVERY_MAX);
        if (canShoot(g)) g.aimedShot(x + w / 2f, y + h / 2f, GAMMA_SHOT, 0f);
    }

    /** Gamma's shots: GAMMA_BURST per burst, the first burst GAMMA_FIRST_MIN..MAX s after it came, then every GAMMA_EVERY_MIN..MAX s. */
    public static final int GAMMA_BURST = 2;
    public static final float GAMMA_BURST_GAP = 0.5f,   // (Albert, 2026-10-30: was 0.15)
            GAMMA_FIRST_MIN = 1.5f, GAMMA_FIRST_MAX = 3.5f,
            GAMMA_EVERY_MIN = 2.5f, GAMMA_EVERY_MAX = 4.5f, GAMMA_SHOT = 80f;
    private float gammaShotT = -1f;
    private int gammaBurstLeft;

    /**
     * Blue dome (Eioneus and Op 4, on the side walls; drawn with stage 2's sprites/dome.png, 18x32: closed, and open
     * while letting flyers out). As in
     * the recording: once its middle is BLUE_DOME_START px below the top edge it lets out a burst of BLUE_DOME_BURST
     * green flyers, BLUE_DOME_GAP s apart, and another burst every BLUE_DOME_REPEAT s while it is on the screen.
     * Stays where it is placed; domeRight = on the right wall (mirrored), its flyers fly the mirrored path.
     */
    public static final int BLUE_DOME_HP = 36, BLUE_DOME_BURST = 4;
    public static final float BLUE_DOME_START = 40f, BLUE_DOME_GAP = 0.5f, BLUE_DOME_REPEAT = 5.3f;
    public boolean domeRight;
    /** Op 4 dome (stage 6): its flyers home in (gfHoming); domeBurst flyers per burst. */
    public boolean domeOp4;
    /** Op 4 dome that lets out Creeping Death (Albert: the two in the rock opening) instead of green flyers. */
    public boolean domeCreep;
    public int domeBurst = BLUE_DOME_BURST;

    /**
     * Green flyer (Eioneus; sprites/green_flyer.png, 4 frames cycled every GREEN_FRAME s): out of a blue dome it flies
     * the loop measured in the recording (GREEN_PATH: offsets in px from where it came out, y down, one point every
     * GREEN_STEP s; mirrored for a dome on the right wall): along the top, a big loop down and round, a smaller one,
     * then up and out through the top. Screen-relative from where it started; it doesn't shoot.
     */
    public static final float GREEN_STEP = 0.5f, GREEN_FRAME = 0.2f;
    private static final float[][] GREEN_PATH = {{0, 0}, {38, 1}, {63, 16}, {80, 39}, {77, 67}, {58, 88}, {32, 97},
            {9, 86}, {-8, 62}, {-1, 34}, {17, 17}, {40, 18}, {57, 31}, {66, 51}, {58, 70}, {38, 81}, {21, 74}, {31, 46},
            {44, 19}, {57, -18}};
    public float gfX, gfY;        // where it came out (screen, y up)
    public boolean gfMirror;

    /**
     * Op 4 (stage 6) flyers (gfHoming), from the stage 6 recording (66-74 s): out of the dome's mouth straight away
     * from the wall at GREEN6_SPEED px/s for GREEN6_OUT s, then they curve round toward the nearest ship (turning at
     * most GREEN6_TURN rad/s) for GREEN6_HOME s, and fly on straight after that. Screen-relative; they don't shoot.
     */
    public static final float GREEN6_SPEED = 60f, GREEN6_OUT = 0.3f, GREEN6_TURN = 2.5f, GREEN6_HOME = 6f;
    public boolean gfHoming;
    /** Homing flyer: the direction it comes out in (radians; NaN = away from its dome's wall). */
    public float gfStartAng = Float.NaN;
    private float gfAng = Float.NaN;

    private void updateGreenFlyer(GameScreen g, float dt) {
        if (gfHoming) { updateHomingFlyer(g, dt); return; }
        float u = Math.max(0f, t) / GREEN_STEP;
        int n = GREEN_PATH.length, i = (int) u;
        float dx, dy;
        if (i >= n - 1) {                                // past the end: straight on
            float[] a = GREEN_PATH[n - 2], b = GREEN_PATH[n - 1];
            float k = u - (n - 1);
            dx = b[0] + (b[0] - a[0]) * k; dy = b[1] + (b[1] - a[1]) * k;
        } else {                                         // Catmull-Rom between the points
            float[] p0 = GREEN_PATH[Math.max(0, i - 1)], p1 = GREEN_PATH[i], p2 = GREEN_PATH[i + 1], p3 = GREEN_PATH[Math.min(n - 1, i + 2)];
            float k = u - i, k2 = k * k, k3 = k2 * k;
            dx = 0.5f * (2 * p1[0] + (-p0[0] + p2[0]) * k + (2 * p0[0] - 5 * p1[0] + 4 * p2[0] - p3[0]) * k2 + (-p0[0] + 3 * p1[0] - 3 * p2[0] + p3[0]) * k3);
            dy = 0.5f * (2 * p1[1] + (-p0[1] + p2[1]) * k + (2 * p0[1] - 5 * p1[1] + 4 * p2[1] - p3[1]) * k2 + (-p0[1] + 3 * p1[1] - 3 * p2[1] + p3[1]) * k3);
        }
        setScreen(g, gfX + (gfMirror ? -dx : dx), gfY - dy);
    }

    private void updateHomingFlyer(GameScreen g, float dt) {
        if (Float.isNaN(gfAng)) gfAng = !Float.isNaN(gfStartAng) ? gfStartAng   // (Op 4's gun units: straight down out of the middle)
                : gfMirror ? MathUtils.PI : 0f;   // straight out of the mouth, away from its wall
        if (t > GREEN6_OUT && t < GREEN6_OUT + GREEN6_HOME) {
            Player p = g.target(x + w / 2f, y + h / 2f);
            if (p != null) {
                float want = MathUtils.atan2((p.y + Player.H / 2f) - (y + h / 2f), (p.x + Player.W / 2f) - (x + w / 2f));
                float d = want - gfAng;
                while (d > MathUtils.PI) d -= MathUtils.PI2;
                while (d < -MathUtils.PI) d += MathUtils.PI2;
                gfAng += MathUtils.clamp(d, -GREEN6_TURN * dt, GREEN6_TURN * dt);
            }
        }
        gfX += MathUtils.cos(gfAng) * GREEN6_SPEED * dt;
        gfY += MathUtils.sin(gfAng) * GREEN6_SPEED * dt;
        setScreen(g, gfX, gfY);
    }

    /** Green flyer that has flown out of the screen at the end of its loop (it is removed). */
    public boolean greenGone(GameScreen g) {
        if (gfHoming) {
            if (t < GREEN6_OUT + 1f) return false;
            float sx = x - g.scrollX, sy = y - g.scrollY;
            return sy > SalamanderGame.H || sy + h < 0f || sx > SalamanderGame.W || sx + w < 0f;
        }
        if (t < GREEN_STEP * (GREEN_PATH.length - 1)) return false;
        float sx = x - g.scrollX, sy = y - g.scrollY;
        return sy > SalamanderGame.H || sy + h < 0f || sx > SalamanderGame.W || sx + w < 0f;
    }

    /**
     * Laser battery (Eioneus; sprites/laser_battery_small.png 24x16, laser_battery_big.png 32x16). As in the
     * recording, its beam is on all the time: a small battery on a wall shoots it sideways to the rock across; a big
     * one on the floor shoots it up to the big one hanging above it (the two share the beam). The beam is the Laser
     * Guards' red / blue pattern (flashing at OMEN_LASER_FLASH) and hurts a ship that touches it; shots pass it. Shot down (BATTERY_HP hits), the
     * battery's beam goes out. Stays where it is placed.
     */
    public static final int BATTERY_HP = 6;
    public static final float BATTERY_BEAM_W = 5f;   // (the height of the Laser Guards' pattern)
    /**
     * Lavinia wall laser (Tiled "laser"): the same beam between two pillars, from an emitter that is part of the wall
     * art (batWall: nothing is drawn but the beam). The emitter's hit box is WALL_EMITTER_W x WALL_EMITTER_H at the
     * beam's end; shooting it (BATTERY_HP hits) puts the beam out, as the beams that go out in the recording.
     */
    public static final float WALL_EMITTER_W = 12f, WALL_EMITTER_H = 9f;
    public boolean batWall;
    public boolean batBig, batRight, batCeiling;
    /** Its beam (world rectangle; w = 0: none) and the battery at the other end (a big pair). */
    public float beamX, beamY, beamW, beamH;
    public Enemy batPartner;

    /** Is its beam there (it and the battery at the other end are alive)? */
    public boolean beamOn() { return !dead && beamW > 0f && beamH > 0f && (batPartner == null || !batPartner.dead); }

    /**
     * Gremlin (Eioneus, the little walkers on the side walls; sprites/gremlin_yellow.png / gremlin_red.png, 3 frames
     * of 16x16: frame 0 = standing still to aim, frames 1-2 = walking). It comes up from the bottom edge climbing its
     * wall (it keeps to the rock face) at GREMLIN_SPEED px/s relative to the screen (Albert, 2026-10-28), walks for
     * GREMLIN_WALK s, stops for GREMLIN_AIM s (standing on its spot of the wall, so it scrolls with it; the aimed shot
     * goes at the end of the stop, GREMLIN_FIRE s in), and walks on again. gremRight = on the right wall. Drawn turned a
     * quarter so it stands on the wall.
     * Every other shot (Albert, 2026-10-28) it first gets ahead of the ship: it climbs at GREMLIN_RUN px/s until its
     * middle is GREMLIN_AHEAD px above the nearest ship's (at least GREMLIN_RUN_MIN s), and aims from there. It stops
     * early near the top edge (GREMLIN_TOP px from it) or after GREMLIN_RUN_MAX s.
     */
    public static final float GREMLIN_SPEED = 48f, GREMLIN_WALK = 2f, GREMLIN_AIM = 1f, GREMLIN_FIRE = 1f, GREMLIN_FRAME = 0.15f, GREMLIN_SHOT_SPEED = 100f;
    public static final float GREMLIN_RUN = 80f, GREMLIN_AHEAD = 40f, GREMLIN_RUN_MIN = 0.3f, GREMLIN_RUN_MAX = 4f, GREMLIN_TOP = 24f;
    public boolean gremRight, gremRed, gremAiming;
    private float gremT;
    /** Gremlin: this time it gets ahead of the ship before it aims (every other shot). */
    private boolean gremAhead;

    private void updateGremlin(GameScreen g, float dt) {
        Level lv = g.level;
        gremT += dt;
        if (gremAiming) {
            // its shot comes soon after it stops (at the end of the stop it was often off the screen already)
            if (gremT - dt < GREMLIN_FIRE && gremT >= GREMLIN_FIRE && canShoot(g)) g.aimedShot(x + w / 2f, y + h / 2f, GREMLIN_SHOT_SPEED, 0f);
            if (gremT >= GREMLIN_AIM) { gremAiming = false; gremT = 0f; gremAhead = !gremAhead; }   // (next time: the other way)
        } else if (gremAhead) {                          // climbing past the ship, to shoot at it from the front
            y += (GREMLIN_RUN + g.scrollVY) * dt;        // (relative to the screen)
            Player tp = g.target(x + w / 2f, y + h / 2f);
            boolean ahead = tp == null || y + h / 2f >= tp.y + Player.H / 2f + GREMLIN_AHEAD;
            boolean top = y + h >= g.scrollY + SalamanderGame.H - GREMLIN_TOP;
            if (y > g.scrollY && gremT >= GREMLIN_RUN_MIN && (ahead || top || gremT >= GREMLIN_RUN_MAX)) { gremAiming = true; gremT = 0f; }
        } else {
            y += (GREMLIN_SPEED + g.scrollVY) * dt;      // (relative to the screen)
            if (gremT >= GREMLIN_WALK && y > g.scrollY) { gremAiming = true; gremT = 0f; }
        }
        // keep to the rock face of its wall at this height (searched across the screen, in world x)
        float cy = y + 4f, best = -1f, sl = g.scrollX;
        if (!gremRight) { for (float sx = 0f; sx < SalamanderGame.W - w; sx += 1f) if (!lv.hits(sl + sx, cy, 1f, h - 8f)) { best = sx; break; } }
        else { for (float sx = SalamanderGame.W - 1f; sx > w; sx -= 1f) if (!lv.hits(sl + sx, cy, 1f, h - 8f)) { best = sx + 1f - w; break; } }
        if (best >= 0f) x = g.scrollX + best;
    }

    /** Its walking / aiming frame (0 = aiming). */
    public int gremlinFrame() { return gremAiming ? 0 : 1 + ((int) (t / GREMLIN_FRAME) & 1); }

    /**
     * Eioneus base (the big red pods, Tiled class "seed"; sprites/eioneus_base.png, 4 frames of 44x46: closed,
     * opening, open, destroyed). As in the recording: once its middle is SEED_START px below the top edge it opens
     * (SEED_OPENING s) and stays open, letting out bursts of SEED_BURST flying mines from its middle, SEED_GAP s
     * apart, a burst every SEED_REPEAT s while it is on the screen. Drawn under the other enemies. It is ground: ships fly over it
     * (no collision), but shots hit it. Shot down it shows the destroyed frame and stays (shots pass then).
     */
    public static final int SEED_HP = 36, SEED_BURST = 4;
    public static final float SEED_START = 50f, SEED_OPENING = 0.4f, SEED_GAP = 0.5f, SEED_REPEAT = 4f;
    /**
     * Op 4 base (stage 6, Tiled class "base6"; Albert's sprites/stage_6_back_ground_base.png, 4 frames of 22x22: closed,
     * opening, open, destroyed): the same as an Eioneus base, but the whole 22x22 picture is the enemy and, as in the
     * stage 6 recording, it lets out one burst of SEED6_BURST mines, SEED6_GAP s apart (the mines: mineOp4).
     */
    public boolean seedOp4;
    public static final int SEED6_BURST = 5;
    /**
     * Albert (2026-10-26): an Op 4 base lets out waves (at least two: the next one SEED6_REPEAT s after the last mine
     * of a wave, while it is on screen), and one mine of every wave (at random) is red and leaves a power capsule.
     */
    public static final float SEED6_GAP = 0.55f, SEED6_REPEAT = 2.5f;
    public float seedT = -1f;     // time to its next mine (-1 = not started)
    public boolean seedOpening;   // opening, the first burst comes next
    public boolean seedOpen;      // has opened (it stays open)

    /** Its frame: 0 closed, 1 opening, 2 open (letting mines out), 3 destroyed. */
    public int seedFrame() { return withered ? 3 : seedOpen ? 2 : seedOpening ? 1 : 0; }

    /** Ground (Eioneus base): ships fly over it. */
    public boolean ground() { return type == Type.SEED; }

    /**
     * Flying mine (Eioneus; sprites/flying_mines.png white, flying_mines_red.png red: 5 frames of 16x16). As in the
     * recording: out of the middle of a base it "takes off" (frames 0-2, a third of MINE_TAKEOFF s each) - meanwhile it can't hit or be hit - flying
     * the first part of the loop measured in the recording (MINE_PATH, world offsets from where it came out, y down,
     * a point every MINE_STEP s: right, down round and up to the left), then, on the same plane as the ships, it
     * flickers between frames 3 and 4 (MINE_FRAME s), finishes the loop and homes in on the nearest ship (accelerating
     * at MINE_ACCEL px/s² up to MINE_SPEED px/s). A red one leaves a power capsule when shot down.
     */
    public static final float MINE_STEP = 0.1f, MINE_TAKEOFF = 1.0f, MINE_FRAME = 0.1f, MINE_ACCEL = 160f, MINE_SPEED = 130f;
    private static final float[][] MINE_PATH = {{0, 0}, {5, 0}, {10, 1}, {12, 3}, {15, 6}, {15, 10}, {10, 15}, {5, 17},
            {3, 17}, {-3, 17}, {-9, 15}, {-16, 8}, {-21, 2}, {-23, -1}, {-26, -8}, {-29, -16}, {-31, -20}, {-35, -26},
            {-37, -28}, {-43, -33}, {-46, -36}, {-53, -39}};
    public float mineX, mineY;     // where it came out (world, y up)
    public boolean mineRed;
    /**
     * Op 4 (stage 6) mines (mineOp4), measured in the stage 6 recording (72-105 s): during the take-off they rise
     * straight up the screen MINE6_RISE px out of the base's middle, then fly the loop MINE6_PATH (world offsets from
     * there, x right, y down, a point every MINE_STEP s: up and to the left, then round to the right), then home in
     * on the nearest ship like the Eioneus ones. The loop doesn't depend on where the ship is.
     */
    public boolean mineOp4;
    public static final float MINE6_RISE = 24f;
    private static final float[][] MINE6_PATH = {{0, 0}, {-5, -2}, {-10, -6}, {-14, -13}, {-18, -20}, {-21, -26},
            {-24, -33}, {-26, -40}, {-27, -46}, {-26, -50}, {-24, -53}, {-20, -55}, {-15, -55}, {-10, -53}, {-5, -50},
            {0, -47}, {6, -43}};

    /**
     * Kierke mines (mineKierke), measured in the Kierke recording (100-104, 119-124, 138-142, 160-165 s): the four
     * nests floating in the web (Tiled nest, spawns "mines") let out one burst of MINEK_BURST, MINEK_GAP s apart, once
     * the nest's middle comes to MINEK_START_X on the screen. Each appears to the right of the nest (taking off for
     * MINEK_TAKEOFF s, frames 0-2), then curls anticlockwise up and over the nest (MINEK_PATH: world offsets from the
     * nest's middle, x right, y down, a point every MINE_STEP s) and dives off toward the nearest ship, faster than
     * the other mines (MINEK_ACCEL, MINEK_SPEED; in the recording ~180 px/s, down-left toward the ship).
     */
    public boolean mineKierke;
    public static final int MINEK_BURST = 5;
    public static final float MINEK_START_X = 208f, MINEK_GAP = 0.5f, MINEK_TAKEOFF = 0.4f, MINEK_ACCEL = 400f, MINEK_SPEED = 180f;
    private static final float[][] MINEK_PATH = {{38, -2}, {35, 3}, {31, 7}, {26, 10}, {22, 12}, {17, 9}, {13, 4},
            {11, -3}, {9, -10}, {5, -16}, {0, -20}, {-6, -22}, {-13, -22}, {-21, -20}, {-30, -17}, {-40, -13}, {-50, -8}};

    /** How long it takes off (can't hit or be hit yet). */
    private float mineTakeoff() { return mineKierke ? MINEK_TAKEOFF : MINE_TAKEOFF; }

    /** Taking off (can't hit or be hit yet). */
    public boolean intangible() { return type == Type.FLYING_MINE && t < mineTakeoff(); }

    /** Stays where the map has it: not removed for being above / below the camera's lane (the Lavinia shaft). */
    public boolean anchored() {
        return type == Type.OMEN || type == Type.LASER_GUN || type == Type.LASER_BATTERY || type == Type.ARTILLERY
                || type == Type.BLOCK_SMALL || type == Type.BLOCK_BIG;
    }

    /** Its frame: 0-2 taking off, then 3 / 4 by turns. */
    public int mineFrame() {
        float to = mineTakeoff();
        if (t < to) return Math.min(2, (int) (Math.max(0f, t) / (to / 3f)));
        return 3 + ((int) ((t - to) / MINE_FRAME) & 1);
    }

    private void updateFlyingMine(GameScreen g, float dt) {
        if (mineKierke) {
            float u = Math.max(0f, t) / MINE_STEP;
            int n = MINEK_PATH.length, i = (int) u;
            if (i < n - 1) {                             // the curl over the nest (offsets: x right, y down)
                float k = u - i;
                float[] a = MINEK_PATH[i], b = MINEK_PATH[i + 1];
                x = mineX + a[0] + (b[0] - a[0]) * k - w / 2f; y = mineY - (a[1] + (b[1] - a[1]) * k) - h / 2f;
                vx = (b[0] - a[0]) / MINE_STEP; vy = -(b[1] - a[1]) / MINE_STEP;
                return;
            }
            homeMine(g, dt, MINEK_ACCEL, MINEK_SPEED);
            return;
        }
        if (mineOp4 && t < MINE_TAKEOFF + MINE_STEP * (MINE6_PATH.length - 1)) {
            float ox, oy, ovx, ovy;
            if (t < MINE_TAKEOFF) {                      // rising out of the base
                float k = Math.max(0f, t) / MINE_TAKEOFF;
                ox = 0f; oy = -MINE6_RISE * k; ovx = 0f; ovy = -MINE6_RISE / MINE_TAKEOFF;
            } else {
                float u = (t - MINE_TAKEOFF) / MINE_STEP;
                int i = Math.min((int) u, MINE6_PATH.length - 2);
                float k = u - i;
                float[] a = MINE6_PATH[i], b = MINE6_PATH[i + 1];
                ox = a[0] + (b[0] - a[0]) * k; oy = -MINE6_RISE + a[1] + (b[1] - a[1]) * k;
                ovx = (b[0] - a[0]) / MINE_STEP; ovy = (b[1] - a[1]) / MINE_STEP;
            }
            x = mineX + ox - w / 2f; y = mineY - oy - h / 2f;
            vx = ovx; vy = -ovy;
            return;
        }
        if (mineOp4) {                                   // then home in on the nearest ship
            homeMine(g, dt, MINE_ACCEL, MINE_SPEED);
            return;
        }
        float u = Math.max(0f, t) / MINE_STEP;
        int n = MINE_PATH.length, i = (int) u;
        if (i < n - 1) {                                 // the loop (offsets: x right, y down)
            float k = u - i;
            float ox = MINE_PATH[i][0] + (MINE_PATH[i + 1][0] - MINE_PATH[i][0]) * k;
            float oy = MINE_PATH[i][1] + (MINE_PATH[i + 1][1] - MINE_PATH[i][1]) * k;
            x = mineX + ox - w / 2f; y = mineY - oy - h / 2f;
            vx = (MINE_PATH[i + 1][0] - MINE_PATH[i][0]) / MINE_STEP; vy = -(MINE_PATH[i + 1][1] - MINE_PATH[i][1]) / MINE_STEP;
            return;
        }
        homeMine(g, dt, MINE_ACCEL, MINE_SPEED);
    }

    private void homeMine(GameScreen g, float dt, float accel, float maxSpeed) {
        Player p = g.target(x + w / 2f, y + h / 2f);     // then home in on the nearest ship
        if (p != null) {
            float dx = (p.x + Player.W / 2f) - (x + w / 2f), dy = (p.y + Player.H / 2f) - (y + h / 2f), d = (float) Math.sqrt(dx * dx + dy * dy);
            if (d > 1f) { vx += dx / d * accel * dt; vy += dy / d * accel * dt; }
        }
        float sp = (float) Math.sqrt(vx * vx + vy * vy);
        if (sp > maxSpeed) { vx *= maxSpeed / sp; vy *= maxSpeed / sp; }
        x += (vx + g.scrollVX) * dt; y += (vy + g.scrollVY) * dt;   // homes in relative to the screen, like the ships move
    }

    /** Ug that has flown out through the right edge (it is removed). */
    public boolean ugGone() { return ugState == 3 && ugX > SalamanderGame.W + w; }

    /** Battleaxe that has flown out through the left edge (it is removed). */
    public boolean axeGone(GameScreen g) { return t > 1f && x + w < g.scrollX - 2f; }

    private void setScreen(GameScreen g, float cx, float cy) { x = g.scrollX + cx - w / 2f; y = g.scrollY + cy - h / 2f; }

    /** Possessed that has left the screen again (it is removed). */
    public boolean possGone(GameScreen g) {
        if (possState != 2) return false;
        return vertical || possRow ? y > g.scrollY + SalamanderGame.H + 4f : x > g.scrollX + SalamanderGame.W + 4f;
    }
    public static final float LEGACY_PICK_MIN = 0.6f, LEGACY_PICK_MAX = 1.6f, LEGACY_DART = 45f, LEGACY_DIVE = 60f,
            LEGACY_DIVE_TIME = 1.4f, LEGACY_DIVE_CHANCE = 0.35f, LEGACY_LEAN = 14f;
    private float legacyPick, legacyWant, legacyDive;
    /** Hovering Legacy: sideways speed (px/s; + = right on a vertical stage, up on a horizontal one). */
    public float legacySide;

    /** Hovering Legacy: frame 0..6 of sprites/legacy.png for its lean. */
    public int legacyFrame() { return 3 + MathUtils.clamp(Math.round(legacySide / LEGACY_LEAN), -3, 3); }

    private void updateHoverLegacy(GameScreen g, float dt) {
        Player p = g.target(x + w / 2f, y + h / 2f);
        legacyPick -= dt;
        if (legacyPick <= 0f) {
            legacyPick = MathUtils.random(LEGACY_PICK_MIN, LEGACY_PICK_MAX);
            if (p == null || MathUtils.randomBoolean(0.3f)) legacyWant = MathUtils.random(-15f, 15f);   // just drifts
            else {
                float d = vertical ? (p.x + Player.W / 2f) - (x + w / 2f) : (p.y + Player.H / 2f) - (y + h / 2f);
                legacyWant = Math.signum(d) * Math.min(LEGACY_DART, Math.abs(d) * 1.5f + MathUtils.random(5f, 20f));
                boolean ahead = p != null && (vertical ? p.y < y : p.x < x);        // the ship is "below" it
                if (legacyDive <= 0f && ahead && Math.abs(d) < 28f && MathUtils.randomBoolean(LEGACY_DIVE_CHANCE))
                    legacyDive = LEGACY_DIVE_TIME;
            }
        }
        legacySide += (legacyWant - legacySide) * Math.min(1f, dt * 4f);
        float dive = 0f;
        if (legacyDive > 0f) { legacyDive -= dt; dive = LEGACY_DIVE; }
        if (vertical) { x += legacySide * dt; y -= dive * dt; }
        else { y += legacySide * dt; x -= dive * dt; }
        float minX = g.scrollX - 4f, maxX = g.scrollX + SalamanderGame.W - w + 4f;   // stays on the screen sideways
        if (vertical) x = MathUtils.clamp(x, minX, maxX);
    }


    /**
     * Gun Ho (stage 3, sprites/Gun Ho.png, faces left): stands where it was placed in Tiled and scrolls in with the
     * terrain. Once it is well inside the screen it keeps pace with the screen and moves up / down (not into rock) to
     * line its gun up with the nearest ship. As soon as it is lined up it fires one shot (sprites/gun ho bullet.png)
     * straight at the ship (horizontally), waits a moment and then flies off the left edge. If it can't line up within GUNHO_GIVE_UP
     * seconds it leaves without firing.
     */
    public static final float GUNHO_CLIMB = 55f, GUNHO_ALIGN = 2f, GUNHO_GIVE_UP = 6f, GUNHO_HOLD = 0.35f,
            GUNHO_LEAVE = 120f, GUNHO_BEAM_SPEED = 260f,
            GUNHO_BEAM_LEN = 28f, GUNHO_BEAM_H = 3f;   // (beam size: only when gun ho bullet.png is missing)
    /** Gun Ho: height of its gun barrel above the bottom of the sprite. */
    public static final float GUNHO_GUN_Y = 22f;
    public static final int GUNHO_COMING = 0, GUNHO_AIMING = 1, GUNHO_FIRED = 2, GUNHO_LEAVING = 3, GUNHO_HOPPING = 4, GUNHO_FIRING = 5;
    /** Gun Ho fires a burst of GUNHO_BURST shots, GUNHO_BURST_GAP s apart, once it is lined up (Albert, 2026-10-30). */
    public static final int GUNHO_BURST = 3;
    public static final float GUNHO_BURST_GAP = 0.2f;
    private int gunShots;
    /**
     * Lavinia (both styles, as in the recording): a Gun Ho comes out of a floor hatch (Tiled dome_type2 with "gunhos") and, keeping
     * its place on the screen, hops out of it: up GUNHO_HOP_UP px, GUNHO_HOP_LEFT1 px to the left, down to the floor
     * again, GUNHO_HOP_LEFT2 px to the left (GUNHO_HOP_VY / GUNHO_HOP_VX px/s). Then it lines up with the nearest ship,
     * fires and leaves as usual, at GUNHO_LEAVE_MSX px/s.
     */
    public static final float GUNHO_HOP_UP = 48f, GUNHO_HOP_LEFT1 = 24f, GUNHO_HOP_LEFT2 = 16f,
            GUNHO_HOP_VY = 53f, GUNHO_HOP_VX = 60f, GUNHO_LEAVE_MSX = 80f;
    /** Hopping Gun Ho: which leg of the hop (0..3) and how far it still has to go on it. */
    public int gunHopLeg;
    public float gunHopLeft = Float.NaN;
    /** Gun Ho: GUNHO_COMING / AIMING / FIRED / LEAVING, seconds in that state, faces right (ship behind it). */
    public int gunState;
    public float gunT;
    public boolean faceRight;

    /**
     * Omen (stage 3, sprites/Omen.png, no animation): sits still on the floor (or the ceiling: drawn upside down)
     * where it was placed in Tiled. OMEN_DELAY seconds after it comes on screen it fires a burst of OMEN_BURST
     * lasers, OMEN_BURST_GAP seconds apart, and then another burst OMEN_REPEAT seconds after the last laser of the
     * previous one, for as long as it is on screen. The lasers fly straight up (down from a
     * ceiling Omen) and widen sideways as they go, like the Ripple on the vertical stages (see GameScreen.omenLaser).
     */
    public static final int OMEN_BURST = 3;
    public static final float OMEN_DELAY = 0.3f, OMEN_BURST_GAP = 0.15f, OMEN_REPEAT = 1.5f;
    /**
     * Omen laser: speed (px/s, vertical), how fast it widens (px/s, both ends together), widest it gets (px), and
     * how often it switches between enemy laser red.png and enemy laser blue.png (times per second).
     */
    public static final float OMEN_LASER_SPEED = 120f, OMEN_LASER_GROW = 90f, OMEN_LASER_MAX_W = 96f, OMEN_LASER_FLASH = 15f;
    /** Omen: lasers of the current burst fired so far; its timer (seconds on screen, set back after each burst). */
    public int omenShots;
    public float omenT;

    /**
     * Laser Guard (stage 3's last room, sprites/Laser Guard.png, pointing down = on the ceiling): can't be destroyed
     * (shots just stop on it). The ceiling and floor ones slide along their wall (x), the one on the right-hand wall
     * slides up and down. After every volley GameScreen.updateGuardVolley picks one guard to chase the nearest ship
     * (staying lined up with it, GUARD_ALIGN_SPEED); sometimes (GUARD_TWO_CHASERS) two: the side-wall one plus the
     * ceiling or the floor one, never both of those. The others slide to random spots (GUARD_SPEED). All guards fire
     * together (GameScreen.updateGuardVolley): every GUARD_FIRE_MIN..MAX seconds, or at once when one of them is lined up. Before every shot they stop for GUARD_WIND_UP seconds. Its laser behaves like the ships' laser: it grows out of the gun for GUARD_BEAM_TIME seconds, then lets
     * go and flies on (GameScreen.steerGuardBeams). The guard stands still while its laser is on screen. When the room's hold is over the guards retreat into their walls.
     */
    public static final int GUARD_CEIL = 0, GUARD_FLOOR = 1, GUARD_WALL = 2;
    public static final float GUARD_SPEED = 90f, GUARD_ALIGN_SPEED = 140f, GUARD_TWO_CHASERS = 0.3f,
            GUARD_FIRE_MIN = 1.6f, GUARD_FIRE_MAX = 3.0f, GUARD_BEAM_TIME = 0.6f, GUARD_BEAM_SPEED = 300f,
            GUARD_RETREAT_TIME = 1.2f;
    /** Laser Guard: it stops this long before every shot. */
    public static final float GUARD_WIND_UP = 0.3f;
    /** Laser Guard: which wall; the wall line (y of the ceiling / floor, x of the side wall); how far it can slide. */
    public int guardSide;
    public float wallLine, moveMin, moveMax;
    /** Laser Guard: where it slides to (x, or y on the side wall); lining up with a ship (seconds left); retreat 0..1. */
    public float guardGoal, retreat;
    /** Laser Guard: picked (after a volley) to chase the nearest ship and stay lined up with it. */
    public boolean chasing;
    /** Laser Guard: seconds left standing still before it fires (0 = not about to fire). */
    public float windT;
    /** Laser Guard: lined up with the nearest ship right now (it may set off the next volley early). */
    public boolean lined;
    /** Laser Guard: its sprite size before it is turned (the side-wall one is drawn turned a quarter). */
    public float spriteW, spriteH;

    /** Laser Guard: position along its wall (x, or y on the side wall) and the ship's, for lining up. */
    public float guardPos() { return guardSide == GUARD_WALL ? y + h / 2f : x + w / 2f; }

    /** Laser Guard: the tip of its gun (the laser comes out here), into out[0], out[1]. */
    public void guardMuzzle(float[] out) {
        if (guardSide == GUARD_WALL) { out[0] = x; out[1] = y + h / 2f; }
        else if (guardSide == GUARD_FLOOR) { out[0] = x + w / 2f; out[1] = y + h; }
        else { out[0] = x + w / 2f; out[1] = y; }
    }

    /**
     * Dome flyer (stage 2, sprites/green_flyer.png: 4 frames of 16x16, played 0 1 2 3 2 1 ...): the side-wall domes
     * send them out in waves of DOME_FLYER_COUNT (Hazard.updateDome). They fly in screen coordinates, so they
     * keep their place on the screen while it scrolls. Each one flies out of the dome's mouth to the bottom
     * of its circle (flyEx, flyEy) in flyLead seconds, then goes round the circle (centre flyCx, flyCy, radius
     * DOME_FLYER_R) DOME_FLYER_LOOPS times (counter-clockwise from a left-wall dome, clockwise from a right-wall
     * one), then flies straight on, away from its dome, and off the screen. The members leave the dome
     * DOME_FLYER_GAP seconds apart, so the 7 of a wave are spread evenly round the circle. Each one only starts
     * shooting (aimed shots) once it has flown its first circle.
     */
    public static final int DOME_FLYER_COUNT = 7, DOME_FLYER_LOOPS = 2;
    public static final float DOME_FLYER_R = 40f, DOME_FLYER_SPEED = 90f, DOME_FLYER_LEAD = 48f,
            DOME_FLYER_GAP = MathUtils.PI2 * DOME_FLYER_R / DOME_FLYER_COUNT / DOME_FLYER_SPEED,
            DOME_FLYER_FPS = 10f, DOME_FLYER_SHOT_INTERVAL = 2.2f, DOME_FLYER_SHOT_SPEED = 110f;
    /** Dome flyer: start (its dome's mouth), bottom of the circle, centre of the circle (all screen coordinates). */
    public float flySx, flySy, flyEx, flyEy, flyCx, flyCy, flyLead;
    /** Dome flyer: +1 = from a left-wall dome (goes round counter-clockwise, leaves to the right), -1 = mirrored. */
    public int flyDir = 1;

    /** Rugal: 0 = level, 1 = diving, 2 = climbing (45 degrees or more). */
    public int rugalFrame() { return vy <= -RUGAL_SPEED ? 1 : vy >= RUGAL_SPEED ? 2 : 0; }

    public boolean upper;
    /**
     * Height of the bottom bar of the Z as a part of the playfield height (the top bar mirrors it: 1 - frostLow).
     * Set from where the wave is placed in Tiled, so every wave can fly its own lines.
     */
    public float frostLow = 0.15f;
    public static final float FROST_LOW_MIN = 0.1f, FROST_LOW_MAX = 0.42f;
    /**
     * Speed along each axis (px/s): the diagonals move as fast across as along, as on the MSX (8 px steps; the
     * recording averages 78 px/s over the whole path, 89 px/s while the screen is quiet); 16 px apart.
     */
    public static final float FROST_SPEED = 80f, FROST_GAP = 16f / 80f;
    /** The path in screen coordinates (centre of the enemy): x as a part of the screen width, y of the playfield height. */
    // Horizontal path, measured frame by frame on the MSX (stage 1, waves 1-4; sprite centres, px from the side it
    // enters on): in on the entry line, left to FROST_TURN1 of the screen width, 45 degrees back toward the middle for
    // FROST_MID px, left to FROST_TURN2, 45 degrees back again until the sprite is flush with the far screen edge, then
    // right along that edge and out (the whole sprite stays on screen there).
    private static final float FROST_TURN1 = 0.64f, FROST_TURN2 = 0.28f, FROST_MID = 64f;
    private static final float[][] frostPts = new float[6][2];
    /**
     * Vertical stages (measured frame by frame in the stage 2 recording; px, sprite centres): down the column to
     * FROST_V_TURN below the top, 45 degrees up toward the middle for FROST_V_STEP px per place in the wave (the first
     * 16, the second 32, ...), straight down FROST_V_DROP, 45 degrees up toward the middle again until FROST_V_OUT
     * across from the entry column, then straight up and out. The wave turns from a column into a diagonal line.
     */
    private static final float FROST_V_TURN = 112f, FROST_V_STEP = 16f, FROST_V_DROP = 72f, FROST_V_OUT = 136f;
    /** Place in its wave (0 = first in); vertical stages use it for the path. */
    public int frostIdx;
    private static final float[] frostPos = new float[2];

    /** Screen position (centre) after flying `d` px along the Z. */
    private void frostAt(float d, float[] out) {
        float W = SalamanderGame.W, H = SalamanderGame.H;
        if (vertical) {                              // (unused by the vertical "V" below, which uses the real sizes)
            float tmp = W; W = H; H = tmp;
        }
        if (!vertical) {
            float e = frostLow * H, edge = H - h / 2f, mid = Math.min(e + FROST_MID, edge);
            float[][] p = frostPts;
            p[0][0] = 1.06f * W;                  p[0][1] = e;
            p[1][0] = FROST_TURN1 * W;            p[1][1] = e;
            p[2][0] = p[1][0] + (mid - e);        p[2][1] = mid;
            p[3][0] = FROST_TURN2 * W;            p[3][1] = mid;
            p[4][0] = p[3][0] + (edge - mid);     p[4][1] = edge;
            p[5][0] = 1.1f * W;                   p[5][1] = edge;
            for (int i = 0; i < p.length - 1; i++) {      // distance counted per axis (a diagonal step moves as far across as along)
                float len = Math.max(Math.abs(p[i + 1][0] - p[i][0]), Math.abs(p[i + 1][1] - p[i][1]));
                if (d <= len || i == p.length - 2) {
                    float k = len > 0f ? Math.min(1f, d / len) : 0f;
                    if (i == p.length - 2) k = len > 0f ? d / len : 0f;   // keeps going right after the last point
                    out[0] = p[i][0] + (p[i + 1][0] - p[i][0]) * k;
                    out[1] = p[i][1] + (p[i + 1][1] - p[i][1]) * k;
                    if (upper) out[1] = H - out[1];
                    return;
                }
                d -= len;
            }
        }
        // Vertical stages (stage 2 recording): see FROST_V_TURN. Screen sizes here are the real ones (W and H were
        // swapped above for the old Z). Distance counted per axis.
        float sw = SalamanderGame.W, sh = SalamanderGame.H, top = -h / 2f - 2f;
        float a0 = frostLow * sw, turn = FROST_V_TURN, k = FROST_V_STEP * (frostIdx + 1);
        float low = turn - k + FROST_V_DROP, outY = turn + FROST_V_DROP - FROST_V_OUT;
        float[][] p = frostPts;                        // (across from its own side, down from the top)
        p[0][0] = a0;                p[0][1] = top;
        p[1][0] = a0;                p[1][1] = turn;
        p[2][0] = a0 + k;            p[2][1] = turn - k;
        p[3][0] = a0 + k;            p[3][1] = low;
        p[4][0] = a0 + FROST_V_OUT;  p[4][1] = outY;
        p[5][0] = a0 + FROST_V_OUT;  p[5][1] = top;
        for (int i = 0; i < 5; i++) {
            float len = Math.max(Math.abs(p[i + 1][0] - p[i][0]), Math.abs(p[i + 1][1] - p[i][1]));
            if (d <= len || i == 4) {
                float across, down;
                if (i == 4) { across = p[4][0]; down = p[4][1] - d; }   // (keeps going up past the top)
                else {
                    float f = len > 0f ? d / len : 0f;
                    across = p[i][0] + (p[i + 1][0] - p[i][0]) * f;
                    down = p[i][1] + (p[i + 1][1] - p[i][1]) * f;
                }
                out[0] = upper ? sw - across : across;
                out[1] = sh - down;
                return;
            }
            d -= len;
        }
    }

    public Enemy(Type type, float x, float y, int levelIdx) {
        this.type = type; this.x = x; this.y = y; this.baseY = y;
        switch (type) {
            case LEGACY: hp = 1; score = 100; vx = -60f; break;
            case POSSESSED: hp = 1; score = 100; w = 16; h = 16; break;
            case WHIPLASH: hp = 1; score = 100; w = 15; h = 16; break;
            case BATTLEAXE: hp = 1; score = 100; w = 16; h = 16; break;
            case ABERRANT: hp = 1; score = 100; w = 16; h = 16; break;
            case NEST: hp = NEST_HP; score = 500; w = 32; h = 17; break;
            case CREEPING_DEATH: hp = 1; score = 100; w = 15; h = 15; break;
            case UG: hp = 1; score = 100; w = 16; h = 16; break;
            case GAMMA: hp = 1; score = 100; w = 16; h = 16; break;
            case GREEN_MINE: hp = 1; score = 100; w = 16; h = 16; break;
            case OPTION_EATER: hp = OEATER_HP; score = 300; w = 16; h = 16; break;
            case BLUE_DOME: hp = BLUE_DOME_HP; score = 500; w = 18; h = 32; break;   // (sprites/dome.png, as on stage 2)
            case GREEN_FLYER: hp = 1; score = 100; w = 16; h = 16; break;
            case LASER_BATTERY: hp = BATTERY_HP; score = 300; w = 24; h = 16; break;
            case GREMLIN: hp = 1; score = 200; w = 16; h = 16; break;
            case SEED: hp = SEED_HP; score = 1000; w = 20; h = 21; break;   // the middle of the base
            case FLYING_MINE: hp = 1; score = 100; w = 16; h = 16; break;
            case EXODUS: hp = 1; score = 100; w = 16; h = 15; break;
            case ARTILLERY: hp = 1; score = 300; w = 16; h = 16; break;   // (Albert, 2026-10-26: 1 hit, all of them)
            case BLUE_ASTEROID: hp = 1; score = 0; w = 34; h = 33; break;   // (can't be destroyed)
            case MOAI: hp = MOAI_HP; score = 1000; w = 50; h = 38; break;
            case ORB: hp = ORB_HP; score = 300; w = 18; h = 18; break;
            case CHAIN: hp = 1; score = 0; w = Enemy.CHAIN_BALL; h = Enemy.CHAIN_BALL; break;   // (can't be destroyed)
            case WALKER: hp = 2 + levelIdx / 2; score = 200; vx = -28f; break;
            case FROST:  hp = 1; score = 100; break;
            case LINK:   hp = 1; score = 100; break;
            case AMOEBA: hp = 1; score = 50; break;
            case RUGAL:  hp = 1; score = 100; break;
            case GUN_HO: hp = 3; score = 300; w = 26; h = 34; break;
            case HEATHEN: hp = 1; score = 0; w = 40; h = 120; break;
            case BLOCK_SMALL: hp = 1; score = 0; w = 32; h = 16; break;
            case BLOCK_BIG: hp = BLOCK_BIG_HP; score = 500; w = 32; h = 32; break;
            case RAGE: hp = 1; score = 100; w = 16; h = 15; break;
            case LED_ZEP: hp = 1; score = 100; w = 16; h = 16; break;
            case LASER_GUN: hp = 1; score = 0; w = 8; h = 8; break;
            case OMEN:   hp = 12; score = 200; w = 34; h = 18; break;   // (Albert, 2026-10-27)
            case LASER_GUARD: hp = 1; score = 0; w = 22; h = 20; break;   // (can't be destroyed)
            case DOME_FLYER: hp = 1; score = 100; break;
            case TURRET_FLOOR: case TURRET_CEIL: case TURRET_WALL: hp = 1; score = 300; break;   // every turret: 1 hit (Albert, 2026-10-27)
            default:     hp = 3 + levelIdx; score = 300; break;
        }
        shootT = MathUtils.random(0.6f, 1.8f);
    }

    /** Laser Guards, Kierke chain balls and Op 4 blue asteroids: shots stop on them, homing shots and guards ignore them. */
    public boolean indestructible() { return type == Type.LASER_GUARD || type == Type.CHAIN || type == Type.BLUE_ASTEROID || type == Type.HEATHEN || type == Type.BLOCK_SMALL
            || type == Type.LASER_GUN || type == Type.ORB || type == Type.RAGE; }   // (Op 4 orbs: only a guard can take one, see GameScreen.guardDamage; Rage: Albert, 2026-10-27)

    /**
     * Led Zep (Lavinia lower corridor; Albert's sprites/led_zep.png, 2 frames of 16x16). As in the MSX recording it
     * crawls along the floor or the ceiling (LEDZEP_WALK px/s across the screen, toward the side it is heading), and
     * after LEDZEP_WALK_MIN..MAX s it leaps away from its surface (LEDZEP_LEAP px/s, still moving sideways), always
     * right across to the other surface (Albert, 2026-10-27: no "half" jumps to the ship's height and back).
     * It doesn't shoot. Picture: frame 0 while it crawls, frame 1 while it is in the air (Albert).
     * They come at random (GameScreen.updateLedZeps, map object "ledzep_zone").
     */
    public static final float LEDZEP_WALK = 45f, LEDZEP_LEAP = 80f, LEDZEP_WALK_MIN = 0.7f, LEDZEP_WALK_MAX = 1.3f;
    /** Led Zep: on the ceiling, heading right, state (0 walk, 1 leap away, 2 back), the two surfaces (world y of its bottom). */
    public boolean zepCeil, zepRight;
    public int zepState;
    public float zepT, zepFloorY, zepCeilY, zepTurnY;
    private boolean zepCross;

    private void updateLedZep(GameScreen g, float dt) {
        float W = SalamanderGame.W;
        x += (g.scrollVX + (zepRight ? LEDZEP_WALK : -LEDZEP_WALK)) * dt;
        zepT -= dt;
        if (zepState == 0) {                                   // crawling along its surface
            y = zepCeil ? zepCeilY : zepFloorY;
            if (zepT > 0f) return;
            zepState = 1;
            zepCross = true;                                    // always all the way to the other surface
            zepTurnY = zepCeil ? zepFloorY : zepCeilY;
            return;
        }
        float home = zepCeil ? zepCeilY : zepFloorY;
        float target = zepState == 1 ? zepTurnY : home;
        float step = LEDZEP_LEAP * dt;
        if (Math.abs(target - y) <= step) {
            y = target;
            if (zepState == 1 && !zepCross) { zepState = 2; return; }   // reached the ship's height: back again
            if (zepState == 1) zepCeil = !zepCeil;                       // landed on the other surface
            zepState = 0;
            zepT = MathUtils.random(LEDZEP_WALK_MIN, LEDZEP_WALK_MAX);
            return;
        }
        y += Math.signum(target - y) * step;
    }

    /**
     * Laser gun point (Lavinia shaft; Albert's sprites/laser_gun_point.png, 8x8, pointing right; flip it in Tiled to
     * point left). Albert: unlike the MSX (a beam that is always on) it can't be destroyed and fires its laser in
     * short pulses, so a ship can slip through between them: every LGUN_PERIOD s (shifted by its "delay") the beam
     * grows out of the muzzle at LGUN_SPEED px/s to the rock across, stays LGUN_HOLD s, then its tail follows it into
     * the rock. The beam is the Laser Guards' red / blue pattern; it hurts a ship and lets shots through.
     * (Albert may want these to move like the Laser Guards at the end of the stage later.)
     */
    public static final float LGUN_PERIOD = 1.8f, LGUN_SPEED = 260f, LGUN_HOLD = 0.25f, LGUN_BEAM_H = 5f;
    public boolean lgunLeft;
    public float lgunDelay, lgunReach = -1f;

    private void updateLaserGun(GameScreen g, float dt) {
        beamW = 0f;
        if (!g.inView(x + w / 2f, y + h / 2f, 0f)) return;
        if (lgunReach < 0f) {                                   // how far the rock across is
            float by = y + h / 2f - LGUN_BEAM_H / 2f, x0 = lgunLeft ? x - 1f : x + w;
            int n = 0;
            while (n < 400 && !g.level.hits(lgunLeft ? x0 - n : x0 + n, by, 1f, LGUN_BEAM_H)) n++;
            lgunReach = n;
        }
        float travel = lgunReach / LGUN_SPEED;
        float u = (t + lgunDelay) % LGUN_PERIOD;
        float front, tail;
        if (u < travel) { front = u * LGUN_SPEED; tail = 0f; }
        else if (u < travel + LGUN_HOLD) { front = lgunReach; tail = 0f; }
        else if (u < 2f * travel + LGUN_HOLD) { front = lgunReach; tail = (u - travel - LGUN_HOLD) * LGUN_SPEED; }
        else return;                                           // off: the gap to slip through
        float len = front - tail;
        if (len <= 0f) return;
        beamY = y + h / 2f - LGUN_BEAM_H / 2f; beamH = LGUN_BEAM_H; beamW = len;
        beamX = lgunLeft ? x - front : x + w + tail;
    }

    /**
     * Lavinia blocks (Albert's sprites/lavinia_block_small.png 32x16, lavinia_block_big.png 32x32), as in the MSX
     * recording. Each waits until it is BLOCK_START px inside the right edge, then:
     * small: slides up and down between the ceiling and the floor at BLOCK_SMALL_SPEED px/s (it starts toward the far
     * side: the map's start position is one end of its run); it can't be destroyed and stops shots and lasers.
     * big: slides left through its slot at BLOCK_BIG_SPEED px/s through the world; BLOCK_BIG_HP hits destroy it. A laser
     * that hits it still hurts it, but is reflected back to the left (GameScreen.reflectLaser) and can hit a ship.
     */
    public static final float BLOCK_START = 32f, BLOCK_SMALL_SPEED = 40f, BLOCK_BIG_SPEED = 20f;
    public static final int BLOCK_BIG_HP = 20;
    /** Small block: its run (world y of its bottom, low and high end), direction (+1 up), started. */
    public float blockLo = Float.NaN, blockHi = Float.NaN, blockDir = 1f;
    public boolean blockOn;

    /**
     * Rage (Albert's sprites/rage.png, 16x15, faces left): launched by the Lavinia warship out of its open hatch. As in
     * the recording it flies straight left at RAGE_SPEED px/s across the screen; its laser (the red / blue enemy
     * laser pattern, RAGE_LASER_W px wide, through its middle) grows up and down from it at RAGE_LASER_GROW px/s each
     * way until it reaches the screen edges (or rock). The laser hurts a ship; shots pass it. 1 hit.
     */
    public static final float RAGE_SPEED = 77f, RAGE_LASER_GROW = 40f, RAGE_LASER_W = 5f, RAGE_LASER_X = 1f;
    public float rageUp, rageDown;

    private void updateBlock(GameScreen g, float dt) {
        if (!blockOn) {
            if (x > g.scrollX + SalamanderGame.W - BLOCK_START) return;   // waits until it is well on the screen
            blockOn = true;
        }
        if (type == Type.BLOCK_BIG) { x -= BLOCK_BIG_SPEED * dt; return; }
        if (Float.isNaN(blockLo)) { blockLo = y; blockHi = y; }
        y += blockDir * BLOCK_SMALL_SPEED * dt;
        if (y >= blockHi) { y = blockHi; blockDir = -1f; }
        if (y <= blockLo) { y = blockLo; blockDir = 1f; }
    }

    private void updateRage(GameScreen g, float dt) {
        x += (g.scrollVX - RAGE_SPEED) * dt;          // straight left across the screen
        float cy = y + h / 2f, bx = x + RAGE_LASER_X;
        float top = g.scrollY + SalamanderGame.H, bottom = g.scrollY;
        float up = rageUp + RAGE_LASER_GROW * dt, down = rageDown + RAGE_LASER_GROW * dt;
        up = Math.min(up, top - cy); down = Math.min(down, cy - bottom);
        if (!g.level.hits(bx, cy + rageUp, RAGE_LASER_W, Math.max(0f, up - rageUp))) rageUp = up;
        if (!g.level.hits(bx, cy - down, RAGE_LASER_W, Math.max(0f, down - rageDown))) rageDown = down;
        beamX = bx; beamY = cy - rageDown; beamW = RAGE_LASER_W; beamH = rageUp + rageDown;
    }

    /**
     * Heathen (Lavinia lower corridor, as in the MSX recording; Albert's sprites/heathen_top.png, heathen_center.png,
     * heathen_bottom.png, 40x120, and heathen_bar.png): a pillar as tall as the corridor that comes in at the right
     * edge and rushes left at HEATHEN_SPEED px/s across the screen (in 8 px steps on the MSX). It can't be destroyed:
     * only a gap with HEATHEN_BARS breakable bars (HEATHEN_BAR_HP hits each, like Tetran's core blocks but weaker)
     * lets a ship through once they are shot away; shots break the front bar first. Which of the three pictures
     * (where the gap is: heathenKind 0 = top, 1 = centre, 2 = bottom) is random every time one comes.
     */
    public static final float HEATHEN_SPEED = 100f;
    public static final int HEATHEN_BARS = 4, HEATHEN_BAR_HP = 3, HEATHEN_BAR_SCORE = 100;
    /** Bars: top row of each picture's bars (from the top of the picture), first bar's x, spacing and size. */
    public static final int[] HEATHEN_BAR_TOP = {19, 51, 83};
    public static final int HEATHEN_BAR_X = 10, HEATHEN_BAR_STEP = 8, HEATHEN_BAR_W = 4, HEATHEN_BAR_H = 18;
    public int heathenKind;
    public final int[] heathenBarHp = new int[HEATHEN_BARS];
    /** One key per bar, so a laser hurts each bar once. */
    public final Object[] heathenBarKey = {new Object(), new Object(), new Object(), new Object()};
    /** The pillar without its bars (its non-black pixels; set when it comes). */
    public PixelMask heathenMask;

    public float heathenBarX(int k) { return x + HEATHEN_BAR_X + k * HEATHEN_BAR_STEP; }
    public float heathenBarY() { return y + h - HEATHEN_BAR_TOP[heathenKind] - HEATHEN_BAR_H; }

    /** The front-most (leftmost) bar still there that the rectangle touches, or -1. */
    public int heathenBarAt(float rx, float ry, float rw, float rh) {
        float by = heathenBarY();
        if (ry >= by + HEATHEN_BAR_H || ry + rh <= by) return -1;
        for (int k = 0; k < HEATHEN_BARS; k++) {
            float bx = heathenBarX(k);
            if (heathenBarHp[k] > 0 && rx < bx + HEATHEN_BAR_W && rx + rw > bx) return k;
        }
        return -1;
    }

    /** Does the rectangle touch the pillar itself (not its gap)? */
    public boolean heathenBodyTouches(float rx, float ry, float rw, float rh) {
        if (rx >= x + w || rx + rw <= x || ry >= y + h || ry + rh <= y) return false;
        return heathenMask == null || heathenMask.overlaps(x, y, rx, ry, rw, rh);
    }

    /** Does the rectangle touch the pillar or a bar still there (what hurts a ship)? */
    public boolean heathenTouches(float rx, float ry, float rw, float rh) {
        return heathenBodyTouches(rx, ry, rw, rh) || heathenBarAt(rx, ry, rw, rh) >= 0;
    }

    public void update(GameScreen g, float dt) {
        t += dt;
        switch (type) {
            case POSSESSED:
                updatePossessed(g, dt);
                break;
            case WHIPLASH:
                updateWhiplash(g, dt);
                break;
            case BATTLEAXE:
                updateBattleaxe(g);
                break;
            case CHAIN:                                  // a ball of a Kierke chain: stays where it grew
            case ABERRANT:                               // (stays where it is placed, alive or withered)
            case NEST:                                   // (GameScreen.releaseNestBugs lets the bugs out)
                break;
            case CREEPING_DEATH:
                updateNestBug(g, dt);
                break;
            case UG:
                updateUg(g, dt);
                break;
            case GAMMA:
                updateGamma(g, dt);
                break;
            case BLUE_DOME:                              // (GameScreen.releaseNestBugs lets its flyers out)
                break;
            case GREEN_FLYER:
                updateGreenFlyer(g, dt);
                break;
            case LASER_BATTERY:                          // (its beam: GameScreen)
                break;
            case GREMLIN:
                updateGremlin(g, dt);
                break;
            case SEED:                                   // (GameScreen.releaseNestBugs lets its mines out)
                break;
            case FLYING_MINE:
                updateFlyingMine(g, dt);
                break;
            case EXODUS:
                updateExodus(g, dt);
                break;
            case ARTILLERY:
                updateArtillery(g, dt);
                break;
            case BLUE_ASTEROID:
                updateBlueAsteroid(g, dt);
                break;
            case MOAI:
                updateMoai(g, dt);
                break;
            case ORB:
                if (bubble) updateBubble(g, dt); else updateOrb(g, dt);
                break;
            case LEGACY: {                               // weaves along; fires back at a ship it has flown past
                if (hover) { updateHoverLegacy(g, dt); break; }
                Player tp = g.target(x + w / 2f, y + h / 2f);
                shootT -= dt;
                boolean past = tp != null && (vertical ? tp.y > y + h : tp.x > x + w);
                if (shootT <= 0f && past && canShoot(g)) {
                    shootT = LEGACY_SHOT_INTERVAL + MathUtils.random(0f, 0.6f);
                    g.aimedShot(x + w / 2f, y + h / 2f, 110f, 0f);
                }
                // (speeds relative to the screen: the scrolling is added on top)
                if (vertical) {                          // fly-by: down the screen, weaving left and right
                    y += (vx + g.scrollVY) * dt;
                    baseX += g.scrollVX * dt;
                    x = baseX + MathUtils.sin(t * 4f + phase) * 32f;
                    break;
                }
                x += (vx + g.scrollVX) * dt;
                baseY += g.scrollVY * dt;
                y = baseY + MathUtils.sin(t * 4f + phase) * 32f;
                break;
            }
            case WALKER: {   // walks along the floor (or ceiling), climbing / dropping straight at steps
                if (leapState != 0) { updateLeaper(g, dt); break; }
                if (leapGround) {                        // stage 1: a Kierke leaper standing on its surface
                    // it stands where it was placed (scrolling in with the rock) until it is on the screen; a runner
                    // from behind starts at once, just off the left edge
                    if (x < g.scrollX + SalamanderGame.W && x + w > g.scrollX - 30f) startLeapOnGround(g);
                    break;
                }
                // Once on screen it chases the nearest ship for CHASE_TIME seconds, then gives up and
                // walks off the left edge. Before that it keeps its starting direction.
                boolean onScreen = x + w > g.scrollX && x < g.scrollX + SalamanderGame.W;
                if (chaseT < 0f && onScreen) chaseT = 0f;
                if (chaseT >= 0f) chaseT += dt;
                if (g.level.msx() && updateWalkerJump(g, dt)) break;
                int dir = vx < 0f ? -1 : 1;
                float speed = Math.abs(vx);
                if (chaseT >= CHASE_TIME) {                       // give up: leave the screen
                    dir = -1;
                    speed = LEAVE_SPEED;
                } else if (chaseT >= 0f) {                        // chase the nearest ship along the floor
                    Player target = g.target(x + w / 2f, y + h / 2f);
                    if (target != null) {
                        float dx = (target.x + Player.W / 2f) - (x + w / 2f);
                        if (Math.abs(dx) < 6f) speed = 0f;        // right underneath / above it: wait
                        else { dir = dx < 0f ? -1 : 1; speed = CHASE_SPEED; }
                    }
                }
                boolean aiming = shootT <= WALKER_AIM_TIME && canShoot(g);
                if (aiming) speed = 0f;                           // stops to aim, then fires
                vx = dir * Math.max(speed, 0.01f);                // keeps the facing even when standing still
                // on screen its speed is relative to the screen: the scrolling is added on top, so it keeps up
                // with the ships. While it stands to aim it plants its feet: it stays on its spot of ground and
                // scrolls along with the terrain.
                float worldV = dir * speed + (chaseT >= 0f && !aiming ? g.scrollVX : 0f);
                int wdir = worldV < 0f ? -1 : 1;
                float wspeed = Math.abs(worldV);
                pos[0] = x; pos[1] = y;
                int r = ceiling ? FloorCrawler.stepCeiling(g.level, pos, w, h, wdir, wspeed, 40f, 999f, dt)
                                : FloorCrawler.step(g.level, pos, w, h, wdir, wspeed, 40f, 999f, dt);
                if (r != FloorCrawler.BLOCKED) { x = pos[0]; y = pos[1]; climb = r; }
                else climb = FloorCrawler.LEVEL;
                fireAimed(g, dt, 2.0f, 105f);
                break;
            }
            case RUGAL: {
                Player tp = g.target(x + w / 2f, y + h / 2f);
                float want = 0f;
                if (tp != null) {
                    float dy = (tp.y + Player.H / 2f) - (y + h / 2f);
                    if (Math.abs(dy) > 3f) want = Math.signum(dy) * RUGAL_CLIMB;
                }
                vy += (want - vy) * Math.min(1f, 4f * dt);
                x += (-RUGAL_SPEED + g.scrollVX) * dt;
                y += vy * dt;
                shootT -= dt;
                if (shootT <= 0f && tp != null && tp.x > x + w && canShoot(g)) {   // only once it has flown past the ship
                    shootT = RUGAL_SHOT_INTERVAL + MathUtils.random(0f, 0.6f);
                    g.aimedShot(x + w, y + h / 2f, 110f, 0f);   // back at it
                }
                break;
            }
            case GUN_HO: updateGunHo(g, dt); break;
            case HEATHEN: x += (g.scrollVX - HEATHEN_SPEED) * dt; break;   // rushes left across the screen
            case BLOCK_SMALL: case BLOCK_BIG: updateBlock(g, dt); break;
            case RAGE: updateRage(g, dt); break;
            case LED_ZEP: updateLedZep(g, dt); break;
            case LASER_GUN: updateLaserGun(g, dt); break;
            case DOME_FLYER: updateDomeFlyer(g, dt); break;
            case LASER_GUARD: updateLaserGuard(g, dt); break;
            case OMEN: {                                  // stationary: a burst every OMEN_REPEAT s while on screen
                if (!canShoot(g)) break;
                omenT += dt;                              // (only counts while it is on screen)
                if (omenT >= OMEN_DELAY + omenShots * OMEN_BURST_GAP) {
                    if (++omenShots >= OMEN_BURST) {     // burst done: the next one starts OMEN_REPEAT s from now
                        omenShots = 0;
                        omenT = OMEN_DELAY - OMEN_REPEAT;
                    }
                    g.laviniaRoom.omenLaser(x + w / 2f, ceiling ? y + 2f : y + h - 2f, !ceiling);   // from its orange top, away from the rock
                }
                break;
            }
            case AMOEBA: {
                vx -= vx * Math.min(1f, AMOEBA_DRAG * dt);        // the throw out of the tower dies down...
                vy -= vy * Math.min(1f, AMOEBA_DRAG * dt);
                float dx = (vx + MathUtils.sin(t * 0.9f + phase) * AMOEBA_DRIFT) * dt;        // ...and it drifts about
                float dy = (vy + MathUtils.cos(t * 1.3f + phase * 1.7f) * AMOEBA_DRIFT) * dt;
                x += dx;                                           // passes through rock freely
                y += dy;
                break;
            }
            case GREEN_MINE: updateGreenMine(g, dt); break;
            case OPTION_EATER: updateOptionEater(g, dt); break;
            case LINK:
                if (linkSwing > 0f) { updateLinkMsx(g, dt); break; }
                x += (g.scrollVX - LINK_SPEED) * dt;     // relative to the screen
                baseY += g.scrollVY * dt;
                y = baseY + MathUtils.sin(t * LINK_BOB_SPEED + phase) * LINK_BOB;
                fireAimed(g, dt, 2.2f, 105f);
                break;
            case FROST:
                frostAt(Math.max(0f, t) * FROST_SPEED, frostPos);
                x = g.scrollX + frostPos[0] - w / 2f;
                y = g.scrollY + frostPos[1] - h / 2f;
                break;
            case TURRET_WALL:
                aimWallTurret(g);
                fireAimed(g, dt, 2.4f, 115f);
                break;
            default:                                     // turrets: turn toward the nearest ship and shoot from the barrel
                aimTurret(g);
                fireAimed(g, dt, 2.4f, 115f);
                break;
        }
    }

    /** Kierke: this walker leaps in from a screen edge (1 = the left edge, -1 = the right edge); see LEAP_*. */
    public void startLeap(int fromSide) {
        leapState = LEAP_IN;
        leapDir = fromSide;
        vx = fromSide * 0.01f;
        vy = 0f;
        leapT = ceiling ? MathUtils.random(0f, 0.1f) : MathUtils.random(LEAP_GLIDE_MIN, LEAP_GLIDE_MAX);   // glide
    }

    /**
     * Stage 1 walker that behaves like a Kierke leaper (Albert, 2026-10-28): it starts on its surface, already landed,
     * and then walks / approaches / hops exactly as a leaper does after landing.
     */
    public boolean leapGround;

    private void startLeapOnGround(GameScreen g) {
        Player target = g.target(x + w / 2f, y + h / 2f);
        float shipDx = target == null ? 0f : (target.x + Player.W / 2f) - (x + w / 2f);
        vy = 0f;
        if (target != null && Math.abs(shipDx) > LEAP_APPROACH_DIST) leapState = LEAP_APPROACH;
        else startLeapWalk(shipDx);
    }

    /** Kierke leaper: walked off a screen edge after it had been on screen. */
    public boolean leaperGone(GameScreen g) {
        if (leapState == 0) return false;
        boolean inside = x + w > g.scrollX && x < g.scrollX + SalamanderGame.W;
        if (inside) { leapSeen = true; return false; }
        return leapSeen && leapState != LEAP_IN;
    }

    private void updateLeaper(GameScreen g, float dt) {
        Player target = g.target(x + w / 2f, y + h / 2f);
        float shipDx = target == null ? 0f : (target.x + Player.W / 2f) - (x + w / 2f);
        float cx = x + w / 2f;
        switch (leapState) {
            case LEAP_IN: {
                x += (leapDir * LEAP_IN_SPEED + g.scrollVX) * dt;
                leapT -= dt;
                if (leapT <= 0f) vy += (ceiling ? LEAP_GRAVITY : -LEAP_GRAVITY) * dt;   // toward its surface
                y += vy * dt;
                if (leapLanded(g, x + w / 2f)) {
                    leapState = target != null && Math.abs(shipDx) > LEAP_APPROACH_DIST ? LEAP_APPROACH : LEAP_WALK;
                    if (leapState == LEAP_WALK) startLeapWalk(shipDx);
                }
                break;
            }
            case LEAP_APPROACH: {
                if (target == null || Math.abs(shipDx) <= LEAP_APPROACH_DIST) { startLeapWalk(shipDx); break; }
                leapWalk(g, dt, shipDx < 0f ? -1 : 1, LEAP_APPROACH_SPEED);
                break;
            }
            case LEAP_WALK: {
                leapT -= dt;
                boolean blocked = leapWalk(g, dt, leapSide, leapSpeed);
                if ((leapT <= 0f || blocked) && target != null) startLeapHop(g, target, shipDx);
                break;
            }
            case LEAP_HOP: {
                if (Math.abs(shipDx) > 4f || leapSide != (shipDx < 0f ? -1 : 1)) x += leapSide * LEAP_HOP_DRIFT * dt;
                x += g.scrollVX * dt;                             // relative to the screen
                vy += (ceiling ? LEAP_GRAVITY : -LEAP_GRAVITY) * dt;
                y += vy * dt;
                float ncx = x + w / 2f;
                // flying away from its surface, it can reach the other one: it grabs it and walks on there
                if (!ceiling && vy > 0f) {
                    float cb = g.level.ceilBottom(ncx, y + h / 2f);
                    if (y + h >= cb) { y = cb - h; ceiling = true; vy = 0f; startLeapWalk(shipDx); }
                } else if (ceiling && vy < 0f) {
                    float fl = g.level.floorTop(ncx, y + h / 2f);
                    if (y <= fl) { y = fl; ceiling = false; vy = 0f; startLeapWalk(shipDx); }
                }
                if (leapState == LEAP_HOP && leapLanded(g, ncx)) startLeapWalk(shipDx);
                break;
            }
        }
        climb = leapState == LEAP_IN || leapState == LEAP_HOP ? FloorCrawler.LEVEL : climb;
        if (cx != x + w / 2f && leapState != LEAP_IN) vx = Math.signum((x + w / 2f) - cx) * 0.01f;
    }

    /** Kierke leaper, in the air: has it come down onto its own surface (floor, or ceiling for a ceiling walker)? */
    private boolean leapLanded(GameScreen g, float cx) {
        if (ceiling) {
            if (vy <= 0f) return false;
            float cb = g.level.ceilBottom(cx, y + h / 2f);
            if (y + h < cb) return false;
            y = cb - h;
        } else {
            if (vy >= 0f) return false;
            float fl = g.level.floorTop(cx, y + h / 2f);
            if (y > fl) return false;
            y = fl;
        }
        vy = 0f;
        return true;
    }

    private void startLeapWalk(float shipDx) {
        leapState = LEAP_WALK;
        leapSide = shipDx > 0f ? -1 : 1;                          // away from the ship
        leapSpeed = MathUtils.random(LEAP_WALK_MIN, LEAP_WALK_MAX);
        leapT = MathUtils.random(LEAP_WALK_TIME_MIN, LEAP_WALK_TIME_MAX);
    }

    private void startLeapHop(GameScreen g, Player target, float shipDx) {
        leapState = LEAP_HOP;
        leapSide = shipDx < 0f ? -1 : 1;                          // toward the ship...
        if (MathUtils.random() < LEAP_HOP_AWAY) leapSide = -leapSide;   // ...mostly
        float up = ceiling ? y - (target.y + Player.H / 2f) : (target.y + Player.H / 2f) - y;   // ship's height above its surface
        float hgt = Math.max(MathUtils.random(LEAP_HOP_MIN_H, LEAP_HOP_MAX_H), up * MathUtils.random(0.7f, 1.1f));
        vy = (ceiling ? -1f : 1f) * (float) Math.sqrt(2f * LEAP_GRAVITY * hgt);
        climb = FloorCrawler.LEVEL;
    }

    /** Kierke leaper on its surface: walks dir at speed px/s relative to the screen. Returns true if a wall stops it. */
    private boolean leapWalk(GameScreen g, float dt, int dir, float speed) {
        float worldV = dir * speed + g.scrollVX;
        pos[0] = x; pos[1] = y;
        int r = ceiling ? FloorCrawler.stepCeiling(g.level, pos, w, h, worldV < 0f ? -1 : 1, Math.abs(worldV), 40f, 999f, dt)
                        : FloorCrawler.step(g.level, pos, w, h, worldV < 0f ? -1 : 1, Math.abs(worldV), 40f, 999f, dt);
        if (r == FloorCrawler.BLOCKED) { climb = FloorCrawler.LEVEL; return true; }
        x = pos[0]; y = pos[1]; climb = r;
        return false;
    }

    /** MSX style walker leap (see WALKER_JUMP_*). Returns true while it is in the air (the normal walk is skipped). */
    private boolean updateWalkerJump(GameScreen g, float dt) {
        Player target = g.target(x + w / 2f, y + h / 2f);
        float out = ceiling ? -1f : 1f;                          // away from the surface it walks on
        if (jumping) {
            if (target != null) {
                float dx = (target.x + Player.W / 2f) - (x + w / 2f);
                if (Math.abs(dx) > 4f) { vx = Math.signum(dx) * 0.01f; x += Math.signum(dx) * WALKER_JUMP_DRIFT * dt; }
            }
            x += g.scrollVX * dt;                                // relative to the screen, like on the ground
            jumpV -= out * WALKER_JUMP_GRAVITY * dt;
            y += jumpV * dt;
            float cx = x + w / 2f;
            if (ceiling) {
                float fl = g.level.floorTop(cx, y);              // flying down: stop on a floor below
                if (y < fl) { y = fl; jumpV = 0f; }
                float cb = g.level.ceilBottom(cx, y + h);        // falling back up: land on the ceiling
                if (jumpV > 0f && y + h >= cb) { y = cb - h; land(); }
            } else {
                float cb = g.level.ceilBottom(cx, y + h);        // flying up: bump the ceiling
                if (y + h > cb) { y = cb - h; jumpV = Math.min(jumpV, 0f); }
                float fl = g.level.floorTop(cx, y + h / 2f);     // falling: land on the floor below
                if (jumpV < 0f && y <= fl) { y = fl; land(); }
            }
            fireAimed(g, dt, 2.0f, 105f);
            return true;
        }
        if (jumpWait < 0f) jumpWait = MathUtils.random(0.5f, WALKER_JUMP_WAIT_MAX);
        jumpWait -= dt;
        if (jumpWait > 0f || chaseT < 0f || chaseT >= CHASE_TIME || target == null) return false;
        float hgt = MathUtils.random(WALKER_JUMP_MIN_H, WALKER_JUMP_MAX_H);
        jumpV = out * (float) Math.sqrt(2f * WALKER_JUMP_GRAVITY * hgt);
        jumping = true; climb = FloorCrawler.LEVEL;
        return true;
    }

    private void land() {
        jumping = false; jumpV = 0f;
        jumpWait = MathUtils.random(WALKER_JUMP_WAIT_MIN, WALKER_JUMP_WAIT_MAX);
    }

    private void updateDomeFlyer(GameScreen g, float dt) {
        float px, py;
        float circle = DOME_FLYER_LOOPS * MathUtils.PI2 * DOME_FLYER_R;   // px flown round the circle in all
        if (t < flyLead) {                                // out of the mouth to the bottom of the circle
            float k = flyLead > 0f ? t / flyLead : 1f;
            px = flySx + (flyEx - flySx) * k;
            py = flySy + (flyEy - flySy) * k;
        } else {
            float d = (t - flyLead) * DOME_FLYER_SPEED;   // px flown since it reached the circle
            if (d < circle) {                             // round the circle, starting at its bottom
                float a = -MathUtils.HALF_PI + flyDir * d / DOME_FLYER_R;
                px = flyCx + MathUtils.cos(a) * DOME_FLYER_R;
                py = flyCy + MathUtils.sin(a) * DOME_FLYER_R;
            } else {                                      // done: straight on, away from its dome, off the screen
                px = flyEx + flyDir * (d - circle);
                py = flyEy;
            }
            // shoots only once it has flown its first circle
            if (d >= MathUtils.PI2 * DOME_FLYER_R) {
                shootT -= dt;
                if (shootT <= 0f) {
                    shootT = DOME_FLYER_SHOT_INTERVAL + MathUtils.random(0f, 1.2f);
                    if (canShoot(g)) g.aimedShot(x + w / 2f, y + h / 2f, DOME_FLYER_SHOT_SPEED, 0f);
                }
            }
        }
        x = g.scrollX + px - w / 2f;
        y = g.scrollY + py - h / 2f;
    }

    private void updateLaserGuard(GameScreen g, float dt) {
        lined = false;
        if (g.roomRetreating()) {                         // slides back into its wall, then is gone
            retreat = Math.min(1f, retreat + dt / GUARD_RETREAT_TIME);
            placeGuard();
            if (retreat >= 1f) dead = true;
            return;
        }
        if (!canShoot(g)) return;                         // waits until it is on screen
        if (windT > 0f) {                                 // stopped, about to fire
            windT -= dt;
            if (windT <= 0f) g.laviniaRoom.guardBeam(this);
            return;
        }
        Player tp = g.target(x + w / 2f, y + h / 2f);
        float shipPos = tp == null ? guardPos() : guardSide == GUARD_WALL ? tp.y + 8f : tp.x + 16f;
        float half = (guardSide == GUARD_WALL ? h : w) / 2f;
        if (chasing) {                                    // chosen after the last volley: stays lined up with the ship
            guardGoal = MathUtils.clamp(shipPos, moveMin + half, moveMax - half);
        } else if (Math.abs(guardGoal - guardPos()) < 1f) {   // got there: pick the next random spot
            guardGoal = MathUtils.random(moveMin + half, moveMax - half);
        }
        boolean firing = g.laviniaRoom.guardBeamOnScreen(this);       // stands still while its laser is on screen
        float d = guardGoal - guardPos(), speed = chasing ? GUARD_ALIGN_SPEED : GUARD_SPEED;
        float step = firing ? 0f : Math.signum(d) * Math.min(Math.abs(d), speed * dt);
        if (guardSide == GUARD_WALL) y += step; else x += step;
        // (the guards fire together: GameScreen.updateGuardVolley starts their wind-up, see windT)
        lined = !firing && chasing && tp != null && Math.abs(shipPos - guardPos()) < 3f;
    }

    /** Laser Guard: puts it against its wall line, pulled back into the wall by `retreat` (0..1). */
    public void placeGuard() {
        float in = retreat * (guardSide == GUARD_WALL ? w : h);
        if (guardSide == GUARD_CEIL) y = wallLine - h + in;
        else if (guardSide == GUARD_FLOOR) y = wallLine - in;
        else x = wallLine - w + in;
    }

    private void updateGunHo(GameScreen g, float dt) {
        gunT += dt;
        Player tp = g.target(x + w / 2f, y + GUNHO_GUN_Y);
        switch (gunState) {
            case GUNHO_HOPPING: {                         // hops out of its hatch (screen-relative)
                x += g.scrollVX * dt;
                float[] legs = {GUNHO_HOP_UP, GUNHO_HOP_LEFT1, GUNHO_HOP_UP, GUNHO_HOP_LEFT2};
                if (Float.isNaN(gunHopLeft)) gunHopLeft = legs[0];
                boolean vert = gunHopLeg % 2 == 0;
                float step = Math.min(gunHopLeft, (vert ? GUNHO_HOP_VY : GUNHO_HOP_VX) * dt);
                if (gunHopLeg == 0) y += step; else if (gunHopLeg == 2) y -= step; else x -= step;
                gunHopLeft -= step;
                if (gunHopLeft <= 0f) {
                    gunHopLeg++;
                    if (gunHopLeg >= legs.length) { gunState = GUNHO_AIMING; gunT = 0f; }
                    else gunHopLeft = legs[gunHopLeg];
                }
                break;
            }
            case GUNHO_COMING:                            // scrolls in with the terrain until it is well on screen
                if (x + w <= g.scrollX + SalamanderGame.W - 8f && g.inView(x + w / 2f, y + h / 2f, 6f)) {
                    gunState = GUNHO_AIMING; gunT = 0f;
                }
                break;
            case GUNHO_AIMING: {
                x += g.scrollVX * dt;                     // keeps its place on the screen
                if (tp == null) break;
                faceRight = tp.x + 16f > x + w;           // turns to a ship behind it
                float dy = (tp.y + 8f) - (y + GUNHO_GUN_Y);
                if (Math.abs(dy) <= GUNHO_ALIGN) {        // lined up: fire its burst
                    y += dy;
                    g.gunHoBeam(faceRight ? x + w : x, y + GUNHO_GUN_Y, faceRight ? GUNHO_BEAM_SPEED : -GUNHO_BEAM_SPEED);
                    gunShots = 1;
                    gunState = GUNHO_BURST > 1 ? GUNHO_FIRING : GUNHO_FIRED; gunT = 0f;
                    break;
                }
                float step = Math.signum(dy) * Math.min(Math.abs(dy), GUNHO_CLIMB * dt);
                boolean stuckNow = g.level.hits(x, y, w, h);   // (already overlapping the rock: let it get out)
                if (stuckNow || !g.level.hits(x, y + step, w, h)) y += step;
                if (gunT >= GUNHO_GIVE_UP) { gunState = GUNHO_LEAVING; gunT = 0f; }
                break;
            }
            case GUNHO_FIRING:                            // the rest of its burst, from where it is
                x += g.scrollVX * dt;
                if (gunT >= GUNHO_BURST_GAP) {
                    g.gunHoBeam(faceRight ? x + w : x, y + GUNHO_GUN_Y, faceRight ? GUNHO_BEAM_SPEED : -GUNHO_BEAM_SPEED);
                    gunT = 0f;
                    if (++gunShots >= GUNHO_BURST) gunState = GUNHO_FIRED;
                }
                break;
            case GUNHO_FIRED:                             // a short pause after the shot
                x += g.scrollVX * dt;
                if (gunT >= GUNHO_HOLD) { gunState = GUNHO_LEAVING; gunT = 0f; faceRight = false; }
                break;
            default:                                      // flies off the left edge (through rock too)
                faceRight = false;
                x += (-(Level.originalStyle ? GUNHO_LEAVE_MSX : GUNHO_LEAVE) + g.scrollVX) * dt;
                break;
        }
    }

    private void fireAimed(GameScreen g, float dt, float interval, float speed) {
        shootT -= dt;
        if (shootT <= 0f) {
            shootT = interval + MathUtils.random(0f, 0.8f);
            if (!canShoot(g)) return;
            if (type == Type.TURRET_WALL) {
                // barrel tip (sprite pixels, y up from the bottom, facing right): sideways (15, 8), diagonal (14, 1 / 15)
                float mx = aim == WALL_SIDE ? 15f : 14f, my = aim == WALL_SIDE ? 8f : aim == WALL_DOWN ? 1f : 15f;
                g.aimedShot(x + (wallRight ? w - mx : mx), y + my, speed, 0f);
            } else if (type == Type.TURRET_FLOOR || type == Type.TURRET_CEIL) {
                // barrel tip (sprite pixels, y up from the base): up = (8, 16), diagonal = (2 or 14, 13)
                float mx = aim == 0 ? 8f : aim < 0 ? 2f : 14f, my = aim == 0 ? 16f : 13f;
                if (type == Type.TURRET_CEIL) my = 16f - my;
                g.aimedShot(x + mx, y + my, speed, 0f);
            } else {
                g.aimedShot(x + w / 2, y + h / 2, speed, 0f);
            }
        }
    }

    /** Barrel straight up (down on the ceiling) when the ship is mostly above (below) it, else diagonally toward it. */
    private void aimTurret(GameScreen g) {
        Player p = g.target(x + 8f, y + 8f);
        if (p == null) return;
        float dx = (p.x + 16f) - (x + 8f), dy = (p.y + 8f) - (y + 8f);
        if (type == Type.TURRET_CEIL) dy = -dy;
        aim = dy > 0f && Math.abs(dx) < dy * 0.6f ? 0 : dx < 0f ? -1 : 1;
    }

    /** Wall turret: straight out when the ship is roughly level with it, else diagonally up / down toward it. */
    private void aimWallTurret(GameScreen g) {
        Player p = g.target(x + 8f, y + 8f);
        if (p == null) return;
        float dx = (p.x + 16f) - (x + 8f), dy = (p.y + 8f) - (y + 8f);
        if (wallRight) dx = -dx;                     // (facing left)
        aim = dx > 0f && Math.abs(dy) < dx * 0.5f ? WALL_SIDE : dy < 0f ? WALL_DOWN : WALL_UP;
    }

    /** Only shoots while it is on screen (its middle at least a few px inside the edges). */
    // ================================================================ Op 4 (stage 6), from the stage 6 recording

    /**
     * Exodus (Albert's sprites/exodus_cyan.png / exodus_red.png, 3 frames of 16x15). They come in threes, side by side
     * EXODUS_GAP px apart, in at the top edge (the recording: columns 16 / 34 / 52 or 208 / 226 / 244 on the screen).
     * Each drops straight down at EXODUS_DROP px/s until its top is EXODUS_DROP_TO px inside the screen, then makes
     * for the nearest ship, moving at EXODUS_SPEED px/s on each axis (as the MSX does: straight, sideways or at 45
     * degrees), relative to the screen. After EXODUS_CHASE s it gives up and drops out through the bottom. They don't
     * shoot. A red one leaves a power capsule.
     */
    public static final float EXODUS_GAP = 18f, EXODUS_DROP = 80f, EXODUS_DROP_TO = 24f, EXODUS_SPEED = 42f,
            EXODUS_CHASE = 10f, EXODUS_FRAME = 0.12f;
    public boolean exRed;
    private float exSx = Float.NaN, exSy, exVx, exVy;

    private void updateExodus(GameScreen g, float dt) {
        float H = SalamanderGame.H;
        if (Float.isNaN(exSx)) { exSx = x - g.scrollX; exSy = H; }    // (sprite bottom-left on the screen, y up)
        float top = H - (exSy + h);                                     // how far its top is inside the screen
        if (top < EXODUS_DROP_TO && t < EXODUS_CHASE) {
            exSy -= EXODUS_DROP * dt;
        } else if (t < EXODUS_CHASE) {
            Player p = g.target(x + w / 2f, y + h / 2f);
            exVx = 0f; exVy = 0f;
            if (p != null) {
                float dx = (p.x + Player.W / 2f) - (x + w / 2f), dy = (p.y + Player.H / 2f) - (y + h / 2f);
                if (Math.abs(dx) > 3f) exVx = Math.signum(dx) * EXODUS_SPEED;
                if (Math.abs(dy) > 3f) exVy = Math.signum(dy) * EXODUS_SPEED;
            } else exVy = -EXODUS_SPEED;
            exSx += exVx * dt; exSy += exVy * dt;
        } else {                                                        // gives up: out through the bottom
            exSy -= EXODUS_DROP * dt;
        }
        x = g.scrollX + exSx; y = g.scrollY + exSy;
    }

    /** Its frame: cycles 0 1 2. */
    public int exodusFrame() { return (int) (Math.max(0f, t) / EXODUS_FRAME) % 3; }

    /**
     * Artillery (Albert's sprites/artillery_green.png, artillery_red.png: one 16x16 frame pointing up), the gun on the
     * shaft walls. Drawn turned a quarter so it points away from its wall (artRight = on the right wall). As in the
     * recording it stays put and fires an aimed shot ARTILLERY_FIRST s after it has come on screen, then every
     * ARTILLERY_PERIOD s, at ARTILLERY_SHOT px/s. A red one leaves a power capsule. It takes as many hits as the
     * floor / ceiling turrets (3 + levelIdx; Albert, 2026-10-25).
     */
    public static final float ARTILLERY_FIRST = 1.4f, ARTILLERY_PERIOD = 1.3f, ARTILLERY_SHOT = 80f;
    public boolean artRight, artRed;
    /** Artillery on a floor (points up) or a ceiling (points down) instead of a wall (Lavinia). */
    public boolean artFloor, artCeil;
    private float artT = -1f;

    private void updateArtillery(GameScreen g, float dt) {
        if (!canShoot(g)) return;
        if (artT < 0f) artT = ARTILLERY_FIRST;
        if ((artT -= dt) > 0f) return;
        artT = ARTILLERY_PERIOD;
        // not aimed (Albert, 2026-10-30): always the same spread, by turns a 3-way (straight out from its wall / floor /
        // ceiling toward the opposite side, and 45 degrees to either side) and a 4-way (no middle shot: 22.5 and 67.5
        // degrees to either side, so its shots are 45 degrees apart too)
        float mx, my, out;                                // its muzzle, and straight out (radians)
        if (artFloor || artCeil) { mx = x + w / 2f; my = artCeil ? y + 2f : y + h - 2f; out = artCeil ? -MathUtils.HALF_PI : MathUtils.HALF_PI; }
        else { mx = artRight ? x + 2f : x + w - 2f; my = y + h / 2f; out = artRight ? MathUtils.PI : 0f; }
        float[] spread = artFour ? ART_SPREAD_4 : ART_SPREAD_3;
        for (float d : spread) g.enemyBullet(mx, my, out + d * MathUtils.degreesToRadians, ARTILLERY_SHOT);
        artFour = !artFour;
    }

    /** Artillery's two spreads (degrees from straight out). */
    public static final float[] ART_SPREAD_3 = {-45f, 0f, 45f}, ART_SPREAD_4 = {-67.5f, -22.5f, 22.5f, 67.5f};
    private boolean artFour;

    /**
     * Blue asteroid (sprites/blue_asteroid.png, the blue rock clump cut from the map): it sinks slowly down through the world (the
     * recording: 5 to 32 px/s, a different speed for each) once it is on the screen. It can't be destroyed (Albert, 2026-10-25): shots stop on it with a spark, as on the
     * Laser Guards, and homing shots ignore it.
     */
    public static final float BLUE_ASTEROID_SINK_MIN = 5f, BLUE_ASTEROID_SINK_MAX = 30f;
    /**
     * Sink speed (px/s; < 0 = a random one, Op 4) and sideways drift (px/s, + = right). Stage 2 (recording, 119-130 s):
     * Tiled properties "sink" / "drift" per rock; three sink straight down (1-14 px/s), two cut across diagonally.
     */
    public float blueAsteroidSink = -1f, blueAsteroidDrift;

    private void updateBlueAsteroid(GameScreen g, float dt) {
        if (y > g.scrollY + SalamanderGame.H) return;                  // waits until it is on the screen
        if (blueAsteroidSink < 0f) blueAsteroidSink = MathUtils.random(BLUE_ASTEROID_SINK_MIN, BLUE_ASTEROID_SINK_MAX);
        y -= blueAsteroidSink * dt;
        x += blueAsteroidDrift * dt;
    }

    /**
     * Moai (sprites/moai.png, 50x36, cut from the map; it faces right, on the left wall; moaiRight = on the right wall,
     * mirrored). As in the recording (171-210 s) it fires rings at the nearest ship from its mouth: bursts of
     * MOAI_BURST, MOAI_RING_GAP s apart, a burst every MOAI_PERIOD s while its mouth is on screen; the rings fly at
     * MOAI_RING_SPEED px/s, each aimed when it leaves, and a whole burst is yellow or white (by turns). Most moai heads
     * (moaiWalk) also step away from their wall: MOAI_WAIT s at the wall, out MOAI_OUT px at MOAI_STEP px/s, back.
     * Albert (2026-10-26): sprites/moai.png has 2 frames of 50x38, mouth shut and mouth open. It starts against its wall
     * (GameScreen snaps it there). Its mouth is open from MOAI_OPEN_LEAD s before a burst until MOAI_OPEN_LEAD s after
     * it, and only the open mouth (MOAI_MOUTH: x, y from the bottom-left, w, h, facing right) takes damage; shots that
     * hit the rest of it just stop. Its rings can be shot down (GameScreen.collide).
     */
    public static final float MOAI_OPEN_LEAD = 0.3f;
    private static final float[] MOAI_MOUTH = {12f, 0f, 19f, 14f};
    public boolean moaiOpen;

    /** Does the rectangle touch its open mouth (the only place it can be hurt)? */
    public boolean moaiMouthHit(float rx, float ry, float rw, float rh) {
        if (!moaiOpen) return false;
        float mx = moaiRight ? x + w - MOAI_MOUTH[0] - MOAI_MOUTH[2] : x + MOAI_MOUTH[0];
        return rx < mx + MOAI_MOUTH[2] && rx + rw > mx && ry < y + MOAI_MOUTH[1] + MOAI_MOUTH[3] && ry + rh > y + MOAI_MOUTH[1];
    }
    public static final int MOAI_HP = Hazard.BAMDA_HP,   // as tough as stage 1's bamdas (Albert, 2026-10-25)
            MOAI_BURST = 6;
    public static final float MOAI_RING_GAP = 0.4f, MOAI_PERIOD = 3.7f, MOAI_RING_SPEED = 52f,
            MOAI_WAIT = 1f, MOAI_OUT = 32f, MOAI_STEP = 24f;
    public boolean moaiRight, moaiWalk = true;
    public float moaiWallX;
    private float moaiT = -1f, moaiMoveT, moaiOff;
    private int moaiLeft, moaiBursts;
    /** Which ring of the current burst is red (it leaves a power capsule when shot down; Albert, 2026-10-27). */
    private int moaiRedIdx;

    private void updateMoai(GameScreen g, float dt) {
        if (moaiWalk && y < g.scrollY + SalamanderGame.H) {             // steps out from its wall and back
            moaiMoveT += dt;
            float out = MOAI_OUT / MOAI_STEP, cycle = MOAI_WAIT + 2f * out, k = moaiMoveT % cycle;
            moaiOff = k < MOAI_WAIT ? 0f : k < MOAI_WAIT + out ? (k - MOAI_WAIT) * MOAI_STEP : MOAI_OUT - (k - MOAI_WAIT - out) * MOAI_STEP;
            x = moaiWallX + (moaiRight ? -moaiOff : moaiOff);
        }
        float mx = moaiMouthX(), my = y - 6f;
        if (!g.inView(mx, my, 2f)) { moaiOpen = false; return; }
        if (moaiT < 0f) { moaiT = MOAI_OPEN_LEAD; moaiLeft = MOAI_BURST; }   // (opens its mouth first)
        float pause = MOAI_PERIOD - MOAI_RING_GAP * (MOAI_BURST - 1);
        moaiT -= dt;
        // open during a burst, and MOAI_OPEN_LEAD s before / after it
        moaiOpen = moaiLeft > 0 || moaiT < MOAI_OPEN_LEAD || moaiT > pause - MOAI_OPEN_LEAD;
        if (moaiT > 0f) return;
        if (moaiLeft <= 0) { moaiLeft = MOAI_BURST; moaiBursts++; }
        if (moaiLeft == MOAI_BURST) moaiRedIdx = MathUtils.random(MOAI_BURST - 1);   // one red ring per burst
        moaiLeft--;
        moaiT = moaiLeft > 0 ? MOAI_RING_GAP : MOAI_PERIOD - MOAI_RING_GAP * (MOAI_BURST - 1);
        g.ringShot(mx, my, MOAI_RING_SPEED, (moaiBursts & 1) == 1, MOAI_BURST - 1 - moaiLeft == moaiRedIdx);
    }

    /** Where its rings come out (x, world): just under its mouth. */
    private float moaiMouthX() { return moaiRight ? x + 41f : x + 17f; }

    /**
     * Orb (sprites/orb.png, 18x18; Tiled class "orb6"), the blue balls of the side room (117-150 s in the recording).
     * It comes in at the top edge and bounces up and down fast (ORB_VY px/s, through the world) between the screen's
     * top and bottom edges and the rock, drifting sideways toward the nearest ship at ORB_VX px/s (bouncing off rock).
     * After ORB_LIFE s it stops bouncing off the bottom edge and leaves.
     */
    public static final int ORB_HP = 3;
    public static final float ORB_VY = 150f, ORB_VX = 12f, ORB_LIFE = 15f;

    private void updateOrb(GameScreen g, float dt) {
        if (t <= dt) {
            vy = -ORB_VY;
            Player p = g.target(x + w / 2f, y + h / 2f);
            vx = (p != null && p.x + Player.W / 2f < x + w / 2f) ? -ORB_VX : ORB_VX;
        }
        Level lv = g.level;
        float nx = x + vx * dt;
        if (lv.hits(nx, y + 2f, w, h - 4f)) vx = -vx; else x = nx;
        float ny = y + vy * dt;
        if (lv.hits(x + 2f, ny, w - 4f, h)) vy = -vy;
        else {
            y = ny;
            if (vy > 0f && y + h > g.scrollY + SalamanderGame.H && t > 0.5f) { y = g.scrollY + SalamanderGame.H - h; vy = -vy; }
            if (vy < 0f && y < g.scrollY && t < ORB_LIFE) { y = g.scrollY; vy = -vy; }
        }
    }

    /**
     * Eioneus boss room bubble (the same orb.png; let out by the gun pods, GameScreen.spawnBubble). From the MSX
     * recording (227-258 s): it moves diagonally at BUBBLE_VX / BUBBLE_VY px/s and bounces off the rock, the screen's
     * edges and the line under the pods (bubbleTop, world y). Like the Op 4 orb it can't be shot; a guard takes it.
     */
    public boolean bubble;
    public float bubbleTop;
    public static final float BUBBLE_VX = 57f, BUBBLE_VY = 44f;

    private void updateBubble(GameScreen g, float dt) {
        Level lv = g.level;
        float nx = x + vx * dt;
        if (lv.hits(nx, y + 2f, w, h - 4f) || (vx < 0f && nx < g.scrollX) || (vx > 0f && nx + w > g.scrollX + SalamanderGame.W)) vx = -vx;
        else x = nx;
        float ny = y + vy * dt;
        if (lv.hits(x + 2f, ny, w - 4f, h) || (vy < 0f && ny < g.scrollY) || (vy > 0f && ny + h > bubbleTop)) vy = -vy;
        else y = ny;
    }

    /** MSX Missing Link (see LINK_MSX_*): baseY = the middle of its swing. */
    private void updateLinkMsx(GameScreen g, float dt) {
        x += (-LINK_MSX_SPEED + g.scrollVX) * dt;
        float c = MathUtils.cos(t * MathUtils.PI2 / linkPeriod()) * linkSwing / 2f;
        y = linkUp ? baseY - c : baseY + c;
        if (linkFireAt < 0f) linkFireAt = MathUtils.random(LINK_MSX_FIRE_MIN, LINK_MSX_FIRE_MAX);
        if (linkFired < linkBurst && t >= linkFireAt + linkFired * LINK_MSX_BURST_GAP) {
            linkFired++;
            if (canShoot(g)) {
                g.aimedShot(x + w / 2f, y + h / 2f, LINK_MSX_SHOT, 0f);   // (relative to the screen, like all enemy shots)
            }
        }
    }

    /** MSX Missing Link: seconds per swing (top - bottom - top). */
    private float linkPeriod() { return MathUtils.PI * linkSwing / LINK_MSX_VMAX; }

    /** MSX Missing Link picture: 0 at the top / bottom of the swing, 1, 2, 3 half-way. */
    public int linkFrame() {
        float a = (t * MathUtils.PI2 / linkPeriod()) % MathUtils.PI;
        float d = Math.min(a, MathUtils.PI - a) / MathUtils.HALF_PI;   // 0 at an end of the swing, 1 half-way
        return Math.min(3, (int) (d * 4f));
    }

    private void updateGreenMine(GameScreen g, float dt) {
        Player p = g.target(x + w / 2f, y + h / 2f);
        if (p != null) {
            float want = MathUtils.atan2(p.y + Player.H / 2f - (y + h / 2f), p.x + Player.W / 2f - (x + w / 2f));
            if (Float.isNaN(gmAng)) gmAng = want;
            float d = want - gmAng;
            while (d > MathUtils.PI) d -= MathUtils.PI2;
            while (d < -MathUtils.PI) d += MathUtils.PI2;
            gmAng += MathUtils.clamp(d, -GMINE_TURN * dt, GMINE_TURN * dt);
        } else if (Float.isNaN(gmAng)) gmAng = vertical ? (gmFromBottom ? MathUtils.HALF_PI : -MathUtils.HALF_PI) : MathUtils.PI;
        if (vertical) {                                  // relative to the screen (it scrolls up)
            if (Float.isNaN(gmSx)) { gmSx = x - g.scrollX; gmSy = y - g.scrollY; }
            gmSx += MathUtils.cos(gmAng) * GMINE_SPEED * dt;
            gmSy += MathUtils.sin(gmAng) * GMINE_SPEED * dt;
            x = g.scrollX + gmSx; y = g.scrollY + gmSy;
            return;
        }
        x += (MathUtils.cos(gmAng) * GMINE_SPEED + g.scrollVX) * dt;
        y += MathUtils.sin(gmAng) * GMINE_SPEED * dt;
    }

    /**
     * Option eater (sprites/option_eater.png: 2 frames of 16x16, white and red, swapped every OEATER_FLASH s; the
     * lattice mine seen under Op 4's brain sphere in the recording, 232.5-233.5 s). It comes in through the bottom edge
     * and makes for the nearest ship's last option (the ship itself if it has none) at OEATER_SPEED px/s, turning at
     * most OEATER_TURN rad/s, relative to the screen. An option it touches is eaten (GameScreen.eatOption): the ship
     * loses it, and the eater is used up. Takes OEATER_HP hits.
     */
    public static final int OEATER_HP = 2;
    public static final float OEATER_SPEED = 30f, OEATER_TURN = 2.2f, OEATER_FLASH = 0.067f, OEATER_LIFE = 14f;
    private float oeSx = Float.NaN, oeSy, oeAng = MathUtils.HALF_PI;

    private void updateOptionEater(GameScreen g, float dt) {
        if (Float.isNaN(oeSx)) { oeSx = x - g.scrollX; oeSy = y - g.scrollY; }
        Player p = g.target(x + w / 2f, y + h / 2f);
        if (p != null && t < OEATER_LIFE) {
            float tx = p.x + Player.W / 2f, ty = p.y + Player.H / 2f;
            if (p.options > 0) { tx = g.optX(p, p.options - 1) + 6f; ty = g.optY(p, p.options - 1) + 6f; }
            float want = MathUtils.atan2(ty - (y + h / 2f), tx - (x + w / 2f));
            float d = want - oeAng;
            while (d > MathUtils.PI) d -= MathUtils.PI2;
            while (d < -MathUtils.PI) d += MathUtils.PI2;
            oeAng += MathUtils.clamp(d, -OEATER_TURN * dt, OEATER_TURN * dt);
        }
        oeSx += MathUtils.cos(oeAng) * OEATER_SPEED * dt;
        oeSy += MathUtils.sin(oeAng) * OEATER_SPEED * dt;
        x = g.scrollX + oeSx; y = g.scrollY + oeSy;
        if (t > 0.5f) g.op4.eatOption(this);
    }

    /** Its frame: 0 = white, 1 = red. */
    public int oeFrame() { return (int) (Math.max(0f, t) / OEATER_FLASH) % 2; }

    private boolean canShoot(GameScreen g) {
        return g.inView(x + w / 2f, y + h / 2f, 6f);
    }
}
