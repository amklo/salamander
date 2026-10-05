package com.example.salamander;

import com.badlogic.gdx.math.MathUtils;

/**
 * End-of-stage boss. Every weapon damages it wherever it hits. Its size comes from its sprite
 * (stage 1 is the big brain from the original map); the behaviour is the same for any size.
 */
public class Boss {
    public final float w, h;
    public PixelMask mask;   // exact shape for hits (null = whole rectangle)
    /** After every hit the boss can't lose HP again for this long (seconds). */
    public static final float HIT_COOLDOWN = 0.1f;
    public float hitCooldown;

    public final int kind;
    public float x, y, t, hp, maxHp, flash, shotT, spin, dieT, expT;
    public boolean entering = true, dying;
    private int burst;

    public Boss(int kind, float x, int hp, float w, float h) {
        this.kind = kind; this.x = x; this.hp = hp; this.maxHp = hp;
        this.w = w; this.h = h;
        this.y = SalamanderGame.H / 2f - h / 2f;
        this.shotT = 1.5f;
    }

    public void update(GameScreen g, float dt) {
        t += dt;
        if (flash > 0f) flash -= dt;
        if (hitCooldown > 0f) hitCooldown -= dt;
        if (dying) {
            dieT += dt;
            expT -= dt;
            if (expT <= 0f) {
                expT = 0.1f;
                g.boom(x + MathUtils.random(0f, w), y + MathUtils.random(0f, h), MathUtils.random(0.8f, 1.6f));
                g.a.play("boom", 0.2f);
            }
            if (dieT > 2.4f) g.bossDefeated();
            return;
        }
        float tx = g.scrollX + SalamanderGame.W - (w + 48f) + MathUtils.sin(t * 0.7f) * 12f;
        float swing = Math.min(78f, (SalamanderGame.H - h) / 2f - 4f);   // a bigger boss sways less
        y = g.scrollY + SalamanderGame.H / 2f - h / 2f + MathUtils.sin(t * (0.8f + 0.15f * kind)) * swing;
        if (entering) {
            x -= 90f * dt;
            if (x <= tx) entering = false;
            return;
        }
        x = tx;
        shotT -= dt;
        float cx = x + w / 2f, cy = y + h / 2f, front = x + 12f;   // shots leave from the front (left) side
        switch (kind) {
            case 0: // triple aimed spread
                if (shotT <= 0f) {
                    shotT = 1.15f;
                    for (int i = -1; i <= 1; i++) g.aimedShot(front, cy, 130f, i * 0.32f);
                }
                break;
            case 1: // rotating ring + aimed shot
                if (shotT <= 0f) {
                    shotT = 1.5f;
                    for (int i = 0; i < 10; i++) g.enemyBullet(cx, cy, i * MathUtils.PI2 / 10f + spin, 100f);
                    spin += 0.17f;
                    g.aimedShot(cx, cy, 150f, 0f);
                }
                break;
            default: // sweeping stream + occasional aimed burst
                spin += dt * 3.2f;
                if (shotT <= 0f) {
                    shotT = 0.11f;
                    g.enemyBullet(front - 4f, cy, MathUtils.PI + MathUtils.sin(spin) * 0.9f, 140f);
                    if (++burst % 20 == 0) {
                        for (int i = -2; i <= 2; i++) g.aimedShot(front - 4f, cy, 150f, i * 0.2f);
                    }
                }
                break;
        }
    }
}
