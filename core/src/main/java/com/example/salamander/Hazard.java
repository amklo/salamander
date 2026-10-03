package com.example.salamander;

import com.badlogic.gdx.math.MathUtils;

/** Environmental dangers: falling stalactites, erupting volcanoes and piston crushers. */
public class Hazard {
    public enum Type { ROCK, VOLCANO, CRUSHER }

    public final Type type;
    public float x, y, w, h, vy, t, timer;
    public int hp;
    public boolean dead, falling, fromCeil;
    public float phase, minLen, maxLen, speed;

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
                    if (y < g.level.floorTop(x + 8f)) {
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
            default:
                updateCrusher(g.level);
                break;
        }
    }

    public void updateCrusher(Level lv) {
        float len = minLen + (maxLen - minLen) * (0.5f + 0.5f * MathUtils.sin(t * speed + phase));
        h = len;
        if (fromCeil) y = lv.ceilBottom(x + 16f) - len;
        else y = lv.floorTop(x + 16f);
    }
}
