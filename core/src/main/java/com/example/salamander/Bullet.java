package com.example.salamander;

import java.util.ArrayList;

/** Every projectile: player shots/lasers/missiles and enemy bullets/lava. World coordinates. */
public class Bullet {
    public enum Kind { SHOT, LASER, RIPPLE, MISSILE, ENEMY, LAVA }

    public final Kind kind;
    public float x, y, w, h, vx, vy, gravity, t;
    public int dmg = 1;
    public boolean dead, grounded;
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
    public ArrayList<Object> hits;   // lasers pierce: remember what they already hurt

    public Bullet(Kind kind, float x, float y, float w, float h) {
        this.kind = kind; this.x = x; this.y = y; this.w = w; this.h = h;
        if (kind == Kind.LASER) hits = new ArrayList<>();
    }

    private static final float CRAWL_SPEED = 170f, CLIMB_SPEED = 140f;
    /** Laser: fired short, its front races ahead while its tail lags until it is this long. */
    public static final float LASER_START_LEN = 8f, LASER_MAX_LEN = 320f, LASER_GROW = 320f;

    /** Ripple laser: an oval ring that starts small and widens (mostly vertically) as it travels. */
    public static final float RIPPLE_START_W = 4f, RIPPLE_START_H = 8f, RIPPLE_MAX_W = 14f, RIPPLE_MAX_H = 48f,
            RIPPLE_GROW_TIME = 0.35f, RIPPLE_SPEED = 480f;

    public void update(float dt, Level lv) {
        if (fresh) { fresh = false; return; }
        t += dt;
        if (kind == Kind.RIPPLE) {
            // grow around the ring's centre so it stays on the line it was fired along
            float k = Math.min(1f, t / RIPPLE_GROW_TIME);
            float nw = RIPPLE_START_W + (RIPPLE_MAX_W - RIPPLE_START_W) * k;
            float nh = RIPPLE_START_H + (RIPPLE_MAX_H - RIPPLE_START_H) * k;
            x -= (nw - w) / 2f; y -= (nh - h) / 2f;
            w = nw; h = nh;
        }
        if (kind == Kind.MISSILE) {
            if (up) { if (!grounded) flyUp(dt, lv); else crawlCeiling(dt, lv); }
            else if (!grounded) fly(dt, lv);
            else crawl(dt, lv);
            return;
        }
        if (kind == Kind.LASER && w < LASER_MAX_LEN) {
            // the front keeps full speed, the tail moves slower, so the beam stretches out as it travels
            float grow = Math.min(LASER_GROW * dt, LASER_MAX_LEN - w);
            w += grow;
            x -= grow;
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
        if (y <= ft) {            // landed: from now on crawl along the terrain
            if (ft - y > 8f) { dead = true; return; }   // flew into a wall, not onto a floor
            grounded = true;
            y = ft;
        } else if (lv.hits(x, y, w, h)) {
            dead = true;
        }
    }

    /** Upward missile rising from the ship until it touches the ceiling. */
    private void flyUp(float dt, Level lv) {
        x += vx * dt;
        y += vy * dt;
        float cb = lv.ceilBottom(x + w / 2, y + h - 1f);
        if (y + h >= cb) {        // reached the ceiling: from now on crawl along it
            if (y + h - cb > 8f) { dead = true; return; }   // flew into a wall, not up to a ceiling
            grounded = true;
            y = cb - h;
        } else if (lv.hits(x, y, w, h)) {
            dead = true;
        }
    }

    private final float[] pos = new float[2];

    /** Missile crawling along the floor (see FloorCrawler): climbs / drops straight at steps. */
    private void crawl(float dt, Level lv) {
        pos[0] = x; pos[1] = y;
        int r = FloorCrawler.step(lv, pos, w, h, +1, CRAWL_SPEED, CLIMB_SPEED, 48f, dt);
        if (r == FloorCrawler.BLOCKED) { dead = true; return; }   // wall too tall / no room
        x = pos[0]; y = pos[1];
        climb = r;
    }

    /** Upward missile hanging from the ceiling, crawling along it (see FloorCrawler.stepCeiling). */
    private void crawlCeiling(float dt, Level lv) {
        pos[0] = x; pos[1] = y;
        int r = FloorCrawler.stepCeiling(lv, pos, w, h, +1, CRAWL_SPEED, CLIMB_SPEED, 48f, dt);
        if (r == FloorCrawler.BLOCKED) { dead = true; return; }
        x = pos[0]; y = pos[1];
        climb = r;
    }
}
