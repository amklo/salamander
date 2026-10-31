package com.example.salamander;

import com.badlogic.gdx.math.MathUtils;

import java.util.ArrayList;

/**
 * Collisions (moved out of GameScreen): player bullets against enemies, hazards, wall targets, meteors and the boss
 * (lasers stopping at what they can't pass), and the ships against everything that can hurt them (SHIELD, force
 * field, losing a ship). The world itself (enemies, bullets, terrain, boss, camera) stays in GameScreen (g).
 */
final class Collisions {
    private static final int W = SalamanderGame.W, H = SalamanderGame.H;
    private final GameScreen g;

    Collisions(GameScreen g) { this.g = g; }

    /** A tower's tip is shot to pieces: the column crumbles. */
    void destroyTower(Hazard h, Player owner) {
        h.dead = true;
        g.addScore(owner, ARM_SCORE);
        for (float yy = h.fromCeil ? h.y + 6f : h.y + h.h - 6f; h.fromCeil ? yy < h.y + h.h - Hazard.TOWER_ROOT : yy > h.y + Hazard.TOWER_ROOT; yy += h.fromCeil ? 12f : -12f) {
            g.boom(h.x + h.w / 2f, yy, 0.8f);
        }
        g.a.play("boom", 0.3f);
    }

    /** A stage arm's blue ball is shot to pieces: the whole arm bursts. */
    void destroyArm(Hazard h, Player owner) {
        h.dead = true;
        g.addScore(owner, ARM_SCORE);
        for (int i = 0; i < h.armX.length; i++) g.boom(h.armX[i], h.armY[i], 0.9f);
        g.a.play("boom", 0.3f);
    }

    private static final int ARM_SCORE = 500;

    /**
     * Stage 2: a player shot against the domes and rock eyes, which sit in the rock (so they have to be checked
     * before the rock stops the shot). Returns true if the bullet is used up.
     */
    boolean hitWallTarget(Bullet b, float bx, float by, float bw, float bh, boolean laser) {
        for (Hazard h : g.hazards) {
            if (h.dead || (h.type != Hazard.Type.DOME && h.type != Hazard.Type.EYE)) continue;
            if (!wallTargetTouches(h, bx, by, bw, bh)) continue;
            if (laser) { if (b.hits.contains(h)) continue; b.hits.add(h); } else b.dead = true;
            h.hp -= b.dmg;
            if (h.hp <= 0) destroyWallTarget(h, b.owner);
            else { g.a.play("hit", 0.15f); g.hitSpark(b, h.x, h.y, h.w, h.h); }
            if (b.dead) return true;
        }
        return false;
    }

    /**
     * Does the rectangle touch a dome's drawn pixels? Uses the shape of the frame showing (closed / open), mirrored for
     * domes on the right wall, so the see-through part of the picture can be flown and shot through.
     */
    boolean wallTargetTouches(Hazard h, float rx, float ry, float rw, float rh) {
        if (h.type == Hazard.Type.DOME) return domeTouches(h, rx, ry, rw, rh);
        return g.overlap(rx, ry, rw, rh, h.x, h.y, h.w, h.h) && g.rockEyeMask.overlaps(h.x, h.y, rx, ry, rw, rh);   // eye rock
    }

    boolean domeTouches(Hazard h, float rx, float ry, float rw, float rh) {
        if (!g.overlap(rx, ry, rw, rh, h.x, h.y, h.w, h.h)) return false;
        PixelMask m = g.domeMasks[Math.min(h.open ? 1 : 0, g.domeMasks.length - 1)][h.domeSide];
        return m.overlaps(h.x, h.y, rx, ry, rw, rh);
    }

    /** A dome or an eye rock is shot to pieces. */
    void destroyWallTarget(Hazard h, Player owner) {
        h.dead = true;
        boolean dome = h.type == Hazard.Type.DOME;
        g.addScore(owner, dome ? Hazard.DOME_SCORE : Hazard.EYE_SCORE);
        if (dome && h.w > h.h) for (float xx = h.x + 6f; xx < h.x + h.w; xx += 10f) g.boom(xx, h.y + h.h / 2f, 0.8f);   // (floor / ceiling)
        else if (dome) for (float yy = h.y + 6f; yy < h.y + h.h; yy += 10f) g.boom(h.x + h.w / 2f, yy, 0.8f);
        else {                                       // the whole rock bursts, leaving rubble that stays like terrain
            g.boom(h.x + h.w / 2f, h.y + h.h / 2f, 1.3f);
            for (int i = 0; i < 4; i++) g.boom(h.x + 8f + (i % 2) * 16f, h.y + 8f + (i / 2) * 16f, 0.8f);
            if (g.rubbleR != null) {
                Hazard r = new Hazard(Hazard.Type.RUBBLE);   // (drawn; the Level.Obstacle makes it solid)
                r.w = g.rubbleR.getRegionWidth(); r.h = g.rubbleR.getRegionHeight();
                r.x = h.x + (h.w - r.w) / 2f; r.y = h.y + (h.h - r.h) / 2f;   // centred where the rock was
                g.pendingHazards.add(r);
                g.level.obstacles.add(new Level.Obstacle(r.x, r.y, g.rubbleMask));
            }
        }
        g.a.play("boom", 0.3f);
    }

    /**
     * Would a laser covering this rectangle run into something it can't go through? The same things that stop it in
     * collide(): terrain, a tower's rock column (not its green tip), arm balls, fangs, every part of a boss, every
     * enemy that can't be destroyed (Enemy.indestructible()) and the asteroid storm's small rocks.
     */
    boolean laserBlocked(float x, float y, float w, float h) {
        if (w <= 0f || h <= 0f) return false;
        if (g.level.hits(x, y, w, h)) return true;
        if (bossBlocksLaser(x, y, w, h)) return true;
        if (g.eioneusRoom.podTouches(x, y, w, h)) return true;      // Eioneus's gun pods
        for (Enemy e : g.enemies) {
            if (e.dead || e.withered || e.intangible()) continue;
            if (e.type == Enemy.Type.HEATHEN) {   // Lavinia: the Heathens' pillars (not their gaps)
                if (e.heathenBodyTouches(x, y, w, h)) return true;
                continue;
            }
            // everything that can't be destroyed (Albert, 2026-10-27: Laser Guards, chain balls, blue asteroids,
            // small blocks, laser guns, orbs, Rages, ...) and Lavinia's big blocks
            if ((e.indestructible() || e.type == Enemy.Type.BLOCK_BIG) && g.overlap(x, y, w, h, e.x, e.y, e.w, e.h)) return true;
        }
        for (Meteor m : g.meteors) {                // asteroid storm: the small rocks can't be destroyed either
            if (m.dead || m.big || m.red) continue;
            float in = m.inset();
            if (g.overlap(x, y, w, h, g.scrollX + m.sx + in, g.scrollY + m.sy + in, m.w - 2 * in, m.h - 2 * in)) return true;
        }
        for (Hazard hz : g.hazards) {
            if (hz.dead) continue;
            if (hz.type == Hazard.Type.TOWER) {
                if (!hz.towerTipHit(x, y, w, h) && hz.touches(x, y, w, h)) return true;
            } else if (hz.type == Hazard.Type.ARM) {
                int ball = hz.armBallAt(x, y, w, h);
                // an arm stops a laser at every ball, its blue weak spots too (they are hit there, see collide)
                if (ball >= 0) return true;
            } else if (hz.type == Hazard.Type.FANG) {
                if (hz.touches(x, y, w, h)) return true;
            }
        }
        return false;
    }

    /**
     * Lasers never pass through a boss (Albert): its drawn pixels, eye, core, Lavinia's hatch, Tetran's rings. The beam
     * stops in front of it; collide() looks LASER_REACH px past the beam's end to still hurt it there.
     */
    boolean bossBlocksLaser(float x, float y, float w, float h) {
        if (g.boss == null) return false;
        if (g.boss.ghost) return !g.boss.dying && g.op4.bossShotTouches(x, y, w, h);   // (Op 4: only its weak spots stop a laser)
        if (g.touchesBoss(x, y, w, h) || g.boss.ringTouches(x, y, w, h)) return true;
        return g.boss.hasHatch && g.overlap(x, y, w, h, g.boss.x + g.boss.hatchX, g.boss.y + g.boss.hatchY, g.boss.hatchW, g.boss.hatchH);
    }

    /** How far past a stopped laser's end collide() looks for the boss or arm ball that stopped it. */
    private static final float LASER_REACH = 2f;

    /**
     * A laser ran into something: find where along the beam (from its tail, on screen: bx, by, bw, bh) it first gets
     * blocked, cut the beam off just before that point and pin it there (Bullet.stuck), so it shrinks into it.
     */
    void stickLaser(Bullet b, float bx, float by, float bw, float bh) {
        float lo = 0f, hi = b.vert ? bh : bw;    // a beam lo long from the tail is clear, hi long is blocked
        for (int i = 0; i < 12; i++) {
            float mid = (lo + hi) / 2f;
            if (b.vert ? laserBlocked(bx, by, bw, mid) : laserBlocked(bx, by, mid, bh)) hi = mid; else lo = mid;
        }
        b.attached = false;
        b.stuck = true;
        if (b.vert) b.h = by + lo - b.y; else b.w = bx + lo - b.x;
        if (b.w <= 0.5f || b.h <= 0.5f) b.dead = true;   // blocked right at its tail: nothing left to shrink
    }

    void collide() {
        // ---- player projectiles (points go to whoever fired)
        for (Bullet b : g.pBullets) {
            if (b.dead) continue;
            boolean laser = b.kind == Bullet.Kind.LASER;
            // only the part of a shot that is on screen can hit anything: a long laser or a ring reaching past
            // the edge must not hurt enemies that haven't appeared yet
            float bx = Math.max(b.x, g.scrollX), by = Math.max(b.y, g.scrollY);
            float bw = Math.min(b.x + b.w, g.scrollX + W) - bx, bh = Math.min(b.y + b.h, g.scrollY + H) - by;
            if (bw <= 0f || bh <= 0f) continue;
            // stage 2 domes and rock eyes sit in the rock: they are hit before the rock stops the shot
            if (hitWallTarget(b, bx, by, bw, bh, laser)) { g.missileBurst(b); continue; }
            // brick maze: every weapon blasts the blocks it touches (lasers keep going)
            float reach = b.kind == Bullet.Kind.MISSILE ? 3f : 0f;
            boolean blasted = (b.vert || b.homing) && b.kind == Bullet.Kind.MISSILE   // (wall-crawling / HOMING: on either side)
                    ? g.level.breakBricks(bx - reach, by - reach, bw + 2 * reach, bh + 2 * reach) > 0
                    : g.level.breakBricks(bx, by - reach, bw + reach, bh + 2 * reach) > 0;
            if (blasted) {
                g.addScore(b.owner, 10);
                g.boom(b.x + b.w, b.y + b.h / 2f, 0.6f);
                g.a.play("hit", 0.15f);
                if (!laser) { b.dead = true; continue; }
            }
            if (b.burst) { b.dead = true; g.missileBurst(b); continue; }   // a vertical / HOMING missile that ran into rock
            if (b.kind == Bullet.Kind.RIPPLE) {
                // a ripple is only stopped by terrain at its core, so a big ring can pass close to walls
                if (b.diag ? g.level.hits(b.x + b.w / 2f - 3f, b.y + b.h / 2f - 3f, 6f, 6f)
                        : b.vert ? g.level.hits(b.x + b.w / 2f - 2f, by, 4f, bh) : g.level.hits(bx, b.y + b.h / 2f - 2f, bw, 4f)) { b.dead = true; continue; }
            } else if (laser && b.seg) {
                // an aimed laser's piece: it still hurts what it touches this frame, and is gone the next (Bullet.segStop)
                if (laserBlocked(bx, by, bw, bh)) b.segStop = true;
            } else if (laser) {
                // a laser that runs into something it can't go through lets go of its gun and shrinks into the impact
                // point (see Bullet.stuck); what is left of it can still hurt what it touches
                if (!b.stuck && laserBlocked(bx, by, bw, bh)) {
                    stickLaser(b, bx, by, bw, bh);
                    if (b.dead) continue;
                    bx = Math.max(b.x, g.scrollX); by = Math.max(b.y, g.scrollY);
                    bw = Math.min(b.x + b.w, g.scrollX + W) - bx; bh = Math.min(b.y + b.h, g.scrollY + H) - by;
                    if (bw <= 0f || bh <= 0f) continue;
                }
            } else if (b.kind != Bullet.Kind.MISSILE && g.level.hits(bx, by, bw, bh)) { b.dead = true; continue; }

            // towers: the green tip can be shot; the rock column just stops shots
            boolean towerHit = false;
            for (Hazard h : g.hazards) {
                if (h.type != Hazard.Type.TOWER || h.dead) continue;
                if (h.towerTipHit(bx, by, bw, bh)) {
                    if (laser) { if (b.hits.contains(h)) continue; b.hits.add(h); } else b.dead = true;
                    towerHit = true;
                    h.hp -= b.dmg;
                    float ty = h.fromCeil ? h.y : h.y + h.h - Hazard.TOWER_TIP;
                    if (h.hp <= 0) destroyTower(h, b.owner);
                    else { g.a.play("hit", 0.15f); g.hitSpark(b, h.x, ty, h.w, Hazard.TOWER_TIP); }
                } else if (!laser && h.touches(bx, by, bw, bh)) {   // (a laser was already stopped by laserBlocked)
                    b.dead = true;
                }
                if (b.dead) break;
            }
            if (b.dead) { if (towerHit || b.homing) g.missileBurst(b); continue; }

            // claw arms: a stage arm's blue ball, or any blue ball of a boss arm, can be damaged; the rest just stop shots
            boolean armHit = false;
            for (Hazard h : g.hazards) {
                if (h.type != Hazard.Type.ARM || h.dead) continue;
                int ball = h.armBallAt(bx, by, bw, bh);
                // a laser stops just in front of an arm's balls (laserBlocked): look a little past its end
                if (ball < 0 && laser) {
                    ball = b.vert ? h.armBallAt(bx, by - LASER_REACH, bw, bh + 2f * LASER_REACH) : h.armBallAt(bx - LASER_REACH, by, bw + 2f * LASER_REACH, bh);
                    boolean weakBall = h.bossArm ? ball >= 0 && ball < h.armX.length - 1 : ball == h.armWeak;
                    if (!weakBall) ball = -1;
                }
                if (ball < 0) continue;
                boolean weak = h.bossArm ? ball < h.armX.length - 1 : ball == h.armWeak;   // (boss arm: not the claw)
                if (!weak) { if (laser) continue; b.dead = true; break; }   // (a laser was already stopped by laserBlocked)
                if (laser) { if (b.hits.contains(h)) continue; b.hits.add(h); } else b.dead = true;
                armHit = true;
                h.hp -= b.dmg;
                if (h.hp <= 0) destroyArm(h, b.owner);
                else { g.a.play("hit", 0.15f); g.hitSpark(b, h.armX[ball] - 8f, h.armY[ball] - 8f, 16f, 16f); }
                if (b.dead) break;
            }
            if (b.dead) { if (armHit || b.homing) g.missileBurst(b); continue; }

            for (Hazard h : g.hazards) {
                if (laser) break;                         // (a laser was already stopped by laserBlocked)
                if (h.type == Hazard.Type.FANG && h.touches(bx, by, bw, bh)) {
                    b.dead = true;
                    break;
                }
            }
            if (b.dead) { if (b.homing) g.missileBurst(b); continue; }   // teeth just stop shots (HOMING bursts on them)

            for (Hazard h : g.hazards) {
                if (!h.shootable() || h.dead || !g.overlap(bx, by, bw, bh, h.x, h.y, h.w, h.h)) continue;
                if (laser) { if (b.hits.contains(h)) continue; b.hits.add(h); } else b.dead = true;
                h.hp -= b.dmg;
                if (h.hp <= 0) { h.dead = true; g.addScore(b.owner, 50); g.boom(h.x + h.w / 2f, h.y + h.h / 2f, h.w / 20f); g.a.play("boom", 0.2f); }
                else { g.a.play("hit", 0.15f); g.hitSpark(b, h.x, h.y, h.w, h.h); }
                if (b.dead) break;
            }
            if (b.dead) { g.missileBurst(b); continue; }    // a missile bursts on a Bamda / falling rock it hit

            // asteroid storm: a small rock stops a shot; a big one splits in three (and takes the shot).
            // Eioneus side volcano rocks (red): one hit destroys them (Albert, 2026-10-27)
            for (Meteor m : g.meteors) {
                if (m.dead) continue;
                float in = m.inset();
                if (!g.overlap(bx, by, bw, bh, g.scrollX + m.sx + in, g.scrollY + m.sy + in, m.w - 2 * in, m.h - 2 * in)) continue;
                if (!m.big && !m.red) {                  // a small storm rock: it stops every shot (Albert, 2026-10-27; a laser: laserBlocked)
                    if (laser) continue;
                    b.dead = true;
                    g.a.play("hit", 0.1f);
                    g.hitSpark(b, g.scrollX + m.sx, g.scrollY + m.sy, m.w, m.h);
                    break;
                }
                if (laser) { if (b.hits.contains(m)) continue; b.hits.add(m); } else b.dead = true;
                if (m.red) {
                    m.dead = true;
                    g.addScore(b.owner, g.VOLCANO_ROCK_SCORE);
                    g.boom(g.scrollX + m.sx + m.w / 2f, g.scrollY + m.sy + m.h / 2f, 0.6f);
                    g.a.play("boom", 0.2f);
                } else g.splitMeteor(m);
                if (b.dead) break;
            }
            if (b.dead) { g.missileBurst(b); continue; }

            // Op 4 moai rings: a shot destroys a ring it hits (and is used up, except a laser)
            boolean ringHit = false;
            for (Bullet r : g.eBullets) {
                if (r.dead || r.ring == 0 || !g.overlap(bx, by, bw, bh, r.x, r.y, r.w, r.h)) continue;
                r.dead = true; ringHit = true;
                g.boom(r.x + r.w / 2f, r.y + r.h / 2f, 0.4f);
                g.addScore(b.owner, g.RING_SCORE);
                if (r.ring == 3) {                       // a red ring: a power capsule where it was (Albert)
                    GameScreen.Capsule c = new GameScreen.Capsule();
                    c.x = r.x + r.w / 2f - g.capW / 2f; c.y = r.y + r.h / 2f - g.capH / 2f; c.y0 = c.y;
                    g.caps.add(c);
                }
                if (!laser) { b.dead = true; break; }
            }
            if (b.dead) { if (ringHit && b.kind == Bullet.Kind.MISSILE) g.boom(b.x, b.y, 0.6f); continue; }

            for (Enemy e : g.enemies) {
                if (e.dead || e.withered || e.intangible() || !g.overlap(bx, by, bw, bh, e.x, e.y, e.w, e.h)) continue;   // (shots pass a withered aberrant / a mine taking off)
                if (e.type == Enemy.Type.HEATHEN) {      // only its bars break; the pillar stops shots, the open gap lets them by
                    int k = e.heathenBarAt(bx, by, bw, bh);
                    if (k >= 0) {
                        if (laser) { if (b.hits.contains(e.heathenBarKey[k])) continue; b.hits.add(e.heathenBarKey[k]); } else b.dead = true;
                        e.heathenBarHp[k] -= Math.max(1, Math.round(b.dmg));
                        if (e.heathenBarHp[k] <= 0) g.lavinia.breakHeathenBar(e, k, b.owner);
                        else { g.a.play("hit", 0.15f); g.hitSpark(b, e.heathenBarX(k), e.heathenBarY(), Enemy.HEATHEN_BAR_W, Enemy.HEATHEN_BAR_H); }
                        if (b.dead) break;
                        continue;
                    }
                    if (!e.heathenBodyTouches(bx, by, bw, bh) || laser) continue;   // (a laser is stopped by laserBlocked)
                    b.dead = true;
                    g.a.play("hit", 0.1f);
                    g.hitSpark(b, e.x, e.y, e.w, e.h);
                    break;
                }
                if (e.type == Enemy.Type.MOAI && !e.moaiMouthHit(bx, by, bw, bh)) {   // a moai: only its open mouth can be hurt
                    if (laser) continue;                  // (a laser goes on; it may reach the open mouth later)
                    b.dead = true;
                    g.a.play("hit", 0.1f);
                    g.hitSpark(b, e.x, e.y, e.w, e.h);
                    break;
                }
                if (laser && !b.seg && e.type == Enemy.Type.BLOCK_BIG && !b.hits.contains(e)) g.lavinia.reflectLaser(b, e);   // (and it still hurts it)
                if (laser) { if (b.hits.contains(e)) continue; b.hits.add(e); } else b.dead = true;
                if (e.indestructible()) {   // can't be destroyed: the shot just stops on it
                    g.a.play("hit", 0.1f);
                    g.hitSpark(b, e.x, e.y, e.w, e.h);
                    if (b.dead) break;
                    continue;
                }
                e.hp -= b.dmg;
                if (e.hp <= 0) killEnemy(e, b.owner);
                else { g.a.play("hit", 0.15f); g.hitSpark(b, e.x, e.y, e.w, e.h); }
                if (b.dead) break;
            }
            if (b.dead) { if (b.kind == Bullet.Kind.MISSILE) g.boom(b.x, b.y, 0.6f); continue; }

            if (g.boss != null && g.boss.ringTouches(bx, by, bw, bh)) {   // Tetran's rings: can't be hurt, block shots
                if (!laser) { b.dead = true; g.missileBurst(b); }
                continue;
            }
            // a laser stops just in front of the boss (laserBlocked): look a little past its end
            float hx = bx, hy = by, hw = bw, hh = bh;
            if (laser) { if (b.vert) { hy -= LASER_REACH; hh += 2f * LASER_REACH; } else { hx -= LASER_REACH; hw += 2f * LASER_REACH; } }
            if (g.barBosses.barBossShot(b, hx, hy, hw, hh, laser)) continue;   // a bar-wall boss: a bar or orb took it
            if (g.eioneusRoom.podShot(b, hx, hy, hw, hh, laser)) continue;       // Eioneus's gun pods: a bar took it, or the pod stopped it
            if (g.kierkeBoss.kierkeBossShot(b, hx, hy, hw, hh, laser)) continue;   // Kierke's machine wall: a barrel, a ball, the core or armour
            if (g.boss != null && !g.boss.dying && (g.op4.bossShotTouches(hx, hy, hw, hh)   // (Tetran's rings stop a laser but aren't hurt)
                    || g.boss.hasHatch && g.overlap(hx, hy, hw, hh, g.boss.x + g.boss.hatchX, g.boss.y + g.boss.hatchY, g.boss.hatchW, g.boss.hatchH))) {
                // Bosses with an eye only take damage on the eye, and not while it is fully closed;
                // other bosses take damage anywhere. A laser hurts it once per beam.
                // After each hit the boss can't lose HP for Boss.HIT_COOLDOWN; shots that land then are absorbed.
                float ex = g.boss.x + g.boss.eyeX, ey = g.boss.y + g.boss.eyeY;
                boolean onEye = g.boss.hasEye && g.overlap(hx, hy, hw, hh, ex, ey, g.boss.eyeW, g.boss.eyeH);
                boolean onCore = g.boss.coreTouches(hx, hy, hw, hh);
                boolean onHatch = g.boss.hasHatch && g.overlap(hx, hy, hw, hh, g.boss.x + g.boss.hatchX, g.boss.y + g.boss.hatchY, g.boss.hatchW, g.boss.hatchH);
                boolean weak = g.barBosses.barBossNow() ? false   // bar-wall boss: only its bars / orbs (barBossShot), the body just stops shots
                        : g.boss.hasHatch ? onHatch || g.boss.hatchBroken()   // Lavinia: the hatch (open or closed), or anywhere while it is broken
                        : g.boss.weakW > 0f ? g.boss.weakTouches(hx, hy, hw, hh)   // (Op 4's sphere: its eye)
                        : g.boss.hasCore ? onCore : !g.boss.hasEye || (onEye && !g.boss.eyeShut());   // core: the only weak spot
                if (!weak || g.boss.hitCooldown > 0f) {
                    if (!laser) {      // a laser keeps going and may still hurt it a moment later
                        b.dead = true;
                        if (onCore) g.hitSpark(b, g.boss.x + g.boss.coreX, g.boss.y + g.boss.coreY, g.boss.coreW, g.boss.coreH);
                        else if (onEye) g.hitSpark(b, ex, ey, g.boss.eyeW, g.boss.eyeH); else g.hitSpark(b, g.boss.x, g.boss.y, g.boss.w, g.boss.h);
                    }
                    if (b.dead && b.kind == Bullet.Kind.MISSILE) g.boom(b.x, b.y, 0.6f);
                    continue;
                }
                if (laser) { if (b.hits.contains(g.boss)) continue; b.hits.add(g.boss); } else b.dead = true;
                if (g.boss.hasHatch && !g.boss.hatchBroken()) {   // Lavinia: the hatch takes the hit (its own health), not the boss
                    float hx0 = g.boss.x + g.boss.hatchX, hy0 = g.boss.y + g.boss.hatchY;
                    g.boss.hitCooldown = Boss.HIT_COOLDOWN;
                    if (g.boss.damageHatch(b.dmg)) { g.boom(hx0 + g.boss.hatchW / 2f, hy0 + g.boss.hatchH / 2f, 0.8f); g.a.play("boom", 0.3f); }
                    else { g.a.play("hit", 0.15f); g.hitSpark(b, hx0, hy0, g.boss.hatchW, g.boss.hatchH); }
                    if (b.dead && b.kind == Bullet.Kind.MISSILE) g.boom(b.x, b.y, 0.6f);
                    continue;
                }
                g.boss.hp = Math.max(0f, g.boss.hp - b.dmg);
                g.boss.hitCooldown = Boss.HIT_COOLDOWN;
                g.boss.flash = 0.08f;
                g.a.play("hit", 0.15f);
                if (g.boss.hasEye) g.boss.onEyeHit();
                if (g.boss.hasHatch) g.boss.onHatchHit();
                if (g.boss.hp > 0) {
                    if (g.boss.hasCore) g.hitSpark(b, g.boss.x + g.boss.coreX, g.boss.y + g.boss.coreY, g.boss.coreW, g.boss.coreH);
                    else if (g.boss.hasEye) g.hitSpark(b, ex, ey, g.boss.eyeW, g.boss.eyeH); else g.hitSpark(b, g.boss.x, g.boss.y, g.boss.w, g.boss.h);
                }
                if (g.boss.hp <= 0) {
                    g.boss.dying = true;
                    g.boss.explodeArms(g);
                    g.boss.explodeRings(g);
                    g.eBullets.clear();
                    g.addScore(b.owner, 5000);
                }
            }
            if (b.dead && b.kind == Bullet.Kind.MISSILE) g.boom(b.x, b.y, 0.6f);
        }

        // ---- enemy bullets
        for (Bullet b : g.eBullets) {
            if (b.dead) continue;
            boolean rock = b.guard != null ? false   // (Laser Guard beams: steerGuardBeams stops them at rock)
                    : b.omenLaser   // Omen's laser: only its middle is stopped by rock (the ends just stop growing)
                    ? g.level.hits(b.anchorX - b.startW / 2f, b.y, b.startW, b.h) : g.level.hits(b.x, b.y, b.w, b.h);
            if (!rock && !b.omenLaser) b.leftRock = true;   // (Omen's laser keeps its own short grace)
            // (Albert, 2026-10-30: no enemy bullet passes scenery; one fired from inside rock only gets out of that rock)
            if (rock && (b.t >= b.terrainGrace || b.leftRock)) { b.dead = true; continue; }
            if (b.guard == null && !b.omenLaser && g.sceneryStops(b.x, b.y, b.w, b.h)) { b.dead = true; continue; }
            if (g.kierkeBoss.kierkeBossNow() && !g.boss.dying && g.kierkeBoss.kierkeTouches(b.x, b.y, b.w, b.h)) { b.dead = true; continue; }   // (Kierke's wall: no shot gets through)
            for (Hazard h : g.hazards) {
                if (h.type == Hazard.Type.FANG && h.touches(b.x, b.y, b.w, b.h)) b.dead = true;
            }
            if (b.dead) continue;
            for (Player p : g.players) {   // SHIELD in front of a ship: blocks the shot (and wears down)
                if (g.alive(p) && p.shield > 0
                        && g.overlap(b.x, b.y, b.w, b.h, p.shieldX(), p.shieldY(), Player.shieldW(), Player.shieldH())) {
                    b.dead = true;
                    g.boom(b.x + b.w / 2f, b.y + b.h / 2f, 0.4f);
                    if (p.invuln <= 0f) shieldHit(p);   // shots that land during the invincibility don't wear it down
                    break;
                }
            }
            if (b.dead) continue;
            for (Player p : g.players) {
                if (g.alive(p) && g.overlap(b.x, b.y, b.w, b.h, p.hitX(), p.hitY(), Player.HIT_W, Player.HIT_H)) {
                    b.dead = true;
                    hurt(p, false);   // got past the SHIELD (from behind / above / below)
                    break;
                }
            }
        }

        // ---- each ship vs everything else
        for (Player p : g.players) if (g.alive(p)) collideShip(p);
    }

    /**
     * What the SHIELD (its own 8x16 picture in front of the nose) touches: an Enemy, a Hazard (Bamda / rock or claw
     * arm), the Boss, or null.
     */
    Object shieldTouches(Player p) {
        float fx = p.shieldX(), fy = p.shieldY(), fw = Player.shieldW(), fh = Player.shieldH();
        for (Enemy e : g.enemies) {
            if (e.type == Enemy.Type.HEATHEN) { if (!e.dead && e.heathenTouches(fx, fy, fw, fh)) return e; continue; }
            if (!e.dead && !e.intangible() && !e.ground() && g.overlap(fx, fy, fw, fh, e.x + 2f, e.y + 2f, e.w - 4f, e.h - 4f)) return e;
        }
        for (Hazard h : g.hazards) {
            if (h.dead) continue;
            if (h.type == Hazard.Type.ARM && h.armTouches(fx, fy, fw, fh)) return h;
            if ((h.type == Hazard.Type.DOME || h.type == Hazard.Type.EYE) && wallTargetTouches(h, fx, fy, fw, fh)) return h;
            if (h.shootable() && g.overlap(fx, fy, fw, fh, h.x + 2f, h.y + 2f, h.w - 4f, h.h - 4f)) return h;
        }
        if (g.boss != null && g.boss.ghost) return null;    // (Op 4: the ships fly over the bosses)
        if (g.boss != null && g.boss.ringTouches(fx, fy, fw, fh)) return g.boss;
        return g.boss != null && !g.boss.dying && g.touchesBoss(fx, fy, fw, fh) ? g.boss : null;
    }

    /** The F.FIELD bubble or the SHIELD took a hit from this: it takes 1 damage in return (enemies and Bamdas; arms and the boss shrug it off). */
    void guardDamage(Object o, Player p) {
        if (o instanceof Enemy) {
            Enemy e = (Enemy) o;
            if (e.type == Enemy.Type.ORB && !e.dead) {   // Op 4 orb: can't be shot, but a guard absorbs it
                e.dead = true;
                g.boom(e.x + e.w / 2f, e.y + e.h / 2f, 0.6f);
                g.a.play("boom", 0.2f);
                return;
            }
            if (e.dead || e.withered || e.indestructible()) return;   // (can't be destroyed)
            e.hp -= 1;
            if (e.hp <= 0) killEnemy(e, p);
            else g.boom(e.x + e.w / 2f, e.y + e.h / 2f, 0.4f);
        } else if (o instanceof Hazard) {
            Hazard h = (Hazard) o;
            if (h.dead || !h.shootable()) return;
            h.hp -= 1;
            if (h.hp <= 0) { h.dead = true; g.addScore(p, 50); g.boom(h.x + h.w / 2f, h.y + h.h / 2f, h.w / 20f); g.a.play("boom", 0.2f); }
            else g.boom(h.x + h.w / 2f, h.y + h.h / 2f, 0.4f);
        }
    }

    void collideShip(Player p) {
        float hx = p.hitX(), hy = p.hitY(), hw = Player.HIT_W, hh = Player.HIT_H;
        boolean ghost = p.ghostT > 0f;   // just (re)entered: passes through terrain and teeth
        if (!ghost && g.level.hits(hx, hy, hw, hh)) { hurt(p, true); return; }
        // SHIELD: whatever runs into the field itself wears it down (and gives the ship a moment of invincibility)
        if (p.shield > 0 && p.invuln <= 0f && !g.debugInvincible) {
            Object hit = shieldTouches(p);
            if (hit != null) { shieldHit(p); guardDamage(hit, p); }   // ...and what ran into it takes 1 damage
        }
        for (Enemy e : g.enemies) {                     // Lavinia: the laser gun points' pulses
            if (e.type == Enemy.Type.LASER_GUN && !e.dead && e.beamW > 0f && g.overlap(hx, hy, hw, hh, e.beamX, e.beamY, e.beamW, e.beamH)) {
                hurt(p, false);
                if (p.dead) return;
            }
        }
        for (Enemy e : g.enemies) {                     // Lavinia: the Rages' lasers
            if (e.type == Enemy.Type.RAGE && !e.dead && e.beamH > 0f && g.overlap(hx, hy, hw, hh, e.beamX, e.beamY, e.beamW, e.beamH)) {
                hurt(p, false);
                if (p.dead) return;
            }
        }
        for (Enemy e : g.enemies) {                     // Eioneus laser beams
            if (e.type == Enemy.Type.LASER_BATTERY && e.beamOn() && g.overlap(hx, hy, hw, hh, e.beamX, e.beamY, e.beamW, e.beamH)) {
                hurt(p, false);
                if (p.dead) return;
            }
        }
        for (Enemy e : g.enemies) {
            if (e.type == Enemy.Type.HEATHEN) {          // its exact shape: the gap lets a ship through once the bars are gone
                if (!e.dead && e.heathenTouches(hx, hy, hw, hh)) { hurt(p, false); if (p.dead) return; }
                continue;
            }
            if (!e.dead && !e.intangible() && !e.ground() && g.overlap(hx, hy, hw, hh, e.x + 2f, e.y + 2f, e.w - 4f, e.h - 4f)) {
                boolean ffieldTakesIt = p.ffield > 0 && p.invuln <= 0f && !g.debugInvincible;
                hurt(p, false);
                if (p.dead) return;
                if (ffieldTakesIt) guardDamage(e, p);       // the F.FIELD hits back: 1 damage
            }
        }
        for (Hazard h : g.hazards) {
            if (h.dead) continue;
            if (!ghost && h.type == Hazard.Type.FANG && h.touches(hx, hy, hw, hh)) { hurt(p, true); return; }
            if (!ghost && h.type == Hazard.Type.TOWER && h.touches(hx, hy, hw, hh)) { hurt(p, true); return; }
            if (!ghost && (h.type == Hazard.Type.DOME || h.type == Hazard.Type.EYE) && wallTargetTouches(h, hx, hy, hw, hh)) { hurt(p, true); return; }
            if (h.type == Hazard.Type.ARM && h.armTouches(hx, hy, hw, hh)) { hurt(p, false); if (p.dead) return; }   // caught
            if (h.shootable() && g.overlap(hx, hy, hw, hh, h.x + 2f, h.y + 2f, h.w - 4f, h.h - 4f)) {
                h.dead = true;
                g.boom(h.x + h.w / 2f, h.y + h.h / 2f, h.w / 20f);
                hurt(p, false);
                if (p.dead) return;
            }
        }
        if (g.boss != null && !g.boss.ghost && (g.touchesBoss(hx, hy, hw, hh) || g.boss.ringTouches(hx, hy, hw, hh))) {
            hurt(p, false);
            if (p.dead) return;
        }
        if (g.eioneusRoom.podTouches(hx, hy, hw, hh)) {             // Eioneus's gun pods (the shot-away inside of a column is free)
            hurt(p, false);
            if (p.dead) return;
        }
        collideMeteors(p);
        if (p.dead) return;
        g.lavinia.collideSparks(p);
        for (GameScreen.Capsule c : g.caps) {
            if (!c.dead() && g.overlap(p.x + 2f, p.y, 28f, 16f, c.x, c.y, g.capW, g.capH)) {
                c.x = -99999f;   // consumed (culled next frame)
                p.score += 300;
                if (c.green && p.options < Player.MAX_OPTIONS) p.options++;   // green: one option back, at once
                else p.meter = p.meter >= Player.SLOT_COUNT ? 1 : p.meter + 1;   // only the player who caught it advances
                g.a.play("pickup", 0.35f);
            }
        }
    }

    /**
     * Asteroid storm rocks against a ship. The SHIELD breaks any rock that runs into it (and wears down, unless the
     * ship is still invincible from its last hit). A rock that reaches the ship hurts it; if the F.FIELD takes the hit
     * the rock breaks, otherwise it just flies on. Rocks pass through a ship that is blinking (invincible).
     */
    void collideMeteors(Player p) {
        float hx = p.hitX(), hy = p.hitY();
        for (Meteor m : g.meteors) {
            if (m.dead) continue;
            float in = m.inset(), mx = g.scrollX + m.sx + in, my = g.scrollY + m.sy + in, mw = m.w - 2 * in, mh = m.h - 2 * in;
            if (p.shield > 0 && g.overlap(p.shieldX(), p.shieldY(), Player.shieldW(), Player.shieldH(), mx, my, mw, mh)) {
                g.shatterMeteor(m);
                if (p.invuln <= 0f && !g.debugInvincible) shieldHit(p);
                continue;
            }
            if (!g.overlap(hx, hy, Player.HIT_W, Player.HIT_H, mx, my, mw, mh)) continue;
            if (p.invuln > 0f || g.debugInvincible) continue;
            boolean ffield = p.ffield > 0;
            hurt(p, false);
            if (ffield) g.shatterMeteor(m);
            if (p.dead) return;
        }
    }

    void killEnemy(Enemy e, Player killer) {
        if (e.withered) return;
        if (e.type == Enemy.Type.ABERRANT || e.type == Enemy.Type.SEED) e.withered = true;   // withers / is destroyed and stays
        else e.dead = true;
        g.addScore(killer, e.score);
        g.boom(e.x + e.w / 2f, e.y + e.h / 2f, 1f);
        g.a.play("boom", 0.22f);
        Enemy.Group grp = e.group;
        if (grp != null) {
            grp.killed++;
            if (grp.carrier && !grp.lost && grp.killed >= grp.size) {
                GameScreen.Capsule c = new GameScreen.Capsule();
                c.x = e.x + e.w / 2f - g.capW / 2f; c.y = e.y + e.h / 2f - g.capH / 2f; c.y0 = c.y;
                g.caps.add(c);
            }
        }
    }

    /** Invincibility after the F.FIELD or the SHIELD takes a hit. */
    private static final float GUARD_IFRAMES = 0.6f;

    /**
     * The SHIELD takes a hit (a blocked shot, or an enemy rammed): it wears down by one and the ship is invincible
     * to enemies and shots for a moment, like with the F.FIELD (terrain still hurts).
     */
    void shieldHit(Player p) {
        p.shield--;
        p.invuln = Math.max(p.invuln, GUARD_IFRAMES);
        p.shieldHitT = GUARD_IFRAMES;   // the field flickers (the ship doesn't blink)
        g.a.play("hit", 0.3f);
    }

    void hurt(Player p, boolean ignoreGuards) {   // (the SHIELD only reacts to what touches the field itself)
        if (g.debugInvincible) return;                 // debug: invincible
        if (p.dead || g.state == GameScreen.State.CLEAR || (g.boss != null && g.boss.dying)) return;   // nobody dies after the boss
        if (!ignoreGuards) {
            if (p.invuln > 0f) return;
            if (p.ffield > 0) {
                p.ffield--;
                p.invuln = GUARD_IFRAMES;
                p.ffieldHitT = p.invuln;   // the F.FIELD bubble blinks (and fades out if this was its last hit)
                g.a.play("hit", 0.3f);
                return;
            }
        }
        p.dead = true;
        p.aiming = false;
        g.boom(p.x + 16f, p.y + 8f, 1.8f);
        g.a.play("boom", 0.5f);
        p.lives--;
        // its options are left behind as green capsules (one option each); on NORMAL the ship starts over without
        // power-ups, on EASY it keeps everything else
        for (int i = 0; i < p.options; i++) {
            GameScreen.Capsule c = new GameScreen.Capsule();
            c.green = true;
            c.x = g.optX(p, i) + 6f - g.capW / 2f; c.y = g.optY(p, i) + 6f - g.capH / 2f; c.y0 = c.y;
            g.caps.add(c);
        }
        if (g.game.easy) {
            p.options = 0;                            // easy: everything else is kept
        } else {
            int meter = p.meter;                      // (the power meter's highlight stays where it was)
            p.resetPowerups();
            p.meter = meter;
        }
        if (g.debugLoadout) p.applyDebugLoadout();
        // the game carries on (no rewind): the ship re-enters on the spot after a moment. Only when nobody has
        // ships left is it game over.
        boolean othersPlaying = false;
        for (Player o : g.players) if (o != null && o != p && !o.out && o.lives >= 0) othersPlaying = true;
        if (p.lives >= 0 || othersPlaying) {
            p.respawnT = g.RESPAWN_DELAY;   // (out of ships: drops out, can press fire to continue)
        } else {
            g.setState(GameScreen.State.DEAD);         // nobody left
        }
    }
}
