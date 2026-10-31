package com.example.salamander;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.ScreenUtils;

import java.util.ArrayList;

/**
 * Drawing one frame of a stage (moved out of GameScreen): stars, background lights, the Kierke web, terrain,
 * hazards, enemies, the boss, bullets, the ships and the HUD. The pictures and the world it draws stay in
 * GameScreen (g); the boss-specific pieces (walls, pods, gun lasers, ...) are still drawn there.
 */
final class GameRenderer {
    private static final int W = SalamanderGame.W, H = SalamanderGame.H;
    private static final int SW = SalamanderGame.SCREEN_W, SH = SalamanderGame.SCREEN_H, HUD_H = SalamanderGame.HUD_H;
    private final GameScreen g;

    GameRenderer(GameScreen g) { this.g = g; }

    /**
     * The star field (parallax 0.6), except over the map's "noStars" range (Level.noStarsLo..Hi along the scrolling),
     * which stays plain black.
     */
    void drawStars(float camL, float camB) {
        if (Float.isNaN(g.level.noStarsLo)) { starRect(camL, camB, 0, 0, (int) W, (int) H); return; }
        float cam = g.level.vertical ? camB : camL, len = g.level.vertical ? H : W;
        int a = (int) MathUtils.clamp(g.level.noStarsLo - cam, 0f, len), b = (int) MathUtils.clamp(g.level.noStarsHi - cam, 0f, len);
        if (g.level.vertical) { starRect(camL, camB, 0, 0, (int) W, a); starRect(camL, camB, 0, b, (int) W, (int) H - b); }
        else { starRect(camL, camB, 0, 0, a, (int) H); starRect(camL, camB, b, 0, (int) W - b, (int) H); }
    }

    /** The part of the star field over the screen rectangle x0, y0 (from the bottom-left), w x h. */
    void starRect(float camL, float camB, int x0, int y0, int w, int h) {
        if (w <= 0 || h <= 0) return;
        int u = (int) (g.scrollX * 0.6f) + x0, v = (int) (-g.scrollY * 0.6f) + (int) H - y0 - h;   // (texture rows run down)
        g.batch.draw(g.starTex, camL + x0, camB + y0, w, h, u, v, w, h, false, false);
    }

    /**
     * The start of a stage, as in the MSX recordings: the operation banner alone on a black screen for INTRO_BANNER s,
     * black for INTRO_BLACK s, then the HUD on a black playfield for INTRO_HUD s; then the stage comes in.
     */
    static final float INTRO_BANNER = 2f, INTRO_BLACK = 0.6f, INTRO_HUD = 1f;

    /** The intro: the banner (white, MSX-style letters; lines 76 / 92 / 116 px below the top as on the MSX), or the HUD alone. */
    void drawIntro() {
        g.batch.setProjectionMatrix(g.hud.combined);
        g.batch.begin();
        if (g.stateT < INTRO_BANNER) {
            float top = HUD_H + H;
            String sub = g.bossTest >= 0 ? "BOSS TEST" : g.level.subtitle;
            say(g.level.bannerTitle, top - 76f - 7f, PixelFont.BOLD, 1, HUD_WHITE);
            if (!sub.isEmpty()) say(sub, top - 92f - 7f, PixelFont.BOLD, 1, g.bossTest >= 0 ? Color.ORANGE : HUD_WHITE);
            if (!g.level.planet.isEmpty()) say(g.level.planet, top - 116f - 7f, PixelFont.BOLD, 1, HUD_WHITE);
        } else if (g.stateT >= INTRO_BANNER + INTRO_BLACK) {
            drawHud();
        }
        g.batch.end();
    }

    void draw() {
        ScreenUtils.clear(0, 0, 0, 1);
        g.vp.apply();
        if (g.state == GameScreen.State.INTRO) { drawIntro(); return; }
        // the playfield fills the screen above the HUD strip: world scrollY lands on screen row HUD_H
        g.cam.position.set(Math.round(g.scrollX) + SW / 2f, Math.round(g.scrollY) + H - SH / 2f, 0f);
        g.cam.update();
        g.batch.setProjectionMatrix(g.cam.combined);
        g.batch.begin();
        float camL = Math.round(g.scrollX), camB = Math.round(g.scrollY);
        boolean dark = g.lightsOut();   // Lavinia: the lights are off (only what moves is seen)
        if (g.level.background != null && !dark) {   // map property "background"; stage 1 is plain black space
            g.batch.draw(g.bgTex, camL, camB, W, H, (int) (g.scrollX * 0.25f), 0, W, H, false, false);
        }
        g.batch.setColor(g.STAR_TINT[g.levelIdx]);
        if (!dark) drawStars(camL, camB);
        g.batch.setColor(Color.WHITE);

        if (!dark) {
            drawFangs();      // teeth first, so the ground hides the part that is pulled in
            drawTerrain();
        }
        g.drawSparks();
        g.drawPods();           // Eioneus's gun pods
        g.drawDissolving();     // pictures breaking up (dissolveDeath)
        drawGround();         // bases and destroyed enemies that stay: under everything in play
        drawHazards();
        drawMeteors();
        drawCapsules();
        drawEnemies();
        if (g.boss != null) drawBoss();
        g.drawGunLasers();
        drawBullets();
        for (Player p : g.players) if (g.alive(p)) drawPlayer(p);
        if (g.debugHitbox) for (Player p : g.players) if (g.alive(p)) drawHitbox(p);
        for (GameScreen.Explosion e : g.fx) {
            int f = Math.min(4, (int) (e.t * 14f));
            float s = 24f * e.scale;
            g.batch.draw(g.boomF[f], e.x - s / 2f, e.y - s / 2f, s, s);
        }
        g.batch.end();

        g.batch.setProjectionMatrix(g.hud.combined);
        g.batch.begin();
        drawHud();
        g.batch.end();
    }

    // ---- animated web (Kierke, from the MSX recording): maps/stage<N>_web.png, the size of the map art. Its
    // pixels are the web pixels that flicker: in the MSX the web's strands hop 1 px back and forth as the screen
    // scrolls, one cycle per 8 px of scrolling (8 states, one per pixel scrolled; the recording: a state every ~3
    // frames at 20 px/s). Each pixel is lit for 4 states in a row and dark for the other 4: its colour is the colour
    // when lit, its alpha = 255 - 16 * phase - (8 if the map art doesn't show it): phase 0..7 = the first state it is
    // lit in. The state is (camera x - WEB_PHASE0) mod 8 (the MSX's 8 px scroll steps fall on x = 5 mod 8 in the map).
    private static final int WEB_PHASE0 = 5;
    private int[] webX, webY, webPh, webRgba;
    private boolean[] webArt;
    private final Color webColor = new Color();

    void loadWeb() {
        String f = "maps/stage" + (g.levelIdx + 1) + "_web.png";
        if (!Gdx.files.internal(f).exists()) return;
        Pixmap pm = new Pixmap(Gdx.files.internal(f));
        int n = 0;
        for (int x = 0; x < pm.getWidth(); x++) for (int y = 0; y < pm.getHeight(); y++) if ((pm.getPixel(x, y) & 0xff) != 0) n++;
        webX = new int[n]; webY = new int[n]; webPh = new int[n]; webRgba = new int[n]; webArt = new boolean[n];
        int i = 0;
        for (int x = 0; x < pm.getWidth(); x++)                 // (sorted by x, for drawing only what is on screen)
            for (int y = 0; y < pm.getHeight(); y++) {
                int c = pm.getPixel(x, y), al = c & 0xff;
                if (al == 0) continue;
                int v = 255 - al;
                webX[i] = x; webY[i] = y;                         // (row from the top, like the art)
                webPh[i] = (v / 16) & 7; webArt[i] = v % 16 == 0; webRgba[i] = c | 0xff;
                i++;
            }
        pm.dispose();
    }

    /** The web's flickering pixels for the current scroll position (lit, or black over the art's pixel). */
    void drawWeb(float camL) {
        if (webX == null || webX.length == 0) return;
        int state = Math.floorMod(Math.round(camL) - WEB_PHASE0, 8);
        int lo = 0, hi = webX.length;                         // first pixel at x >= camL - 1
        while (lo < hi) { int m = (lo + hi) >>> 1; if (webX[m] < camL - 1) lo = m + 1; else hi = m; }
        float top = g.level.art.isEmpty() ? g.level.worldH : g.level.art.get(0).y + g.level.art.get(0).h;   // the art's top edge
        for (int i = lo; i < webX.length && webX[i] <= camL + W; i++) {
            boolean lit = Math.floorMod(state - webPh[i], 8) < 4;
            if (lit) Color.rgba8888ToColor(webColor, webRgba[i]);
            else if (webArt[i]) webColor.set(0f, 0f, 0f, 1f);
            else continue;
            g.batch.setColor(webColor);
            g.batch.draw(g.pix, webX[i], top - 1f - webY[i], 1f, 1f);
        }
        g.batch.setColor(Color.WHITE);
    }

    /**
     * The background lights are also painted in the stage art (one frame each); a light's own pixels are taken out of
     * the art where a "back_ground_lights" object is, so only the animation shows. Only pixels that match one of the
     * sprite's frames there are taken out, so a light moved onto other art in Tiled doesn't punch a hole in it.
     */
    void unbakeBgLights() {
        if (g.bgLightsF == null || g.level.bgLights.isEmpty()) return;
        float n = g.bgLightsSize;
        for (int i = 0; i < g.artTex.length; i++) {
            Level.Art t = g.level.art.get(i);
            boolean any = false;
            for (float[] L : g.level.bgLights)
                if (!(L[0] + n <= t.x || L[0] >= t.x + t.w || L[1] + n <= t.y || L[1] >= t.y + t.h)) { any = true; break; }
            if (any) g.artTex[i] = g.a.made(t.image + "#no_bg_lights", () -> unbakeBgLights(t));
        }
    }

    /** A copy of the art picture t with the lights' pixels taken out (t has at least one light on it). */
    Texture unbakeBgLights(Level.Art t) {
        Pixmap lp = new Pixmap(Gdx.files.internal("sprites/" + g.bgLightsFile + ".png"));
        Pixmap pm = new Pixmap(Gdx.files.internal(t.image));
        pm.setBlending(Pixmap.Blending.None);
        int n = g.bgLightsSize;
        for (float[] L : g.level.bgLights) {
            if (L[0] + n <= t.x || L[0] >= t.x + t.w || L[1] + n <= t.y || L[1] >= t.y + t.h) continue;
            int ax = Math.round(L[0] - t.x), ay = Math.round(t.y + t.h - L[1] - n);   // (art rows run down)
            for (int yy = 0; yy < n; yy++)
                for (int xx = 0; xx < n; xx++) {
                    int c = pm.getPixel(ax + xx, ay + yy);
                    if ((c & 0xff) == 0) continue;
                    for (int f = 0; f < g.bgLightsF.length; f++) {
                        int lc = lp.getPixel(f * n + xx, yy);
                        if ((lc & 0xff) != 0 && (lc | 0xff) == (c | 0xff)) { pm.drawPixel(ax + xx, ay + yy, 0); break; }
                    }
                }
        }
        Texture tx = new Texture(pm);
        tx.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        pm.dispose(); lp.dispose();
        return tx;
    }

    /** The background lights on screen: each cycles its frames, level.bgLightsFrame s apiece (from the frame the map gives it). */
    void drawBgLights(float camL, float camB) {
        if (g.bgLightsF == null) return;
        int step = (int) (g.bgLightsClock / g.level.bgLightsFrame), n = g.bgLightsSize;
        for (float[] L : g.level.bgLights) {
            if (L[0] > camL + W || L[0] + n < camL || L[1] > camB + H || L[1] + n < camB) continue;
            g.batch.draw(g.bgLightsF[Math.floorMod((int) L[2] + step, g.bgLightsF.length)], L[0], L[1]);
        }
    }

    /** Stage art (image layers), the "terrain" tile layer, and the bricks that are still standing. */
    void drawTerrain() {
        float camL = Math.round(g.scrollX), camB = Math.round(g.scrollY);
        for (int i = 0; i < g.artTex.length && !g.terrainGone; i++) {   // (Op 4's end: dissolved)
            Level.Art t = g.level.art.get(i);
            if (t.x > camL + W || t.x + t.w < camL || t.y > camB + H || t.y + t.h < camB) continue;
            g.batch.draw(g.artTex[i], t.x, t.y, t.w, t.h);
        }
        if (!g.terrainGone) drawWeb(camL);                 // (Kierke: the web flickers as the screen scrolls)
        if (!g.terrainGone) drawBgLights(camL, camB);      // (Op 4: the background lights, still under every sprite)
        g.drawWalls();                                     // stage 3: the end room's wall (until it has dissolved)
        float s = g.level.cell;
        int c0 = Math.max(0, (int) (camL / s) - 1), c1 = Math.min(g.level.gCols - 1, (int) ((camL + W) / s) + 1);
        int r0 = Math.max(0, (int) (camB / s) - 1), r1 = Math.min(g.level.gRows - 1, (int) ((camB + H) / s) + 1);
        if (g.level.terrain != null) {
            for (int r = r0; r <= r1; r++)
                for (int c = c0; c <= c1; c++) {
                    int gid = g.level.terrain[r * g.level.gCols + c];
                    if (gid != 0) drawTile(gid, c * s, r * s, s);
                }
        }
        if (g.level.bricks != null && g.brickArtTex != null) {   // each brick shows its own piece of the map's brick picture
            int top = g.brickArtTex.getHeight(), cs = (int) s;
            for (int r = r0; r <= r1; r++)
                for (int c = c0; c <= c1; c++) {
                    int i = r * g.level.gCols + c;
                    float alpha = g.level.bricks[i] ? 1f : g.level.brickGrow[i];
                    if (alpha <= 0f) continue;
                    if (alpha < 1f) g.batch.setColor(1f, 1f, 1f, alpha);
                    g.batch.draw(g.brickArtTex, c * s, r * s, s, s, c * cs, top - (r + 1) * cs, cs, cs, false, false);
                    if (alpha < 1f) g.batch.setColor(Color.WHITE);
                }
        } else if (g.level.bricks != null) {
            float p = g.brickTex.getWidth();   // the brick pattern repeats seamlessly across the wall
            for (int r = r0; r <= r1; r++)
                for (int c = c0; c <= c1; c++) {
                    int i = r * g.level.gCols + c;
                    float alpha = g.level.bricks[i] ? 1f : g.level.brickGrow[i];   // materializing bricks fade in
                    if (alpha <= 0f) continue;
                    float x = c * s, y = r * s;
                    if (alpha < 1f) g.batch.setColor(1f, 1f, 1f, alpha);
                    g.batch.draw(g.brickTex, x, y, s, s, x / p, -y / p, (x + s) / p, -(y + s) / p);
                    if (alpha < 1f) g.batch.setColor(Color.WHITE);
                }
        }
        if (g.debugHitbox) {   // H: show the solid cells of the map (terrain + collision layers)
            g.batch.setColor(1f, 0.15f, 0.15f, 0.35f);
            for (int r = r0; r <= r1; r++)
                for (int c = c0; c <= c1; c++)
                    if (g.level.solidTerrainCell(c, r)) g.batch.draw(g.pix, c * s, r * s, s, s);
            g.batch.setColor(Color.WHITE);
        }
    }

    private final java.util.HashMap<Integer, TextureRegion> tileRegions = new java.util.HashMap<>();

    /** Draws one map tile (with Tiled's flip flags) at world position (x, y). */
    void drawTile(int gid, float x, float y, float size) {
        int id = gid & TiledMap.GID_MASK;
        TextureRegion r = tileRegions.get(id);
        if (r == null) {
            TiledMap.Tileset ts = g.level.tiled.tilesetFor(id);
            if (ts == null || ts.image == null) return;
            int local = id - ts.firstGid, cols = Math.max(1, ts.columns);
            r = new TextureRegion(g.a.texFile(ts.image), (local % cols) * ts.tileWidth, (local / cols) * ts.tileHeight,
                    ts.tileWidth, ts.tileHeight);
            tileRegions.put(id, r);
        }
        boolean fh = (gid & TiledMap.FLIP_H) != 0, fv = (gid & TiledMap.FLIP_V) != 0;
        g.batch.draw(r, fh ? x + size : x, fv ? y + size : y, fh ? -size : size, fv ? -size : size);
    }

    void drawFangs() {
        for (Hazard h : g.hazards) {
            if ((h.type != Hazard.Type.FANG && h.type != Hazard.Type.TOWER) || h.x > g.scrollX + W + 40f || h.x + h.w < g.scrollX) continue;
            if (h.flipV) g.batch.draw(g.a.texFile(h.image), h.x, h.y + h.h, h.w, -h.h);   // flipped in Tiled
            else g.batch.draw(g.a.texFile(h.image), h.x, h.y, h.w, h.h);
        }
    }

    /** Claw arm: base and orange ball, blue ball, then the claw turned toward where it reaches. */
    void drawArm(Hazard h) {
        int n = h.armX.length - 1;
        for (int i = 0; i < n; i++) {
            if (!h.armBallOut(i)) continue;
            TextureRegion r = g.armF[h.bossArm || i == h.armWeak ? 1 : 0];   // blue ball(s), else orange
            g.batch.draw(r, Math.round(h.armX[i] - 8f), Math.round(h.armY[i] - 8f));
        }
        float dx = h.armX[n] - h.armX[n - 1], dy = h.armY[n] - h.armY[n - 1];
        float turn = dx * dx + dy * dy < 0.01f ? 0f        // still inside the boss: as it rests
                : (MathUtils.atan2(dy, dx) - MathUtils.atan2(h.restDY, h.restDX)) * MathUtils.radiansToDegrees;
        g.batch.draw(g.armF[h.fromCeil || h.bossArm ? 3 : 2], Math.round(h.armX[n] - 8f), Math.round(h.armY[n] - 8f), 8f, 8f, 16f, 16f, 1f, 1f, turn);
    }

    void drawHazards() {
        for (Hazard h : g.hazards) {
            if (h.type == Hazard.Type.FANG || h.type == Hazard.Type.TOWER) continue;   // drawn behind the terrain
            if (h.x > g.scrollX + W + 40f || h.x + Math.max(40f, h.w + 60f) < g.scrollX) continue;
            if (g.level.vertical && (h.y > g.scrollY + H + 40f || h.y + h.h < g.scrollY - 40f)) continue;
            switch (h.type) {
                case DOME: {                                 // facing right on the left wall, mirrored on the right wall
                    TextureRegion f = g.domeF[h.open ? 1 : 0];
                    float fw = f.getRegionWidth(), fh = f.getRegionHeight();
                    if (h.domeSide == Hazard.DOME_FLOOR)      // turned a quarter to face up (matches PixelMask.rotatedCCW)
                        g.batch.draw(f, h.x + h.w, h.y, 0f, 0f, fw, fh, 1f, 1f, 90f);
                    else if (h.domeSide == Hazard.DOME_CEIL)  // turned a quarter to face down (PixelMask.rotatedCW)
                        g.batch.draw(f, h.x, h.y + h.h, 0f, 0f, fw, fh, 1f, 1f, -90f);
                    else g.batch.draw(f, h.fromCeil ? h.x + h.w : h.x, h.y, h.fromCeil ? -h.w : h.w, h.h);
                    break;
                }
                case EYE:
                    g.batch.draw(g.rockEyeR, h.x, h.y, h.w, h.h);
                    break;
                case RUBBLE:
                    g.batch.draw(g.rubbleR, h.x, h.y, h.w, h.h);
                    break;
                case ROCK:
                    g.batch.draw(g.rockR, h.x, h.y, 16, 16);
                    break;
                case ARM:
                    if (!h.bossArm) drawArm(h);              // boss arms are drawn with the boss, in front of it
                    break;
                case BAMDA:
                    g.batch.draw(g.bamdaF[h.frame], h.x, h.y);
                    break;
                default: // volcano: glowing crater (a side volcano is just its rock tip in the map art)
                    if (h.spit != 0) break;
                    float pulse = 0.5f + 0.5f * MathUtils.sin(h.t * 6f);
                    g.batch.setColor(1f, 0.4f + 0.3f * pulse, 0.1f, 1f);
                    g.batch.draw(g.pix, h.x - 6f, h.y, 12, 2);
                    g.batch.setColor(Color.WHITE);
                    break;
            }
        }
    }

    void drawMeteors() {
        float cl = Math.round(g.scrollX), cb = Math.round(g.scrollY);   // (the camera is on whole pixels)
        for (Meteor m : g.meteors) {
            if (m.dead) continue;
            float x = cl + Math.round(m.sx), y = cb + Math.round(m.sy);
            if (m.red) g.batch.draw(g.rockRedF[m.frame], x, y);
            else if (m.big) g.batch.draw(g.rockBigR, x, y);
            else g.batch.draw(g.rockSmallF[(int) (m.t * Meteor.SMALL_FPS) % g.rockSmallF.length], x, y);
        }
    }

    void drawCapsules() {
        for (GameScreen.Capsule c : g.caps) {
            TextureRegion[] fr = c.green ? g.capGreenF : g.capF;
            TextureRegion f = fr[(int) (c.t * 6f) % fr.length];
            g.batch.draw(f, c.x, c.y, f.getRegionWidth(), f.getRegionHeight());
        }
    }

    /**
     * The ground (Albert, 2026-10-29): the bases (Eioneus, Op 4), alive or destroyed, and every destroyed enemy that
     * stays (withered roots): drawn before capsules, hazards and every enemy still in play, so nothing hides under them.
     */
    void drawGround() {
        for (Enemy e : g.enemies) {                     // Op 4 bases are ground too (the whole picture)
            if (e.dead || e.type != Enemy.Type.SEED || !e.seedOp4 || g.base6F == null) continue;
            g.batch.draw(g.base6F[Math.min(e.seedFrame(), g.base6F.length - 1)], e.x, e.y, e.w, e.h);
        }
        if (g.baseF != null)                            // Eioneus bases are ground: under every other enemy
            for (Enemy e : g.enemies) {
                if (e.dead || e.type != Enemy.Type.SEED || e.seedOp4) continue;
                TextureRegion sr = g.baseF[Math.min(e.seedFrame(), g.baseF.length - 1)];   // closed / opening / open / destroyed
                float sw = sr.getRegionWidth(), sh = sr.getRegionHeight();
                g.batch.draw(sr, e.x + (e.w - sw) / 2f, e.y + (e.h - sh) / 2f, sw, sh);
            }
        for (Enemy e : g.enemies) {                     // destroyed ones that stay (the bases are drawn above)
            if (e.dead || !e.withered || e.type == Enemy.Type.SEED) continue;
            if (e.type == Enemy.Type.ABERRANT && g.aberrantF != null) g.batch.draw(g.aberrantF[1], e.x, e.y, e.w, e.h);
        }
    }

    void drawEnemies() {
        for (Enemy e : g.enemies) {
            if (e.withered) continue;                     // (ground: drawGround)
            TextureRegion r;
            switch (e.type) {
                case LEGACY: {
                    // all frames in a loop, whichever way it is heading (Albert, 2026-10-29): 0 1 2 ... 6 0 1 ...
                    int k = (int) (Math.max(0f, e.t) * Enemy.LEGACY_FPS) % g.legacyF.length;
                    r = g.legacyF[k];
                    break;
                }
                case WALKER: r = g.walkF[(int) (e.t * 6f) % g.walkF.length]; break;
                case FROST: r = g.frostR; break;
                case LINK: r = g.linkF[e.linkSwing > 0f ? e.linkFrame() : (int) (e.t * Enemy.LINK_FPS) % g.linkF.length]; break;
                case RUGAL: r = g.rugalF[Math.min(e.rugalFrame(), g.rugalF.length - 1)]; break;
                case AMOEBA: r = g.amoebaF[(int) (e.t * Enemy.AMOEBA_FPS) % g.amoebaF.length]; break;
                case DOME_FLYER: {                       // forward, then backward: 0 1 2 3 2 1 0 ...
                    if (g.domeFlyerF == null) { r = g.amoebaF[0]; break; }
                    int n = g.domeFlyerF.length, cycle = Math.max(1, 2 * n - 2), k = (int) (e.t * Enemy.DOME_FLYER_FPS) % cycle;
                    r = g.domeFlyerF[k < n ? k : cycle - k];
                    break;
                }
                default: r = null; break;
            }
            boolean red = e.group != null && e.group.carrier;
            if (e.type == Enemy.Type.WALKER && g.bioWalkRedF != null) {   // stage 1 bio walker: ping-pong walk cycle
                TextureRegion[] fr = red ? g.bioWalkRedF : g.bioWalkBlueF;
                int n = fr.length, cycle = 2 * n - 2;                 // 0 1 2 3 4 3 2 1 0 ...
                int k = (int) (e.t * g.BIO_WALK_FPS) % cycle;
                TextureRegion f = fr[k < n ? k : cycle - k];
                int dir = e.vx < 0f ? -1 : 1;
                float rot = (e.ceiling ? -1 : 1) * dir * e.climb * 45f;   // still tilts at steps
                g.batch.draw(f, e.x, e.y, e.w / 2f, e.h / 2f, e.w, e.h, 1f, e.ceiling ? -1f : 1f, rot);
                continue;
            }
            if (e.type == Enemy.Type.LASER_GUARD) {
                if (g.laserGuardTex != null) drawLaserGuard(e);
                continue;
            }
            if (e.type == Enemy.Type.OMEN) {          // no animation; upside down on the ceiling
                if (g.omenR != null) g.batch.draw(g.omenR, e.x, e.ceiling ? e.y + e.h : e.y, e.w, e.ceiling ? -e.h : e.h);
                continue;
            }
            if (e.type == Enemy.Type.GUN_HO) {        // faces left; mirrored when it turns to a ship behind it
                // frame 1 while it lines up with a ship to fire; after the shot (and before) frame 0
                boolean firing = e.gunState == Enemy.GUNHO_AIMING || e.gunState == Enemy.GUNHO_FIRING;   // (until its last shot)
                TextureRegion gr = firing && g.gunHoFireR != null ? g.gunHoFireR : g.gunHoR;
                if (gr != null) g.batch.draw(gr, e.faceRight ? e.x + e.w : e.x, e.y, e.faceRight ? -e.w : e.w, e.h);
                continue;
            }
            if (e.type == Enemy.Type.TURRET_WALL) {   // stage 2 wall turret: red = carries a capsule
                TextureRegion f = (red ? g.wallTurretRedF : g.wallTurretGreenF)[e.aim == Enemy.WALL_SIDE ? 0 : 1];
                boolean flipX = e.wallRight, flipY = e.aim == Enemy.WALL_UP;   // (the diagonal frame points down)
                g.batch.draw(f, flipX ? e.x + e.w : e.x, flipY ? e.y + e.h : e.y, flipX ? -e.w : e.w, flipY ? -e.h : e.h);
                continue;
            }
            if (e.type == Enemy.Type.POSSESSED) {       // red or cyan picture, as it is
                TextureRegion pr = e.possRed ? g.possRedR : g.possCyanR;
                if (pr != null) g.batch.draw(pr, e.x, e.y, e.w, e.h);
                continue;
            }
            if (e.type == Enemy.Type.ABERRANT) {       // alive / withered (blue)
                if (g.aberrantF != null) g.batch.draw(g.aberrantF[e.withered ? 1 : 0], e.x, e.y, e.w, e.h);
                continue;
            }
            if (e.type == Enemy.Type.NEST) {           // the 38x22 picture around the 32x17 nest; floor nests upside down
                if (g.nestR == null) continue;
                float pw = g.nestR.getRegionWidth(), ph = g.nestR.getRegionHeight();
                if (e.nestDown) g.batch.draw(g.nestR, e.x - 3f, e.y - 3f, pw, ph);
                else g.batch.draw(g.nestR, e.x - 3f, e.y - 2f + ph, pw, -ph);
                continue;
            }
            if (e.type == Enemy.Type.SEED) continue;  // (drawn first, under the other enemies)
            if (e.type == Enemy.Type.FLYING_MINE) {
                TextureRegion[] mf = e.mineRed && g.mineRedF != null ? g.mineRedF : g.mineWhiteF;
                if (mf != null) g.batch.draw(mf[Math.min(e.mineFrame(), mf.length - 1)], e.x, e.y, e.w, e.h);
                continue;
            }
            if (e.type == Enemy.Type.GREMLIN) {        // turned a quarter to stand on its wall (feet to the rock)
                TextureRegion[] gf = e.gremRed ? g.gremlinRedF : g.gremlinYellowF;
                if (gf == null) continue;
                TextureRegion fr = gf[Math.min(e.gremlinFrame(), gf.length - 1)];
                float ox = e.w / 2f, oy = e.h / 2f;
                if (e.gremRight) g.batch.draw(fr, e.x, e.y, ox, oy, e.w, e.h, 1f, 1f, 90f);
                else g.batch.draw(fr, e.x, e.y, ox, oy, e.w, e.h, 1f, -1f, 90f);   // (the right-wall picture mirrored)
                continue;
            }
            if (e.type == Enemy.Type.BLOCK_SMALL || e.type == Enemy.Type.BLOCK_BIG) {
                TextureRegion br = e.type == Enemy.Type.BLOCK_BIG ? g.blockBigR : g.blockSmallR;
                if (br != null) g.batch.draw(br, e.x, e.y);
                continue;
            }
            if (e.type == Enemy.Type.LED_ZEP) {         // the picture walks left on the floor: mirrored heading right, upside down on the ceiling
                if (g.ledZepF != null) {
                    TextureRegion f = g.ledZepF[e.zepState == 0 ? 0 : g.ledZepF.length - 1];   // frame 0 on the ground, frame 1 in the air (Albert)
                    g.batch.draw(f, e.zepRight ? e.x + e.w : e.x, e.zepCeil ? e.y + e.h : e.y, e.zepRight ? -e.w : e.w, e.zepCeil ? -e.h : e.h);
                }
                continue;
            }
            if (e.type == Enemy.Type.LASER_GUN) {       // the gun (mirrored pointing left), then its pulse
                if (g.laserGunR != null) g.batch.draw(g.laserGunR, e.lgunLeft ? e.x + e.w : e.x, e.y, e.lgunLeft ? -e.w : e.w, e.h);
                if (e.beamW > 0f && g.omenLaserTex != null) {
                    Texture lt = g.omenLaserTex[(int) (e.t * Enemy.OMEN_LASER_FLASH) % g.omenLaserTex.length];
                    g.batch.draw(lt, e.beamX, e.beamY, e.beamW, e.beamH, 0, 0, Math.round(e.beamW), lt.getHeight(), e.lgunLeft, false);
                }
                continue;
            }
            if (e.type == Enemy.Type.RAGE) {            // its laser (the Laser Guards' pattern turned upright), then the Rage
                if (e.beamH > 0f && g.omenLaserTex != null) {
                    Texture lt = g.omenLaserTex[(int) (e.t * Enemy.OMEN_LASER_FLASH) % g.omenLaserTex.length];
                    int th = lt.getHeight();
                    g.batch.draw(lt, e.beamX + e.beamW, e.beamY, 0f, 0f, e.beamH, e.beamW, 1f, 1f, 90f, 0, 0, Math.round(e.beamH), th, false, false);
                }
                if (g.rageR != null) g.batch.draw(g.rageR, e.x, e.y);
                continue;
            }
            if (e.type == Enemy.Type.HEATHEN) {         // the pillar; a bar that was shot away is painted over in black
                if (g.heathenR == null) continue;
                g.batch.draw(g.heathenR[e.heathenKind], e.x, e.y);
                for (int k = 0; k < Enemy.HEATHEN_BARS; k++) {
                    if (e.heathenBarHp[k] > 0) continue;
                    g.batch.setColor(Color.BLACK);
                    g.batch.draw(g.pix, e.heathenBarX(k), e.heathenBarY(), Enemy.HEATHEN_BAR_W, Enemy.HEATHEN_BAR_H);
                    g.batch.setColor(Color.WHITE);
                }
                continue;
            }
            if (e.type == Enemy.Type.LASER_BATTERY) {  // the battery (flipped like its object), then its beam
                TextureRegion br = e.batBig ? g.batteryBigR : g.batterySmallR;
                if (br != null && !e.batWall) {
                    boolean fx = e.batBig ? e.batCeiling : !e.batRight, fy = e.batCeiling || (!e.batBig && !e.batRight);
                    g.batch.draw(br, fx ? e.x + e.w : e.x, fy ? e.y + e.h : e.y, fx ? -e.w : e.w, fy ? -e.h : e.h);
                }
                if (e.beamOn() && g.omenLaserTex != null) {   // the Laser Guards' beam: the pattern along it, flashing red / blue
                    Texture lt = g.omenLaserTex[(int) (e.t * Enemy.OMEN_LASER_FLASH) % g.omenLaserTex.length];
                    int th = lt.getHeight();
                    if (e.beamW > e.beamH) g.batch.draw(lt, e.beamX, e.beamY, e.beamW, e.beamH, 0, 0, Math.round(e.beamW), th, !e.batRight, false);
                    else g.batch.draw(lt, e.beamX + e.beamW, e.beamY, 0f, 0f, e.beamH, e.beamW, 1f, 1f, 90f, 0, 0, Math.round(e.beamH), th, false, false);
                }
                continue;
            }
            if (e.type == Enemy.Type.EXODUS) {
                TextureRegion[] xf = e.exRed ? g.exodusRedF : g.exodusCyanF;
                if (xf != null) g.batch.draw(xf[e.exodusFrame() % xf.length], e.x, e.y, e.w, e.h);
                continue;
            }
            if (e.type == Enemy.Type.ARTILLERY) {      // the picture points up: turned to point away from its wall
                TextureRegion[] af = e.artRed && g.artilleryRedF != null ? g.artilleryRedF : g.artilleryGreenF;
                TextureRegion ar = af == null ? null : af[(int) (Math.max(0f, e.t) / g.ART_FRAME) % af.length];
                if (ar != null && (e.artFloor || e.artCeil)) g.batch.draw(ar, e.x, e.artCeil ? e.y + e.h : e.y, e.w, e.artCeil ? -e.h : e.h);   // up / hanging down
                else if (ar != null) g.batch.draw(ar, e.x, e.y, e.w / 2f, e.h / 2f, e.w, e.h, 1f, 1f, e.artRight ? 90f : -90f);
                continue;
            }
            if (e.type == Enemy.Type.BLUE_ASTEROID) {
                if (g.blueAsteroidR != null) g.batch.draw(g.blueAsteroidR, e.x, e.y, e.w, e.h);
                continue;
            }
            if (e.type == Enemy.Type.MOAI) {           // faces right on the left wall; mirrored on the right wall
                if (g.moaiF != null) {
                    TextureRegion mr = g.moaiF[e.moaiOpen && g.moaiF.length > 1 ? 1 : 0];
                    g.batch.draw(mr, e.moaiRight ? e.x + e.w : e.x, e.y, e.moaiRight ? -e.w : e.w, e.h);
                }
                continue;
            }
            if (e.type == Enemy.Type.ORB) {
                if (g.orbR != null) g.batch.draw(g.orbR, e.x, e.y, e.w, e.h);
                continue;
            }
            if (e.type == Enemy.Type.BLUE_DOME) {      // stage 2's dome.png: open while it is letting a burst out; mirrored on the right wall
                if (g.domeF == null) continue;
                TextureRegion dr = g.domeF[e.wave != null && e.wave.active() ? 1 : 0];
                if (e.domeRight) g.batch.draw(dr, e.x + e.w, e.y, -e.w, e.h);   // (its back to the wall on the right)
                else g.batch.draw(dr, e.x, e.y, e.w, e.h);
                continue;
            }
            if (e.type == Enemy.Type.GREEN_FLYER) {
                if (g.greenF != null) g.batch.draw(g.greenF[(int) (Math.max(0f, e.t) / Enemy.GREEN_FRAME) % g.greenF.length], e.x, e.y, e.w, e.h);
                continue;
            }
            if (e.type == Enemy.Type.OPTION_EATER) {    // white / red, swapped every OEATER_FLASH s
                if (g.optionEaterF != null) g.batch.draw(g.optionEaterF[e.oeFrame() % g.optionEaterF.length], e.x, e.y, e.w, e.h);
                continue;
            }
            if (e.type == Enemy.Type.GREEN_MINE) {      // 2 frames, swapped every GMINE_FRAME s
                if (g.greenMineF != null) g.batch.draw(g.greenMineF[(int) (Math.max(0f, e.t) / Enemy.GMINE_FRAME) % g.greenMineF.length], e.x, e.y, e.w, e.h);
                continue;
            }
            if (e.type == Enemy.Type.GAMMA) {
                TextureRegion[] gfr = e.gammaRed && g.gammaRedF != null ? g.gammaRedF : g.gammaF;
                if (gfr != null) g.batch.draw(gfr[Enemy.GAMMA_ORDER[(int) (Math.max(0f, e.t) / Enemy.GAMMA_FRAME) % Enemy.GAMMA_ORDER.length] % gfr.length], e.x, e.y, e.w, e.h);
                continue;
            }
            if (e.type == Enemy.Type.UG) {             // cycles its 3 frames
                if (g.ugF != null) g.batch.draw(g.ugF[((int) (Math.max(0f, e.t) / Enemy.UG_FRAME) + (int) e.phase) % g.ugF.length], e.x, e.y, e.w, e.h);
                continue;
            }
            if (e.type == Enemy.Type.CREEPING_DEATH) { // frames back and forth, flickering between its two colours
                if (g.creepF == null) continue;
                int n = g.creepF.length, step = (int) (Math.max(0f, e.t) / Enemy.CREEP_FRAME) % (2 * n - 2);
                boolean second = ((int) (Math.max(0f, e.t) / Enemy.CREEP_FLASH) & 1) == 1;
                g.batch.setColor(e.creepRed ? (second ? g.CREEP_DARK_GREEN : g.CREEP_RED) : (second ? g.CREEP_GREEN : g.CREEP_CYAN));
                g.batch.draw(g.creepF[step < n ? step : 2 * n - 2 - step], e.x, e.y, e.w, e.h);
                g.batch.setColor(Color.WHITE);
                continue;
            }
            if (e.type == Enemy.Type.CHAIN) {          // grows 4, 6, 8 px, centred on its place
                if (g.chainR == null) continue;
                int k = e.t < Enemy.CHAIN_GROW / 2f ? 2 : e.t < Enemy.CHAIN_GROW ? 1 : 0;
                TextureRegion cr = g.chainR[k];
                float cw = cr.getRegionWidth(), ch = cr.getRegionHeight();
                g.batch.draw(cr, e.x + (e.w - cw) / 2f, e.y + (e.h - ch) / 2f, cw, ch);
                continue;
            }
            if (e.type == Enemy.Type.BATTLEAXE) {
                if (g.axeR != null) g.batch.draw(g.axeR, e.x, e.y, e.w, e.h);
                continue;
            }
            if (e.type == Enemy.Type.WHIPLASH) {
                if (g.whipR != null) g.batch.draw(g.whipR, e.x, e.y, e.w, e.h);
                continue;
            }
            if (r == null) {                           // bio turret: red = carries a capsule; mirrored to aim right
                TextureRegion f = (red ? g.bioRedF : g.bioBlueF)[e.aim == 0 ? 1 : 0];
                boolean flipX = e.aim > 0, ceil = e.type == Enemy.Type.TURRET_CEIL;
                g.batch.draw(f, flipX ? e.x + e.w : e.x, ceil ? e.y + e.h : e.y, flipX ? -e.w : e.w, ceil ? -e.h : e.h);
                continue;
            }
            if (e.type == Enemy.Type.FROST || e.type == Enemy.Type.DOME_FLYER || e.type == Enemy.Type.GREEN_FLYER) red = false;   // keeps its own colours (the group still carries a capsule)
            if (e.type == Enemy.Type.RUGAL && red && g.rugalRedF != null) {   // red Rugal: its own picture
                r = g.rugalRedF[Math.min(e.rugalFrame(), g.rugalRedF.length - 1)];
                red = false;
            }
            if (e.type == Enemy.Type.LEGACY) {          // fly-by: always green; hovering: the MSX colour cycle
                if (e.hover) {
                    int k = (int) (g.amoebaClock / g.LEGACY_COLOUR_TIME + e.phase * 3f) % g.LEGACY_COLOURS.length;
                    g.batch.setColor(g.LEGACY_COLOURS[k]);
                } else g.batch.setColor(g.LEGACY_GREEN);
                g.batch.draw(r, e.x, e.y, e.w, e.h);
                g.batch.setColor(Color.WHITE);
                continue;
            }
            if (red) g.batch.setColor(e.type == Enemy.Type.AMOEBA ? g.AMOEBA_RED : g.CARRIER_TINT);
            else if (e.type == Enemy.Type.AMOEBA) {
                g.batch.setColor(g.AMOEBA_COLOURS[(int) (g.amoebaClock / g.AMOEBA_COLOUR_TIME) % g.AMOEBA_COLOURS.length]);
                red = true;                               // (resets the colour after drawing)
            }
            if (e.type == Enemy.Type.WALKER) {
                // mirrored when walking right, upside down on the ceiling, tilted 45 degrees at steps
                // (front up while climbing a step, front down while dropping off one)
                int dir = e.vx < 0f ? -1 : 1;
                float rot = (e.ceiling ? -1 : 1) * dir * e.climb * 45f;
                g.batch.draw(r, e.x, e.y, e.w / 2f, e.h / 2f, e.w, e.h, dir > 0 ? -1f : 1f, e.ceiling ? -1f : 1f, rot);
            } else g.batch.draw(r, e.x, e.y, e.w, e.h);
            if (red) g.batch.setColor(Color.WHITE);
        }
    }

    /**
     * Laser Guard: pointing down on the ceiling, flipped on the floor, turned a quarter (pointing left) on the right-hand
     * wall. While it retreats only the part still outside its wall is drawn (its gun end).
     */
    void drawLaserGuard(Enemy e) {
        Texture t = g.laserGuardTex;
        int sw = t.getWidth(), sh = t.getHeight();
        int vis = Math.max(0, Math.round(sh * (1f - e.retreat)));   // rows still showing, counted from the gun end
        if (vis == 0) return;
        if (e.guardSide == Enemy.GUARD_CEIL)
            g.batch.draw(t, e.x, e.wallLine - vis, sw, vis, 0, sh - vis, sw, vis, false, false);
        else if (e.guardSide == Enemy.GUARD_FLOOR)
            g.batch.draw(t, e.x, e.wallLine, sw, vis, 0, sh - vis, sw, vis, false, true);
        else   // turned clockwise: the gun end (bottom rows) on the left, against the wall on the right
            g.batch.draw(t, e.wallLine - vis, e.y + sw, 0f, 0f, sw, vis, 1f, 1f, -90f, 0, sh - vis, sw, vis, false, false);
    }

    void drawBoss() {
        if (g.kierkeBossNow()) { g.drawKierkeBoss(); return; }
        if (g.boss.dissolve && g.boss.dying) return;        // (breaking up: drawDissolving shows it)
        if (g.boss.appearing) { if (g.gunsAppear != null) g.gunsAppear.draw(g.batch, g.boss.x, g.boss.y); return; }
        drawTentacles();                                // under the body
        if (g.boss.flash > 0f) g.batch.setColor(1f, 0.55f, 0.55f, 1f);
        if (g.boss.dying && (int) (g.boss.dieT * 20f) % 2 == 0) g.batch.setColor(1f, 1f, 1f, 0.5f);
        g.batch.draw(g.curBossR(), g.boss.x, g.boss.y, g.boss.w, g.boss.h);
        if (g.boss.hasCore) {                              // in its hole, with the blocks still left
            g.batch.draw(g.coreR, g.boss.x + g.boss.coreX, g.boss.y + g.boss.coreY, g.boss.coreW, g.boss.coreH);
            g.drawCoreBlocks();
        }
        if (g.boss.hasHatch && g.hatchF != null) {
            TextureRegion f = g.hatchF[Math.min(g.boss.hatchState, g.hatchF.length - 1)];
            g.batch.draw(f, g.boss.x + g.boss.hatchX, g.boss.y + g.boss.hatchY);
        }
        if (g.boss.hasEye && g.eyeF != null) {
            TextureRegion f = g.eyeF[g.boss.eyeFrame()];
            g.batch.draw(f, g.boss.x + g.boss.eyeX, g.boss.y + g.boss.eyeY, f.getRegionWidth(), f.getRegionHeight());
        }
        g.batch.setColor(Color.WHITE);
        if (g.boss.arms != null) for (Hazard arm : g.boss.arms) if (!arm.dead) drawArm(arm);   // over the brain, as in the original
    }

    /** Tetran's tentacles: drawn before (under) the body. */
    void drawTentacles() {
        if (!g.boss.tetran || g.boss.dying) return;
        float rs = Boss.RING_SIZE;
        for (int k = 0; k < Boss.TENTACLES; k++)   // the inner rings, from the centre to the root (under the body)
            for (int j = 0; j < Boss.INNER_RINGS; j++)
                g.batch.draw(g.ringR, Math.round(g.boss.innerX[k][j] - rs / 2f), Math.round(g.boss.innerY[k][j] - rs / 2f), rs, rs);
        for (int k = 0; k < Boss.TENTACLES; k++)
            for (int i = 0; i < g.boss.ringCount; i++)
                g.batch.draw(g.ringR, Math.round(g.boss.ringX[k][i] - rs / 2f), Math.round(g.boss.ringY[k][i] - rs / 2f), rs, rs);
    }

    void drawBullets() {
        for (Bullet b : g.pBullets) {
            switch (b.kind) {
                case LASER:
                    if (b.seg) {   // aimed laser piece: a short bit of beam, turned to its direction, centred on its box
                        float lh = g.laserR.getRegionHeight(), ll = PlayerWeapons.AIM_PIECE_LEN;
                        g.batch.draw(g.laserR, b.x + b.w / 2f - ll / 2f, b.y + b.h / 2f - lh / 2f, ll / 2f, lh / 2f, ll, lh, 1f, 1f,
                                MathUtils.atan2(b.vy, b.vx) * MathUtils.radiansToDegrees);
                    } else if (b.vert) g.batch.draw(g.laserR, b.x + b.w, b.y, 0f, 0f, b.h, b.w, 1f, 1f, 90f);   // turned to point up
                    else g.batch.draw(g.laserR, b.x, b.y, b.w, b.h);
                    break;
                case RIPPLE: g.drawRipple(b); break;
                case MISSILE:   // tilted 45 degrees nose-up while climbing, nose-down while dropping
                    if (b.chasing) {   // HOMING on its way to a target: turned to where it is going, never upside down
                        float mw = g.missR.getRegionWidth(), mh = g.missR.getRegionHeight();
                        g.batch.draw(g.missR, b.x + b.w / 2f - mw / 2f, b.y + b.h / 2f - mh / 2f, mw / 2f, mh / 2f, mw, mh, 1f,
                                b.vx < 0f ? -1f : 1f, MathUtils.atan2(b.vy, b.vx) * MathUtils.radiansToDegrees);
                    } else if (b.vert) {   // vertical stage: turned to where it is going (centred on its little hit box)
                        float mw = g.missR.getRegionWidth(), mh = g.missR.getRegionHeight();
                        g.batch.draw(g.missR, b.x + b.w / 2f - mw / 2f, b.y + b.h / 2f - mh / 2f, mw / 2f, mh / 2f, mw, mh, 1f,
                                b.up ? -1f : 1f, MathUtils.atan2(b.vy, b.vx) * MathUtils.radiansToDegrees);
                    } else if (!b.grounded)  // still flying to the floor / ceiling: nose 45 degrees down / up
                        g.batch.draw(g.missR, b.x, b.y, b.w / 2f, b.h / 2f, b.w, b.h, 1f, b.up ? -1f : 1f, b.up ? 45f : -45f);
                    else if (b.up)       // upward missile: drawn upside down; "climbing" a lower ceiling means going down
                        g.batch.draw(g.missR, b.x, b.y, b.w / 2f, b.h / 2f, b.w, b.h, 1f, -1f, -b.climb * 45f);
                    else if (b.climb == FloorCrawler.LEVEL) g.batch.draw(g.missR, b.x, b.y, b.w, b.h);
                    else g.batch.draw(g.missR, b.x, b.y, b.w / 2f, b.h / 2f, b.w, b.h, 1f, 1f, b.climb * 45f);
                    break;
                default:
                    float sw = g.shotR.getRegionWidth(), sh = g.shotR.getRegionHeight();
                    if (b.vy == 0f && b.vx > 0f) g.batch.draw(g.shotR, b.x, b.y, sw, sh);
                    else g.batch.draw(g.shotR, b.x + b.w / 2f - sw / 2f, b.y + b.h / 2f - sh / 2f, sw / 2f, sh / 2f, sw, sh,
                            1f, 1f, MathUtils.atan2(b.vy, b.vx) * MathUtils.radiansToDegrees);   // centred, turned to its direction
                    break;
            }
        }
        for (Bullet b : g.eBullets) {
            if (b.reflected) g.batch.draw(g.laserR, b.x, b.y, b.w, b.h);   // a ship's laser, bounced back by a big block
            else if (b.kind == Bullet.Kind.LAVA) g.batch.draw(g.lavaF[(int) (b.t * 10f) % g.lavaF.length], b.x, b.y, 8, 8);
            else if (b.guard != null && g.omenLaserTex != null) {   // Laser Guard: the pattern along the beam, flashing
                Texture t = g.omenLaserTex[(int) (b.t * Enemy.OMEN_LASER_FLASH) % g.omenLaserTex.length];
                int th = t.getHeight();
                if (b.beamDir == Enemy.GUARD_WALL) {
                    int srcX = Math.round(b.x - b.beamTail);
                    g.batch.draw(t, b.x, b.y, b.w, b.h, srcX, 0, Math.round(b.w), th, false, false);
                } else {   // turned a quarter to run up / down
                    int srcX = Math.round(b.y - b.beamTail);
                    g.batch.draw(t, b.x + th, b.y, 0f, 0f, b.h, th, 1f, 1f, 90f, srcX, 0, Math.round(b.h), th, false, false);
                }
            } else if (b.omenLaser && g.omenLaserTex != null) {   // the pattern repeated sideways, flashing red / blue
                Texture t = g.omenLaserTex[(int) (b.t * Enemy.OMEN_LASER_FLASH) % g.omenLaserTex.length];
                int srcX = Math.round(b.x - (b.anchorX - t.getWidth() / 2f));   // pattern anchored to the middle
                g.batch.draw(t, b.x, b.y, b.w, b.h, srcX, 0, Math.round(b.w), t.getHeight(), false, b.vy < 0f);
            } else if (b.ring != 0 && g.ring6R != null) {   // Op 4 moai ring, centred on its 12x12 hit box
                if (b.ring == 3) g.batch.setColor(((int) (b.t * g.RING_FLASH) & 1) == 0 ? g.AMOEBA_RED : g.RING_RED_DARK);   // red: flashes red / dark red
                else if (((int) (b.t * g.RING_FLASH) & 1) == 0) g.batch.setColor(g.RING_YELLOW);   // flashes yellow / white
                g.batch.draw(g.ring6R, b.x - 2f, b.y - 2f, 16f, 16f);
                g.batch.setColor(Color.WHITE);
            } else if (b.beam && g.gunHoBulletR != null) {   // Gun Ho's shot: its own picture (mirrored when flying right)
                g.batch.draw(g.gunHoBulletR, b.vx > 0f ? b.x + b.w : b.x, b.y, b.vx > 0f ? -b.w : b.w, b.h);
            } else if (b.beam) {   // (no gun ho bullet.png: a drawn laser, red edges, yellow core)
                g.batch.setColor(g.GUNHO_BEAM_EDGE);
                g.batch.draw(g.pix, b.x, b.y, b.w, b.h);
                g.batch.setColor(g.GUNHO_BEAM_CORE);
                g.batch.draw(g.pix, b.x + 1f, b.y + 1f, b.w - 2f, Math.max(1f, b.h - 2f));
                g.batch.setColor(Color.WHITE);
            } else {   // spins in quarter turns around its centre (whole 90 degree steps keep the pixel art crisp)
                float rot = 90f * ((int) (b.t * g.EBULLET_SPIN) % 4);
                g.batch.draw(g.ebR, b.x, b.y, b.w / 2f, b.h / 2f, b.w, b.h, 1f, 1f, -rot);
            }
        }
    }

    /**
     * The camera is snapped to whole pixels (Math.round(scrollX)). Things that keep a fixed position
     * on screen while the stage scrolls (ships, options) have fractional world X, so drawing them at
     * that X makes them hop back and forth by a pixel against the snapped camera. Snapping their
     * screen offset instead keeps them perfectly still.
     */
    float snapX(float worldX) { return Math.round(g.scrollX) + Math.round(worldX - g.scrollX); }

    float snapY(float worldY) { return Math.round(g.scrollY) + Math.round(worldY - g.scrollY); }

    void drawPlayer(Player p) {
        Color tint = p.index == 1 ? g.P2_TINT : Color.WHITE;   // (player 2's options; its ship has its own red sprites)
        float px = snapX(p.x), py = snapY(p.y);
        boolean blinkPhase = (int) (p.invuln * 20f) % 2 == 0;
        boolean ffieldHit = p.ffieldHitT > 0f;          // the F.FIELD took the hit: it blinks, the ship stays solid
        boolean blink = p.invuln > 0f && !ffieldHit && p.shieldHitT <= 0f && blinkPhase;   // a guard took it: no blink
        if (!blink) {
            boolean p2 = p.index == 1;
            if (g.level.vertical) {   // pointing up, banking left / right while moving sideways; centred on the hit box
                TextureRegion[] vf = p2 ? g.playerVRedF : g.playerVF;
                TextureRegion f = vf[p.tilt == Player.TILT_LEFT ? 0 : p.tilt == Player.TILT_RIGHT ? 2 : 1];
                g.batch.draw(f, px + Math.round(16.5f - f.getRegionWidth() / 2f), py + Player.VART_B);
            } else {
                TextureRegion[] hf = p2 ? g.playerRedF : g.playerF;
                g.batch.draw(hf[Math.min(p.tilt, hf.length - 1)], px, py, 32, 16);
            }
        }
        g.batch.setColor(tint);
        for (int i = 0; i < p.options; i++) {
            // The option's logical box is 12x12 (centre at +6, +6); the 16x10 art is centred on it.
            // On a vertical stage it is turned 90 degrees (10 wide, 16 tall) around that same centre.
            TextureRegion f = g.optionF[(int) (g.stateT * 8f + i) % g.optionF.length];
            float ox = snapX(g.optX(p, i)) - 2f, oy = snapY(g.optY(p, i)) + 1f;
            if (g.level.vertical)
                g.batch.draw(f, ox, oy, 8f, 5f, f.getRegionWidth(), f.getRegionHeight(), 1f, 1f, 90f);
            else
                g.batch.draw(f, ox, oy);
        }
        g.batch.setColor(Color.WHITE);
        if (p.aiming) {   // dotted line showing the aimed direction (DOUBLE's extra shot, or LASER / RIPPLE)
            float ang = p.weapon == Player.W_DOUBLE ? g.weapons.doubleAngle(p) : g.weapons.aimAngle(p.laserDir), cx = px + 16f, cy = py + 8f;
            g.batch.setColor(1f, 0.9f, 0.3f, 0.9f);
            for (int i = 1; i <= 4; i++) {
                g.batch.draw(g.pix, cx + MathUtils.cos(ang) * (10f + i * 7f) - 1f, cy + MathUtils.sin(ang) * (10f + i * 7f) - 1f, 2f, 2f);
            }
            g.batch.setColor(Color.WHITE);
        }
        if (p.shield > 0 && !blink && !(p.shieldHitT > 0f && (int) (p.shieldHitT * 40f) % 2 == 0)) {   // flickers when hit
            float fx = snapX(p.shieldX()), fy = snapY(p.shieldY());
            TextureRegion[] ff = p.shield <= Player.SHIELD_LOW && g.shieldRedF != null ? g.shieldRedF : g.shieldF;   // red: nearly used up
            if (ff != null && g.level.vertical)       // turned a quarter: lying across the top of the nose
                g.batch.draw(ff[(int) (g.stateT * g.SHIELD_FPS) % ff.length], fx + Player.shieldW(), fy, 0f, 0f, Player.SHIELD_W, Player.SHIELD_H, 1f, 1f, 90f);
            else if (ff != null) g.batch.draw(ff[(int) (g.stateT * g.SHIELD_FPS) % ff.length], fx, fy, Player.SHIELD_W, Player.SHIELD_H);
            else { g.batch.setColor(0.4f, 0.9f, 1f, 0.8f); g.batch.draw(g.pix, fx + 2f, fy, 4f, Player.SHIELD_H); g.batch.setColor(Color.WHITE); }
        }
        if ((p.ffield > 0 || ffieldHit) && !(ffieldHit && blinkPhase)) {
            g.batch.setColor(1f, 1f, 1f, 0.35f + 0.1f * Math.max(p.ffield, 1));
            g.batch.draw(g.ffieldR, px - 6f, py - 8f, 40, 32);
            g.batch.setColor(Color.WHITE);
        }
        if (p.invuln > 1f && g.playersInGame() > 1) {   // "1P" / "2P" tag just after (re)entering
            PixelFont.drawShadow(g.batch, g.pix, (p.index + 1) + "P", px, py + 20f, 32, Align.center, PixelFont.SMALL, 1,
                    p.index == 1 ? g.P2_TINT : Color.CYAN);
        }
    }

    /** Debug overlay: the player's collision box as a translucent fill with a solid outline. */
    void drawHitbox(Player p) {
        float x = snapX(p.x) + Player.HIT_OX, y = snapY(p.y) + Player.HIT_OY, w = Player.HIT_W, h = Player.HIT_H;
        g.batch.setColor(1f, 0f, 1f, 0.35f);
        g.batch.draw(g.pix, x, y, w, h);
        g.batch.setColor(1f, 0f, 1f, 1f);
        g.batch.draw(g.pix, x, y, w, 1f);
        g.batch.draw(g.pix, x, y + h - 1f, w, 1f);
        g.batch.draw(g.pix, x, y, 1f, h);
        g.batch.draw(g.pix, x + w - 1f, y, 1f, h);
        g.batch.setColor(Color.WHITE);
    }

    // ---------------------------------------------------------------- HUD
    // The original MSX layout: a 24px strip under the playfield, player 1 on the left half, player 2 on
    // the right. Per player: the 7-slot power meter; ship icon + ships left, stage, and a yellow box
    // with the name of the highlighted power-up; "1P" + 9-digit score. Graphics: sprites/hud.png.

    private static final Color HUD_WHITE = new Color(0.88f, 0.88f, 0.88f, 1f);
    private static final Color BOX_TEXT = new Color(0.08f, 0.08f, 0.05f, 1f);
    /** Tint for HUD pieces that are inactive: the meter of a side nobody plays. */
    private static final Color DIM_TINT = new Color(0.35f, 0.35f, 0.35f, 1f);

    /** Text in the HUD / overlay coordinate system (bottom-left origin, 256 x 192). */
    void text(String s, float x, float y, float w, int align, int style, int scale, Color c) {
        PixelFont.draw(g.batch, g.pix, s, x, y, w, align, style, scale, c);
    }

    /** Overlay text over the playfield, with a shadow. */
    void say(String s, float y, int style, int scale, Color c) {
        PixelFont.drawShadow(g.batch, g.pix, s, 0, y, SW, Align.center, style, scale, c);
    }

    void drawHud() {
        boolean blinkOn = (int) (g.stateT * 2f) % 2 == 0;
        g.batch.setColor(Color.BLACK);
        g.batch.draw(g.pix, 0, 0, SW, HUD_H);               // the strip covers whatever lies below the playfield
        g.batch.setColor(Color.WHITE);
        drawPlayerHud(0, blinkOn);
        drawPlayerHud(1, blinkOn);

        // boss health bar with "hp/max" in it, along the top of the playfield
        if (g.boss != null && !g.boss.dying && !g.boss.entering) {
            float bw = 96f, bh = 7f, bx = SW / 2f - bw / 2f, by = SH - 10f;
            g.batch.setColor(0.25f, 0f, 0f, 0.85f);
            g.batch.draw(g.pix, bx, by, bw, bh);
            g.batch.setColor(0.88f, 0.13f, 0.13f, 1f);
            g.batch.draw(g.pix, bx, by, bw * Math.max(0f, g.boss.hp / g.boss.maxHp), bh);
            g.batch.setColor(Color.WHITE);
            String hp = (int) Math.ceil(g.boss.hp) + "/" + (int) g.boss.maxHp;
            PixelFont.drawShadow(g.batch, g.pix, hp, 0, by + 1f, SW, Align.center, PixelFont.SMALL, 1, HUD_WHITE);
        }

        if (g.game.volumeMsgT > 0f && g.state != GameScreen.State.PAUSE) say(g.game.volumeText(), SH - 22f, PixelFont.SMALL, 1, Color.YELLOW);

        float mid = HUD_H + H / 2f;                      // middle of the playfield
        switch (g.state) {
            case INTRO:                                  // (the banner is drawn by drawIntro)
                break;
            case PAUSE:
                say("PAUSED", mid + 26f, PixelFont.BOLD, 1, HUD_WHITE);
                for (int i = 0; i < g.PAUSE_MENU.length; i++) {   // the selected entry in yellow, between arrows
                    boolean on = i == g.pauseSel;
                    say(on ? "> " + g.PAUSE_MENU[i] + " <" : g.PAUSE_MENU[i], mid + 8f - i * 11f, PixelFont.NORMAL, 1,
                            on ? Color.YELLOW : Color.LIGHT_GRAY);
                }
                say(g.game.volumeText(), mid - 22f, PixelFont.SMALL, 1, Color.YELLOW);
                say("1/2 MUSIC -/+   3/4 SFX -/+", mid - 32f, PixelFont.SMALL, 1, Color.LIGHT_GRAY);
                say("PAD: BACK + D-PAD", mid - 40f, PixelFont.SMALL, 1, Color.LIGHT_GRAY);
                break;
            case CLEAR:
                say("STAGE " + (g.levelIdx + 1) + " CLEAR", mid + 6f, PixelFont.BOLD, 1, Color.GREEN);
                say("BONUS " + g.stageBonus(), mid - 10f, PixelFont.NORMAL, 1, HUD_WHITE);
                break;
            case GAMEOVER:
                say("GAME OVER", mid + 14f, PixelFont.BOLD, 1, Color.RED);
                if (g.stateT > 1f) {
                    for (int i = 0; i < g.GAMEOVER_MENU.length; i++) {   // the selected entry in yellow, between arrows
                        boolean on = i == g.gameOverSel;
                        say(on ? "> " + g.GAMEOVER_MENU[i] + " <" : g.GAMEOVER_MENU[i], mid - 6f - i * 11f, PixelFont.NORMAL, 1,
                                on ? Color.YELLOW : Color.LIGHT_GRAY);
                    }
                }
                break;
            case WIN:
                say("YOU SAVED THE GALAXY", mid + 14f, PixelFont.NORMAL, 1, Color.CYAN);
                say(finalScores(), mid - 2f, PixelFont.NORMAL, 1, HUD_WHITE);
                if (g.stateT > 1f) say("PRESS ENTER / START", mid - 18f, PixelFont.SMALL, 1, Color.YELLOW);
                break;
            default:
                break;
        }
    }

    String finalScores() {
        StringBuilder s = new StringBuilder();
        for (Player p : g.players) {
            if (p == null) continue;
            if (s.length() > 0) s.append("   ");
            s.append(p.index + 1).append("P ").append(String.format("%06d", p.score));
        }
        return s.toString();
    }

    /** One player's half of the HUD strip. */
    void drawPlayerHud(int idx, boolean blinkOn) {
        Player p = g.players[idx];
        int ox = idx * (SW / 2);
        boolean choosingType = p == null && g.choosing[idx] >= 0;
        boolean inPlay = p != null && !p.out;

        // power meter: 7 slots of 14x6, 16px apart
        int[] slots = p != null ? p.slots : g.choosingGuard[idx] != 0 ? Player.slotsFor(Math.max(0, g.choosing[idx]), g.choosingGuard[idx])
                : Player.slotsFor(Math.max(0, g.choosing[idx]));
        for (int i = 0; i < Player.SLOT_COUNT; i++) {
            float x = ox + 9 + 16 * i;
            boolean sel = inPlay && p.meter == i + 1;
            boolean occupied = inPlay && owned(p, slots[i]);           // can't be taken (any further) right now
            if (!inPlay && !choosingType) g.batch.setColor(DIM_TINT);   // nobody flying on this side
            g.batch.draw(g.hudSlotR[(sel ? 1 : 0) + (occupied ? 2 : 0)], x, 17);
            g.batch.setColor(Color.WHITE);
        }

        // ship icon + ships left, stage number, yellow box
        g.batch.draw(g.hudShipR[idx], ox + 8, 8);
        text(String.valueOf(p == null ? 0 : Math.max(0, Math.min(9, p.lives))), ox + 25, 8, 8, Align.left, PixelFont.BOLD, 1, HUD_WHITE);
        g.batch.draw(g.hudER, ox + 40, 8);
        text(String.format("%02d", g.levelIdx + 1), ox + 49, 8, 16, Align.left, PixelFont.BOLD, 1, HUD_WHITE);
        // the box turns red when the highlighted power-up is occupied (that slot can't be taken right now)
        boolean selTaken = inPlay && p.selectedPower() != 0 && owned(p, p.selectedPower());
        g.batch.draw(selTaken ? g.hudBoxOffR : g.hudBoxR, ox + 72, 8);
        String box = "";
        if (choosingType) {
            box = !blinkOn ? "" : g.choosingGuard[idx] != 0 ? Player.POWER_NAMES[g.choosingGuard[idx]] : "TYPE " + (g.choosing[idx] + 1);
        } else if (inPlay) {
            int power = p.selectedPower();
            box = power == 0 ? "" : p.powerLabel(power);
        } else if (g.state != GameScreen.State.GAMEOVER && g.state != GameScreen.State.WIN) {
            if (!Controls.hasInput(idx)) box = "PAD " + (idx + 1);
            else if (blinkOn) box = p == null ? "JOIN" : "RETRY";
        }
        text(box, ox + 77, 9, 42, Align.center, PixelFont.SMALL, 1, BOX_TEXT);

        // "1P" + score
        text((idx + 1) + "P", ox + 9, 0, 16, Align.left, PixelFont.BOLD, 1, HUD_WHITE);
        text(String.format("%09d", p == null ? 0 : Math.min(999999999, p.score)), ox + 41, 0, 72, Align.left, PixelFont.BOLD, 1, HUD_WHITE);
    }

    /** Is this power-up already active (taken as far as it goes)? */
    static boolean owned(Player p, int power) {
        switch (power) {
            case Player.SPEED: return false;                   // always open (cycles back to 0)
            case Player.MISSILE: case Player.TWO_WAY: case Player.HOMING: return p.missile;
            case Player.DOUBLE: return p.weapon == Player.W_DOUBLE;
            case Player.LASER: return p.weapon == Player.W_LASER;
            case Player.RIPPLE: return p.weapon == Player.W_RIPPLE;
            case Player.OPTION: return p.options >= Player.MAX_OPTIONS;
            case Player.FFIELD: return p.ffield >= Player.FFIELD_HITS;   // open again once it has taken a hit
            case Player.SHIELD: return p.shield >= Player.SHIELD_HITS;
            case Player.EXTRA: return p.lives >= Player.MAX_LIVES;
            default: return false;
        }
    }
}
