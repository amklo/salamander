package com.example.salamander;

import com.badlogic.gdx.math.MathUtils;

/** End-of-stage boss. Every weapon damages it wherever it hits. */
public class Boss {
    public static final float W = 64, H = 64;

    public final int kind;
    public float x, y, t, hp, maxHp, flash, shotT, spin, dieT, expT;
    public boolean entering = true, dying;
    private int burst;

    public Boss(int kind, float x, int hp) {
        this.kind = kind; this.x = x; this.hp = hp; this.maxHp = hp;
        this.y = SalamanderGame.H / 2f - 32f;
        this.shotT = 1.5f;
    }

    public void update(GameScreen g, float dt) {
        t += dt;
        if (flash > 0f) flash -= dt;
        if (dying) {
            dieT += dt;
            expT -= dt;
            if (expT <= 0f) {
                expT = 0.1f;
                g.boom(x + MathUtils.random(0f, W), y + MathUtils.random(0f, H), MathUtils.random(0.8f, 1.6f));
                g.a.play("boom", 0.2f);
            }
            if (dieT > 2.4f) g.bossDefeated();
            return;
        }
        float tx = g.scrollX + SalamanderGame.W - 112f + MathUtils.sin(t * 0.7f) * 12f;
        y = SalamanderGame.H / 2f - 32f + MathUtils.sin(t * (0.8f + 0.15f * kind)) * 78f;
        if (entering) {
            x -= 90f * dt;
            if (x <= tx) entering = false;
            return;
        }
        x = tx;
        shotT -= dt;
        float cx = x + 32f, cy = y + 32f;
        switch (kind) {
            case 0: // triple aimed spread
                if (shotT <= 0f) {
                    shotT = 1.15f;
                    for (int i = -1; i <= 1; i++) g.aimedShot(cx - 20f, cy, 130f, i * 0.32f);
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
                    g.enemyBullet(cx - 24f, cy, MathUtils.PI + MathUtils.sin(spin) * 0.9f, 140f);
                    if (++burst % 20 == 0) {
                        for (int i = -2; i <= 2; i++) g.aimedShot(cx - 24f, cy, 150f, i * 0.2f);
                    }
                }
                break;
        }
    }
}
