package com.example.salamander;

import com.badlogic.gdx.math.MathUtils;

public class Enemy {
    public enum Type { FAN, RUSHER, WALKER, TURRET_FLOOR, TURRET_CEIL, FROST, LINK, AMOEBA, RUGAL }

    /** Red "carrier" waves drop a power-up capsule when every member is shot down. */
    public static class Group {
        public int size, killed;
        public boolean carrier, lost;
    }

    public final Type type;
    public float x, y, w = 16, h = 16, vx, vy, baseY, t, shootT, phase;
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
    private final float[] pos = new float[2];
    public Group group;
    /**
     * Turrets: where the barrel points. -1 = diagonally left, 0 = straight up (down on the ceiling), 1 = diagonally
     * right. The bio turret sprites have a "left" and an "up" frame; right is the left frame mirrored.
     */
    public int aim = -1;

    /**
     * Celtic Frost: flies a "Z" across the screen, independent of the scrolling. In from the right edge low on the
     * screen, left along the bottom bar to A, diagonally up and back to the right to B, then left along the top bar
     * and out the left edge. "upper" ones fly the same path mirrored top to bottom. The six of a group follow each
     * other on the same path, FROST_GAP seconds apart (t starts negative for the ones still waiting).
     */
    /** Missing Link: drifts left on its own (one at a time), bobbing up and down, and shoots at the ships. */
    public static final float LINK_SPEED = 40f, LINK_BOB = 50f, LINK_BOB_SPEED = 2.6f, LINK_FPS = 12f;

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

    /** Rugal: 0 = level, 1 = diving, 2 = climbing (45 degrees or more). */
    public int rugalFrame() { return vy <= -RUGAL_SPEED ? 1 : vy >= RUGAL_SPEED ? 2 : 0; }

    public boolean upper;
    /**
     * Height of the bottom bar of the Z as a part of the playfield height (the top bar mirrors it: 1 - frostLow).
     * Set from where the wave is placed in Tiled, so every wave can fly its own lines.
     */
    public float frostLow = 0.15f;
    public static final float FROST_LOW_MIN = 0.1f, FROST_LOW_MAX = 0.42f;
    public static final float FROST_SPEED = 135f, FROST_GAP = 0.11f;   // ~15 px apart
    /** The path in screen coordinates (centre of the enemy): x as a part of the screen width, y of the playfield height. */
    private static final float[][] FROST_PATH = {{1.06f, 0f}, {0.2f, 0f}, {0.8f, 1f}, {-0.1f, 1f}};   // y: 0 = bottom bar, 1 = top bar
    private static final float[] frostPos = new float[2];

    /** Screen position (centre) after flying `d` px along the Z. */
    private void frostAt(float d, float[] out) {
        float W = SalamanderGame.W, H = SalamanderGame.H;
        for (int i = 0; i < FROST_PATH.length - 1; i++) {
            float lo = frostLow * H, hi = (1f - frostLow) * H;
            float x0 = FROST_PATH[i][0] * W, y0 = lo + FROST_PATH[i][1] * (hi - lo);
            float x1 = FROST_PATH[i + 1][0] * W, y1 = lo + FROST_PATH[i + 1][1] * (hi - lo);
            float len = (float) Math.hypot(x1 - x0, y1 - y0);
            if (d <= len || i == FROST_PATH.length - 2) {
                float k = len > 0f ? d / len : 0f;
                out[0] = x0 + (x1 - x0) * k;
                out[1] = y0 + (y1 - y0) * k;
                if (upper) out[1] = H - out[1];
                return;
            }
            d -= len;
        }
    }

    public Enemy(Type type, float x, float y, int levelIdx) {
        this.type = type; this.x = x; this.y = y; this.baseY = y;
        switch (type) {
            case FAN:    hp = 1; score = 100; vx = -60f; break;
            case RUSHER: hp = 1; score = 150; vx = -150f; w = 18; h = 12; break;
            case WALKER: hp = 2 + levelIdx / 2; score = 200; vx = -28f; break;
            case FROST:  hp = 1; score = 100; break;
            case LINK:   hp = 1; score = 100; break;
            case AMOEBA: hp = 1; score = 50; break;
            case RUGAL:  hp = 1; score = 100; break;
            default:     hp = 3 + levelIdx; score = 300; break;
        }
        shootT = MathUtils.random(0.6f, 1.8f);
    }

    public void update(GameScreen g, float dt) {
        t += dt;
        switch (type) {
            case FAN:
                x += vx * dt;
                y = baseY + MathUtils.sin(t * 4f + phase) * 32f;
                break;
            case RUSHER:
                x += vx * dt;
                Player p = g.target(x, y);           // home in on the nearest ship
                if (p != null && x > p.x + 50f) {
                    float dy = (p.y + 4f) - y;
                    if (Math.abs(dy) > 2f) y += Math.signum(dy) * 60f * dt;
                }
                break;
            case WALKER: {   // walks along the floor (or ceiling), climbing / dropping straight at steps
                // Once on screen it chases the nearest ship for CHASE_TIME seconds, then gives up and
                // walks off the left edge. Before that it keeps its starting direction.
                boolean onScreen = x + w > g.scrollX && x < g.scrollX + SalamanderGame.W;
                if (chaseT < 0f && onScreen) chaseT = 0f;
                if (chaseT >= 0f) chaseT += dt;
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
            case AMOEBA: {
                vx -= vx * Math.min(1f, AMOEBA_DRAG * dt);        // the throw out of the tower dies down...
                vy -= vy * Math.min(1f, AMOEBA_DRAG * dt);
                float dx = (vx + MathUtils.sin(t * 0.9f + phase) * AMOEBA_DRIFT) * dt;        // ...and it drifts about
                float dy = (vy + MathUtils.cos(t * 1.3f + phase * 1.7f) * AMOEBA_DRIFT) * dt;
                x += dx;                                           // passes through rock freely
                y += dy;
                break;
            }
            case LINK:
                x -= LINK_SPEED * dt;
                y = baseY + MathUtils.sin(t * LINK_BOB_SPEED + phase) * LINK_BOB;
                fireAimed(g, dt, 2.2f, 105f);
                break;
            case FROST:
                frostAt(Math.max(0f, t) * FROST_SPEED, frostPos);
                x = g.scrollX + frostPos[0] - w / 2f;
                y = g.scrollY + frostPos[1] - h / 2f;
                break;
            default:                                     // turrets: turn toward the nearest ship and shoot from the barrel
                aimTurret(g);
                fireAimed(g, dt, 2.4f, 115f);
                break;
        }
    }

    private void fireAimed(GameScreen g, float dt, float interval, float speed) {
        shootT -= dt;
        if (shootT <= 0f) {
            shootT = interval + MathUtils.random(0f, 0.8f);
            if (!canShoot(g)) return;
            if (type == Type.TURRET_FLOOR || type == Type.TURRET_CEIL) {
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

    /** Only shoots while it is on screen (its middle at least a few px inside the edges). */
    private boolean canShoot(GameScreen g) {
        return g.inView(x + w / 2f, y + h / 2f, 6f);
    }
}
