package com.example.salamander;

import java.util.ArrayList;

/** Every projectile: player shots/lasers/missiles and enemy bullets/lava. World coordinates. */
public class Bullet {
    public enum Kind { SHOT, LASER, RIPPLE, MISSILE, ENEMY, LAVA }

    public final Kind kind;
    public float x, y, w, h, vx, vy, gravity, t;
    public int dmg = 1;
    /** Damage of one laser beam. It hits each target once, but hard: tough enemies go down in fewer beams. */
    public static final int LASER_DAMAGE = 3;
    /** Damage of one ripple ring. */
    public static final int RIPPLE_DAMAGE = 3;
    public boolean dead, grounded;
    /** Enemy shot drawn as a straight laser beam (Gun Ho) instead of the spinning ebullet. */
    public boolean beam;
    /** A ship's laser reflected by a Lavinia big block: drawn like the ships' laser, it hurts the ships. */
    public boolean reflected;
    /** Op 4 moai ring (sprites/ring_laser.png): 1 = yellow, 2 = white. */
    public int ring;
    /**
     * Omen's laser: flies straight up / down and widens sideways around anchorX (its middle, where the picture's
     * pattern is anchored). Only its middle (the width it started with) is stopped by rock.
     */
    public boolean omenLaser;
    /**
     * Laser Guard's laser (GameScreen.steerGuardBeams): the guard that fires it, its direction (Enemy.GUARD_CEIL =
     * down, GUARD_FLOOR = up, GUARD_WALL = left), and its front / tail along that direction. Rock stops its front.
     */
    public Enemy guard;
    public int beamDir;
    public float beamFront, beamTail, beamSide;
    public boolean beamAttached;
    public float anchorX, startW;
    /** Skip movement on the frame the bullet is created, so it is first drawn where it was fired. */
    public boolean fresh = true;
    /** Ground-crawling missile: FloorCrawler.LEVEL / CLIMBING / DROPPING (drawn tilted 45 degrees). */
    public int climb;
    public Player owner;             // player bullets: who gets the points
    /** Player bullets: which gun fired it (0 = the ship, 1..4 = option 1..4), for the on-screen limits. */
    public int src;
    /** DOUBLE's angled shot: has its own on-screen limit, separate from the forward shots. */
    public boolean angled;
    /** 2-WAY's upward missile: flies up and crawls along the ceiling. */
    public boolean up;
    /**
     * Vertical stage: fired upward. A laser grows in height (and follows its gun sideways), a ripple widens sideways,
     * a missile flies diagonally up and to the right (2-WAY's second one: to the left) and crawls up along rock walls.
     */
    public boolean vert;
    /**
     * Vertical missile ran into rock (or bricks) and stops right there: GameScreen blasts the bricks it touches and
     * then bursts it.
     */
    public boolean burst;
    /**
     * HOMING missile (ship type 3): launched like 2-WAY's and flies the same diagonal until there is an enemy to go
     * after; then GameScreen sets {@link #chasing} and the target point (tx, ty) every frame and it flies at it.
     * It never crawls: it explodes on whatever it touches, terrain (map collision) included.
     */
    public boolean homing;
    /** HOMING missile that has left the 2-WAY path and is flying at a target (it never goes back to crawling). */
    public boolean chasing;
    /** HOMING: there is a target this frame, and where its middle is (world pixels). */
    public boolean hasTarget;
    public float tx, ty;
    /** HOMING: what it is chasing (an Enemy, Hazard or Boss), kept until it dies or leaves the screen. */
    public Object target;
    /** Enemy bullet fired from inside rock (a rock eye): rock doesn't stop it for this many seconds. */
    public float terrainGrace;
    /** Vertical missile: how far it has stepped away from the wall to get past rock in front of it. */
    private float sideStep;
    public ArrayList<Object> hits;   // lasers pierce: remember what they already hurt

    public Bullet(Kind kind, float x, float y, float w, float h) {
        this.kind = kind; this.x = x; this.y = y; this.w = w; this.h = h;
        if (kind == Kind.LASER) hits = new ArrayList<>();
    }

    /**
     * Missile speed in px/s, the one number to tune: used for flying forward and down / up (a 45 degree dive),
     * for crawling along the floor / ceiling, and for climbing / dropping at steps.
     */
    public static final float MISSILE_SPEED = 100f;
    /** HOMING missile while chasing: speed in px/s and how fast it can turn (degrees per second). */
    public static final float HOMING_SPEED = 150f, HOMING_TURN = 360f;
    /** HOMING missile while chasing: small square hit box (it turns as it goes). */
    public static final float HOMING_BOX = 4f;
    /** Speed of the normal shot and of DOUBLE's angled shot (px/s). */
    public static final float SHOT_SPEED = 400f;
    /** Laser: length when fired; its front then races ahead at LASER_SPEED (px/s). */
    public static final float LASER_START_LEN = 8f, LASER_SPEED = 540f;
    /**
     * Laser still fixed to its gun: the tail stays at the muzzle (GameScreen.steerLasers moves it there every frame)
     * while the front races ahead, so the beam grows out of the ship. Once the front reaches the edge of the screen
     * GameScreen lets go of it and the whole beam flies on (still following its gun up and down) and off the screen.
     */
    public boolean attached;
    /**
     * Laser that ran into something it can't go through (rock, a tower column, armour...): its front stays pinned at
     * the point of impact while its tail keeps flying at LASER_SPEED, so the beam shrinks into that point and is gone.
     * It no longer follows its gun.
     */
    public boolean stuck;

    /**
     * Ripple laser: starts as sprites/ripple.png (8x16) and stretches up and down as it travels, up to the full
     * playfield height, wherever terrain doesn't block it (GameScreen grows it). RIPPLE_GROW = px/s, both ends together:
     * fired from the left edge it is about 80% of the screen height when it reaches the right edge.
     */
    public static final float RIPPLE_START_W = 8f, RIPPLE_START_H = 16f, RIPPLE_GROW = 110f, RIPPLE_SPEED = 240f;

    public void update(float dt, Level lv) {
        if (fresh) { fresh = false; return; }
        t += dt;
        if (kind == Kind.MISSILE) {
            if (burst) return;                    // ran into something: GameScreen blows it up this frame
            if (homing && hasTarget && !chasing) startChase();
            if (chasing) { chase(dt, lv); return; }
            if (vert) wallMove(dt, lv);
            else if (up) { if (!grounded) flyUp(dt, lv); else crawlCeiling(dt, lv); }
            else if (!grounded) fly(dt, lv);
            else crawl(dt, lv);
            // HOMING explodes on terrain where a normal missile would just vanish
            if (homing && dead) { dead = false; burst = true; }
            return;
        }
        if (kind == Kind.LASER && stuck) {        // pinned at the impact point: only the tail moves, the beam shrinks
            if (vert) { float d = Math.min(vy * dt, h); y += d; h -= d; }
            else { float d = Math.min(vx * dt, w); x += d; w -= d; }
            if (w <= 0f || h <= 0f) dead = true;
            return;
        }
        if (kind == Kind.LASER && attached) {     // growing out of the gun: only the front moves
            if (vert) h += vy * dt;               // (vertical stage: grows upward)
            else w += vx * dt;
            return;
        }
        vy -= gravity * dt;
        x += vx * dt;
        y += vy * dt;
    }

    /** Missile dropping from the ship until it touches the floor. */
    private void fly(float dt, Level lv) {
        x += vx * dt;
        y += vy * dt;
        float ft = lv.floorTop(x + w / 2, y + 1f);
        if (homing && (y <= ft || lv.hitsMap(x, y, w, h))) { burst = true; return; }   // HOMING: explodes on impact
        if (y <= ft) {            // landed: from now on crawl along the terrain
            if (ft - y > 8f) { dead = true; return; }   // flew into a wall, not onto a floor
            grounded = true;
            y = ft;
        } else if (lv.hitsMap(x, y, w, h)) {
            dead = true;
        }
    }

    /** Upward missile rising from the ship until it touches the ceiling. */
    private void flyUp(float dt, Level lv) {
        x += vx * dt;
        y += vy * dt;
        float cb = lv.ceilBottom(x + w / 2, y + h - 1f);
        if (homing && (y + h >= cb || lv.hitsMap(x, y, w, h))) { burst = true; return; }   // HOMING: explodes on impact
        if (y + h >= cb) {        // reached the ceiling: from now on crawl along it
            if (y + h - cb > 8f) { dead = true; return; }   // flew into a wall, not up to a ceiling
            grounded = true;
            y = cb - h;
        } else if (lv.hitsMap(x, y, w, h)) {
            dead = true;
        }
    }

    /**
     * Vertical stage missile: flies diagonally up and to the side (right, or left for 2-WAY's second missile) until it
     * touches a rock wall, then crawls straight up along it. Without rock on that side it flies off the side of the
     * screen (the screen edges are not walls). Where the wall ends it flies diagonally again (around
     * the corner); rock right in front of it makes it step away from the wall (up to 48 px) to get past.
     * vx / vy keep the direction it is moving in (for drawing).
     */
    private void wallMove(float dt, Level lv) {
        if (burst) return;
        int dir = up ? -1 : 1;
        float dist = MISSILE_SPEED * dt;
        int n = Math.max(1, (int) Math.ceil(dist));
        float st = dist / n, d = st * 0.7071f;
        for (int k = 0; k < n; k++) {
            // the sides of the map are the sides of the screen: no rock there, the missile just flies off
            if (x + w < 0f || x > lv.worldW) { dead = true; return; }
            if (!grounded) {
                x += dir * d;
                if (x < 0f || x + w > lv.worldW) { y += d; vx = dir; vy = 1f; continue; }   // flying out past the side
                if (lv.hitsMap(x, y, w, h)) {                      // reached a wall
                    x -= dir * d;
                    if (homing) { burst = true; return; }          // HOMING: explodes on impact, doesn't climb
                    grounded = true; sideStep = 0f;
                }
                y += d;
                if (lv.hitsMap(x, y, w, h)) { y -= d; burst = true; return; }                 // flew into rock
                vx = grounded ? 0f : dir; vy = 1f;
            } else if (!lv.hitsMap(x, y + st, w, h)) {                // crawl up along the wall
                y += st;
                sideStep = 0f;
                vx = 0f; vy = 1f;
                if (!lv.hitsMap(x + dir, y, w, h)) grounded = false;  // the wall ends: around the corner
            } else {                                               // rock in front: step away from the wall
                x -= dir * st;
                sideStep += st;
                vx = -dir; vy = 0f;
                if (sideStep > 48f || lv.hitsMap(x, y, w, h)) { x += dir * st; burst = true; return; }
            }
        }
    }

    /** HOMING: leaves the floor / ceiling / wall and switches to a small square hit box, centred where it was. */
    private void startChase() {
        chasing = true;
        grounded = false;
        climb = FloorCrawler.LEVEL;
        float cx = x + w / 2f, cy = y + h / 2f;
        w = h = HOMING_BOX;
        x = cx - w / 2f; y = cy - h / 2f;
        if (vx == 0f && vy == 0f) vx = 1f;
    }

    /**
     * HOMING: turns toward the target (at most HOMING_TURN degrees per second) and flies at HOMING_SPEED. Without a
     * target (it was destroyed) it keeps going straight. Any rock it touches makes it burst.
     */
    private void chase(float dt, Level lv) {
        float ang = (float) Math.atan2(vy, vx);
        if (hasTarget) {
            float want = (float) Math.atan2(ty - (y + h / 2f), tx - (x + w / 2f));
            float diff = want - ang;
            while (diff > Math.PI) diff -= 2f * (float) Math.PI;
            while (diff < -Math.PI) diff += 2f * (float) Math.PI;
            float maxTurn = HOMING_TURN * (float) Math.PI / 180f * dt;
            ang += Math.max(-maxTurn, Math.min(maxTurn, diff));
        }
        vx = (float) Math.cos(ang) * HOMING_SPEED;
        vy = (float) Math.sin(ang) * HOMING_SPEED;
        float dist = HOMING_SPEED * dt;
        int n = Math.max(1, (int) Math.ceil(dist / 2f));   // small steps: never skips through thin rock
        for (int k = 0; k < n; k++) {
            x += vx * dt / n;
            y += vy * dt / n;
            if (lv.hitsMap(x, y, w, h)) { burst = true; return; }
        }
    }

    private final float[] pos = new float[2];

    /** Missile crawling along the floor (see FloorCrawler): climbs / drops straight at steps. */
    private void crawl(float dt, Level lv) {
        pos[0] = x; pos[1] = y;
        int r = FloorCrawler.step(lv, pos, w, h, +1, MISSILE_SPEED, MISSILE_SPEED, 48f, dt);
        if (r == FloorCrawler.BLOCKED) { dead = true; return; }   // wall too tall / no room
        x = pos[0]; y = pos[1];
        climb = r;
    }

    /** Upward missile hanging from the ceiling, crawling along it (see FloorCrawler.stepCeiling). */
    private void crawlCeiling(float dt, Level lv) {
        pos[0] = x; pos[1] = y;
        int r = FloorCrawler.stepCeiling(lv, pos, w, h, +1, MISSILE_SPEED, MISSILE_SPEED, 48f, dt);
        if (r == FloorCrawler.BLOCKED) { dead = true; return; }
        x = pos[0]; y = pos[1];
        climb = r;
    }
}
