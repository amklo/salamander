package com.example.salamander;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;

import java.util.ArrayList;

/**
 * Op 4's end (stage 6, moved out of GameScreen): terrain breaking up, the brain sphere, the gun units, edge mines
 * and option eaters.
 */
final class Op4End {
    private static final int W = SalamanderGame.W, H = SalamanderGame.H;
    private final GameScreen g;

    Op4End(GameScreen g) { this.g = g; }

    // =============================================================== Op 4's end (stage 6)
    // From the MSX recording (220-268 s; Albert, 2026-10-27): the scrolling stops at the end of the shaft (the path's
    // end, camera top 376 in the map) and the whole terrain on the screen breaks up pixel by pixel (OP4_TERRAIN_TIME s),
    // leaving black space. The brain sphere (boss5a) then rises in through the bottom edge; option eaters come in
    // while it is there. Beaten, it breaks up the same way, and the gun units (boss5b) appear pixel by pixel where they
    // are. The ships fly over both bosses (Boss.ghost): only the sphere's eye and the gun units' bars and orbs (and
    // their core shield) stop shots.
    static final int OP4_NONE = 0, OP4_TERRAIN = 1, OP4_FIGHT = 2;
    static final float OP4_TERRAIN_TIME = 3f, OP4_SPHERE_WAIT = 1.2f, OP4_GUNS_WAIT = 1.5f;
    /** Option eaters during the sphere fight: the first after OEATER_FIRST s, then one every OEATER_EVERY s. */
    static final float OEATER_FIRST = 4f, OEATER_EVERY = 5f;
    /** Green mines during the gun units' fight (Albert): the first GMINE5_FIRST s after they have appeared, then one
     *  every GMINE5_EVERY_MIN..MAX s, in from a random edge of the screen (left, right, top or bottom). */
    static final float GMINE5_FIRST = 3f, GMINE5_EVERY_MIN = 2.5f, GMINE5_EVERY_MAX = 4.5f;
    /** The sphere's eye in boss5a.png (bottom-left, y up from the picture's bottom, w, h): its only weak spot. */
    static final float[] SPHERE_EYE = {100f, 27f, 13f, 15f};
    static final int SPHERE_HP = 60;
    int op4State;
    float op4T, oeaterT, gmine5T;
    /** The terrain has dissolved (its art isn't drawn, nothing to run into); a beaten boss is breaking up. */
    boolean terrainGone, bossDissolving;
    /** The gun units coming together pixel by pixel (null when they are whole). */
    Dissolver gunsAppear;

    /** Is this Op 4, with its two bosses (boss5a, boss5b)? */
    boolean op4End() { return g.levelIdx == 5 && g.bossPartR != null; }

    void updateOp4End(float dt) {
        if (g.scrollT < g.level.stopT) return;
        op4T += dt;
        switch (op4State) {
            case OP4_NONE:
                dissolveTerrain();
                op4State = OP4_TERRAIN;
                op4T = 0f;
                break;
            case OP4_TERRAIN:
                if (op4T >= OP4_TERRAIN_TIME + OP4_SPHERE_WAIT) { op4State = OP4_FIGHT; op4T = 0f; g.spawnBoss(); }
                break;
            default:
                if (g.boss == null && !g.bossSpawned && g.bossPart > 0 && op4T >= OP4_GUNS_WAIT) g.spawnBoss();   // the gun units
                if (g.boss != null && g.boss.sphere && !g.boss.dying && !g.boss.entering && g.state == GameScreen.State.PLAY
                        && (oeaterT -= dt) <= 0f) { oeaterT = OEATER_EVERY; spawnOptionEater(); }
                if (g.boss != null && g.boss.guns && !g.boss.dying && !g.boss.appearing && g.state == GameScreen.State.PLAY
                        && (gmine5T -= dt) <= 0f) { gmine5T = MathUtils.random(GMINE5_EVERY_MIN, GMINE5_EVERY_MAX); spawnEdgeMine(); }
                if (gunsAppear != null && g.boss != null && g.boss.guns) {
                    gunsAppear.set(g.boss.appearT / Boss.GUNS_APPEAR);
                    if (!g.boss.appearing) { gunsAppear.dispose(); gunsAppear = null; }
                }
                break;
        }
    }

    /** spawnBoss for Op 4: where the boss starts and how it moves (see Boss.initSphere / initGuns). */
    void op4Boss() {
        g.boss.fixed = false;
        if (g.bossPart == 0) {
            g.boss.initSphere(g.boss.mask);
            g.boss.x = g.scrollX + W / 2f - g.boss.bounceX0 - g.boss.bounceW / 2f;
            g.boss.y = g.scrollY - g.boss.bounceY0 - g.boss.bounceH;   // (just below the bottom edge)
            g.boss.weakX = SPHERE_EYE[0]; g.boss.weakY = SPHERE_EYE[1]; g.boss.weakW = SPHERE_EYE[2]; g.boss.weakH = SPHERE_EYE[3];
            g.boss.hp = g.boss.maxHp = SPHERE_HP;         // (only its eye can be hit: about 7 s of shooting on the MSX)
            oeaterT = OEATER_FIRST;
        } else {
            g.boss.initGuns(g);
            gmine5T = GMINE5_FIRST;
            if (gunsAppear != null) gunsAppear.dispose();
            gunsAppear = g.barBosses.barPix != null ? Dissolver.appear(g.barBosses.barPix) : null;
            if (gunsAppear == null) g.boss.appearing = false;
        }
    }

    /**
     * The end of the shaft: everything drawn on the screen (the stage art) is copied and breaks up pixel by pixel
     * (dissolveDeath); from now on the art isn't drawn and there is no terrain to run into.
     */
    void dissolveTerrain() { dissolveTerrain(OP4_TERRAIN_TIME); }

    void dissolveTerrain(float time) {
        int camL = Math.round(g.scrollX), camB = Math.round(g.scrollY), sw = (int) W, sh = (int) H;
        Pixmap shot = new Pixmap(sw, sh, Pixmap.Format.RGBA8888);
        shot.setBlending(Pixmap.Blending.SourceOver);
        for (Level.Art t : g.level.art) {
            if (t.x > camL + W || t.x + t.w < camL || t.y > camB + H || t.y + t.h < camB) continue;
            if (!Gdx.files.internal(t.image).exists()) continue;
            Pixmap ap = new Pixmap(Gdx.files.internal(t.image));
            shot.drawPixmap(ap, Math.round(t.x) - camL, (camB + sh) - Math.round(t.y + t.h));   // (rows run down)
            ap.dispose();
        }
        if (g.eioneusRoom.podsShown()) for (int i = 0; i < g.level.pods.size(); i++)   // (Eioneus: the gun pods go with it)
            shot.drawPixmap(g.eioneusRoom.podPm, Math.round(g.eioneusRoom.podX(i)) - camL, (camB + sh) - Math.round(g.eioneusRoom.podY(i) + g.eioneusRoom.podPm.getHeight()));
        g.dissolveDeath(shot, camL, camB, time);
        shot.dispose();
        terrainGone = true;
        g.level.clearGrid();
        g.a.play("power", 0.4f);
    }

    /** A beaten Op 4 boss: its picture (the gun units: as shot to pieces) breaks up where it is. */
    void bossDissolveStart() {
        bossDissolving = true;
        g.eBullets.clear();
        g.bossGuns.gunLasers.clear();
        if (g.barBosses.barBossNow() && g.barBosses.barPix != null) g.dissolveDeath(g.barBosses.barPix, g.boss.x, g.boss.y, Boss.DISSOLVE_TIME);
        else g.dissolveDeath(g.curBossR(), g.boss.x, g.boss.y, Boss.DISSOLVE_TIME);
        g.a.play("boom", 0.4f);
    }

    /** The gun units' fight: a green mine (Kierke's, homing) comes in at a random edge of the screen, 11 px showing. */
    void spawnEdgeMine() {
        if (g.greenMineF == null) return;
        float in = Enemy.GMINE_IN, x, y;
        switch (MathUtils.random(3)) {
            case 0: x = g.scrollX - 16f + in; y = g.scrollY + MathUtils.random(24f, H - 40f); break;   // left
            case 1: x = g.scrollX + W - in; y = g.scrollY + MathUtils.random(24f, H - 40f); break;     // right
            case 2: x = g.scrollX + MathUtils.random(8f, W - 24f); y = g.scrollY + H - in; break;      // top
            default: x = g.scrollX + MathUtils.random(8f, W - 24f); y = g.scrollY - 16f + in; break;   // bottom
        }
        Enemy e = new Enemy(Enemy.Type.GREEN_MINE, x, y, g.levelIdx);
        e.vertical = true;
        e.gmFromBottom = y < g.scrollY;
        g.enemies.add(e);
    }

    /** The sphere fight: an option eater comes in through the bottom edge (Enemy.updateOptionEater). */
    void spawnOptionEater() {
        if (g.optionEaterF == null) return;
        Enemy e = new Enemy(Enemy.Type.OPTION_EATER, g.scrollX + MathUtils.random(24f, W - 40f), g.scrollY - 16f, g.levelIdx);
        e.vertical = true;
        g.enemies.add(e);
    }

    /** An option eater touching an option eats it: the ship loses an option (its last one) and the eater is used up. */
    void eatOption(Enemy e) {
        for (Player p : g.players) {
            if (!g.alive(p) || p.options <= 0) continue;
            for (int i = 0; i < p.options; i++) {
                if (!g.overlap(e.x + 2f, e.y + 2f, e.w - 4f, e.h - 4f, g.optX(p, i), g.optY(p, i), 12f, 12f)) continue;
                p.options--;
                e.dead = true;
                g.boom(g.optX(p, i) + 6f, g.optY(p, i) + 6f, 1f);
                g.a.play("boom", 0.3f);
                return;
            }
        }
    }

    /** Can a shot (or laser) at this rectangle hit the boss? Op 4's bosses (ghost) only stop shots on their weak spots. */
    boolean bossShotTouches(float x, float y, float w, float h) {
        if (!g.boss.ghost) return g.touchesBoss(x, y, w, h);
        if (g.boss.appearing) return false;
        return g.boss.weakTouches(x, y, w, h) || barShieldTouches(x, y, w, h);
    }

    /** A bar-wall boss's core shield, while it is there (it stops shots even on a boss the ships fly over). */
    boolean barShieldTouches(float x, float y, float w, float h) {
        if (!g.barBosses.barBossNow() || !g.barBosses.barShield || g.barBosses.barShieldPm == null || g.boss == null) return false;
        float sw = g.barBosses.barShieldPm.getWidth(), sh = g.barBosses.barShieldPm.getHeight();
        return g.overlap(x, y, w, h, g.boss.x + g.barBosses.bb.shieldX, g.boss.y + g.boss.h - g.barBosses.bb.shieldY - sh, sw, sh);
    }
}
