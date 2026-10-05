package com.example.salamander;

import com.badlogic.gdx.math.MathUtils;

public class Enemy {
    public enum Type { FAN, RUSHER, WALKER, TURRET_FLOOR, TURRET_CEIL }

    /** Red "carrier" waves drop a power-up capsule when every member is shot down. */
    public static class Group {
        public int size, killed;
        public boolean carrier, lost;
    }

    public final Type type;
    public float x, y, w = 16, h = 16, vx, baseY, t, shootT, phase;
    public int hp, score;
    public boolean dead;
    /** Walkers: FloorCrawler.LEVEL / CLIMBING / DROPPING (drawn tilted 45 degrees). */
    public int climb;
    /** Walkers: hangs upside down from the ceiling. */
    public boolean ceiling;
    /** Walkers: seconds spent chasing since it came on screen (-1 = not on screen yet). */
    public float chaseT = -1f;
    public static final float CHASE_TIME = 10f, CHASE_SPEED = 120f, LEAVE_SPEED = 60f;
    private final float[] pos = new float[2];
    public Group group;

    public Enemy(Type type, float x, float y, int levelIdx) {
        this.type = type; this.x = x; this.y = y; this.baseY = y;
        switch (type) {
            case FAN:    hp = 1; score = 100; vx = -60f; break;
            case RUSHER: hp = 1; score = 150; vx = -150f; w = 18; h = 12; break;
            case WALKER: hp = 2 + levelIdx / 2; score = 200; vx = -28f; break;
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
                vx = dir * Math.max(speed, 0.01f);                // keeps the facing even when standing still
                pos[0] = x; pos[1] = y;
                int r = ceiling ? FloorCrawler.stepCeiling(g.level, pos, w, h, dir, speed, 40f, 999f, dt)
                                : FloorCrawler.step(g.level, pos, w, h, dir, speed, 40f, 999f, dt);
                if (r != FloorCrawler.BLOCKED) { x = pos[0]; y = pos[1]; climb = r; }
                else climb = FloorCrawler.LEVEL;
                fireAimed(g, dt, 2.0f, 105f);
                break;
            }
            default:
                fireAimed(g, dt, 2.4f, 115f);
                break;
        }
    }

    private void fireAimed(GameScreen g, float dt, float interval, float speed) {
        shootT -= dt;
        if (shootT <= 0f) {
            shootT = interval + MathUtils.random(0f, 0.8f);
            if (x > g.scrollX + 24f && x < g.scrollX + SalamanderGame.W - 24f
                    && y > g.scrollY - 8f && y < g.scrollY + SalamanderGame.H + 8f) {
                g.aimedShot(x + w / 2, y + h / 2, speed, 0f);
            }
        }
    }
}
