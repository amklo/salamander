package com.example.salamander;

import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.example.salamander.Controls.Action;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * One playable stage for one or two players (co-op): scrolling, spawning, collisions,
 * power-up meters, boss and HUD.
 *
 * Co-op rules: each player has their own ships, score and power-up meter. A player who is shot down
 * while the other is still flying re-enters on the spot after a short delay; when nobody is left
 * flying the stage rewinds to the last checkpoint. Player 2 (or a player with no ships left) can
 * (re)join at any time by pressing fire.
 */
public class GameScreen extends ScreenAdapter {
    private static final int W = SalamanderGame.W, H = SalamanderGame.H;
    private static final float RESPAWN_DELAY = 1.8f;
    /** Player 2's ship and options are tinted so the two ships can be told apart. */
    private static final Color P2_TINT = new Color(1f, 0.7f, 0.4f, 1f);

    private enum State { INTRO, PLAY, PAUSE, DEAD, CLEAR, GAMEOVER, WIN }

    private static class Capsule { float x, y, y0, t; boolean dead() { return x < -9999f; } }
    private static class Explosion { float x, y, t, scale; }

    final SalamanderGame game;
    final Assets a;
    private final SpriteBatch batch;
    private final OrthographicCamera cam = new OrthographicCamera();
    private final OrthographicCamera hud = new OrthographicCamera(W, H);
    private final FitViewport vp;

    final int levelIdx;
    final Level level;
    /** [0] = player 1, [1] = player 2; null = has not joined. */
    final Player[] players;
    /** Camera bottom-left in world coordinates; scrollT = distance travelled along the level's camera path. */
    float scrollX, scrollY, scrollT, scrollSpeed;
    private final float[] camTmp = new float[2];
    final ArrayList<Bullet> eBullets = new ArrayList<>();

    private State state = State.INTRO;
    private float stateT, checkpointT, bannerT;
    private int nextSpawn;
    private Boss boss;
    private boolean bossSpawned;
    /** F1 / H toggles the hitbox overlay (static so it survives stage changes). */
    private static boolean debugHitbox;
    /** D on the title screen: every stage (and every newly joined ship) starts with the debug loadout. */
    static boolean debugLoadout;
    private final ArrayList<Bullet> pBullets = new ArrayList<>();
    private final ArrayList<Enemy> enemies = new ArrayList<>();
    private final ArrayList<Hazard> hazards = new ArrayList<>();
    private final ArrayList<Capsule> caps = new ArrayList<>();
    private final ArrayList<Explosion> fx = new ArrayList<>();
    private final HashMap<Integer, Enemy.Group> groups = new HashMap<>();

    // art
    private final Texture bgTex, starTex;
    private final TextureRegion[] playerF, optionF, fanF, rushF, walkF, capF, boomF, lavaF;
    private final TextureRegion turretR, rockR, shotR, laserR, missR, ebR, shieldR, pix, headR, shaftR, bossR;
    // map stages: pre-rendered terrain strips, brick maze picture, asteroid sprites
    private Texture[] artTex;
    private Texture brickTex;
    private final java.util.HashMap<String, PixelMask> masks = new java.util.HashMap<>();
    private PixelMask bossMask;
    private static final Color[] STAR_TINT = {new Color(0.7f, 0.8f, 1f, 1f), new Color(1f, 0.6f, 0.3f, 1f),
            new Color(0.6f, 1f, 0.9f, 1f)};

    /** @param players the joined players (length 2, null entries = not joined); lives and score carry over */
    public GameScreen(SalamanderGame game, int levelIdx, Player[] players) {
        this.game = game;
        this.a = game.assets;
        this.batch = game.batch;
        this.levelIdx = levelIdx;
        this.level = Level.build(levelIdx);
        this.players = players;
        for (Player p : players) {
            if (p == null) continue;
            p.resetPowerups();                       // every stage starts without upgrades...
            if (debugLoadout) p.applyDebugLoadout(); // ...unless the debug loadout is on
        }
        vp = new FitViewport(W, H, cam);
        hud.position.set(W / 2f, H / 2f, 0);
        hud.update();

        bgTex = a.tex(level.background != null ? level.background : "bg0");
        starTex = a.tex("stars");
        bgTex.setWrap(Texture.TextureWrap.Repeat, Texture.TextureWrap.Repeat);
        starTex.setWrap(Texture.TextureWrap.Repeat, Texture.TextureWrap.Repeat);
        playerF = a.strip("player", 32);
        optionF = a.strip("option", 12);
        fanF = a.strip("fan", 16);
        rushF = a.strip("rusher", 18);
        walkF = a.strip("walker", 16);
        capF = a.strip("capsule", 12);
        boomF = a.strip("explosion", 24);
        lavaF = a.strip("lava", 8);
        turretR = a.one("turret");
        rockR = a.one("rock");
        shotR = a.one("shot");
        laserR = a.one("laser");
        missR = a.one("missile");
        ebR = a.one("ebullet");
        shieldR = a.one("shield");
        pix = a.one("pixel");
        headR = a.one("crusher_head");
        shaftR = a.one("crusher_shaft");
        bossR = a.one("boss" + levelIdx);
        // stage art from the Tiled map: image layers, terrain tileset, brick pattern, hazard pictures
        artTex = new Texture[level.art.size()];
        for (int i = 0; i < artTex.length; i++) artTex[i] = a.texFile(level.art.get(i).image);
        brickTex = a.texFile("sprites/brick_pattern.png");
        brickTex.setWrap(Texture.TextureWrap.Repeat, Texture.TextureWrap.Repeat);
        for (Level.Tooth t : level.teeth) {
            if (!masks.containsKey(t.image)) masks.put(t.image, PixelMask.load(t.image));
        }
        bossMask = PixelMask.load("sprites/boss" + levelIdx + ".png");

        resetWorld(0f);
        checkpointT = 0f;
    }

    // =============================================================== players

    static boolean alive(Player p) { return p != null && p.alive(); }

    boolean anyAlive() {
        for (Player p : players) if (alive(p)) return true;
        return false;
    }

    /** Nearest player still flying (enemies aim at / home in on this one), or null. */
    Player target(float x, float y) {
        Player best = null;
        float bestD = Float.MAX_VALUE;
        for (Player p : players) {
            if (!alive(p)) continue;
            float dx = p.x + 16f - x, dy = p.y + 8f - y, d = dx * dx + dy * dy;
            if (d < bestD) { bestD = d; best = p; }
        }
        return best;
    }

    private int playersInGame() {
        int n = 0;
        for (Player p : players) if (p != null && !p.out) n++;
        return n;
    }

    /** Puts a ship at the left of the screen (scroll position SX), invulnerable for a moment. */
    private void placePlayer(Player p, float sx) {
        p.dead = false;
        p.respawnT = 0f;
        p.aiming = false;
        p.x = scrollX + 40f;
        float off = playersInGame() > 1 ? (p.index == 0 ? 14f : -14f) : 0f;   // P1 above, P2 below
        p.y = openSpot(p.x, scrollY + H / 2f - Player.H / 2f + off);
        p.invuln = 2f;
        p.ghostT = p.invuln;   // ...and the walls can't hurt it either while it blinks
        p.fireCd = 0f; p.missCd = 0f;
        p.fillTrail(40f, p.y - scrollY);
    }

    /** Ship-sized free spot closest to height y at x (searched up and down within the screen). */
    private float openSpot(float x, float y) {
        float lo = scrollY + 2f, hi = scrollY + H - Player.H - 2f;
        for (float d = 0f; d < H; d += 4f) {
            for (int s = -1; s <= 1; s += 2) {
                float yy = MathUtils.clamp(y + s * d, lo, hi);
                if (!level.hits(x + 4f, yy, Player.W - 8f, Player.H)) return yy;
            }
        }
        return MathUtils.clamp(y, lo, hi);
    }

    /** Mid-game joiners pick a ship type first: -1 = not choosing, else the type under the cursor. */
    private final int[] choosing = {-1, -1};

    /**
     * Player 2 joins by pressing fire, picks a ship type (left / right, fire to launch) and enters;
     * a player with no ships left continues with the same ship type by pressing fire.
     */
    private void checkJoins() {
        for (int i = 0; i < players.length; i++) {
            Player p = players[i];
            if (p == null) {
                if (choosing[i] < 0) {
                    if (Controls.pressed(i, Action.FIRE)) { choosing[i] = 0; a.play("pickup", 0.4f); }
                    continue;
                }
                if (Controls.pressed(i, Action.LEFT)) { choosing[i] = (choosing[i] + Player.SHIP_TYPES - 1) % Player.SHIP_TYPES; a.play("hit", 0.2f); }
                if (Controls.pressed(i, Action.RIGHT)) { choosing[i] = (choosing[i] + 1) % Player.SHIP_TYPES; a.play("hit", 0.2f); }
                if (!Controls.pressed(i, Action.FIRE)) continue;
                p = new Player(i, choosing[i]);
                choosing[i] = -1;
                if (debugLoadout) p.applyDebugLoadout();
                players[i] = p;
            } else if (p.out && Controls.pressed(i, Action.FIRE)) {
                p.out = false;
                p.lives = Player.START_LIVES;
            } else {
                continue;
            }
            placePlayer(p, scrollX);
            a.play("power", 0.4f);
        }
    }

    // =============================================================== setup / reset

    private void resetWorld(float t) {
        scrollT = t;
        level.camAt(t, camTmp);
        scrollX = camTmp[0];
        scrollY = camTmp[1];
        scrollSpeed = 0f;
        float sx = scrollX;
        pBullets.clear(); eBullets.clear(); enemies.clear(); caps.clear(); groups.clear();
        boss = null;
        bossSpawned = false;
        buildHazards();
        nextSpawn = 0;
        while (nextSpawn < level.spawns.size() && level.spawns.get(nextSpawn).trigger < sx + W + 40f) nextSpawn++;
        for (Player p : players) {
            if (p == null || p.out) continue;
            if (p.lives < 0) p.out = true;           // was shot down on its last ship
            else placePlayer(p, sx);
        }
    }

    private void buildHazards() {
        hazards.clear();
        for (float[] r : level.rocks) {              // stalactites: hang until a ship flies underneath
            Hazard h = new Hazard(Hazard.Type.ROCK);
            h.x = r[0]; h.y = r[1]; h.w = 16; h.h = 16; h.hp = 2;
            hazards.add(h);
        }
        for (float[] v : level.volcanoes) {
            Hazard h = new Hazard(Hazard.Type.VOLCANO);
            h.x = v[0]; h.y = v[1];
            h.timer = MathUtils.random(0.5f, 2.5f);
            hazards.add(h);
        }
        for (Level.Asteroid r : level.asteroids) {   // floating rocks you can shoot
            Hazard h = new Hazard(Hazard.Type.ASTEROID);
            h.x = r.x; h.y = r.y; h.w = r.w; h.h = r.h;
            h.image = r.image;
            h.hp = r.w > 30f ? 3 : 2;
            hazards.add(h);
        }
        for (Level.Tooth t : level.teeth) {          // teeth that jut out of the floor / ceiling
            Hazard h = new Hazard(Hazard.Type.FANG);
            h.x = t.x; h.baseY = t.y; h.w = t.w; h.h = t.h; h.fromCeil = t.ceiling;
            h.delay = t.delay; h.period = t.period;
            h.image = t.image; h.mask = masks.get(t.image);
            h.y = h.baseY + (h.fromCeil ? 1f : -1f) * (h.h + Hazard.FANG_TUCK);   // start hidden
            hazards.add(h);
        }
        for (Level.Crusher c : level.crushers) {
            Hazard h = new Hazard(Hazard.Type.CRUSHER);
            h.x = c.x; h.w = 32; h.fromCeil = c.ceiling; h.baseY = c.baseY;
            h.phase = c.phase; h.minLen = c.minLen; h.maxLen = c.maxLen; h.speed = c.speed;
            h.updateCrusher();
            hazards.add(h);
        }
    }

    @Override
    public void resize(int w, int h) { vp.update(w, h, false); }

    /** Music per stage (index = levelIdx); null = no music for that stage. */
    /** Stage music and boss music come from the map properties "music" and "bossMusic". */
    private void playStageMusic() {
        if (level.music != null && !level.music.isEmpty()) game.bgm.play(level.music);
        else game.bgm.stop();
    }

    @Override
    public void show() { playStageMusic(); }

    @Override
    public void hide() { game.bgm.stop(); }

    @Override
    public void pause() { game.bgm.pause(); }      // window minimised / lost focus

    @Override
    public void resume() { if (state != State.PAUSE) game.bgm.resume(); }

    // =============================================================== main loop

    @Override
    public void render(float dt) {
        dt = Math.min(dt, 1f / 30f);
        stateT += dt;
        // F1 (fn+F1 on a Mac keyboard) or H toggles the hitbox overlay
        if (Controls.pressed(Action.DEBUG)) debugHitbox = !debugHitbox;
        switch (state) {
            case INTRO:
                checkJoins();
                if (stateT > 2.2f) setState(State.PLAY);
                break;
            case PLAY:
                if (Controls.pressed(Action.PAUSE)) {
                    setState(State.PAUSE);
                } else {
                    checkJoins();
                    updatePlay(dt);
                }
                break;
            case PAUSE:
                if (Controls.pressed(Action.PAUSE)) {
                    state = State.PLAY;
                    game.bgm.resume();
                }
                break;
            case DEAD:   // every ship is down: rewind to the checkpoint, or game over
                updateFx(dt);
                if (stateT > RESPAWN_DELAY) {
                    for (Player p : players) if (p != null && p.lives < 0) p.out = true;
                    if (playersInGame() > 0) {
                        resetWorld(checkpointT);
                        playStageMusic();             // back from the boss fight: stage music again
                        setState(State.PLAY);
                    } else {
                        submitScores();
                        setState(State.GAMEOVER);
                    }
                }
                break;
            case CLEAR:
                updateFx(dt);
                for (Player p : players) if (alive(p)) p.x += 220f * dt;
                if (stateT > 3.6f) {
                    if (levelIdx < 2) {
                        game.setScreen(new GameScreen(game, levelIdx + 1, players));
                    } else {
                        submitScores();
                        setState(State.WIN);
                    }
                }
                break;
            case GAMEOVER:
            case WIN:
                updateFx(dt);
                if (stateT > 1f && Controls.pressed(Action.CONFIRM)) game.setScreen(new TitleScreen(game));
                break;
        }
        draw();
    }

    private void submitScores() {
        for (Player p : players) if (p != null) game.submitScore(p.score);
    }

    private void setState(State s) {
        if (s == State.PAUSE) game.bgm.pause();
        if (s == State.CLEAR || s == State.GAMEOVER || s == State.WIN) game.bgm.stop();
        state = s;
        stateT = 0f;
    }

    private void updateFx(float dt) {
        for (Explosion e : fx) e.t += dt;
        fx.removeIf(e -> e.t * 14f >= 5f);
    }

    // =============================================================== gameplay update

    private void updatePlay(float dt) {
        // --- scrolling: speed depends on the terrain section; stops at the boss arena
        // the camera follows the level's path (stage 1 also scrolls up/down); stops at the boss arena
        boolean arena = scrollT >= level.stopT;
        float target = arena ? 0f : level.baseSpeed * level.speedAtT(scrollT);
        scrollSpeed += (target - scrollSpeed) * Math.min(1f, dt * 2.5f);
        scrollT = Math.min(scrollT + scrollSpeed * dt, level.stopT);
        float oldX = scrollX, oldY = scrollY;
        level.camAt(scrollT, camTmp);
        scrollX = camTmp[0];
        scrollY = camTmp[1];
        float dS = scrollX - oldX, dSy = scrollY - oldY;
        for (float cp : level.checkpoints) if (scrollT >= cp) checkpointT = Math.max(checkpointT, cp);
        if (scrollT >= level.stopT && !bossSpawned) spawnBoss();

        // --- spawning
        while (nextSpawn < level.spawns.size() && level.spawns.get(nextSpawn).trigger <= scrollX + W + 40f) {
            spawnEnemy(level.spawns.get(nextSpawn++));
        }

        for (Player p : players) {
            if (p == null || p.out) continue;
            if (p.dead) updateRespawn(p, dt);
            else updatePlayer(p, dt, dS, dSy);
        }

        // --- world objects
        for (Bullet b : pBullets) {
            b.update(dt, level);
            if (b.x > scrollX + W + 60f || b.x < scrollX - 60f || b.y < scrollY - 40f || b.y > scrollY + H + 40f) b.dead = true;
        }
        for (Bullet b : eBullets) {
            b.update(dt, level);
            if (b.x > scrollX + W + 80f || b.x < scrollX - 60f || b.y < scrollY - 30f || b.y > scrollY + H + 60f) b.dead = true;
        }
        for (Enemy e : enemies) {
            e.update(this, dt);
            if (e.x + e.w < scrollX - 40f || e.x > scrollX + W + 300f || e.y < scrollY - 60f || e.y > scrollY + H + 60f) {
                e.dead = true;
                if (e.group != null) e.group.lost = true;
            }
        }
        for (Hazard h : hazards) {
            if (h.dead) continue;
            if (h.x < scrollX + W + 80f && h.x + 40f > scrollX - 80f) h.update(this, dt);
            else if (h.type == Hazard.Type.CRUSHER) h.t += dt;
        }
        for (Capsule c : caps) {
            c.t += dt;
            c.x -= 28f * dt;
            c.y = c.y0 + MathUtils.sin(c.t * 3f) * 6f;
        }
        caps.removeIf(c -> c.x < scrollX - 24f);
        if (boss != null) boss.update(this, dt);
        regrowBricks(dt);
        updateFx(dt);
        if (bannerT > 0f) bannerT -= dt;

        collide();

        pBullets.removeIf(b -> b.dead);
        eBullets.removeIf(b -> b.dead);
        enemies.removeIf(e -> e.dead);
        hazards.removeIf(h -> h.dead);
    }

    /**
     * Brick maze: every brick that was shot away grows back after Level.BRICK_REGROW_TIME seconds.
     * A brick waits while a ship is inside its spot, so it never grows back on top of a player.
     */
    private void regrowBricks(float dt) {
        if (level.bricks == null) return;
        float s = level.cell;
        for (int i = 0; i < level.bricks.length; i++) {
            if (level.bricks[i] || level.brickRegrow[i] <= 0f) continue;
            level.brickRegrow[i] -= dt;
            if (level.brickRegrow[i] > 0f) continue;
            float bx = (i % level.gCols) * s, by = (i / level.gCols) * s;
            boolean occupied = false;
            for (Player p : players) {
                if (alive(p) && overlap(p.x, p.y, Player.W, Player.H, bx - 2f, by - 2f, s + 4f, s + 4f)) occupied = true;
            }
            if (occupied) level.brickRegrow[i] = 0.1f;   // try again in a moment
            else level.bricks[i] = true;
        }
    }

    /** Co-op: a downed player re-enters on the spot while the other keeps flying. */
    private void updateRespawn(Player p, float dt) {
        if (p.respawnT <= 0f) return;
        p.respawnT -= dt;
        if (p.respawnT <= 0f) {
            if (p.lives < 0) p.out = true;          // no ships left: press fire to continue
            else placePlayer(p, scrollX);
        }
    }

    private void updatePlayer(Player p, float dt, float dS, float dSy) {
        int id = p.index;
        float sp = 125f + 34f * p.speedLvl;
        float dx = 0f, dy = 0f;
        if (Controls.held(id, Action.LEFT)) dx -= 1f;
        if (Controls.held(id, Action.RIGHT)) dx += 1f;
        if (Controls.held(id, Action.UP)) dy += 1f;
        if (Controls.held(id, Action.DOWN)) dy -= 1f;
        // DOUBLE aiming: hold AIM (Enter / pad R1) and the direction sets the extra shot's angle;
        // the ship holds its position while aiming
        p.aiming = p.weapon == 1 && Controls.held(id, Action.AIM);
        if (p.aiming) {
            if (dx != 0f || dy != 0f) {
                p.doubleDir = Math.round(MathUtils.atan2(dy, dx) / (MathUtils.PI / 4f)) & 7;
            }
            dx = 0f;
            dy = 0f;
        }
        if (dx != 0f && dy != 0f) { dx *= 0.7071f; dy *= 0.7071f; }
        p.x += dx * sp * dt + dS;
        p.y += dy * sp * dt + dSy;
        p.x = MathUtils.clamp(p.x, scrollX + 6f, scrollX + W - Player.W - 6f);
        p.y = MathUtils.clamp(p.y, scrollY + 2f, scrollY + H - Player.H - 2f);
        p.tilt = dy > 0f ? 1 : (dy < 0f ? 2 : 0);

        p.invuln = Math.max(0f, p.invuln - dt);
        p.ghostT = Math.max(0f, p.ghostT - dt);
        p.shieldHitT = Math.max(0f, p.shieldHitT - dt);
        p.fireCd -= dt;
        p.missCd -= dt;

        // Options follow the ship's path. Only record when the ship actually moved on screen,
        // so the options stay where they are while the ship stands still.
        p.trailAcc += dt;
        while (p.trailAcc >= 1f / 60f) {
            float sx = p.x - scrollX, sy = p.y - scrollY;   // screen-relative, so options ride along with the camera
            if (Math.abs(sx - p.trailX(0)) > 0.25f || Math.abs(sy - p.trailY(0)) > 0.25f) p.record(sx, sy);
            p.trailAcc -= 1f / 60f;
        }

        steerLasers(p);
        boolean fire = Controls.held(id, Action.FIRE);
        if (fire && p.fireCd <= 0f && shoot(p)) p.fireCd = FIRE_INTERVAL;
        if (fire && p.missile && p.missCd <= 0f && launchMissiles(p)) p.missCd = FIRE_INTERVAL;
        if (Controls.pressed(id, Action.POWER)) activateMeter(p);
    }

    float optX(Player p, int i) { return scrollX + p.trailX(10 * (i + 1)) + 10f; }
    float optY(Player p, int i) { return scrollY + p.trailY(10 * (i + 1)) + 2f; }

    // Shots are fired from the "nose": the front tip of the ship art (x 7..23 of the 32px frame,
    // centre line at y 8) or the right edge of an option.
    private static final float NOSE_X = 24f, NOSE_Y = 8f;

    /** Fastest a ship or option may fire: 8 shots per second, as long as it is under its on-screen limit. */
    public static final float FIRE_INTERVAL = 1f / 8f;
    /** On-screen limits per gun (the ship and each option count separately). With DOUBLE the forward
     *  and the angled shots each get their own, smaller limit. */
    public static final int MAX_SHOTS = 4, MAX_DOUBLE_SHOTS = 2, MAX_LASERS = 1, MAX_RIPPLES = 2, MAX_MISSILES = 2, MAX_TWO_WAY = 1;

    /** Fires every gun (ship + options) that is under its limit. Returns true if anything was fired. */
    private boolean shoot(Player p) {
        boolean fired = fireGun(p, 0, p.x + NOSE_X, p.y + NOSE_Y);
        for (int i = 0; i < p.options; i++) fired |= fireGun(p, i + 1, optX(p, i) + 12f, optY(p, i) + 6f);
        if (fired) a.play("shoot", 0.15f);
        return fired;
    }

    /** DOUBLE aimed straight forward works exactly like the normal shot (one shot, limit MAX_SHOTS). */
    private static boolean doubleActive(Player p) { return p.weapon == 1 && p.doubleDir != 0; }

    /** One gun: fires whichever of its shots are under their on-screen limit. */
    private boolean fireGun(Player p, int src, float nx, float ny) {
        if (p.weapon == 2) {
            if (onScreen(p, src, Bullet.Kind.LASER, false) >= MAX_LASERS) return false;
            fireLaser(p, src, nx, ny);
            return true;
        }
        if (p.weapon == Player.W_RIPPLE) {
            if (onScreen(p, src, Bullet.Kind.RIPPLE, false) >= MAX_RIPPLES) return false;
            fireRipple(p, src, nx, ny);
            return true;
        }
        boolean dbl = doubleActive(p);
        int max = dbl ? MAX_DOUBLE_SHOTS : MAX_SHOTS;
        boolean fired = false;
        if (onScreen(p, src, Bullet.Kind.SHOT, false) < max) { fireShot(p, src, nx, ny); fired = true; }
        if (dbl && onScreen(p, src, Bullet.Kind.SHOT, true) < MAX_DOUBLE_SHOTS) { fireAngled(p, src, nx, ny); fired = true; }
        return fired;
    }

    private boolean launchMissiles(Player p) {
        boolean fired = launchFrom(p, 0, p.x + 14f, p.y + 2f, p.y + Player.H - 8f);
        for (int i = 0; i < p.options; i++) fired |= launchFrom(p, i + 1, optX(p, i) + 2f, optY(p, i), optY(p, i) + 6f);
        return fired;
    }

    /**
     * One gun's missiles. Normal: one downward missile, up to MAX_MISSILES on screen. 2-WAY: one down
     * and one up, each direction with its own limit of MAX_TWO_WAY. yDown / yUp = launch heights.
     */
    private boolean launchFrom(Player p, int src, float x, float yDown, float yUp) {
        if (!p.twoWay()) {
            if (onScreen(p, src, Bullet.Kind.MISSILE, false) >= MAX_MISSILES) return false;
            launchMissile(p, src, x, yDown, false);
            return true;
        }
        boolean fired = false;
        if (onScreen(p, src, Bullet.Kind.MISSILE, false) < MAX_TWO_WAY) { launchMissile(p, src, x, yDown, false); fired = true; }
        if (onScreen(p, src, Bullet.Kind.MISSILE, true) < MAX_TWO_WAY) { launchMissile(p, src, x, yUp, true); fired = true; }
        return fired;
    }

    /** Lasers stay level with the gun that fired them: when the ship or option moves up or down, so does its beam. */
    private void steerLasers(Player p) {
        for (int i = 0, k = pBullets.size(); i < k; i++) {
            Bullet b = pBullets.get(i);
            if (b.dead || b.owner != p || b.kind != Bullet.Kind.LASER) continue;
            if (b.src == 0) b.y = p.y + NOSE_Y - 2f;
            else if (b.src <= p.options) b.y = optY(p, b.src - 1) + 6f - 2f;
        }
    }

    /**
     * How many of this gun's bullets of this kind are still flying. alt = count the second stream
     * instead: DOUBLE's angled shots, or 2-WAY's upward missiles.
     */
    private int onScreen(Player p, int src, Bullet.Kind kind, boolean alt) {
        int n = 0;
        for (int i = 0, k = pBullets.size(); i < k; i++) {
            Bullet b = pBullets.get(i);
            if (!b.dead && b.owner == p && b.src == src && b.kind == kind && (b.angled || b.up) == alt) n++;
        }
        return n;
    }

    // (nx, ny) = the muzzle: shots start with their tip there, so they come out of the ship, not ahead of it.
    private void fireLaser(Player p, int src, float nx, float ny) {
        Bullet b = new Bullet(Bullet.Kind.LASER, nx - Bullet.LASER_START_LEN, ny - 2f, Bullet.LASER_START_LEN, 4);
        b.vx = 540f;
        b.owner = p; b.src = src;
        pBullets.add(b);
    }

    private void fireRipple(Player p, int src, float nx, float ny) {
        Bullet b = new Bullet(Bullet.Kind.RIPPLE, nx - Bullet.RIPPLE_START_W, ny - Bullet.RIPPLE_START_H / 2f,
                Bullet.RIPPLE_START_W, Bullet.RIPPLE_START_H);
        b.vx = Bullet.RIPPLE_SPEED;
        b.owner = p; b.src = src;
        pBullets.add(b);
    }

    private void fireShot(Player p, int src, float nx, float ny) {
        Bullet b = new Bullet(Bullet.Kind.SHOT, nx - 12f, ny - 2f, 12, 4);
        b.vx = 800f;
        b.owner = p; b.src = src;
        pBullets.add(b);
    }

    /** DOUBLE's extra shot, in the direction chosen with AIM. */
    private void fireAngled(Player p, int src, float nx, float ny) {
        float ang = p.doubleDir * MathUtils.PI / 4f;
        Bullet d = new Bullet(Bullet.Kind.SHOT, nx - 8f, ny - 4f, 8, 8);
        d.vx = MathUtils.cos(ang) * 792f;
        d.vy = MathUtils.sin(ang) * 792f;
        d.owner = p; d.src = src; d.angled = true;
        pBullets.add(d);
    }

    private void launchMissile(Player p, int src, float x, float y, boolean up) {
        Bullet m = new Bullet(Bullet.Kind.MISSILE, x, y, 8, 6);
        m.vx = 110f; m.vy = up ? 130f : -130f; m.dmg = 3;
        m.up = up;
        m.owner = p; m.src = src;
        pBullets.add(m);
    }

    private void activateMeter(Player p) {
        boolean ok = false;
        switch (p.selectedPower()) {
            case Player.SPEED: if (p.speedLvl < 5) { p.speedLvl++; ok = true; } break;
            case Player.MISSILE: case Player.TWO_WAY: if (!p.missile) { p.missile = true; ok = true; } break;
            case Player.DOUBLE: if (p.weapon != 1) { p.weapon = 1; ok = true; } break;
            case Player.LASER: if (p.weapon != 2) { p.weapon = 2; ok = true; } break;
            case Player.RIPPLE: if (p.weapon != Player.W_RIPPLE) { p.weapon = Player.W_RIPPLE; ok = true; } break;
            case Player.OPTION: if (p.options < Player.MAX_OPTIONS) { p.options++; ok = true; } break;
            case Player.SHIELD: if (p.shield <= 0) { p.shield = 5; ok = true; } break;
            default: break;
        }
        if (ok) {
            p.meter = 0;
            a.play("power", 0.4f);
        }
    }

    // =============================================================== spawning

    private void spawnEnemy(Level.Spawn s) {
        Enemy.Group grp = groups.get(s.group);
        if (grp == null) {
            grp = new Enemy.Group();
            grp.size = s.groupSize;
            grp.carrier = s.carrier;
            groups.put(s.group, grp);
        }
        Enemy e;
        switch (s.type) {
            case FAN:
                e = new Enemy(Enemy.Type.FAN, scrollX + W + 24f, s.y, levelIdx);
                e.phase = s.phase;
                break;
            case RUSHER:
                e = new Enemy(Enemy.Type.RUSHER, scrollX + W + 24f, s.y, levelIdx);
                break;
            case WALKER: {
                float x = s.behind ? scrollX - 20f : s.x;   // runners from behind enter at the left edge
                if (s.ceiling) {
                    e = new Enemy(Enemy.Type.WALKER, x, level.ceilBottom(x + 8f, s.y) - 16f, levelIdx);
                    e.ceiling = true;
                } else {
                    e = new Enemy(Enemy.Type.WALKER, x, level.floorTop(x + 8f, s.y), levelIdx);
                }
                if (s.behind) e.vx = 80f;                     // faster than the scrolling, so they catch up
                break;
            }
            case TURRET_CEIL:
                e = new Enemy(Enemy.Type.TURRET_CEIL, s.x, level.ceilBottom(s.x + 8f, s.y) - 16f, levelIdx);
                break;
            default:
                e = new Enemy(Enemy.Type.TURRET_FLOOR, s.x, level.floorTop(s.x + 8f, s.y), levelIdx);
                break;
        }
        e.group = grp;
        enemies.add(e);
    }

    private void spawnBoss() {
        bossSpawned = true;
        int[] hp = {70, 100, 140};
        boss = new Boss(levelIdx, scrollX + W + 20f, hp[levelIdx], bossR.getRegionWidth(), bossR.getRegionHeight());
        boss.mask = bossMask;
        bannerT = 2.6f;
        a.play("power", 0.3f);
        if (level.bossMusic != null && !level.bossMusic.isEmpty()) game.bgm.play(level.bossMusic);
    }

    void bossDefeated() {
        if (state != State.PLAY) return;
        for (Player p : players) if (p != null && !p.out) p.score += stageBonus();
        eBullets.clear();
        setState(State.CLEAR);
    }

    private int stageBonus() { return 2000 + 1000 * (levelIdx + 1); }

    // =============================================================== shooting helpers (used by enemies/bosses)

    /** Fires at the nearest player (straight left if nobody is flying). */
    void aimedShot(float cx, float cy, float speed, float offset) {
        Player p = target(cx, cy);
        float ang = p == null ? MathUtils.PI : MathUtils.atan2(p.y + 8f - cy, p.x + 16f - cx);
        enemyBullet(cx, cy, ang + offset, speed);
    }

    void enemyBullet(float cx, float cy, float ang, float speed) {
        Bullet b = new Bullet(Bullet.Kind.ENEMY, cx - 3f, cy - 3f, 6, 6);
        b.vx = MathUtils.cos(ang) * speed;
        b.vy = MathUtils.sin(ang) * speed;
        eBullets.add(b);
    }

    void boom(float cx, float cy, float scale) {
        Explosion e = new Explosion();
        e.x = cx; e.y = cy; e.scale = scale;
        fx.add(e);
    }

    /** Small explosion where a shot or laser hit something that survived it (missiles already burst on their own). */
    private void hitSpark(Bullet b, float tx, float ty, float tw, float th) {
        if (b.kind == Bullet.Kind.MISSILE) return;
        float x = b.kind == Bullet.Kind.LASER ? Math.max(b.x, tx)                     // where the beam enters it
                : MathUtils.clamp(b.x + b.w, tx, tx + tw);                         // the shot's tip
        float y = MathUtils.clamp(b.y + b.h / 2f, ty, ty + th);
        boom(x, y, HIT_SPARK_SCALE);
    }

    private static final float HIT_SPARK_SCALE = 0.4f;

    /** Ripple laser: an oval ring of 2x2 dots, plotted fresh at its current size so it stays crisp. */
    private void drawRipple(Bullet b) {
        float cx = b.x + b.w / 2f, cy = b.y + b.h / 2f, rx = b.w / 2f, ry = b.h / 2f;
        int n = 12 + (int) (ry * 1.6f);                       // more dots as it grows, so the ring stays closed
        batch.setColor(1f, 0.55f, 0.15f, 1f);
        for (int i = 0; i < n; i++) {
            float ang = i * MathUtils.PI2 / n;
            batch.draw(pix, Math.round(cx + MathUtils.cos(ang) * rx - 1f), Math.round(cy + MathUtils.sin(ang) * ry - 1f), 2f, 2f);
        }
        batch.setColor(1f, 0.95f, 0.6f, 1f);                  // bright inner edge on the front half
        for (int i = -n / 4; i <= n / 4; i++) {
            float ang = i * MathUtils.PI2 / n;
            batch.draw(pix, Math.round(cx + MathUtils.cos(ang) * (rx - 1.5f) - 0.5f), Math.round(cy + MathUtils.sin(ang) * (ry - 1.5f) - 0.5f), 1f, 1f);
        }
        batch.setColor(Color.WHITE);
    }

    private static void addScore(Player p, int pts) { if (p != null) p.score += pts; }

    // =============================================================== collisions

    private static boolean overlap(float ax, float ay, float aw, float ah, float bx, float by, float bw, float bh) {
        return ax < bx + bw && ax + aw > bx && ay < by + bh && ay + ah > by;
    }

    private void collide() {
        // ---- player projectiles (points go to whoever fired)
        for (Bullet b : pBullets) {
            if (b.dead) continue;
            boolean laser = b.kind == Bullet.Kind.LASER;
            // brick maze: every weapon blasts the blocks it touches (lasers keep going)
            float reach = b.kind == Bullet.Kind.MISSILE ? 3f : 0f;
            if (level.breakBricks(b.x, b.y - reach, b.w + reach, b.h + 2 * reach) > 0) {
                addScore(b.owner, 10);
                boom(b.x + b.w, b.y + b.h / 2f, 0.6f);
                a.play("hit", 0.15f);
                if (!laser) { b.dead = true; continue; }
            }
            if (b.kind == Bullet.Kind.RIPPLE) {
                // a ripple is only stopped by terrain at its core, so a big ring can pass close to walls
                if (level.hits(b.x, b.y + b.h / 2f - 2f, b.w, 4f)) { b.dead = true; continue; }
            } else if (b.kind != Bullet.Kind.MISSILE && level.hits(b.x, b.y, b.w, b.h)) { b.dead = true; continue; }

            for (Hazard h : hazards) {
                if ((h.type == Hazard.Type.CRUSHER && overlap(b.x, b.y, b.w, b.h, h.x, h.y, h.w, h.h))
                        || (h.type == Hazard.Type.FANG && h.touches(b.x, b.y, b.w, b.h))) {
                    b.dead = true;
                    break;
                }
            }
            if (b.dead) continue;

            for (Hazard h : hazards) {
                if (!h.shootable() || h.dead || !overlap(b.x, b.y, b.w, b.h, h.x, h.y, h.w, h.h)) continue;
                if (laser) { if (b.hits.contains(h)) continue; b.hits.add(h); } else b.dead = true;
                h.hp -= b.dmg;
                if (h.hp <= 0) { h.dead = true; addScore(b.owner, 50); boom(h.x + h.w / 2f, h.y + h.h / 2f, h.w / 20f); a.play("boom", 0.2f); }
                else hitSpark(b, h.x, h.y, h.w, h.h);
                if (b.dead) break;
            }
            if (b.dead) continue;

            for (Enemy e : enemies) {
                if (e.dead || !overlap(b.x, b.y, b.w, b.h, e.x, e.y, e.w, e.h)) continue;
                if (laser) { if (b.hits.contains(e)) continue; b.hits.add(e); } else b.dead = true;
                e.hp -= b.dmg;
                if (e.hp <= 0) killEnemy(e, b.owner);
                else { a.play("hit", 0.15f); hitSpark(b, e.x, e.y, e.w, e.h); }
                if (b.dead) break;
            }
            if (b.dead) { if (b.kind == Bullet.Kind.MISSILE) boom(b.x, b.y, 0.6f); continue; }

            if (boss != null && !boss.dying && overlap(b.x, b.y, b.w, b.h, boss.x, boss.y, boss.w, boss.h)
                    && (boss.mask == null || boss.mask.overlaps(boss.x, boss.y, b.x, b.y, b.w, b.h))) {
                // any weapon, anywhere on the boss; a laser hurts it once per beam.
                // After each hit the boss can't lose HP for Boss.HIT_COOLDOWN; shots that land then are absorbed.
                if (boss.hitCooldown > 0f) {
                    if (!laser) { b.dead = true; hitSpark(b, boss.x, boss.y, boss.w, boss.h); }   // a laser keeps going and may still hurt it a moment later
                    if (b.dead && b.kind == Bullet.Kind.MISSILE) boom(b.x, b.y, 0.6f);
                    continue;
                }
                if (laser) { if (b.hits.contains(boss)) continue; b.hits.add(boss); } else b.dead = true;
                boss.hp = Math.max(0f, boss.hp - b.dmg);
                boss.hitCooldown = Boss.HIT_COOLDOWN;
                boss.flash = 0.08f;
                a.play("hit", 0.15f);
                if (boss.hp > 0) hitSpark(b, boss.x, boss.y, boss.w, boss.h);
                if (boss.hp <= 0) {
                    boss.dying = true;
                    eBullets.clear();
                    addScore(b.owner, 5000);
                }
            }
            if (b.dead && b.kind == Bullet.Kind.MISSILE) boom(b.x, b.y, 0.6f);
        }

        // ---- enemy bullets
        for (Bullet b : eBullets) {
            if (b.dead) continue;
            if (level.hits(b.x, b.y, b.w, b.h)) { b.dead = true; continue; }
            for (Hazard h : hazards) {
                if (h.type == Hazard.Type.CRUSHER && overlap(b.x, b.y, b.w, b.h, h.x, h.y, h.w, h.h)) b.dead = true;
                if (h.type == Hazard.Type.FANG && h.touches(b.x, b.y, b.w, b.h)) b.dead = true;
            }
            if (b.dead) continue;
            for (Player p : players) {
                if (alive(p) && overlap(b.x, b.y, b.w, b.h, p.hitX(), p.hitY(), Player.HIT_W, Player.HIT_H)) {
                    b.dead = true;
                    hurt(p, false);
                    break;
                }
            }
        }

        // ---- each ship vs everything else
        for (Player p : players) if (alive(p)) collideShip(p);
    }

    private void collideShip(Player p) {
        float hx = p.hitX(), hy = p.hitY(), hw = Player.HIT_W, hh = Player.HIT_H;
        boolean ghost = p.ghostT > 0f;   // just (re)entered: passes through terrain, teeth and crushers
        if (!ghost && level.hits(hx, hy, hw, hh)) { hurt(p, true); return; }
        for (Enemy e : enemies) {
            if (!e.dead && overlap(hx, hy, hw, hh, e.x + 2f, e.y + 2f, e.w - 4f, e.h - 4f)) { hurt(p, false); if (p.dead) return; }
        }
        for (Hazard h : hazards) {
            if (h.dead) continue;
            if (!ghost && h.type == Hazard.Type.CRUSHER && overlap(hx, hy, hw, hh, h.x, h.y, h.w, h.h)) { hurt(p, true); return; }
            if (!ghost && h.type == Hazard.Type.FANG && h.touches(hx, hy, hw, hh)) { hurt(p, true); return; }
            if (h.shootable() && overlap(hx, hy, hw, hh, h.x + 2f, h.y + 2f, h.w - 4f, h.h - 4f)) {
                h.dead = true;
                boom(h.x + h.w / 2f, h.y + h.h / 2f, h.w / 20f);
                hurt(p, false);
                if (p.dead) return;
            }
        }
        if (boss != null && overlap(hx, hy, hw, hh, boss.x, boss.y, boss.w, boss.h)
                && (boss.mask == null || boss.mask.overlaps(boss.x, boss.y, hx, hy, hw, hh))) {
            hurt(p, false);
            if (p.dead) return;
        }
        for (Capsule c : caps) {
            if (!c.dead() && overlap(p.x + 2f, p.y, 28f, 16f, c.x, c.y, 12f, 12f)) {
                c.x = -99999f;   // consumed (culled next frame)
                p.score += 300;
                p.meter = p.meter >= 6 ? 1 : p.meter + 1;   // only the player who caught it advances
                a.play("pickup", 0.35f);
            }
        }
    }

    private void killEnemy(Enemy e, Player killer) {
        e.dead = true;
        addScore(killer, e.score);
        boom(e.x + e.w / 2f, e.y + e.h / 2f, 1f);
        a.play("boom", 0.22f);
        Enemy.Group g = e.group;
        if (g != null) {
            g.killed++;
            if (g.carrier && !g.lost && g.killed >= g.size) {
                Capsule c = new Capsule();
                c.x = e.x; c.y = e.y; c.y0 = e.y;
                caps.add(c);
            }
        }
    }

    private void hurt(Player p, boolean ignoreShield) {
        if (p.dead || (boss != null && boss.dying)) return;
        if (!ignoreShield) {
            if (p.invuln > 0f) return;
            if (p.shield > 0) {
                p.shield--;
                p.invuln = 0.6f;
                p.shieldHitT = p.invuln;   // the shield blinks (and fades out if this was its last hit)
                a.play("hit", 0.3f);
                return;
            }
        }
        p.dead = true;
        p.aiming = false;
        boom(p.x + 16f, p.y + 8f, 1.8f);
        a.play("boom", 0.5f);
        p.lives--;           // upgrades are kept when the ship is destroyed
        if (anyAlive()) {
            p.respawnT = RESPAWN_DELAY;   // partner still flying: re-enter on the spot
        } else {
            setState(State.DEAD);         // nobody left: rewind to the checkpoint
        }
    }

    // =============================================================== drawing

    private void draw() {
        ScreenUtils.clear(0, 0, 0, 1);
        vp.apply();
        cam.position.set(Math.round(scrollX) + W / 2f, Math.round(scrollY) + H / 2f, 0f);
        cam.update();
        batch.setProjectionMatrix(cam.combined);
        batch.begin();
        float camL = Math.round(scrollX), camB = Math.round(scrollY);
        if (level.background != null) {   // map property "background"; stage 1 is plain black space
            batch.draw(bgTex, camL, camB, W, H, (int) (scrollX * 0.25f), 0, W, H, false, false);
        }
        batch.setColor(STAR_TINT[levelIdx]);
        batch.draw(starTex, camL, camB, W, H, (int) (scrollX * 0.6f), (int) (-scrollY * 0.6f), W, H, false, false);
        batch.setColor(Color.WHITE);

        drawFangs();          // teeth first, so the ground hides the part that is pulled in
        drawTerrain();
        drawHazards();
        drawCapsules();
        drawEnemies();
        if (boss != null) drawBoss();
        drawBullets();
        for (Player p : players) if (alive(p)) drawPlayer(p);
        if (debugHitbox) for (Player p : players) if (alive(p)) drawHitbox(p);
        for (Explosion e : fx) {
            int f = Math.min(4, (int) (e.t * 14f));
            float s = 24f * e.scale;
            batch.draw(boomF[f], e.x - s / 2f, e.y - s / 2f, s, s);
        }
        batch.end();

        batch.setProjectionMatrix(hud.combined);
        batch.begin();
        drawHud();
        batch.end();
    }

    /** Stage art (image layers), the "terrain" tile layer, and the bricks that are still standing. */
    private void drawTerrain() {
        float camL = Math.round(scrollX), camB = Math.round(scrollY);
        for (int i = 0; i < artTex.length; i++) {
            Level.Art t = level.art.get(i);
            if (t.x > camL + W || t.x + t.w < camL || t.y > camB + H || t.y + t.h < camB) continue;
            batch.draw(artTex[i], t.x, t.y, t.w, t.h);
        }
        float s = level.cell;
        int c0 = Math.max(0, (int) (camL / s) - 1), c1 = Math.min(level.gCols - 1, (int) ((camL + W) / s) + 1);
        int r0 = Math.max(0, (int) (camB / s) - 1), r1 = Math.min(level.gRows - 1, (int) ((camB + H) / s) + 1);
        if (level.terrain != null) {
            for (int r = r0; r <= r1; r++)
                for (int c = c0; c <= c1; c++) {
                    int gid = level.terrain[r * level.gCols + c];
                    if (gid != 0) drawTile(gid, c * s, r * s, s);
                }
        }
        if (level.bricks != null) {
            float p = brickTex.getWidth();   // the brick pattern repeats seamlessly across the wall
            for (int r = r0; r <= r1; r++)
                for (int c = c0; c <= c1; c++) {
                    if (!level.bricks[r * level.gCols + c]) continue;
                    float x = c * s, y = r * s;
                    batch.draw(brickTex, x, y, s, s, x / p, -y / p, (x + s) / p, -(y + s) / p);
                }
        }
        if (debugHitbox) {   // H: show the solid cells of the map (terrain + collision layers)
            batch.setColor(1f, 0.15f, 0.15f, 0.35f);
            for (int r = r0; r <= r1; r++)
                for (int c = c0; c <= c1; c++)
                    if (level.solidTerrainCell(c, r)) batch.draw(pix, c * s, r * s, s, s);
            batch.setColor(Color.WHITE);
        }
    }

    private final java.util.HashMap<Integer, TextureRegion> tileRegions = new java.util.HashMap<>();

    /** Draws one map tile (with Tiled's flip flags) at world position (x, y). */
    private void drawTile(int gid, float x, float y, float size) {
        int id = gid & TiledMap.GID_MASK;
        TextureRegion r = tileRegions.get(id);
        if (r == null) {
            TiledMap.Tileset ts = level.tiled.tilesetFor(id);
            if (ts == null || ts.image == null) return;
            int local = id - ts.firstGid, cols = Math.max(1, ts.columns);
            r = new TextureRegion(a.texFile(ts.image), (local % cols) * ts.tileWidth, (local / cols) * ts.tileHeight,
                    ts.tileWidth, ts.tileHeight);
            tileRegions.put(id, r);
        }
        boolean fh = (gid & TiledMap.FLIP_H) != 0, fv = (gid & TiledMap.FLIP_V) != 0;
        batch.draw(r, fh ? x + size : x, fv ? y + size : y, fh ? -size : size, fv ? -size : size);
    }

    private void drawFangs() {
        for (Hazard h : hazards) {
            if (h.type != Hazard.Type.FANG || h.x > scrollX + W + 40f || h.x + h.w < scrollX) continue;
            batch.draw(a.texFile(h.image), h.x, h.y, h.w, h.h);
        }
    }

    private void drawHazards() {
        for (Hazard h : hazards) {
            if (h.type == Hazard.Type.FANG) continue;   // drawn behind the terrain
            if (h.x > scrollX + W + 40f || h.x + 40f < scrollX) continue;
            switch (h.type) {
                case ROCK:
                    batch.draw(rockR, h.x, h.y, 16, 16);
                    break;
                case ASTEROID:
                    batch.draw(a.texFile(h.image), h.x, h.y, h.w, h.h);
                    break;
                case CRUSHER:
                    for (float yy = h.y; yy < h.y + h.h; yy += 16f) batch.draw(shaftR, h.x + 8f, yy, 16, 16);
                    batch.draw(headR, h.x, h.fromCeil ? h.y : h.y + h.h - 16f, 32, 16);
                    break;
                default: // volcano: glowing crater
                    float pulse = 0.5f + 0.5f * MathUtils.sin(h.t * 6f);
                    batch.setColor(1f, 0.4f + 0.3f * pulse, 0.1f, 1f);
                    batch.draw(pix, h.x - 6f, h.y, 12, 2);
                    batch.setColor(Color.WHITE);
                    break;
            }
        }
    }

    private void drawCapsules() {
        for (Capsule c : caps) batch.draw(capF[(int) (c.t * 6f) % capF.length], c.x, c.y, 12, 12);
    }

    private void drawEnemies() {
        for (Enemy e : enemies) {
            TextureRegion r;
            switch (e.type) {
                case FAN: r = fanF[(int) (e.t * 10f) % fanF.length]; break;
                case RUSHER: r = rushF[(int) (e.t * 12f) % rushF.length]; break;
                case WALKER: r = walkF[(int) (e.t * 6f) % walkF.length]; break;
                default: r = turretR; break;
            }
            boolean red = e.group != null && e.group.carrier;
            if (red) batch.setColor(1f, 0.45f, 0.45f, 1f);
            if (e.type == Enemy.Type.TURRET_CEIL) batch.draw(r, e.x, e.y + e.h, e.w, -e.h);
            else if (e.type == Enemy.Type.WALKER) {
                // mirrored when walking right, upside down on the ceiling, tilted 45 degrees at steps
                // (front up while climbing a step, front down while dropping off one)
                int dir = e.vx < 0f ? -1 : 1;
                float rot = (e.ceiling ? -1 : 1) * dir * e.climb * 45f;
                batch.draw(r, e.x, e.y, e.w / 2f, e.h / 2f, e.w, e.h, dir > 0 ? -1f : 1f, e.ceiling ? -1f : 1f, rot);
            } else batch.draw(r, e.x, e.y, e.w, e.h);
            if (red) batch.setColor(Color.WHITE);
        }
    }

    private void drawBoss() {
        if (boss.flash > 0f) batch.setColor(1f, 0.55f, 0.55f, 1f);
        if (boss.dying && (int) (boss.dieT * 20f) % 2 == 0) batch.setColor(1f, 1f, 1f, 0.5f);
        batch.draw(bossR, boss.x, boss.y, boss.w, boss.h);
        batch.setColor(Color.WHITE);
    }

    private void drawBullets() {
        for (Bullet b : pBullets) {
            switch (b.kind) {
                case LASER: batch.draw(laserR, b.x, b.y, b.w, b.h); break;
                case RIPPLE: drawRipple(b); break;
                case MISSILE:   // tilted 45 degrees nose-up while climbing, nose-down while dropping
                    if (b.up)       // 2-WAY's upward missile: drawn upside down; "climbing" a lower ceiling means going down
                        batch.draw(missR, b.x, b.y, b.w / 2f, b.h / 2f, b.w, b.h, 1f, -1f, -b.climb * 45f);
                    else if (b.climb == FloorCrawler.LEVEL) batch.draw(missR, b.x, b.y, b.w, b.h);
                    else batch.draw(missR, b.x, b.y, b.w / 2f, b.h / 2f, b.w, b.h, 1f, 1f, b.climb * 45f);
                    break;
                default:
                    if (b.vy == 0f && b.vx > 0f) batch.draw(shotR, b.x, b.y, b.w, b.h);
                    else batch.draw(shotR, b.x - 2f, b.y + 2f, 6f, 2f, 12f, 4f, 1f, 1f,
                            MathUtils.atan2(b.vy, b.vx) * MathUtils.radiansToDegrees);
                    break;
            }
        }
        for (Bullet b : eBullets) {
            if (b.kind == Bullet.Kind.LAVA) batch.draw(lavaF[(int) (b.t * 10f) % lavaF.length], b.x, b.y, 8, 8);
            else batch.draw(ebR, b.x, b.y, 6, 6);
        }
    }

    /**
     * The camera is snapped to whole pixels (Math.round(scrollX)). Things that keep a fixed position
     * on screen while the stage scrolls (ships, options) have fractional world X, so drawing them at
     * that X makes them hop back and forth by a pixel against the snapped camera. Snapping their
     * screen offset instead keeps them perfectly still.
     */
    private float snapX(float worldX) { return Math.round(scrollX) + Math.round(worldX - scrollX); }

    private float snapY(float worldY) { return Math.round(scrollY) + Math.round(worldY - scrollY); }

    private void drawPlayer(Player p) {
        Color tint = p.index == 1 ? P2_TINT : Color.WHITE;
        float px = snapX(p.x), py = snapY(p.y);
        boolean blinkPhase = (int) (p.invuln * 20f) % 2 == 0;
        boolean shieldHit = p.shieldHitT > 0f;          // shield took the hit: it blinks, the ship stays solid
        boolean blink = p.invuln > 0f && !shieldHit && blinkPhase;
        batch.setColor(tint);
        if (!blink) batch.draw(playerF[Math.min(p.tilt, playerF.length - 1)], px, py, 32, 16);
        for (int i = 0; i < p.options; i++) {
            batch.draw(optionF[(int) (stateT * 8f + i) % optionF.length], snapX(optX(p, i)), snapY(optY(p, i)), 12, 12);
        }
        batch.setColor(Color.WHITE);
        if (p.aiming) {   // dotted line showing the DOUBLE shot direction
            float ang = p.doubleDir * MathUtils.PI / 4f, cx = px + 16f, cy = py + 8f;
            batch.setColor(1f, 0.9f, 0.3f, 0.9f);
            for (int i = 1; i <= 4; i++) {
                batch.draw(pix, cx + MathUtils.cos(ang) * (10f + i * 7f) - 1f, cy + MathUtils.sin(ang) * (10f + i * 7f) - 1f, 2f, 2f);
            }
            batch.setColor(Color.WHITE);
        }
        if ((p.shield > 0 || shieldHit) && !(shieldHit && blinkPhase)) {
            batch.setColor(1f, 1f, 1f, 0.35f + 0.1f * Math.max(p.shield, 1));
            batch.draw(shieldR, px - 6f, py - 8f, 40, 32);
            batch.setColor(Color.WHITE);
        }
        if (p.invuln > 1f && playersInGame() > 1) {   // "1P" / "2P" tag just after (re)entering
            text((p.index + 1) + "P", px, py + 28f, 0.5f, p.index == 1 ? P2_TINT : Color.CYAN, 32, Align.center);
            a.font.getData().setScale(1f);
            a.font.setColor(Color.WHITE);
        }
    }

    /** Debug overlay: the player's collision box as a translucent fill with a solid outline. */
    private void drawHitbox(Player p) {
        float x = snapX(p.x) + Player.HIT_OX, y = snapY(p.y) + Player.HIT_OY, w = Player.HIT_W, h = Player.HIT_H;
        batch.setColor(1f, 0f, 1f, 0.35f);
        batch.draw(pix, x, y, w, h);
        batch.setColor(1f, 0f, 1f, 1f);
        batch.draw(pix, x, y, w, 1f);
        batch.draw(pix, x, y + h - 1f, w, 1f);
        batch.draw(pix, x, y, 1f, h);
        batch.draw(pix, x + w - 1f, y, 1f, h);
        batch.setColor(Color.WHITE);
    }

    // ---------------------------------------------------------------- HUD

    private void text(String s, float x, float y, float scale, Color c, float width, int align) {
        BitmapFont f = a.font;
        f.getData().setScale(scale);
        f.setColor(c);
        f.draw(batch, s, x, y, width, align, false);
    }

    private static final float SLOT_W = 38f, METER_MARGIN = 4f;

    private void drawHud() {
        int hi = game.hiScore;
        for (Player p : players) if (p != null) hi = Math.max(hi, p.score);
        boolean blinkOn = (int) (stateT * 2f) % 2 == 0;

        // top row: 1UP left, HI centre, 2UP right; stage name underneath
        drawScore(0, 8, Align.left, blinkOn);
        text("HI " + String.format("%06d", hi), 0, H - 4, 0.55f, Color.WHITE, W, Align.center);
        drawScore(1, W - 8 - 140, Align.right, blinkOn);
        text("STAGE " + (levelIdx + 1) + "  " + level.name, 0, H - 15, 0.45f, Color.LIGHT_GRAY, W, Align.center);

        // power-up meters: player 1 bottom-left, player 2 bottom-right
        drawMeter(0, METER_MARGIN, Align.left, blinkOn);
        drawMeter(1, W - METER_MARGIN - 6 * SLOT_W, Align.right, blinkOn);

        // boss health bar with "hp/max" in it
        if (boss != null && !boss.dying && !boss.entering) {
            float bw = 160f, bh = 9f, bx = W / 2f - bw / 2f, by = H - 33f;
            batch.setColor(0.2f, 0f, 0f, 0.8f);
            batch.draw(pix, bx, by, bw, bh);
            batch.setColor(1f, 0.25f, 0.2f, 1f);
            batch.draw(pix, bx, by, bw * Math.max(0f, boss.hp / boss.maxHp), bh);
            batch.setColor(Color.WHITE);
            String hp = (int) Math.ceil(boss.hp) + "/" + (int) boss.maxHp;
            text(hp, 1, by + bh - 1f, 0.42f, Color.BLACK, W, Align.center);   // shadow
            text(hp, 0, by + bh, 0.42f, Color.WHITE, W, Align.center);
        }

        if (game.volumeMsgT > 0f && state != State.PAUSE) {
            text(game.volumeText(), 0, H - 44, 0.55f, Color.YELLOW, W, Align.center);
        }

        switch (state) {
            case INTRO:
                text("STAGE " + (levelIdx + 1), 0, 160, 1.6f, Color.WHITE, W, Align.center);
                text(level.name, 0, 130, 0.9f, Color.YELLOW, W, Align.center);
                break;
            case PLAY:
                if (bannerT > 0f && (int) (bannerT * 4f) % 2 == 0) {
                    text("WARNING", 0, 170, 1.8f, Color.RED, W, Align.center);
                }
                break;
            case PAUSE:
                text("PAUSED", 0, 150, 1.6f, Color.WHITE, W, Align.center);
                text(game.volumeText(), 0, 120, 0.7f, Color.YELLOW, W, Align.center);
                text("1/2  MUSIC -/+      3/4  SFX -/+      PAD: BACK + D-PAD", 0, 100, 0.55f, Color.LIGHT_GRAY, W, Align.center);
                break;
            case CLEAR:
                text("STAGE " + (levelIdx + 1) + " CLEAR", 0, 160, 1.5f, Color.GREEN, W, Align.center);
                text("BONUS " + stageBonus(), 0, 125, 0.8f, Color.WHITE, W, Align.center);
                break;
            case GAMEOVER:
                text("GAME OVER", 0, 160, 1.8f, Color.RED, W, Align.center);
                if (stateT > 1f) text("PRESS ENTER / START", 0, 120, 0.7f, Color.WHITE, W, Align.center);
                break;
            case WIN:
                text("YOU SAVED THE GALAXY", 0, 170, 1.3f, Color.CYAN, W, Align.center);
                text(finalScores(), 0, 135, 0.8f, Color.WHITE, W, Align.center);
                if (stateT > 1f) text("PRESS ENTER / START", 0, 105, 0.7f, Color.YELLOW, W, Align.center);
                break;
            default:
                break;
        }
        a.font.getData().setScale(1f);
        a.font.setColor(Color.WHITE);
    }

    private String finalScores() {
        StringBuilder s = new StringBuilder();
        for (Player p : players) {
            if (p == null) continue;
            if (s.length() > 0) s.append("     ");
            s.append(p.index + 1).append("P ").append(String.format("%06d", p.score));
        }
        return s.toString();
    }

    private void drawScore(int idx, float x, int align, boolean blinkOn) {
        Player p = players[idx];
        String label = (idx + 1) + "UP ";
        if (p != null) {
            text(label + String.format("%06d", p.score), x, H - 4, 0.55f, idx == 1 ? P2_TINT : Color.WHITE, 140, align);
        }
    }

    /** One player's lives line plus six power-up slots, or a join / continue prompt. */
    private void drawMeter(int idx, float x0, int align, boolean blinkOn) {
        Player p = players[idx];
        float width = 6 * SLOT_W;
        if (p == null && choosing[idx] >= 0) {     // mid-game joiner picking a ship type
            int type = choosing[idx];
            text((idx + 1) + "P  < TYPE " + (type + 1) + " >   FIRE: LAUNCH", x0, 31, 0.45f,
                    blinkOn ? Color.YELLOW : Color.WHITE, width, align);
            drawSlots(x0, Player.slotsFor(type), null);
            return;
        }
        if (p == null || p.out) {
            if (state == State.GAMEOVER || state == State.WIN) return;
            String msg;
            if (!Controls.hasInput(idx)) msg = "CONNECT PAD 2 TO JOIN";
            else msg = p == null ? (idx + 1) + "P  PRESS FIRE TO JOIN" : (idx + 1) + "P  PRESS FIRE TO CONTINUE";
            if (blinkOn || !Controls.hasInput(idx)) {
                text(msg, x0, 14f, 0.45f, Controls.hasInput(idx) ? Color.YELLOW : Color.GRAY, width, Align.center);
            }
            return;
        }
        Color who = idx == 1 ? P2_TINT : Color.CYAN;
        text((idx + 1) + "P  TYPE " + (p.shipType + 1) + "  SHIPS x" + Math.max(0, p.lives), x0, 31, 0.45f, who, width, align);
        drawSlots(x0, p.slots, p);
    }

    /** Six meter slots in the given order; p = null draws a preview (nothing owned or highlighted). */
    private void drawSlots(float x0, int[] slots, Player p) {
        for (int i = 0; i < 6; i++) {
            float x = x0 + i * SLOT_W;
            int power = slots[i];
            boolean sel = p != null && p.meter == i + 1;
            boolean owned = p != null && ((power == Player.SPEED && p.speedLvl > 0) || ((power == Player.MISSILE || power == Player.TWO_WAY) && p.missile)
                    || (power == Player.DOUBLE && p.weapon == 1) || (power == Player.LASER && p.weapon == 2)
                    || (power == Player.RIPPLE && p.weapon == Player.W_RIPPLE)
                    || (power == Player.OPTION && p.options > 0) || (power == Player.SHIELD && p.shield > 0));
            boolean moved = p == null && power != Player.CLASSIC_SLOTS[i];
            if (sel) batch.setColor(1f, 0.85f, 0.1f, 0.9f);
            else if (owned) batch.setColor(0.15f, 0.5f, 0.25f, 0.8f);
            else if (moved) batch.setColor(0.45f, 0.15f, 0.45f, 0.85f);   // preview: the two swapped slots
            else batch.setColor(0.1f, 0.12f, 0.25f, 0.8f);
            batch.draw(pix, x, 3, SLOT_W - 2f, 14);
            batch.setColor(Color.WHITE);
            String label = Player.POWER_NAMES[power];
            if (p != null && power == Player.SPEED && p.speedLvl > 0) label += " " + p.speedLvl;
            if (p != null && power == Player.OPTION && p.options > 0) label += " " + p.options;
            text(label, x, 13.5f, 0.38f, sel ? Color.BLACK : Color.WHITE, SLOT_W - 2f, Align.center);
        }
    }
}
