package com.example.salamander;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;

import java.util.ArrayList;

/**
 * Lavinia's set pieces (stage 3, moved out of GameScreen): Gun Ho hatches, Heathens, the blocks, the darkness and its
 * spark, Led Zeps, the warship's Rages, lasers bouncing off big blocks.
 */
final class LaviniaStage {
    private static final int W = SalamanderGame.W, H = SalamanderGame.H;
    private final GameScreen g;

    LaviniaStage(GameScreen g) { this.g = g; }

    /** Lavinia (both styles): a Gun Ho hops out of a floor hatch (bottom-left x, y). */
    void hatchGunHo(float cx, float y) {
        Enemy e = new Enemy(Enemy.Type.GUN_HO, cx, y, g.levelIdx);
        if (g.gunHoR != null) { e.w = g.gunHoR.getRegionWidth(); e.h = g.gunHoR.getRegionHeight(); }
        e.x = cx - e.w / 2f;                          // (x given is the middle of the hatch)
        e.gunState = Enemy.GUNHO_HOPPING;
        g.enemies.add(e);
    }

    /** Heathens (Lavinia): the three pictures (gap at the top / centre / bottom), and the pillars' shapes without the bars. */
    TextureRegion[] heathenR;
    PixelMask[] heathenMasks;
    static final String[] HEATHEN_PICS = {"heathen_top", "heathen_center", "heathen_bottom"};

    void loadHeathens() {
        for (String n : HEATHEN_PICS) if (!Gdx.files.internal("sprites/" + n + ".png").exists()) return;
        heathenR = new TextureRegion[3];
        heathenMasks = new PixelMask[3];
        for (int k = 0; k < 3; k++) {
            heathenR[k] = g.a.one(HEATHEN_PICS[k]);
            // the picture is opaque (black inside): the pillar is its non-black pixels, without the bars
            com.badlogic.gdx.graphics.Pixmap pm = new com.badlogic.gdx.graphics.Pixmap(Gdx.files.internal("sprites/" + HEATHEN_PICS[k] + ".png"));
            int pw = pm.getWidth(), ph = pm.getHeight();
            boolean[] solid = new boolean[pw * ph];
            int bt = Enemy.HEATHEN_BAR_TOP[k];
            for (int yy = 0; yy < ph; yy++)
                for (int xx = 0; xx < pw; xx++) {
                    int c = pm.getPixel(xx, yy);
                    boolean lit = (c & 0xff) > 0 && ((c >>> 24) & 0xff) + ((c >>> 16) & 0xff) + ((c >>> 8) & 0xff) > 30;
                    boolean inBar = false;
                    for (int b = 0; b < Enemy.HEATHEN_BARS; b++) {
                        int bx = Enemy.HEATHEN_BAR_X + b * Enemy.HEATHEN_BAR_STEP;
                        if (xx >= bx && xx < bx + Enemy.HEATHEN_BAR_W && yy >= bt && yy < bt + Enemy.HEATHEN_BAR_H) inBar = true;
                    }
                    solid[(ph - 1 - yy) * pw + xx] = lit && !inBar;
                }
            pm.dispose();
            heathenMasks[k] = new PixelMask(pw, ph, solid);
        }
    }

    TextureRegion blockSmallR, blockBigR, rageR, laserGunR;

    /**
     * Lavinia's darkness (as in the MSX recording): from a "dark_zone" on, the stage's lights are off - no background,
     * stars or terrain are drawn (it is all still there and still solid); only the ships, enemies, domes, shots and
     * the item_spark show. A ship touching the spark turns the lights straight back on.
     */
    boolean[] darkLit = new boolean[0];
    /** The item_sparks still to be picked up ({x, y} bottom-left). */
    final ArrayList<float[]> sparkItems = new ArrayList<>();
    TextureRegion sparkR;
    float sparkT;
    public static final float SPARK_SIZE = 16f;

    /** The lights are off: the camera is inside a dark zone whose spark hasn't been picked up. */
    boolean lightsOut() {
        float camL = g.scrollX;
        for (int i = 0; i < g.level.darkZones.size(); i++) {
            float[] z = g.level.darkZones.get(i);
            if (camL >= z[0] && camL < z[1] && !(i < darkLit.length && darkLit[i])) return true;
        }
        return false;
    }

    /** Darkness after a (re)start at camera x sx: sparks behind the camera count as picked up. */
    void resetDarkness(float sx) {
        if (darkLit.length != g.level.darkZones.size()) darkLit = new boolean[g.level.darkZones.size()];
        sparkItems.clear();
        for (int i = 0; i < darkLit.length; i++) darkLit[i] = false;
        for (float[] s : g.level.sparks) {
            if (s[0] + SPARK_SIZE < sx) lightUp(s[0]);
            else sparkItems.add(new float[]{s[0], s[1]});
        }
    }

    /** A spark at x picked up: the lights come on in the dark zone it is in (in every zone that has started, if none). */
    void lightUp(float x) {
        boolean any = false;
        for (int i = 0; i < darkLit.length; i++) {
            float[] z = g.level.darkZones.get(i);
            if (x >= z[0] && x < z[1]) { darkLit[i] = true; any = true; }
        }
        if (!any) for (int i = 0; i < darkLit.length; i++) if (g.scrollX >= g.level.darkZones.get(i)[0]) darkLit[i] = true;
    }

    void collideSparks(Player p) {
        for (int i = sparkItems.size() - 1; i >= 0; i--) {
            float[] s = sparkItems.get(i);
            if (g.overlap(p.x + 2f, p.y, 28f, 16f, s[0], s[1], SPARK_SIZE, SPARK_SIZE)) {
                sparkItems.remove(i);
                lightUp(s[0]);
                p.score += 300;
                g.a.play("pickup", 0.35f);
            }
        }
    }

    void drawSparks() {
        if (sparkItems.isEmpty()) return;
        if (sparkR == null) {
            String f = Gdx.files.internal("sprites/item_spark.png").exists() ? "item_spark" : "spark";
            sparkR = new TextureRegion(g.a.tex(f));
        }
        sparkT += Gdx.graphics.getDeltaTime();
        if (((int) (sparkT * 8f)) % 4 == 3) return;   // a little twinkle
        for (float[] s : sparkItems) g.batch.draw(sparkR, s[0], s[1], SPARK_SIZE, SPARK_SIZE);
    }
    TextureRegion[] ledZepF, gammaRedF;
    /** Lavinia Led Zeps: seconds to the next one. */
    float ledZepT = 1f;

    /** Lavinia: while the camera's right edge is in a "ledzep_zone", Led Zeps come at random at either edge, on the floor or the ceiling. */
    void updateLedZeps(float dt) {
        if (g.level.ledZepZones.isEmpty()) return;
        float right = g.scrollX + W;
        float[] zone = null;
        for (float[] z : g.level.ledZepZones) if (right >= z[0] && right <= z[1]) zone = z;
        if (zone == null) return;
        if ((ledZepT -= dt) > 0f) return;
        ledZepT = MathUtils.random(0.5f, 1.5f) * zone[2];
        boolean fromLeft = MathUtils.random() < 0.3f, ceil = MathUtils.randomBoolean();
        float ex = fromLeft ? g.scrollX - 16f : g.scrollX + W, mid = g.scrollY + H / 2f;
        float fl = g.level.floorTop(ex + 8f, mid), cb = g.level.ceilBottom(ex + 8f, mid);
        if (fl <= Level.NO_FLOOR || cb >= Level.NO_CEILING || cb - fl < 48f) return;   // (no corridor there)
        Enemy e = new Enemy(Enemy.Type.LED_ZEP, ex, ceil ? cb - 16f : fl, g.levelIdx);
        e.zepFloorY = fl; e.zepCeilY = cb - 16f; e.zepCeil = ceil; e.zepRight = fromLeft;
        e.zepT = MathUtils.random(Enemy.LEDZEP_WALK_MIN, Enemy.LEDZEP_WALK_MAX);
        g.enemies.add(e);
    }
    /** The Lavinia warship's hatch: closed, open, destroyed. */
    TextureRegion[] hatchF;

    /** The Lavinia warship launches a Rage out of its hatch (bottom-left of the Rage). */
    void launchRage(float x, float y) {
        Enemy e = new Enemy(Enemy.Type.RAGE, x, y, g.levelIdx);
        g.enemies.add(e);
    }

    /** A ship's laser hit a Lavinia big block: it bounces straight back to the left, as long as the laser was. */
    void reflectLaser(Bullet laser, Enemy blk) {
        float len = Math.min(Math.max(16f, laser.w), 64f), h = Math.max(2f, laser.h);
        Bullet r = new Bullet(Bullet.Kind.ENEMY, blk.x - len, laser.y, len, h);
        r.vx = -360f;
        r.reflected = true;
        g.eBullets.add(r);
    }

    /** A Heathen's bar was shot away. */
    void breakHeathenBar(Enemy e, int k, Player owner) {
        e.heathenBarHp[k] = 0;
        g.boom(e.heathenBarX(k) + Enemy.HEATHEN_BAR_W / 2f, e.heathenBarY() + Enemy.HEATHEN_BAR_H / 2f, 0.5f);
        g.addScore(owner, Enemy.HEATHEN_BAR_SCORE);
        g.a.play("boom", 0.15f);
    }
}
