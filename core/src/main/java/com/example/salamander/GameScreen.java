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

    /** A power capsule; green = gives back one option lost when a ship was destroyed. */
    private static class Capsule {
        float x, y, y0, t;
        boolean green;
        /** Green capsules come back once at the far edge after leaving the screen: true once that has happened. */
        boolean wrapped;
        boolean dead() { return x < -9999f; }
    }
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
     * How fast the screen moves up / down along the path right now (px/s; the follow drift toward the ships is not
     * included, as for the ships). Free-flying things that move relative to the screen add scrollVX / scrollVY.
     */
    float scrollVY;
    /**
     * Where the camera path puts the camera (bottom), and how far the camera has drifted up or down from
     * that to follow the ships (|camOff| <= laneSlack()). scrollY = pathY + camOff.
     */
    float pathY, camOff;
    private final float[] camTmp = new float[2];
    final ArrayList<Bullet> eBullets = new ArrayList<>();

    private State state = State.INTRO;
    private float stateT, checkpointT;
    private int nextSpawn;
    private Boss boss;
    private boolean bossSpawned;
    /** F1 / H toggles the hitbox overlay (static so it survives stage changes). */
    private static boolean debugHitbox;
    /** D on the title screen: every stage (and every newly joined ship) starts with the debug loadout. */
    static boolean debugLoadout;
    public static final int STAGES = 7;
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
    /** Kierke chains on their way: started (its start reached Enemy.CHAIN_START_X), time since, balls grown, the last ball. */
    private static final class ChainRun { Level.Chain c; boolean started; float t, lastX, lastY; int made; Enemy root; }
    private final ArrayList<ChainRun> chainRuns = new ArrayList<>();
    private final ArrayList<Hazard> hazards = new ArrayList<>();

    void addHazard(Hazard h) { hazards.add(h); }
    /** Hazards made while the hazards are being gone through (e.g. rubble during the collisions); added after. */
    private final ArrayList<Hazard> pendingHazards = new ArrayList<>();
    private final ArrayList<Capsule> caps = new ArrayList<>();
    /** Asteroid storm rocks (screen coordinates, see Meteor), and the pieces of big rocks split during the collisions. */
    private final ArrayList<Meteor> meteors = new ArrayList<>();
    private final ArrayList<Meteor> pendingMeteors = new ArrayList<>();
    /** Asteroid storms (level.storms): the next one to start, seconds left of the current one, time to the next rock. */
    private int nextStorm;
    private float stormLeft, stormSpawnT;
    /** The storm going on (its map object's numbers, see Level.storms). */
    private float[] storm;
    /** Rocks spawned in this storm since the last big one (a big one comes about every Meteor.BIG_EVERY small ones). */
    private int stormSmallRun;
    private final ArrayList<Explosion> fx = new ArrayList<>();
    private final HashMap<Integer, Enemy.Group> groups = new HashMap<>();

    // art
    private final Texture bgTex, starTex;
    private final float capW, capH;                 // power capsule size, from sprites/PowerCapsule.png
    /** Legacy (sprites/legacy.png): 7 frames of 16x16, white with a black outline (see Enemy.legacyFrame). */
    private final TextureRegion[] legacyF;
    private static final Color LEGACY_GREEN = new Color(0x73 / 255f, 1f, 0x73 / 255f, 1f);   // the old fly-by wave
    /** Hovering Legacy colour cycle (MSX): magenta, magenta, blue, magenta, magenta, red; LEGACY_COLOUR_TIME s each. */
    private static final Color[] LEGACY_COLOURS;
    static {
        Color m = new Color(224 / 255f, 82 / 255f, 186 / 255f, 1f), b = new Color(82 / 255f, 115 / 255f, 1f, 1f),
                r = new Color(1f, 39 / 255f, 39 / 255f, 1f);
        LEGACY_COLOURS = new Color[]{m, m, b, m, m, r};
    }
    private static final float LEGACY_COLOUR_TIME = 2f / 60f;
    /** Player 2's ship (sprites/player_red.png, same layout as player.png). */
    private final TextureRegion[] playerRedF;
    private final TextureRegion[] playerF, optionF, walkF, capF, boomF, lavaF;
    /** sprites/powercapsule_green.png (same layout as PowerCapsule.png); the normal capsule when it is missing. */
    private final TextureRegion[] capGreenF;
    private final TextureRegion hudER, hudBoxR, hudBoxOffR;
    private final TextureRegion[] bamdaF, armF;
    private final TextureRegion[] hudSlotR, hudShipR;
    /** Bio turrets: frame 0 points diagonally left, frame 1 points up. Red ones carry a power capsule. */
    private final TextureRegion[] bioRedF, bioBlueF;
    /**
     * Stage 1 and Kierke walkers: "bio walker red/blue.png", 5 frames played forward then backward (no left / right facing).
     * Red ones carry a power capsule. null on the other stages, which keep walker.png.
     */
    private final TextureRegion[] bioWalkRedF, bioWalkBlueF;
    private static final float BIO_WALK_FPS = 8f;
    /** Enemy bullets spin this many quarter turns per second. */
    private static final float EBULLET_SPIN = 12f;
    /** Celtic Frost (sprites/celtic frost.png). */
    private final TextureRegion frostR;
    /** Possessed (sprites/possessed_red.png / possessed_cyan.png, 16x16), drawn as they are. */
    private TextureRegion possRedR, possCyanR;
    /** Whiplash (sprites/whiplash.png, 15x16), drawn as it is. */
    private TextureRegion whipR;
    /** Battleaxe (sprites/battleaxe.png, 16x16), drawn as it is. */
    private TextureRegion axeR;
    /** Kierke chain balls (sprites/chain_balls.png, 26x8): the 8x8, 6x6 and 4x4 ball. */
    private TextureRegion[] chainR;
    /** Aberrant (sprites/aberrant.png, 2 frames of 16x16: alive, withered); nest (sprites/nest.png, 38x22); nest bug (sprites/nest_bug.png). */
    private TextureRegion[] aberrantF;
    private TextureRegion nestR;
    /** Creeping Death (sprites/creeping death.png: 4 frames, white; any black pixels made see-through) and its colours. */
    private TextureRegion[] creepF;
    private static final Color CREEP_CYAN = new Color(82 / 255f, 224 / 255f, 1f, 1f), CREEP_GREEN = new Color(115 / 255f, 1f, 115 / 255f, 1f),
            CREEP_RED = new Color(1f, 39 / 255f, 39 / 255f, 1f), CREEP_DARK_GREEN = new Color(39 / 255f, 155 / 255f, 39 / 255f, 1f);
    /** Ug (sprites/ug.png, 3 frames of 16x16). */
    private TextureRegion[] ugF;
    /** Gamma (sprites/gamma.png, 4 frames of 16x16). */
    private TextureRegion[] gammaF;
    /** Kierke green mine: sprites/green_mine.png, 2 frames of 16x16. */
    private TextureRegion[] greenMineF;
    /** Op 4's option eater (sprites/option_eater.png: white, red; see Enemy.updateOptionEater). */
    private TextureRegion[] optionEaterF;
    /** Op 4 (stage 6): Exodus (cyan / red, 3 frames), artillery (green / red), base, dome, blue asteroid, moai, orb, ring. */
    private TextureRegion[] exodusCyanF, exodusRedF, base6F;
    /**
     * Background lights (Op 4: stage_6_back_ground_lights.png, 3 frames of 7x7; Lavinia: stage_3_..., 3 frames of 8x8):
     * sprites/stage_<N>_back_ground_lights.png, square frames in a row; each shown level.bgLightsFrame s.
     */
    private TextureRegion[] bgLightsF;
    private String bgLightsFile;
    private int bgLightsSize;
    private float bgLightsClock;
    private TextureRegion artilleryGreenR, artilleryRedR, blueAsteroidR, orbR, ring6R;
    /** Artillery's frames (artillery_green / _red.png: 2 frames of 16x16 side by side; Albert, 2026-10-30), cycled every ART_FRAME s. */
    private TextureRegion[] artilleryGreenF, artilleryRedF;
    private static final float ART_FRAME = 0.4f;
    /** Op 4 moai: mouth shut, mouth open (sprites/moai.png, 2 frames of 50x38). */
    private TextureRegion[] moaiF;
    /** The green flyers of the Eioneus / Op 4 domes (sprites/green_flyer.png, 4 frames of 16x16; the domes use domeF). */
    /** Eioneus laser batteries (sprites/laser_battery_small.png, laser_battery_big.png; their dark background is see-through). */
    private TextureRegion batterySmallR, batteryBigR;
    /** Eioneus gremlins (sprites/gremlin_yellow.png, gremlin_red.png: 3 frames of 16x16, frame 0 = aiming). */
    private TextureRegion[] gremlinYellowF, gremlinRedF;
    /** Eioneus bases (sprites/eioneus_base_core.png, the middle of the base: 4 frames of 20x21) and flying mines (sprites/flying_mines.png, flying_mines_red.png: 5 frames of 16x16; black = see-through). */
    private TextureRegion[] baseF;
    private TextureRegion[] mineWhiteF, mineRedF;
    private static final Color BEAM_RED = new Color(1f, 39 / 255f, 39 / 255f, 1f), BEAM_BLUE = new Color(82 / 255f, 115 / 255f, 1f, 1f);
    private TextureRegion[] greenF;
    /** SHIELD (sprites/shield.png): 8x16 frames, looped. null = picture not there yet (drawn as a plain bar). */
    private final TextureRegion[] shieldF;
    /** sprites/shield_red.png: the same field once it has Player.SHIELD_LOW hits or fewer left (null = keep shield.png). */
    private final TextureRegion[] shieldRedF;
    private static final float SHIELD_FPS = 12f;
    /** Missing Link (sprites/missing_link.png): 7 frames of a barrel roll, looped. */
    private final TextureRegion[] linkF;
    /** Amoeba (sprites/amoeba.png): 3 frames of 16x16, looped. */
    private final TextureRegion[] amoebaF;
    /** Rugal (sprites/rugal.png): level, diving, climbing. */
    private final TextureRegion[] rugalF;
    /** sprites/rugal_red.png: a Rugal that carries a power capsule (same frames as rugal.png). */
    private final TextureRegion[] rugalRedF;
    /** Stage 2 dome flyer (sprites/green_flyer.png): 4 frames of 16x16, already green. null = not on this stage. */
    private final TextureRegion[] domeFlyerF;
    /** At most this many amoebas float around at once (the towers stop spitting until some are gone). */
    private static final int AMOEBA_MAX = 16;
    /**
     * Stage 3 Gun Ho (sprites/Gun Ho.png, faces left): frame 0 (gunHoR), and, when the picture holds two frames side by
     * side (wider than tall; Albert, 2026-10-30), frame 1 (gunHoFireR), shown while it lines up to fire (null: one frame).
     */
    private TextureRegion gunHoR, gunHoFireR;
    /** Stage 3 Omen (sprites/Omen.png, floor version; drawn upside down on the ceiling). */
    private TextureRegion omenR;
    /** Gun Ho's shot (sprites/Gun Ho bullet.png); its size is the hit box. Missing: a drawn red / yellow beam. */
    private TextureRegion gunHoBulletR;
    /** Omen's laser: sprites/enemy laser red.png and blue.png (same pattern), repeated sideways, flashing. */
    private Texture[] omenLaserTex;
    /** Stage 3 Laser Guard (sprites/Laser Guard.png, pointing down: the ceiling version). */
    private Texture laserGuardTex;

    /**
     * Stage 3's last room (map objects "hold" + "dematwall"): the scrolling stops at the hold for level.holdTime
     * seconds (ROOM_FIGHT, the Laser Guards' fight), then the guards retreat into their walls (ROOM_RETREAT), the
     * walls dissolve pixel by pixel (ROOM_DEMAT) and the scrolling goes on to the boss (ROOM_DONE).
     */
    static final int ROOM_NONE = 0, ROOM_FIGHT = 1, ROOM_RETREAT = 2, ROOM_DEMAT = 3, ROOM_DONE = 4;
    int roomState;
    float roomT;
    /** How long the walls take to dissolve (s). */
    static final float WALL_DEMAT_TIME = 1.5f;
    /** Per dematwall: its picture (being dissolved), the texture drawn from it, its solid pixels in random order. */
    private Pixmap[] wallPix;
    private Texture[] wallTex;
    private int[][] wallOrder;
    private int[] wallCleared;

    /** The guards slide back into their walls (and stop firing) once the room's time is up. */
    boolean roomRetreating() { return roomState >= ROOM_RETREAT; }
    /** Gun Ho's laser beam: red edges, yellow core (the colours of its shot in the original). */
    private static final Color GUNHO_BEAM_EDGE = new Color(0xf7 / 255f, 0x20 / 255f, 0x20 / 255f, 1f);
    private static final Color GUNHO_BEAM_CORE = new Color(0xd8 / 255f, 0xd8 / 255f, 0x20 / 255f, 1f);
    private final TextureRegion rockR, shotR, laserR, missR, ebR, ffieldR, pix, bossR;
    // ---- vertical stage (stage 2)
    /**
     * sprites/player_blue_vertical.png: the ship pointing up (vertical stages). Three frames side by side: banking
     * left, level, banking right (a picture with a single frame is used for all three).
     */
    private final TextureRegion[] playerVF;
    /** Player 2's vertical ship (sprites/player_red_vertical.png, same layout). */
    private final TextureRegion[] playerVRedF;
    /** Wall turrets: [0] = sideways, [1] = diagonal (pointing down), facing right. */
    private final TextureRegion[] wallTurretRedF, wallTurretGreenF;
    /** sprites/dome.png: two frames side by side, closed and open (facing right, for the left wall). */
    private final TextureRegion[] domeF;
    /** Exact shapes of the dome frames: [frame: closed, open][0 = as drawn (left wall), 1 = mirrored (right wall)]. */
    private PixelMask[][] domeMasks;
    private final TextureRegion rockEyeR, ringR;
    /** Asteroid storm rocks: sprites/rock_small.png (4 frames of 15x15) and sprites/rock_big.png (null = no storms). */
    private final TextureRegion[] rockSmallF;
    private final TextureRegion rockBigR;
    /** Side volcanoes' red rocks: sprites/rock_small_red.png (the 4 rock_small pictures in the stage's rock red). */
    private final TextureRegion[] rockRedF;
    /** Exact shape of the eye rock (sprites/rock_eye.png, its dark background made see-through). */
    private PixelMask rockEyeMask;
    /** sprites/rubbles.png: what is left of a destroyed eye rock (solid like terrain), and its exact shape. */
    private final TextureRegion rubbleR;
    private PixelMask rubbleMask;
    /** The map's "brickArt" picture (null = bricks use brick_pattern.png). */
    private Texture brickArtTex;
    // map stages: pre-rendered terrain strips, brick maze picture, bamda sprites
    private Texture[] artTex;
    private Texture brickTex;
    private final java.util.HashMap<String, PixelMask> masks = new java.util.HashMap<>();
    private PixelMask bossMask;
    private static final Color[] STAR_TINT = {new Color(0.7f, 0.8f, 1f, 1f), new Color(0.9f, 0.9f, 1f, 1f),
            new Color(0.9f, 0.9f, 1f, 1f), new Color(0.9f, 0.9f, 1f, 1f), new Color(0.8f, 0.85f, 1f, 1f),
            new Color(0.9f, 0.9f, 1f, 1f), new Color(0.7f, 1f, 0.8f, 1f)};   // (stage 2 on: plain white stars, like the original)

    /** @param players the joined players (length 2, null entries = not joined); lives and score carry over */
    public GameScreen(SalamanderGame game, int levelIdx, Player[] players) {
        this.game = game;
        this.a = game.assets;
        this.batch = game.batch;
        this.levelIdx = levelIdx;
        this.level = Level.build(levelIdx);
        this.players = players;
        Player.vertical = level.vertical;
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
        playerRedF = Gdx.files.internal("sprites/player_red.png").exists() ? a.strip("player_red", 32) : playerF;   // player 2
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
        hudBoxOffR = new TextureRegion(hudTex, 137, 0, 48, 7);   // red box: the highlighted power-up is taken
        optionF = a.frames("option", 4);   // 4 frames of 16x10 (the last one trimmed to 14 wide)
        legacyF = a.strip("legacy", 16);
        walkF = a.strip("walker", 16);
        bioWalkRedF = levelIdx == 0 || levelIdx == 4 ? a.strip("bio walker red", 16) : null;     // stage 1 and Kierke
        bioWalkBlueF = levelIdx == 0 || levelIdx == 4 ? a.strip("bio walker blue", 16) : null;
        capF = scanFrames("sprites/PowerCapsule.png");   // frames separated by empty columns
        capGreenF = Gdx.files.internal("sprites/powercapsule_green.png").exists() ? scanFrames("sprites/powercapsule_green.png") : capF;
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
        gunHoR = Gdx.files.internal("sprites/Gun Ho.png").exists() ? a.one("Gun Ho") : null;
        if (gunHoR != null && gunHoR.getRegionWidth() > gunHoR.getRegionHeight()) {   // two frames: ready, about to fire
            int fw = gunHoR.getRegionWidth() / 2, fh = gunHoR.getRegionHeight();
            TextureRegion all = gunHoR;
            gunHoR = new TextureRegion(all, 0, 0, fw, fh);
            gunHoFireR = new TextureRegion(all, fw, 0, fw, fh);
        }
        loadHeathens();
        blockSmallR = Gdx.files.internal("sprites/lavinia_block_small.png").exists() ? a.one("lavinia_block_small") : null;
        blockBigR = Gdx.files.internal("sprites/lavinia_block_big.png").exists() ? a.one("lavinia_block_big") : null;
        rageR = Gdx.files.internal("sprites/rage.png").exists() ? a.one("rage") : null;
        ledZepF = Gdx.files.internal("sprites/led_zep.png").exists() ? a.strip("led_zep", 16) : null;
        laserGunR = Gdx.files.internal("sprites/laser_gun_point.png").exists() ? a.one("laser_gun_point") : null;
        gammaRedF = Gdx.files.internal("sprites/gamma_red.png").exists() ? a.strip("gamma_red", 16) : null;
        hatchF = levelIdx == 2 && Gdx.files.internal("sprites/lavinia_boss_hatch.png").exists() ? a.strip("lavinia_boss_hatch", 16) : null;
        omenR = Gdx.files.internal("sprites/Omen.png").exists() ? a.one("Omen") : null;
        laserGuardTex = Gdx.files.internal("sprites/Laser Guard.png").exists() ? a.tex("Laser Guard") : null;
        gunHoBulletR = Gdx.files.internal("sprites/Gun Ho bullet.png").exists() ? a.one("Gun Ho bullet") : null;
        if (Gdx.files.internal("sprites/enemy laser red.png").exists() && Gdx.files.internal("sprites/enemy laser blue.png").exists()) {
            omenLaserTex = new Texture[]{a.tex("enemy laser red"), a.tex("enemy laser blue")};
            for (Texture t : omenLaserTex) t.setWrap(Texture.TextureWrap.Repeat, Texture.TextureWrap.ClampToEdge);   // tiled sideways
        }
        frostR = a.one("celtic frost");
        whipR = Gdx.files.internal("sprites/whiplash.png").exists() ? a.one("whiplash") : null;
        axeR = Gdx.files.internal("sprites/battleaxe.png").exists() ? a.one("battleaxe") : null;
        aberrantF = Gdx.files.internal("sprites/aberrant.png").exists() ? a.strip("aberrant", 16) : null;
        nestR = Gdx.files.internal("sprites/nest.png").exists() ? a.one("nest") : null;
        if (Gdx.files.internal("sprites/creeping death.png").exists()) {   // black = see-through
            com.badlogic.gdx.graphics.Pixmap src = new com.badlogic.gdx.graphics.Pixmap(Gdx.files.internal("sprites/creeping death.png"));
            com.badlogic.gdx.graphics.Pixmap pm = new com.badlogic.gdx.graphics.Pixmap(src.getWidth(), src.getHeight(), com.badlogic.gdx.graphics.Pixmap.Format.RGBA8888);
            pm.setBlending(com.badlogic.gdx.graphics.Pixmap.Blending.None);
            for (int yy = 0; yy < src.getHeight(); yy++) for (int xx = 0; xx < src.getWidth(); xx++) {
                int c = src.getPixel(xx, yy), r = c >>> 24 & 255, gg = c >>> 16 & 255, bb = c >>> 8 & 255, al = c & 255;
                pm.drawPixel(xx, yy, al == 0 || r + gg + bb < 96 ? 0 : 0xffffffff);
            }
            Texture tx = new Texture(pm);
            int fw = src.getWidth() / 4;
            creepF = new TextureRegion[4];
            for (int i = 0; i < 4; i++) creepF[i] = new TextureRegion(tx, i * fw, 0, fw, src.getHeight());
            pm.dispose(); src.dispose();
        }
        ugF = Gdx.files.internal("sprites/ug.png").exists() ? a.strip("ug", 16) : null;
        greenMineF = Gdx.files.internal("sprites/green_mine.png").exists() ? a.strip("green_mine", 16) : null;
        gammaF = Gdx.files.internal("sprites/gamma_blue.png").exists() ? a.strip("gamma_blue", 16)   // (Albert's blue / red pictures)
                : Gdx.files.internal("sprites/gamma.png").exists() ? a.strip("gamma", 16) : null;
        artilleryGreenR = keyedDark("sprites/artillery_green.png");   // artillery: Op 4 and Lavinia (any stage that has it)
        artilleryRedR = keyedDark("sprites/artillery_red.png");
        artilleryGreenF = squareFrames(artilleryGreenR);
        artilleryRedF = squareFrames(artilleryRedR);
        // blue rock clumps: Op 4 and stage 2 (~120-130 s in the stage 2 recording)
        blueAsteroidR = (levelIdx == 1 || levelIdx == 5) && Gdx.files.internal("sprites/blue_asteroid.png").exists() ? a.one("blue_asteroid") : null;
        if (levelIdx == 5) {                          // Op 4 (Albert's sprites; black is see-through)
            exodusCyanF = keyedFrames("sprites/exodus_cyan.png", 16);
            exodusRedF = keyedFrames("sprites/exodus_red.png", 16);
            base6F = Gdx.files.internal("sprites/stage_6_back_ground_base.png").exists() ? a.strip("stage_6_back_ground_base", 22) : null;
            moaiF = Gdx.files.internal("sprites/moai.png").exists() ? a.strip("moai", 50) : null;
            orbR = Gdx.files.internal("sprites/orb.png").exists() ? a.one("orb") : null;
            ring6R = keyedDark("sprites/ring_laser.png");
            optionEaterF = Gdx.files.internal("sprites/option_eater.png").exists() ? a.strip("option_eater", 16) : null;
        }
        batterySmallR = keyedDark("sprites/laser_battery_small.png");
        // the middle of the Eioneus base, 4 frames of 20x21 (closed, opening, open, destroyed); the rest of the base is map art
        baseF = Gdx.files.internal("sprites/eioneus_base_core.png").exists() ? a.strip("eioneus_base_core", 20) : null;
        mineWhiteF = keyedFrames("sprites/flying_mines.png", 16);
        mineRedF = keyedFrames("sprites/flying_mines_red.png", 16);
        gremlinYellowF = Gdx.files.internal("sprites/gremlin_yellow.png").exists() ? a.strip("gremlin_yellow", 16) : null;
        gremlinRedF = Gdx.files.internal("sprites/gremlin_red.png").exists() ? a.strip("gremlin_red", 16) : null;
        batteryBigR = keyedDark("sprites/laser_battery_big.png");
        greenF = Gdx.files.internal("sprites/green_flyer.png").exists() ? a.strip("green_flyer", 16) : null;
        if (Gdx.files.internal("sprites/chain_balls.png").exists()) {
            TextureRegion all = a.one("chain_balls");
            chainR = new TextureRegion[]{new TextureRegion(all, 0, 0, 8, 8), new TextureRegion(all, 10, 1, 6, 6), new TextureRegion(all, 20, 2, 4, 4)};
        }
        possRedR = Gdx.files.internal("sprites/possessed_red.png").exists() ? a.one("possessed_red") : null;
        possCyanR = Gdx.files.internal("sprites/possessed_cyan.png").exists() ? a.one("possessed_cyan") : null;
        shieldF = Gdx.files.internal("sprites/shield.png").exists() ? a.strip("shield", 8) : null;   // drawn as it is (its own transparency)
        shieldRedF = Gdx.files.internal("sprites/shield_red.png").exists() ? a.strip("shield_red", 8) : null;
        linkF = a.strip("missing_link", 16);
        amoebaF = framesOuterBlackKeyed("sprites/amoeba.png", 16);
        rugalF = a.strip("rugal", 16);
        rugalRedF = Gdx.files.internal("sprites/rugal_red.png").exists() ? a.strip("rugal_red", 16) : null;
        rippleTex = a.texFile("sprites/ripple.png");   // drawn as it is (its own transparency)
        ffieldR = a.one("ffield");
        pix = a.one("pixel");
        bossR = loadBossPicture();                     // (also sets bossMask)
        loadBarBoss();
        loadPods();
        if (levelIdx == 3 && orbR == null && Gdx.files.internal("sprites/orb.png").exists()) orbR = a.one("orb");   // (the pod room's bubbles)
        loadKierkeBoss();
        boolean v = level.vertical;   // pictures only the vertical stage uses
        playerVF = v ? verticalShipFrames(keyedBackground("sprites/player_blue_vertical.png")) : null;
        playerVRedF = v && Gdx.files.internal("sprites/player_red_vertical.png").exists()
                ? verticalShipFrames(keyedBackground("sprites/player_red_vertical.png")) : playerVF;   // player 2
        wallTurretRedF = v ? a.strip("wall turret red", 16) : null;
        wallTurretGreenF = v ? a.strip("wall turret green", 16) : null;
        boolean domes = v || !level.domes.isEmpty();   // (stage 3 has them too, on the floor and the ceiling)
        domeF = domes ? a.strip("dome", a.tex("dome").getWidth() / 2) : null;   // 2 frames: closed, open
        domeFlyerF = v && Gdx.files.internal("sprites/green_flyer.png").exists() ? a.strip("green_flyer", 16) : null;
        if (domes) {   // per frame: [left wall, right wall (mirrored), floor (turned to face up), ceiling (facing down)]
            PixelMask[] dm = PixelMask.loadFrames("sprites/dome.png", domeF[0].getRegionWidth());
            domeMasks = new PixelMask[dm.length][];
            for (int i = 0; i < dm.length; i++) domeMasks[i] = new PixelMask[]{dm[i], dm[i].flippedH(), dm[i].rotatedCCW(), dm[i].rotatedCW()};
        }
        PixelMask[] eyeMask = new PixelMask[1];
        rockEyeR = v ? keyedBackground("sprites/rock_eye.png", eyeMask) : null;
        rockEyeMask = eyeMask[0];
        boolean rubble = v && Gdx.files.internal("sprites/rubbles.png").exists();
        rubbleR = rubble ? a.one("rubbles") : null;
        rubbleMask = rubble ? PixelMask.load("sprites/rubbles.png") : null;
        ringR = v ? a.one("tetran ring") : null;
        boolean storms = !level.storms.isEmpty() && Gdx.files.internal("sprites/rock_small.png").exists();
        rockSmallF = storms ? a.strip("rock_small", 15) : null;
        // big rocks (that split) only in stage 2's storm (Albert); the other stages' storms are small rocks only
        rockBigR = storms && levelIdx == 1 && Gdx.files.internal("sprites/rock_big.png").exists() ? a.one("rock_big") : null;
        boolean sideVolcanoes = false;
        for (float[] vo : level.volcanoes) if (vo.length > 2 && vo[2] != 0f) sideVolcanoes = true;
        rockRedF = sideVolcanoes && Gdx.files.internal("sprites/rock_small_red.png").exists() ? a.strip("rock_small_red", 15) : null;
        if (level.brickArt != null) brickArtTex = a.texFile(level.brickArt);
        // stage art from the Tiled map: image layers, terrain tileset, brick pattern, hazard pictures
        artTex = new Texture[level.art.size()];
        for (int i = 0; i < artTex.length; i++) artTex[i] = a.texFile(level.art.get(i).image);
        bgLightsFile = "stage_" + (levelIdx + 1) + "_back_ground_lights";
        if (!level.bgLights.isEmpty() && Gdx.files.internal("sprites/" + bgLightsFile + ".png").exists()) {
            bgLightsSize = a.tex(bgLightsFile).getHeight();
            bgLightsF = a.strip(bgLightsFile, bgLightsSize);
        }
        unbakeBgLights();
        loadWeb();
        brickTex = a.texFile("maps/brick_pattern.png");
        brickTex.setWrap(Texture.TextureWrap.Repeat, Texture.TextureWrap.Repeat);
        for (Level.Tooth t : level.teeth) {
            if (!masks.containsKey(t.image)) masks.put(t.image, PixelMask.load(t.image));
        }
        for (Level.Tower t : level.towers) {
            if (!masks.containsKey(t.image)) masks.put(t.image, PixelMask.load(t.image));
            if (t.flip && !masks.containsKey(t.image + "#flipV")) masks.put(t.image + "#flipV", masks.get(t.image).flippedV());
        }
        if (levelIdx == 0) loadBossEye("sprites/boss" + levelIdx + ".png");   // (only stage 1's brain has an eye)
        loadBossCore();

        if (bossTest >= 0) {
            checkpointT = level.stopT;               // boss test: start (and restart after dying) at the boss arena
            if (bossPartR != null) {                 // a multi-boss stage: just before the first boss (on the last stretch of path)
                bossPartBox();
                level.camAt(level.stopT, camTmp);
                float want = partBox[1] + partBox[3] + BOSS_PART_TOP - 40f - H;   // camera bottom 40 px before it wakes
                checkpointT = Math.max(0f, level.stopT - Math.max(0f, camTmp[1] - want));
            }
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
        if (level.vertical) {                     // near the bottom, in the middle (P1 left, P2 right)
            float off = playersInGame() > 1 ? (p.index == 0 ? -20f : 20f) : 0f;
            p.y = scrollY + 24f;
            p.x = openSpotX(scrollX + W / 2f - Player.W / 2f + off, p.y);
        } else {
            p.x = scrollX + 40f;
            float off = playersInGame() > 1 ? (p.index == 0 ? 14f : -14f) : 0f;   // P1 above, P2 below
            p.y = openSpot(p.x, scrollY + H / 2f - Player.H / 2f + off);
        }
        p.invuln = 2f;
        p.ghostT = p.invuln;   // ...and the walls can't hurt it either while it blinks
        p.fireCd = 0f; p.missCd = 0f;
        p.fillTrail(p.x - scrollX, p.y - pathY);
        p.optReady = false;                              // (the options start in their trail places, normal formation)
        p.optMode = Player.OPT_TRAIL;
    }

    /** Vertical stages: ship-sized free spot closest to x at height y (searched left and right within the screen). */
    private float openSpotX(float x, float y) {
        float lo = scrollX + 2f, hi = scrollX + W - Player.W - 2f;
        for (float d = 0f; d < W; d += 4f) {
            for (int s = -1; s <= 1; s += 2) {
                float xx = MathUtils.clamp(x + s * d, lo, hi);
                if (!level.hits(xx + Player.VART_L, y, Player.VART_R - Player.VART_L, Player.VART_T)) return xx;
            }
        }
        return MathUtils.clamp(x, lo, hi);
    }

    /** How far the stage has scrolled in its own direction: the camera's left edge, or its bottom on a vertical stage. */
    private float front() { return level.vertical ? scrollY : scrollX; }

    /** Size of the view in the scrolling direction. */
    private float viewLen() { return level.vertical ? H : W; }

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
    /** Mid-game joiners then pick their guard: 0 = not yet (still on the ship type), else Player.SHIELD / FFIELD. */
    private final int[] choosingGuard = {0, 0};

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
                if (choosingGuard[i] == 0) {                 // 1: ship type
                    if (Controls.pressed(i, Action.CANCEL)) { choosing[i] = -1; a.play("hit", 0.2f); continue; }   // changed their mind
                    if (Controls.pressed(i, Action.LEFT)) { choosing[i] = (choosing[i] + Player.SHIP_TYPES - 1) % Player.SHIP_TYPES; a.play("hit", 0.2f); }
                    if (Controls.pressed(i, Action.RIGHT)) { choosing[i] = (choosing[i] + 1) % Player.SHIP_TYPES; a.play("hit", 0.2f); }
                    if (Controls.pressed(i, Action.FIRE)) { choosingGuard[i] = Player.defaultGuard(choosing[i]); a.play("pickup", 0.4f); }
                    continue;
                }
                // 2: guard (SHIELD / F.FIELD)
                if (Controls.pressed(i, Action.CANCEL)) { choosingGuard[i] = 0; a.play("hit", 0.2f); continue; }   // back to the ship type
                if (Controls.pressed(i, Action.LEFT) || Controls.pressed(i, Action.RIGHT)) {
                    choosingGuard[i] = choosingGuard[i] == Player.SHIELD ? Player.FFIELD : Player.SHIELD;
                    a.play("hit", 0.2f);
                }
                if (!Controls.pressed(i, Action.FIRE)) continue;
                p = new Player(i, choosing[i], choosingGuard[i]);
                p.lives = Player.startLives(game.easy);
                choosing[i] = -1;
                choosingGuard[i] = 0;
                if (debugLoadout) p.applyDebugLoadout();
                players[i] = p;
            } else if (p.out && Controls.pressed(i, Action.FIRE)) {
                p.out = false;
                p.lives = Player.startLives(game.easy);
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
        scrollVY = 0f;
        float sx = scrollX, fr = front();
        resetDarkness(sx);
        pBullets.clear(); eBullets.clear(); enemies.clear(); caps.clear(); groups.clear();
        aimBeamList.clear();
        level.obstacles.clear();                      // rubble goes (the eye rocks come back)
        pendingHazards.clear();
        meteors.clear(); pendingMeteors.clear();
        stormLeft = 0f; stormSmallRun = 0;
        nextStorm = 0;                                // a restart further on: storms the screen already passed are over
        while (t > 0f && nextStorm < level.storms.size() && level.storms.get(nextStorm)[0] <= fr + viewLen()) nextStorm++;
        boss = null;
        bossSpawned = false;
        bossPart = 0;
        op4State = OP4_NONE; op4T = 0f; bossDissolving = false;   // (Op 4's end: the terrain back, if it had gone)
        if (terrainGone) { terrainGone = false; level.restoreGrid(); }
        for (Dissolving d : dissolving) d.d.dispose();
        dissolving.clear();
        gunLasers.clear();
        if (gunsAppear != null) { gunsAppear.dispose(); gunsAppear = null; }
        resetPods();                                  // (Eioneus: the pod room starts over)
        buildHazards();
        // the last room of stage 3: past it (a restart beyond the hold), its walls are already gone
        roomT = 0f;
        roomState = level.holdT >= 0f && t > level.holdT ? ROOM_DONE : ROOM_NONE;
        resetWalls(roomState == ROOM_DONE);
        nextSpawn = 0;
        while (nextSpawn < level.spawns.size() && level.spawns.get(nextSpawn).trigger < fr + viewLen() + 40f) nextSpawn++;
        chainRuns.clear();                            // chains: the ones the screen already passed stay gone
        for (Level.Chain c : level.chains) {
            ChainRun r = new ChainRun(); r.c = c;
            if (!level.vertical && c.x - sx < Enemy.CHAIN_START_X) r.made = c.count;
            chainRuns.add(r);
        }
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
            h.spit = v.length > 2 ? (int) v[2] : 0;
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
            // fully out, the tooth reaches right across the passage: its tip touches the floor (ceiling teeth) or the
            // ceiling (floor teeth); the rest of it stays in the rock it comes out of. The passage is measured at the
            // tooth's tip, as placed in Tiled.
            float cx = t.x + t.w / 2f, tip = t.ceiling ? t.y + 4f : t.y + t.h - 4f;
            float ceil = level.ceilBottom(cx, tip), floor = level.floorTop(cx, tip);
            if (ceil < Level.NO_CEILING && floor > Level.NO_FLOOR && ceil > floor) {
                h.baseY = t.ceiling ? floor : ceil - t.h;
                h.fangTravel = ceil - floor + Hazard.FANG_TUCK;    // hidden: the tip just inside its own rock
                h.fangCeil = ceil; h.fangFloor = floor;
                h.fangSwitch = t.switchSides;
                h.fangSink = t.sink && !t.switchSides;
                h.fangSinkWait = t.sinkWait;
                if (h.fangSwitch && h.mask != null) h.maskFlipped = h.mask.flippedV();
            } else {
                h.fangTravel = t.h + Hazard.FANG_TUCK;            // no passage found: as placed
            }
            h.fangHoldOut = t.holdOut; h.fangRest = t.rest; h.fangShort = t.shortBy;
            h.y = h.baseY + (h.fromCeil ? 1f : -1f) * (h.fangTravel - h.fangExtension());   // starts resting
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
        for (Level.Dome d : level.domes) {           // stage 2 domes: closed until they come on screen
            Hazard h = new Hazard(Hazard.Type.DOME);
            h.x = d.x; h.y = d.y; h.fromCeil = d.right;
            h.w = domeF[0].getRegionWidth(); h.h = domeF[0].getRegionHeight();   // size of a frame of sprites/dome.png
            h.domeSide = d.right ? Hazard.DOME_RIGHT : Hazard.DOME_LEFT;
            if (d.floor || d.ceiling) {              // stage 3: turned a quarter, so it is wide and flat
                float fw = h.w; h.w = h.h; h.h = fw;
                h.domeSide = d.floor ? Hazard.DOME_FLOOR : Hazard.DOME_CEIL;
                if (d.ceiling) h.y = d.top - h.h;    // hangs from the top edge of its object; floor: stands on the bottom
            }
            h.hp = Hazard.DOME_HP;
            h.timer = Hazard.DOME_OPEN_DELAY;
            h.gunHos = d.gunHos;
            hazards.add(h);
        }
        for (float[] e : level.eyes) {               // stage 2 eye rocks (the whole rock; size from sprites/rock_eye.png)
            Hazard h = new Hazard(Hazard.Type.EYE);
            h.x = e[0]; h.y = e[1]; h.w = rockEyeR.getRegionWidth(); h.h = rockEyeR.getRegionHeight();
            h.hp = Hazard.EYE_HP;
            h.timer = MathUtils.random(0.6f, 1.8f);
            hazards.add(h);
        }
    }

    @Override
    public void resize(int w, int h) { vp.update(w, h, false); }

    /** Music per stage (index = levelIdx); null = no music for that stage. */
    /** Played (once) on the game over screen. */
    private static final String GAME_OVER_MUSIC = "14. Crystal Forever.mp3";
    /** Seconds the stage music takes to fade out before the boss music starts, and the silence after it. */
    private static final float BOSS_MUSIC_FADE = 2f, BOSS_MUSIC_GAP = 0.5f;

    /** Stage music and boss music come from the map properties "music" and "bossMusic". */
    private void playStageMusic() {
        if (level.music != null && !level.music.isEmpty()) game.bgm.play(level.music, true, level.musicLoop, level.musicLoopEnd);
        else game.bgm.stop();
    }

    @Override
    public void show() {
        playStageMusic();
        if (level.bossMusic != null && !level.bossMusic.isEmpty())   // decoded in the background, ready for the boss
            game.bgm.preload(level.bossMusic, level.bossMusicLoop, level.bossMusicLoopEnd);
    }

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
                if (stateT > INTRO_BANNER + INTRO_BLACK + INTRO_HUD) setState(State.PLAY);
                break;
            case PLAY:
                if (Controls.pressed(Action.PAUSE)) {
                    setState(State.PAUSE);
                } else {
                    checkJoins();
                    updatePlay(dt, false);
                }
                break;
            case PAUSE:   // menu: CONTINUE / TITLE SCREEN
                if (updatePauseMenu()) return;          // went back to the title screen
                break;
            case DEAD:   // nobody has ships left: game over (rewinds to the checkpoint if somebody is somehow still in)
                updateFx(dt);
                if (stateT > RESPAWN_DELAY) {
                    for (Player p : players) if (p != null && p.lives < 0) p.out = true;
                    if (playersInGame() > 0) {
                        resetWorld(checkpointT);
                        playStageMusic();             // back from the boss fight: stage music again
                        setState(State.PLAY);
                    } else {
                        gameOverSel = 0;                  // (scores go in when they choose END)
                        setState(State.GAMEOVER);
                    }
                }
                break;
            case CLEAR:   // the world keeps running while the ships (and their options) fly out
                updatePlay(dt, true);
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
            case GAMEOVER:   // menu: CONTINUE (back in at the last checkpoint) / END (title screen)
                updateFx(dt);
                if (stateT > 1f && updateGameOverMenu()) return;
                break;
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

    /** Game over menu entries (Albert, 2026-10-27); gameOverSel = the one under the cursor. */
    private static final String[] GAMEOVER_MENU = {"CONTINUE", "END"};
    private int gameOverSel;

    /**
     * Game over menu. CONTINUE: every player who was in the game is back with a full set of ships (score kept, as with
     * a mid-game continue), at the last checkpoint, stage music again. END: scores in, back to the title screen.
     * Returns true when the game was left.
     */
    private boolean updateGameOverMenu() {
        if (Controls.pressed(Action.UP) || Controls.pressed(Action.DOWN)) {
            gameOverSel = (gameOverSel + 1) % GAMEOVER_MENU.length;
            a.play("hit", 0.2f);
        }
        if (!Controls.pressed(Action.FIRE) && !Controls.pressed(Action.CONFIRM)) return false;
        if (gameOverSel == 0) {
            for (Player p : players) {
                if (p == null) continue;
                p.out = false;
                p.lives = Player.startLives(game.easy);
            }
            game.bgm.stop();
            resetWorld(checkpointT);                     // (places the ships)
            playStageMusic();
            setState(State.PLAY);
            for (Player p : players) if (p != null) p.fireCd = Math.max(p.fireCd, 0.15f);   // the confirm press doesn't fire
            a.play("power", 0.4f);
            return false;
        }
        submitScores();
        game.setScreen(new TitleScreen(game));
        return true;
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
        if (s == State.GAMEOVER) game.bgm.play(GAME_OVER_MUSIC, false);   // once, not looped
        state = s;
        stateT = 0f;
    }

    private void updateFx(float dt) {
        for (Explosion e : fx) e.t += dt;
        fx.removeIf(e -> e.t * 14f >= 5f);
        for (Dissolving d : dissolving) {
            d.t += dt;
            float f = Math.min(1f, d.t / d.time);
            d.d.set(f * f);                              // (slow at first, faster and faster, as on the MSX)
        }
        dissolving.removeIf(d -> { if (!d.d.finished()) return false; d.d.dispose(); return true; });
    }

    // =============================================================== the pixel-by-pixel death (Dissolver)

    /** Pictures breaking up where they are (dissolveDeath): the copy, where it is (world, bottom-left), clock, length. */
    private static final class Dissolving { Dissolver d; float x, y, t, time; }
    private final ArrayList<Dissolving> dissolving = new ArrayList<>();

    /**
     * Death animation (Albert, 2026-10-27; Op 4's terrain, brain sphere and gun units, and for more enemies to come):
     * the picture stays where it is (world x, y: bottom-left) and breaks up pixel by pixel, in a random order and
     * faster and faster, until nothing is left after `time` s. The thing itself should be gone (or not drawn) by then;
     * this only shows its picture going. The picture is copied, so it can be changed or freed right after.
     */
    void dissolveDeath(Pixmap picture, float x, float y, float time) {
        Dissolving d = new Dissolving();
        d.d = Dissolver.of(picture);
        d.x = x; d.y = y; d.time = Math.max(0.05f, time);
        dissolving.add(d);
    }

    /** The same for a sprite frame (e.g. an enemy's current frame): its pixels are read from its texture's picture. */
    void dissolveDeath(TextureRegion frame, float x, float y, float time) {
        com.badlogic.gdx.graphics.TextureData td = frame.getTexture().getTextureData();
        if (!td.isPrepared()) td.prepare();
        Pixmap all = td.consumePixmap();
        Pixmap part = new Pixmap(frame.getRegionWidth(), frame.getRegionHeight(), Pixmap.Format.RGBA8888);
        part.setBlending(Pixmap.Blending.None);
        part.drawPixmap(all, 0, 0, frame.getRegionX(), frame.getRegionY(), frame.getRegionWidth(), frame.getRegionHeight());
        if (td.disposePixmap()) all.dispose();
        dissolveDeath(part, x, y, time);
        part.dispose();
    }

    /** An enemy dies the same way: gone at once, its picture breaking up where it was. */
    void dissolveDeath(Enemy e, TextureRegion frame, float time) {
        e.dead = true;
        dissolveDeath(frame, e.x, e.y, time);
    }

    private void drawDissolving() {
        for (Dissolving d : dissolving) d.d.draw(batch, d.x, d.y);
    }

    // =============================================================== gameplay update

    /** Speed at which the ships leave the screen after a stage is cleared (px/s). */
    private static final float FLY_OUT_SPEED = 220f;

    /**
     * One step of the game world. CLEARING = the stage-clear fly-out: everything keeps moving, but the ships
     * are flown out by themselves (no input, no firing), nothing new spawns and nobody can be hurt.
     */
    private void updatePlay(float dt, boolean clearing) {
        // --- scrolling: speed depends on the terrain section; stops at the boss arena
        // the camera follows the level's path (stage 1 also scrolls up/down); stops at the boss arena
        boolean arena = scrollT >= level.stopT;
        boolean held = level.holdT >= 0f && roomState != ROOM_DONE;   // stage 3's last room: wait there
        // a multi-boss stage: the scrolling waits while a boss that isn't the last one is being fought
        boolean bossHold = bossPartR != null && boss != null && bossPart < bossPartR.length - 1;
        float target = arena || bossHold ? 0f : level.baseSpeed * level.speedAtT(scrollT);
        amoebaClock += dt;
        bgLightsClock += dt;
        scrollSpeed += (target - scrollSpeed) * Math.min(1f, dt * 2.5f);
        if (bossHold) scrollSpeed = 0f;
        scrollT = Math.min(scrollT + scrollSpeed * dt, held ? Math.min(level.holdT, level.stopT) : level.stopT);
        if (held && scrollT >= level.holdT) scrollSpeed = 0f;   // (eases back in when it may go on)
        if (!clearing) updateRoom(dt);
        float oldX = scrollX, oldY = pathY;
        level.camAt(scrollT, camTmp);
        scrollX = camTmp[0];
        pathY = camTmp[1];
        if (!clearing) followShips(dt);
        else scrollY = MathUtils.clamp(pathY + camOff, 0f, Math.max(0f, level.worldH - H));   // don't chase the leaving ships
        // ships ride along with the path only; the follow drift must not drag them (or it would chase itself)
        float dS = scrollX - oldX, dSy = pathY - oldY;
        scrollVX = dt > 0f ? dS / dt : 0f;
        scrollVY = dt > 0f ? dSy / dt : 0f;
        for (float cp : level.checkpoints) if (scrollT >= cp) checkpointT = Math.max(checkpointT, cp);
        if (op4End()) updateOp4End(dt);              // (Op 4: the terrain dissolves, then its two bosses come)
        else if (eioEnd()) updateEioneusEnd(dt);     // (Eioneus: the gun pods, the terrain dissolves, then the boss)
        else if (scrollT >= level.stopT && !bossSpawned) spawnBoss();
        // a boss that sits in the map (map object "boss" with "fixed" on) wakes up as soon as it comes on screen
        if (op4End()) {
            // (updateOp4End brings Op 4's bosses on)
        } else if (!bossSpawned && level.bossFixed && bossPartR != null) {   // a multi-boss stage: the next one
            bossPartBox();
            boolean last = bossPart >= bossPartR.length - 1, across = partBox[0] < scrollX + W && partBox[0] + partBox[2] > scrollX;
            boolean wake = last ? partBox[1] < scrollY + H && partBox[1] + partBox[3] > scrollY   // (the last one: as soon as it shows)
                    : partBox[1] + partBox[3] <= scrollY + H - BOSS_PART_TOP && partBox[1] >= scrollY;   // (the others: once it is all on screen)
            if (across && wake) spawnBoss();
        } else if (!bossSpawned && level.bossFixed && level.bossX < scrollX + W && level.bossX + level.bossW > scrollX
                && level.bossY < scrollY + H && level.bossY + level.bossH > scrollY) spawnBoss();
        // the boss music starts before the boss comes: where the map's "bossmusic" object is on the path (or the old
        // map property "bossMusicAt", an x), the stage music fades out; the boss music follows and plays into the fight
        boolean bossMusicHere = level.bossMusicT >= 0f ? scrollT >= level.bossMusicT
                : level.bossMusicAt >= 0f && scrollX + W / 2f >= level.bossMusicAt;
        if (bossMusicHere && !bossSpawned && level.bossMusic != null && !level.bossMusic.isEmpty()) game.bgm.fadeOutThen(level.bossMusic, true, level.bossMusicLoop, level.bossMusicLoopEnd, BOSS_MUSIC_FADE, BOSS_MUSIC_GAP);

        // --- asteroid storm: starts when the front edge of the screen reaches its map object
        if (!clearing) updateStorm(dt);

        // --- spawning
        while (!clearing && nextSpawn < level.spawns.size() && level.spawns.get(nextSpawn).trigger <= front() + viewLen() + 40f) {
            spawnEnemy(level.spawns.get(nextSpawn++));
        }
        if (!clearing) { growChains(dt); releaseNestBugs(dt); }

        for (Player p : players) {
            if (p == null || p.out) continue;
            if (clearing) { if (!p.dead) flyOut(p, dt, dS, dSy); }   // a downed ship doesn't come back now
            else if (p.dead) updateRespawn(p, dt);
            else updatePlayer(p, dt, dS, dSy);
            if (!p.dead) updateOptions(p, dt);
        }

        // --- world objects
        for (Bullet b : pBullets) {
            if (b.homing) aimHoming(b);
            boolean fresh = b.fresh;
            b.update(dt, level);
            if (!fresh && b.ridesScreen()) {          // moves relative to the screen, like the ships
                if (b.kind == Bullet.Kind.LASER && b.attached) { if (b.vert) b.h += dSy; else b.w += dS; }   // (only its front)
                else { b.x += dS; b.y += dSy; }
            }
            if (b.kind == Bullet.Kind.RIPPLE) growRipple(b, dt);
            if (b.kind == Bullet.Kind.LASER) detachLaser(b);
            // a player shot is gone once it is completely off screen (so the on-screen limits really mean on screen)
            if (b.x > scrollX + W || b.x + b.w < scrollX || b.y > scrollY + H || b.y + b.h < scrollY) b.dead = true;
        }
        steerGuardBeams(dt);
        for (Bullet b : eBullets) {
            boolean fresh = b.fresh;
            b.update(dt, level);
            if (!fresh && b.ridesScreen()) { b.x += dS; b.y += dSy; }   // moves relative to the screen, as on the MSX
            if (b.omenLaser) growOmenLaser(b, dt);
            if (b.x > scrollX + W + 80f || b.x < scrollX - 60f || b.y < scrollY - 30f || b.y > scrollY + H + 60f) b.dead = true;
        }
        updateGuardVolley(dt);
        for (Enemy e : enemies) {
            e.update(this, dt);
            // vertical limits follow the whole lane, not the view, so enemies the camera drifted away from survive
            float laneB = pathY - laneSlack(), laneT = pathY + H + laneSlack();
            boolean gone = level.vertical   // vertical stage: gone once below the screen (or far off it)
                    ? e.y + e.h < scrollY - 40f || e.y > scrollY + H + 300f || e.x + e.w < scrollX - 80f || e.x > scrollX + W + 80f
                      || (e.type == Enemy.Type.FROST && e.t > 1f && e.y > scrollY + H + 4f)   // a frost left through the top again
                      || (e.type == Enemy.Type.POSSESSED && e.possGone(this))
                      || (e.type == Enemy.Type.WHIPLASH && e.whipGone())
                      || (e.type == Enemy.Type.BATTLEAXE && e.axeGone(this))
                      || (e.type == Enemy.Type.UG && e.ugGone())
                      || (e.type == Enemy.Type.GREEN_FLYER && e.greenGone(this))
                    : Math.max(e.x + e.w, e.batWall ? e.beamX + e.beamW : 0f) < scrollX - 40f   // (a wall laser: its beam too)
                      || e.x > scrollX + W + 300f || (!e.anchored() && (e.y < laneB - 60f || e.y > laneT + 60f))
                      || (e.type == Enemy.Type.UG && e.ugGone())
                      || (e.type == Enemy.Type.WALKER && e.leaperGone(this))                      // Kierke: walked off an edge
                      || (e.type == Enemy.Type.POSSESSED && e.possRow && e.possGone(this))   // a row left through the top
                      || (e.type == Enemy.Type.FROST && e.t > 0f && e.x > scrollX + W + 20f);   // left again at the right edge
            if (gone) {
                e.dead = true;
                if (e.group != null) e.group.lost = true;
            }
        }
        for (Hazard h : hazards) {
            if (h.dead) continue;
            boolean near = level.vertical ? h.y < scrollY + H + 80f && h.y + h.h > scrollY - 80f
                    : h.x < scrollX + W + 80f && h.x + 40f > scrollX - 80f;
            if (near) h.update(this, dt);
        }
        flushWaves();                                 // (what the towers and domes let out)
        for (Meteor m : meteors) {
            m.update(dt);
            if (!m.red) {                            // a storm rock: walls and other solid scenery stop it (Albert, 2026-10-29)
                float in = m.inset();
                if (!m.dead && level.hits(scrollX + m.sx + in, scrollY + m.sy + in, m.w - 2 * in, m.h - 2 * in)) {
                    if (m.big) splitMeteor(m);           // a big one splits, as when it is shot (its pieces may meet the rock too)
                    else {
                        m.dead = true;
                        boom(scrollX + m.sx + m.w / 2f, scrollY + m.sy + m.h / 2f, 0.6f);
                    }
                }
                continue;
            }                                        // side volcano rock: it moves through the world
            m.sx = m.wx - scrollX; m.sy = m.wy - scrollY;
            if (m.sy + m.h < -8f || m.sy > H + 8f || m.sx + m.w < -8f || m.sx > W + 8f) m.dead = true;
            // falling back toward its wall: gone once it is back in the rock
            else if (m.vx * m.ax > 0f && level.hits(m.wx + 5f, m.wy + 5f, m.w - 10f, m.h - 10f)) m.dead = true;
        }
        for (Capsule c : caps) {
            c.t += dt;                               // stays where it was dropped: only the scrolling moves it
        }
        for (Capsule c : caps) {                     // a green capsule that left the screen comes back once, at the far edge
            if (!c.green || c.wrapped || c.dead()) continue;
            if (level.vertical && c.y < scrollY - capH) {
                c.y = scrollY + H; c.x = MathUtils.clamp(c.x, scrollX, scrollX + W - capW); c.wrapped = true;
            } else if (!level.vertical && c.x < scrollX - capW) {
                c.x = scrollX + W; c.y = MathUtils.clamp(c.y, scrollY, scrollY + H - capH); c.wrapped = true;
            }
        }
        caps.removeIf(c -> level.vertical ? c.y < scrollY - capH - 8f : c.x < scrollX - capW - 8f);
        if (boss != null) { boss.update(this, dt); updateCoreBlocks(); }
        if (boss != null && boss.dying && boss.dissolve && !bossDissolving) bossDissolveStart();
        updateGunLasers(dt, clearing);
        updateLedZeps(dt);
        regrowBricks(dt);
        updateFx(dt);

        collide();

        pBullets.removeIf(b -> b.dead);
        eBullets.removeIf(b -> b.dead);
        enemies.removeIf(e -> e.dead);
        hazards.removeIf(h -> h.dead);
        hazards.addAll(pendingHazards);
        pendingHazards.clear();
        meteors.removeIf(m -> m.dead);
        meteors.addAll(pendingMeteors);
        pendingMeteors.clear();
    }

    /** Asteroid storm (map object "meteors"): starts it when the screen gets there, and drops rocks while it lasts. */
    private void updateStorm(float dt) {
        if (rockSmallF == null) return;              // (no rock pictures: no storms)
        if (nextStorm < level.storms.size() && level.storms.get(nextStorm)[0] <= front() + viewLen()) {
            storm = level.storms.get(nextStorm++);
            stormLeft = storm[1];
            stormSpawnT = 0f;
            stormSmallRun = MathUtils.random(0, Meteor.BIG_EVERY - 1);   // the first big one doesn't always come at once
        }
        if (stormLeft <= 0f) return;
        stormLeft -= dt;
        stormSpawnT -= dt;
        if (stormSpawnT > 0f) return;
        stormSpawnT = MathUtils.random(Meteor.SPAWN_MIN, Meteor.SPAWN_MAX);
        boolean big = rockBigR != null && stormSmallRun >= Meteor.BIG_EVERY - 1 && MathUtils.randomBoolean(0.5f);   // 5 small per big on average
        stormSmallRun = big ? 0 : stormSmallRun + 1;
        Meteor m = big ? new Meteor(true, rockBigR.getRegionWidth(), rockBigR.getRegionHeight())
                : new Meteor(false, rockSmallF[0].getRegionWidth(), rockSmallF[0].getRegionHeight());
        boolean slant = levelIdx == 1;                   // only stage 2's rocks slant; everywhere else straight down (Albert, 2026-10-29)
        if (big) m.aim(Meteor.BIG_SPEED, slant ? MathUtils.random(-Meteor.BIG_ANGLE_MAX, Meteor.BIG_ANGLE_MAX) : 0f);
        else if (storm != null && storm[2] > 0f)         // (Op 4: one speed, maybe all straight down)
            m.aim(storm[2], storm[5] > 0f || !slant ? 0f : Meteor.randomSmallAngle());
        else m.aim(MathUtils.random(Meteor.SMALL_SPEED_MIN, Meteor.SMALL_SPEED_MAX), slant ? Meteor.randomSmallAngle() : 0f);
        // just above the top edge; one going sideways starts toward the side it comes from, so it crosses the screen
        float drift = m.vx * (H / -m.vy) * 0.5f;
        boolean band = storm != null && storm[4] > storm[3];   // (Op 4: only in a band of the screen, as on the MSX)
        m.sy = H;
        float in = m.inset();
        for (int tries = 0; ; tries++) {                 // not over solid rock at the top edge (it would burst at once)
            m.sx = band ? MathUtils.random(storm[3], storm[4] - m.w) : MathUtils.random(0f, W - m.w) - drift;
            m.sx = MathUtils.clamp(m.sx, -m.w / 2f, W - m.w / 2f);
            if (!level.hits(scrollX + m.sx + in, scrollY + H - m.h, m.w - 2 * in, m.h)) break;
            if (tries >= 8) return;                      // (nowhere free: no rock this time)
        }
        meteors.add(m);
    }

    /**
     * A side volcano (Eioneus) throws a red rock out of its rock tip at (x, y), to the left (dir -1) or right (+1): it
     * flies out, slows down, and falls back toward the wall it came from. It hurts a ship, a guard breaks it, and one
     * shot destroys it (Albert, 2026-10-27).
     */
    /** Points for shooting down a side volcano's red rock. */
    private static final int VOLCANO_ROCK_SCORE = 50;

    void volcanoRock(float x, float y, int dir) {
        if (rockRedF == null) return;
        Meteor m = new Meteor(false, rockRedF[0].getRegionWidth(), rockRedF[0].getRegionHeight());
        m.red = true;
        m.frame = MathUtils.random(rockRedF.length - 1);
        m.wx = x - m.w / 2f; m.wy = y - m.h / 2f;
        m.vx = dir * MathUtils.random(Meteor.SIDE_SPEED_MIN, Meteor.SIDE_SPEED_MAX);
        m.ax = -dir * Meteor.SIDE_PULL;
        m.vy = MathUtils.random(Meteor.SIDE_VY_MIN, Meteor.SIDE_VY_MAX);
        m.sx = m.wx - scrollX; m.sy = m.wy - scrollY;
        meteors.add(m);
    }

    /** A big rock was shot: it splits into three small ones, straight down and SPLIT_ANGLE degrees to either side. */
    private void splitMeteor(Meteor m) {
        m.dead = true;
        float cx = m.sx + m.w / 2f, cy = m.sy + m.h / 2f;
        float speed = Meteor.BIG_SPEED * Meteor.SPLIT_SPEED_MUL;
        float sw = rockSmallF[0].getRegionWidth(), sh = rockSmallF[0].getRegionHeight();
        for (int i = -1; i <= 1; i++) {
            Meteor p = new Meteor(false, sw, sh);
            p.sx = cx - sw / 2f + i * 6f;
            p.sy = cy - sh / 2f;
            p.aim(speed, i * Meteor.SPLIT_ANGLE);
            pendingMeteors.add(p);
        }
        boom(scrollX + cx, scrollY + cy, 0.8f);
        a.play("boom", 0.18f);
    }

    /** A guard (SHIELD / F.FIELD) broke a rock. */
    private void shatterMeteor(Meteor m) {
        m.dead = true;
        boom(scrollX + m.sx + m.w / 2f, scrollY + m.sy + m.h / 2f, m.w / 20f);
        a.play("boom", 0.2f);
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
        float sp = 75f + 34f * p.speedLvl;   // starting speed 75 px/s (was 125), +34 per SPEED level
        float dx = 0f, dy = 0f;
        if (Controls.held(id, Action.LEFT)) dx -= 1f;
        if (Controls.held(id, Action.RIGHT)) dx += 1f;
        if (Controls.held(id, Action.UP)) dy += 1f;
        if (Controls.held(id, Action.DOWN)) dy -= 1f;
        // aiming: hold AIM (Enter / pad R1) and the direction sets DOUBLE's extra shot's angle, or where LASER and RIPPLE
        // fire (Albert, 2026-10-27: the whole beam / ring goes that way); the ship holds its position while aiming
        p.aiming = (p.weapon == Player.W_DOUBLE || p.weapon == Player.W_LASER || p.weapon == Player.W_RIPPLE) && Controls.held(id, Action.AIM);
        if (p.aiming) {
            if (dx != 0f || dy != 0f) {
                // (0 = straight ahead: right, or up on a vertical stage)
                int d = (Math.round(MathUtils.atan2(dy, dx) / (MathUtils.PI / 4f)) - (level.vertical ? 2 : 0)) & 7;
                if (p.weapon == Player.W_DOUBLE) p.doubleDir = d; else p.laserDir = d;
            }
            dx = 0f;
            dy = 0f;
        }
        if (dx != 0f && dy != 0f) { dx *= 0.7071f; dy *= 0.7071f; }
        p.x += dx * sp * dt + dS;
        p.y += dy * sp * dt + dSy;
        if (level.vertical) {   // the ship may touch every edge
            p.x = MathUtils.clamp(p.x, scrollX - Player.VART_L, scrollX + W - Player.VART_R);
            p.y = MathUtils.clamp(p.y, scrollY - Player.VART_B, scrollY + H - Player.VART_T);
        } else {
            p.x = MathUtils.clamp(p.x, scrollX - Player.ART_L, scrollX + W - Player.ART_R);
            p.y = MathUtils.clamp(p.y, scrollY - Player.ART_B, scrollY + H - Player.ART_T);
        }
        if (level.vertical) p.tilt = dx < 0f ? Player.TILT_LEFT : (dx > 0f ? Player.TILT_RIGHT : 0);   // banks sideways
        else p.tilt = dy > 0f ? 1 : (dy < 0f ? 2 : 0);

        p.invuln = Math.max(0f, p.invuln - dt);
        p.ghostT = Math.max(0f, p.ghostT - dt);
        p.ffieldHitT = Math.max(0f, p.ffieldHitT - dt);
        p.shieldHitT = Math.max(0f, p.shieldHitT - dt);
        p.fireCd -= dt;
        p.missCd -= dt;

        recordTrail(p, dt);
        steerLasers(p);
        emitAimBeams(p, dt);
        boolean fire = Controls.held(id, Action.FIRE);
        if (fire && p.fireCd <= 0f && shoot(p)) p.fireCd = FIRE_INTERVAL;
        if (fire && p.missile && p.missCd <= 0f && launchMissiles(p)) p.missCd = FIRE_INTERVAL;
        if (Controls.pressed(id, Action.POWER)) activateMeter(p);
        if (Controls.pressed(id, Action.OPT_ORBIT)) toggleOptMode(p, Player.OPT_ORBIT);
        if (Controls.pressed(id, Action.OPT_V)) toggleOptMode(p, Player.OPT_V);
        if (Controls.pressed(id, Action.OPT_FREEZE)) toggleOptMode(p, Player.OPT_FREEZE);
    }

    /** Options follow the ship's path. Only record when the ship actually moved on screen,
     *  so the options stay where they are while the ship stands still. */
    private void recordTrail(Player p, float dt) {
        p.trailAcc += dt;
        while (p.trailAcc >= 1f / 60f) {
            float sx = p.x - scrollX, sy = p.y - pathY;     // path-relative, so options ride along with the camera path
            if (Math.abs(sx - p.trailX(0)) > 0.25f || Math.abs(sy - p.trailY(0)) > 0.25f) p.record(sx, sy);
            p.trailAcc -= 1f / 60f;
        }
    }

    /** Stage clear: the ship flies straight out of the screen on its own, its options trailing behind. */
    private void flyOut(Player p, float dt, float dS, float dSy) {
        p.aiming = false;
        p.tilt = 0;
        if (level.vertical) { p.x += dS; p.y += FLY_OUT_SPEED * dt + dSy; }
        else { p.x += FLY_OUT_SPEED * dt + dS; p.y += dSy; }
        p.invuln = Math.max(0f, p.invuln - dt);
        p.ghostT = Math.max(0f, p.ghostT - dt);
        p.ffieldHitT = Math.max(0f, p.ffieldHitT - dt);
        p.shieldHitT = Math.max(0f, p.shieldHitT - dt);
        recordTrail(p, dt);
        steerLasers(p);
    }

    float optX(Player p, int i) { return scrollX + (p.optReady && i < p.optKnown ? p.optSX[i] : p.trailX(10 * (i + 1)) + 10f); }
    float optY(Player p, int i) { return pathY + (p.optReady && i < p.optKnown ? p.optSY[i] : p.trailY(10 * (i + 1)) + 2f); }

    // ---- option formations (Albert, 2026-10-28): R2 orbit, L1 V formation, L2 freeze; the same button again = normal
    /** How fast an option glides to its new place when the formation changes (px/s). */
    private static final float OPT_GATHER = 260f;
    /** Orbit: radius (px, ship's middle to option's middle) and turning speed (rad/s). */
    private static final float ORBIT_R = 26f, ORBIT_SPEED = 3.2f;
    /** V formation: each pair of options V_BACK px further back and V_SIDE px further out than the one before. */
    private static final float V_BACK = 14f, V_SIDE = 16f;
    /** An option's middle in its picture (16x10). */
    private static final float OPT_MID_X = 8f, OPT_MID_Y = 5f;

    /** Switches to a formation, or back to the normal trail if it is the one already on. */
    private void toggleOptMode(Player p, int mode) {
        int m = p.optMode == mode ? Player.OPT_TRAIL : mode;
        float scx = p.x - scrollX + 16f, scy = p.y - pathY + 8f;   // the ship's middle (path-relative)
        for (int i = 0; i < Player.MAX_OPTIONS; i++) p.optLock[i] = false;
        if (m == Player.OPT_FREEZE)                        // held where they are now
            for (int i = 0; i < p.optKnown; i++) { p.optFX[i] = p.optSX[i] - scx; p.optFY[i] = p.optSY[i] - scy; }
        if (m == Player.OPT_ORBIT && p.optKnown > 0)       // the circle starts where the first option is
            p.orbitAng = MathUtils.atan2(p.optSY[0] + OPT_MID_Y - scy, p.optSX[0] + OPT_MID_X - scx);
        p.optMode = m;
        a.play("hit", 0.2f);
    }

    /** Every frame: where each option should be in the formation, and gliding there (or there already). */
    private void updateOptions(Player p, float dt) {
        if (p.optMode == Player.OPT_ORBIT) p.orbitAng += ORBIT_SPEED * dt;
        float scx = p.x - scrollX + 16f, scy = p.y - pathY + 8f;
        for (int i = 0; i < p.options; i++) {
            float tx, ty;
            if (p.optMode == Player.OPT_ORBIT) {
                float ang = p.orbitAng + i * MathUtils.PI2 / p.options;
                tx = scx + MathUtils.cos(ang) * ORBIT_R - OPT_MID_X;
                ty = scy + MathUtils.sin(ang) * ORBIT_R - OPT_MID_Y;
            } else if (p.optMode == Player.OPT_V) {           // pairs, one on each side, further back each time
                int k = i / 2 + 1, side = (i & 1) == 0 ? 1 : -1;
                if (level.vertical) { tx = scx + side * V_SIDE * k - OPT_MID_X; ty = scy - V_BACK * k - OPT_MID_Y; }   // beside it
                else { tx = scx - V_BACK * k - OPT_MID_X; ty = scy + side * V_SIDE * k - OPT_MID_Y; }               // above / below
            } else if (p.optMode == Player.OPT_FREEZE && i < p.optKnown) {
                tx = scx + p.optFX[i]; ty = scy + p.optFY[i];
            } else {
                if (p.optMode == Player.OPT_FREEZE) {          // (a new option while frozen: it joins at its trail place)
                    p.optFX[i] = p.trailX(10 * (i + 1)) + 10f - scx; p.optFY[i] = p.trailY(10 * (i + 1)) + 2f - scy;
                }
                tx = p.trailX(10 * (i + 1)) + 10f; ty = p.trailY(10 * (i + 1)) + 2f;
            }
            if (!p.optReady || i >= p.optKnown || p.optLock[i]) {   // (new, or in its place: just there)
                p.optSX[i] = tx; p.optSY[i] = ty; p.optLock[i] = true;
                continue;
            }
            float dx = tx - p.optSX[i], dy = ty - p.optSY[i], d = (float) Math.sqrt(dx * dx + dy * dy), step = OPT_GATHER * dt;
            if (d <= step) { p.optSX[i] = tx; p.optSY[i] = ty; p.optLock[i] = true; }
            else { p.optSX[i] += dx / d * step; p.optSY[i] += dy / d * step; }
        }
        p.optKnown = p.options;
        p.optReady = true;
    }

    // Shots are fired from the "nose": the front tip of the ship art (x 7..23 of the 32px frame,
    // centre line at y 8) or the right edge of an option.
    private static final float NOSE_X = 24f, NOSE_Y = 8f;

    /** Fastest a ship or option may fire: 8 shots per second, as long as it is under its on-screen limit. */
    public static final float FIRE_INTERVAL = 1f / 8f;
    /** On-screen limits per gun (the ship and each option count separately). With DOUBLE the forward
     *  and the angled shots each get their own, smaller limit. */
    public static final int MAX_SHOTS = 4, MAX_DOUBLE_SHOTS = 2, MAX_LASERS = 1, MAX_RIPPLES = 1, MAX_MISSILES = 2, MAX_TWO_WAY = 1, MAX_HOMING = 1;

    /** Fires every gun (ship + options) that is under its limit. Returns true if anything was fired. */
    private boolean shoot(Player p) {
        boolean fired;
        if (level.vertical) {   // vertical stage: the nose is at the top of the picture, options fire from their top
            fired = fireGun(p, 0, p.x + 16f, p.y + Player.VART_T);
            for (int i = 0; i < p.options; i++) fired |= fireGun(p, i + 1, optX(p, i) + 6f, optY(p, i) + 14f);
        } else {
            fired = fireGun(p, 0, p.x + NOSE_X, p.y + NOSE_Y);
            for (int i = 0; i < p.options; i++) fired |= fireGun(p, i + 1, optX(p, i) + 14f, optY(p, i) + 6f);
        }
        if (fired) a.play("shoot", 0.15f);
        return fired;
    }

    /** DOUBLE aimed straight forward works exactly like the normal shot (one shot, limit MAX_SHOTS). */
    private static boolean doubleActive(Player p) { return p.weapon == 1 && p.doubleDir != 0; }

    /** One gun: fires whichever of its shots are under their on-screen limit. */
    private boolean fireGun(Player p, int src, float nx, float ny) {
        if (p.weapon == 2) {
            if (onScreen(p, src, Bullet.Kind.LASER, false) + aimBeams(p, src) >= MAX_LASERS) return false;
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
        if (level.vertical) {   // vertical stage: from the sides of the ship / option (y = the missile's middle)
            boolean fired = launchFromV(p, 0, p.x + Player.VART_R - 2f, p.x + Player.VART_L + 2f, p.y + 6f, p.x + 16f);
            for (int i = 0; i < p.options; i++)
                fired |= launchFromV(p, i + 1, optX(p, i) + 10f, optX(p, i) + 2f, optY(p, i) + 6f, optX(p, i) + 6f);
            return fired;
        }
        float mh = missR.getRegionHeight();
        boolean fired = launchFrom(p, 0, p.x + 14f, p.y + Player.ART_B, p.y + Player.ART_T - mh, p.y + 8f);
        for (int i = 0; i < p.options; i++) fired |= launchFrom(p, i + 1, optX(p, i) + 2f, optY(p, i), optY(p, i) + 12f - mh, optY(p, i) + 6f);
        return fired;
    }

    /**
     * One gun's missiles. Normal: one missile toward the nearer edge of the screen (down if the gun is in the lower
     * half, up if in the upper half), up to MAX_MISSILES on screen; HOMING the same, up to MAX_HOMING. 2-WAY: one down and one up, each direction with
     * its own limit of MAX_TWO_WAY. yDown / yUp = launch heights, gunY = the gun's middle.
     */
    private boolean launchFrom(Player p, int src, float x, float yDown, float yUp, float gunY) {
        if (!p.twoWay() || p.homing()) {   // (HOMING: one missile at a time, MAX_HOMING, fired like the normal one)
            int max = p.homing() ? MAX_HOMING : MAX_MISSILES;
            if (onScreen(p, src, Bullet.Kind.MISSILE, false) + onScreen(p, src, Bullet.Kind.MISSILE, true) >= max) return false;
            // nearer the top edge? (the playfield runs from scrollY, the top of the HUD strip, to scrollY + H: the
            // middle of what you can fly in, not of the whole screen with the HUD)
            boolean up = gunY > scrollY + H / 2f;
            launchMissile(p, src, x, up ? yUp : yDown, up);
            return true;
        }
        boolean fired = false;
        if (onScreen(p, src, Bullet.Kind.MISSILE, false) < MAX_TWO_WAY) { launchMissile(p, src, x, yDown, false); fired = true; }
        if (onScreen(p, src, Bullet.Kind.MISSILE, true) < MAX_TWO_WAY) { launchMissile(p, src, x, yUp, true); fired = true; }
        return fired;
    }

    /**
     * Vertical stage: one missile up and to the right, up to MAX_MISSILES on screen; 2-WAY: one to the right and one
     * to the left, each with its own limit of MAX_TWO_WAY. xr / xl = launch points (centre) on the right / left side.
     */
    private boolean launchFromV(Player p, int src, float xr, float xl, float y, float gunX) {
        if (!p.twoWay() || p.homing()) {   // normal MISSILE / HOMING: toward the nearer side edge (left half: left, right half: right)
            int max = p.homing() ? MAX_HOMING : MAX_MISSILES;
            if (onScreen(p, src, Bullet.Kind.MISSILE, false) + onScreen(p, src, Bullet.Kind.MISSILE, true) >= max) return false;
            boolean left = gunX < scrollX + W / 2f;
            launchMissileV(p, src, left ? xl : xr, y, left);
            return true;
        }
        boolean fired = false;
        if (onScreen(p, src, Bullet.Kind.MISSILE, false) < MAX_TWO_WAY) { launchMissileV(p, src, xr, y, false); fired = true; }
        if (onScreen(p, src, Bullet.Kind.MISSILE, true) < MAX_TWO_WAY) { launchMissileV(p, src, xl, y, true); fired = true; }
        return fired;
    }

    /** Vertical missile: a small square hit box (it turns as it goes), see Bullet.wallMove. left = 2-WAY's second one. */
    private static final float VMISSILE_BOX = 4f;

    private void launchMissileV(Player p, int src, float cx, float cy, boolean left) {
        float s = VMISSILE_BOX;
        Bullet m = new Bullet(Bullet.Kind.MISSILE, cx - s / 2f, cy - s / 2f, s, s);
        m.vx = left ? -1f : 1f;                             // (direction only: Bullet.MISSILE_SPEED sets the speed)
        m.vy = 1f;
        m.dmg = 3;
        m.up = left;
        m.vert = true;
        m.homing = p.homing();
        m.owner = p; m.src = src;
        if (level.hitsMap(m.x, m.y, m.w, m.h)) return;      // launched inside rock (outside the map is open space)
        pBullets.add(m);
    }

    /**
     * Lasers stay level with the gun that fired them: when the ship or option moves up or down, so does its beam.
     * While a beam is still attached (growing out of the gun) its tail also stays at the muzzle; the front keeps its
     * own position, so the beam gets longer or shorter as the gun moves back or forward.
     */
    private void steerLasers(Player p) {
        for (int i = 0, k = pBullets.size(); i < k; i++) {
            Bullet b = pBullets.get(i);
            if (b.dead || b.owner != p || b.kind != Bullet.Kind.LASER || b.stuck || b.seg) continue;   // (stuck: pinned where it hit; seg: aimed)
            if (b.src > p.options) { b.attached = false; continue; }   // its option is gone: the beam flies on alone
            if (level.vertical) {   // (vertical stage: in line with it, left / right)
                b.x = (b.src == 0 ? p.x + 16f : optX(p, b.src - 1) + 6f) - b.w / 2f;
                if (b.attached) {
                    float top = b.y + b.h, tail = b.src == 0 ? p.y + Player.VART_T : optY(p, b.src - 1) + 14f;
                    b.y = tail;
                    b.h = Math.max(1f, top - tail);
                }
                continue;
            }
            b.y = (b.src == 0 ? p.y + NOSE_Y : optY(p, b.src - 1) + 6f) - b.h / 2f;
            if (b.attached) {
                float front = b.x + b.w, tail = b.src == 0 ? p.x + NOSE_X : optX(p, b.src - 1) + 14f;
                b.x = tail;
                b.w = Math.max(1f, front - tail);
            }
        }
    }

    /** An attached laser whose front has reached the edge of the screen (or whose ship is gone) lets go of its gun. */
    private void detachLaser(Bullet b) {
        if (!b.attached) return;
        if (b.owner == null || b.owner.dead || b.owner.out
                || (b.vert ? b.y + b.h >= scrollY + H : b.x + b.w >= scrollX + W)) b.attached = false;
    }

    /**
     * How many of this gun's bullets of this kind are still flying. alt = count the second stream
     * instead: DOUBLE's angled shots, or 2-WAY's upward missiles.
     */
    private int onScreen(Player p, int src, Bullet.Kind kind, boolean alt) {
        int n = 0;
        for (int i = 0, k = pBullets.size(); i < k; i++) {
            Bullet b = pBullets.get(i);
            if (!b.dead && !b.seg && b.owner == p && b.src == src && b.kind == kind && (b.angled || b.up) == alt) n++;
        }
        return n;
    }

    // (nx, ny) = the muzzle: shots start with their tip there, so they come out of the ship, not ahead of it.
    private void fireLaser(Player p, int src, float nx, float ny) {
        if (p.laserDir != 0) { startAimBeam(p, src, nx, ny); return; }   // (aimed: see AimBeam)
        float lh = laserR.getRegionHeight();   // beam thickness (and hit height) from sprites/laser.png
        if (level.vertical) {
            Bullet b = new Bullet(Bullet.Kind.LASER, nx - lh / 2f, ny, lh, Bullet.LASER_START_LEN);
            b.dmg = Bullet.LASER_DAMAGE;
            b.vy = Bullet.LASER_SPEED;
            b.vert = true;
            b.attached = true;
            b.owner = p; b.src = src;
            pBullets.add(b);
            return;
        }
        Bullet b = new Bullet(Bullet.Kind.LASER, nx, ny - lh / 2f, Bullet.LASER_START_LEN, lh);
        b.dmg = Bullet.LASER_DAMAGE;
        b.vx = Bullet.LASER_SPEED;
        b.attached = true;
        b.owner = p; b.src = src;
        pBullets.add(b);
    }

    private void fireRipple(Player p, int src, float nx, float ny) {
        if (p.laserDir != 0) { fireAimedRipple(p, src, nx, ny); return; }
        if (level.vertical) {   // lies across the way up: widens sideways as it climbs
            Bullet b = new Bullet(Bullet.Kind.RIPPLE, nx - Bullet.RIPPLE_START_H / 2f, ny - Bullet.RIPPLE_START_W,
                    Bullet.RIPPLE_START_H, Bullet.RIPPLE_START_W);
            b.vy = Bullet.RIPPLE_SPEED;
            b.dmg = Bullet.RIPPLE_DAMAGE;
            b.vert = true;
            b.owner = p; b.src = src;
            pBullets.add(b);
            return;
        }
        Bullet b = new Bullet(Bullet.Kind.RIPPLE, nx - Bullet.RIPPLE_START_W, ny - Bullet.RIPPLE_START_H / 2f,
                Bullet.RIPPLE_START_W, Bullet.RIPPLE_START_H);
        b.vx = Bullet.RIPPLE_SPEED;
        b.dmg = Bullet.RIPPLE_DAMAGE;
        b.owner = p; b.src = src;
        pBullets.add(b);
    }

    // Shots take their size from sprites/shot.png (the picture is the hitbox), tip at the muzzle.
    private void fireShot(Player p, int src, float nx, float ny) {
        float sw = shotR.getRegionWidth(), sh = shotR.getRegionHeight();
        if (level.vertical) {   // straight up (drawn turned)
            Bullet b = new Bullet(Bullet.Kind.SHOT, nx - (int) (sh / 2f), ny - sw, sh, sw);
            b.vy = Bullet.SHOT_SPEED;
            b.vert = true;
            b.owner = p; b.src = src;
            pBullets.add(b);
            return;
        }
        Bullet b = new Bullet(Bullet.Kind.SHOT, nx - sw, ny - (int) (sh / 2f), sw, sh);
        b.vx = Bullet.SHOT_SPEED;
        b.owner = p; b.src = src;
        pBullets.add(b);
    }

    /** DOUBLE's extra shot, in the direction chosen with AIM. */
    private void fireAngled(Player p, int src, float nx, float ny) {
        float ang = doubleAngle(p);
        float s = shotR.getRegionWidth();               // square hitbox as wide as the shot, so any angle is covered
        Bullet d = level.vertical ? new Bullet(Bullet.Kind.SHOT, nx - s / 2f, ny - s, s, s)
                : new Bullet(Bullet.Kind.SHOT, nx - s, ny - s / 2f, s, s);
        d.vx = MathUtils.cos(ang) * Bullet.SHOT_SPEED;      // same speed as the normal shot, in the aimed direction
        d.vy = MathUtils.sin(ang) * Bullet.SHOT_SPEED;
        d.owner = p; d.src = src; d.angled = true;
        pBullets.add(d);
    }

    /** DOUBLE's direction in radians: doubleDir steps of 45 degrees counter-clockwise from straight ahead. */
    private float doubleAngle(Player p) {
        return (p.doubleDir + (level.vertical ? 2 : 0)) * MathUtils.PI / 4f;
    }

    /** An aim direction (0 = straight ahead, then every 45 degrees counter-clockwise) in radians, 0 = right. */
    private float aimAngle(int dir) { return (dir + (level.vertical ? 2 : 0)) * MathUtils.PI / 4f; }

    // ---- aimed LASER and RIPPLE (AIM held; Albert, 2026-10-27) -----------------------------------------------------
    /**
     * An aimed laser beam: while it is coming out (emit s left; as long as the front takes to reach the screen's edge,
     * like the straight beam, which lets go of its gun there) its gun lets out a piece every frame (Bullet.seg),
     * AIM_PIECE px of box, all flying the aimed way at LASER_SPEED. The pieces share hits (each target is hurt once
     * per beam). A beam counts toward its gun's MAX_LASERS while it is coming out or any piece is left.
     */
    private static final class AimBeam { Player p; int src; float dx, dy, emit; ArrayList<Object> hits = new ArrayList<>(); }
    private final ArrayList<AimBeam> aimBeamList = new ArrayList<>();
    private static final float AIM_PIECE = 8f, AIM_PIECE_LEN = Bullet.LASER_SPEED / 60f + 3f;
    /** A diagonal ripple grows to this size across its way. */
    private static final float RIPPLE_DIAG_MAX = 96f;

    private void startAimBeam(Player p, int src, float nx, float ny) {
        AimBeam ab = new AimBeam();
        ab.p = p; ab.src = src;
        float ang = aimAngle(p.laserDir);
        ab.dx = MathUtils.cos(ang); ab.dy = MathUtils.sin(ang);
        // how long until the front reaches the edge of the screen
        float tx = ab.dx > 0.01f ? (scrollX + W - nx) / ab.dx : ab.dx < -0.01f ? (scrollX - nx) / ab.dx : Float.MAX_VALUE;
        float ty = ab.dy > 0.01f ? (scrollY + H - ny) / ab.dy : ab.dy < -0.01f ? (scrollY - ny) / ab.dy : Float.MAX_VALUE;
        ab.emit = Math.max(0.1f, Math.min(tx, ty) / Bullet.LASER_SPEED);
        aimBeamList.add(ab);
        aimPiece(ab, nx, ny);
    }

    /** One piece of an aimed beam, its middle at (cx, cy). */
    private void aimPiece(AimBeam ab, float cx, float cy) {
        float s = AIM_PIECE;
        Bullet b = new Bullet(Bullet.Kind.LASER, cx - s / 2f, cy - s / 2f, s, s);
        b.seg = true;
        b.aimBeam = ab;
        b.hits = ab.hits;
        b.dmg = Bullet.LASER_DAMAGE;
        b.vx = ab.dx * Bullet.LASER_SPEED;
        b.vy = ab.dy * Bullet.LASER_SPEED;
        b.owner = ab.p; b.src = ab.src;
        pBullets.add(b);
    }

    /** Aimed beams still coming out of this player's guns: a piece from each gun's muzzle (where it is now). */
    private void emitAimBeams(Player p, float dt) {
        if (aimBeamList.isEmpty()) return;
        for (AimBeam ab : aimBeamList) {
            if (ab.p != p || ab.emit <= 0f) continue;
            if (ab.src > p.options) { ab.emit = 0f; continue; }   // its option is gone
            ab.emit -= dt;
            float nx, ny;
            if (level.vertical) {
                nx = ab.src == 0 ? p.x + 16f : optX(p, ab.src - 1) + 6f;
                ny = ab.src == 0 ? p.y + Player.VART_T : optY(p, ab.src - 1) + 14f;
            } else {
                nx = ab.src == 0 ? p.x + NOSE_X : optX(p, ab.src - 1) + 14f;
                ny = ab.src == 0 ? p.y + NOSE_Y : optY(p, ab.src - 1) + 6f;
            }
            aimPiece(ab, nx, ny);
        }
        aimBeamList.removeIf(ab -> ab.emit <= 0f && !aimBeamLive(ab));
    }

    private boolean aimBeamLive(AimBeam ab) {
        for (int i = 0, k = pBullets.size(); i < k; i++) {
            Bullet b = pBullets.get(i);
            if (b.aimBeam == ab && !b.dead) return true;
        }
        return false;
    }

    /** How many aimed beams of this gun are still coming out or flying. */
    private int aimBeams(Player p, int src) {
        int n = 0;
        for (AimBeam ab : aimBeamList) if (ab.p == p && ab.src == src && (ab.emit > 0f || aimBeamLive(ab))) n++;
        return n;
    }

    /**
     * An aimed ripple: straight up / down / back flies like the normal ring turned that way (it stretches across its
     * way up to the playfield, stopping at rock); a diagonal one grows to RIPPLE_DIAG_MAX across its way.
     */
    private void fireAimedRipple(Player p, int src, float nx, float ny) {
        float ang = aimAngle(p.laserDir);
        int deg = Math.round(ang * MathUtils.radiansToDegrees) % 360;
        float sw = Bullet.RIPPLE_START_W, sh = Bullet.RIPPLE_START_H;
        Bullet b;
        if (deg == 0 || deg == 180) {                    // across: right / left
            b = new Bullet(Bullet.Kind.RIPPLE, deg == 0 ? nx - sw : nx, ny - sh / 2f, sw, sh);
            b.vx = deg == 0 ? Bullet.RIPPLE_SPEED : -Bullet.RIPPLE_SPEED;
        } else if (deg == 90 || deg == 270) {            // up / down
            b = new Bullet(Bullet.Kind.RIPPLE, nx - sh / 2f, deg == 90 ? ny - sw : ny, sh, sw);
            b.vy = deg == 90 ? Bullet.RIPPLE_SPEED : -Bullet.RIPPLE_SPEED;
            b.vert = true;
        } else {                                         // diagonal
            b = new Bullet(Bullet.Kind.RIPPLE, nx - sh / 2f, ny - sh / 2f, sh, sh);
            b.vx = MathUtils.cos(ang) * Bullet.RIPPLE_SPEED;
            b.vy = MathUtils.sin(ang) * Bullet.RIPPLE_SPEED;
            b.diag = true;
            b.len = sh;
        }
        b.dmg = Bullet.RIPPLE_DAMAGE;
        b.owner = p; b.src = src;
        pBullets.add(b);
    }

    private void launchMissile(Player p, int src, float x, float y, boolean up) {
        Bullet m = new Bullet(Bullet.Kind.MISSILE, x, y, missR.getRegionWidth(), missR.getRegionHeight());   // size of missile.png
        m.vx = Bullet.MISSILE_SPEED;                        // speed: Bullet.MISSILE_SPEED (45 degree dive)
        m.vy = up ? Bullet.MISSILE_SPEED : -Bullet.MISSILE_SPEED;
        m.dmg = 3;
        m.up = up;
        m.homing = p.homing();
        m.owner = p; m.src = src;
        pBullets.add(m);
    }

    // ---- HOMING missiles

    /**
     * Gives a HOMING missile its target for this frame: keeps the one it is chasing while that is alive and on screen,
     * otherwise picks the nearest enemy on screen (enemies, Bamdas / falling rocks, domes / rock eyes, tower tips,
     * the weak blue balls of claw arms, the boss's weak spot).
     */
    private void aimHoming(Bullet b) {
        if (b.dead || b.burst) { b.hasTarget = false; return; }
        homingFromX = b.x + b.w / 2f; homingFromY = b.y + b.h / 2f;
        if (b.target == null || !targetPoint(b.target)) {
            b.target = null;
            float cx = b.x + b.w / 2f, cy = b.y + b.h / 2f, best = Float.MAX_VALUE;
            for (Enemy e : enemies) if (!e.indestructible() && !e.withered && !e.intangible()) best = consider(b, e, cx, cy, best);   // (not the indestructible guards)
            for (Hazard h : hazards)
                if (h.shootable() || h.type == Hazard.Type.DOME || h.type == Hazard.Type.EYE
                        || h.type == Hazard.Type.TOWER || h.type == Hazard.Type.ARM) best = consider(b, h, cx, cy, best);
            if (boss != null) best = consider(b, boss, cx, cy, best);
        }
        b.hasTarget = b.target != null && targetPoint(b.target);
        if (b.hasTarget) { b.tx = homingPt[0]; b.ty = homingPt[1]; }
    }

    private final float[] homingPt = new float[2];
    /** The missile being aimed (its middle): a claw arm is aimed at its weak ball nearest to it. */
    private float homingFromX, homingFromY;

    private float consider(Bullet b, Object o, float cx, float cy, float best) {
        if (!targetPoint(o)) return best;
        float dx = homingPt[0] - cx, dy = homingPt[1] - cy, d = dx * dx + dy * dy;
        if (d < best) { b.target = o; return d; }
        return best;
    }

    /** Puts the target's aim point in homingPt; false if it can't be chased (gone, dying or off screen). */
    private boolean targetPoint(Object o) {
        float x, y;
        if (o instanceof Enemy) {
            Enemy e = (Enemy) o;
            if (e.dead || e.withered || e.hp <= 0) return false;   // (a destroyed base / root stays, but is no target)
            x = e.x + e.w / 2f; y = e.y + e.h / 2f;
        } else if (o instanceof Hazard) {
            Hazard h = (Hazard) o;
            if (h.dead || h.hp <= 0) return false;
            if (h.type == Hazard.Type.TOWER) {          // the green tip, once it is out of the rock (see towerTipHit)
                if (h.grown < Hazard.TOWER_ROOT + Hazard.TOWER_TIP) return false;
                float ty = h.fromCeil ? h.y : h.y + h.h - Hazard.TOWER_TIP;
                x = h.x + h.w / 2f; y = ty + Hazard.TOWER_TIP / 2f;
            } else if (h.type == Hazard.Type.ARM) {     // a blue ball that can be damaged (as in collide), nearest first
                int n = h.armX.length, pick = -1;
                float bestD = Float.MAX_VALUE;
                for (int i = 0; i < n; i++) {
                    boolean weak = h.bossArm ? i < n - 1 : i == h.armWeak;
                    if (!weak || !h.armBallOut(i)) continue;
                    float bx = h.armX[i], by = h.armY[i];
                    if (bx < scrollX || bx > scrollX + W || by < scrollY || by > scrollY + H) continue;
                    float dx = bx - homingFromX, dy = by - homingFromY, d = dx * dx + dy * dy;
                    if (d < bestD) { bestD = d; pick = i; }
                }
                if (pick < 0) return false;
                x = h.armX[pick]; y = h.armY[pick];
            } else {
                x = h.x + h.w / 2f; y = h.y + h.h / 2f;
            }
        } else if (o instanceof Boss) {
            Boss bs = (Boss) o;
            if (bs != boss || bs.dying) return false;
            if (bs.hasCore) { x = bs.x + bs.coreX + bs.coreW / 2f; y = bs.y + bs.coreY + bs.coreH / 2f; }   // its weak spot
            else if (bs.hasEye) { x = bs.x + bs.eyeX + bs.eyeW / 2f; y = bs.y + bs.eyeY + bs.eyeH / 2f; }
            else { x = bs.x + bs.w / 2f; y = bs.y + bs.h / 2f; }
        } else return false;
        if (x < scrollX || x > scrollX + W || y < scrollY || y > scrollY + H) return false;   // only what's on screen
        homingPt[0] = x; homingPt[1] = y;
        return true;
    }

    private void activateMeter(Player p) {
        boolean ok = false;
        switch (p.selectedPower()) {
            case Player.SPEED: p.speedLvl = p.nextSpeed(); ok = true; break;   // always open: steps up, then back to 0
            case Player.MISSILE: case Player.TWO_WAY: case Player.HOMING: if (!p.missile) { p.missile = true; ok = true; } break;
            case Player.DOUBLE: if (p.weapon != 1) { p.weapon = 1; ok = true; } break;
            case Player.LASER: if (p.weapon != 2) { p.weapon = 2; ok = true; } break;
            case Player.RIPPLE: if (p.weapon != Player.W_RIPPLE) { p.weapon = Player.W_RIPPLE; ok = true; } break;
            case Player.OPTION: if (p.options < Player.MAX_OPTIONS) { p.options++; ok = true; } break;
            // F.FIELD / SHIELD: open again as soon as they have taken a hit; taking them refills them to full
            case Player.FFIELD: if (p.ffield < Player.FFIELD_HITS) { p.ffield = Player.FFIELD_HITS; ok = true; } break;
            case Player.SHIELD: if (p.shield < Player.SHIELD_HITS) { p.shield = Player.SHIELD_HITS; ok = true; } break;
            case Player.EXTRA: if (p.lives < Player.MAX_LIVES) { p.lives++; ok = true; } break;   // "!": one more ship
            default: break;
        }
        if (ok) {
            p.meter = 0;
            a.play("power", 0.4f);
        }
    }

    // =============================================================== spawning

    /**
     * Kierke nests: a Ug nest lets its Enemy.UG_COUNT Ugs out (Enemy.UG_GAP s apart) once its middle comes to
     * Enemy.UG_START_X on the screen; a Creeping Death nest lets them out in bursts (Enemy.CREEP_*) while it is on
     * the screen.
     */
    /** Eioneus: a big laser battery on the floor gets its beam up to the big one hanging above it once that one is there. */
    private void pairBatteries() {
        for (Enemy e : enemies) {
            if (e.dead || e.type != Enemy.Type.LASER_BATTERY || !e.batBig || e.batCeiling || e.batPartner != null) continue;
            Enemy top = null;
            for (Enemy o : enemies)
                if (!o.dead && o.type == Enemy.Type.LASER_BATTERY && o.batBig && o.batCeiling && o.y > e.y
                        && Math.abs(o.x - e.x) < 48f && (top == null || o.y < top.y)) top = o;
            if (top == null) continue;
            e.batPartner = top;
            e.beamX = (e.x + e.w / 2f + top.x + top.w / 2f) / 2f - Enemy.BATTERY_BEAM_W / 2f;
            e.beamY = e.y + e.h; e.beamW = Enemy.BATTERY_BEAM_W; e.beamH = top.y - (e.y + e.h);
        }
    }

    /**
     * The spawners that are enemies themselves (Wave): the Eioneus / Op 4 ground bases (waves of flying mines), the
     * Eioneus / Op 4 blue domes (green flyers; Op 4's opening domes: Creeping Death) and Kierke's nests (Ugs, flying
     * mines or Creeping Death). Each one only decides when it may let its waves out; the members are made by
     * spawnWaveMember. (The towers and the other domes are hazards: Hazard.updateTower / updateDome.)
     */
    private void releaseNestBugs(float dt) {
        pairBatteries();
        for (Enemy sd : enemies) {                    // ground bases: waves of flying mines while their middle is on screen
            if (sd.dead || sd.withered || sd.type != Enemy.Type.SEED) continue;
            float below = scrollY + H - (sd.y + sd.h / 2f);  // how far its middle is below the top edge
            if (below < Enemy.SEED_START || sd.y + sd.h < scrollY) continue;
            if (sd.seedT < 0f) { sd.seedT = Enemy.SEED_OPENING; sd.seedOpening = true; }   // it comes on: opens
            if (sd.seedOpening) {
                if ((sd.seedT -= dt) > 0f) continue;
                sd.seedOpening = false; sd.seedOpen = true;   // open now, and it stays open
            }
            if (sd.wave == null) sd.wave = sd.seedOp4    // (Op 4: waves of 5, the next one SEED6_REPEAT s later)
                    ? Wave.of(Wave.Kind.FLYING_MINE, Enemy.SEED6_BURST, Enemy.SEED6_GAP, Enemy.SEED6_REPEAT, Wave.Capsule.RED_ONE)
                    : Wave.of(Wave.Kind.FLYING_MINE, Enemy.SEED_BURST, Enemy.SEED_GAP, Enemy.SEED_REPEAT, Wave.Capsule.RED_ONE);
            for (int k = sd.wave.tick(dt, true); k >= 0; k = sd.wave.tick(0f, true))
                spawnWaveMember(sd.wave, k, sd, sd.x + sd.w / 2f, sd.y + sd.h / 2f, 0, 1);   // (out of its middle)
        }
        for (Enemy d : enemies) {                     // blue domes on the walls, once their middle is far enough down
            if (d.dead || d.type != Enemy.Type.BLUE_DOME || d.domeBurst <= 0) continue;   // (domeBurst 0: lets nothing out)
            float top = scrollY + H - (d.y + d.h / 2f);  // how far its middle is below the top edge
            if (top < Enemy.BLUE_DOME_START || d.y + d.h < scrollY) continue;
            int out = d.domeRight ? -1 : 1;
            if (d.wave == null) d.wave = d.domeCreep    // (Op 4's opening domes: Creeping Death, timed like a Kierke nest)
                    ? Wave.of(Wave.Kind.CREEPING_DEATH, d.domeBurst, Enemy.CREEP_BURST_GAP, Enemy.CREEP_PAUSE, Wave.Capsule.RED_ONE)
                    : Wave.of(Wave.Kind.GREEN_FLYER, d.domeBurst, Enemy.BLUE_DOME_GAP,
                            Enemy.BLUE_DOME_REPEAT - Enemy.BLUE_DOME_GAP * (d.domeBurst - 1), Wave.Capsule.WHOLE);
            for (int k = d.wave.tick(dt, true); k >= 0; k = d.wave.tick(0f, true)) {
                if (d.domeCreep) spawnWaveMember(d.wave, k, d, d.domeRight ? d.x : d.x + d.w, d.y + d.h / 2f, out, 0);
                else {   // just off its mouth: 11 px beyond where the old blue dome's front was (14 px from the wall, Op 4: 16)
                    float mouth = d.domeOp4 ? 16f : 14f;
                    spawnWaveMember(d.wave, k, d, d.domeRight ? d.x + d.w - mouth - 11f : d.x + mouth + 11f, d.y + d.h / 2f + 7f, out, 0);
                }
            }
        }
        for (Enemy n : enemies) {                     // Kierke's nests, while their middle is in their stretch of the screen
            if (n.dead || n.type != Enemy.Type.NEST || n.nestKind == Enemy.NEST_NONE) continue;
            float sx = n.x + n.w / 2f - scrollX;
            boolean mines = n.nestKind == Enemy.NEST_MINES, ugs = n.nestKind == Enemy.NEST_UGS;
            float start = mines ? Enemy.MINEK_START_X : ugs ? Enemy.UG_START_X : Enemy.CREEP_START_X;
            if (sx > start || sx < 16f) continue;
            if (n.wave == null) n.wave = mines   // web nests and Ug nests: one wave each; the others: bursts of Creeping Death
                    ? Wave.of(Wave.Kind.FLYING_MINE, Enemy.MINEK_BURST, Enemy.MINEK_GAP, 0f, Wave.Capsule.RED_ONE).once()
                    : ugs ? Wave.of(Wave.Kind.UG, Enemy.UG_COUNT, Enemy.UG_GAP, 0f, Wave.Capsule.WHOLE).once()
                    : Wave.of(Wave.Kind.CREEPING_DEATH, Enemy.CREEP_BURST, Enemy.CREEP_BURST_GAP, Enemy.CREEP_PAUSE, Wave.Capsule.RED_ONE);
            int out = n.nestDown ? -1 : 1;
            for (int k = n.wave.tick(dt, true); k >= 0; k = n.wave.tick(0f, true))
                spawnWaveMember(n.wave, k, n, n.x + n.w / 2f, mines ? n.y + n.h / 2f : (n.nestDown ? n.y : n.y + n.h), 0, out);
        }
        flushWaves();
    }

    // =============================================================== wave members (Wave)
    /** Members made this frame (added to enemies after the loops over enemies / hazards). */
    private final ArrayList<Enemy> waveOut = new ArrayList<>();

    private void flushWaves() {
        if (waveOut.isEmpty()) return;
        enemies.addAll(waveOut);
        waveOut.clear();
    }

    /**
     * Member k of wave w, let out by spawner src (an Enemy: nest, blue dome, ground base; or a Hazard: tower, dome) at
     * its mouth (mx, my; world; for amoebas their bottom-left) facing (ox, oy) (out of a side wall: ox -1 / +1; out of a
     * floor / ceiling: oy +1 / -1). The same for every spawner of that kind; what differs is only the orientation and
     * the stage's own flight path (Op 4's homing flyers, Kierke's mine path). Applies the
     * wave's capsule rule. Returns the member (null if none could be made).
     */
    Enemy spawnWaveMember(Wave w, int k, Object src, float mx, float my, int ox, int oy) {
        Enemy se = src instanceof Enemy ? (Enemy) src : null;
        Enemy e = null;
        switch (w.kind) {
            case AMOEBA: {                            // a spray: fanned out across the mouth (index in its volley)
                int i = k % w.volley;
                float spread = (i - (w.volley - 1) / 2f) * 45f + MathUtils.random(-10f, 10f);
                float out = MathUtils.random(60f, 110f);
                e = makeAmoeba(mx, my, oy != 0 ? spread : ox * out, oy != 0 ? oy * out : spread, w.groupFor(k));
                break;
            }
            case GREEN_FLYER:
                if (greenF != null) {                 // every stage: the Eioneus path, mirrored on the right wall (Albert, 2026-10-30)
                    e = new Enemy(Enemy.Type.GREEN_FLYER, 0f, 0f, levelIdx);
                    e.vertical = level.vertical;
                    e.gfMirror = ox < 0;
                    e.gfHoming = se != null && se.domeOp4;   // (Op 4: homes in)
                    e.gfX = mx - scrollX; e.gfY = my - scrollY;
                    e.x = mx - 8f; e.y = my - 8f;
                    e.group = w.groupFor(k);
                }
                break;
            case CREEPING_DEATH:
                if (creepF == null) {                 // (no picture: an amoeba instead)
                    e = makeAmoeba(mx - 8f, my, MathUtils.random(-40f, 40f), oy < 0 ? -80f : 80f, w.groupFor(k));
                    break;
                }
                e = new Enemy(Enemy.Type.CREEPING_DEATH, 0f, 0f, levelIdx);
                e.x = mx - e.w / 2f; e.y = my - e.h / 2f;   // its middle on the mouth
                if (ox != 0) e.creepOut = ox;            // out of a side wall (Op 4's domes)
                else e.nestDown = oy < 0;                // out of a floor / ceiling (nests, Lavinia's domes)
                e.creepRed = w.red(k);
                e.group = w.groupFor(k);
                break;
            case FLYING_MINE: {
                if (mineWhiteF == null) break;
                e = new Enemy(Enemy.Type.FLYING_MINE, 0f, 0f, levelIdx);
                e.mineX = mx; e.mineY = my;           // (its path is measured from the spawner's middle)
                if (se != null && se.type == Enemy.Type.NEST) {   // Kierke's web nests: their own path
                    e.mineKierke = true;
                    e.x = mx + 38f - e.w / 2f; e.y = my + 2f - e.h / 2f;
                } else {
                    e.mineOp4 = se != null && se.seedOp4;
                    e.x = mx - e.w / 2f; e.y = my - e.h / 2f;
                }
                boolean extra = !w.red(k) && se != null && se.type == Enemy.Type.SEED && !se.seedOp4
                        && MathUtils.random() < 1f / 6f;      // (Eioneus: about one in six red too, as in the recording)
                e.mineRed = mineRedF != null && (w.red(k) || extra);
                if (e.mineRed) e.group = extra ? carrier1() : w.groupFor(k);
                break;
            }
            case UG:
                if (ugF == null) break;
                e = new Enemy(Enemy.Type.UG, 0f, 0f, levelIdx);
                e.ugX = mx - scrollX; e.ugY = my - scrollY;   // (screen)
                e.phase = (k + 1) % 3;
                e.nestDown = oy < 0;
                e.group = w.groupFor(k);
                break;
        }
        if (e == null) return null;
        if (se != null) e.nest = se;
        w.members.add(e);
        waveOut.add(e);
        return e;
    }

    /** A capsule group of one (a red one that is not the wave's chosen one). */
    private static Enemy.Group carrier1() {
        Enemy.Group g = new Enemy.Group();
        g.size = 1;
        g.carrier = true;
        return g;
    }

    /** A strip of square frames side by side (each as tall as the picture); null for null. */
    private static TextureRegion[] squareFrames(TextureRegion r) {
        if (r == null) return null;
        int fh = r.getRegionHeight(), n = Math.max(1, r.getRegionWidth() / fh);
        TextureRegion[] f = new TextureRegion[n];
        for (int i = 0; i < n; i++) f[i] = new TextureRegion(r, i * fh, 0, fh, fh);
        return f;
    }

    /** A picture whose near-black pixels (its background box) are made see-through; null if the file is missing. */
    private static TextureRegion keyedDark(String file) {
        if (!Gdx.files.internal(file).exists()) return null;
        com.badlogic.gdx.graphics.Pixmap src = new com.badlogic.gdx.graphics.Pixmap(Gdx.files.internal(file));
        com.badlogic.gdx.graphics.Pixmap pm = new com.badlogic.gdx.graphics.Pixmap(src.getWidth(), src.getHeight(), com.badlogic.gdx.graphics.Pixmap.Format.RGBA8888);
        pm.setBlending(com.badlogic.gdx.graphics.Pixmap.Blending.None);
        for (int yy = 0; yy < src.getHeight(); yy++) for (int xx = 0; xx < src.getWidth(); xx++) {
            int c = src.getPixel(xx, yy), r = c >>> 24 & 255, gg = c >>> 16 & 255, bb = c >>> 8 & 255, al = c & 255;
            pm.drawPixel(xx, yy, al == 0 || (r < 16 && gg < 16 && bb < 16) ? 0 : c);
        }
        TextureRegion t = new TextureRegion(new Texture(pm));
        pm.dispose(); src.dispose();
        return t;
    }

    /** A strip of frames (frameW wide) whose near-black pixels are made see-through; null if the file is missing. */
    private static TextureRegion[] keyedFrames(String file, int frameW) {
        TextureRegion all = keyedDark(file);
        if (all == null) return null;
        int n = Math.max(1, all.getRegionWidth() / frameW);
        TextureRegion[] f = new TextureRegion[n];
        for (int i = 0; i < n; i++) f[i] = new TextureRegion(all, i * frameW, 0, frameW, all.getRegionHeight());
        return f;
    }

    /** Kierke chains: start when they come to Enemy.CHAIN_START_X on the screen, then grow a ball at a time. */
    private void growChains(float dt) {
        final float B = Enemy.CHAIN_BALL;
        for (ChainRun r : chainRuns) {
            if (r.made >= r.c.count) continue;
            if (!r.started) {
                if (r.c.x - scrollX > Enemy.CHAIN_START_X) continue;
                r.started = true; r.t = 0f;
                // its root: the aberrant just under (or over) the first ball
                for (Enemy e : enemies) {
                    if (e.dead || e.type != Enemy.Type.ABERRANT) continue;
                    float dx = (e.x + e.w / 2f) - (r.c.x + B / 2f);
                    float under = (e.y + e.h) - (r.c.top - B), over = (r.c.top) - e.y;
                    if (Math.abs(dx) <= 8f && (Math.abs(under) <= 8f || Math.abs(over) <= 8f)) { r.root = e; break; }
                }
                if (r.root != null && r.root.withered) { r.made = r.c.count; continue; }   // shot before it began
            } else r.t += dt;
            if (r.root != null && (r.root.withered || r.root.dead)) { r.made = r.c.count; continue; }   // its aberrant is gone: no more balls
            while (r.made < r.c.count && r.t >= r.made * Enemy.CHAIN_STEP) {
                float bx, by;
                if (r.made == 0) { bx = r.c.x; by = r.c.top - B; }
                else {
                    // a step of one ball toward the nearest ship: sideways and / or up or down (diagonal when both)
                    Player p = target(r.lastX + B / 2f, r.lastY + B / 2f);
                    float dx = p == null ? -B : (p.x + Player.W / 2f) - (r.lastX + B / 2f);
                    float dy = p == null ? 0f : (p.y + Player.H / 2f) - (r.lastY + B / 2f);
                    float sx = Math.abs(dx) >= B ? Math.signum(dx) * B : 0f, sy = Math.abs(dy) >= B ? Math.signum(dy) * B : 0f;
                    if (sx == 0f && sy == 0f) { r.made = r.c.count; break; }   // it has reached the ship
                    bx = r.lastX + sx; by = r.lastY + sy;
                    if (level.hits(bx + 1f, by + 1f, B - 2f, B - 2f)) {      // rock in the way: only one of the two
                        if (sx != 0f && !level.hits(r.lastX + sx + 1f, r.lastY + 1f, B - 2f, B - 2f)) { bx = r.lastX + sx; by = r.lastY; }
                        else if (sy != 0f && !level.hits(r.lastX + 1f, r.lastY + sy + 1f, B - 2f, B - 2f)) { bx = r.lastX; by = r.lastY + sy; }
                        else { r.made = r.c.count; break; }                    // a wall: it ends
                    }
                }
                Enemy e = new Enemy(Enemy.Type.CHAIN, bx, by, levelIdx);
                e.t = r.t - r.made * Enemy.CHAIN_STEP;     // its age (draw: grows from 4 px)
                enemies.add(e);
                r.lastX = bx; r.lastY = by; r.made++;
            }
        }
    }

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
            case WHIPLASH:
                e = new Enemy(Enemy.Type.WHIPLASH, scrollX, scrollY + H + 20f, levelIdx);
                e.vertical = level.vertical;
                e.whipFromBottom = s.fromBottom;
                e.whipAcross = level.vertical ? s.midX - scrollX : s.y - scrollY;
                break;
            case SEED:
                e = new Enemy(Enemy.Type.SEED, s.x, s.y, levelIdx);
                if (s.what.equals("op4")) { e.seedOp4 = true; e.w = 22; e.h = 22; }   // the whole 22x22 picture
                break;
            case EXODUS:                                  // in at the top edge, in its column
                e = new Enemy(Enemy.Type.EXODUS, s.x, scrollY + H, levelIdx);
                e.exRed = s.red;
                break;
            case ARTILLERY:
                e = new Enemy(Enemy.Type.ARTILLERY, s.x, s.y, levelIdx);
                e.artRight = s.right; e.artRed = s.red;
                e.artFloor = s.what.equals("floor"); e.artCeil = s.what.equals("ceiling");
                break;
            case BLUE_ASTEROID:
                e = new Enemy(Enemy.Type.BLUE_ASTEROID, s.x, s.y, levelIdx);
                e.blueAsteroidSink = s.sink; e.blueAsteroidDrift = s.drift;
                if (blueAsteroidR != null) { e.w = blueAsteroidR.getRegionWidth(); e.h = blueAsteroidR.getRegionHeight(); }
                break;
            case MOAI:
                e = new Enemy(Enemy.Type.MOAI, s.x, s.y, levelIdx);
                e.moaiRight = s.right; e.moaiWalk = !s.still;
                {   // it starts against its wall (Albert): slide it there if the map has it a little away from the rock
                    float wx = s.x;
                    int n = 0;
                    while (n < 48 && !level.hits(s.right ? wx + e.w : wx - 1f, e.y + 6f, 1f, e.h - 12f)) { wx += s.right ? 1f : -1f; n++; }
                    e.x = n < 48 ? wx : s.x;
                }
                e.moaiWallX = e.x;
                break;
            case ORB:                                     // in at the top edge, at its x
                e = new Enemy(Enemy.Type.ORB, s.x, scrollY + H, levelIdx);
                break;
            case GREMLIN:                                 // just below the bottom edge, on its wall
                e = new Enemy(Enemy.Type.GREMLIN, s.x, scrollY - 16f, levelIdx);
                e.vertical = level.vertical;
                e.gremRight = s.right; e.gremRed = s.red;
                break;
            case LASER_BATTERY: {
                e = new Enemy(Enemy.Type.LASER_BATTERY, s.x, s.y, levelIdx);
                e.batBig = s.what.equals("big"); e.batRight = s.right; e.batCeiling = s.ceiling;
                e.batWall = s.what.equals("wall");
                e.w = e.batBig ? 32 : 24; e.h = 16;
                if (e.batWall) {                          // Lavinia: an emitter in the wall art, its beam from the map
                    e.w = Enemy.WALL_EMITTER_W; e.h = Enemy.WALL_EMITTER_H;
                    e.batRight = !s.right;                // (batRight = the beam goes right)
                    e.beamX = s.beamX; e.beamY = s.beamY; e.beamW = s.beamW; e.beamH = s.beamH;
                } else if (!e.batBig) {                          // sideways from its lens to the rock across
                    float by = e.y + 8f - Enemy.BATTERY_BEAM_W / 2f, x0 = e.batRight ? e.x + e.w : e.x - 1f;
                    int n = 0;
                    while (n < 400 && !level.hits(e.batRight ? x0 + n : x0 - n, by, 1f, Enemy.BATTERY_BEAM_W)) n++;
                    e.beamX = e.batRight ? x0 : x0 - n + 1f; e.beamY = by; e.beamW = n; e.beamH = Enemy.BATTERY_BEAM_W;
                }                                         // (a big pair: see pairBatteries)
                break;
            }
            case BLUE_DOME:
                e = new Enemy(Enemy.Type.BLUE_DOME, s.x, s.y, levelIdx);
                e.domeRight = s.right;
                if (s.what.equals("op4")) { e.domeOp4 = true; e.domeBurst = s.count; e.domeCreep = s.spawns.startsWith("creeping"); }
                break;
            case GAMMA:                                   // in at the right edge, at its object's height
                e = new Enemy(Enemy.Type.GAMMA, s.fromBottom ? scrollX - 16f : scrollX + W, s.y, levelIdx);
                e.gammaLeft = s.fromBottom;
                e.gammaCeil = s.ceiling;
                e.gammaRed = s.red;
                break;
            case ABERRANT:
                e = new Enemy(Enemy.Type.ABERRANT, s.x, s.y, levelIdx);
                break;
            case NEST:
                e = new Enemy(Enemy.Type.NEST, s.x, s.y, levelIdx);
                e.nestDown = s.ceiling;
                String what = s.what.toLowerCase().replace("_", " ").trim();
                e.nestKind = what.equals("creeping death") || what.equals("creeping") || what.equals("bug") ? Enemy.NEST_CREEPING
                        : what.equals("mines") || what.equals("mine") ? Enemy.NEST_MINES
                        : what.equals("none") ? Enemy.NEST_NONE : Enemy.NEST_UGS;
                break;
            case BATTLEAXE:
                e = new Enemy(Enemy.Type.BATTLEAXE, scrollX + W + 40f, scrollY + H / 2f, levelIdx);
                e.axeSlot = s.member; e.axeHigh = s.upper;
                break;
            case POSSESSED:
                e = new Enemy(Enemy.Type.POSSESSED, scrollX, scrollY + H + 20f, levelIdx);
                e.vertical = level.vertical;
                e.possMember = s.member; e.possRed = s.red;
                e.possX = level.vertical ? s.midX - scrollX : s.y - scrollY;   // the cluster's place across the screen
                if (s.row) {                                  // a row group: runs toward the side the nearest ship is on
                    e.possRow = true;
                    if (!Float.isNaN(s.place)) e.possX = s.place;   // (horizontal stages: the first place, screen x)
                    else if (!level.vertical) e.possX = W / 2f;
                    if (!Float.isNaN(s.rowDown)) e.possRowDown = s.rowDown;
                    float wx = level.vertical ? s.midX : scrollX + e.possX;
                    Player tp = target(wx, scrollY + H);
                    e.possSide = s.side != 0f ? s.side : tp != null && tp.x + Player.W / 2f > wx ? 1f : -1f;
                }
                e.t = -s.delay;
                break;
            case LEGACY:
                if (s.hover) {                            // hangs in the world where it was placed
                    e = new Enemy(s.type, s.x, s.y - 8f, levelIdx);
                    e.hover = true; e.vertical = level.vertical; e.phase = s.phase;
                    e.w = 16; e.h = 16;
                    break;
                }
                // the fly-by Legacy wave
                if (level.vertical) {                     // in from the top, weaving down around the object's column
                    e = new Enemy(s.type, s.midX - 8f, scrollY + H + 24f, levelIdx);
                    e.vertical = true;
                    e.baseX = s.midX - 8f;
                } else {
                    e = new Enemy(s.type, scrollX + W + 24f, s.y, levelIdx);
                }
                e.phase = s.phase;
                break;
            case RUGAL:                                   // comes in from the right edge, at its height from Tiled
                e = new Enemy(Enemy.Type.RUGAL, scrollX + W + 8f, s.y - 8f, levelIdx);
                break;
            case GREEN_MINE:                              // Kierke: in at the right edge, at its object's height
                if (level.vertical) {                     // Op 4: in at the top (or bottom) edge, at its object's x
                    e = new Enemy(Enemy.Type.GREEN_MINE, s.x, s.fromBottom ? scrollY - 16f + Enemy.GMINE_IN : scrollY + H - Enemy.GMINE_IN, levelIdx);
                    e.vertical = true;
                    e.gmFromBottom = s.fromBottom;
                    break;
                }
                e = new Enemy(Enemy.Type.GREEN_MINE, scrollX + W - Enemy.GMINE_IN, s.y, levelIdx);
                break;
            case LINK:                                    // enters at the right edge at its height from Tiled
                if (s.linkSwing > 0f) {                   // MSX (Kierke): at the top (bottom) of its swing
                    e = new Enemy(Enemy.Type.LINK, scrollX + W - Enemy.LINK_MSX_IN, s.y, levelIdx);
                    e.linkSwing = s.linkSwing; e.linkUp = s.upper; e.linkBurst = s.linkBurst;
                    e.baseY = s.y + (s.upper ? s.linkSwing / 2f : -s.linkSwing / 2f);
                    break;
                }
                e = new Enemy(Enemy.Type.LINK, scrollX + W + 8f, s.y - 8f, levelIdx);
                e.phase = s.phase;
                break;
            case FROST:                                   // the path is in screen coordinates (see Enemy.FROST_PATH)
                e = new Enemy(Enemy.Type.FROST, scrollX + W + 16f, scrollY, levelIdx);
                e.w = frostR.getRegionWidth(); e.h = frostR.getRegionHeight();
                e.upper = s.upper;
                e.t = -s.delay;
                e.frostIdx = Math.round(s.delay / Enemy.FROST_GAP);   // its place in the wave
                e.vertical = level.vertical;
                {   // the wave enters where it was placed in Tiled (its height on the screen), and leaves mirrored;
                    // vertical stage: the column it comes down (its x on the screen)
                    float f = level.vertical ? (s.midX - scrollX) / W : (s.y - scrollY) / H;
                    e.frostLow = MathUtils.clamp(s.upper ? 1f - f : f, Enemy.FROST_LOW_MIN, Enemy.FROST_LOW_MAX);
                }
                break;
            case WALKER: {
                if (s.leap != 0) {                            // Kierke: leaps in from a screen edge at its object's height
                    e = new Enemy(Enemy.Type.WALKER, s.leap > 0 ? scrollX - Enemy.LEAP_IN_LEFT : scrollX + Enemy.LEAP_IN_RIGHT, s.y, levelIdx);
                    e.ceiling = s.ceiling;
                    e.startLeap(s.leap);
                    break;
                }
                float x = s.behind ? scrollX - 20f : s.x;   // runners from behind enter at the left edge
                if (s.ceiling) {
                    e = new Enemy(Enemy.Type.WALKER, x, level.ceilBottom(x + 8f, s.y) - 16f, levelIdx);
                    e.ceiling = true;
                } else {
                    e = new Enemy(Enemy.Type.WALKER, x, level.floorTop(x + 8f, s.y), levelIdx);
                }
                if (s.behind) e.vx = 80f;                     // faster than the scrolling, so they catch up
                if (levelIdx == 0) e.leapGround = true;      // stage 1: they behave like the Kierke leapers (Albert, 2026-10-28)
                break;
            }
            case OMEN: {                                  // stage 3: sits where it was placed in Tiled (top edge = s.y)
                e = new Enemy(Enemy.Type.OMEN, s.x, 0f, levelIdx);
                if (omenR != null) { e.w = omenR.getRegionWidth(); e.h = omenR.getRegionHeight(); }
                e.y = s.y - e.h;
                e.ceiling = s.ceiling;
                break;
            }
            case BLOCK_SMALL: {                           // Lavinia: its run is from the ceiling to the floor around it
                e = new Enemy(Enemy.Type.BLOCK_SMALL, s.x, s.y, levelIdx);
                float lo = s.y, hi = s.y;
                while (lo > s.y - 200f && !level.hits(s.x + 1f, lo - 1f, 30f, 1f)) lo -= 1f;
                while (hi < s.y + 200f && !level.hits(s.x + 1f, hi + 16f, 30f, 1f)) hi += 1f;
                e.blockLo = lo; e.blockHi = hi;
                e.blockDir = hi - s.y > s.y - lo ? 1f : -1f;   // starts toward the far end
                break;
            }
            case BLOCK_BIG:
                e = new Enemy(Enemy.Type.BLOCK_BIG, s.x, s.y, levelIdx);
                break;
            case LASER_GUN:
                e = new Enemy(Enemy.Type.LASER_GUN, s.x, s.y, levelIdx);
                e.lgunLeft = !s.right; e.lgunDelay = s.delay;
                break;
            case HEATHEN:                                 // Lavinia: in at the right edge, a random one of the three
                e = new Enemy(Enemy.Type.HEATHEN, scrollX + W, s.y, levelIdx);
                e.heathenKind = MathUtils.random(2);
                if (heathenMasks != null) e.heathenMask = heathenMasks[e.heathenKind];
                java.util.Arrays.fill(e.heathenBarHp, Enemy.HEATHEN_BAR_HP);
                break;
            case GUN_HO:                                  // stage 3: stands where it was placed in Tiled
                e = new Enemy(Enemy.Type.GUN_HO, s.x, s.y, levelIdx);
                if (gunHoR != null) { e.w = gunHoR.getRegionWidth(); e.h = gunHoR.getRegionHeight(); }
                break;
            case LASER_GUARD: {                           // stage 3's last room: against its wall, slides along it
                e = new Enemy(Enemy.Type.LASER_GUARD, s.x, s.y, levelIdx);
                e.guardSide = s.guardSide;
                if (laserGuardTex != null) { e.spriteW = laserGuardTex.getWidth(); e.spriteH = laserGuardTex.getHeight(); }
                else { e.spriteW = 22f; e.spriteH = 20f; }
                boolean wall = s.guardSide == Enemy.GUARD_WALL;
                e.w = wall ? e.spriteH : e.spriteW;           // (the side-wall one is turned a quarter)
                e.h = wall ? e.spriteW : e.spriteH;
                if (wall) {                                   // wall line: the rock to the right of where it was placed
                    float cy = s.y + e.h / 2f, wx = s.x - 4f;
                    while (wx < s.x + 64f && !level.hits(wx, cy, 1f, 1f)) wx += 1f;
                    e.wallLine = wx;
                    e.y = s.y;
                } else {
                    float cx = s.x + e.w / 2f;
                    e.wallLine = s.guardSide == Enemy.GUARD_CEIL ? level.ceilBottom(cx, s.y - 4f) : level.floorTop(cx, s.y + 4f);
                    e.x = s.x;
                }
                e.placeGuard();
                // how far it can slide: along its wall until rock is in the way
                float lo = wall ? e.y : e.x, hi = lo + (wall ? e.h : e.w), size = hi - lo;
                if (wall) {
                    while (lo > e.y - 300f && !level.hits(e.x, lo - 1f, e.w, size)) lo -= 1f;
                    while (hi < e.y + e.h + 300f && !level.hits(e.x, hi - size + 1f, e.w, size)) hi += 1f;
                } else {
                    while (lo > e.x - 300f && !level.hits(lo - 1f, e.y, size, e.h)) lo -= 1f;
                    while (hi < e.x + e.w + 300f && !level.hits(hi - size + 1f, e.y, size, e.h)) hi += 1f;
                }
                e.moveMin = lo; e.moveMax = hi;
                e.guardGoal = e.guardPos();
                break;
            }
            case TURRET_WALL:                             // stage 2: stays where it was placed in Tiled
                e = new Enemy(Enemy.Type.TURRET_WALL, s.x, s.y, levelIdx);
                e.wallRight = s.right;
                e.aim = !s.diag ? Enemy.WALL_SIDE : s.diagUp ? Enemy.WALL_UP : Enemy.WALL_DOWN;
                break;
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
        if (e.type == Enemy.Type.POSSESSED) {        // Albert: every red one leaves its own capsule (not the whole group)
            Enemy.Group pg = new Enemy.Group();
            pg.size = 1;
            pg.carrier = e.possRed;
            e.group = pg;
        }
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

    /**
     * A single picture drawn on a solid dark background (like sprites/player_blue_vertical.png): the background colour
     * (the top-left pixel) connected to the edges becomes see-through. A picture with transparency is used as it is.
     */
    private TextureRegion keyedBackground(String file) { return keyedBackground(file, null); }

    /** keyedBackground, and maskOut[0] = the exact shape of the result (when maskOut is given). */
    private TextureRegion keyedBackground(String file, PixelMask[] maskOut) {
        Pixmap pm = new Pixmap(Gdx.files.internal(file));
        if (pm.getFormat() != Pixmap.Format.RGBA8888) {
            Pixmap c = new Pixmap(pm.getWidth(), pm.getHeight(), Pixmap.Format.RGBA8888);
            c.setBlending(Pixmap.Blending.None);
            c.drawPixmap(pm, 0, 0);
            pm.dispose();
            pm = c;
        }
        pm.setBlending(Pixmap.Blending.None);
        int W = pm.getWidth(), H = pm.getHeight(), bg = pm.getPixel(0, 0);
        boolean dark = (bg & 0xff) == 0xff && ((bg >>> 24) & 0xff) < 24 && ((bg >>> 16) & 0xff) < 24 && ((bg >>> 8) & 0xff) < 24;
        if (dark) {
            boolean[] seen = new boolean[W * H];
            java.util.ArrayDeque<int[]> q = new java.util.ArrayDeque<>();
            for (int x = 0; x < W; x++) { q.add(new int[]{x, 0}); q.add(new int[]{x, H - 1}); }
            for (int y = 0; y < H; y++) { q.add(new int[]{0, y}); q.add(new int[]{W - 1, y}); }
            while (!q.isEmpty()) {
                int[] c = q.poll();
                int x = c[0], y = c[1];
                if (x < 0 || x >= W || y < 0 || y >= H || seen[y * W + x] || pm.getPixel(x, y) != bg) continue;
                seen[y * W + x] = true;
                pm.drawPixel(x, y, 0);
                q.add(new int[]{x + 1, y}); q.add(new int[]{x - 1, y}); q.add(new int[]{x, y + 1}); q.add(new int[]{x, y - 1});
            }
        }
        if (maskOut != null) maskOut[0] = PixelMask.fromPixmap(pm, 0, W);
        Texture tex = new Texture(pm);
        tex.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        pm.dispose();
        return new TextureRegion(tex);
    }

    /**
     * The vertical ship's frames: three of equal width side by side (left, level, right). A picture no wider than it
     * is tall is a single frame, used for all three.
     */
    private static TextureRegion[] verticalShipFrames(TextureRegion sheet) {
        int w = sheet.getRegionWidth(), h = sheet.getRegionHeight();
        if (w <= h) return new TextureRegion[]{sheet, sheet, sheet};
        int fw = w / 3;
        TextureRegion[] f = new TextureRegion[3];
        for (int i = 0; i < 3; i++) f[i] = new TextureRegion(sheet, i * fw, 0, fw, h);
        return f;
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
        if (boss.appearing) return false;                // (Op 4's gun units coming together: not solid yet)
        if (kierkeBossNow()) return kierkeTouches(x, y, w, h);   // (its column slides out of the boss box)
        if (!overlap(x, y, w, h, boss.x, boss.y, boss.w, boss.h)) return false;
        if (boss.hasEye && overlap(x, y, w, h, boss.x + boss.eyeX, boss.y + boss.eyeY, boss.eyeW, boss.eyeH)) return true;
        if (boss.coreTouches(x, y, w, h)) return true;
        return boss.mask == null || boss.mask.overlaps(boss.x, boss.y, x, y, w, h);
    }

    /** sprites/bossN_core.png (null = this boss has no separate core), and where it sits on the body. */
    private TextureRegion coreR;
    private PixelMask coreMask;
    private int coreOX, coreOY;

    /**
     * Loads the core picture and finds where it belongs: over the hole cut out of the body for it (the spot where the
     * most core pixels fall on see-through body pixels and the fewest on drawn ones).
     */
    private void loadBossCore() {
        String file = "sprites/boss" + levelIdx + "_core.png";
        if (!Gdx.files.internal(file).exists()) return;
        coreR = a.one("boss" + levelIdx + "_core");
        coreMask = PixelMask.load(file);
        loadCoreBlocks();
        int best = Integer.MIN_VALUE;
        for (int oy = 0; oy <= bossMask.h - coreMask.h; oy++)
            for (int ox = 0; ox <= bossMask.w - coreMask.w; ox++) {
                int score = 0;
                for (int y = 0; y < coreMask.h; y++)
                    for (int x = 0; x < coreMask.w; x++)
                        if (coreMask.at(x, y)) score += bossMask.at(ox + x, oy + y) ? -1 : 1;
                if (score > best) { best = score; coreOX = ox; coreOY = oy; }
            }
    }

    /**
     * Tetran's core blocks (sprites/core_block.png): CORE_BLOCKS bars below the red core, drawn over the core picture.
     * Every time the boss loses 1/(CORE_BLOCKS+1) of its health (20%), the lowest block left disappears, showing the
     * dark inside of the core picture.
     */
    private TextureRegion blockR;
    private static final int CORE_BLOCKS = 4;
    /** Top row of each block in the core picture (counted from its top), top block first; x = its left column. */
    private static final int[] CORE_BLOCK_ROWS = {26, 30, 34, 38};
    private static final int CORE_BLOCK_X = 8, CORE_BLOCK_SPAN = 8;
    private int coreBlocksShown = CORE_BLOCKS;
    /** Where a block's picture goes, so its drawn part sits in the middle of the bar's span. */
    private int blockOffX;

    private void loadCoreBlocks() {
        if (!Gdx.files.internal("sprites/core_block.png").exists()) return;
        blockR = a.one("core_block");
        PixelMask bm = PixelMask.load("sprites/core_block.png");   // centre the drawn part of the block
        int x0 = bm.w, x1 = -1;
        for (int x = 0; x < bm.w; x++) for (int y = 0; y < bm.h; y++) if (bm.at(x, y)) { x0 = Math.min(x0, x); x1 = Math.max(x1, x); }
        blockOffX = x1 < 0 ? 0 : Math.round((CORE_BLOCK_SPAN - (x1 - x0 + 1)) / 2f) - x0;
    }

    /** Draws the core blocks still left, at their spots in the core (each block's picture centred on its bar). */
    private void drawCoreBlocks() {
        if (blockR == null) return;
        int ch = coreR.getRegionHeight();
        float bw = blockR.getRegionWidth(), bh = blockR.getRegionHeight();
        for (int i = 0; i < coreBlocksShown; i++) {         // the lowest ones go first
            int top = CORE_BLOCK_ROWS[i];
            float x = boss.x + boss.coreX + CORE_BLOCK_X + blockOffX;
            float y = boss.y + boss.coreY + ch - top - bh;
            batch.draw(blockR, x, y, bw, bh);
        }
    }

    /** Takes a block away for every 20% of health the boss has lost (with a small burst where it was). */
    private void updateCoreBlocks() {
        if (blockR == null || boss == null || !boss.hasCore) return;
        int want = Math.max(0, Math.min(CORE_BLOCKS, (int) Math.ceil(boss.hp / boss.maxHp * (CORE_BLOCKS + 1)) - 1));
        while (coreBlocksShown > want) {
            coreBlocksShown--;
            int top = CORE_BLOCK_ROWS[coreBlocksShown];
            boom(boss.x + boss.coreX + CORE_BLOCK_X + CORE_BLOCK_SPAN / 2f, boss.y + boss.coreY + coreR.getRegionHeight() - top - 1f, 0.5f);
            a.play("hit", 0.3f);
        }
    }

    // ---- Kierke's machine wall (stage 5; see Boss.kierke), from Albert's pictures (2026-10-28):
    // sprites/boss4_main.png (the main body with the giant core, drawn K_MAIN_X px right of the boss's x, behind the
    // wall), sprites/boss4_wall_without_shield.png (the wall, 89x320, wrapping), and its parts: boss4_wall_bar.png (11
    // per unit, at x 1 + 4 i, 7x16, overlapping), boss4_wall_bar_core.png (the middle unit's 2 core bars, x 49 and 57),
    // boss4_wall_main_orb.png (the middle orb, x 65) and boss4_wall_shield.png (x 0, over the middle unit's bars). The
    // wall's own pixels under the parts are taken out (kWallBase); the parts still standing are drawn back onto it
    // (kWallPm) whenever one breaks, and its hit mask is rebuilt. An outer orb is the ball already in the wall picture
    // (12 px round at x K_ORB_CX); destroyed, its pixels go. Black is see-through (and flown / shot through).
    private static final int K_WALL_W = 89, K_MAIN_X = 89, K_BAR_X0 = 1, K_BAR_STEP = 4, K_BAR_W = 7, K_PART_H = 16;
    private static final int[] K_CORE_BAR_X = {49, 57};
    private static final int K_CORE_BAR_W = 8, K_MAIN_ORB_X = 65, K_MAIN_ORB_W = 17, K_SHIELD_W = 49;
    private static final float K_ORB_CX = 56.5f, K_ORB_R = 6.5f;
    /** The giant core in boss4_main.png: x, y (down), w, h. */
    private static final int K_CORE_X = 10, K_CORE_Y = 82, K_CORE_W = 24, K_CORE_H = 14;
    private Pixmap kWallBase, kWallPm, kBarPm, kCoreBarPm, kMainOrbPm, kShieldPm;
    private Texture kWallTex, kMainTex;
    private TextureRegion kWallR, kMainR;
    private PixelMask kWallMask, kMainMask;
    /** Scratch box (x, y, w, h). */
    private final float[] kBx = new float[4];

    /** See-through: transparent, black, or the near-black (6,6,6) of the boss pictures. */
    private static boolean kBlack(int rgba) {
        return (rgba & 0xff) == 0 || ((rgba >>> 24) <= 6 && ((rgba >>> 16) & 0xff) <= 6 && ((rgba >>> 8) & 0xff) <= 6);
    }

    private static Pixmap kLoad(String name) {
        Pixmap src = new Pixmap(Gdx.files.internal("sprites/" + name + ".png"));
        Pixmap pm = new Pixmap(src.getWidth(), src.getHeight(), Pixmap.Format.RGBA8888);
        pm.setBlending(Pixmap.Blending.None);
        for (int y = 0; y < src.getHeight(); y++)
            for (int x = 0; x < src.getWidth(); x++) { int c = src.getPixel(x, y); pm.drawPixel(x, y, kBlack(c) ? 0 : c); }
        src.dispose();
        return pm;
    }

    /** Clears the wall base where the part picture has pixels, at (px, py). */
    private void kCut(Pixmap part, int px, int py) {
        for (int y = 0; y < part.getHeight(); y++)
            for (int x = 0; x < part.getWidth(); x++) if ((part.getPixel(x, y) & 0xff) != 0) kWallBase.drawPixel(px + x, py + y, 0);
    }

    /** Draws the part picture's pixels onto the wall at (px, py). */
    private void kPaste(Pixmap part, int px, int py) {
        for (int y = 0; y < part.getHeight(); y++)
            for (int x = 0; x < part.getWidth(); x++) { int c = part.getPixel(x, y); if ((c & 0xff) != 0) kWallPm.drawPixel(px + x, py + y, c); }
    }

    private void loadKierkeBoss() {
        if (levelIdx != 4) return;
        for (String n : new String[]{"boss4_main", "boss4_wall_without_shield", "boss4_wall_bar", "boss4_wall_bar_core",
                "boss4_wall_main_orb", "boss4_wall_shield"})
            if (!Gdx.files.internal("sprites/" + n + ".png").exists()) return;
        kBarPm = kLoad("boss4_wall_bar"); kCoreBarPm = kLoad("boss4_wall_bar_core");
        kMainOrbPm = kLoad("boss4_wall_main_orb"); kShieldPm = kLoad("boss4_wall_shield");
        kResetWall();
        kWallPm = new Pixmap(K_WALL_W, Boss.K_STACK_H, Pixmap.Format.RGBA8888);
        kWallPm.setBlending(Pixmap.Blending.None);
        kWallTex = new Texture(kWallPm);
        kWallR = new TextureRegion(kWallTex);
        Pixmap main = kLoad("boss4_main");
        kMainTex = new Texture(main);
        kMainR = new TextureRegion(kMainTex);
        kMainMask = PixelMask.fromPixmap(main, 0, main.getWidth());
        main.dispose();
    }

    /** A fresh wall base (every boss fight starts with a whole wall): the picture with its parts taken out. */
    private void kResetWall() {
        if (kWallBase != null) kWallBase.dispose();
        kWallBase = kLoad("boss4_wall_without_shield");
        for (int k = 0; k < Boss.K_UNITS; k++)
            for (int i = 0; i < Boss.K_BARS; i++) kCut(kBarPm, K_BAR_X0 + K_BAR_STEP * i, Boss.kUnitRow(k));
        int mid = Boss.kUnitRow(Boss.K_MIDDLE);
        for (int x : K_CORE_BAR_X) kCut(kCoreBarPm, x, mid);
        kCut(kMainOrbPm, K_MAIN_ORB_X, mid);
    }

    /** Puts the wall together from its base and the parts still standing, and rebuilds its hit mask. */
    private void kCompose() {
        kWallPm.drawPixmap(kWallBase, 0, 0);
        int mid = Boss.kUnitRow(Boss.K_MIDDLE);
        for (int k = 0; k < Boss.K_UNITS; k++)
            for (int i = Boss.K_BARS - 1; i >= 0; i--)
                if (boss.kBarHp[k][i] > 0f) kPaste(kBarPm, K_BAR_X0 + K_BAR_STEP * i, Boss.kUnitRow(k));
        for (int i = Boss.K_CORE_BARS - 1; i >= 0; i--) if (boss.kCoreBarHp[i] > 0f) kPaste(kCoreBarPm, K_CORE_BAR_X[i], mid);
        if (!boss.kWayOpen()) kPaste(kMainOrbPm, K_MAIN_ORB_X, mid);
        if (boss.kShield) kPaste(kShieldPm, 0, mid);
        kWallTex.draw(kWallPm, 0, 0);
        kWallMask = PixelMask.fromPixmap(kWallPm, 0, K_WALL_W);
    }

    private boolean kierkeBossNow() { return boss != null && boss.kierke && kWallPm != null; }

    /** Does the rectangle touch the wall (either copy on screen) or the main body (their drawn pixels)? */
    private boolean kierkeTouches(float x, float y, float w, float h) {
        if (kMainMask.overlaps(boss.x + K_MAIN_X, boss.y, x, y, w, h)) return true;
        for (int n = 0; n < 2; n++)
            if (kWallMask.overlaps(boss.kColX(), boss.kRowY(Boss.K_STACK_H, n), x, y, w, h)) return true;
        return false;
    }

    /** World box of wall columns x0..x1 and rows r0..r1 (copy n) into kBx. */
    private void kBox(float x0, float x1, float r0, float r1, int n) {
        kBx[0] = boss.kColX() + x0; kBx[1] = boss.kRowY(r1, n); kBx[2] = x1 - x0; kBx[3] = r1 - r0;
    }

    /** A part was destroyed: a blast, points, and the wall without it. */
    private void kBreak(Player owner, int pts, float scale) {
        boom(kBx[0] + kBx[2] / 2f, kBx[1] + kBx[3] / 2f, scale);
        a.play("boom", 0.3f + 0.1f * scale);
        addScore(owner, pts);
        kCompose();
    }

    /**
     * A player shot against the machine wall or its main body. In a unit's row it hits the front part still standing:
     * the shield (middle unit, can't be hurt), the bars (leftmost first), the middle unit's core bars, then the orb.
     * Returns true if it hit something (the boss stops every shot).
     */
    private boolean kierkeBossShot(Bullet b, float hx, float hy, float hw, float hh, boolean laser) {
        if (!kierkeBossNow() || boss.dying || !kierkeTouches(hx, hy, hw, hh)) return false;
        if (laser) { if (b.hits.contains(boss)) return true; b.hits.add(boss); } else { b.dead = true; missileBurst(b); }
        Player owner = b.owner;
        float mx = boss.x + K_MAIN_X;
        if (kMainMask.overlaps(mx, boss.y, hx, hy, hw, hh)) {   // the main body: only its giant core, once the way is open
            float cx = mx + K_CORE_X, cy = boss.y + boss.h - K_CORE_Y - K_CORE_H;
            boolean onCore = overlap(hx, hy, hw, hh, cx, cy, K_CORE_W, K_CORE_H);
            if (!onCore || !boss.kWayOpen() || boss.hitCooldown > 0f) {
                a.play("hit", 0.15f);
                hitSpark(b, onCore ? cx : mx, onCore ? cy : boss.y, onCore ? K_CORE_W : kMainR.getRegionWidth(), onCore ? K_CORE_H : boss.h);
                return true;
            }
            boss.hp = Math.max(0f, boss.hp - b.dmg);
            boss.hitCooldown = Boss.HIT_COOLDOWN;
            boss.flash = 0.08f;
            a.play("hit", 0.2f);
            if (boss.hp > 0f) { hitSpark(b, cx, cy, K_CORE_W, K_CORE_H); return true; }
            boss.dying = true;
            eBullets.clear();
            addScore(owner, 5000);
            return true;
        }
        float right = hx + hw - boss.kColX();                  // how far into the wall the shot reaches
        for (int n = 0; n < 2; n++)
            for (int k = 0; k < Boss.K_UNITS; k++) {
                int row = Boss.kUnitRow(k);
                kBox(0, K_WALL_W, row, row + K_PART_H, n);
                if (!overlap(hx, hy, hw, hh, kBx[0], kBx[1], kBx[2], kBx[3])) continue;
                boolean mid = k == Boss.K_MIDDLE;
                if (mid && boss.kShield) {                      // the shield: can't be hurt
                    kBox(0, K_SHIELD_W, row, row + K_PART_H, n);
                    a.play("hit", 0.15f); hitSpark(b, kBx[0], kBx[1], kBx[2], kBx[3]);
                    return true;
                }
                int bar = boss.kFrontBar(k);
                if (bar >= 0) {
                    float bx = K_BAR_X0 + K_BAR_STEP * bar;
                    kBox(bx, bx + K_BAR_W, row, row + K_PART_H, n);
                    if (right < bx) break;                      // (short of it: the wall around it took the shot)
                    boss.kBarHp[k][bar] -= b.dmg;
                    if (boss.kBarHp[k][bar] > 0f) { a.play("hit", 0.15f); hitSpark(b, kBx[0], kBx[1], kBx[2], kBx[3]); }
                    else kBreak(owner, BAR_SCORE, 0.6f);
                    return true;
                }
                if (mid) {
                    int cb = boss.kFrontCoreBar();
                    if (cb >= 0) {
                        float bx = K_CORE_BAR_X[cb];
                        kBox(bx, bx + K_CORE_BAR_W, row, row + K_PART_H, n);
                        if (right < bx) break;
                        boss.kCoreBarHp[cb] -= b.dmg;
                        if (boss.kCoreBarHp[cb] > 0f) { a.play("hit", 0.15f); hitSpark(b, kBx[0], kBx[1], kBx[2], kBx[3]); }
                        else kBreak(owner, BAR_SCORE, 0.7f);
                        return true;
                    }
                }
                if (boss.kOrbHp[k] > 0f) {
                    float ox = mid ? K_MAIN_ORB_X : K_ORB_CX - K_ORB_R, ow = mid ? K_MAIN_ORB_W : 2f * K_ORB_R;
                    kBox(ox, ox + ow, row, row + K_PART_H, n);
                    if (right < ox) break;
                    boss.kOrbHp[k] -= b.dmg;
                    if (boss.kOrbHp[k] > 0f) { a.play("hit", 0.15f); hitSpark(b, kBx[0], kBx[1], kBx[2], kBx[3]); return true; }
                    if (!mid) {                                 // an outer orb: its pixels go from the wall picture
                        float cy = row + K_PART_H / 2f;
                        for (int y = (int) (cy - 7f); y < cy + 7f; y++)
                            for (int x = (int) (K_ORB_CX - 7f); x < K_ORB_CX + 7f; x++)
                                if ((x + 0.5f - K_ORB_CX) * (x + 0.5f - K_ORB_CX) + (y + 0.5f - cy) * (y + 0.5f - cy) <= K_ORB_R * K_ORB_R)
                                    kWallBase.drawPixel(x, y, 0);
                    }
                    kBreak(owner, BAR_ORB_SCORE, 1.2f);
                    if (!mid && boss.kShield && boss.kOuterOrbsGone()) {   // the four outer orbs gone: the shield goes
                        boss.kShield = false;
                        kBox(0, K_SHIELD_W, Boss.kUnitRow(Boss.K_MIDDLE), Boss.kUnitRow(Boss.K_MIDDLE) + K_PART_H, 0);
                        kBreak(null, 0, 1.6f);
                    }
                    return true;
                }
                break;
            }
        a.play("hit", 0.15f);                                // the wall's body: it just stops the shot
        hitSpark(b, boss.kColX(), boss.kRowY(Boss.K_STACK_H, 0), K_WALL_W, Boss.K_STACK_H);
        return true;
    }

    private void drawKierkeBoss() {
        if (boss.flash > 0f) batch.setColor(1f, 0.55f, 0.55f, 1f);
        if (boss.dying && (int) (boss.dieT * 20f) % 2 == 0) batch.setColor(1f, 1f, 1f, 0.5f);
        batch.draw(kMainR, boss.x + K_MAIN_X, boss.y);       // (behind the wall)
        int off = Math.round(boss.kOffMod()) % Boss.K_STACK_H;
        int h0 = Math.min(H, Boss.K_STACK_H - off);          // the wall from row off down, then from its top again
        kWallR.setRegion(0, off, K_WALL_W, h0);
        batch.draw(kWallR, boss.kColX(), boss.kTop - h0, K_WALL_W, h0);
        if (h0 < H) {
            kWallR.setRegion(0, 0, K_WALL_W, H - h0);
            batch.draw(kWallR, boss.kColX(), boss.kTop - H, K_WALL_W, H - h0);
        }
        batch.setColor(Color.WHITE);
    }

    // ---- bar-wall bosses (Albert, 2026-10-27): Eioneus (stage 4, sprites/boss3.png) and Op 4's second boss
    // (stage 6, sprites/boss5b.png). Columns of bar walls (sprites/boss_bar.png, painted into the boss picture), each
    // guarding a coloured orb above it (the ships shoot up at it from below). A shot into a column hurts its lowest bar
    // left (BAR_HP each), and once the bars are gone, the orb (BAR_ORB_HP). The middle column is closed off by the core
    // shield (sprites/boss3_core_shield.png, also painted into the picture) until every other orb is destroyed: then
    // the shield is blown away. Destroying the middle orb beats the boss. The rest of the body only stops shots.
    // Positions are top-left in the boss picture (x right, y down), found by matching the pictures.
    /** One bar-wall boss: its picture, the boss part it is (-1 = the stage's only boss), bars, orbs, middle column, shield. */
    private static final class BarBoss {
        final int level, part;
        final String file;
        /** Per column: its bars, lowest (first in line) first. */
        final int[][][] bars;
        /** Per column: its orb (top-left of a 10x10 box). */
        final int[][] orbs;
        final int mid, shieldX, shieldY;
        BarBoss(int level, int part, String file, int[][][] bars, int[][] orbs, int mid, int shieldX, int shieldY) {
            this.level = level; this.part = part; this.file = file; this.bars = bars; this.orbs = orbs;
            this.mid = mid; this.shieldX = shieldX; this.shieldY = shieldY;
        }
    }
    /** Five bars stacked 8 px apart, the lowest with its top at y. */
    private static int[][] barStack(int x, int y) { return new int[][]{{x, y}, {x, y - 8}, {x, y - 16}, {x, y - 24}, {x, y - 32}}; }
    private static final BarBoss[] BAR_BOSSES = {
            // Eioneus: blue, red (middle), grey
            new BarBoss(3, -1, "boss3", new int[][][]{barStack(55, 98), barStack(95, 82), barStack(135, 98)},
                    new int[][]{{56, 46}, {96, 30}, {136, 46}}, 1, 89, 89),
            // Op 4, the gun units (boss5b): blue, grey, red (middle), yellow, green
            new BarBoss(5, 1, "boss5b", new int[][][]{barStack(23, 105), barStack(63, 81), barStack(119, 137), barStack(175, 81), barStack(215, 105)},
                    new int[][]{{24, 53}, {64, 29}, {120, 85}, {176, 29}, {216, 53}}, 2, 113, 152),
    };
    private static final int BAR_W = 12, BAR_H = 6, BAR_ORB = 10, BAR_PARTS = 6;   // per column: 5 bars, then the orb (index 5)
    private static final float BAR_HP = 10f, BAR_ORB_HP = 30f;
    private static final int BAR_SCORE = 100, BAR_ORB_SCORE = 1000;
    /** This stage's bar-wall boss (null: none). */
    private BarBoss bb;
    /** The untouched picture, the one being shot to pieces (and its texture), the bar and shield pictures. */
    private Pixmap barOrig, barPix, barPm, barShieldPm;
    private Texture barTex;
    private TextureRegion barR;
    /** Its exact shape, with the shot-away shield taken out. */
    private PixelMask barMask;
    private float[][] barHp;
    /** One object per part, so a laser hurts each part once. */
    private Object[][] barKey;
    private boolean barShield;

    /** Loads this stage's bar-wall boss picture as an editable copy, if the bar and shield pictures are there. */
    private void loadBarBoss() {
        for (BarBoss c : BAR_BOSSES) if (c.level == levelIdx) bb = c;
        if (bb == null) return;
        String f = "sprites/" + bb.file + ".png";
        if (!Gdx.files.internal(f).exists() || !Gdx.files.internal("sprites/boss_bar.png").exists()
                || !Gdx.files.internal("sprites/boss3_core_shield.png").exists()) { bb = null; return; }
        barOrig = new Pixmap(Gdx.files.internal(f));
        barPix = new Pixmap(barOrig.getWidth(), barOrig.getHeight(), Pixmap.Format.RGBA8888);
        barPix.setBlending(Pixmap.Blending.None);
        barPix.drawPixmap(barOrig, 0, 0);
        barPm = new Pixmap(Gdx.files.internal("sprites/boss_bar.png"));
        barShieldPm = new Pixmap(Gdx.files.internal("sprites/boss3_core_shield.png"));
        barTex = new Texture(barPix);
        barR = new TextureRegion(barTex);
        int n = bb.bars.length;
        barHp = new float[n][BAR_PARTS];
        barKey = new Object[n][BAR_PARTS];
        for (int c = 0; c < n; c++) for (int k = 0; k < BAR_PARTS; k++) barKey[c][k] = new Object();
    }

    /** Is the boss being fought (or coming next) this stage's bar-wall boss? */
    private boolean barBossNow() { return bb != null && (bb.part < 0 || bossPart == bb.part); }

    /** A new (or restarted) fight: every part back, full health; the health bar shows what is left of the parts. */
    private void resetBarBoss() {
        if (!barBossNow() || boss == null) return;
        barPix.drawPixmap(barOrig, 0, 0);
        barTex.draw(barPix, 0, 0);
        barMask = PixelMask.load("sprites/" + bb.file + ".png");
        boss.mask = barMask;
        barShield = true;
        float total = 0f;
        for (int c = 0; c < barHp.length; c++)
            for (int k = 0; k < BAR_PARTS; k++) total += barHp[c][k] = k < BAR_PARTS - 1 ? BAR_HP : BAR_ORB_HP;
        boss.hp = boss.maxHp = total;
    }

    /** Column c's part still standing that is first in line (lowest bar, then the orb); -1 = nothing left. */
    private int barTarget(int c) {
        for (int k = 0; k < BAR_PARTS; k++) if (barHp[c][k] > 0f) return k;
        return -1;
    }

    /** Part k of column c in the picture: x, y (top-left, y down), w, h into barBox. */
    private final int[] barBox = new int[4];
    private void barPartBox(int c, int k) {
        boolean orb = k == BAR_PARTS - 1;
        int[] p = orb ? bb.orbs[c] : bb.bars[c][k];
        barBox[0] = p[0]; barBox[1] = p[1];
        barBox[2] = orb ? BAR_ORB : BAR_W; barBox[3] = orb ? BAR_ORB : BAR_H;
    }

    /**
     * A ship's shot (on screen: hx, hy, hw, hh) against a bar-wall boss's columns: if it is in a column that still
     * has something standing, that part takes it. Returns true if it was dealt with here.
     */
    private boolean barBossShot(Bullet b, float hx, float hy, float hw, float hh, boolean laser) {
        if (!barBossNow() || boss == null || boss.dying || boss.appearing) return false;
        for (int c = 0; c < barHp.length; c++) {
            if (c == bb.mid && barShield) continue;      // the middle column: behind the core shield
            int k = barTarget(c);
            if (k < 0) continue;
            // the column: from the top of its orb down to just below its lowest bar
            int[] low = bb.bars[c][0];
            float top = bb.orbs[c][1], bottom = low[1] + BAR_H + 3f;
            float cx = boss.x + low[0], cy = boss.y + boss.h - bottom;
            if (!overlap(hx, hy, hw, hh, cx, cy, BAR_W, bottom - top)) continue;
            Object key = barKey[c][k];
            if (laser) { if (b.hits.contains(key)) return true; b.hits.add(key); } else b.dead = true;
            barHp[c][k] -= b.dmg;
            boss.flash = 0.08f;
            barPartBox(c, k);
            float px = boss.x + barBox[0], py = boss.y + boss.h - barBox[1] - barBox[3];
            if (barHp[c][k] <= 0f) barPartDestroyed(c, k, b.owner, px, py);
            else { a.play("hit", 0.15f); hitSpark(b, px, py, barBox[2], barBox[3]); }
            float left = 0f;
            for (float[] col : barHp) for (float v : col) left += Math.max(0f, v);
            if (!boss.dying) boss.hp = left;
            if (b.dead && b.kind == Bullet.Kind.MISSILE) boom(b.x, b.y, 0.6f);
            return true;
        }
        return false;
    }

    /** Part k of column c is shot away (bottom-left on screen px, py): gone from the picture, points, and what follows. */
    private void barPartDestroyed(int c, int k, Player owner, float px, float py) {
        barHp[c][k] = 0f;
        boolean orb = k == BAR_PARTS - 1;
        barPartBox(c, k);
        int black = 0x000000ff;                          // what is behind: the dark inside of the column
        for (int y = 0; y < barBox[3]; y++)
            for (int x = 0; x < barBox[2]; x++) {
                boolean part = orb ? (barOrig.getPixel(barBox[0] + x, barBox[1] + y) & 0xff) > 0
                        : (barPm.getPixel(x, y) & 0xff) > 0;
                if (part) barPix.drawPixel(barBox[0] + x, barBox[1] + y, black);
            }
        {   // the ships can fly up into a column as far as it is shot away (as on the MSX)
            int[] low = bb.bars[c][0];
            int ph2 = barPix.getHeight();
            for (int y = barBox[1]; y < ph2; y++)
                for (int x = low[0]; x < low[0] + BAR_W; x++) barMask.clear(x, ph2 - 1 - y);
        }
        boom(px + barBox[2] / 2f, py + barBox[3] / 2f, orb ? 1.2f : 0.5f);
        a.play("boom", orb ? 0.4f : 0.2f);
        if (bb.level != 3) addScore(owner, orb ? BAR_ORB_SCORE : BAR_SCORE);   // (Eioneus: no points for bars and orbs, as on the MSX)
        if (orb && c == bb.mid) {                        // the middle orb: the boss is beaten
            barTex.draw(barPix, 0, 0);
            boss.hp = 0f;
            boss.dying = true;
            boss.explodeArms(this);
            boss.explodeRings(this);
            eBullets.clear();
            addScore(owner, 5000);
            return;
        }
        boolean othersGone = true;
        for (int i = 0; i < barHp.length; i++) if (i != bb.mid && barHp[i][BAR_PARTS - 1] > 0f) othersGone = false;
        if (orb && barShield && othersGone) {           // every other orb gone
            barShield = false;                           // the core shield is blown away: the middle column is open
            int sh = barShieldPm.getHeight(), ph = barPix.getHeight();
            for (int y = 0; y < sh; y++)
                for (int x = 0; x < barShieldPm.getWidth(); x++) {
                    if ((barShieldPm.getPixel(x, y) & 0xff) == 0) continue;
                    barPix.drawPixel(bb.shieldX + x, bb.shieldY + y, 0);
                    barMask.clear(bb.shieldX + x, ph - 1 - (bb.shieldY + y));
                }
            // ...and the way up the middle column is clear: nothing left below its lowest bar
            int[] low = bb.bars[bb.mid][0];
            for (int y = low[1] + BAR_H; y < ph; y++)
                for (int x = low[0]; x < low[0] + BAR_W; x++) {
                    barPix.drawPixel(x, y, 0);
                    barMask.clear(x, ph - 1 - y);
                }
            float sx = boss.x + bb.shieldX + barShieldPm.getWidth() / 2f, sy = boss.y + boss.h - bb.shieldY - sh / 2f;
            boom(sx, sy + 8f, 1.6f);
            boom(sx, sy - 8f, 1.6f);
            a.play("boom", 0.5f);
        }
        barTex.draw(barPix, 0, 0);
    }

    /**
     * Stages with more than one boss, fought one after the other (Albert): sprites/boss<N>a.png, boss<N>b.png, ...
     * Per boss its top-left inside the map's "boss" object (x right, y down). Op 4 (stage 6): first boss5a, the brain
     * sphere, then boss5b, the gun units above it. The scrolling stops for every boss but the last one (once it is
     * BOSS_PART_TOP px inside the top edge) and goes on when it is beaten; the last one is fought like a single boss.
     */
    private static final int[][][] BOSS_PARTS = {null, null, null, null, null, {{5, 229}, {0, 0}}, null};
    private static final float BOSS_PART_TOP = 8f;
    /** The bosses of a multi-boss stage (null: one boss, bossR) and which one is (or comes) next. */
    private TextureRegion[] bossPartR;
    private PixelMask[] bossPartMask;
    private int bossPart;
    private final float[] partBox = new float[4];

    /** sprites/boss<N>.png (or the first of BOSS_PARTS); also sets bossMask, and bossPartR / bossPartMask. */
    private TextureRegion loadBossPicture() {
        String single = "sprites/boss" + levelIdx + ".png";
        int[][] parts = levelIdx < BOSS_PARTS.length ? BOSS_PARTS[levelIdx] : null;
        if (Gdx.files.internal(single).exists() || parts == null) {
            bossMask = PixelMask.load(single);
            return a.one("boss" + levelIdx);
        }
        bossPartR = new TextureRegion[parts.length];
        bossPartMask = new PixelMask[parts.length];
        for (int i = 0; i < parts.length; i++) {
            String n = "boss" + levelIdx + (char) ('a' + i);
            bossPartR[i] = a.one(n);
            bossPartMask[i] = PixelMask.load("sprites/" + n + ".png");
        }
        bossMask = bossPartMask[0];
        return bossPartR[0];
    }

    /** The picture of the boss being fought (or coming next). */
    private TextureRegion curBossR() {
        if (barBossNow()) return barR;                   // (a bar-wall boss: the picture with the parts shot away)
        return bossPartR != null ? bossPartR[Math.min(bossPart, bossPartR.length - 1)] : bossR;
    }

    /** Multi-boss stage: where the current boss sits in the map (world, bottom-left, w, h) into partBox. */
    private void bossPartBox() {
        int k = Math.min(bossPart, bossPartR.length - 1);
        int[] off = BOSS_PARTS[levelIdx][k];
        float w = bossPartR[k].getRegionWidth(), h = bossPartR[k].getRegionHeight();
        partBox[0] = level.bossX + off[0];
        partBox[1] = level.bossY + level.bossH - off[1] - h;
        partBox[2] = w; partBox[3] = h;
    }

    private void spawnBoss() {
        bossSpawned = true;
        bossDissolving = false;
        int[] hp = {70, 100, 120, 140, 160, 180, 220};
        TextureRegion br = curBossR();
        boss = new Boss(levelIdx, scrollX + W + 20f, hp[levelIdx], br.getRegionWidth(), br.getRegionHeight());
        boss.mask = bossPartMask != null ? bossPartMask[Math.min(bossPart, bossPartMask.length - 1)] : bossMask;
        coreBlocksShown = CORE_BLOCKS;
        resetBarBoss();                               // (bar-wall bosses: all bars, orbs and the core shield back)
        if (level.bossFixed) {                        // sits where the map has it (stages 5-7 for now)
            if (bossPartR != null) { bossPartBox(); boss.x = partBox[0]; boss.y = partBox[1]; }
            else { boss.x = level.bossX; boss.y = level.bossY; }
            boss.fixed = true;
            if (levelIdx == 4 && kWallBase != null) {     // Kierke's machine wall: top-aligned in the map's boss box
                boss.y = level.bossY + level.bossH - boss.h;
                boss.initKierke(level.bossY + level.bossH);
                kResetWall();                             // (a whole wall, e.g. after a retry)
                kCompose();
            }
            boss.entering = false;
            if (op4End()) op4Boss();                  // Op 4: the brain sphere rises in, the gun units appear
        } else if (level.vertical) {                  // comes down from the top, in the middle
            boss.x = scrollX + W / 2f - boss.w / 2f;
            boss.y = scrollY + H + (levelIdx == 1 ? 70f : 10f);   // (Tetran's tentacles reach 60+ px around it)
            if (levelIdx == 1) boss.initTetran();
            if (eioEnd()) boss.initEioneus(this);     // (Eioneus: its own movement and lasers, from the recording)
        }
        if (levelIdx == 0) {
            // the brain's two arms, as attached in the original map (sprite 100x100, y up from its bottom):
            // {anchor x, anchor y, direction x, direction y, ball spacing}
            boss.armSpec = new float[][]{
                    {29f, 96f, -16f, -16f, 22.6f},   // from the top left, down at 45 degrees
                    {37f, 32f, -16f, -8f, 17.9f},    // from under the eye, down and to the left
            };
        }
        if (coreR != null) {                          // only the core takes damage
            boss.hasCore = true;
            boss.coreMask = coreMask;
            boss.coreX = coreOX; boss.coreY = coreOY;
            boss.coreW = coreR.getRegionWidth(); boss.coreH = coreR.getRegionHeight();
        }
        if (hatchF != null && !level.bossFixed && !level.vertical) {   // Lavinia warship: in from the left, the hatch on its nose
            boss.hasHatch = true;
            boss.hatchW = hatchF[0].getRegionWidth(); boss.hatchH = hatchF[0].getRegionHeight();
            boss.hatchX = 1f; boss.hatchY = boss.h - 16f - boss.hatchH;   // (16 px below the top of boss2.png)
            boss.x = scrollX - boss.w;
            boss.y = scrollY + H / 2f - boss.h / 2f;
        }
        if (eyeF != null) {
            boss.hasEye = true;
            boss.eyeFrames = eyeF.length;
            boss.eyeX = eyeOX; boss.eyeY = eyeOY;
            boss.eyeW = eyeF[0].getRegionWidth(); boss.eyeH = eyeF[0].getRegionHeight();
        }
        a.play("power", 0.3f);
        if (bossPart == 0 && level.bossMusic != null && !level.bossMusic.isEmpty()) game.bgm.fadeOutThen(level.bossMusic, true, level.bossMusicLoop, level.bossMusicLoopEnd, BOSS_MUSIC_FADE, BOSS_MUSIC_GAP);
    }

    void bossDefeated() {
        if (state != State.PLAY) return;
        if (bossPartR != null && bossPart < bossPartR.length - 1) {   // a multi-boss stage: on to the next boss
            eBullets.clear();
            if (!boss.dissolve) {                     // (one that broke up pixel by pixel is just gone)
                boom(boss.x + boss.w / 2f, boss.y + boss.h / 2f, 2.5f);
                a.play("boom", 0.5f);
            }
            boss = null;
            op4T = 0f;
            bossPart++;
            bossSpawned = false;                      // (the scrolling goes on; the next one wakes on screen)
            return;
        }
        for (Player p : players) if (p != null && !p.out) p.score += stageBonus();
        eBullets.clear();
        gunLasers.clear();
        if (!boss.dissolve) {
            boom(boss.x + boss.w / 2f, boss.y + boss.h / 2f, 2.5f);   // a last big blast, then the boss is gone
            a.play("boom", 0.5f);
        }
        boss = null;
        setState(State.CLEAR);
    }

    // =============================================================== Op 4's end (stage 6)
    // From the MSX recording (220-268 s; Albert, 2026-10-27): the scrolling stops at the end of the shaft (the path's
    // end, camera top 376 in the map) and the whole terrain on the screen breaks up pixel by pixel (OP4_TERRAIN_TIME s),
    // leaving black space. The brain sphere (boss5a) then rises in through the bottom edge; option eaters come in
    // while it is there. Beaten, it breaks up the same way, and the gun units (boss5b) appear pixel by pixel where they
    // are. The ships fly over both bosses (Boss.ghost): only the sphere's eye and the gun units' bars and orbs (and
    // their core shield) stop shots.
    private static final int OP4_NONE = 0, OP4_TERRAIN = 1, OP4_FIGHT = 2;
    private static final float OP4_TERRAIN_TIME = 3f, OP4_SPHERE_WAIT = 1.2f, OP4_GUNS_WAIT = 1.5f;
    /** Option eaters during the sphere fight: the first after OEATER_FIRST s, then one every OEATER_EVERY s. */
    private static final float OEATER_FIRST = 4f, OEATER_EVERY = 5f;
    /** Green mines during the gun units' fight (Albert): the first GMINE5_FIRST s after they have appeared, then one
     *  every GMINE5_EVERY_MIN..MAX s, in from a random edge of the screen (left, right, top or bottom). */
    private static final float GMINE5_FIRST = 3f, GMINE5_EVERY_MIN = 2.5f, GMINE5_EVERY_MAX = 4.5f;
    /** The sphere's eye in boss5a.png (bottom-left, y up from the picture's bottom, w, h): its only weak spot. */
    private static final float[] SPHERE_EYE = {100f, 27f, 13f, 15f};
    private static final int SPHERE_HP = 60;
    private int op4State;
    private float op4T, oeaterT, gmine5T;
    /** The terrain has dissolved (its art isn't drawn, nothing to run into); a beaten boss is breaking up. */
    private boolean terrainGone, bossDissolving;
    /** The gun units coming together pixel by pixel (null when they are whole). */
    private Dissolver gunsAppear;

    /** Is this Op 4, with its two bosses (boss5a, boss5b)? */
    private boolean op4End() { return levelIdx == 5 && bossPartR != null; }

    private void updateOp4End(float dt) {
        if (scrollT < level.stopT) return;
        op4T += dt;
        switch (op4State) {
            case OP4_NONE:
                dissolveTerrain();
                op4State = OP4_TERRAIN;
                op4T = 0f;
                break;
            case OP4_TERRAIN:
                if (op4T >= OP4_TERRAIN_TIME + OP4_SPHERE_WAIT) { op4State = OP4_FIGHT; op4T = 0f; spawnBoss(); }
                break;
            default:
                if (boss == null && !bossSpawned && bossPart > 0 && op4T >= OP4_GUNS_WAIT) spawnBoss();   // the gun units
                if (boss != null && boss.sphere && !boss.dying && !boss.entering && state == State.PLAY
                        && (oeaterT -= dt) <= 0f) { oeaterT = OEATER_EVERY; spawnOptionEater(); }
                if (boss != null && boss.guns && !boss.dying && !boss.appearing && state == State.PLAY
                        && (gmine5T -= dt) <= 0f) { gmine5T = MathUtils.random(GMINE5_EVERY_MIN, GMINE5_EVERY_MAX); spawnEdgeMine(); }
                if (gunsAppear != null && boss != null && boss.guns) {
                    gunsAppear.set(boss.appearT / Boss.GUNS_APPEAR);
                    if (!boss.appearing) { gunsAppear.dispose(); gunsAppear = null; }
                }
                break;
        }
    }

    /** spawnBoss for Op 4: where the boss starts and how it moves (see Boss.initSphere / initGuns). */
    private void op4Boss() {
        boss.fixed = false;
        if (bossPart == 0) {
            boss.initSphere(boss.mask);
            boss.x = scrollX + W / 2f - boss.bounceX0 - boss.bounceW / 2f;
            boss.y = scrollY - boss.bounceY0 - boss.bounceH;   // (just below the bottom edge)
            boss.weakX = SPHERE_EYE[0]; boss.weakY = SPHERE_EYE[1]; boss.weakW = SPHERE_EYE[2]; boss.weakH = SPHERE_EYE[3];
            boss.hp = boss.maxHp = SPHERE_HP;         // (only its eye can be hit: about 7 s of shooting on the MSX)
            oeaterT = OEATER_FIRST;
        } else {
            boss.initGuns(this);
            gmine5T = GMINE5_FIRST;
            if (gunsAppear != null) gunsAppear.dispose();
            gunsAppear = barPix != null ? Dissolver.appear(barPix) : null;
            if (gunsAppear == null) boss.appearing = false;
        }
    }

    /**
     * The end of the shaft: everything drawn on the screen (the stage art) is copied and breaks up pixel by pixel
     * (dissolveDeath); from now on the art isn't drawn and there is no terrain to run into.
     */
    private void dissolveTerrain() { dissolveTerrain(OP4_TERRAIN_TIME); }

    private void dissolveTerrain(float time) {
        int camL = Math.round(scrollX), camB = Math.round(scrollY), sw = (int) W, sh = (int) H;
        Pixmap shot = new Pixmap(sw, sh, Pixmap.Format.RGBA8888);
        shot.setBlending(Pixmap.Blending.SourceOver);
        for (Level.Art t : level.art) {
            if (t.x > camL + W || t.x + t.w < camL || t.y > camB + H || t.y + t.h < camB) continue;
            if (!Gdx.files.internal(t.image).exists()) continue;
            Pixmap ap = new Pixmap(Gdx.files.internal(t.image));
            shot.drawPixmap(ap, Math.round(t.x) - camL, (camB + sh) - Math.round(t.y + t.h));   // (rows run down)
            ap.dispose();
        }
        if (podsShown()) for (int i = 0; i < level.pods.size(); i++)   // (Eioneus: the gun pods go with it)
            shot.drawPixmap(podPm, Math.round(podX(i)) - camL, (camB + sh) - Math.round(podY(i) + podPm.getHeight()));
        dissolveDeath(shot, camL, camB, time);
        shot.dispose();
        terrainGone = true;
        level.clearGrid();
        a.play("power", 0.4f);
    }

    /** A beaten Op 4 boss: its picture (the gun units: as shot to pieces) breaks up where it is. */
    private void bossDissolveStart() {
        bossDissolving = true;
        eBullets.clear();
        gunLasers.clear();
        if (barBossNow() && barPix != null) dissolveDeath(barPix, boss.x, boss.y, Boss.DISSOLVE_TIME);
        else dissolveDeath(curBossR(), boss.x, boss.y, Boss.DISSOLVE_TIME);
        a.play("boom", 0.4f);
    }

    /** The gun units' fight: a green mine (Kierke's, homing) comes in at a random edge of the screen, 11 px showing. */
    private void spawnEdgeMine() {
        if (greenMineF == null) return;
        float in = Enemy.GMINE_IN, x, y;
        switch (MathUtils.random(3)) {
            case 0: x = scrollX - 16f + in; y = scrollY + MathUtils.random(24f, H - 40f); break;   // left
            case 1: x = scrollX + W - in; y = scrollY + MathUtils.random(24f, H - 40f); break;     // right
            case 2: x = scrollX + MathUtils.random(8f, W - 24f); y = scrollY + H - in; break;      // top
            default: x = scrollX + MathUtils.random(8f, W - 24f); y = scrollY - 16f + in; break;   // bottom
        }
        Enemy e = new Enemy(Enemy.Type.GREEN_MINE, x, y, levelIdx);
        e.vertical = true;
        e.gmFromBottom = y < scrollY;
        enemies.add(e);
    }

    /** The sphere fight: an option eater comes in through the bottom edge (Enemy.updateOptionEater). */
    private void spawnOptionEater() {
        if (optionEaterF == null) return;
        Enemy e = new Enemy(Enemy.Type.OPTION_EATER, scrollX + MathUtils.random(24f, W - 40f), scrollY - 16f, levelIdx);
        e.vertical = true;
        enemies.add(e);
    }

    /** An option eater touching an option eats it: the ship loses an option (its last one) and the eater is used up. */
    void eatOption(Enemy e) {
        for (Player p : players) {
            if (!alive(p) || p.options <= 0) continue;
            for (int i = 0; i < p.options; i++) {
                if (!overlap(e.x + 2f, e.y + 2f, e.w - 4f, e.h - 4f, optX(p, i), optY(p, i), 12f, 12f)) continue;
                p.options--;
                e.dead = true;
                boom(optX(p, i) + 6f, optY(p, i) + 6f, 1f);
                a.play("boom", 0.3f);
                return;
            }
        }
    }

    /** Can a shot (or laser) at this rectangle hit the boss? Op 4's bosses (ghost) only stop shots on their weak spots. */
    private boolean bossShotTouches(float x, float y, float w, float h) {
        if (!boss.ghost) return touchesBoss(x, y, w, h);
        if (boss.appearing) return false;
        return boss.weakTouches(x, y, w, h) || barShieldTouches(x, y, w, h);
    }

    /** A bar-wall boss's core shield, while it is there (it stops shots even on a boss the ships fly over). */
    private boolean barShieldTouches(float x, float y, float w, float h) {
        if (!barBossNow() || !barShield || barShieldPm == null || boss == null) return false;
        float sw = barShieldPm.getWidth(), sh = barShieldPm.getHeight();
        return overlap(x, y, w, h, boss.x + bb.shieldX, boss.y + boss.h - bb.shieldY - sh, sw, sh);
    }

    // =============================================================== Eioneus's boss room (stage 4)
    // From the MSX recording 216-266 s (Albert, 2026-10-27). The scrolling stops at the top of the cave; POD_WAIT s
    // later the three gun pods (map objects "gunpod", gunpod.png) come down out of the ceiling, POD_STEP_PX px every
    // POD_STEP s, until they hang in their places. Each has a column of POD_BARS bars (boss_bar.png, painted into
    // gunpod.png) in front of its red orb: a shot into the column hurts its lowest bar left (POD_BAR_HP each), and once
    // its bars are gone the pod is beaten (POD_SCORE points, the moment the bars are gone, as on the MSX; the orb
    // stays). The rest of a pod stops shots and lasers and hurts the ships; the inside of a column is free once its
    // bars are gone (the ship flies up into it in the recording). While the pods hang there, they let out bouncing
    // bubbles (Enemy.bubble): the first BUBBLE_FIRST s after they are in place, then one every BUBBLE_EVERY s while
    // there are fewer than BUBBLE_MAX. PODS_DONE_WAIT s after the last pod is beaten the bubbles go and everything on
    // the screen breaks up pixel by pixel (EIO_TERRAIN_TIME s, leaving black space), and EIO_BOSS_WAIT s later the
    // boss comes down (Boss.initEioneus).
    private static final float POD_WAIT = 0f,   // (Albert, 2026-10-30: they come as soon as the scrolling stops; was 6 s)
            POD_STEP = 0.4f, POD_STEP_PX = 8f, POD_BAR_HP = 10f,
            BUBBLE_FIRST = 1.5f, BUBBLE_EVERY = 0.8f, PODS_DONE_WAIT = 2f, EIO_TERRAIN_TIME = 2f, EIO_BOSS_WAIT = 4f;
    private static final int POD_BARS = 4, POD_SCORE = 10000, BUBBLE_MAX = 4;
    /** The bars in gunpod.png (top-left, y down), lowest (first in line) first; the top of its orb. */
    private static final int[][] POD_BAR_POS = {{16, 58}, {16, 50}, {16, 42}, {16, 34}};
    private static final int POD_ORB_TOP = 12;
    private static final int EIO_NONE = 0, EIO_WAIT = 1, EIO_DESCEND = 2, EIO_FIGHT = 3, EIO_AFTER = 4, EIO_TERRAIN = 5, EIO_BOSS = 6;
    private int eioState;
    private float eioT, eioStepT, bubbleT;
    /** How far above their places the pods still are (px). */
    private float podOff;
    private float[][] podHp;
    private boolean[] podBeaten;
    private Object[][] podKey;
    /** gunpod.png with its bars taken out (they are drawn on their own while they stand), its solid (not dark) pixels. */
    private Pixmap podPm;
    private TextureRegion podBaseR, podBarR;
    private PixelMask podMask;

    /** Is this Eioneus, with its gun pods before the boss? */
    private boolean eioEnd() { return levelIdx == 3 && podBaseR != null; }

    /** Are the gun pods on the screen (coming down, or hanging there until the terrain breaks up)? */
    private boolean podsShown() { return eioEnd() && eioState >= EIO_DESCEND && eioState < EIO_TERRAIN; }

    private float podX(int i) { return level.pods.get(i)[0]; }
    private float podY(int i) { return level.pods.get(i)[1] + podOff; }

    private void loadPods() {
        if (levelIdx != 3 || level.pods.isEmpty() || !Gdx.files.internal("sprites/gunpod.png").exists()
                || !Gdx.files.internal("sprites/boss_bar.png").exists()) return;
        Pixmap src = new Pixmap(Gdx.files.internal("sprites/gunpod.png"));
        podPm = new Pixmap(src.getWidth(), src.getHeight(), Pixmap.Format.RGBA8888);
        podPm.setBlending(Pixmap.Blending.None);
        podPm.drawPixmap(src, 0, 0);
        src.dispose();
        Pixmap bar = new Pixmap(Gdx.files.internal("sprites/boss_bar.png"));
        for (int[] b : POD_BAR_POS)                      // (behind a bar: the dark inside of the column)
            for (int y = 0; y < bar.getHeight(); y++)
                for (int x = 0; x < bar.getWidth(); x++)
                    if ((bar.getPixel(x, y) & 0xff) != 0) podPm.drawPixel(b[0] + x, b[1] + y, 0x000000ff);
        bar.dispose();
        int w = podPm.getWidth(), h = podPm.getHeight();
        boolean[] solid = new boolean[w * h];
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                int c = podPm.getPixel(x, y);
                int r = c >>> 24, gg = (c >>> 16) & 0xff, bb2 = (c >>> 8) & 0xff;
                solid[(h - 1 - y) * w + x] = (c & 0xff) != 0 && Math.max(r, Math.max(gg, bb2)) >= 30;
            }
        podMask = new PixelMask(w, h, solid);
        podBaseR = new TextureRegion(new Texture(podPm));
        podBarR = new TextureRegion(new Texture(Gdx.files.internal("sprites/boss_bar.png")));
        int n = level.pods.size();
        podHp = new float[n][POD_BARS];
        podBeaten = new boolean[n];
        podKey = new Object[n][POD_BARS];
        for (int i = 0; i < n; i++) for (int k = 0; k < POD_BARS; k++) podKey[i][k] = new Object();
        resetPods();
    }

    /** The pod room starts over (a new try): pods whole and out of sight, no bubbles. */
    private void resetPods() {
        if (podHp == null) return;
        eioState = EIO_NONE; eioT = 0f; podOff = 0f;
        for (int i = 0; i < podHp.length; i++) { podBeaten[i] = false; for (int k = 0; k < POD_BARS; k++) podHp[i][k] = POD_BAR_HP; }
    }

    private void updateEioneusEnd(float dt) {
        if (scrollT < level.stopT) return;
        eioT += dt;
        switch (eioState) {
            case EIO_NONE: {                              // the scrolling has stopped: the pods wait out of sight
                float lowest = Float.MAX_VALUE;
                for (float[] pd : level.pods) lowest = Math.min(lowest, pd[1]);
                podOff = Math.max(0f, (float) Math.ceil((scrollY + H - lowest) / POD_STEP_PX) * POD_STEP_PX);
                eioState = EIO_WAIT; eioT = 0f;
                break;
            }
            case EIO_WAIT:
                if (eioT >= POD_WAIT) { eioState = EIO_DESCEND; eioT = 0f; eioStepT = POD_STEP; }
                break;
            case EIO_DESCEND:
                podOff = Math.max(0f, podOff - POD_STEP_PX / POD_STEP * dt);   // smoothly (Albert, 2026-10-30), 20 px/s
                if (podOff <= 0f) { eioState = EIO_FIGHT; eioT = 0f; bubbleT = BUBBLE_FIRST; }
                break;
            case EIO_FIGHT: {
                if ((bubbleT -= dt) <= 0f) { bubbleT = BUBBLE_EVERY; if (bubbles() < BUBBLE_MAX) spawnBubble(); }
                boolean all = true;
                for (boolean b : podBeaten) all &= b;
                if (all) { eioState = EIO_AFTER; eioT = 0f; }
                break;
            }
            case EIO_AFTER:
                if (eioT >= PODS_DONE_WAIT) {
                    for (Enemy e : enemies) if (e.bubble) e.dead = true;
                    dissolveTerrain(EIO_TERRAIN_TIME);    // (the pods break up with it)
                    eioState = EIO_TERRAIN; eioT = 0f;
                }
                break;
            case EIO_TERRAIN:
                if (eioT >= EIO_TERRAIN_TIME + EIO_BOSS_WAIT && !bossSpawned) { eioState = EIO_BOSS; spawnBoss(); }
                break;
            default:
                break;
        }
    }

    private int bubbles() {
        int n = 0;
        for (Enemy e : enemies) if (e.bubble && !e.dead) n++;
        return n;
    }

    /** A pod (at random) lets a bubble out from under its middle, heading down to the left or right. */
    private void spawnBubble() {
        if (orbR == null) return;
        int i = MathUtils.random(level.pods.size() - 1);
        float line = Float.MAX_VALUE;
        for (int j = 0; j < level.pods.size(); j++) line = Math.min(line, podY(j));
        Enemy e = new Enemy(Enemy.Type.ORB, 0f, 0f, levelIdx);
        e.bubble = true;
        e.bubbleTop = line;
        e.x = podX(i) + podPm.getWidth() / 2f - e.w / 2f;
        e.y = line - e.h - 1f;
        e.vx = MathUtils.randomBoolean() ? -Enemy.BUBBLE_VX : Enemy.BUBBLE_VX;
        e.vy = -Enemy.BUBBLE_VY;
        enemies.add(e);
    }

    /** Lowest bar of pod i still standing (0 = the lowest), -1 = none left. */
    private int podTarget(int i) {
        for (int k = 0; k < POD_BARS; k++) if (podHp[i][k] > 0f) return k;
        return -1;
    }

    /** Does the rectangle touch a gun pod: its solid pixels or a bar still standing? */
    private boolean podTouches(float x, float y, float w, float h) {
        if (!podsShown()) return false;
        int ph = podPm.getHeight();
        for (int i = 0; i < level.pods.size(); i++) {
            float px = podX(i), py = podY(i);
            if (!overlap(x, y, w, h, px, py, podPm.getWidth(), ph)) continue;
            if (podMask.overlaps(px, py, x, y, w, h)) return true;
            for (int k = 0; k < POD_BARS; k++)
                if (podHp[i][k] > 0f && overlap(x, y, w, h, px + POD_BAR_POS[k][0], py + ph - POD_BAR_POS[k][1] - podBarR.getRegionHeight(),
                        podBarR.getRegionWidth(), podBarR.getRegionHeight())) return true;
        }
        return false;
    }

    /**
     * A ship's shot (on screen: hx, hy, hw, hh) against the gun pods: in a column with bars left, its lowest bar takes
     * it; anywhere else on a pod it just stops (a laser is stopped by laserBlocked). Returns true if dealt with here.
     */
    private boolean podShot(Bullet b, float hx, float hy, float hw, float hh, boolean laser) {
        if (!podsShown()) return false;
        int ph = podPm.getHeight();
        float bw = podBarR.getRegionWidth(), bh = podBarR.getRegionHeight();
        for (int i = 0; i < level.pods.size(); i++) {
            float px = podX(i), py = podY(i);
            int k = podBeaten[i] ? -1 : podTarget(i);
            if (k >= 0) {
                int[] low = POD_BAR_POS[0];
                float top = py + ph - POD_ORB_TOP, bottom = py + ph - (low[1] + bh + 3f);
                if (overlap(hx, hy, hw, hh, px + low[0], bottom, bw, top - bottom)) {
                    Object key = podKey[i][k];
                    if (laser) { if (b.hits.contains(key)) return true; b.hits.add(key); } else b.dead = true;
                    podHp[i][k] -= b.dmg;
                    float bx = px + POD_BAR_POS[k][0], by = py + ph - POD_BAR_POS[k][1] - bh;
                    if (podHp[i][k] <= 0f) {
                        podHp[i][k] = 0f;
                        boom(bx + bw / 2f, by + bh / 2f, 0.5f);
                        a.play("boom", 0.2f);
                        // (no points for a pod's bar, as on the MSX; the pod gives POD_SCORE)
                        if (podTarget(i) < 0) {               // its last bar: the pod is beaten
                            podBeaten[i] = true;
                            addScore(b.owner, POD_SCORE);
                            boom(px + podPm.getWidth() / 2f, py + ph - POD_ORB_TOP - 6f, 1.4f);
                            a.play("boom", 0.5f);
                        }
                    } else { a.play("hit", 0.15f); hitSpark(b, bx, by, bw, bh); }
                    if (b.dead && b.kind == Bullet.Kind.MISSILE) boom(b.x, b.y, 0.6f);
                    return true;
                }
            }
            if (!laser && podMask.overlaps(px, py, hx, hy, hw, hh)) {
                b.dead = true;
                hitSpark(b, px, py, podPm.getWidth(), ph);
                if (b.kind == Bullet.Kind.MISSILE) boom(b.x, b.y, 0.6f);
                return true;
            }
        }
        return false;
    }

    private void drawPods() {
        if (!podsShown()) return;
        int ph = podPm.getHeight();
        float bh = podBarR.getRegionHeight();
        for (int i = 0; i < level.pods.size(); i++) {
            float px = podX(i), py = podY(i);
            batch.draw(podBaseR, px, py);
            for (int k = 0; k < POD_BARS; k++)
                if (podHp[i][k] > 0f) batch.draw(podBarR, px + POD_BAR_POS[k][0], py + ph - POD_BAR_POS[k][1] - bh);
        }
    }

    // ---- the gun units' attacks (from the recording, 250.6 s and 256.6 s): out from under the columns, at five
    // places (GUN_EMITTERS: x, y top-down in boss5b.png), lasers grow down at GUN_LASER_SPEED px/s until they are
    // GUN_LASER_LEN px long, then fall on down the screen: the enemies' red / blue laser (Albert; dotted on the MSX).
    // A green creature comes out of the middle (GUN_MOUTH) and goes for the nearest ship (the Op 4 green flyer).
    private static final class GunLaser { float sx, top, bottom, t, ex, ey, speed, len; }
    private final ArrayList<GunLaser> gunLasers = new ArrayList<>();
    private static final float[][] GUN_EMITTERS = {{18f, 136f}, {42f, 120f}, {90f, 152f}, {170f, 152f}, {218f, 120f}};
    private static final float[] GUN_MOUTH = {125f, 180f};
    private static final float GUN_LASER_SPEED = 80f, GUN_LASER_LEN = 80f;

    void gunLasers(Boss b) { bossLasers(b, GUN_EMITTERS, GUN_LASER_SPEED, GUN_LASER_LEN); }

    /**
     * Eioneus (from the recording, 271.8 s and 282.8 s): four lasers out of the boss's leg tips (EIO_EMITTERS: x, y
     * top-down in boss3.png), growing down at EIO_LASER_SPEED px/s until EIO_LASER_LEN px long, then falling on. Dotted
     * on the MSX; here the enemies' laser, like the gun units' (Albert).
     */
    private static final float[][] EIO_EMITTERS = {{45f, 121f}, {76f, 137f}, {116f, 137f}, {155f, 121f}};
    private static final float EIO_LASER_SPEED = 160f, EIO_LASER_LEN = 100f;

    void eioneusLasers(Boss b) { bossLasers(b, EIO_EMITTERS, EIO_LASER_SPEED, EIO_LASER_LEN); }

    private void bossLasers(Boss b, float[][] emitters, float speed, float len) {
        for (float[] em : emitters) {
            GunLaser l = new GunLaser();
            l.ex = em[0]; l.ey = em[1]; l.speed = speed; l.len = len;
            l.sx = b.x - scrollX + l.ex;
            l.top = l.bottom = b.y - scrollY + b.h - l.ey;
            gunLasers.add(l);
        }
        a.play("shoot", 0.25f);
    }

    void gunCreature(Boss b) {
        if (greenF == null) return;
        Enemy f = new Enemy(Enemy.Type.GREEN_FLYER, 0f, 0f, levelIdx);
        f.vertical = true;
        f.gfHoming = true;
        f.gfStartAng = -MathUtils.HALF_PI;
        f.gfX = b.x - scrollX + GUN_MOUTH[0];
        f.gfY = b.y - scrollY + b.h - GUN_MOUTH[1];
        f.x = f.gfX + scrollX - 8f; f.y = f.gfY + scrollY - 8f;
        enemies.add(f);
    }

    private void updateGunLasers(float dt, boolean clearing) {
        if (gunLasers.isEmpty()) return;
        for (GunLaser l : gunLasers) {
            l.t += dt;
            if (l.t < l.len / l.speed && boss != null && !boss.dying) {   // still coming out: its top at the emitter
                l.sx = boss.x - scrollX + l.ex;
                l.top = boss.y - scrollY + boss.h - l.ey;
                l.bottom = l.top - l.speed * l.t;
            } else {
                l.top -= l.speed * dt;
                l.bottom -= l.speed * dt;
            }
        }
        gunLasers.removeIf(l -> l.top < 0f);
        if (clearing) return;
        for (Player p : players) {
            if (!alive(p)) continue;
            float hx = p.hitX(), hy = p.hitY();
            for (GunLaser l : gunLasers) {
                if (l.top - l.bottom < 1f) continue;
                float lw = omenLaserTex != null ? omenLaserTex[0].getHeight() : 5f;   // (as wide as the laser picture)
                if (overlap(hx, hy, Player.HIT_W, Player.HIT_H, scrollX + l.sx - lw / 2f, scrollY + l.bottom, lw, l.top - l.bottom)) {
                    hurt(p, false);
                    break;
                }
            }
        }
    }

    /**
     * The bosses' falling lasers (the gun units, Eioneus): the enemies' laser (sprites/enemy laser red / blue.png,
     * flashing), turned upright as the Rages fire it.
     */
    private void drawGunLasers() {
        if (gunLasers.isEmpty() || omenLaserTex == null) return;
        for (GunLaser l : gunLasers) {
            float b1 = Math.min(H, l.top), b0 = Math.max(0f, l.bottom), len = b1 - b0;
            if (len < 1f) continue;
            Texture lt = omenLaserTex[(int) (l.t * Enemy.OMEN_LASER_FLASH) % omenLaserTex.length];
            int th = lt.getHeight();
            float left = Math.round(scrollX + l.sx - th / 2f);
            batch.draw(lt, left + th, Math.round(scrollY) + b0, 0f, 0f, len, th, 1f, 1f, 90f,
                    Math.round(l.top - b1), 0, Math.round(len), th, false, false);
        }
    }

    private int stageBonus() { return 2000 + 1000 * (levelIdx + 1); }

    // =============================================================== shooting helpers (used by enemies/bosses)

    /** Is the point (x, y) on screen, at least `margin` px inside its edges? Things off screen never shoot. */
    boolean inView(float x, float y, float margin) {
        return x >= scrollX + margin && x <= scrollX + W - margin && y >= scrollY + margin && y <= scrollY + H - margin;
    }

    /** Op 4 moai rings flash between yellow and white this many times a second (Albert). */
    private static final float RING_FLASH = 15f;
    /** Points for shooting down an Op 4 moai ring. */
    private static final int RING_SCORE = 10;

    /** Op 4 moai rings: the yellow ones (#e0e027, as on the MSX); ring_laser.png is white. */
    private static final Color RING_YELLOW = new Color(0xe0 / 255f, 0xe0 / 255f, 0x27 / 255f, 1f);
    /** The red rings (they leave a capsule) flash between AMOEBA_RED and this. */
    private static final Color RING_RED_DARK = new Color(0xb0 / 255f, 0x20 / 255f, 0x20 / 255f, 1f);

    /** Red amoeba colour (#ff2727): the white of amoeba.png is tinted with it. */
    private static final Color AMOEBA_RED = new Color(1f, 0x27 / 255f, 0x27 / 255f, 1f);
    /**
     * Amoebas all change colour together (as on the MSX): yellow, cream, white, green, AMOEBA_COLOUR_TIME s each
     * (3 frames at 60 fps). amoebaClock runs while playing.
     */
    private static final Color[] AMOEBA_COLOURS = {new Color(224 / 255f, 224 / 255f, 39 / 255f, 1f),
            new Color(224 / 255f, 224 / 255f, 155 / 255f, 1f), new Color(1f, 1f, 1f, 1f), new Color(115 / 255f, 1f, 115 / 255f, 1f)};
    private static final float AMOEBA_COLOUR_TIME = 0.05f;
    private float amoebaClock;
    /** Other capsule carriers are tinted light red. */
    private static final Color CARRIER_TINT = new Color(1f, 0.45f, 0.45f, 1f);

    /** A tower spits out an amoeba at (x, y) (bottom-left), thrown with (vx, vy). A red one leaves a power capsule. */
    /** Lavinia (both styles): a Gun Ho hops out of a floor hatch (bottom-left x, y). */
    void hatchGunHo(float cx, float y) {
        Enemy e = new Enemy(Enemy.Type.GUN_HO, cx, y, levelIdx);
        if (gunHoR != null) { e.w = gunHoR.getRegionWidth(); e.h = gunHoR.getRegionHeight(); }
        e.x = cx - e.w / 2f;                          // (x given is the middle of the hatch)
        e.gunState = Enemy.GUNHO_HOPPING;
        enemies.add(e);
    }

    /** Heathens (Lavinia): the three pictures (gap at the top / centre / bottom), and the pillars' shapes without the bars. */
    private TextureRegion[] heathenR;
    private PixelMask[] heathenMasks;
    private static final String[] HEATHEN_PICS = {"heathen_top", "heathen_center", "heathen_bottom"};

    private void loadHeathens() {
        for (String n : HEATHEN_PICS) if (!Gdx.files.internal("sprites/" + n + ".png").exists()) return;
        heathenR = new TextureRegion[3];
        heathenMasks = new PixelMask[3];
        for (int k = 0; k < 3; k++) {
            heathenR[k] = a.one(HEATHEN_PICS[k]);
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

    private TextureRegion blockSmallR, blockBigR, rageR, laserGunR;

    /**
     * Lavinia's darkness (as in the MSX recording): from a "dark_zone" on, the stage's lights are off - no background,
     * stars or terrain are drawn (it is all still there and still solid); only the ships, enemies, domes, shots and
     * the item_spark show. A ship touching the spark turns the lights straight back on.
     */
    private boolean[] darkLit = new boolean[0];
    /** The item_sparks still to be picked up ({x, y} bottom-left). */
    private final ArrayList<float[]> sparkItems = new ArrayList<>();
    private TextureRegion sparkR;
    private float sparkT;
    public static final float SPARK_SIZE = 16f;

    /** The lights are off: the camera is inside a dark zone whose spark hasn't been picked up. */
    boolean lightsOut() {
        float camL = scrollX;
        for (int i = 0; i < level.darkZones.size(); i++) {
            float[] z = level.darkZones.get(i);
            if (camL >= z[0] && camL < z[1] && !(i < darkLit.length && darkLit[i])) return true;
        }
        return false;
    }

    /** Darkness after a (re)start at camera x sx: sparks behind the camera count as picked up. */
    private void resetDarkness(float sx) {
        if (darkLit.length != level.darkZones.size()) darkLit = new boolean[level.darkZones.size()];
        sparkItems.clear();
        for (int i = 0; i < darkLit.length; i++) darkLit[i] = false;
        for (float[] s : level.sparks) {
            if (s[0] + SPARK_SIZE < sx) lightUp(s[0]);
            else sparkItems.add(new float[]{s[0], s[1]});
        }
    }

    /** A spark at x picked up: the lights come on in the dark zone it is in (in every zone that has started, if none). */
    private void lightUp(float x) {
        boolean any = false;
        for (int i = 0; i < darkLit.length; i++) {
            float[] z = level.darkZones.get(i);
            if (x >= z[0] && x < z[1]) { darkLit[i] = true; any = true; }
        }
        if (!any) for (int i = 0; i < darkLit.length; i++) if (scrollX >= level.darkZones.get(i)[0]) darkLit[i] = true;
    }

    private void collideSparks(Player p) {
        for (int i = sparkItems.size() - 1; i >= 0; i--) {
            float[] s = sparkItems.get(i);
            if (overlap(p.x + 2f, p.y, 28f, 16f, s[0], s[1], SPARK_SIZE, SPARK_SIZE)) {
                sparkItems.remove(i);
                lightUp(s[0]);
                p.score += 300;
                a.play("pickup", 0.35f);
            }
        }
    }

    private void drawSparks() {
        if (sparkItems.isEmpty()) return;
        if (sparkR == null) {
            String f = Gdx.files.internal("sprites/item_spark.png").exists() ? "item_spark" : "spark";
            sparkR = new TextureRegion(a.tex(f));
        }
        sparkT += Gdx.graphics.getDeltaTime();
        if (((int) (sparkT * 8f)) % 4 == 3) return;   // a little twinkle
        for (float[] s : sparkItems) batch.draw(sparkR, s[0], s[1], SPARK_SIZE, SPARK_SIZE);
    }
    private TextureRegion[] ledZepF, gammaRedF;
    /** Lavinia Led Zeps: seconds to the next one. */
    private float ledZepT = 1f;

    /** Lavinia: while the camera's right edge is in a "ledzep_zone", Led Zeps come at random at either edge, on the floor or the ceiling. */
    private void updateLedZeps(float dt) {
        if (level.ledZepZones.isEmpty()) return;
        float right = scrollX + W;
        float[] zone = null;
        for (float[] z : level.ledZepZones) if (right >= z[0] && right <= z[1]) zone = z;
        if (zone == null) return;
        if ((ledZepT -= dt) > 0f) return;
        ledZepT = MathUtils.random(0.5f, 1.5f) * zone[2];
        boolean fromLeft = MathUtils.random() < 0.3f, ceil = MathUtils.randomBoolean();
        float ex = fromLeft ? scrollX - 16f : scrollX + W, mid = scrollY + H / 2f;
        float fl = level.floorTop(ex + 8f, mid), cb = level.ceilBottom(ex + 8f, mid);
        if (fl <= Level.NO_FLOOR || cb >= Level.NO_CEILING || cb - fl < 48f) return;   // (no corridor there)
        Enemy e = new Enemy(Enemy.Type.LED_ZEP, ex, ceil ? cb - 16f : fl, levelIdx);
        e.zepFloorY = fl; e.zepCeilY = cb - 16f; e.zepCeil = ceil; e.zepRight = fromLeft;
        e.zepT = MathUtils.random(Enemy.LEDZEP_WALK_MIN, Enemy.LEDZEP_WALK_MAX);
        enemies.add(e);
    }
    /** The Lavinia warship's hatch: closed, open, destroyed. */
    private TextureRegion[] hatchF;

    /** The Lavinia warship launches a Rage out of its hatch (bottom-left of the Rage). */
    void launchRage(float x, float y) {
        Enemy e = new Enemy(Enemy.Type.RAGE, x, y, levelIdx);
        enemies.add(e);
    }

    /** A ship's laser hit a Lavinia big block: it bounces straight back to the left, as long as the laser was. */
    private void reflectLaser(Bullet laser, Enemy blk) {
        float len = Math.min(Math.max(16f, laser.w), 64f), h = Math.max(2f, laser.h);
        Bullet r = new Bullet(Bullet.Kind.ENEMY, blk.x - len, laser.y, len, h);
        r.vx = -360f;
        r.reflected = true;
        eBullets.add(r);
    }

    /** A Heathen's bar was shot away. */
    private void breakHeathenBar(Enemy e, int k, Player owner) {
        e.heathenBarHp[k] = 0;
        boom(e.heathenBarX(k) + Enemy.HEATHEN_BAR_W / 2f, e.heathenBarY() + Enemy.HEATHEN_BAR_H / 2f, 0.5f);
        addScore(owner, Enemy.HEATHEN_BAR_SCORE);
        a.play("boom", 0.15f);
    }

    /** An amoeba at (x, y) (bottom-left) thrown with (vx, vy), in capsule group g (or none); null if there are too many. */
    private Enemy makeAmoeba(float x, float y, float vx, float vy, Enemy.Group g) {
        int n = 0;
        for (Enemy e : enemies) if (!e.dead && e.type == Enemy.Type.AMOEBA) n++;
        for (Enemy e : waveOut) if (e.type == Enemy.Type.AMOEBA) n++;
        if (n >= AMOEBA_MAX) return null;
        Enemy e = new Enemy(Enemy.Type.AMOEBA, x, y, levelIdx);
        e.vx = vx; e.vy = vy;
        e.phase = MathUtils.random(0f, MathUtils.PI2);
        e.group = g;
        return e;
    }

    /** Fires at the nearest player (straight left if nobody is flying). Nothing is fired from off screen. */
    void aimedShot(float cx, float cy, float speed, float offset) {
        if (!inView(cx, cy, 0f)) return;
        Player p = target(cx, cy);
        float ang = p == null ? MathUtils.PI : MathUtils.atan2(p.y + 8f - cy, p.x + 16f - cx);
        enemyBullet(cx, cy, ang + offset, speed);
    }

    /**
     * Solid scenery besides the rock (which Level.hits covers): stage 1's towers, Eioneus's gun pods, Lavinia's blocks
     * and the Heathens' pillars. Enemy bullets stop on it.
     */
    private boolean sceneryStops(float x, float y, float w, float h) {
        for (Hazard hz : hazards) if (!hz.dead && hz.type == Hazard.Type.TOWER && hz.touches(x, y, w, h)) return true;
        if (podTouches(x, y, w, h)) return true;
        for (Enemy e : enemies) {
            if (e.dead) continue;
            if ((e.type == Enemy.Type.BLOCK_SMALL || e.type == Enemy.Type.BLOCK_BIG) && overlap(x, y, w, h, e.x, e.y, e.w, e.h)) return true;
            if (e.type == Enemy.Type.HEATHEN && e.heathenBodyTouches(x, y, w, h)) return true;
        }
        return false;
    }

    /** aimedShot from something sitting in the rock (a rock eye): the shot first gets out of the rock. */
    void aimedShotFromRock(float cx, float cy, float speed) {
        int n = eBullets.size();
        aimedShot(cx, cy, speed, 0f);
        if (eBullets.size() > n) eBullets.get(n).terrainGrace = 0.3f;
    }

    /**
     * Gun Ho's shot, flying horizontally at vx out of the gun: gunX = the muzzle (the shot starts in front of it),
     * gunY = the height of the gun barrel (the shot is centred on it).
     */
    void gunHoBeam(float gunX, float gunY, float vx) {
        float bw = gunHoBulletR != null ? gunHoBulletR.getRegionWidth() : Enemy.GUNHO_BEAM_LEN;
        float bh = gunHoBulletR != null ? gunHoBulletR.getRegionHeight() : Enemy.GUNHO_BEAM_H;
        float x = vx < 0f ? gunX - bw : gunX, y = gunY - bh / 2f;
        if (!inView(gunX, gunY, 0f)) return;
        Bullet b = new Bullet(Bullet.Kind.ENEMY, x, y, bw, bh);
        b.vx = vx;
        b.beam = true;
        eBullets.add(b);
    }

    /** Stage 3's last room: hold, the Laser Guards' fight, their retreat, the walls dissolving. */
    private void updateRoom(float dt) {
        if (level.holdT < 0f || roomState == ROOM_DONE) return;
        roomT += dt;
        switch (roomState) {
            case ROOM_NONE:
                if (scrollT >= level.holdT) { roomState = ROOM_FIGHT; roomT = 0f; }
                break;
            case ROOM_FIGHT:
                if (roomT >= level.holdTime) { roomState = ROOM_RETREAT; roomT = 0f; }
                break;
            case ROOM_RETREAT:
                if (roomT >= Enemy.GUARD_RETREAT_TIME + 0.4f) { roomState = ROOM_DEMAT; roomT = 0f; a.play("power", 0.4f); }
                break;
            default: {                                    // ROOM_DEMAT: the walls lose their pixels, faster and faster
                float f = Math.min(1f, roomT / WALL_DEMAT_TIME);
                dissolveWalls(f * f);
                if (f >= 1f) {
                    for (Level.DematWall dw : level.dematWalls) level.setWallGone(dw, true);
                    roomState = ROOM_DONE;
                }
                break;
            }
        }
    }

    /** (Re)loads the dematwall pictures and their collision: whole (gone = false) or already dissolved. */
    private void resetWalls(boolean gone) {
        int n = level.dematWalls.size();
        if (wallPix == null) { wallPix = new Pixmap[n]; wallTex = new Texture[n]; wallOrder = new int[n][]; wallCleared = new int[n]; }
        for (int i = 0; i < n; i++) {
            Level.DematWall dw = level.dematWalls.get(i);
            level.setWallGone(dw, gone);
            if (wallPix[i] != null) wallPix[i].dispose();
            if (wallTex[i] != null) wallTex[i].dispose();
            wallPix[i] = new Pixmap(Gdx.files.internal(dw.image));
            wallPix[i].setBlending(Pixmap.Blending.None);
            wallTex[i] = new Texture(wallPix[i]);
            int pw = wallPix[i].getWidth(), ph = wallPix[i].getHeight(), k = 0;
            int[] order = new int[pw * ph];
            for (int y = 0; y < ph; y++)
                for (int x = 0; x < pw; x++) if ((wallPix[i].getPixel(x, y) & 0xff) != 0) order[k++] = y * pw + x;
            order = java.util.Arrays.copyOf(order, k);
            for (int j = k - 1; j > 0; j--) { int r = MathUtils.random(j); int tmp = order[j]; order[j] = order[r]; order[r] = tmp; }
            wallOrder[i] = order;
            wallCleared[i] = gone ? k : 0;
        }
    }

    /** Clears the walls' pixels (in random order) until the part f (0..1) of them is gone. */
    private void dissolveWalls(float f) {
        for (int i = 0; i < wallPix.length; i++) {
            int[] order = wallOrder[i];
            int want = Math.min(order.length, Math.round(order.length * f));
            if (want <= wallCleared[i]) continue;
            int pw = wallPix[i].getWidth();
            while (wallCleared[i] < want) {
                int q = order[wallCleared[i]++];
                wallPix[i].drawPixel(q % pw, q / pw, 0);
            }
            wallTex[i].draw(wallPix[i], 0, 0);
        }
    }

    /** The dematwalls (not part of the stage art), until they have dissolved completely. */
    private void drawWalls() {
        if (wallTex == null) return;
        for (int i = 0; i < wallTex.length; i++) {
            if (wallCleared[i] >= wallOrder[i].length) continue;
            Level.DematWall dw = level.dematWalls.get(i);
            batch.draw(wallTex[i], dw.x, dw.y, dw.w, dw.h);
        }
    }

    /** Seconds until the Laser Guards' next volley (they all fire together). */
    private float guardVolleyT = Enemy.GUARD_FIRE_MAX;

    /**
     * The Laser Guards fire together: once none of their lasers is on screen any more, the next volley comes after
     * Enemy.GUARD_FIRE_MIN..MAX seconds, or as soon as one of them is lined up with a ship (but not straight after the
     * last one). A volley = every guard on screen stops for Enemy.GUARD_WIND_UP seconds, then fires.
     */
    private void updateGuardVolley(float dt) {
        boolean any = false, lined = false;
        for (Enemy e : enemies) {
            if (e.dead || e.type != Enemy.Type.LASER_GUARD || !inView(e.x + e.w / 2f, e.y + e.h / 2f, 6f)) continue;
            if (e.windT > 0f || guardBeamOnScreen(e)) return;   // a volley is still going on
            any = true;
            lined |= e.lined;
        }
        if (!any || roomRetreating()) return;
        boolean chaser = false;
        for (Enemy e : enemies) if (!e.dead && e.type == Enemy.Type.LASER_GUARD && e.chasing) chaser = true;
        if (!chaser) pickGuardChasers();                 // (the first ones, before the first volley)
        guardVolleyT -= dt;
        if (guardVolleyT > 0f && !(lined && guardVolleyT < Enemy.GUARD_FIRE_MIN - 0.8f)) return;
        guardVolleyT = MathUtils.random(Enemy.GUARD_FIRE_MIN, Enemy.GUARD_FIRE_MAX);
        for (Enemy e : enemies) {
            if (e.dead || e.type != Enemy.Type.LASER_GUARD || !inView(e.x + e.w / 2f, e.y + e.h / 2f, 6f)) continue;
            e.windT = Enemy.GUARD_WIND_UP;               // all stop now, and fire together
        }
        pickGuardChasers();                              // ...and after the volley, new ones chase the ship
    }

    /**
     * Picks which Laser Guards chase the ship until the next volley: one at random, or sometimes
     * (Enemy.GUARD_TWO_CHASERS) two: the side-wall one (horizontal laser) and the ceiling or the floor one, never both.
     */
    private void pickGuardChasers() {
        ArrayList<Enemy> gs = new ArrayList<>(), upDown = new ArrayList<>();
        Enemy wall = null;
        for (Enemy e : enemies) {
            if (e.dead || e.type != Enemy.Type.LASER_GUARD) continue;
            e.chasing = false;
            gs.add(e);
            if (e.guardSide == Enemy.GUARD_WALL) wall = e; else upDown.add(e);
        }
        if (gs.isEmpty()) return;
        if (wall != null && !upDown.isEmpty() && MathUtils.random() < Enemy.GUARD_TWO_CHASERS) {
            wall.chasing = true;
            upDown.get(MathUtils.random(upDown.size() - 1)).chasing = true;
        } else gs.get(MathUtils.random(gs.size() - 1)).chasing = true;
    }

    /** Is one of this Laser Guard's beams still on screen? (It stands still until it is gone.) */
    boolean guardBeamOnScreen(Enemy e) {
        for (Bullet b : eBullets)
            if (!b.dead && b.guard == e && b.x < scrollX + W && b.x + b.w > scrollX && b.y < scrollY + H && b.y + b.h > scrollY) return true;
        return false;
    }

    /** A Laser Guard fires: the beam starts at the gun and grows out of it (see steerGuardBeams). */
    void guardBeam(Enemy e) {
        e.guardMuzzle(muzzleTmp);
        if (!inView(muzzleTmp[0], muzzleTmp[1], 0f)) return;
        Bullet b = new Bullet(Bullet.Kind.ENEMY, muzzleTmp[0], muzzleTmp[1], 1f, 1f);
        b.guard = e;
        b.beamDir = e.guardSide;
        b.beamFront = b.beamTail = e.guardSide == Enemy.GUARD_WALL ? muzzleTmp[0] : muzzleTmp[1];
        b.beamAttached = true;
        b.beamSide = e.guardSide == Enemy.GUARD_WALL ? muzzleTmp[1] : muzzleTmp[0];
        b.terrainGrace = 9999f;                      // (rock is handled by steerGuardBeams)
        eBullets.add(b);
        placeGuardBeam(b);
    }
    private final float[] muzzleTmp = new float[2];

    /**
     * Laser Guard beams behave like the ships' laser: they stay in line with the gun (sweeping along as the guard
     * slides); while attached (Enemy.GUARD_BEAM_TIME) the tail stays at the muzzle and the front grows; then the beam
     * lets go and flies on. The front stops at rock or the screen edge; the beam is gone when its tail gets there too.
     */
    private void steerGuardBeams(float dt) {
        for (Bullet b : eBullets) {
            if (b.dead || b.guard == null) continue;
            Enemy e = b.guard;
            boolean gunThere = !e.dead && !roomRetreating();
            if (gunThere) e.guardMuzzle(muzzleTmp);
            if (b.beamAttached && (!gunThere || b.t >= Enemy.GUARD_BEAM_TIME)) b.beamAttached = false;
            float dir = b.beamDir == Enemy.GUARD_FLOOR ? 1f : -1f;   // up, or down / left
            boolean horiz = b.beamDir == Enemy.GUARD_WALL;
            if (gunThere) b.beamSide = horiz ? muzzleTmp[1] : muzzleTmp[0];
            float step = Enemy.GUARD_BEAM_SPEED * dt;
            if (b.beamAttached) b.beamTail = horiz ? muzzleTmp[0] : muzzleTmp[1];
            else b.beamTail += dir * step;
            float nf = b.beamFront + dir * step, th = omenLaserTex != null ? omenLaserTex[0].getHeight() : 5f;
            boolean blocked = horiz ? level.hits(Math.min(nf, b.beamFront), b.beamSide - th / 2f, step, th) || nf < scrollX
                    : level.hits(b.beamSide - th / 2f, Math.min(nf, b.beamFront), th, step) || nf < scrollY || nf > scrollY + H;
            if (!blocked) b.beamFront = nf;
            if ((b.beamTail - b.beamFront) * dir >= 0f && !b.beamAttached) b.dead = true;   // tail caught up: gone
            placeGuardBeam(b);
        }
    }

    /** The beam's hit box from its front, tail and line. */
    private void placeGuardBeam(Bullet b) {
        float th = omenLaserTex != null ? omenLaserTex[0].getHeight() : 5f;
        float lo = Math.min(b.beamFront, b.beamTail), len = Math.max(1f, Math.abs(b.beamFront - b.beamTail));
        if (b.beamDir == Enemy.GUARD_WALL) { b.x = lo; b.w = len; b.y = b.beamSide - th / 2f; b.h = th; }
        else { b.y = lo; b.h = len; b.x = b.beamSide - th / 2f; b.w = th; }
    }

    /** Omen's laser: starts one pattern wide, centred on (cx, cy), and flies up (or down). */
    void omenLaser(float cx, float cy, boolean up) {
        if (!inView(cx, cy, 0f)) return;
        float bw = omenLaserTex != null ? omenLaserTex[0].getWidth() : 8f, bh = omenLaserTex != null ? omenLaserTex[0].getHeight() : 5f;
        Bullet b = new Bullet(Bullet.Kind.ENEMY, cx - bw / 2f, up ? cy : cy - bh, bw, bh);
        b.vy = up ? Enemy.OMEN_LASER_SPEED : -Enemy.OMEN_LASER_SPEED;
        b.omenLaser = true;
        b.anchorX = cx;
        b.startW = bw;
        b.terrainGrace = 0.15f;   // (it starts on top of the Omen, right against the rock)
        eBullets.add(b);
    }

    /**
     * Omen's laser widens as it flies, both ends moving apart, up to Enemy.OMEN_LASER_MAX_W. Like the Ripple on the
     * vertical stages: an end stops at rock and at the screen edge, and pulls back in a narrower passage.
     */
    private void growOmenLaser(Bullet b, float dt) {
        float step = Enemy.OMEN_LASER_GROW * dt / 2f;
        float mid = b.anchorX, half = b.startW / 2f, maxHalf = Enemy.OMEN_LASER_MAX_W / 2f;
        float right = b.x + b.w, left = b.x;
        float y = b.y + 1f, h = b.h - 2f;
        if (right - 2f > mid + half && level.hits(right - 2f, y, 2f, h)) right = Math.max(mid + half, right - step);
        else if (right + step <= Math.min(scrollX + W, mid + maxHalf) && !level.hits(right, y, step, h)) right += step;
        if (left + 2f < mid - half && level.hits(left, y, 2f, h)) left = Math.min(mid - half, left + step);
        else if (left - step >= Math.max(scrollX, mid - maxHalf) && !level.hits(left - step, y, step, h)) left -= step;
        b.x = left;
        b.w = right - left;
    }

    void enemyBullet(float cx, float cy, float ang, float speed) {
        float bw = ebR.getRegionWidth(), bh = ebR.getRegionHeight();   // size (and hit box) from sprites/ebullet.png
        Bullet b = new Bullet(Bullet.Kind.ENEMY, cx - bw / 2f, cy - bh / 2f, bw, bh);
        b.vx = MathUtils.cos(ang) * speed;
        b.vy = MathUtils.sin(ang) * speed;
        eBullets.add(b);
    }

    /** Op 4 moai ring: aimed at the nearest ship, ring_laser.png (yellow, or white; or red), a 12x12 hit box. */
    void ringShot(float cx, float cy, float speed, boolean white, boolean red) {
        Player p = target(cx, cy);
        float ang = p == null ? -MathUtils.PI / 2f : MathUtils.atan2(p.y + 8f - cy, p.x + 16f - cx);
        Bullet b = new Bullet(Bullet.Kind.ENEMY, cx - 6f, cy - 6f, 12f, 12f);
        b.vx = MathUtils.cos(ang) * speed;
        b.vy = MathUtils.sin(ang) * speed;
        b.ring = red ? 3 : white ? 2 : 1;            // (3: red, leaves a power capsule when shot down)
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
        if (b.vert) {   // vertical stage: the shot's tip is at its top, a beam enters from below
            float vy = b.kind == Bullet.Kind.LASER ? Math.max(b.y, ty) : MathUtils.clamp(b.y + b.h, ty, ty + th);
            boom(MathUtils.clamp(b.x + b.w / 2f, tx, tx + tw), vy, HIT_SPARK_SCALE);
            return;
        }
        float x = b.kind == Bullet.Kind.LASER ? Math.max(b.x, tx)                     // where the beam enters it
                : MathUtils.clamp(b.x + b.w, tx, tx + tw);                         // the shot's tip
        float y = MathUtils.clamp(b.y + b.h / 2f, ty, ty + th);
        boom(x, y, HIT_SPARK_SCALE);
    }

    private static final float HIT_SPARK_SCALE = 0.4f;

    /**
     * Ripple laser: each end stretches away from the line it was fired along (up and down) until the ring is as tall
     * as the playfield, but an end stops where terrain is in the way, and pulls back if the ring flies into a
     * narrower passage. It never gets smaller than the sprite.
     */
    private void growRipple(Bullet b, float dt) {
        if (b.diag) {   // aimed diagonally: the ring just grows (up to RIPPLE_DIAG_MAX), its box kept around its middle
            float cx = b.x + b.w / 2f, cy = b.y + b.h / 2f;
            b.len = Math.min(RIPPLE_DIAG_MAX, b.len + Bullet.RIPPLE_GROW * dt);
            float e = (b.len / 2f + Bullet.RIPPLE_START_W / 2f) * 0.7071f;
            b.x = cx - e; b.y = cy - e; b.w = b.h = 2f * e;
            return;
        }
        if (b.vert) { growRippleV(b, dt); return; }
        float step = Bullet.RIPPLE_GROW * dt / 2f;
        float mid = b.y + b.h / 2f, half = Bullet.RIPPLE_START_H / 2f;
        float top = b.y + b.h, bot = b.y;
        float x = b.x + 1f, w = b.w - 2f;
        // top end
        if (top - 2f > mid + half && level.hits(x, top - 2f, w, 2f)) top = Math.max(mid + half, top - step);   // squeezed
        else if (top + step <= scrollY + H && !level.hits(x, top, w, step)) top += step;                     // room to grow
        else top = Math.min(top, scrollY + H);
        // bottom end
        if (bot + 2f < mid - half && level.hits(x, bot, w, 2f)) bot = Math.min(mid - half, bot + step);
        else if (bot - step >= scrollY && !level.hits(x, bot - step, w, step)) bot -= step;
        else bot = Math.max(bot, scrollY);
        b.y = bot;
        b.h = top - bot;
    }

    /** growRipple on a vertical stage: the ring flies up and its ends stretch left and right. */
    private void growRippleV(Bullet b, float dt) {
        float step = Bullet.RIPPLE_GROW * dt / 2f;
        float mid = b.x + b.w / 2f, half = Bullet.RIPPLE_START_H / 2f;
        float right = b.x + b.w, left = b.x;
        float y = b.y + 1f, h = b.h - 2f;
        // right end
        if (right - 2f > mid + half && level.hits(right - 2f, y, 2f, h)) right = Math.max(mid + half, right - step);
        else if (right + step <= scrollX + W && !level.hits(right, y, step, h)) right += step;
        else right = Math.min(right, scrollX + W);
        // left end
        if (left + 2f < mid - half && level.hits(left, y, 2f, h)) left = Math.min(mid - half, left + step);
        else if (left - step >= scrollX && !level.hits(left - step, y, step, h)) left -= step;
        else left = Math.max(left, scrollX);
        b.x = left;
        b.w = right - left;
    }

    /** Ripple laser picture: the 6 rows at each end of ripple.png stay as drawn, the straight middle rows stretch. */
    private void drawRipple(Bullet b) {
        if (b.diag) {   // aimed diagonally: the whole picture, stretched to len, turned to where it flies
            int th = rippleTex.getHeight(), tw = rippleTex.getWidth();
            float cx = b.x + b.w / 2f, cy = b.y + b.h / 2f;
            batch.draw(rippleTex, cx - tw / 2f, cy - b.len / 2f, tw / 2f, b.len / 2f, tw, b.len, 1f, 1f,
                    MathUtils.atan2(b.vy, b.vx) * MathUtils.radiansToDegrees, 0, 0, tw, th, false, false);
            return;
        }
        if (b.vert) {   // the picture turned a quarter (its front pointing up; flying down: mirrored), stretched sideways
            boolean fl = b.vy < 0f;
            int th = rippleTex.getHeight(), tw = rippleTex.getWidth(), cap = Math.min(6, th / 2);
            float midW = b.w - 2 * cap;
            batch.draw(rippleTex, b.x + b.w, b.y, 0f, 0f, tw, cap, 1f, 1f, 90f, 0, th - cap, tw, cap, fl, false);
            if (midW > 0f) batch.draw(rippleTex, b.x + b.w - cap, b.y, 0f, 0f, tw, midW, 1f, 1f, 90f, 0, cap, tw, th - 2 * cap, fl, false);
            batch.draw(rippleTex, b.x + cap, b.y, 0f, 0f, tw, cap, 1f, 1f, 90f, 0, 0, tw, cap, fl, false);
            return;
        }
        boolean fl = b.vx < 0f;                          // (flying back: mirrored)
        int th = rippleTex.getHeight(), tw = rippleTex.getWidth(), cap = Math.min(6, th / 2);
        float midH = b.h - 2 * cap;
        batch.draw(rippleTex, b.x, b.y, tw, cap, 0, th - cap, tw, cap, fl, false);                     // bottom end
        if (midH > 0f) batch.draw(rippleTex, b.x, b.y + cap, tw, midH, 0, cap, tw, th - 2 * cap, fl, false);   // sides
        batch.draw(rippleTex, b.x, b.y + b.h - cap, tw, cap, 0, 0, tw, cap, fl, false);              // top end
    }

    /** sprites/ripple.png */
    private Texture rippleTex;

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

    /**
     * Stage 2: a player shot against the domes and rock eyes, which sit in the rock (so they have to be checked
     * before the rock stops the shot). Returns true if the bullet is used up.
     */
    private boolean hitWallTarget(Bullet b, float bx, float by, float bw, float bh, boolean laser) {
        for (Hazard h : hazards) {
            if (h.dead || (h.type != Hazard.Type.DOME && h.type != Hazard.Type.EYE)) continue;
            if (!wallTargetTouches(h, bx, by, bw, bh)) continue;
            if (laser) { if (b.hits.contains(h)) continue; b.hits.add(h); } else b.dead = true;
            h.hp -= b.dmg;
            if (h.hp <= 0) destroyWallTarget(h, b.owner);
            else { a.play("hit", 0.15f); hitSpark(b, h.x, h.y, h.w, h.h); }
            if (b.dead) return true;
        }
        return false;
    }

    /**
     * Does the rectangle touch a dome's drawn pixels? Uses the shape of the frame showing (closed / open), mirrored for
     * domes on the right wall, so the see-through part of the picture can be flown and shot through.
     */
    private boolean wallTargetTouches(Hazard h, float rx, float ry, float rw, float rh) {
        if (h.type == Hazard.Type.DOME) return domeTouches(h, rx, ry, rw, rh);
        return overlap(rx, ry, rw, rh, h.x, h.y, h.w, h.h) && rockEyeMask.overlaps(h.x, h.y, rx, ry, rw, rh);   // eye rock
    }

    private boolean domeTouches(Hazard h, float rx, float ry, float rw, float rh) {
        if (!overlap(rx, ry, rw, rh, h.x, h.y, h.w, h.h)) return false;
        PixelMask m = domeMasks[Math.min(h.open ? 1 : 0, domeMasks.length - 1)][h.domeSide];
        return m.overlaps(h.x, h.y, rx, ry, rw, rh);
    }

    /** A dome or an eye rock is shot to pieces. */
    private void destroyWallTarget(Hazard h, Player owner) {
        h.dead = true;
        boolean dome = h.type == Hazard.Type.DOME;
        addScore(owner, dome ? Hazard.DOME_SCORE : Hazard.EYE_SCORE);
        if (dome && h.w > h.h) for (float xx = h.x + 6f; xx < h.x + h.w; xx += 10f) boom(xx, h.y + h.h / 2f, 0.8f);   // (floor / ceiling)
        else if (dome) for (float yy = h.y + 6f; yy < h.y + h.h; yy += 10f) boom(h.x + h.w / 2f, yy, 0.8f);
        else {                                       // the whole rock bursts, leaving rubble that stays like terrain
            boom(h.x + h.w / 2f, h.y + h.h / 2f, 1.3f);
            for (int i = 0; i < 4; i++) boom(h.x + 8f + (i % 2) * 16f, h.y + 8f + (i / 2) * 16f, 0.8f);
            if (rubbleR != null) {
                Hazard r = new Hazard(Hazard.Type.RUBBLE);   // (drawn; the Level.Obstacle makes it solid)
                r.w = rubbleR.getRegionWidth(); r.h = rubbleR.getRegionHeight();
                r.x = h.x + (h.w - r.w) / 2f; r.y = h.y + (h.h - r.h) / 2f;   // centred where the rock was
                pendingHazards.add(r);
                level.obstacles.add(new Level.Obstacle(r.x, r.y, rubbleMask));
            }
        }
        a.play("boom", 0.3f);
    }

    /**
     * Would a laser covering this rectangle run into something it can't go through? The same things that stop it in
     * collide(): terrain, a tower's rock column (not its green tip), arm balls, fangs, every part of a boss, every
     * enemy that can't be destroyed (Enemy.indestructible()) and the asteroid storm's small rocks.
     */
    private boolean laserBlocked(float x, float y, float w, float h) {
        if (w <= 0f || h <= 0f) return false;
        if (level.hits(x, y, w, h)) return true;
        if (bossBlocksLaser(x, y, w, h)) return true;
        if (podTouches(x, y, w, h)) return true;      // Eioneus's gun pods
        for (Enemy e : enemies) {
            if (e.dead || e.withered || e.intangible()) continue;
            if (e.type == Enemy.Type.HEATHEN) {   // Lavinia: the Heathens' pillars (not their gaps)
                if (e.heathenBodyTouches(x, y, w, h)) return true;
                continue;
            }
            // everything that can't be destroyed (Albert, 2026-10-27: Laser Guards, chain balls, blue asteroids,
            // small blocks, laser guns, orbs, Rages, ...) and Lavinia's big blocks
            if ((e.indestructible() || e.type == Enemy.Type.BLOCK_BIG) && overlap(x, y, w, h, e.x, e.y, e.w, e.h)) return true;
        }
        for (Meteor m : meteors) {                // asteroid storm: the small rocks can't be destroyed either
            if (m.dead || m.big || m.red) continue;
            float in = m.inset();
            if (overlap(x, y, w, h, scrollX + m.sx + in, scrollY + m.sy + in, m.w - 2 * in, m.h - 2 * in)) return true;
        }
        for (Hazard hz : hazards) {
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
    private boolean bossBlocksLaser(float x, float y, float w, float h) {
        if (boss == null) return false;
        if (boss.ghost) return !boss.dying && bossShotTouches(x, y, w, h);   // (Op 4: only its weak spots stop a laser)
        if (touchesBoss(x, y, w, h) || boss.ringTouches(x, y, w, h)) return true;
        return boss.hasHatch && overlap(x, y, w, h, boss.x + boss.hatchX, boss.y + boss.hatchY, boss.hatchW, boss.hatchH);
    }

    /** How far past a stopped laser's end collide() looks for the boss or arm ball that stopped it. */
    private static final float LASER_REACH = 2f;

    /**
     * A laser ran into something: find where along the beam (from its tail, on screen: bx, by, bw, bh) it first gets
     * blocked, cut the beam off just before that point and pin it there (Bullet.stuck), so it shrinks into it.
     */
    private void stickLaser(Bullet b, float bx, float by, float bw, float bh) {
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
            // stage 2 domes and rock eyes sit in the rock: they are hit before the rock stops the shot
            if (hitWallTarget(b, bx, by, bw, bh, laser)) { missileBurst(b); continue; }
            // brick maze: every weapon blasts the blocks it touches (lasers keep going)
            float reach = b.kind == Bullet.Kind.MISSILE ? 3f : 0f;
            boolean blasted = (b.vert || b.homing) && b.kind == Bullet.Kind.MISSILE   // (wall-crawling / HOMING: on either side)
                    ? level.breakBricks(bx - reach, by - reach, bw + 2 * reach, bh + 2 * reach) > 0
                    : level.breakBricks(bx, by - reach, bw + reach, bh + 2 * reach) > 0;
            if (blasted) {
                addScore(b.owner, 10);
                boom(b.x + b.w, b.y + b.h / 2f, 0.6f);
                a.play("hit", 0.15f);
                if (!laser) { b.dead = true; continue; }
            }
            if (b.burst) { b.dead = true; missileBurst(b); continue; }   // a vertical / HOMING missile that ran into rock
            if (b.kind == Bullet.Kind.RIPPLE) {
                // a ripple is only stopped by terrain at its core, so a big ring can pass close to walls
                if (b.diag ? level.hits(b.x + b.w / 2f - 3f, b.y + b.h / 2f - 3f, 6f, 6f)
                        : b.vert ? level.hits(b.x + b.w / 2f - 2f, by, 4f, bh) : level.hits(bx, b.y + b.h / 2f - 2f, bw, 4f)) { b.dead = true; continue; }
            } else if (laser && b.seg) {
                // an aimed laser's piece: it still hurts what it touches this frame, and is gone the next (Bullet.segStop)
                if (laserBlocked(bx, by, bw, bh)) b.segStop = true;
            } else if (laser) {
                // a laser that runs into something it can't go through lets go of its gun and shrinks into the impact
                // point (see Bullet.stuck); what is left of it can still hurt what it touches
                if (!b.stuck && laserBlocked(bx, by, bw, bh)) {
                    stickLaser(b, bx, by, bw, bh);
                    if (b.dead) continue;
                    bx = Math.max(b.x, scrollX); by = Math.max(b.y, scrollY);
                    bw = Math.min(b.x + b.w, scrollX + W) - bx; bh = Math.min(b.y + b.h, scrollY + H) - by;
                    if (bw <= 0f || bh <= 0f) continue;
                }
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
                } else if (!laser && h.touches(bx, by, bw, bh)) {   // (a laser was already stopped by laserBlocked)
                    b.dead = true;
                }
                if (b.dead) break;
            }
            if (b.dead) { if (towerHit || b.homing) missileBurst(b); continue; }

            // claw arms: a stage arm's blue ball, or any blue ball of a boss arm, can be damaged; the rest just stop shots
            boolean armHit = false;
            for (Hazard h : hazards) {
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
                else { a.play("hit", 0.15f); hitSpark(b, h.armX[ball] - 8f, h.armY[ball] - 8f, 16f, 16f); }
                if (b.dead) break;
            }
            if (b.dead) { if (armHit || b.homing) missileBurst(b); continue; }

            for (Hazard h : hazards) {
                if (laser) break;                         // (a laser was already stopped by laserBlocked)
                if (h.type == Hazard.Type.FANG && h.touches(bx, by, bw, bh)) {
                    b.dead = true;
                    break;
                }
            }
            if (b.dead) { if (b.homing) missileBurst(b); continue; }   // teeth just stop shots (HOMING bursts on them)

            for (Hazard h : hazards) {
                if (!h.shootable() || h.dead || !overlap(bx, by, bw, bh, h.x, h.y, h.w, h.h)) continue;
                if (laser) { if (b.hits.contains(h)) continue; b.hits.add(h); } else b.dead = true;
                h.hp -= b.dmg;
                if (h.hp <= 0) { h.dead = true; addScore(b.owner, 50); boom(h.x + h.w / 2f, h.y + h.h / 2f, h.w / 20f); a.play("boom", 0.2f); }
                else { a.play("hit", 0.15f); hitSpark(b, h.x, h.y, h.w, h.h); }
                if (b.dead) break;
            }
            if (b.dead) { missileBurst(b); continue; }    // a missile bursts on a Bamda / falling rock it hit

            // asteroid storm: a small rock stops a shot; a big one splits in three (and takes the shot).
            // Eioneus side volcano rocks (red): one hit destroys them (Albert, 2026-10-27)
            for (Meteor m : meteors) {
                if (m.dead) continue;
                float in = m.inset();
                if (!overlap(bx, by, bw, bh, scrollX + m.sx + in, scrollY + m.sy + in, m.w - 2 * in, m.h - 2 * in)) continue;
                if (!m.big && !m.red) {                  // a small storm rock: it stops every shot (Albert, 2026-10-27; a laser: laserBlocked)
                    if (laser) continue;
                    b.dead = true;
                    a.play("hit", 0.1f);
                    hitSpark(b, scrollX + m.sx, scrollY + m.sy, m.w, m.h);
                    break;
                }
                if (laser) { if (b.hits.contains(m)) continue; b.hits.add(m); } else b.dead = true;
                if (m.red) {
                    m.dead = true;
                    addScore(b.owner, VOLCANO_ROCK_SCORE);
                    boom(scrollX + m.sx + m.w / 2f, scrollY + m.sy + m.h / 2f, 0.6f);
                    a.play("boom", 0.2f);
                } else splitMeteor(m);
                if (b.dead) break;
            }
            if (b.dead) { missileBurst(b); continue; }

            // Op 4 moai rings: a shot destroys a ring it hits (and is used up, except a laser)
            boolean ringHit = false;
            for (Bullet r : eBullets) {
                if (r.dead || r.ring == 0 || !overlap(bx, by, bw, bh, r.x, r.y, r.w, r.h)) continue;
                r.dead = true; ringHit = true;
                boom(r.x + r.w / 2f, r.y + r.h / 2f, 0.4f);
                addScore(b.owner, RING_SCORE);
                if (r.ring == 3) {                       // a red ring: a power capsule where it was (Albert)
                    Capsule c = new Capsule();
                    c.x = r.x + r.w / 2f - capW / 2f; c.y = r.y + r.h / 2f - capH / 2f; c.y0 = c.y;
                    caps.add(c);
                }
                if (!laser) { b.dead = true; break; }
            }
            if (b.dead) { if (ringHit && b.kind == Bullet.Kind.MISSILE) boom(b.x, b.y, 0.6f); continue; }

            for (Enemy e : enemies) {
                if (e.dead || e.withered || e.intangible() || !overlap(bx, by, bw, bh, e.x, e.y, e.w, e.h)) continue;   // (shots pass a withered aberrant / a mine taking off)
                if (e.type == Enemy.Type.HEATHEN) {      // only its bars break; the pillar stops shots, the open gap lets them by
                    int k = e.heathenBarAt(bx, by, bw, bh);
                    if (k >= 0) {
                        if (laser) { if (b.hits.contains(e.heathenBarKey[k])) continue; b.hits.add(e.heathenBarKey[k]); } else b.dead = true;
                        e.heathenBarHp[k] -= Math.max(1, Math.round(b.dmg));
                        if (e.heathenBarHp[k] <= 0) breakHeathenBar(e, k, b.owner);
                        else { a.play("hit", 0.15f); hitSpark(b, e.heathenBarX(k), e.heathenBarY(), Enemy.HEATHEN_BAR_W, Enemy.HEATHEN_BAR_H); }
                        if (b.dead) break;
                        continue;
                    }
                    if (!e.heathenBodyTouches(bx, by, bw, bh) || laser) continue;   // (a laser is stopped by laserBlocked)
                    b.dead = true;
                    a.play("hit", 0.1f);
                    hitSpark(b, e.x, e.y, e.w, e.h);
                    break;
                }
                if (e.type == Enemy.Type.MOAI && !e.moaiMouthHit(bx, by, bw, bh)) {   // a moai: only its open mouth can be hurt
                    if (laser) continue;                  // (a laser goes on; it may reach the open mouth later)
                    b.dead = true;
                    a.play("hit", 0.1f);
                    hitSpark(b, e.x, e.y, e.w, e.h);
                    break;
                }
                if (laser && !b.seg && e.type == Enemy.Type.BLOCK_BIG && !b.hits.contains(e)) reflectLaser(b, e);   // (and it still hurts it)
                if (laser) { if (b.hits.contains(e)) continue; b.hits.add(e); } else b.dead = true;
                if (e.indestructible()) {   // can't be destroyed: the shot just stops on it
                    a.play("hit", 0.1f);
                    hitSpark(b, e.x, e.y, e.w, e.h);
                    if (b.dead) break;
                    continue;
                }
                e.hp -= b.dmg;
                if (e.hp <= 0) killEnemy(e, b.owner);
                else { a.play("hit", 0.15f); hitSpark(b, e.x, e.y, e.w, e.h); }
                if (b.dead) break;
            }
            if (b.dead) { if (b.kind == Bullet.Kind.MISSILE) boom(b.x, b.y, 0.6f); continue; }

            if (boss != null && boss.ringTouches(bx, by, bw, bh)) {   // Tetran's rings: can't be hurt, block shots
                if (!laser) { b.dead = true; missileBurst(b); }
                continue;
            }
            // a laser stops just in front of the boss (laserBlocked): look a little past its end
            float hx = bx, hy = by, hw = bw, hh = bh;
            if (laser) { if (b.vert) { hy -= LASER_REACH; hh += 2f * LASER_REACH; } else { hx -= LASER_REACH; hw += 2f * LASER_REACH; } }
            if (barBossShot(b, hx, hy, hw, hh, laser)) continue;   // a bar-wall boss: a bar or orb took it
            if (podShot(b, hx, hy, hw, hh, laser)) continue;       // Eioneus's gun pods: a bar took it, or the pod stopped it
            if (kierkeBossShot(b, hx, hy, hw, hh, laser)) continue;   // Kierke's machine wall: a barrel, a ball, the core or armour
            if (boss != null && !boss.dying && (bossShotTouches(hx, hy, hw, hh)   // (Tetran's rings stop a laser but aren't hurt)
                    || boss.hasHatch && overlap(hx, hy, hw, hh, boss.x + boss.hatchX, boss.y + boss.hatchY, boss.hatchW, boss.hatchH))) {
                // Bosses with an eye only take damage on the eye, and not while it is fully closed;
                // other bosses take damage anywhere. A laser hurts it once per beam.
                // After each hit the boss can't lose HP for Boss.HIT_COOLDOWN; shots that land then are absorbed.
                float ex = boss.x + boss.eyeX, ey = boss.y + boss.eyeY;
                boolean onEye = boss.hasEye && overlap(hx, hy, hw, hh, ex, ey, boss.eyeW, boss.eyeH);
                boolean onCore = boss.coreTouches(hx, hy, hw, hh);
                boolean onHatch = boss.hasHatch && overlap(hx, hy, hw, hh, boss.x + boss.hatchX, boss.y + boss.hatchY, boss.hatchW, boss.hatchH);
                boolean weak = barBossNow() ? false   // bar-wall boss: only its bars / orbs (barBossShot), the body just stops shots
                        : boss.hasHatch ? onHatch || boss.hatchBroken()   // Lavinia: the hatch (open or closed), or anywhere while it is broken
                        : boss.weakW > 0f ? boss.weakTouches(hx, hy, hw, hh)   // (Op 4's sphere: its eye)
                        : boss.hasCore ? onCore : !boss.hasEye || (onEye && !boss.eyeShut());   // core: the only weak spot
                if (!weak || boss.hitCooldown > 0f) {
                    if (!laser) {      // a laser keeps going and may still hurt it a moment later
                        b.dead = true;
                        if (onCore) hitSpark(b, boss.x + boss.coreX, boss.y + boss.coreY, boss.coreW, boss.coreH);
                        else if (onEye) hitSpark(b, ex, ey, boss.eyeW, boss.eyeH); else hitSpark(b, boss.x, boss.y, boss.w, boss.h);
                    }
                    if (b.dead && b.kind == Bullet.Kind.MISSILE) boom(b.x, b.y, 0.6f);
                    continue;
                }
                if (laser) { if (b.hits.contains(boss)) continue; b.hits.add(boss); } else b.dead = true;
                if (boss.hasHatch && !boss.hatchBroken()) {   // Lavinia: the hatch takes the hit (its own health), not the boss
                    float hx0 = boss.x + boss.hatchX, hy0 = boss.y + boss.hatchY;
                    boss.hitCooldown = Boss.HIT_COOLDOWN;
                    if (boss.damageHatch(b.dmg)) { boom(hx0 + boss.hatchW / 2f, hy0 + boss.hatchH / 2f, 0.8f); a.play("boom", 0.3f); }
                    else { a.play("hit", 0.15f); hitSpark(b, hx0, hy0, boss.hatchW, boss.hatchH); }
                    if (b.dead && b.kind == Bullet.Kind.MISSILE) boom(b.x, b.y, 0.6f);
                    continue;
                }
                boss.hp = Math.max(0f, boss.hp - b.dmg);
                boss.hitCooldown = Boss.HIT_COOLDOWN;
                boss.flash = 0.08f;
                a.play("hit", 0.15f);
                if (boss.hasEye) boss.onEyeHit();
                if (boss.hasHatch) boss.onHatchHit();
                if (boss.hp > 0) {
                    if (boss.hasCore) hitSpark(b, boss.x + boss.coreX, boss.y + boss.coreY, boss.coreW, boss.coreH);
                    else if (boss.hasEye) hitSpark(b, ex, ey, boss.eyeW, boss.eyeH); else hitSpark(b, boss.x, boss.y, boss.w, boss.h);
                }
                if (boss.hp <= 0) {
                    boss.dying = true;
                    boss.explodeArms(this);
                    boss.explodeRings(this);
                    eBullets.clear();
                    addScore(b.owner, 5000);
                }
            }
            if (b.dead && b.kind == Bullet.Kind.MISSILE) boom(b.x, b.y, 0.6f);
        }

        // ---- enemy bullets
        for (Bullet b : eBullets) {
            if (b.dead) continue;
            boolean rock = b.guard != null ? false   // (Laser Guard beams: steerGuardBeams stops them at rock)
                    : b.omenLaser   // Omen's laser: only its middle is stopped by rock (the ends just stop growing)
                    ? level.hits(b.anchorX - b.startW / 2f, b.y, b.startW, b.h) : level.hits(b.x, b.y, b.w, b.h);
            if (!rock && !b.omenLaser) b.leftRock = true;   // (Omen's laser keeps its own short grace)
            // (Albert, 2026-10-30: no enemy bullet passes scenery; one fired from inside rock only gets out of that rock)
            if (rock && (b.t >= b.terrainGrace || b.leftRock)) { b.dead = true; continue; }
            if (b.guard == null && !b.omenLaser && sceneryStops(b.x, b.y, b.w, b.h)) { b.dead = true; continue; }
            if (kierkeBossNow() && !boss.dying && kierkeTouches(b.x, b.y, b.w, b.h)) { b.dead = true; continue; }   // (Kierke's wall: no shot gets through)
            for (Hazard h : hazards) {
                if (h.type == Hazard.Type.FANG && h.touches(b.x, b.y, b.w, b.h)) b.dead = true;
            }
            if (b.dead) continue;
            for (Player p : players) {   // SHIELD in front of a ship: blocks the shot (and wears down)
                if (alive(p) && p.shield > 0
                        && overlap(b.x, b.y, b.w, b.h, p.shieldX(), p.shieldY(), Player.shieldW(), Player.shieldH())) {
                    b.dead = true;
                    boom(b.x + b.w / 2f, b.y + b.h / 2f, 0.4f);
                    if (p.invuln <= 0f) shieldHit(p);   // shots that land during the invincibility don't wear it down
                    break;
                }
            }
            if (b.dead) continue;
            for (Player p : players) {
                if (alive(p) && overlap(b.x, b.y, b.w, b.h, p.hitX(), p.hitY(), Player.HIT_W, Player.HIT_H)) {
                    b.dead = true;
                    hurt(p, false);   // got past the SHIELD (from behind / above / below)
                    break;
                }
            }
        }

        // ---- each ship vs everything else
        for (Player p : players) if (alive(p)) collideShip(p);
    }

    /**
     * What the SHIELD (its own 8x16 picture in front of the nose) touches: an Enemy, a Hazard (Bamda / rock or claw
     * arm), the Boss, or null.
     */
    private Object shieldTouches(Player p) {
        float fx = p.shieldX(), fy = p.shieldY(), fw = Player.shieldW(), fh = Player.shieldH();
        for (Enemy e : enemies) {
            if (e.type == Enemy.Type.HEATHEN) { if (!e.dead && e.heathenTouches(fx, fy, fw, fh)) return e; continue; }
            if (!e.dead && !e.intangible() && !e.ground() && overlap(fx, fy, fw, fh, e.x + 2f, e.y + 2f, e.w - 4f, e.h - 4f)) return e;
        }
        for (Hazard h : hazards) {
            if (h.dead) continue;
            if (h.type == Hazard.Type.ARM && h.armTouches(fx, fy, fw, fh)) return h;
            if ((h.type == Hazard.Type.DOME || h.type == Hazard.Type.EYE) && wallTargetTouches(h, fx, fy, fw, fh)) return h;
            if (h.shootable() && overlap(fx, fy, fw, fh, h.x + 2f, h.y + 2f, h.w - 4f, h.h - 4f)) return h;
        }
        if (boss != null && boss.ghost) return null;    // (Op 4: the ships fly over the bosses)
        if (boss != null && boss.ringTouches(fx, fy, fw, fh)) return boss;
        return boss != null && !boss.dying && touchesBoss(fx, fy, fw, fh) ? boss : null;
    }

    /** The F.FIELD bubble or the SHIELD took a hit from this: it takes 1 damage in return (enemies and Bamdas; arms and the boss shrug it off). */
    private void guardDamage(Object o, Player p) {
        if (o instanceof Enemy) {
            Enemy e = (Enemy) o;
            if (e.type == Enemy.Type.ORB && !e.dead) {   // Op 4 orb: can't be shot, but a guard absorbs it
                e.dead = true;
                boom(e.x + e.w / 2f, e.y + e.h / 2f, 0.6f);
                a.play("boom", 0.2f);
                return;
            }
            if (e.dead || e.withered || e.indestructible()) return;   // (can't be destroyed)
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
        boolean ghost = p.ghostT > 0f;   // just (re)entered: passes through terrain and teeth
        if (!ghost && level.hits(hx, hy, hw, hh)) { hurt(p, true); return; }
        // SHIELD: whatever runs into the field itself wears it down (and gives the ship a moment of invincibility)
        if (p.shield > 0 && p.invuln <= 0f && !debugInvincible) {
            Object hit = shieldTouches(p);
            if (hit != null) { shieldHit(p); guardDamage(hit, p); }   // ...and what ran into it takes 1 damage
        }
        for (Enemy e : enemies) {                     // Lavinia: the laser gun points' pulses
            if (e.type == Enemy.Type.LASER_GUN && !e.dead && e.beamW > 0f && overlap(hx, hy, hw, hh, e.beamX, e.beamY, e.beamW, e.beamH)) {
                hurt(p, false);
                if (p.dead) return;
            }
        }
        for (Enemy e : enemies) {                     // Lavinia: the Rages' lasers
            if (e.type == Enemy.Type.RAGE && !e.dead && e.beamH > 0f && overlap(hx, hy, hw, hh, e.beamX, e.beamY, e.beamW, e.beamH)) {
                hurt(p, false);
                if (p.dead) return;
            }
        }
        for (Enemy e : enemies) {                     // Eioneus laser beams
            if (e.type == Enemy.Type.LASER_BATTERY && e.beamOn() && overlap(hx, hy, hw, hh, e.beamX, e.beamY, e.beamW, e.beamH)) {
                hurt(p, false);
                if (p.dead) return;
            }
        }
        for (Enemy e : enemies) {
            if (e.type == Enemy.Type.HEATHEN) {          // its exact shape: the gap lets a ship through once the bars are gone
                if (!e.dead && e.heathenTouches(hx, hy, hw, hh)) { hurt(p, false); if (p.dead) return; }
                continue;
            }
            if (!e.dead && !e.intangible() && !e.ground() && overlap(hx, hy, hw, hh, e.x + 2f, e.y + 2f, e.w - 4f, e.h - 4f)) {
                boolean ffieldTakesIt = p.ffield > 0 && p.invuln <= 0f && !debugInvincible;
                hurt(p, false);
                if (p.dead) return;
                if (ffieldTakesIt) guardDamage(e, p);       // the F.FIELD hits back: 1 damage
            }
        }
        for (Hazard h : hazards) {
            if (h.dead) continue;
            if (!ghost && h.type == Hazard.Type.FANG && h.touches(hx, hy, hw, hh)) { hurt(p, true); return; }
            if (!ghost && h.type == Hazard.Type.TOWER && h.touches(hx, hy, hw, hh)) { hurt(p, true); return; }
            if (!ghost && (h.type == Hazard.Type.DOME || h.type == Hazard.Type.EYE) && wallTargetTouches(h, hx, hy, hw, hh)) { hurt(p, true); return; }
            if (h.type == Hazard.Type.ARM && h.armTouches(hx, hy, hw, hh)) { hurt(p, false); if (p.dead) return; }   // caught
            if (h.shootable() && overlap(hx, hy, hw, hh, h.x + 2f, h.y + 2f, h.w - 4f, h.h - 4f)) {
                h.dead = true;
                boom(h.x + h.w / 2f, h.y + h.h / 2f, h.w / 20f);
                hurt(p, false);
                if (p.dead) return;
            }
        }
        if (boss != null && !boss.ghost && (touchesBoss(hx, hy, hw, hh) || boss.ringTouches(hx, hy, hw, hh))) {
            hurt(p, false);
            if (p.dead) return;
        }
        if (podTouches(hx, hy, hw, hh)) {             // Eioneus's gun pods (the shot-away inside of a column is free)
            hurt(p, false);
            if (p.dead) return;
        }
        collideMeteors(p);
        if (p.dead) return;
        collideSparks(p);
        for (Capsule c : caps) {
            if (!c.dead() && overlap(p.x + 2f, p.y, 28f, 16f, c.x, c.y, capW, capH)) {
                c.x = -99999f;   // consumed (culled next frame)
                p.score += 300;
                if (c.green && p.options < Player.MAX_OPTIONS) p.options++;   // green: one option back, at once
                else p.meter = p.meter >= Player.SLOT_COUNT ? 1 : p.meter + 1;   // only the player who caught it advances
                a.play("pickup", 0.35f);
            }
        }
    }

    /**
     * Asteroid storm rocks against a ship. The SHIELD breaks any rock that runs into it (and wears down, unless the
     * ship is still invincible from its last hit). A rock that reaches the ship hurts it; if the F.FIELD takes the hit
     * the rock breaks, otherwise it just flies on. Rocks pass through a ship that is blinking (invincible).
     */
    private void collideMeteors(Player p) {
        float hx = p.hitX(), hy = p.hitY();
        for (Meteor m : meteors) {
            if (m.dead) continue;
            float in = m.inset(), mx = scrollX + m.sx + in, my = scrollY + m.sy + in, mw = m.w - 2 * in, mh = m.h - 2 * in;
            if (p.shield > 0 && overlap(p.shieldX(), p.shieldY(), Player.shieldW(), Player.shieldH(), mx, my, mw, mh)) {
                shatterMeteor(m);
                if (p.invuln <= 0f && !debugInvincible) shieldHit(p);
                continue;
            }
            if (!overlap(hx, hy, Player.HIT_W, Player.HIT_H, mx, my, mw, mh)) continue;
            if (p.invuln > 0f || debugInvincible) continue;
            boolean ffield = p.ffield > 0;
            hurt(p, false);
            if (ffield) shatterMeteor(m);
            if (p.dead) return;
        }
    }

    private void killEnemy(Enemy e, Player killer) {
        if (e.withered) return;
        if (e.type == Enemy.Type.ABERRANT || e.type == Enemy.Type.SEED) e.withered = true;   // withers / is destroyed and stays
        else e.dead = true;
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

    /** Invincibility after the F.FIELD or the SHIELD takes a hit. */
    private static final float GUARD_IFRAMES = 0.6f;

    /**
     * The SHIELD takes a hit (a blocked shot, or an enemy rammed): it wears down by one and the ship is invincible
     * to enemies and shots for a moment, like with the F.FIELD (terrain still hurts).
     */
    private void shieldHit(Player p) {
        p.shield--;
        p.invuln = Math.max(p.invuln, GUARD_IFRAMES);
        p.shieldHitT = GUARD_IFRAMES;   // the field flickers (the ship doesn't blink)
        a.play("hit", 0.3f);
    }

    private void hurt(Player p, boolean ignoreGuards) {   // (the SHIELD only reacts to what touches the field itself)
        if (debugInvincible) return;                 // debug: invincible
        if (p.dead || state == State.CLEAR || (boss != null && boss.dying)) return;   // nobody dies after the boss
        if (!ignoreGuards) {
            if (p.invuln > 0f) return;
            if (p.ffield > 0) {
                p.ffield--;
                p.invuln = GUARD_IFRAMES;
                p.ffieldHitT = p.invuln;   // the F.FIELD bubble blinks (and fades out if this was its last hit)
                a.play("hit", 0.3f);
                return;
            }
        }
        p.dead = true;
        p.aiming = false;
        boom(p.x + 16f, p.y + 8f, 1.8f);
        a.play("boom", 0.5f);
        p.lives--;
        // its options are left behind as green capsules (one option each); on NORMAL the ship starts over without
        // power-ups, on EASY it keeps everything else
        for (int i = 0; i < p.options; i++) {
            Capsule c = new Capsule();
            c.green = true;
            c.x = optX(p, i) + 6f - capW / 2f; c.y = optY(p, i) + 6f - capH / 2f; c.y0 = c.y;
            caps.add(c);
        }
        if (game.easy) {
            p.options = 0;                            // easy: everything else is kept
        } else {
            int meter = p.meter;                      // (the power meter's highlight stays where it was)
            p.resetPowerups();
            p.meter = meter;
        }
        if (debugLoadout) p.applyDebugLoadout();
        // the game carries on (no rewind): the ship re-enters on the spot after a moment. Only when nobody has
        // ships left is it game over.
        boolean othersPlaying = false;
        for (Player o : players) if (o != null && o != p && !o.out && o.lives >= 0) othersPlaying = true;
        if (p.lives >= 0 || othersPlaying) {
            p.respawnT = RESPAWN_DELAY;   // (out of ships: drops out, can press fire to continue)
        } else {
            setState(State.DEAD);         // nobody left
        }
    }

    // =============================================================== drawing

    /**
     * The star field (parallax 0.6), except over the map's "noStars" range (Level.noStarsLo..Hi along the scrolling),
     * which stays plain black.
     */
    private void drawStars(float camL, float camB) {
        if (Float.isNaN(level.noStarsLo)) { starRect(camL, camB, 0, 0, (int) W, (int) H); return; }
        float cam = level.vertical ? camB : camL, len = level.vertical ? H : W;
        int a = (int) MathUtils.clamp(level.noStarsLo - cam, 0f, len), b = (int) MathUtils.clamp(level.noStarsHi - cam, 0f, len);
        if (level.vertical) { starRect(camL, camB, 0, 0, (int) W, a); starRect(camL, camB, 0, b, (int) W, (int) H - b); }
        else { starRect(camL, camB, 0, 0, a, (int) H); starRect(camL, camB, b, 0, (int) W - b, (int) H); }
    }

    /** The part of the star field over the screen rectangle x0, y0 (from the bottom-left), w x h. */
    private void starRect(float camL, float camB, int x0, int y0, int w, int h) {
        if (w <= 0 || h <= 0) return;
        int u = (int) (scrollX * 0.6f) + x0, v = (int) (-scrollY * 0.6f) + (int) H - y0 - h;   // (texture rows run down)
        batch.draw(starTex, camL + x0, camB + y0, w, h, u, v, w, h, false, false);
    }

    /**
     * The start of a stage, as in the MSX recordings: the operation banner alone on a black screen for INTRO_BANNER s,
     * black for INTRO_BLACK s, then the HUD on a black playfield for INTRO_HUD s; then the stage comes in.
     */
    private static final float INTRO_BANNER = 2f, INTRO_BLACK = 0.6f, INTRO_HUD = 1f;

    /** The intro: the banner (white, MSX-style letters; lines 76 / 92 / 116 px below the top as on the MSX), or the HUD alone. */
    private void drawIntro() {
        batch.setProjectionMatrix(hud.combined);
        batch.begin();
        if (stateT < INTRO_BANNER) {
            float top = HUD_H + H;
            String sub = bossTest >= 0 ? "BOSS TEST" : level.subtitle;
            say(level.bannerTitle, top - 76f - 7f, PixelFont.BOLD, 1, HUD_WHITE);
            if (!sub.isEmpty()) say(sub, top - 92f - 7f, PixelFont.BOLD, 1, bossTest >= 0 ? Color.ORANGE : HUD_WHITE);
            if (!level.planet.isEmpty()) say(level.planet, top - 116f - 7f, PixelFont.BOLD, 1, HUD_WHITE);
        } else if (stateT >= INTRO_BANNER + INTRO_BLACK) {
            drawHud();
        }
        batch.end();
    }

    private void draw() {
        ScreenUtils.clear(0, 0, 0, 1);
        vp.apply();
        if (state == State.INTRO) { drawIntro(); return; }
        // the playfield fills the screen above the HUD strip: world scrollY lands on screen row HUD_H
        cam.position.set(Math.round(scrollX) + SW / 2f, Math.round(scrollY) + H - SH / 2f, 0f);
        cam.update();
        batch.setProjectionMatrix(cam.combined);
        batch.begin();
        float camL = Math.round(scrollX), camB = Math.round(scrollY);
        boolean dark = lightsOut();   // Lavinia: the lights are off (only what moves is seen)
        if (level.background != null && !dark) {   // map property "background"; stage 1 is plain black space
            batch.draw(bgTex, camL, camB, W, H, (int) (scrollX * 0.25f), 0, W, H, false, false);
        }
        batch.setColor(STAR_TINT[levelIdx]);
        if (!dark) drawStars(camL, camB);
        batch.setColor(Color.WHITE);

        if (!dark) {
            drawFangs();      // teeth first, so the ground hides the part that is pulled in
            drawTerrain();
        }
        drawSparks();
        drawPods();           // Eioneus's gun pods
        drawDissolving();     // pictures breaking up (dissolveDeath)
        drawGround();         // bases and destroyed enemies that stay: under everything in play
        drawHazards();
        drawMeteors();
        drawCapsules();
        drawEnemies();
        if (boss != null) drawBoss();
        drawGunLasers();
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

    private void loadWeb() {
        String f = "maps/stage" + (levelIdx + 1) + "_web.png";
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
    private void drawWeb(float camL) {
        if (webX == null || webX.length == 0) return;
        int state = Math.floorMod(Math.round(camL) - WEB_PHASE0, 8);
        int lo = 0, hi = webX.length;                         // first pixel at x >= camL - 1
        while (lo < hi) { int m = (lo + hi) >>> 1; if (webX[m] < camL - 1) lo = m + 1; else hi = m; }
        float top = level.art.isEmpty() ? level.worldH : level.art.get(0).y + level.art.get(0).h;   // the art's top edge
        for (int i = lo; i < webX.length && webX[i] <= camL + W; i++) {
            boolean lit = Math.floorMod(state - webPh[i], 8) < 4;
            if (lit) Color.rgba8888ToColor(webColor, webRgba[i]);
            else if (webArt[i]) webColor.set(0f, 0f, 0f, 1f);
            else continue;
            batch.setColor(webColor);
            batch.draw(pix, webX[i], top - 1f - webY[i], 1f, 1f);
        }
        batch.setColor(Color.WHITE);
    }

    /**
     * The background lights are also painted in the stage art (one frame each); a light's own pixels are taken out of
     * the art where a "back_ground_lights" object is, so only the animation shows. Only pixels that match one of the
     * sprite's frames there are taken out, so a light moved onto other art in Tiled doesn't punch a hole in it.
     */
    private void unbakeBgLights() {
        if (bgLightsF == null || level.bgLights.isEmpty()) return;
        float n = bgLightsSize;
        for (int i = 0; i < artTex.length; i++) {
            Level.Art t = level.art.get(i);
            boolean any = false;
            for (float[] L : level.bgLights)
                if (!(L[0] + n <= t.x || L[0] >= t.x + t.w || L[1] + n <= t.y || L[1] >= t.y + t.h)) { any = true; break; }
            if (any) artTex[i] = a.made(t.image + "#no_bg_lights", () -> unbakeBgLights(t));
        }
    }

    /** A copy of the art picture t with the lights' pixels taken out (t has at least one light on it). */
    private Texture unbakeBgLights(Level.Art t) {
        Pixmap lp = new Pixmap(Gdx.files.internal("sprites/" + bgLightsFile + ".png"));
        Pixmap pm = new Pixmap(Gdx.files.internal(t.image));
        pm.setBlending(Pixmap.Blending.None);
        int n = bgLightsSize;
        for (float[] L : level.bgLights) {
            if (L[0] + n <= t.x || L[0] >= t.x + t.w || L[1] + n <= t.y || L[1] >= t.y + t.h) continue;
            int ax = Math.round(L[0] - t.x), ay = Math.round(t.y + t.h - L[1] - n);   // (art rows run down)
            for (int yy = 0; yy < n; yy++)
                for (int xx = 0; xx < n; xx++) {
                    int c = pm.getPixel(ax + xx, ay + yy);
                    if ((c & 0xff) == 0) continue;
                    for (int f = 0; f < bgLightsF.length; f++) {
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
    private void drawBgLights(float camL, float camB) {
        if (bgLightsF == null) return;
        int step = (int) (bgLightsClock / level.bgLightsFrame), n = bgLightsSize;
        for (float[] L : level.bgLights) {
            if (L[0] > camL + W || L[0] + n < camL || L[1] > camB + H || L[1] + n < camB) continue;
            batch.draw(bgLightsF[Math.floorMod((int) L[2] + step, bgLightsF.length)], L[0], L[1]);
        }
    }

    /** Stage art (image layers), the "terrain" tile layer, and the bricks that are still standing. */
    private void drawTerrain() {
        float camL = Math.round(scrollX), camB = Math.round(scrollY);
        for (int i = 0; i < artTex.length && !terrainGone; i++) {   // (Op 4's end: dissolved)
            Level.Art t = level.art.get(i);
            if (t.x > camL + W || t.x + t.w < camL || t.y > camB + H || t.y + t.h < camB) continue;
            batch.draw(artTex[i], t.x, t.y, t.w, t.h);
        }
        if (!terrainGone) drawWeb(camL);                 // (Kierke: the web flickers as the screen scrolls)
        if (!terrainGone) drawBgLights(camL, camB);      // (Op 4: the background lights, still under every sprite)
        drawWalls();                                     // stage 3: the end room's wall (until it has dissolved)
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
        if (level.bricks != null && brickArtTex != null) {   // each brick shows its own piece of the map's brick picture
            int top = brickArtTex.getHeight(), cs = (int) s;
            for (int r = r0; r <= r1; r++)
                for (int c = c0; c <= c1; c++) {
                    int i = r * level.gCols + c;
                    float alpha = level.bricks[i] ? 1f : level.brickGrow[i];
                    if (alpha <= 0f) continue;
                    if (alpha < 1f) batch.setColor(1f, 1f, 1f, alpha);
                    batch.draw(brickArtTex, c * s, r * s, s, s, c * cs, top - (r + 1) * cs, cs, cs, false, false);
                    if (alpha < 1f) batch.setColor(Color.WHITE);
                }
        } else if (level.bricks != null) {
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
            if (level.vertical && (h.y > scrollY + H + 40f || h.y + h.h < scrollY - 40f)) continue;
            switch (h.type) {
                case DOME: {                                 // facing right on the left wall, mirrored on the right wall
                    TextureRegion f = domeF[h.open ? 1 : 0];
                    float fw = f.getRegionWidth(), fh = f.getRegionHeight();
                    if (h.domeSide == Hazard.DOME_FLOOR)      // turned a quarter to face up (matches PixelMask.rotatedCCW)
                        batch.draw(f, h.x + h.w, h.y, 0f, 0f, fw, fh, 1f, 1f, 90f);
                    else if (h.domeSide == Hazard.DOME_CEIL)  // turned a quarter to face down (PixelMask.rotatedCW)
                        batch.draw(f, h.x, h.y + h.h, 0f, 0f, fw, fh, 1f, 1f, -90f);
                    else batch.draw(f, h.fromCeil ? h.x + h.w : h.x, h.y, h.fromCeil ? -h.w : h.w, h.h);
                    break;
                }
                case EYE:
                    batch.draw(rockEyeR, h.x, h.y, h.w, h.h);
                    break;
                case RUBBLE:
                    batch.draw(rubbleR, h.x, h.y, h.w, h.h);
                    break;
                case ROCK:
                    batch.draw(rockR, h.x, h.y, 16, 16);
                    break;
                case ARM:
                    if (!h.bossArm) drawArm(h);              // boss arms are drawn with the boss, in front of it
                    break;
                case BAMDA:
                    batch.draw(bamdaF[h.frame], h.x, h.y);
                    break;
                default: // volcano: glowing crater (a side volcano is just its rock tip in the map art)
                    if (h.spit != 0) break;
                    float pulse = 0.5f + 0.5f * MathUtils.sin(h.t * 6f);
                    batch.setColor(1f, 0.4f + 0.3f * pulse, 0.1f, 1f);
                    batch.draw(pix, h.x - 6f, h.y, 12, 2);
                    batch.setColor(Color.WHITE);
                    break;
            }
        }
    }

    private void drawMeteors() {
        float cl = Math.round(scrollX), cb = Math.round(scrollY);   // (the camera is on whole pixels)
        for (Meteor m : meteors) {
            if (m.dead) continue;
            float x = cl + Math.round(m.sx), y = cb + Math.round(m.sy);
            if (m.red) batch.draw(rockRedF[m.frame], x, y);
            else if (m.big) batch.draw(rockBigR, x, y);
            else batch.draw(rockSmallF[(int) (m.t * Meteor.SMALL_FPS) % rockSmallF.length], x, y);
        }
    }

    private void drawCapsules() {
        for (Capsule c : caps) {
            TextureRegion[] fr = c.green ? capGreenF : capF;
            TextureRegion f = fr[(int) (c.t * 6f) % fr.length];
            batch.draw(f, c.x, c.y, f.getRegionWidth(), f.getRegionHeight());
        }
    }

    /**
     * The ground (Albert, 2026-10-29): the bases (Eioneus, Op 4), alive or destroyed, and every destroyed enemy that
     * stays (withered roots): drawn before capsules, hazards and every enemy still in play, so nothing hides under them.
     */
    private void drawGround() {
        for (Enemy e : enemies) {                     // Op 4 bases are ground too (the whole picture)
            if (e.dead || e.type != Enemy.Type.SEED || !e.seedOp4 || base6F == null) continue;
            batch.draw(base6F[Math.min(e.seedFrame(), base6F.length - 1)], e.x, e.y, e.w, e.h);
        }
        if (baseF != null)                            // Eioneus bases are ground: under every other enemy
            for (Enemy e : enemies) {
                if (e.dead || e.type != Enemy.Type.SEED || e.seedOp4) continue;
                TextureRegion sr = baseF[Math.min(e.seedFrame(), baseF.length - 1)];   // closed / opening / open / destroyed
                float sw = sr.getRegionWidth(), sh = sr.getRegionHeight();
                batch.draw(sr, e.x + (e.w - sw) / 2f, e.y + (e.h - sh) / 2f, sw, sh);
            }
        for (Enemy e : enemies) {                     // destroyed ones that stay (the bases are drawn above)
            if (e.dead || !e.withered || e.type == Enemy.Type.SEED) continue;
            if (e.type == Enemy.Type.ABERRANT && aberrantF != null) batch.draw(aberrantF[1], e.x, e.y, e.w, e.h);
        }
    }

    private void drawEnemies() {
        for (Enemy e : enemies) {
            if (e.withered) continue;                     // (ground: drawGround)
            TextureRegion r;
            switch (e.type) {
                case LEGACY: {
                    // all frames in a loop, whichever way it is heading (Albert, 2026-10-29): 0 1 2 ... 6 0 1 ...
                    int k = (int) (Math.max(0f, e.t) * Enemy.LEGACY_FPS) % legacyF.length;
                    r = legacyF[k];
                    break;
                }
                case WALKER: r = walkF[(int) (e.t * 6f) % walkF.length]; break;
                case FROST: r = frostR; break;
                case LINK: r = linkF[e.linkSwing > 0f ? e.linkFrame() : (int) (e.t * Enemy.LINK_FPS) % linkF.length]; break;
                case RUGAL: r = rugalF[Math.min(e.rugalFrame(), rugalF.length - 1)]; break;
                case AMOEBA: r = amoebaF[(int) (e.t * Enemy.AMOEBA_FPS) % amoebaF.length]; break;
                case DOME_FLYER: {                       // forward, then backward: 0 1 2 3 2 1 0 ...
                    if (domeFlyerF == null) { r = amoebaF[0]; break; }
                    int n = domeFlyerF.length, cycle = Math.max(1, 2 * n - 2), k = (int) (e.t * Enemy.DOME_FLYER_FPS) % cycle;
                    r = domeFlyerF[k < n ? k : cycle - k];
                    break;
                }
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
            if (e.type == Enemy.Type.LASER_GUARD) {
                if (laserGuardTex != null) drawLaserGuard(e);
                continue;
            }
            if (e.type == Enemy.Type.OMEN) {          // no animation; upside down on the ceiling
                if (omenR != null) batch.draw(omenR, e.x, e.ceiling ? e.y + e.h : e.y, e.w, e.ceiling ? -e.h : e.h);
                continue;
            }
            if (e.type == Enemy.Type.GUN_HO) {        // faces left; mirrored when it turns to a ship behind it
                // frame 1 while it lines up with a ship to fire; after the shot (and before) frame 0
                boolean firing = e.gunState == Enemy.GUNHO_AIMING || e.gunState == Enemy.GUNHO_FIRING;   // (until its last shot)
                TextureRegion gr = firing && gunHoFireR != null ? gunHoFireR : gunHoR;
                if (gr != null) batch.draw(gr, e.faceRight ? e.x + e.w : e.x, e.y, e.faceRight ? -e.w : e.w, e.h);
                continue;
            }
            if (e.type == Enemy.Type.TURRET_WALL) {   // stage 2 wall turret: red = carries a capsule
                TextureRegion f = (red ? wallTurretRedF : wallTurretGreenF)[e.aim == Enemy.WALL_SIDE ? 0 : 1];
                boolean flipX = e.wallRight, flipY = e.aim == Enemy.WALL_UP;   // (the diagonal frame points down)
                batch.draw(f, flipX ? e.x + e.w : e.x, flipY ? e.y + e.h : e.y, flipX ? -e.w : e.w, flipY ? -e.h : e.h);
                continue;
            }
            if (e.type == Enemy.Type.POSSESSED) {       // red or cyan picture, as it is
                TextureRegion pr = e.possRed ? possRedR : possCyanR;
                if (pr != null) batch.draw(pr, e.x, e.y, e.w, e.h);
                continue;
            }
            if (e.type == Enemy.Type.ABERRANT) {       // alive / withered (blue)
                if (aberrantF != null) batch.draw(aberrantF[e.withered ? 1 : 0], e.x, e.y, e.w, e.h);
                continue;
            }
            if (e.type == Enemy.Type.NEST) {           // the 38x22 picture around the 32x17 nest; floor nests upside down
                if (nestR == null) continue;
                float pw = nestR.getRegionWidth(), ph = nestR.getRegionHeight();
                if (e.nestDown) batch.draw(nestR, e.x - 3f, e.y - 3f, pw, ph);
                else batch.draw(nestR, e.x - 3f, e.y - 2f + ph, pw, -ph);
                continue;
            }
            if (e.type == Enemy.Type.SEED) continue;  // (drawn first, under the other enemies)
            if (e.type == Enemy.Type.FLYING_MINE) {
                TextureRegion[] mf = e.mineRed && mineRedF != null ? mineRedF : mineWhiteF;
                if (mf != null) batch.draw(mf[Math.min(e.mineFrame(), mf.length - 1)], e.x, e.y, e.w, e.h);
                continue;
            }
            if (e.type == Enemy.Type.GREMLIN) {        // turned a quarter to stand on its wall (feet to the rock)
                TextureRegion[] gf = e.gremRed ? gremlinRedF : gremlinYellowF;
                if (gf == null) continue;
                TextureRegion fr = gf[Math.min(e.gremlinFrame(), gf.length - 1)];
                float ox = e.w / 2f, oy = e.h / 2f;
                if (e.gremRight) batch.draw(fr, e.x, e.y, ox, oy, e.w, e.h, 1f, 1f, 90f);
                else batch.draw(fr, e.x, e.y, ox, oy, e.w, e.h, 1f, -1f, 90f);   // (the right-wall picture mirrored)
                continue;
            }
            if (e.type == Enemy.Type.BLOCK_SMALL || e.type == Enemy.Type.BLOCK_BIG) {
                TextureRegion br = e.type == Enemy.Type.BLOCK_BIG ? blockBigR : blockSmallR;
                if (br != null) batch.draw(br, e.x, e.y);
                continue;
            }
            if (e.type == Enemy.Type.LED_ZEP) {         // the picture walks left on the floor: mirrored heading right, upside down on the ceiling
                if (ledZepF != null) {
                    TextureRegion f = ledZepF[e.zepState == 0 ? 0 : ledZepF.length - 1];   // frame 0 on the ground, frame 1 in the air (Albert)
                    batch.draw(f, e.zepRight ? e.x + e.w : e.x, e.zepCeil ? e.y + e.h : e.y, e.zepRight ? -e.w : e.w, e.zepCeil ? -e.h : e.h);
                }
                continue;
            }
            if (e.type == Enemy.Type.LASER_GUN) {       // the gun (mirrored pointing left), then its pulse
                if (laserGunR != null) batch.draw(laserGunR, e.lgunLeft ? e.x + e.w : e.x, e.y, e.lgunLeft ? -e.w : e.w, e.h);
                if (e.beamW > 0f && omenLaserTex != null) {
                    Texture lt = omenLaserTex[(int) (e.t * Enemy.OMEN_LASER_FLASH) % omenLaserTex.length];
                    batch.draw(lt, e.beamX, e.beamY, e.beamW, e.beamH, 0, 0, Math.round(e.beamW), lt.getHeight(), e.lgunLeft, false);
                }
                continue;
            }
            if (e.type == Enemy.Type.RAGE) {            // its laser (the Laser Guards' pattern turned upright), then the Rage
                if (e.beamH > 0f && omenLaserTex != null) {
                    Texture lt = omenLaserTex[(int) (e.t * Enemy.OMEN_LASER_FLASH) % omenLaserTex.length];
                    int th = lt.getHeight();
                    batch.draw(lt, e.beamX + e.beamW, e.beamY, 0f, 0f, e.beamH, e.beamW, 1f, 1f, 90f, 0, 0, Math.round(e.beamH), th, false, false);
                }
                if (rageR != null) batch.draw(rageR, e.x, e.y);
                continue;
            }
            if (e.type == Enemy.Type.HEATHEN) {         // the pillar; a bar that was shot away is painted over in black
                if (heathenR == null) continue;
                batch.draw(heathenR[e.heathenKind], e.x, e.y);
                for (int k = 0; k < Enemy.HEATHEN_BARS; k++) {
                    if (e.heathenBarHp[k] > 0) continue;
                    batch.setColor(Color.BLACK);
                    batch.draw(pix, e.heathenBarX(k), e.heathenBarY(), Enemy.HEATHEN_BAR_W, Enemy.HEATHEN_BAR_H);
                    batch.setColor(Color.WHITE);
                }
                continue;
            }
            if (e.type == Enemy.Type.LASER_BATTERY) {  // the battery (flipped like its object), then its beam
                TextureRegion br = e.batBig ? batteryBigR : batterySmallR;
                if (br != null && !e.batWall) {
                    boolean fx = e.batBig ? e.batCeiling : !e.batRight, fy = e.batCeiling || (!e.batBig && !e.batRight);
                    batch.draw(br, fx ? e.x + e.w : e.x, fy ? e.y + e.h : e.y, fx ? -e.w : e.w, fy ? -e.h : e.h);
                }
                if (e.beamOn() && omenLaserTex != null) {   // the Laser Guards' beam: the pattern along it, flashing red / blue
                    Texture lt = omenLaserTex[(int) (e.t * Enemy.OMEN_LASER_FLASH) % omenLaserTex.length];
                    int th = lt.getHeight();
                    if (e.beamW > e.beamH) batch.draw(lt, e.beamX, e.beamY, e.beamW, e.beamH, 0, 0, Math.round(e.beamW), th, !e.batRight, false);
                    else batch.draw(lt, e.beamX + e.beamW, e.beamY, 0f, 0f, e.beamH, e.beamW, 1f, 1f, 90f, 0, 0, Math.round(e.beamH), th, false, false);
                }
                continue;
            }
            if (e.type == Enemy.Type.EXODUS) {
                TextureRegion[] xf = e.exRed ? exodusRedF : exodusCyanF;
                if (xf != null) batch.draw(xf[e.exodusFrame() % xf.length], e.x, e.y, e.w, e.h);
                continue;
            }
            if (e.type == Enemy.Type.ARTILLERY) {      // the picture points up: turned to point away from its wall
                TextureRegion[] af = e.artRed && artilleryRedF != null ? artilleryRedF : artilleryGreenF;
                TextureRegion ar = af == null ? null : af[(int) (Math.max(0f, e.t) / ART_FRAME) % af.length];
                if (ar != null && (e.artFloor || e.artCeil)) batch.draw(ar, e.x, e.artCeil ? e.y + e.h : e.y, e.w, e.artCeil ? -e.h : e.h);   // up / hanging down
                else if (ar != null) batch.draw(ar, e.x, e.y, e.w / 2f, e.h / 2f, e.w, e.h, 1f, 1f, e.artRight ? 90f : -90f);
                continue;
            }
            if (e.type == Enemy.Type.BLUE_ASTEROID) {
                if (blueAsteroidR != null) batch.draw(blueAsteroidR, e.x, e.y, e.w, e.h);
                continue;
            }
            if (e.type == Enemy.Type.MOAI) {           // faces right on the left wall; mirrored on the right wall
                if (moaiF != null) {
                    TextureRegion mr = moaiF[e.moaiOpen && moaiF.length > 1 ? 1 : 0];
                    batch.draw(mr, e.moaiRight ? e.x + e.w : e.x, e.y, e.moaiRight ? -e.w : e.w, e.h);
                }
                continue;
            }
            if (e.type == Enemy.Type.ORB) {
                if (orbR != null) batch.draw(orbR, e.x, e.y, e.w, e.h);
                continue;
            }
            if (e.type == Enemy.Type.BLUE_DOME) {      // stage 2's dome.png: open while it is letting a burst out; mirrored on the right wall
                if (domeF == null) continue;
                TextureRegion dr = domeF[e.wave != null && e.wave.active() ? 1 : 0];
                if (e.domeRight) batch.draw(dr, e.x + e.w, e.y, -e.w, e.h);   // (its back to the wall on the right)
                else batch.draw(dr, e.x, e.y, e.w, e.h);
                continue;
            }
            if (e.type == Enemy.Type.GREEN_FLYER) {
                if (greenF != null) batch.draw(greenF[(int) (Math.max(0f, e.t) / Enemy.GREEN_FRAME) % greenF.length], e.x, e.y, e.w, e.h);
                continue;
            }
            if (e.type == Enemy.Type.OPTION_EATER) {    // white / red, swapped every OEATER_FLASH s
                if (optionEaterF != null) batch.draw(optionEaterF[e.oeFrame() % optionEaterF.length], e.x, e.y, e.w, e.h);
                continue;
            }
            if (e.type == Enemy.Type.GREEN_MINE) {      // 2 frames, swapped every GMINE_FRAME s
                if (greenMineF != null) batch.draw(greenMineF[(int) (Math.max(0f, e.t) / Enemy.GMINE_FRAME) % greenMineF.length], e.x, e.y, e.w, e.h);
                continue;
            }
            if (e.type == Enemy.Type.GAMMA) {
                TextureRegion[] gfr = e.gammaRed && gammaRedF != null ? gammaRedF : gammaF;
                if (gfr != null) batch.draw(gfr[Enemy.GAMMA_ORDER[(int) (Math.max(0f, e.t) / Enemy.GAMMA_FRAME) % Enemy.GAMMA_ORDER.length] % gfr.length], e.x, e.y, e.w, e.h);
                continue;
            }
            if (e.type == Enemy.Type.UG) {             // cycles its 3 frames
                if (ugF != null) batch.draw(ugF[((int) (Math.max(0f, e.t) / Enemy.UG_FRAME) + (int) e.phase) % ugF.length], e.x, e.y, e.w, e.h);
                continue;
            }
            if (e.type == Enemy.Type.CREEPING_DEATH) { // frames back and forth, flickering between its two colours
                if (creepF == null) continue;
                int n = creepF.length, step = (int) (Math.max(0f, e.t) / Enemy.CREEP_FRAME) % (2 * n - 2);
                boolean second = ((int) (Math.max(0f, e.t) / Enemy.CREEP_FLASH) & 1) == 1;
                batch.setColor(e.creepRed ? (second ? CREEP_DARK_GREEN : CREEP_RED) : (second ? CREEP_GREEN : CREEP_CYAN));
                batch.draw(creepF[step < n ? step : 2 * n - 2 - step], e.x, e.y, e.w, e.h);
                batch.setColor(Color.WHITE);
                continue;
            }
            if (e.type == Enemy.Type.CHAIN) {          // grows 4, 6, 8 px, centred on its place
                if (chainR == null) continue;
                int k = e.t < Enemy.CHAIN_GROW / 2f ? 2 : e.t < Enemy.CHAIN_GROW ? 1 : 0;
                TextureRegion cr = chainR[k];
                float cw = cr.getRegionWidth(), ch = cr.getRegionHeight();
                batch.draw(cr, e.x + (e.w - cw) / 2f, e.y + (e.h - ch) / 2f, cw, ch);
                continue;
            }
            if (e.type == Enemy.Type.BATTLEAXE) {
                if (axeR != null) batch.draw(axeR, e.x, e.y, e.w, e.h);
                continue;
            }
            if (e.type == Enemy.Type.WHIPLASH) {
                if (whipR != null) batch.draw(whipR, e.x, e.y, e.w, e.h);
                continue;
            }
            if (r == null) {                           // bio turret: red = carries a capsule; mirrored to aim right
                TextureRegion f = (red ? bioRedF : bioBlueF)[e.aim == 0 ? 1 : 0];
                boolean flipX = e.aim > 0, ceil = e.type == Enemy.Type.TURRET_CEIL;
                batch.draw(f, flipX ? e.x + e.w : e.x, ceil ? e.y + e.h : e.y, flipX ? -e.w : e.w, ceil ? -e.h : e.h);
                continue;
            }
            if (e.type == Enemy.Type.FROST || e.type == Enemy.Type.DOME_FLYER || e.type == Enemy.Type.GREEN_FLYER) red = false;   // keeps its own colours (the group still carries a capsule)
            if (e.type == Enemy.Type.RUGAL && red && rugalRedF != null) {   // red Rugal: its own picture
                r = rugalRedF[Math.min(e.rugalFrame(), rugalRedF.length - 1)];
                red = false;
            }
            if (e.type == Enemy.Type.LEGACY) {          // fly-by: always green; hovering: the MSX colour cycle
                if (e.hover) {
                    int k = (int) (amoebaClock / LEGACY_COLOUR_TIME + e.phase * 3f) % LEGACY_COLOURS.length;
                    batch.setColor(LEGACY_COLOURS[k]);
                } else batch.setColor(LEGACY_GREEN);
                batch.draw(r, e.x, e.y, e.w, e.h);
                batch.setColor(Color.WHITE);
                continue;
            }
            if (red) batch.setColor(e.type == Enemy.Type.AMOEBA ? AMOEBA_RED : CARRIER_TINT);
            else if (e.type == Enemy.Type.AMOEBA) {
                batch.setColor(AMOEBA_COLOURS[(int) (amoebaClock / AMOEBA_COLOUR_TIME) % AMOEBA_COLOURS.length]);
                red = true;                               // (resets the colour after drawing)
            }
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

    /**
     * Laser Guard: pointing down on the ceiling, flipped on the floor, turned a quarter (pointing left) on the right-hand
     * wall. While it retreats only the part still outside its wall is drawn (its gun end).
     */
    private void drawLaserGuard(Enemy e) {
        Texture t = laserGuardTex;
        int sw = t.getWidth(), sh = t.getHeight();
        int vis = Math.max(0, Math.round(sh * (1f - e.retreat)));   // rows still showing, counted from the gun end
        if (vis == 0) return;
        if (e.guardSide == Enemy.GUARD_CEIL)
            batch.draw(t, e.x, e.wallLine - vis, sw, vis, 0, sh - vis, sw, vis, false, false);
        else if (e.guardSide == Enemy.GUARD_FLOOR)
            batch.draw(t, e.x, e.wallLine, sw, vis, 0, sh - vis, sw, vis, false, true);
        else   // turned clockwise: the gun end (bottom rows) on the left, against the wall on the right
            batch.draw(t, e.wallLine - vis, e.y + sw, 0f, 0f, sw, vis, 1f, 1f, -90f, 0, sh - vis, sw, vis, false, false);
    }

    private void drawBoss() {
        if (kierkeBossNow()) { drawKierkeBoss(); return; }
        if (boss.dissolve && boss.dying) return;        // (breaking up: drawDissolving shows it)
        if (boss.appearing) { if (gunsAppear != null) gunsAppear.draw(batch, boss.x, boss.y); return; }
        drawTentacles();                                // under the body
        if (boss.flash > 0f) batch.setColor(1f, 0.55f, 0.55f, 1f);
        if (boss.dying && (int) (boss.dieT * 20f) % 2 == 0) batch.setColor(1f, 1f, 1f, 0.5f);
        batch.draw(curBossR(), boss.x, boss.y, boss.w, boss.h);
        if (boss.hasCore) {                              // in its hole, with the blocks still left
            batch.draw(coreR, boss.x + boss.coreX, boss.y + boss.coreY, boss.coreW, boss.coreH);
            drawCoreBlocks();
        }
        if (boss.hasHatch && hatchF != null) {
            TextureRegion f = hatchF[Math.min(boss.hatchState, hatchF.length - 1)];
            batch.draw(f, boss.x + boss.hatchX, boss.y + boss.hatchY);
        }
        if (boss.hasEye && eyeF != null) {
            TextureRegion f = eyeF[boss.eyeFrame()];
            batch.draw(f, boss.x + boss.eyeX, boss.y + boss.eyeY, f.getRegionWidth(), f.getRegionHeight());
        }
        batch.setColor(Color.WHITE);
        if (boss.arms != null) for (Hazard arm : boss.arms) if (!arm.dead) drawArm(arm);   // over the brain, as in the original
    }

    /** Tetran's tentacles: drawn before (under) the body. */
    private void drawTentacles() {
        if (!boss.tetran || boss.dying) return;
        float rs = Boss.RING_SIZE;
        for (int k = 0; k < Boss.TENTACLES; k++)   // the inner rings, from the centre to the root (under the body)
            for (int j = 0; j < Boss.INNER_RINGS; j++)
                batch.draw(ringR, Math.round(boss.innerX[k][j] - rs / 2f), Math.round(boss.innerY[k][j] - rs / 2f), rs, rs);
        for (int k = 0; k < Boss.TENTACLES; k++)
            for (int i = 0; i < boss.ringCount; i++)
                batch.draw(ringR, Math.round(boss.ringX[k][i] - rs / 2f), Math.round(boss.ringY[k][i] - rs / 2f), rs, rs);
    }

    private void drawBullets() {
        for (Bullet b : pBullets) {
            switch (b.kind) {
                case LASER:
                    if (b.seg) {   // aimed laser piece: a short bit of beam, turned to its direction, centred on its box
                        float lh = laserR.getRegionHeight(), ll = AIM_PIECE_LEN;
                        batch.draw(laserR, b.x + b.w / 2f - ll / 2f, b.y + b.h / 2f - lh / 2f, ll / 2f, lh / 2f, ll, lh, 1f, 1f,
                                MathUtils.atan2(b.vy, b.vx) * MathUtils.radiansToDegrees);
                    } else if (b.vert) batch.draw(laserR, b.x + b.w, b.y, 0f, 0f, b.h, b.w, 1f, 1f, 90f);   // turned to point up
                    else batch.draw(laserR, b.x, b.y, b.w, b.h);
                    break;
                case RIPPLE: drawRipple(b); break;
                case MISSILE:   // tilted 45 degrees nose-up while climbing, nose-down while dropping
                    if (b.chasing) {   // HOMING on its way to a target: turned to where it is going, never upside down
                        float mw = missR.getRegionWidth(), mh = missR.getRegionHeight();
                        batch.draw(missR, b.x + b.w / 2f - mw / 2f, b.y + b.h / 2f - mh / 2f, mw / 2f, mh / 2f, mw, mh, 1f,
                                b.vx < 0f ? -1f : 1f, MathUtils.atan2(b.vy, b.vx) * MathUtils.radiansToDegrees);
                    } else if (b.vert) {   // vertical stage: turned to where it is going (centred on its little hit box)
                        float mw = missR.getRegionWidth(), mh = missR.getRegionHeight();
                        batch.draw(missR, b.x + b.w / 2f - mw / 2f, b.y + b.h / 2f - mh / 2f, mw / 2f, mh / 2f, mw, mh, 1f,
                                b.up ? -1f : 1f, MathUtils.atan2(b.vy, b.vx) * MathUtils.radiansToDegrees);
                    } else if (!b.grounded)  // still flying to the floor / ceiling: nose 45 degrees down / up
                        batch.draw(missR, b.x, b.y, b.w / 2f, b.h / 2f, b.w, b.h, 1f, b.up ? -1f : 1f, b.up ? 45f : -45f);
                    else if (b.up)       // upward missile: drawn upside down; "climbing" a lower ceiling means going down
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
            if (b.reflected) batch.draw(laserR, b.x, b.y, b.w, b.h);   // a ship's laser, bounced back by a big block
            else if (b.kind == Bullet.Kind.LAVA) batch.draw(lavaF[(int) (b.t * 10f) % lavaF.length], b.x, b.y, 8, 8);
            else if (b.guard != null && omenLaserTex != null) {   // Laser Guard: the pattern along the beam, flashing
                Texture t = omenLaserTex[(int) (b.t * Enemy.OMEN_LASER_FLASH) % omenLaserTex.length];
                int th = t.getHeight();
                if (b.beamDir == Enemy.GUARD_WALL) {
                    int srcX = Math.round(b.x - b.beamTail);
                    batch.draw(t, b.x, b.y, b.w, b.h, srcX, 0, Math.round(b.w), th, false, false);
                } else {   // turned a quarter to run up / down
                    int srcX = Math.round(b.y - b.beamTail);
                    batch.draw(t, b.x + th, b.y, 0f, 0f, b.h, th, 1f, 1f, 90f, srcX, 0, Math.round(b.h), th, false, false);
                }
            } else if (b.omenLaser && omenLaserTex != null) {   // the pattern repeated sideways, flashing red / blue
                Texture t = omenLaserTex[(int) (b.t * Enemy.OMEN_LASER_FLASH) % omenLaserTex.length];
                int srcX = Math.round(b.x - (b.anchorX - t.getWidth() / 2f));   // pattern anchored to the middle
                batch.draw(t, b.x, b.y, b.w, b.h, srcX, 0, Math.round(b.w), t.getHeight(), false, b.vy < 0f);
            } else if (b.ring != 0 && ring6R != null) {   // Op 4 moai ring, centred on its 12x12 hit box
                if (b.ring == 3) batch.setColor(((int) (b.t * RING_FLASH) & 1) == 0 ? AMOEBA_RED : RING_RED_DARK);   // red: flashes red / dark red
                else if (((int) (b.t * RING_FLASH) & 1) == 0) batch.setColor(RING_YELLOW);   // flashes yellow / white
                batch.draw(ring6R, b.x - 2f, b.y - 2f, 16f, 16f);
                batch.setColor(Color.WHITE);
            } else if (b.beam && gunHoBulletR != null) {   // Gun Ho's shot: its own picture (mirrored when flying right)
                batch.draw(gunHoBulletR, b.vx > 0f ? b.x + b.w : b.x, b.y, b.vx > 0f ? -b.w : b.w, b.h);
            } else if (b.beam) {   // (no gun ho bullet.png: a drawn laser, red edges, yellow core)
                batch.setColor(GUNHO_BEAM_EDGE);
                batch.draw(pix, b.x, b.y, b.w, b.h);
                batch.setColor(GUNHO_BEAM_CORE);
                batch.draw(pix, b.x + 1f, b.y + 1f, b.w - 2f, Math.max(1f, b.h - 2f));
                batch.setColor(Color.WHITE);
            } else {   // spins in quarter turns around its centre (whole 90 degree steps keep the pixel art crisp)
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
        Color tint = p.index == 1 ? P2_TINT : Color.WHITE;   // (player 2's options; its ship has its own red sprites)
        float px = snapX(p.x), py = snapY(p.y);
        boolean blinkPhase = (int) (p.invuln * 20f) % 2 == 0;
        boolean ffieldHit = p.ffieldHitT > 0f;          // the F.FIELD took the hit: it blinks, the ship stays solid
        boolean blink = p.invuln > 0f && !ffieldHit && p.shieldHitT <= 0f && blinkPhase;   // a guard took it: no blink
        if (!blink) {
            boolean p2 = p.index == 1;
            if (level.vertical) {   // pointing up, banking left / right while moving sideways; centred on the hit box
                TextureRegion[] vf = p2 ? playerVRedF : playerVF;
                TextureRegion f = vf[p.tilt == Player.TILT_LEFT ? 0 : p.tilt == Player.TILT_RIGHT ? 2 : 1];
                batch.draw(f, px + Math.round(16.5f - f.getRegionWidth() / 2f), py + Player.VART_B);
            } else {
                TextureRegion[] hf = p2 ? playerRedF : playerF;
                batch.draw(hf[Math.min(p.tilt, hf.length - 1)], px, py, 32, 16);
            }
        }
        batch.setColor(tint);
        for (int i = 0; i < p.options; i++) {
            // The option's logical box is 12x12 (centre at +6, +6); the 16x10 art is centred on it.
            // On a vertical stage it is turned 90 degrees (10 wide, 16 tall) around that same centre.
            TextureRegion f = optionF[(int) (stateT * 8f + i) % optionF.length];
            float ox = snapX(optX(p, i)) - 2f, oy = snapY(optY(p, i)) + 1f;
            if (level.vertical)
                batch.draw(f, ox, oy, 8f, 5f, f.getRegionWidth(), f.getRegionHeight(), 1f, 1f, 90f);
            else
                batch.draw(f, ox, oy);
        }
        batch.setColor(Color.WHITE);
        if (p.aiming) {   // dotted line showing the aimed direction (DOUBLE's extra shot, or LASER / RIPPLE)
            float ang = p.weapon == Player.W_DOUBLE ? doubleAngle(p) : aimAngle(p.laserDir), cx = px + 16f, cy = py + 8f;
            batch.setColor(1f, 0.9f, 0.3f, 0.9f);
            for (int i = 1; i <= 4; i++) {
                batch.draw(pix, cx + MathUtils.cos(ang) * (10f + i * 7f) - 1f, cy + MathUtils.sin(ang) * (10f + i * 7f) - 1f, 2f, 2f);
            }
            batch.setColor(Color.WHITE);
        }
        if (p.shield > 0 && !blink && !(p.shieldHitT > 0f && (int) (p.shieldHitT * 40f) % 2 == 0)) {   // flickers when hit
            float fx = snapX(p.shieldX()), fy = snapY(p.shieldY());
            TextureRegion[] ff = p.shield <= Player.SHIELD_LOW && shieldRedF != null ? shieldRedF : shieldF;   // red: nearly used up
            if (ff != null && level.vertical)       // turned a quarter: lying across the top of the nose
                batch.draw(ff[(int) (stateT * SHIELD_FPS) % ff.length], fx + Player.shieldW(), fy, 0f, 0f, Player.SHIELD_W, Player.SHIELD_H, 1f, 1f, 90f);
            else if (ff != null) batch.draw(ff[(int) (stateT * SHIELD_FPS) % ff.length], fx, fy, Player.SHIELD_W, Player.SHIELD_H);
            else { batch.setColor(0.4f, 0.9f, 1f, 0.8f); batch.draw(pix, fx + 2f, fy, 4f, Player.SHIELD_H); batch.setColor(Color.WHITE); }
        }
        if ((p.ffield > 0 || ffieldHit) && !(ffieldHit && blinkPhase)) {
            batch.setColor(1f, 1f, 1f, 0.35f + 0.1f * Math.max(p.ffield, 1));
            batch.draw(ffieldR, px - 6f, py - 8f, 40, 32);
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
    /** Tint for HUD pieces that are inactive: the meter of a side nobody plays. */
    private static final Color DIM_TINT = new Color(0.35f, 0.35f, 0.35f, 1f);

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
            case INTRO:                                  // (the banner is drawn by drawIntro)
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
                say("GAME OVER", mid + 14f, PixelFont.BOLD, 1, Color.RED);
                if (stateT > 1f) {
                    for (int i = 0; i < GAMEOVER_MENU.length; i++) {   // the selected entry in yellow, between arrows
                        boolean on = i == gameOverSel;
                        say(on ? "> " + GAMEOVER_MENU[i] + " <" : GAMEOVER_MENU[i], mid - 6f - i * 11f, PixelFont.NORMAL, 1,
                                on ? Color.YELLOW : Color.LIGHT_GRAY);
                    }
                }
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
        int[] slots = p != null ? p.slots : choosingGuard[idx] != 0 ? Player.slotsFor(Math.max(0, choosing[idx]), choosingGuard[idx])
                : Player.slotsFor(Math.max(0, choosing[idx]));
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
        // the box turns red when the highlighted power-up is occupied (that slot can't be taken right now)
        boolean selTaken = inPlay && p.selectedPower() != 0 && owned(p, p.selectedPower());
        batch.draw(selTaken ? hudBoxOffR : hudBoxR, ox + 72, 8);
        String box = "";
        if (choosingType) {
            box = !blinkOn ? "" : choosingGuard[idx] != 0 ? Player.POWER_NAMES[choosingGuard[idx]] : "TYPE " + (choosing[idx] + 1);
        } else if (inPlay) {
            int power = p.selectedPower();
            box = power == 0 ? "" : p.powerLabel(power);
        } else if (state != State.GAMEOVER && state != State.WIN) {
            if (!Controls.hasInput(idx)) box = "PAD " + (idx + 1);
            else if (blinkOn) box = p == null ? "JOIN" : "RETRY";
        }
        text(box, ox + 77, 9, 42, Align.center, PixelFont.SMALL, 1, BOX_TEXT);

        // "1P" + score
        text((idx + 1) + "P", ox + 9, 0, 16, Align.left, PixelFont.BOLD, 1, HUD_WHITE);
        text(String.format("%09d", p == null ? 0 : Math.min(999999999, p.score)), ox + 41, 0, 72, Align.left, PixelFont.BOLD, 1, HUD_WHITE);
    }

    /** Is this power-up already active (taken as far as it goes)? */
    private static boolean owned(Player p, int power) {
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
