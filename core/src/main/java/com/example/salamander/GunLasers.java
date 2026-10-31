package com.example.salamander;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;

import java.util.ArrayList;

/**
 * The bosses' falling lasers (moved out of GameScreen): Op 4's gun units and Eioneus, and the gun units' creature.
 */
final class GunLasers {
    private static final int W = SalamanderGame.W, H = SalamanderGame.H;
    private final GameScreen g;

    GunLasers(GameScreen g) { this.g = g; }

    // ---- the gun units' attacks (from the recording, 250.6 s and 256.6 s): out from under the columns, at five
    // places (GUN_EMITTERS: x, y top-down in boss5b.png), lasers grow down at GUN_LASER_SPEED px/s until they are
    // GUN_LASER_LEN px long, then fall on down the screen: the enemies' red / blue laser (Albert; dotted on the MSX).
    // A green creature comes out of the middle (GUN_MOUTH) and goes for the nearest ship (the Op 4 green flyer).
    static final class GunLaser { float sx, top, bottom, t, ex, ey, speed, len; }
    final ArrayList<GunLaser> gunLasers = new ArrayList<>();
    static final float[][] GUN_EMITTERS = {{18f, 136f}, {42f, 120f}, {90f, 152f}, {170f, 152f}, {218f, 120f}};
    static final float[] GUN_MOUTH = {125f, 180f};
    static final float GUN_LASER_SPEED = 80f, GUN_LASER_LEN = 80f;

    void gunLasers(Boss b) { bossLasers(b, GUN_EMITTERS, GUN_LASER_SPEED, GUN_LASER_LEN); }

    /**
     * Eioneus (from the recording, 271.8 s and 282.8 s): four lasers out of the boss's leg tips (EIO_EMITTERS: x, y
     * top-down in boss3.png), growing down at EIO_LASER_SPEED px/s until EIO_LASER_LEN px long, then falling on. Dotted
     * on the MSX; here the enemies' laser, like the gun units' (Albert).
     */
    static final float[][] EIO_EMITTERS = {{45f, 121f}, {76f, 137f}, {116f, 137f}, {155f, 121f}};
    static final float EIO_LASER_SPEED = 160f, EIO_LASER_LEN = 100f;

    void eioneusLasers(Boss b) { bossLasers(b, EIO_EMITTERS, EIO_LASER_SPEED, EIO_LASER_LEN); }

    void bossLasers(Boss b, float[][] emitters, float speed, float len) {
        for (float[] em : emitters) {
            GunLaser l = new GunLaser();
            l.ex = em[0]; l.ey = em[1]; l.speed = speed; l.len = len;
            l.sx = b.x - g.scrollX + l.ex;
            l.top = l.bottom = b.y - g.scrollY + b.h - l.ey;
            gunLasers.add(l);
        }
        g.a.play("shoot", 0.25f);
    }

    void gunCreature(Boss b) {
        if (g.greenF == null) return;
        Enemy f = new Enemy(Enemy.Type.GREEN_FLYER, 0f, 0f, g.levelIdx);
        f.vertical = true;
        f.gfHoming = true;
        f.gfStartAng = -MathUtils.HALF_PI;
        f.gfX = b.x - g.scrollX + GUN_MOUTH[0];
        f.gfY = b.y - g.scrollY + b.h - GUN_MOUTH[1];
        f.x = f.gfX + g.scrollX - 8f; f.y = f.gfY + g.scrollY - 8f;
        g.enemies.add(f);
    }

    void updateGunLasers(float dt, boolean clearing) {
        if (gunLasers.isEmpty()) return;
        for (GunLaser l : gunLasers) {
            l.t += dt;
            if (l.t < l.len / l.speed && g.boss != null && !g.boss.dying) {   // still coming out: its top at the emitter
                l.sx = g.boss.x - g.scrollX + l.ex;
                l.top = g.boss.y - g.scrollY + g.boss.h - l.ey;
                l.bottom = l.top - l.speed * l.t;
            } else {
                l.top -= l.speed * dt;
                l.bottom -= l.speed * dt;
            }
        }
        gunLasers.removeIf(l -> l.top < 0f);
        if (clearing) return;
        for (Player p : g.players) {
            if (!g.alive(p)) continue;
            float hx = p.hitX(), hy = p.hitY();
            for (GunLaser l : gunLasers) {
                if (l.top - l.bottom < 1f) continue;
                float lw = g.omenLaserTex != null ? g.omenLaserTex[0].getHeight() : 5f;   // (as wide as the laser picture)
                if (g.overlap(hx, hy, Player.HIT_W, Player.HIT_H, g.scrollX + l.sx - lw / 2f, g.scrollY + l.bottom, lw, l.top - l.bottom)) {
                    g.collisions.hurt(p, false);
                    break;
                }
            }
        }
    }

    /**
     * The bosses' falling lasers (the gun units, Eioneus): the enemies' laser (sprites/enemy laser red / blue.png,
     * flashing), turned upright as the Rages fire it.
     */
    void drawGunLasers() {
        if (gunLasers.isEmpty() || g.omenLaserTex == null) return;
        for (GunLaser l : gunLasers) {
            float b1 = Math.min(H, l.top), b0 = Math.max(0f, l.bottom), len = b1 - b0;
            if (len < 1f) continue;
            Texture lt = g.omenLaserTex[(int) (l.t * Enemy.OMEN_LASER_FLASH) % g.omenLaserTex.length];
            int th = lt.getHeight();
            float left = Math.round(g.scrollX + l.sx - th / 2f);
            g.batch.draw(lt, left + th, Math.round(g.scrollY) + b0, 0f, 0f, len, th, 1f, 1f, 90f,
                    Math.round(l.top - b1), 0, Math.round(len), th, false, false);
        }
    }
}
