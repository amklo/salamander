package com.example.salamander;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Pixmap;
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
    private static final int SW = SalamanderGame.SCREEN_W, SH = SalamanderGame.SCREEN_H, HUD_H = SalamanderGame.HUD_H;
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
    private final OrthographicCamera hud = new OrthographicCamera(SW, SH);
    private final FitViewport vp;

    final int levelIdx;
    final Level level;
    /** [0] = player 1, [1] = player 2; null = has not joined. */
    final Player[] players;
    /** Camera bottom-left in world coordinates; scrollT = distance travelled along the level's camera path. */
    float scrollX, scrollY, scrollT, scrollSpeed;
    /** How fast the screen moves sideways right now (px/s): walkers on screen move relative to it, like the ships. */
    float scrollVX;
    /**
     * Where the camera path puts the camera (bottom), and how far the camera has drifted up or down from
     * that to follow the ships (|camOff| <= laneSlack()). scrollY = pathY + camOff.
     */
    float pathY, camOff;
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
    public static final int STAGES = 3;
    /**
     * B on the title screen: boss test. -1 = off, else the stage (0..STAGES-1) whose boss to fight.
     * The game starts at that stage's boss arena; beating the boss (or game over) returns to the title.
     */
    static int bossTest = -1;
    /**
     * S on the title screen: stage select. -1 = off, else the stage (0..STAGES-1) to start at, from checkpoint
     * startCheckpoint (0 = the start of the stage). Dying rewinds to that checkpoint (or a later one reached);
     * clearing the stage carries on to the next one as usual.
     */
    static int startStage = -1, startCheckpoint;
    /** I on the title screen: nothing can hurt the ships (terrain, enemies, bullets, teeth, the boss). */
    static boolean debugInvincible;
    private final ArrayList<Bullet> pBullets = new ArrayList<>();
    private final ArrayList<Enemy> enemies = new ArrayList<>();
    private final ArrayList<Hazard> hazards = new ArrayList<>();

    void addHazard(Hazard h) { hazards.add(h); }
    private final ArrayList<Capsule> caps = new ArrayList<>();
    private final ArrayList<Explosion> fx = new ArrayList<>();
    private final HashMap<Integer, Enemy.Group> groups = new HashMap<>();

    // art
    private final Texture bgTex, starTex;
    private final float capW, capH;                 // power capsule size, from sprites/PowerCapsule.png
    private final TextureRegion[] playerF, optionF, fanF, rushF, walkF, capF, boomF, lavaF;
    private final TextureRegion hudER, hudBoxR;
    private final TextureRegion[] bamdaF, armF;
    private final TextureRegion[] hudSlotR, hudShipR;
    /** Bio turrets: frame 0 points diagonally left, frame 1 points up. Red ones carry a power capsule. */
    private final TextureRegion[] bioRedF, bioBlueF;
    /**
     * Stage 1 walkers: "bio walker red/blue.png", 5 frames played forward then backward (no left / right facing).
     * Red ones carry a power capsule. null on the other stages, which keep walker.png.
     */
    private final TextureRegion[] bioWalkRedF, bioWalkBlueF;
    private static final float BIO_WALK_FPS = 8f;
    /** Enemy bullets spin this many quarter turns per second. */
    private static final float EBULLET_SPIN = 12f;
    /** Celtic Frost (sprites/celtic frost.png). */
    private final TextureRegion frostR;
    /** F.FIELD (sprites/ffield.png): 8x16 frames, looped. null = picture not there yet (drawn as a plain bar). */
    private final TextureRegion[] ffieldF;
    /** sprites/ffield_red.png: the same field once it has Player.FFIELD_LOW hits or fewer left (null = keep ffield.png). */
    private final TextureRegion[] ffieldRedF;
    private static final float FFIELD_FPS = 12f;
    /** Missing Link (sprites/missing_link.png): 7 frames of a barrel roll, looped. */
    private final TextureRegion[] linkF;
    /** Amoeba (sprites/amoeba.png): 3 frames of 16x16, looped. */
    private final TextureRegion[] amoebaF;
    /** Rugal (sprites/rugal.png): level, diving, climbing. */
    private final TextureRegion[] rugalF;
    /** At most this many amoebas float around at once (the towers stop spitting until some are gone). */
    private static final int AMOEBA_MAX = 16;
    private final TextureRegion rockR, shotR, laserR, missR, ebR, shieldR, pix, headR, shaftR, bossR;
    // map stages: pre-rendered terrain strips, brick maze picture, bamda sprites
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
        vp = new FitViewport(SW, SH, cam);
        hud.position.set(SW / 2f, SH / 2f, 0);
        hud.update();

        bgTex = a.tex(level.background != null ? level.background : "bg0");
        starTex = a.tex("stars");
        bgTex.setWrap(Texture.TextureWrap.Repeat, Texture.TextureWrap.Repeat);
        starTex.setWrap(Texture.TextureWrap.Repeat, Texture.TextureWrap.Repeat);
        playerF = a.strip("player", 32);
        // sprites/hud.png, pieces from the original game's HUD: meter slot (empty / highlighted / occupied /
        // highlighted + occupied), ship icons 1P / 2P, "E" icon, yellow box
        Texture hudTex = a.tex("hud");
        bamdaF = loadBamdaFrames();
        armF = a.strip("arm", 16);                      // orange ball, blue ball, claw (floor arm), claw (ceiling arm)
        hudSlotR = new TextureRegion[4];
        for (int i = 0; i < 4; i++) hudSlotR[i] = new TextureRegion(hudTex, i * 16, 0, 14, 6);
        hudShipR = new TextureRegion[]{new TextureRegion(hudTex, 64, 0, 8, 8), new TextureRegion(hudTex, 72, 0, 8, 8)};
        hudER = new TextureRegion(hudTex, 80, 0, 7, 7);
        hudBoxR = new TextureRegion(hudTex, 88, 0, 48, 7);
        optionF = a.strip("option", 12);
        fanF = a.strip("fan", 16);
        rushF = a.strip("rusher", 18);
        walkF = a.strip("walker", 16);
        bioWalkRedF = levelIdx == 0 ? a.strip("bio walker red", 16) : null;
        bioWalkBlueF = levelIdx == 0 ? a.strip("bio walker blue", 16) : null;
        capF = scanFrames("sprites/PowerCapsule.png");   // frames separated by empty columns
        capW = capF[0].getRegionWidth();
        capH = capF[0].getRegionHeight();
        boomF = a.strip("explosion", 24);
        lavaF = a.strip("lava", 8);
        bioRedF = a.strip("bio turret red", 16);
        bioBlueF = a.strip("bio turret blue", 16);
        rockR = a.one("rock");
        shotR = a.one("shot");
        laserR = a.one("laser");
        missR = a.one("missile");
        ebR = a.one("ebullet");
        frostR = a.one("celtic frost");
        ffieldF = Gdx.files.internal("sprites/ffield.png").exists() ? a.strip("ffield", 8) : null;   // drawn as it is (its own transparency)
        ffieldRedF = Gdx.files.internal("sprites/ffield_red.png").exists() ? a.strip("ffield_red", 8) : null;
        linkF = a.strip("missing_link", 16);
        amoebaF = framesOuterBlackKeyed("sprites/amoeba.png", 16);
        rugalF = a.strip("rugal", 16);
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
        for (Level.Tower t : level.towers) {
            if (!masks.containsKey(t.image)) masks.put(t.image, PixelMask.load(t.image));
            if (t.flip && !masks.containsKey(t.image + "#flipV")) masks.put(t.image + "#flipV", masks.get(t.image).flippedV());
        }
        bossMask = PixelMask.load("sprites/boss" + levelIdx + ".png");
        loadBossEye("sprites/boss" + levelIdx + ".png");

        if (bossTest >= 0) {
            checkpointT = level.stopT;               // boss test: start (and restart after dying) at the boss arena
            resetWorld(checkpointT);
        } else if (startStage == levelIdx && startCheckpoint > 0) {   // stage select: start at that checkpoint
            checkpointT = level.checkpoints[Math.min(startCheckpoint, level.checkpoints.length - 1)];
            resetWorld(checkpointT);
        } else {
            resetWorld(0f);
            checkpointT = 0f;
        }
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
        p.fillTrail(40f, p.y - pathY);
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
                if (Controls.pressed(i, Action.CANCEL)) { choosing[i] = -1; a.play("hit", 0.2f); continue; }   // changed their mind
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
        pathY = camTmp[1];
        camOff = 0f;
        scrollY = pathY;
        scrollSpeed = 0f;
        scrollVX = 0f;
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
        for (Level.Bamda r : level.bamdas) {   // floating rocks you can shoot: all use the pulsing bamda
            Hazard h = new Hazard(Hazard.Type.BAMDA);
            h.cx = r.x + r.w / 2f; h.cy = r.y + r.h / 2f;   // the map object marks its centre
            h.phase = MathUtils.random(0f, 4f * Hazard.PULSE_FRAME_TIME);   // not all in step
            h.hp = Hazard.BAMDA_HP;
            h.timer = MathUtils.random(0.6f, 1.8f);          // first shot (like the walkers)
            h.pulse();
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
        for (Level.Tower t : level.towers) {         // green-tipped towers: start inside the rock, grow when on screen
            Hazard h = new Hazard(Hazard.Type.TOWER);
            h.x = t.x; h.baseY = t.y; h.w = t.w; h.h = t.h; h.fromCeil = t.ceiling;
            h.image = t.image; h.flipV = t.flip; h.mask = masks.get(t.flip ? t.image + "#flipV" : t.image);
            h.hp = Hazard.TOWER_HP;
            h.timer = MathUtils.random(0.5f, 1.5f);
            h.y = h.baseY + (h.fromCeil ? 1f : -1f) * h.h;
            hazards.add(h);
        }
        for (Level.Arm r : level.arms) {             // claw arms: base ball at the right end, 16 across / 8 up or down per ball
            Hazard h = new Hazard(Hazard.Type.ARM);
            h.x = r.x; h.y = r.y; h.w = 64; h.h = 40; h.fromCeil = r.ceiling;
            h.anchorX = r.x + 56f; h.anchorY = r.ceiling ? r.y + 32f : r.y + 8f;
            h.initArm(4, 17.9f, h.anchorX, h.anchorY, -16f, r.ceiling ? -8f : 8f, true);
            h.armWeak = 2;                           // the blue ball: the only part that can be shot
            h.hp = Hazard.ARM_HP;
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
            case PAUSE:   // menu: CONTINUE / TITLE SCREEN
                if (updatePauseMenu()) return;          // went back to the title screen
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
                    if (bossTest >= 0) {
                        submitScores();
                        game.setScreen(new TitleScreen(game));   // boss test done
                    } else if (levelIdx < STAGES - 1) {
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

    /** Pause menu entries; pauseSel = the one under the cursor. */
    private static final String[] PAUSE_MENU = {"CONTINUE", "TITLE SCREEN"};
    private int pauseSel;

    /** Returns true when the game was left (back to the title screen). */
    private boolean updatePauseMenu() {
        if (Controls.pressed(Action.PAUSE) || Controls.pressed(Action.CANCEL)) {   // Start / back again: carry on
            unpause();
            return false;
        }
        if (Controls.pressed(Action.UP) || Controls.pressed(Action.DOWN)) {
            pauseSel = (pauseSel + 1) % PAUSE_MENU.length;
            a.play("hit", 0.2f);
        }
        if (Controls.pressed(Action.FIRE) || Controls.pressed(Action.CONFIRM)) {
            if (pauseSel == 0) {
                unpause();
            } else {
                game.bgm.stop();
                submitScores();                          // a hi-score still counts
                game.setScreen(new TitleScreen(game));
                return true;
            }
        }
        return false;
    }

    private void unpause() {
        state = State.PLAY;
        game.bgm.resume();
        for (Player p : players) if (p != null) p.fireCd = Math.max(p.fireCd, 0.15f);   // the confirm press doesn't fire
    }

    private void setState(State s) {
        if (s == State.PAUSE) { game.bgm.pause(); pauseSel = 0; }
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
        float oldX = scrollX, oldY = pathY;
        level.camAt(scrollT, camTmp);
        scrollX = camTmp[0];
        pathY = camTmp[1];
        followShips(dt);
        // ships ride along with the path only; the follow drift must not drag them (or it would chase itself)
        float dS = scrollX - oldX, dSy = pathY - oldY;
        scrollVX = dt > 0f ? dS / dt : 0f;
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
            // a player shot is gone once it is completely off screen (so the on-screen limits really mean on screen)
            if (b.x > scrollX + W || b.x + b.w < scrollX || b.y > scrollY + H || b.y + b.h < scrollY) b.dead = true;
        }
        for (Bullet b : eBullets) {
            b.update(dt, level);
            if (b.x > scrollX + W + 80f || b.x < scrollX - 60f || b.y < scrollY - 30f || b.y > scrollY + H + 60f) b.dead = true;
        }
        for (Enemy e : enemies) {
            e.update(this, dt);
            // vertical limits follow the whole lane, not the view, so enemies the camera drifted away from survive
            float laneB = pathY - laneSlack(), laneT = pathY + H + laneSlack();
            if (e.x + e.w < scrollX - 40f || e.x > scrollX + W + 300f || e.y < laneB - 60f || e.y > laneT + 60f) {
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
            c.t += dt;                               // stays where it was dropped: only the scrolling moves it
        }
        caps.removeIf(c -> c.x < scrollX - capW - 8f);
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
     * Brick maze: every brick that was shot away starts to materialize after Level.BRICK_REGROW_TIME seconds
     * and is solid again Level.BRICK_GROW_TIME seconds later. While it materializes it is harmless (shots can
     * blast it away again). It only turns solid once no ship is inside its spot.
     */
    private void regrowBricks(float dt) {
        if (level.bricks == null) return;
        float s = level.cell;
        for (int i = 0; i < level.bricks.length; i++) {
            if (level.bricks[i]) continue;
            if (level.brickGrow[i] > 0f) {                          // materializing
                level.brickGrow[i] = Math.min(1f, level.brickGrow[i] + dt / Level.BRICK_GROW_TIME);
                if (level.brickGrow[i] < 1f) continue;
                float bx = (i % level.gCols) * s, by = (i / level.gCols) * s;
                boolean occupied = false;
                for (Player p : players) {
                    if (alive(p) && overlap(p.x, p.y, Player.W, Player.H, bx - 2f, by - 2f, s + 4f, s + 4f)) occupied = true;
                }
                if (!occupied) { level.bricks[i] = true; level.brickGrow[i] = 0f; }   // else waits, fully visible
                continue;
            }
            if (level.brickRegrow[i] <= 0f) continue;
            level.brickRegrow[i] -= dt;
            if (level.brickRegrow[i] <= 0f) level.brickGrow[i] = 0.001f;   // starts to materialize
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

    /** How far the camera may drift up / down from the path (0 when the level's lane fits the view). */
    private float laneSlack() { return Math.max(0f, (level.laneH - H) / 2f); }

    /** Max camera drift speed when following the ships up and down (px/s). */
    private static final float FOLLOW_SPEED = 110f;

    /**
     * The 168px view slides within the 272px lane the levels were designed for: the ships' height in the
     * lane maps onto the drift, so a ship at the lane's top edge sees the top of the lane, and so on.
     */
    private void followShips(float dt) {
        float sum = 0f;
        int n = 0;
        for (Player p : players) {
            if (!alive(p)) continue;
            sum += p.y + Player.H / 2f;
            n++;
        }
        float target = camOff;
        if (n > 0) {
            float rel = sum / n - (pathY + H / 2f);          // ships' height relative to the lane centre
            float slack = laneSlack();
            target = slack <= 0f ? 0f : MathUtils.clamp(rel * slack / (level.laneH / 2f), -slack, slack);
        }
        float step = FOLLOW_SPEED * dt;
        camOff += MathUtils.clamp(target - camOff, -step, step);
        scrollY = MathUtils.clamp(pathY + camOff, 0f, Math.max(0f, level.worldH - H));
        camOff = scrollY - pathY;
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
        p.x = MathUtils.clamp(p.x, scrollX - Player.ART_L, scrollX + W - Player.ART_R);   // the ship may touch every edge
        p.y = MathUtils.clamp(p.y, scrollY - Player.ART_B, scrollY + H - Player.ART_T);
        p.tilt = dy > 0f ? 1 : (dy < 0f ? 2 : 0);

        p.invuln = Math.max(0f, p.invuln - dt);
        p.ghostT = Math.max(0f, p.ghostT - dt);
        p.shieldHitT = Math.max(0f, p.shieldHitT - dt);
        p.ffieldHitT = Math.max(0f, p.ffieldHitT - dt);
        p.fireCd -= dt;
        p.missCd -= dt;

        // Options follow the ship's path. Only record when the ship actually moved on screen,
        // so the options stay where they are while the ship stands still.
        p.trailAcc += dt;
        while (p.trailAcc >= 1f / 60f) {
            float sx = p.x - scrollX, sy = p.y - pathY;     // path-relative, so options ride along with the camera path
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
    float optY(Player p, int i) { return pathY + p.trailY(10 * (i + 1)) + 2f; }

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
        // down missiles leave from the bottom of the ship / option, 2-WAY's up missiles from the top
        float mh = missR.getRegionHeight();
        boolean fired = launchFrom(p, 0, p.x + 14f, p.y + Player.ART_B, p.y + Player.ART_T - mh);
        for (int i = 0; i < p.options; i++) fired |= launchFrom(p, i + 1, optX(p, i) + 2f, optY(p, i), optY(p, i) + 12f - mh);
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
            if (b.src == 0) b.y = p.y + NOSE_Y - b.h / 2f;
            else if (b.src <= p.options) b.y = optY(p, b.src - 1) + 6f - b.h / 2f;
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
        float lh = laserR.getRegionHeight();   // beam thickness (and hit height) from sprites/laser.png
        Bullet b = new Bullet(Bullet.Kind.LASER, nx - Bullet.LASER_START_LEN, ny - lh / 2f, Bullet.LASER_START_LEN, lh);
        b.dmg = Bullet.LASER_DAMAGE;
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

    // Shots take their size from sprites/shot.png (the picture is the hitbox), tip at the muzzle.
    private void fireShot(Player p, int src, float nx, float ny) {
        float sw = shotR.getRegionWidth(), sh = shotR.getRegionHeight();
        Bullet b = new Bullet(Bullet.Kind.SHOT, nx - sw, ny - sh / 2f, sw, sh);
        b.vx = Bullet.SHOT_SPEED;
        b.owner = p; b.src = src;
        pBullets.add(b);
    }

    /** DOUBLE's extra shot, in the direction chosen with AIM. */
    private void fireAngled(Player p, int src, float nx, float ny) {
        float ang = p.doubleDir * MathUtils.PI / 4f;
        float s = shotR.getRegionWidth();               // square hitbox as wide as the shot, so any angle is covered
        Bullet d = new Bullet(Bullet.Kind.SHOT, nx - s, ny - s / 2f, s, s);
        d.vx = MathUtils.cos(ang) * Bullet.SHOT_SPEED;      // same speed as the normal shot, in the aimed direction
        d.vy = MathUtils.sin(ang) * Bullet.SHOT_SPEED;
        d.owner = p; d.src = src; d.angled = true;
        pBullets.add(d);
    }

    private void launchMissile(Player p, int src, float x, float y, boolean up) {
        Bullet m = new Bullet(Bullet.Kind.MISSILE, x, y, missR.getRegionWidth(), missR.getRegionHeight());   // size of missile.png
        m.vx = Bullet.MISSILE_SPEED;                        // speed: Bullet.MISSILE_SPEED (45 degree dive)
        m.vy = up ? Bullet.MISSILE_SPEED : -Bullet.MISSILE_SPEED;
        m.dmg = 3;
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
            case Player.FFIELD: if (p.ffield <= 0) { p.ffield = Player.FFIELD_HITS; ok = true; } break;
            case Player.EXTRA: if (p.lives < Player.MAX_LIVES) { p.lives++; ok = true; } break;   // "!": one more ship
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
            case RUGAL:                                   // comes in from the right edge, at its height from Tiled
                e = new Enemy(Enemy.Type.RUGAL, scrollX + W + 8f, s.y - 8f, levelIdx);
                break;
            case LINK:                                    // enters at the right edge at its height from Tiled
                e = new Enemy(Enemy.Type.LINK, scrollX + W + 8f, s.y - 8f, levelIdx);
                e.phase = s.phase;
                break;
            case FROST:                                   // the path is in screen coordinates (see Enemy.FROST_PATH)
                e = new Enemy(Enemy.Type.FROST, scrollX + W + 16f, scrollY, levelIdx);
                e.w = frostR.getRegionWidth(); e.h = frostR.getRegionHeight();
                e.upper = s.upper;
                e.t = -s.delay;
                {   // the wave enters where it was placed in Tiled (its height on the screen), and leaves mirrored
                    float f = (s.y - scrollY) / H;
                    e.frostLow = MathUtils.clamp(s.upper ? 1f - f : f, Enemy.FROST_LOW_MIN, Enemy.FROST_LOW_MAX);
                }
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
            case TURRET_CEIL: {
                // stays where it was placed in Tiled if that is within 8 px of the ceiling (the 8 px collision cells
                // don't follow the rock art exactly), else it snaps onto the ceiling
                float placed = s.y + 4f - 16f, snapped = level.ceilBottom(s.x + 8f, s.y) - 16f;
                e = new Enemy(Enemy.Type.TURRET_CEIL, s.x, Math.abs(placed - snapped) <= 8f ? placed : snapped, levelIdx);
                break;
            }
            default: {
                float placed = s.y - 4f, snapped = level.floorTop(s.x + 8f, s.y);
                e = new Enemy(Enemy.Type.TURRET_FLOOR, s.x, Math.abs(placed - snapped) <= 8f ? placed : snapped, levelIdx);
                break;
            }
        }
        e.group = grp;
        enemies.add(e);
    }

    /**
     * sprites/bamda.png: the pulse frames side by side (separated by empty columns), each trimmed to its
     * own height. Also tells Hazard the frame sizes, so the hit box pulses with the picture.
     */
    private TextureRegion[] loadBamdaFrames() {
        TextureRegion[] f = scanFrames("sprites/bamda.png");
        Hazard.bamdaW = new float[f.length];
        Hazard.bamdaH = new float[f.length];
        for (int i = 0; i < f.length; i++) {
            Hazard.bamdaW[i] = f[i].getRegionWidth();
            Hazard.bamdaH[i] = f[i].getRegionHeight();
        }
        return f;
    }

    /**
     * Frames of a fixed width from a picture drawn on solid black: the black around each figure (connected to the
     * frame's edge) becomes see-through, black enclosed by the figure stays. A picture that already has
     * transparency is used as it is.
     */
    private TextureRegion[] framesOuterBlackKeyed(String file, int frameW) {
        Pixmap pm = new Pixmap(Gdx.files.internal(file));
        if (pm.getFormat() != Pixmap.Format.RGBA8888) {
            Pixmap c = new Pixmap(pm.getWidth(), pm.getHeight(), Pixmap.Format.RGBA8888);
            c.setBlending(Pixmap.Blending.None);
            c.drawPixmap(pm, 0, 0);
            pm.dispose();
            pm = c;
        }
        pm.setBlending(Pixmap.Blending.None);
        int W = pm.getWidth(), H = pm.getHeight();
        boolean[] seen = new boolean[W * H];
        java.util.ArrayDeque<int[]> q = new java.util.ArrayDeque<>();
        for (int f = 0; f * frameW < W; f++) {
            int x0 = f * frameW, x1 = Math.min(W, x0 + frameW) - 1;
            for (int x = x0; x <= x1; x++) { q.add(new int[]{x, 0, x0, x1}); q.add(new int[]{x, H - 1, x0, x1}); }
            for (int y = 0; y < H; y++) { q.add(new int[]{x0, y, x0, x1}); q.add(new int[]{x1, y, x0, x1}); }
        }
        while (!q.isEmpty()) {                                  // flood the black from each frame's border
            int[] c = q.poll();
            int x = c[0], y = c[1];
            if (x < c[2] || x > c[3] || y < 0 || y >= H || seen[y * W + x]) continue;
            int px = pm.getPixel(x, y);
            boolean blackOrClear = (px & 0xff) == 0 || (px >>> 8) == 0;
            if (!blackOrClear) continue;
            seen[y * W + x] = true;
            pm.drawPixel(x, y, 0);
            q.add(new int[]{x + 1, y, c[2], c[3]}); q.add(new int[]{x - 1, y, c[2], c[3]});
            q.add(new int[]{x, y + 1, c[2], c[3]}); q.add(new int[]{x, y - 1, c[2], c[3]});
        }
        Texture tex = new Texture(pm);
        tex.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        pm.dispose();
        int n = Math.max(1, W / frameW);
        TextureRegion[] fr = new TextureRegion[n];
        for (int i = 0; i < n; i++) fr[i] = new TextureRegion(tex, i * frameW, 0, frameW, H);
        return fr;
    }

    /** Frames side by side in one picture, separated by empty columns; each frame is trimmed to its own height. */
    private TextureRegion[] scanFrames(String file) {
        Pixmap pm = new Pixmap(Gdx.files.internal(file));
        ArrayList<int[]> frames = new ArrayList<>();          // {x, y, w, h}
        int start = -1;
        for (int x = 0; x <= pm.getWidth(); x++) {
            boolean empty = true;
            if (x < pm.getWidth()) for (int y = 0; y < pm.getHeight() && empty; y++) if ((pm.getPixel(x, y) & 0xff) != 0) empty = false;
            if (!empty && start < 0) start = x;
            if (empty && start >= 0) {
                int top = pm.getHeight(), bottom = -1;
                for (int y = 0; y < pm.getHeight(); y++)
                    for (int xx = start; xx < x; xx++)
                        if ((pm.getPixel(xx, y) & 0xff) != 0) { top = Math.min(top, y); bottom = Math.max(bottom, y); }
                frames.add(new int[]{start, top, x - start, bottom - top + 1});
                start = -1;
            }
        }
        pm.dispose();
        Texture tex = a.texFile(file);
        TextureRegion[] f = new TextureRegion[frames.size()];
        for (int i = 0; i < f.length; i++) {
            int[] r = frames.get(i);
            f[i] = new TextureRegion(tex, r[0], r[1], r[2], r[3]);
        }
        return f;
    }

    // ---- boss eye: "sprites/boss eye.png" holds the frames side by side (closed -> open), separated by
    // empty columns. The boss sprite marks the eye's centre with a small pure green (0,255,0) square.
    private TextureRegion[] eyeF;
    private float eyeOX, eyeOY;
    private static final String EYE_FILE = "sprites/boss eye.png";

    private void loadBossEye(String bossPath) {
        eyeF = null;
        if (!Gdx.files.internal(EYE_FILE).exists()) return;
        Pixmap pm = new Pixmap(Gdx.files.internal(bossPath));
        int gx0 = Integer.MAX_VALUE, gy0 = Integer.MAX_VALUE, gx1 = -1, gy1 = -1;
        for (int y = 0; y < pm.getHeight(); y++)
            for (int x = 0; x < pm.getWidth(); x++) {
                int c = pm.getPixel(x, y);
                int r = (c >>> 24) & 0xff, g = (c >>> 16) & 0xff, b = (c >>> 8) & 0xff, al = c & 0xff;
                if (al > 200 && g > 200 && r < 60 && b < 60) { gx0 = Math.min(gx0, x); gy0 = Math.min(gy0, y); gx1 = Math.max(gx1, x); gy1 = Math.max(gy1, y); }
            }
        int bossH = pm.getHeight();
        pm.dispose();
        if (gx1 < 0) return;                        // no green marker: this boss has no eye

        Pixmap ep = new Pixmap(Gdx.files.internal(EYE_FILE));
        ArrayList<int[]> runs = new ArrayList<>();   // {x, width} of each frame
        int start = -1;
        for (int x = 0; x <= ep.getWidth(); x++) {
            boolean empty = true;
            if (x < ep.getWidth()) for (int y = 0; y < ep.getHeight() && empty; y++) if ((ep.getPixel(x, y) & 0xff) != 0) empty = false;
            if (!empty && start < 0) start = x;
            if (empty && start >= 0) { runs.add(new int[]{start, x - start}); start = -1; }
        }
        int eh = ep.getHeight();
        ep.dispose();
        if (runs.isEmpty()) return;
        Texture et = a.texFile(EYE_FILE);
        eyeF = new TextureRegion[runs.size()];
        for (int i = 0; i < eyeF.length; i++) eyeF[i] = new TextureRegion(et, runs.get(i)[0], 0, runs.get(i)[1], eh);
        // centre the eye on the marker (sprite rows run top-down, world y up)
        float cx = (gx0 + gx1 + 1) / 2f, cy = (gy0 + gy1 + 1) / 2f;
        eyeOX = Math.round(cx - eyeF[0].getRegionWidth() / 2f);
        eyeOY = bossH - Math.round(cy + eh / 2f);
    }

    /** Does the rectangle touch the boss: its sprite's solid pixels, or its eye (drawn over a see-through gap)? */
    private boolean touchesBoss(float x, float y, float w, float h) {
        if (!overlap(x, y, w, h, boss.x, boss.y, boss.w, boss.h)) return false;
        if (boss.hasEye && overlap(x, y, w, h, boss.x + boss.eyeX, boss.y + boss.eyeY, boss.eyeW, boss.eyeH)) return true;
        return boss.mask == null || boss.mask.overlaps(boss.x, boss.y, x, y, w, h);
    }

    private void spawnBoss() {
        bossSpawned = true;
        int[] hp = {70, 100, 140};
        boss = new Boss(levelIdx, scrollX + W + 20f, hp[levelIdx], bossR.getRegionWidth(), bossR.getRegionHeight());
        boss.mask = bossMask;
        if (levelIdx == 0) {
            // the brain's two arms, as attached in the original map (sprite 100x100, y up from its bottom):
            // {anchor x, anchor y, direction x, direction y, ball spacing}
            boss.armSpec = new float[][]{
                    {29f, 96f, -16f, -16f, 22.6f},   // from the top left, down at 45 degrees
                    {37f, 32f, -16f, -8f, 17.9f},    // from under the eye, down and to the left
            };
        }
        if (eyeF != null) {
            boss.hasEye = true;
            boss.eyeFrames = eyeF.length;
            boss.eyeX = eyeOX; boss.eyeY = eyeOY;
            boss.eyeW = eyeF[0].getRegionWidth(); boss.eyeH = eyeF[0].getRegionHeight();
        }
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

    /** Is the point (x, y) on screen, at least `margin` px inside its edges? Things off screen never shoot. */
    boolean inView(float x, float y, float margin) {
        return x >= scrollX + margin && x <= scrollX + W - margin && y >= scrollY + margin && y <= scrollY + H - margin;
    }

    /** A tower spits out an amoeba at (x, y) (bottom-left), thrown with (vx, vy). */
    void spawnAmoeba(float x, float y, float vx, float vy) {
        int n = 0;
        for (Enemy e : enemies) if (!e.dead && e.type == Enemy.Type.AMOEBA) n++;
        if (n >= AMOEBA_MAX) return;
        Enemy e = new Enemy(Enemy.Type.AMOEBA, x, y, levelIdx);
        e.vx = vx; e.vy = vy;
        e.phase = MathUtils.random(0f, MathUtils.PI2);
        enemies.add(e);
    }

    /** Fires at the nearest player (straight left if nobody is flying). Nothing is fired from off screen. */
    void aimedShot(float cx, float cy, float speed, float offset) {
        if (!inView(cx, cy, 0f)) return;
        Player p = target(cx, cy);
        float ang = p == null ? MathUtils.PI : MathUtils.atan2(p.y + 8f - cy, p.x + 16f - cx);
        enemyBullet(cx, cy, ang + offset, speed);
    }

    void enemyBullet(float cx, float cy, float ang, float speed) {
        float bw = ebR.getRegionWidth(), bh = ebR.getRegionHeight();   // size (and hit box) from sprites/ebullet.png
        Bullet b = new Bullet(Bullet.Kind.ENEMY, cx - bw / 2f, cy - bh / 2f, bw, bh);
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

    /** A tower's tip is shot to pieces: the column crumbles. */
    private void destroyTower(Hazard h, Player owner) {
        h.dead = true;
        addScore(owner, ARM_SCORE);
        for (float yy = h.fromCeil ? h.y + 6f : h.y + h.h - 6f; h.fromCeil ? yy < h.y + h.h - Hazard.TOWER_ROOT : yy > h.y + Hazard.TOWER_ROOT; yy += h.fromCeil ? 12f : -12f) {
            boom(h.x + h.w / 2f, yy, 0.8f);
        }
        a.play("boom", 0.3f);
    }

    /** A stage arm's blue ball is shot to pieces: the whole arm bursts. */
    private void destroyArm(Hazard h, Player owner) {
        h.dead = true;
        addScore(owner, ARM_SCORE);
        for (int i = 0; i < h.armX.length; i++) boom(h.armX[i], h.armY[i], 0.9f);
        a.play("boom", 0.3f);
    }

    private static final int ARM_SCORE = 500;

    /** A missile that hit something bursts where it is (shots and lasers show a hit spark instead). */
    private void missileBurst(Bullet b) {
        if (b.kind == Bullet.Kind.MISSILE) boom(b.x, b.y, 0.6f);
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
            // only the part of a shot that is on screen can hit anything: a long laser or a ring reaching past
            // the edge must not hurt enemies that haven't appeared yet
            float bx = Math.max(b.x, scrollX), by = Math.max(b.y, scrollY);
            float bw = Math.min(b.x + b.w, scrollX + W) - bx, bh = Math.min(b.y + b.h, scrollY + H) - by;
            if (bw <= 0f || bh <= 0f) continue;
            // brick maze: every weapon blasts the blocks it touches (lasers keep going)
            float reach = b.kind == Bullet.Kind.MISSILE ? 3f : 0f;
            if (level.breakBricks(bx, by - reach, bw + reach, bh + 2 * reach) > 0) {
                addScore(b.owner, 10);
                boom(b.x + b.w, b.y + b.h / 2f, 0.6f);
                a.play("hit", 0.15f);
                if (!laser) { b.dead = true; continue; }
            }
            if (b.kind == Bullet.Kind.RIPPLE) {
                // a ripple is only stopped by terrain at its core, so a big ring can pass close to walls
                if (level.hits(bx, b.y + b.h / 2f - 2f, bw, 4f)) { b.dead = true; continue; }
            } else if (b.kind != Bullet.Kind.MISSILE && level.hits(bx, by, bw, bh)) { b.dead = true; continue; }

            // towers: the green tip can be shot; the rock column just stops shots
            boolean towerHit = false;
            for (Hazard h : hazards) {
                if (h.type != Hazard.Type.TOWER || h.dead) continue;
                if (h.towerTipHit(bx, by, bw, bh)) {
                    if (laser) { if (b.hits.contains(h)) continue; b.hits.add(h); } else b.dead = true;
                    towerHit = true;
                    h.hp -= b.dmg;
                    float ty = h.fromCeil ? h.y : h.y + h.h - Hazard.TOWER_TIP;
                    if (h.hp <= 0) destroyTower(h, b.owner);
                    else { a.play("hit", 0.15f); hitSpark(b, h.x, ty, h.w, Hazard.TOWER_TIP); }
                } else if (h.touches(bx, by, bw, bh)) {
                    b.dead = true;
                }
                if (b.dead) break;
            }
            if (b.dead) { if (towerHit) missileBurst(b); continue; }

            // claw arms: a stage arm's blue ball, or any blue ball of a boss arm, can be damaged; the rest just stop shots
            boolean armHit = false;
            for (Hazard h : hazards) {
                if (h.type != Hazard.Type.ARM || h.dead) continue;
                int ball = h.armBallAt(bx, by, bw, bh);
                if (ball < 0) continue;
                boolean weak = h.bossArm ? ball < h.armX.length - 1 : ball == h.armWeak;   // (boss arm: not the claw)
                if (!weak) { b.dead = true; break; }
                if (laser) { if (b.hits.contains(h)) continue; b.hits.add(h); } else b.dead = true;
                armHit = true;
                h.hp -= b.dmg;
                if (h.hp <= 0) destroyArm(h, b.owner);
                else { a.play("hit", 0.15f); hitSpark(b, h.armX[ball] - 8f, h.armY[ball] - 8f, 16f, 16f); }
                if (b.dead) break;
            }
            if (b.dead) { if (armHit) missileBurst(b); continue; }

            for (Hazard h : hazards) {
                if ((h.type == Hazard.Type.CRUSHER && overlap(bx, by, bw, bh, h.x, h.y, h.w, h.h))
                        || (h.type == Hazard.Type.FANG && h.touches(bx, by, bw, bh))) {
                    b.dead = true;
                    break;
                }
            }
            if (b.dead) continue;                          // teeth / crushers just stop shots (no burst: they take no damage)

            for (Hazard h : hazards) {
                if (!h.shootable() || h.dead || !overlap(bx, by, bw, bh, h.x, h.y, h.w, h.h)) continue;
                if (laser) { if (b.hits.contains(h)) continue; b.hits.add(h); } else b.dead = true;
                h.hp -= b.dmg;
                if (h.hp <= 0) { h.dead = true; addScore(b.owner, 50); boom(h.x + h.w / 2f, h.y + h.h / 2f, h.w / 20f); a.play("boom", 0.2f); }
                else { a.play("hit", 0.15f); hitSpark(b, h.x, h.y, h.w, h.h); }
                if (b.dead) break;
            }
            if (b.dead) { missileBurst(b); continue; }    // a missile bursts on a Bamda / falling rock it hit

            for (Enemy e : enemies) {
                if (e.dead || !overlap(bx, by, bw, bh, e.x, e.y, e.w, e.h)) continue;
                if (laser) { if (b.hits.contains(e)) continue; b.hits.add(e); } else b.dead = true;
                e.hp -= b.dmg;
                if (e.hp <= 0) killEnemy(e, b.owner);
                else { a.play("hit", 0.15f); hitSpark(b, e.x, e.y, e.w, e.h); }
                if (b.dead) break;
            }
            if (b.dead) { if (b.kind == Bullet.Kind.MISSILE) boom(b.x, b.y, 0.6f); continue; }

            if (boss != null && !boss.dying && touchesBoss(bx, by, bw, bh)) {
                // Bosses with an eye only take damage on the eye, and not while it is fully closed;
                // other bosses take damage anywhere. A laser hurts it once per beam.
                // After each hit the boss can't lose HP for Boss.HIT_COOLDOWN; shots that land then are absorbed.
                float ex = boss.x + boss.eyeX, ey = boss.y + boss.eyeY;
                boolean onEye = boss.hasEye && overlap(bx, by, bw, bh, ex, ey, boss.eyeW, boss.eyeH);
                boolean weak = !boss.hasEye || (onEye && !boss.eyeShut());
                if (!weak || boss.hitCooldown > 0f) {
                    if (!laser) {      // a laser keeps going and may still hurt it a moment later
                        b.dead = true;
                        if (onEye) hitSpark(b, ex, ey, boss.eyeW, boss.eyeH); else hitSpark(b, boss.x, boss.y, boss.w, boss.h);
                    }
                    if (b.dead && b.kind == Bullet.Kind.MISSILE) boom(b.x, b.y, 0.6f);
                    continue;
                }
                if (laser) { if (b.hits.contains(boss)) continue; b.hits.add(boss); } else b.dead = true;
                boss.hp = Math.max(0f, boss.hp - b.dmg);
                boss.hitCooldown = Boss.HIT_COOLDOWN;
                boss.flash = 0.08f;
                a.play("hit", 0.15f);
                if (boss.hasEye) boss.onEyeHit();
                if (boss.hp > 0) {
                    if (boss.hasEye) hitSpark(b, ex, ey, boss.eyeW, boss.eyeH); else hitSpark(b, boss.x, boss.y, boss.w, boss.h);
                }
                if (boss.hp <= 0) {
                    boss.dying = true;
                    boss.explodeArms(this);
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
            for (Player p : players) {   // F.FIELD in front of a ship: blocks the shot (and wears down)
                if (alive(p) && p.ffield > 0
                        && overlap(b.x, b.y, b.w, b.h, p.ffieldX(), p.ffieldY(), Player.FFIELD_W, Player.FFIELD_H)) {
                    b.dead = true;
                    boom(b.x + b.w / 2f, b.y + b.h / 2f, 0.4f);
                    if (p.invuln <= 0f) ffieldHit(p);   // shots that land during the invincibility don't wear it down
                    break;
                }
            }
            if (b.dead) continue;
            for (Player p : players) {
                if (alive(p) && overlap(b.x, b.y, b.w, b.h, p.hitX(), p.hitY(), Player.HIT_W, Player.HIT_H)) {
                    b.dead = true;
                    hurt(p, false);   // got past the F.FIELD (from behind / above / below)
                    break;
                }
            }
        }

        // ---- each ship vs everything else
        for (Player p : players) if (alive(p)) collideShip(p);
    }

    /**
     * What the F.FIELD (its own 8x16 picture in front of the nose) touches: an Enemy, a Hazard (Bamda / rock or claw
     * arm), the Boss, or null.
     */
    private Object ffieldTouches(Player p) {
        float fx = p.ffieldX(), fy = p.ffieldY(), fw = Player.FFIELD_W, fh = Player.FFIELD_H;
        for (Enemy e : enemies) if (!e.dead && overlap(fx, fy, fw, fh, e.x + 2f, e.y + 2f, e.w - 4f, e.h - 4f)) return e;
        for (Hazard h : hazards) {
            if (h.dead) continue;
            if (h.type == Hazard.Type.ARM && h.armTouches(fx, fy, fw, fh)) return h;
            if (h.shootable() && overlap(fx, fy, fw, fh, h.x + 2f, h.y + 2f, h.w - 4f, h.h - 4f)) return h;
        }
        return boss != null && !boss.dying && touchesBoss(fx, fy, fw, fh) ? boss : null;
    }

    /** The shield or the F.FIELD took a hit from this: it takes 1 damage in return (enemies and Bamdas; arms and the boss shrug it off). */
    private void guardDamage(Object o, Player p) {
        if (o instanceof Enemy) {
            Enemy e = (Enemy) o;
            if (e.dead) return;
            e.hp -= 1;
            if (e.hp <= 0) killEnemy(e, p);
            else boom(e.x + e.w / 2f, e.y + e.h / 2f, 0.4f);
        } else if (o instanceof Hazard) {
            Hazard h = (Hazard) o;
            if (h.dead || !h.shootable()) return;
            h.hp -= 1;
            if (h.hp <= 0) { h.dead = true; addScore(p, 50); boom(h.x + h.w / 2f, h.y + h.h / 2f, h.w / 20f); a.play("boom", 0.2f); }
            else boom(h.x + h.w / 2f, h.y + h.h / 2f, 0.4f);
        }
    }

    private void collideShip(Player p) {
        float hx = p.hitX(), hy = p.hitY(), hw = Player.HIT_W, hh = Player.HIT_H;
        boolean ghost = p.ghostT > 0f;   // just (re)entered: passes through terrain, teeth and crushers
        if (!ghost && level.hits(hx, hy, hw, hh)) { hurt(p, true); return; }
        // F.FIELD: whatever runs into the field itself wears it down (and gives the ship a moment of invincibility)
        if (p.ffield > 0 && p.invuln <= 0f && !debugInvincible) {
            Object hit = ffieldTouches(p);
            if (hit != null) { ffieldHit(p); guardDamage(hit, p); }   // ...and what ran into it takes 1 damage
        }
        for (Enemy e : enemies) {
            if (!e.dead && overlap(hx, hy, hw, hh, e.x + 2f, e.y + 2f, e.w - 4f, e.h - 4f)) {
                boolean shieldTakesIt = p.shield > 0 && p.invuln <= 0f && !debugInvincible;
                hurt(p, false);
                if (p.dead) return;
                if (shieldTakesIt) guardDamage(e, p);       // the shield hits back: 1 damage
            }
        }
        for (Hazard h : hazards) {
            if (h.dead) continue;
            if (!ghost && h.type == Hazard.Type.CRUSHER && overlap(hx, hy, hw, hh, h.x, h.y, h.w, h.h)) { hurt(p, true); return; }
            if (!ghost && h.type == Hazard.Type.FANG && h.touches(hx, hy, hw, hh)) { hurt(p, true); return; }
            if (!ghost && h.type == Hazard.Type.TOWER && h.touches(hx, hy, hw, hh)) { hurt(p, true); return; }
            if (h.type == Hazard.Type.ARM && h.armTouches(hx, hy, hw, hh)) { hurt(p, false); if (p.dead) return; }   // caught
            if (h.shootable() && overlap(hx, hy, hw, hh, h.x + 2f, h.y + 2f, h.w - 4f, h.h - 4f)) {
                h.dead = true;
                boom(h.x + h.w / 2f, h.y + h.h / 2f, h.w / 20f);
                hurt(p, false);
                if (p.dead) return;
            }
        }
        if (boss != null && touchesBoss(hx, hy, hw, hh)) {
            hurt(p, false);
            if (p.dead) return;
        }
        for (Capsule c : caps) {
            if (!c.dead() && overlap(p.x + 2f, p.y, 28f, 16f, c.x, c.y, capW, capH)) {
                c.x = -99999f;   // consumed (culled next frame)
                p.score += 300;
                p.meter = p.meter >= Player.SLOT_COUNT ? 1 : p.meter + 1;   // only the player who caught it advances
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
                c.x = e.x + e.w / 2f - capW / 2f; c.y = e.y + e.h / 2f - capH / 2f; c.y0 = c.y;
                caps.add(c);
            }
        }
    }

    /** Invincibility after the shield or the F.FIELD takes a hit. */
    private static final float GUARD_IFRAMES = 0.6f;

    /**
     * The F.FIELD takes a hit (a blocked shot, or an enemy rammed): it wears down by one and the ship is invincible
     * to enemies and shots for a moment, like with the shield (terrain still hurts).
     */
    private void ffieldHit(Player p) {
        p.ffield--;
        p.invuln = Math.max(p.invuln, GUARD_IFRAMES);
        p.ffieldHitT = GUARD_IFRAMES;   // the field flickers (the ship doesn't blink)
        a.play("hit", 0.3f);
    }

    private void hurt(Player p, boolean ignoreShield) {   // (the F.FIELD only reacts to what touches the field itself)
        if (debugInvincible) return;                 // debug: invincible
        if (p.dead || (boss != null && boss.dying)) return;
        if (!ignoreShield) {
            if (p.invuln > 0f) return;
            if (p.shield > 0) {
                p.shield--;
                p.invuln = GUARD_IFRAMES;
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
        // the playfield fills the screen above the HUD strip: world scrollY lands on screen row HUD_H
        cam.position.set(Math.round(scrollX) + SW / 2f, Math.round(scrollY) + H - SH / 2f, 0f);
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
                    int i = r * level.gCols + c;
                    float alpha = level.bricks[i] ? 1f : level.brickGrow[i];   // materializing bricks fade in
                    if (alpha <= 0f) continue;
                    float x = c * s, y = r * s;
                    if (alpha < 1f) batch.setColor(1f, 1f, 1f, alpha);
                    batch.draw(brickTex, x, y, s, s, x / p, -y / p, (x + s) / p, -(y + s) / p);
                    if (alpha < 1f) batch.setColor(Color.WHITE);
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
            if ((h.type != Hazard.Type.FANG && h.type != Hazard.Type.TOWER) || h.x > scrollX + W + 40f || h.x + h.w < scrollX) continue;
            if (h.flipV) batch.draw(a.texFile(h.image), h.x, h.y + h.h, h.w, -h.h);   // flipped in Tiled
            else batch.draw(a.texFile(h.image), h.x, h.y, h.w, h.h);
        }
    }

    /** Claw arm: base and orange ball, blue ball, then the claw turned toward where it reaches. */
    private void drawArm(Hazard h) {
        int n = h.armX.length - 1;
        for (int i = 0; i < n; i++) {
            if (!h.armBallOut(i)) continue;
            TextureRegion r = armF[h.bossArm || i == h.armWeak ? 1 : 0];   // blue ball(s), else orange
            batch.draw(r, Math.round(h.armX[i] - 8f), Math.round(h.armY[i] - 8f));
        }
        float dx = h.armX[n] - h.armX[n - 1], dy = h.armY[n] - h.armY[n - 1];
        float turn = dx * dx + dy * dy < 0.01f ? 0f        // still inside the boss: as it rests
                : (MathUtils.atan2(dy, dx) - MathUtils.atan2(h.restDY, h.restDX)) * MathUtils.radiansToDegrees;
        batch.draw(armF[h.fromCeil || h.bossArm ? 3 : 2], Math.round(h.armX[n] - 8f), Math.round(h.armY[n] - 8f), 8f, 8f, 16f, 16f, 1f, 1f, turn);
    }

    private void drawHazards() {
        for (Hazard h : hazards) {
            if (h.type == Hazard.Type.FANG || h.type == Hazard.Type.TOWER) continue;   // drawn behind the terrain
            if (h.x > scrollX + W + 40f || h.x + Math.max(40f, h.w + 60f) < scrollX) continue;
            switch (h.type) {
                case ROCK:
                    batch.draw(rockR, h.x, h.y, 16, 16);
                    break;
                case ARM:
                    if (!h.bossArm) drawArm(h);              // boss arms are drawn with the boss, in front of it
                    break;
                case BAMDA:
                    batch.draw(bamdaF[h.frame], h.x, h.y);
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
        for (Capsule c : caps) {
            TextureRegion f = capF[(int) (c.t * 6f) % capF.length];
            batch.draw(f, c.x, c.y, f.getRegionWidth(), f.getRegionHeight());
        }
    }

    private void drawEnemies() {
        for (Enemy e : enemies) {
            TextureRegion r;
            switch (e.type) {
                case FAN: r = fanF[(int) (e.t * 10f) % fanF.length]; break;
                case RUSHER: r = rushF[(int) (e.t * 12f) % rushF.length]; break;
                case WALKER: r = walkF[(int) (e.t * 6f) % walkF.length]; break;
                case FROST: r = frostR; break;
                case LINK: r = linkF[(int) (e.t * Enemy.LINK_FPS) % linkF.length]; break;
                case RUGAL: r = rugalF[Math.min(e.rugalFrame(), rugalF.length - 1)]; break;
                case AMOEBA: r = amoebaF[(int) (e.t * Enemy.AMOEBA_FPS) % amoebaF.length]; break;
                default: r = null; break;
            }
            boolean red = e.group != null && e.group.carrier;
            if (e.type == Enemy.Type.WALKER && bioWalkRedF != null) {   // stage 1 bio walker: ping-pong walk cycle
                TextureRegion[] fr = red ? bioWalkRedF : bioWalkBlueF;
                int n = fr.length, cycle = 2 * n - 2;                 // 0 1 2 3 4 3 2 1 0 ...
                int k = (int) (e.t * BIO_WALK_FPS) % cycle;
                TextureRegion f = fr[k < n ? k : cycle - k];
                int dir = e.vx < 0f ? -1 : 1;
                float rot = (e.ceiling ? -1 : 1) * dir * e.climb * 45f;   // still tilts at steps
                batch.draw(f, e.x, e.y, e.w / 2f, e.h / 2f, e.w, e.h, 1f, e.ceiling ? -1f : 1f, rot);
                continue;
            }
            if (r == null) {                           // bio turret: red = carries a capsule; mirrored to aim right
                TextureRegion f = (red ? bioRedF : bioBlueF)[e.aim == 0 ? 1 : 0];
                boolean flipX = e.aim > 0, ceil = e.type == Enemy.Type.TURRET_CEIL;
                batch.draw(f, flipX ? e.x + e.w : e.x, ceil ? e.y + e.h : e.y, flipX ? -e.w : e.w, ceil ? -e.h : e.h);
                continue;
            }
            if (e.type == Enemy.Type.FROST) red = false;   // keeps its own colours (the group still carries a capsule)
            if (red) batch.setColor(1f, 0.45f, 0.45f, 1f);
            if (e.type == Enemy.Type.WALKER) {
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
        if (boss.hasEye && eyeF != null) {
            TextureRegion f = eyeF[boss.eyeFrame()];
            batch.draw(f, boss.x + boss.eyeX, boss.y + boss.eyeY, f.getRegionWidth(), f.getRegionHeight());
        }
        batch.setColor(Color.WHITE);
        if (boss.arms != null) for (Hazard arm : boss.arms) if (!arm.dead) drawArm(arm);   // over the brain, as in the original
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
                    float sw = shotR.getRegionWidth(), sh = shotR.getRegionHeight();
                    if (b.vy == 0f && b.vx > 0f) batch.draw(shotR, b.x, b.y, sw, sh);
                    else batch.draw(shotR, b.x + b.w / 2f - sw / 2f, b.y + b.h / 2f - sh / 2f, sw / 2f, sh / 2f, sw, sh,
                            1f, 1f, MathUtils.atan2(b.vy, b.vx) * MathUtils.radiansToDegrees);   // centred, turned to its direction
                    break;
            }
        }
        for (Bullet b : eBullets) {
            if (b.kind == Bullet.Kind.LAVA) batch.draw(lavaF[(int) (b.t * 10f) % lavaF.length], b.x, b.y, 8, 8);
            else {   // spins in quarter turns around its centre (whole 90 degree steps keep the pixel art crisp)
                float rot = 90f * ((int) (b.t * EBULLET_SPIN) % 4);
                batch.draw(ebR, b.x, b.y, b.w / 2f, b.h / 2f, b.w, b.h, 1f, 1f, -rot);
            }
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
        boolean blink = p.invuln > 0f && !shieldHit && p.ffieldHitT <= 0f && blinkPhase;   // a guard took it: no blink
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
        if (p.ffield > 0 && !blink && !(p.ffieldHitT > 0f && (int) (p.ffieldHitT * 40f) % 2 == 0)) {   // flickers when hit
            float fx = snapX(p.ffieldX()), fy = snapY(p.ffieldY());
            TextureRegion[] ff = p.ffield <= Player.FFIELD_LOW && ffieldRedF != null ? ffieldRedF : ffieldF;   // red: nearly used up
            if (ff != null) batch.draw(ff[(int) (stateT * FFIELD_FPS) % ff.length], fx, fy, Player.FFIELD_W, Player.FFIELD_H);
            else { batch.setColor(0.4f, 0.9f, 1f, 0.8f); batch.draw(pix, fx + 2f, fy, 4f, Player.FFIELD_H); batch.setColor(Color.WHITE); }
        }
        if ((p.shield > 0 || shieldHit) && !(shieldHit && blinkPhase)) {
            batch.setColor(1f, 1f, 1f, 0.35f + 0.1f * Math.max(p.shield, 1));
            batch.draw(shieldR, px - 6f, py - 8f, 40, 32);
            batch.setColor(Color.WHITE);
        }
        if (p.invuln > 1f && playersInGame() > 1) {   // "1P" / "2P" tag just after (re)entering
            PixelFont.drawShadow(batch, pix, (p.index + 1) + "P", px, py + 20f, 32, Align.center, PixelFont.SMALL, 1,
                    p.index == 1 ? P2_TINT : Color.CYAN);
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
    // The original MSX layout: a 24px strip under the playfield, player 1 on the left half, player 2 on
    // the right. Per player: the 7-slot power meter; ship icon + ships left, stage, and a yellow box
    // with the name of the highlighted power-up; "1P" + 9-digit score. Graphics: sprites/hud.png.

    private static final Color HUD_WHITE = new Color(0.88f, 0.88f, 0.88f, 1f);
    private static final Color BOX_TEXT = new Color(0.08f, 0.08f, 0.05f, 1f);
    /** Tint for HUD pieces that are inactive: a side nobody plays, or the label of an occupied power-up. */
    private static final Color DIM_TINT = new Color(0.35f, 0.35f, 0.35f, 1f);
    /** Label text on the dimmed box: a little lighter than the box, so the name stays readable. */
    private static final Color BOX_TEXT_DIM = new Color(0.16f, 0.16f, 0.1f, 1f);

    /** Text in the HUD / overlay coordinate system (bottom-left origin, 256 x 192). */
    private void text(String s, float x, float y, float w, int align, int style, int scale, Color c) {
        PixelFont.draw(batch, pix, s, x, y, w, align, style, scale, c);
    }

    /** Overlay text over the playfield, with a shadow. */
    private void say(String s, float y, int style, int scale, Color c) {
        PixelFont.drawShadow(batch, pix, s, 0, y, SW, Align.center, style, scale, c);
    }

    private void drawHud() {
        boolean blinkOn = (int) (stateT * 2f) % 2 == 0;
        batch.setColor(Color.BLACK);
        batch.draw(pix, 0, 0, SW, HUD_H);               // the strip covers whatever lies below the playfield
        batch.setColor(Color.WHITE);
        drawPlayerHud(0, blinkOn);
        drawPlayerHud(1, blinkOn);

        // boss health bar with "hp/max" in it, along the top of the playfield
        if (boss != null && !boss.dying && !boss.entering) {
            float bw = 96f, bh = 7f, bx = SW / 2f - bw / 2f, by = SH - 10f;
            batch.setColor(0.25f, 0f, 0f, 0.85f);
            batch.draw(pix, bx, by, bw, bh);
            batch.setColor(0.88f, 0.13f, 0.13f, 1f);
            batch.draw(pix, bx, by, bw * Math.max(0f, boss.hp / boss.maxHp), bh);
            batch.setColor(Color.WHITE);
            String hp = (int) Math.ceil(boss.hp) + "/" + (int) boss.maxHp;
            PixelFont.drawShadow(batch, pix, hp, 0, by + 1f, SW, Align.center, PixelFont.SMALL, 1, HUD_WHITE);
        }

        if (game.volumeMsgT > 0f && state != State.PAUSE) say(game.volumeText(), SH - 22f, PixelFont.SMALL, 1, Color.YELLOW);

        float mid = HUD_H + H / 2f;                      // middle of the playfield
        switch (state) {
            case INTRO:
                say("STAGE " + (levelIdx + 1), mid + 8f, PixelFont.BOLD, 1, HUD_WHITE);
                say(bossTest >= 0 ? "BOSS TEST" : level.name, mid - 12f, PixelFont.NORMAL, 1,
                        bossTest >= 0 ? Color.ORANGE : Color.YELLOW);
                break;
            case PLAY:
                if (bannerT > 0f && (int) (bannerT * 4f) % 2 == 0) say("WARNING", mid + 8f, PixelFont.BOLD, 1, Color.RED);
                break;
            case PAUSE:
                say("PAUSED", mid + 26f, PixelFont.BOLD, 1, HUD_WHITE);
                for (int i = 0; i < PAUSE_MENU.length; i++) {   // the selected entry in yellow, between arrows
                    boolean on = i == pauseSel;
                    say(on ? "> " + PAUSE_MENU[i] + " <" : PAUSE_MENU[i], mid + 8f - i * 11f, PixelFont.NORMAL, 1,
                            on ? Color.YELLOW : Color.LIGHT_GRAY);
                }
                say(game.volumeText(), mid - 22f, PixelFont.SMALL, 1, Color.YELLOW);
                say("1/2 MUSIC -/+   3/4 SFX -/+", mid - 32f, PixelFont.SMALL, 1, Color.LIGHT_GRAY);
                say("PAD: BACK + D-PAD", mid - 40f, PixelFont.SMALL, 1, Color.LIGHT_GRAY);
                break;
            case CLEAR:
                say("STAGE " + (levelIdx + 1) + " CLEAR", mid + 6f, PixelFont.BOLD, 1, Color.GREEN);
                say("BONUS " + stageBonus(), mid - 10f, PixelFont.NORMAL, 1, HUD_WHITE);
                break;
            case GAMEOVER:
                say("GAME OVER", mid + 4f, PixelFont.BOLD, 1, Color.RED);
                if (stateT > 1f) say("PRESS ENTER / START", mid - 14f, PixelFont.SMALL, 1, HUD_WHITE);
                break;
            case WIN:
                say("YOU SAVED THE GALAXY", mid + 14f, PixelFont.NORMAL, 1, Color.CYAN);
                say(finalScores(), mid - 2f, PixelFont.NORMAL, 1, HUD_WHITE);
                if (stateT > 1f) say("PRESS ENTER / START", mid - 18f, PixelFont.SMALL, 1, Color.YELLOW);
                break;
            default:
                break;
        }
    }

    private String finalScores() {
        StringBuilder s = new StringBuilder();
        for (Player p : players) {
            if (p == null) continue;
            if (s.length() > 0) s.append("   ");
            s.append(p.index + 1).append("P ").append(String.format("%06d", p.score));
        }
        return s.toString();
    }

    /** One player's half of the HUD strip. */
    private void drawPlayerHud(int idx, boolean blinkOn) {
        Player p = players[idx];
        int ox = idx * (SW / 2);
        boolean choosingType = p == null && choosing[idx] >= 0;
        boolean inPlay = p != null && !p.out;

        // power meter: 7 slots of 14x6, 16px apart
        int[] slots = p != null ? p.slots : Player.slotsFor(Math.max(0, choosing[idx]));
        for (int i = 0; i < Player.SLOT_COUNT; i++) {
            float x = ox + 9 + 16 * i;
            boolean sel = inPlay && p.meter == i + 1;
            boolean occupied = inPlay && owned(p, slots[i]);           // can't be taken (any further) right now
            if (!inPlay && !choosingType) batch.setColor(DIM_TINT);   // nobody flying on this side
            batch.draw(hudSlotR[(sel ? 1 : 0) + (occupied ? 2 : 0)], x, 17);
            batch.setColor(Color.WHITE);
        }

        // ship icon + ships left, stage number, yellow box
        batch.draw(hudShipR[idx], ox + 8, 8);
        text(String.valueOf(p == null ? 0 : Math.max(0, Math.min(9, p.lives))), ox + 25, 8, 8, Align.left, PixelFont.BOLD, 1, HUD_WHITE);
        batch.draw(hudER, ox + 40, 8);
        text(String.format("%02d", levelIdx + 1), ox + 49, 8, 16, Align.left, PixelFont.BOLD, 1, HUD_WHITE);
        // the box (and its label) dims, like an inactive player's meter, when the highlighted power-up is occupied
        boolean selTaken = inPlay && p.selectedPower() != 0 && owned(p, p.selectedPower());
        if (selTaken) batch.setColor(DIM_TINT);
        batch.draw(hudBoxR, ox + 72, 8);
        batch.setColor(Color.WHITE);
        String box = "";
        if (choosingType) {
            box = blinkOn ? "TYPE " + (choosing[idx] + 1) : "";
        } else if (inPlay) {
            int power = p.selectedPower();
            box = power == 0 ? "" : Player.POWER_NAMES[power];
        } else if (state != State.GAMEOVER && state != State.WIN) {
            if (!Controls.hasInput(idx)) box = "PAD " + (idx + 1);
            else if (blinkOn) box = p == null ? "JOIN" : "RETRY";
        }
        text(box, ox + 77, 9, 42, Align.center, PixelFont.SMALL, 1, selTaken ? BOX_TEXT_DIM : BOX_TEXT);

        // "1P" + score
        text((idx + 1) + "P", ox + 9, 0, 16, Align.left, PixelFont.BOLD, 1, HUD_WHITE);
        text(String.format("%09d", p == null ? 0 : Math.min(999999999, p.score)), ox + 41, 0, 72, Align.left, PixelFont.BOLD, 1, HUD_WHITE);
    }

    /** Is this power-up already active (taken as far as it goes)? */
    private static boolean owned(Player p, int power) {
        switch (power) {
            case Player.SPEED: return p.speedLvl >= 5;
            case Player.MISSILE: case Player.TWO_WAY: return p.missile;
            case Player.DOUBLE: return p.weapon == Player.W_DOUBLE;
            case Player.LASER: return p.weapon == Player.W_LASER;
            case Player.RIPPLE: return p.weapon == Player.W_RIPPLE;
            case Player.OPTION: return p.options >= Player.MAX_OPTIONS;
            case Player.SHIELD: return p.shield > 0;
            case Player.FFIELD: return p.ffield > 0;
            case Player.EXTRA: return p.lives >= Player.MAX_LIVES;
            default: return false;
        }
    }
}
