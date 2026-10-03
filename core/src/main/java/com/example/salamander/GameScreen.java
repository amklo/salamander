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
    private static final String[] METER = {"SPEED", "MISSILE", "DOUBLE", "LASER", "OPTION", "SHIELD"};
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
    float scrollX, scrollSpeed;
    final ArrayList<Bullet> eBullets = new ArrayList<>();

    private State state = State.INTRO;
    private float stateT, checkpointX, bannerT;
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

        bgTex = a.tex("bg" + levelIdx);
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

        resetWorld(0f);
        checkpointX = 0f;
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
        p.x = sx + 40f;
        float mid = (level.floorTop(p.x) + level.ceilBottom(p.x)) / 2f - Player.H / 2f;
        float off = playersInGame() > 1 ? (p.index == 0 ? 14f : -14f) : 0f;   // P1 above, P2 below
        p.y = MathUtils.clamp(mid + off, 2f, H - Player.H - 2f);
        p.invuln = 2f;
        p.fireCd = 0f; p.missCd = 0f;
        p.fillTrail(40f, p.y);
    }

    /** Player 2 joins, or a player with no ships left continues, by pressing fire. */
    private void checkJoins() {
        for (int i = 0; i < players.length; i++) {
            Player p = players[i];
            if ((p == null || p.out) && Controls.pressed(i, Action.FIRE)) {
                if (p == null) {
                    p = new Player(i);
                    if (debugLoadout) p.applyDebugLoadout();
                    players[i] = p;
                } else {
                    p.out = false;
                    p.lives = Player.START_LIVES;
                }
                placePlayer(p, scrollX);
                a.play("power", 0.4f);
            }
        }
    }

    // =============================================================== setup / reset

    private void resetWorld(float sx) {
        scrollX = sx;
        scrollSpeed = 0f;
        pBullets.clear(); eBullets.clear(); enemies.clear(); caps.clear(); groups.clear();
        boss = null;
        bossSpawned = false;
        buildHazards();
        nextSpawn = 0;
        while (nextSpawn < level.spawns.size() && level.spawns.get(nextSpawn).x < sx + W + 40f) nextSpawn++;
        for (Player p : players) {
            if (p == null || p.out) continue;
            if (p.lives < 0) p.out = true;           // was shot down on its last ship
            else placePlayer(p, sx);
        }
    }

    private void buildHazards() {
        hazards.clear();
        for (float[] r : level.rocks) {
            Hazard h = new Hazard(Hazard.Type.ROCK);
            h.x = r[0]; h.w = 16; h.h = 16; h.hp = 2;
            h.y = level.ceilBottom(r[0] + 8f) - 16f;
            hazards.add(h);
        }
        for (float[] v : level.volcanoes) {
            Hazard h = new Hazard(Hazard.Type.VOLCANO);
            h.x = v[0];
            h.y = level.floorTop(v[0]);
            h.timer = MathUtils.random(0.5f, 2.5f);
            hazards.add(h);
        }
        for (float[] c : level.crushers) {
            Hazard h = new Hazard(Hazard.Type.CRUSHER);
            h.x = c[0]; h.w = 32; h.fromCeil = c[1] > 0f;
            h.phase = c[2]; h.minLen = c[3]; h.maxLen = c[4]; h.speed = c[5];
            h.updateCrusher(level);
            hazards.add(h);
        }
    }

    @Override
    public void resize(int w, int h) { vp.update(w, h, false); }

    /** Music per stage (index = levelIdx); null = no music for that stage. */
    private static final String[] STAGE_BGM = {"stg1.mid", "stg2.mid", "stg3.mid"};
    /** Plays from the moment the boss arrives until the stage is cleared. */
    private static final String BOSS_BGM = "boss.mid";

    private void playStageMusic() {
        String song = STAGE_BGM[levelIdx];
        if (song != null) game.bgm.play(song);
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
                        resetWorld(checkpointX);
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
        boolean arena = scrollX >= level.stopX;
        float target = arena ? 0f : level.baseSpeed * level.speedAt(scrollX + 240f);
        scrollSpeed += (target - scrollSpeed) * Math.min(1f, dt * 2.5f);
        float oldS = scrollX;
        scrollX = Math.min(scrollX + scrollSpeed * dt, level.stopX);
        float dS = scrollX - oldS;
        for (float cp : level.checkpoints) if (scrollX >= cp) checkpointX = Math.max(checkpointX, cp);
        if (scrollX >= level.stopX && !bossSpawned) spawnBoss();

        // --- spawning
        while (nextSpawn < level.spawns.size() && level.spawns.get(nextSpawn).x <= scrollX + W + 40f) {
            spawnEnemy(level.spawns.get(nextSpawn++));
        }

        for (Player p : players) {
            if (p == null || p.out) continue;
            if (p.dead) updateRespawn(p, dt);
            else updatePlayer(p, dt, dS);
        }

        // --- world objects
        for (Bullet b : pBullets) {
            b.update(dt, level);
            if (b.x > scrollX + W + 60f || b.x < scrollX - 60f || b.y < -40f || b.y > H + 40f) b.dead = true;
        }
        for (Bullet b : eBullets) {
            b.update(dt, level);
            if (b.x > scrollX + W + 80f || b.x < scrollX - 60f || b.y < -30f || b.y > H + 60f) b.dead = true;
        }
        for (Enemy e : enemies) {
            e.update(this, dt);
            if (e.x + e.w < scrollX - 40f || e.y < -60f || e.y > H + 60f) {
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
        updateFx(dt);
        if (bannerT > 0f) bannerT -= dt;

        collide();

        pBullets.removeIf(b -> b.dead);
        eBullets.removeIf(b -> b.dead);
        enemies.removeIf(e -> e.dead);
        hazards.removeIf(h -> h.dead);
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

    private void updatePlayer(Player p, float dt, float dS) {
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
        p.y += dy * sp * dt;
        p.x = MathUtils.clamp(p.x, scrollX + 6f, scrollX + W - Player.W - 6f);
        p.y = MathUtils.clamp(p.y, 2f, H - Player.H - 2f);
        p.tilt = dy > 0f ? 1 : (dy < 0f ? 2 : 0);

        p.invuln = Math.max(0f, p.invuln - dt);
        p.fireCd -= dt;
        p.missCd -= dt;

        // Options follow the ship's path. Only record when the ship actually moved on screen,
        // so the options stay where they are while the ship stands still.
        p.trailAcc += dt;
        while (p.trailAcc >= 1f / 60f) {
            float sx = p.x - scrollX;
            if (Math.abs(sx - p.trailX(0)) > 0.25f || Math.abs(p.y - p.trailY(0)) > 0.25f) p.record(sx, p.y);
            p.trailAcc -= 1f / 60f;
        }

        boolean fire = Controls.held(id, Action.FIRE);
        if (fire && p.fireCd <= 0f) {
            shoot(p);
            p.fireCd = p.weapon == 2 ? 0.2f : 0.13f;
        }
        if (fire && p.missile && p.missCd <= 0f) {
            launchMissile(p, p.x + 14f, p.y + 2f);
            for (int i = 0; i < p.options; i++) launchMissile(p, optX(p, i) + 2f, optY(p, i));
            p.missCd = 1.0f;
        }
        if (Controls.pressed(id, Action.POWER)) activateMeter(p);
    }

    float optX(Player p, int i) { return scrollX + p.trailX(10 * (i + 1)) + 10f; }
    float optY(Player p, int i) { return p.trailY(10 * (i + 1)) + 2f; }

    // Shots are fired from the "nose": the front tip of the ship art (x 7..23 of the 32px frame,
    // centre line at y 8) or the right edge of an option.
    private static final float NOSE_X = 24f, NOSE_Y = 8f;

    private void shoot(Player p) {
        fireFrom(p, p.x + NOSE_X, p.y + NOSE_Y);
        for (int i = 0; i < p.options; i++) fireFrom(p, optX(p, i) + 12f, optY(p, i) + 6f);
        a.play("shoot", 0.15f);
    }

    /** (nx, ny) = the muzzle: shots start with their tip there, so they come out of the ship, not ahead of it. */
    private void fireFrom(Player p, float nx, float ny) {
        if (p.weapon == 2) {
            Bullet b = new Bullet(Bullet.Kind.LASER, nx - Bullet.LASER_START_LEN, ny - 2f, Bullet.LASER_START_LEN, 4);
            b.vx = 540f;
            b.owner = p;
            pBullets.add(b);
            return;
        }
        Bullet b = new Bullet(Bullet.Kind.SHOT, nx - 12f, ny - 2f, 12, 4);
        b.vx = 800f;
        b.owner = p;
        pBullets.add(b);
        if (p.weapon == 1) {
            float ang = p.doubleDir * MathUtils.PI / 4f;
            Bullet d = new Bullet(Bullet.Kind.SHOT, nx - 8f, ny - 4f, 8, 8);
            d.vx = MathUtils.cos(ang) * 792f;
            d.vy = MathUtils.sin(ang) * 792f;
            d.owner = p;
            pBullets.add(d);
        }
    }

    private void launchMissile(Player p, float x, float y) {
        Bullet m = new Bullet(Bullet.Kind.MISSILE, x, y, 8, 6);
        m.vx = 110f; m.vy = -130f; m.dmg = 3;
        m.owner = p;
        pBullets.add(m);
    }

    private void activateMeter(Player p) {
        boolean ok = false;
        switch (p.meter) {
            case 1: if (p.speedLvl < 5) { p.speedLvl++; ok = true; } break;
            case 2: if (!p.missile) { p.missile = true; ok = true; } break;
            case 3: if (p.weapon != 1) { p.weapon = 1; ok = true; } break;
            case 4: if (p.weapon != 2) { p.weapon = 2; ok = true; } break;
            case 5: if (p.options < Player.MAX_OPTIONS) { p.options++; ok = true; } break;
            case 6: if (p.shield <= 0) { p.shield = 5; ok = true; } break;
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
            case WALKER:
                e = new Enemy(Enemy.Type.WALKER, s.x, level.floorTop(s.x + 8f), levelIdx);
                break;
            case TURRET_CEIL:
                e = new Enemy(Enemy.Type.TURRET_CEIL, s.x, level.ceilBottom(s.x + 8f) - 16f, levelIdx);
                break;
            default:
                e = new Enemy(Enemy.Type.TURRET_FLOOR, s.x, level.floorTop(s.x + 8f), levelIdx);
                break;
        }
        e.group = grp;
        enemies.add(e);
    }

    private void spawnBoss() {
        bossSpawned = true;
        int[] hp = {70, 100, 140};
        boss = new Boss(levelIdx, scrollX + W + 20f, hp[levelIdx]);
        bannerT = 2.6f;
        a.play("power", 0.3f);
        game.bgm.play(BOSS_BGM);
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
            if (b.kind != Bullet.Kind.MISSILE && level.hits(b.x, b.y, b.w, b.h)) { b.dead = true; continue; }

            for (Hazard h : hazards) {
                if (h.type == Hazard.Type.CRUSHER && overlap(b.x, b.y, b.w, b.h, h.x, h.y, h.w, h.h)) {
                    b.dead = true;
                    break;
                }
            }
            if (b.dead) continue;

            for (Hazard h : hazards) {
                if (h.type != Hazard.Type.ROCK || h.dead || !overlap(b.x, b.y, b.w, b.h, h.x, h.y, h.w, h.h)) continue;
                if (laser) { if (b.hits.contains(h)) continue; b.hits.add(h); } else b.dead = true;
                h.hp -= b.dmg;
                if (h.hp <= 0) { h.dead = true; addScore(b.owner, 50); boom(h.x + 8f, h.y + 8f, 0.8f); a.play("boom", 0.2f); }
                if (b.dead) break;
            }
            if (b.dead) continue;

            for (Enemy e : enemies) {
                if (e.dead || !overlap(b.x, b.y, b.w, b.h, e.x, e.y, e.w, e.h)) continue;
                if (laser) { if (b.hits.contains(e)) continue; b.hits.add(e); } else b.dead = true;
                e.hp -= b.dmg;
                if (e.hp <= 0) killEnemy(e, b.owner); else a.play("hit", 0.15f);
                if (b.dead) break;
            }
            if (b.dead) { if (b.kind == Bullet.Kind.MISSILE) boom(b.x, b.y, 0.6f); continue; }

            if (boss != null && !boss.dying && overlap(b.x, b.y, b.w, b.h, boss.x, boss.y, Boss.W, Boss.H)) {
                // any weapon, anywhere on the boss; a laser hurts it once per beam
                if (laser) { if (b.hits.contains(boss)) continue; b.hits.add(boss); } else b.dead = true;
                boss.hp -= b.dmg;
                boss.flash = 0.08f;
                a.play("hit", 0.15f);
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
        if (level.hits(hx, hy, hw, hh)) { hurt(p, true); return; }
        for (Enemy e : enemies) {
            if (!e.dead && overlap(hx, hy, hw, hh, e.x + 2f, e.y + 2f, e.w - 4f, e.h - 4f)) { hurt(p, false); if (p.dead) return; }
        }
        for (Hazard h : hazards) {
            if (h.dead) continue;
            if (h.type == Hazard.Type.CRUSHER && overlap(hx, hy, hw, hh, h.x, h.y, h.w, h.h)) { hurt(p, true); return; }
            if (h.type == Hazard.Type.ROCK && overlap(hx, hy, hw, hh, h.x + 1f, h.y + 1f, h.w - 2f, h.h - 2f)) {
                h.dead = true;
                boom(h.x + 8f, h.y + 8f, 0.8f);
                hurt(p, false);
                if (p.dead) return;
            }
        }
        if (boss != null && overlap(hx, hy, hw, hh, boss.x + 4f, boss.y + 4f, Boss.W - 8f, Boss.H - 8f)) {
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
        cam.position.set(Math.round(scrollX) + W / 2f, H / 2f, 0f);
        cam.update();
        batch.setProjectionMatrix(cam.combined);
        batch.begin();
        float camL = Math.round(scrollX);
        batch.draw(bgTex, camL, 0, W, H, (int) (scrollX * 0.25f), 0, W, H, false, false);
        batch.setColor(STAR_TINT[levelIdx]);
        batch.draw(starTex, camL, 0, W, H, (int) (scrollX * 0.6f), 0, W, H, false, false);
        batch.setColor(Color.WHITE);

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

    private void drawTerrain() {
        int c0 = Math.max(0, (int) (scrollX / 16f) - 1);
        int c1 = Math.min(level.cols - 1, c0 + 33);
        TextureRegion[] ts = a.tiles[levelIdx];
        for (int c = c0; c <= c1; c++) {
            int f = level.floor[c], ce = level.ceil[c];
            float x = c * 16f;
            for (int r = 0; r < f; r++) batch.draw(r == f - 1 ? ts[1] : ts[0], x, r * 16f);
            for (int r = 0; r < ce; r++) batch.draw(r == ce - 1 ? ts[2] : ts[0], x, H - (r + 1) * 16f);
        }
    }

    private void drawHazards() {
        for (Hazard h : hazards) {
            if (h.x > scrollX + W + 40f || h.x + 40f < scrollX) continue;
            switch (h.type) {
                case ROCK:
                    batch.draw(rockR, h.x, h.y, 16, 16);
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
            else if (e.type == Enemy.Type.WALKER && e.climb != FloorCrawler.LEVEL) {
                // walking left: tilt 45 degrees front-up while climbing, front-down while dropping
                batch.draw(r, e.x, e.y, e.w / 2f, e.h / 2f, e.w, e.h, 1f, 1f, -e.climb * 45f);
            } else batch.draw(r, e.x, e.y, e.w, e.h);
            if (red) batch.setColor(Color.WHITE);
        }
    }

    private void drawBoss() {
        if (boss.flash > 0f) batch.setColor(1f, 0.55f, 0.55f, 1f);
        if (boss.dying && (int) (boss.dieT * 20f) % 2 == 0) batch.setColor(1f, 1f, 1f, 0.5f);
        batch.draw(bossR, boss.x, boss.y, Boss.W, Boss.H);
        batch.setColor(Color.WHITE);
    }

    private void drawBullets() {
        for (Bullet b : pBullets) {
            switch (b.kind) {
                case LASER: batch.draw(laserR, b.x, b.y, b.w, b.h); break;
                case MISSILE:   // tilted 45 degrees nose-up while climbing, nose-down while dropping
                    if (b.climb == FloorCrawler.LEVEL) batch.draw(missR, b.x, b.y, b.w, b.h);
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

    private static float snapY(float y) { return Math.round(y); }

    private void drawPlayer(Player p) {
        Color tint = p.index == 1 ? P2_TINT : Color.WHITE;
        float px = snapX(p.x), py = snapY(p.y);
        boolean blink = p.invuln > 0f && (int) (p.invuln * 20f) % 2 == 0;
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
        if (p.shield > 0) {
            batch.setColor(1f, 1f, 1f, 0.35f + 0.1f * p.shield);
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

        // boss health bar
        if (boss != null && !boss.dying && !boss.entering) {
            batch.setColor(0.2f, 0f, 0f, 0.8f);
            batch.draw(pix, W / 2f - 80f, H - 30f, 160, 5);
            batch.setColor(1f, 0.25f, 0.2f, 1f);
            batch.draw(pix, W / 2f - 80f, H - 30f, 160f * Math.max(0f, boss.hp / boss.maxHp), 5);
            batch.setColor(Color.WHITE);
        }

        if (game.volumeMsgT > 0f && state != State.PAUSE) {
            text(game.volumeText(), 0, H - 36, 0.55f, Color.YELLOW, W, Align.center);
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
        text((idx + 1) + "P  SHIPS x" + Math.max(0, p.lives), x0, 31, 0.45f, who, width, align);
        for (int i = 0; i < 6; i++) {
            float x = x0 + i * SLOT_W;
            boolean sel = p.meter == i + 1;
            boolean owned = (i == 0 && p.speedLvl > 0) || (i == 1 && p.missile) || (i == 2 && p.weapon == 1)
                    || (i == 3 && p.weapon == 2) || (i == 4 && p.options > 0) || (i == 5 && p.shield > 0);
            if (sel) batch.setColor(1f, 0.85f, 0.1f, 0.9f);
            else if (owned) batch.setColor(0.15f, 0.5f, 0.25f, 0.8f);
            else batch.setColor(0.1f, 0.12f, 0.25f, 0.8f);
            batch.draw(pix, x, 3, SLOT_W - 2f, 14);
            batch.setColor(Color.WHITE);
            String label = METER[i];
            if (i == 0 && p.speedLvl > 0) label += " " + p.speedLvl;
            if (i == 4 && p.options > 0) label += " " + p.options;
            text(label, x, 13.5f, 0.38f, sel ? Color.BLACK : Color.WHITE, SLOT_W - 2f, Align.center);
        }
    }
}
