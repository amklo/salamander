package com.example.salamander;

import java.util.ArrayList;

/** Every projectile: player shots/lasers/missiles and enemy bullets/lava. World coordinates. */
public class Bullet {
    public enum Kind { SHOT, LASER, MISSILE, ENEMY, LAVA }

    public final Kind kind;
    public float x, y, w, h, vx, vy, gravity, t;
    public int dmg = 1;
    public boolean dead, grounded;
    /** Skip movement on the frame the bullet is created, so it is first drawn where it was fired. */
    public boolean fresh = true;
    /** Ground-crawling missile: FloorCrawler.LEVEL / CLIMBING / DROPPING (drawn tilted 45 degrees). */
    public int climb;
    public Player owner;             // player bullets: who gets the points
    public ArrayList<Object> hits;   // lasers pierce: remember what they already hurt

    public Bullet(Kind kind, float x, float y, float w, float h) {
        this.kind = kind; this.x = x; this.y = y; this.w = w; this.h = h;
        if (kind == Kind.LASER) hits = new ArrayList<>();
    }

    private static final float CRAWL_SPEED = 170f, CLIMB_SPEED = 140f;
    /** Laser: fired short, its front races ahead while its tail lags until it is this long. */
    public static final float LASER_START_LEN = 8f, LASER_MAX_LEN = 80f, LASER_GROW = 300f;

    public void update(float dt, Level lv) {
        if (fresh) { fresh = false; return; }
        t += dt;
        if (kind == Kind.MISSILE) {
            if (!grounded) fly(dt, lv);
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
        float ft = lv.floorTop(x + w / 2);
        if (y <= ft) {            // landed: from now on crawl along the terrain
            grounded = true;
            y = ft;
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
}
