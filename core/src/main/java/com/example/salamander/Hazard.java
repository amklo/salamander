package com.example.salamander;

import com.badlogic.gdx.math.MathUtils;

/** Environmental dangers: falling stalactites, floating asteroids, moving teeth, erupting volcanoes and piston crushers. */
public class Hazard {
    public enum Type { ROCK, VOLCANO, CRUSHER, ASTEROID, FANG }

    /** Rocks and asteroids can be shot to pieces and hurt the ship on contact. */
    public boolean shootable() { return type == Type.ROCK || type == Type.ASTEROID; }

    public final Type type;
    public float x, y, w, h, vy, t, timer;
    public int hp;
    public boolean dead, falling, fromCeil;
    public float phase, minLen, maxLen, speed;
    // FANG (stage 1 teeth): position when fully out (CRUSHER: mount surface), start delay, cycle length, picture, exact shape
    public float baseY, delay, period;
    public String image;             // FANG / ASTEROID picture
    public PixelMask mask;
    /** Hidden teeth sit this much deeper than their own length, so no tip peeks out of the rock. */
    public static final float FANG_TUCK = 16f;

    public Hazard(Type type) { this.type = type; }

    public void update(GameScreen g, float dt) {
        t += dt;
        float sx = g.scrollX;
        switch (type) {
            case ROCK:
                if (!falling) {
                    // drops when a ship flies underneath
                    if (x > sx && x < sx + SalamanderGame.W) {
                        for (Player p : g.players) {
                            if (GameScreen.alive(p) && Math.abs((p.x + 16f) - (x + 8f)) < 58f) falling = true;
                        }
                    }
                } else {
                    vy -= 430f * dt;
                    y += vy * dt;
                    if (y < g.level.floorTop(x + 8f, y + 8f)) {
                        dead = true;
                        g.boom(x + 8f, y + 8f, 0.8f);
                    }
                }
                break;
            case VOLCANO:
                timer -= dt;
                if (timer <= 0f && x > sx - 10f && x < sx + SalamanderGame.W + 10f) {
                    timer = MathUtils.random(1.8f, 3.0f);
                    int n = MathUtils.random(3, 5);
                    for (int i = 0; i < n; i++) {
                        Bullet b = new Bullet(Bullet.Kind.LAVA, x - 4f, y, 8, 8);
                        b.vx = MathUtils.random(-75f, 75f);
                        b.vy = MathUtils.random(230f, 340f);
                        b.gravity = 420f;
                        g.eBullets.add(b);
                    }
                }
                break;
            case ASTEROID:   // floats in place
                break;
            case FANG:       // hidden in the ground / ceiling, then juts out, holds, and pulls back in
                y = baseY + (fromCeil ? 1f : -1f) * (1f - fangOut()) * (h + FANG_TUCK);
                break;
            default:
                updateCrusher();
                break;
        }
    }

    /** How far a tooth is out: 0 = hidden, 1 = fully out. */
    private float fangOut() {
        if (t < delay) return 0f;
        float u = ((t - delay) % period) / period;
        if (u < 0.2f) return smooth(u / 0.2f);              // jut out
        if (u < 0.45f) return 1f;                           // hold
        if (u < 0.65f) return 1f - smooth((u - 0.45f) / 0.2f);   // pull back
        return 0f;                                          // stay hidden
    }

    private static float smooth(float v) { return v * v * (3f - 2f * v); }

    /** FANG: exact overlap test against the tooth's shape. */
    public boolean touches(float rx, float ry, float rw, float rh) {
        if (rx > x + w || rx + rw < x || ry > y + h || ry + rh < y) return false;
        return mask == null || mask.overlaps(x, y, rx, ry, rw, rh);
    }

    /** Crusher: baseY is the surface it is mounted on (ceiling underside or floor top). */
    public void updateCrusher() {
        float len = minLen + (maxLen - minLen) * (0.5f + 0.5f * MathUtils.sin(t * speed + phase));
        h = len;
        y = fromCeil ? baseY - len : baseY;
    }
}
