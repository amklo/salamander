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
    static final float RESPAWN_DELAY = 1.8f;
    /** Player 2's ship and options are tinted so the two ships can be told apart. */
    static final Color P2_TINT = new Color(1f, 0.7f, 0.4f, 1f);

    enum State { INTRO, PLAY, PAUSE, DEAD, CLEAR, GAMEOVER, WIN }

    /** A power capsule; green = gives back one option lost when a ship was destroyed. */
    static class Capsule {
        float x, y, y0, t;
        boolean green;
        /** Green capsules come back once at the far edge after leaving the screen: true once that has happened. */
        boolean wrapped;
        boolean dead() { return x < -9999f; }
    }
    static class Explosion { float x, y, t, scale; }

    final SalamanderGame game;
    final Assets a;
    final SpriteBatch batch;
    final OrthographicCamera cam = new OrthographicCamera();
    final OrthographicCamera hud = new OrthographicCamera(SW, SH);
    final FitViewport vp;

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
    /** The ships' guns and options (see PlayerWeapons). */
    final PlayerWeapons weapons = new PlayerWeapons(this);
    /** Bullets against targets, and the ships against everything (see Collisions). */
    final Collisions collisions = new Collisions(this);
    /** Draws each frame (see GameRenderer). */
    final GameRenderer renderer = new GameRenderer(this);
    /** Stage 1's brain boss: its eye and core blocks (see BrainBoss). */
    final BrainBoss brain = new BrainBoss(this);
    /** Kierke's machine wall (stage 5, see KierkeBoss). */
    final KierkeBoss kierkeBoss = new KierkeBoss(this);
    /** The bar-wall bosses: Eioneus and Op 4's gun units (see BarBosses). */
    final BarBosses barBosses = new BarBosses(this);
    /** Op 4's end: the brain sphere, then the gun units (see Op4End). */
    final Op4End op4 = new Op4End(this);
    /** Eioneus's boss room: the gun pods and bubbles (see EioneusRoom). */
    final EioneusRoom eioneusRoom = new EioneusRoom(this);
    /** The bosses' falling lasers: Op 4's gun units, Eioneus (see GunLasers). */
    final GunLasers bossGuns = new GunLasers(this);
    /** Lavinia's set pieces: darkness, Heathens, Led Zeps, Rages (see LaviniaStage). */
    final LaviniaStage lavinia = new LaviniaStage(this);
    /** Lavinia's last room: the walls, the Laser Guards, Omen lasers (see LaviniaRoom). */
    final LaviniaRoom laviniaRoom = new LaviniaRoom(this);

    State state = State.INTRO;
    float stateT, checkpointT;
    private int nextSpawn;
    Boss boss;
    boolean bossSpawned;
    /** F1 / H toggles the hitbox overlay (static so it survives stage changes). */
    static boolean debugHitbox;
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
    final ArrayList<Bullet> pBullets = new ArrayList<>();
    final ArrayList<Enemy> enemies = new ArrayList<>();
    /** Kierke chains on their way: started (its start reached Enemy.CHAIN_START_X), time since, balls grown, the last ball. */
    static final class ChainRun { Level.Chain c; boolean started; float t, lastX, lastY; int made; Enemy root; }
    private final ArrayList<ChainRun> chainRuns = new ArrayList<>();
    final ArrayList<Hazard> hazards = new ArrayList<>();

    void addHazard(Hazard h) { hazards.add(h); }
    /** Hazards made while the hazards are being gone through (e.g. rubble during the collisions); added after. */
    final ArrayList<Hazard> pendingHazards = new ArrayList<>();
    final ArrayList<Capsule> caps = new ArrayList<>();
    /** Asteroid storm rocks (screen coordinates, see Meteor), and the pieces of big rocks split during the collisions. */
    final ArrayList<Meteor> meteors = new ArrayList<>();
    private final ArrayList<Meteor> pendingMeteors = new ArrayList<>();
    /** Asteroid storms (level.storms): the next one to start, seconds left of the current one, time to the next rock. */
    private int nextStorm;
    private float stormLeft, stormSpawnT;
    /** The storm going on (its map object's numbers, see Level.storms). */
    private float[] storm;
    /** Rocks spawned in this storm since the last big one (a big one comes about every Meteor.BIG_EVERY small ones). */
    private int stormSmallRun;
    final ArrayList<Explosion> fx = new ArrayList<>();
    private final HashMap<Integer, Enemy.Group> groups = new HashMap<>();

    // art
    final Texture bgTex, starTex;
    final float capW, capH;                 // power capsule size, from sprites/PowerCapsule.png
    /** Legacy (sprites/legacy.png): 7 frames of 16x16, white with a black outline (see Enemy.legacyFrame). */
    final TextureRegion[] legacyF;
    static final Color LEGACY_GREEN = new Color(0x73 / 255f, 1f, 0x73 / 255f, 1f);   // the old fly-by wave
    /** Hovering Legacy colour cycle (MSX): magenta, magenta, blue, magenta, magenta, red; LEGACY_COLOUR_TIME s each. */
    static final Color[] LEGACY_COLOURS;
    static {
        Color m = new Color(224 / 255f, 82 / 255f, 186 / 255f, 1f), b = new Color(82 / 255f, 115 / 255f, 1f, 1f),
                r = new Color(1f, 39 / 255f, 39 / 255f, 1f);
        LEGACY_COLOURS = new Color[]{m, m, b, m, m, r};
    }
    static final float LEGACY_COLOUR_TIME = 2f / 60f;
    /** Player 2's ship (sprites/player_red.png, same layout as player.png). */
    final TextureRegion[] playerRedF;
    final TextureRegion[] playerF, optionF, walkF, capF, boomF, lavaF;
    /** sprites/powercapsule_green.png (same layout as PowerCapsule.png); the normal capsule when it is missing. */
    final TextureRegion[] capGreenF;
    final TextureRegion hudER, hudBoxR, hudBoxOffR;
    final TextureRegion[] bamdaF, armF;
    final TextureRegion[] hudSlotR, hudShipR;
    /** Bio turrets: frame 0 points diagonally left, frame 1 points up. Red ones carry a power capsule. */
    final TextureRegion[] bioRedF, bioBlueF;
    /**
     * Stage 1 and Kierke walkers: "bio walker red/blue.png", 5 frames played forward then backward (no left / right facing).
     * Red ones carry a power capsule. null on the other stages, which keep walker.png.
     */
    final TextureRegion[] bioWalkRedF, bioWalkBlueF;
    static final float BIO_WALK_FPS = 8f;
    /** Enemy bullets spin this many quarter turns per second. */
    static final float EBULLET_SPIN = 12f;
    /** Celtic Frost (sprites/celtic frost.png). */
    final TextureRegion frostR;
    /** Possessed (sprites/possessed_red.png / possessed_cyan.png, 16x16), drawn as they are. */
    TextureRegion possRedR, possCyanR;
    /** Whiplash (sprites/whiplash.png, 15x16), drawn as it is. */
    TextureRegion whipR;
    /** Battleaxe (sprites/battleaxe.png, 16x16), drawn as it is. */
    TextureRegion axeR;
    /** Kierke chain balls (sprites/chain_balls.png, 26x8): the 8x8, 6x6 and 4x4 ball. */
    TextureRegion[] chainR;
    /** Aberrant (sprites/aberrant.png, 2 frames of 16x16: alive, withered); nest (sprites/nest.png, 38x22); nest bug (sprites/nest_bug.png). */
    TextureRegion[] aberrantF;
    TextureRegion nestR;
    /** Creeping Death (sprites/creeping death.png: 4 frames, white; any black pixels made see-through) and its colours. */
    TextureRegion[] creepF;
    static final Color CREEP_CYAN = new Color(82 / 255f, 224 / 255f, 1f, 1f), CREEP_GREEN = new Color(115 / 255f, 1f, 115 / 255f, 1f),
            CREEP_RED = new Color(1f, 39 / 255f, 39 / 255f, 1f), CREEP_DARK_GREEN = new Color(39 / 255f, 155 / 255f, 39 / 255f, 1f);
    /** Ug (sprites/ug.png, 3 frames of 16x16). */
    TextureRegion[] ugF;
    /** Gamma (sprites/gamma.png, 4 frames of 16x16). */
    TextureRegion[] gammaF;
    /** Kierke green mine: sprites/green_mine.png, 2 frames of 16x16. */
    TextureRegion[] greenMineF;
    /** Op 4's option eater (sprites/option_eater.png: white, red; see Enemy.updateOptionEater). */
    TextureRegion[] optionEaterF;
    /** Op 4 (stage 6): Exodus (cyan / red, 3 frames), artillery (green / red), base, dome, blue asteroid, moai, orb, ring. */
    TextureRegion[] exodusCyanF, exodusRedF, base6F;
    /**
     * Background lights (Op 4: stage_6_back_ground_lights.png, 3 frames of 7x7; Lavinia: stage_3_..., 3 frames of 8x8):
     * sprites/stage_<N>_back_ground_lights.png, square frames in a row; each shown level.bgLightsFrame s.
     */
    TextureRegion[] bgLightsF;
    String bgLightsFile;
    int bgLightsSize;
    float bgLightsClock;
    TextureRegion artilleryGreenR, artilleryRedR, blueAsteroidR, orbR, ring6R;
    /** Artillery's frames (artillery_green / _red.png: 2 frames of 16x16 side by side; Albert, 2026-10-30), cycled every ART_FRAME s. */
    TextureRegion[] artilleryGreenF, artilleryRedF;
    static final float ART_FRAME = 0.4f;
    /** Op 4 moai: mouth shut, mouth open (sprites/moai.png, 2 frames of 50x38). */
    TextureRegion[] moaiF;
    /** The green flyers of the Eioneus / Op 4 domes (sprites/green_flyer.png, 4 frames of 16x16; the domes use domeF). */
    /** Eioneus laser batteries (sprites/laser_battery_small.png, laser_battery_big.png; their dark background is see-through). */
    TextureRegion batterySmallR, batteryBigR;
    /** Eioneus gremlins (sprites/gremlin_yellow.png, gremlin_red.png: 3 frames of 16x16, frame 0 = aiming). */
    TextureRegion[] gremlinYellowF, gremlinRedF;
    /** Eioneus bases (sprites/eioneus_base_core.png, the middle of the base: 4 frames of 20x21) and flying mines (sprites/flying_mines.png, flying_mines_red.png: 5 frames of 16x16; black = see-through). */
    TextureRegion[] baseF;
    TextureRegion[] mineWhiteF, mineRedF;
    private static final Color BEAM_RED = new Color(1f, 39 / 255f, 39 / 255f, 1f), BEAM_BLUE = new Color(82 / 255f, 115 / 255f, 1f, 1f);
    TextureRegion[] greenF;
    /** SHIELD (sprites/shield.png): 8x16 frames, looped. null = picture not there yet (drawn as a plain bar). */
    final TextureRegion[] shieldF;
    /** sprites/shield_red.png: the same field once it has Player.SHIELD_LOW hits or fewer left (null = keep shield.png). */
    final TextureRegion[] shieldRedF;
    static final float SHIELD_FPS = 12f;
    /** Missing Link (sprites/missing_link.png): 7 frames of a barrel roll, looped. */
    final TextureRegion[] linkF;
    /** Amoeba (sprites/amoeba.png): 3 frames of 16x16, looped. */
    final TextureRegion[] amoebaF;
    /** Rugal (sprites/rugal.png): level, diving, climbing. */
    final TextureRegion[] rugalF;
    /** sprites/rugal_red.png: a Rugal that carries a power capsule (same frames as rugal.png). */
    final TextureRegion[] rugalRedF;
    /** Stage 2 dome flyer (sprites/green_flyer.png): 4 frames of 16x16, already green. null = not on this stage. */
    final TextureRegion[] domeFlyerF;
    /** At most this many amoebas float around at once (the towers stop spitting until some are gone). */
    private static final int AMOEBA_MAX = 16;
    /**
     * Stage 3 Gun Ho (sprites/Gun Ho.png, faces left): frame 0 (gunHoR), and, when the picture holds two frames side by
     * side (wider than tall; Albert, 2026-10-30), frame 1 (gunHoFireR), shown while it lines up to fire (null: one frame).
     */
    TextureRegion gunHoR, gunHoFireR;
    /** Stage 3 Omen (sprites/Omen.png, floor version; drawn upside down on the ceiling). */
    TextureRegion omenR;
    /** Gun Ho's shot (sprites/Gun Ho bullet.png); its size is the hit box. Missing: a drawn red / yellow beam. */
    TextureRegion gunHoBulletR;
    /** Omen's laser: sprites/enemy laser red.png and blue.png (same pattern), repeated sideways, flashing. */
    Texture[] omenLaserTex;
    /** Stage 3 Laser Guard (sprites/Laser Guard.png, pointing down: the ceiling version). */
    Texture laserGuardTex;

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
    Pixmap[] wallPix;
    Texture[] wallTex;
    int[][] wallOrder;
    int[] wallCleared;

    /** The guards slide back into their walls (and stop firing) once the room's time is up. */
    boolean roomRetreating() { return roomState >= ROOM_RETREAT; }
    /** Gun Ho's laser beam: red edges, yellow core (the colours of its shot in the original). */
    static final Color GUNHO_BEAM_EDGE = new Color(0xf7 / 255f, 0x20 / 255f, 0x20 / 255f, 1f);
    static final Color GUNHO_BEAM_CORE = new Color(0xd8 / 255f, 0xd8 / 255f, 0x20 / 255f, 1f);
    final TextureRegion rockR, shotR, laserR, missR, ebR, ffieldR, pix, bossR;
    // ---- vertical stage (stage 2)
    /**
     * sprites/player_blue_vertical.png: the ship pointing up (vertical stages). Three frames side by side: banking
     * left, level, banking right (a picture with a single frame is used for all three).
     */
    final TextureRegion[] playerVF;
    /** Player 2's vertical ship (sprites/player_red_vertical.png, same layout). */
    final TextureRegion[] playerVRedF;
    /** Wall turrets: [0] = sideways, [1] = diagonal (pointing down), facing right. */
    final TextureRegion[] wallTurretRedF, wallTurretGreenF;
    /** sprites/dome.png: two frames side by side, closed and open (facing right, for the left wall). */
    final TextureRegion[] domeF;
    /** Exact shapes of the dome frames: [frame: closed, open][0 = as drawn (left wall), 1 = mirrored (right wall)]. */
    PixelMask[][] domeMasks;
    final TextureRegion rockEyeR, ringR;
    /** Asteroid storm rocks: sprites/rock_small.png (4 frames of 15x15) and sprites/rock_big.png (null = no storms). */
    final TextureRegion[] rockSmallF;
    final TextureRegion rockBigR;
    /** Side volcanoes' red rocks: sprites/rock_small_red.png (the 4 rock_small pictures in the stage's rock red). */
    final TextureRegion[] rockRedF;
    /** Exact shape of the eye rock (sprites/rock_eye.png, its dark background made see-through). */
    PixelMask rockEyeMask;
    /** sprites/rubbles.png: what is left of a destroyed eye rock (solid like terrain), and its exact shape. */
    final TextureRegion rubbleR;
    PixelMask rubbleMask;
    /** The map's "brickArt" picture (null = bricks use brick_pattern.png). */
    Texture brickArtTex;
    // map stages: pre-rendered terrain strips, brick maze picture, bamda sprites
    Texture[] artTex;
    Texture brickTex;
    private final java.util.HashMap<String, PixelMask> masks = new java.util.HashMap<>();
    PixelMask bossMask;
    static final Color[] STAR_TINT = {new Color(0.7f, 0.8f, 1f, 1f), new Color(0.9f, 0.9f, 1f, 1f),
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
        capF = Sprites.scanFrames(a, "sprites/PowerCapsule.png");   // frames separated by empty columns
        capGreenF = Gdx.files.internal("sprites/powercapsule_green.png").exists() ? Sprites.scanFrames(a, "sprites/powercapsule_green.png") : capF;
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
        lavinia.loadHeathens();
        lavinia.blockSmallR = Gdx.files.internal("sprites/lavinia_block_small.png").exists() ? a.one("lavinia_block_small") : null;
        lavinia.blockBigR = Gdx.files.internal("sprites/lavinia_block_big.png").exists() ? a.one("lavinia_block_big") : null;
        lavinia.rageR = Gdx.files.internal("sprites/rage.png").exists() ? a.one("rage") : null;
        lavinia.ledZepF = Gdx.files.internal("sprites/led_zep.png").exists() ? a.strip("led_zep", 16) : null;
        lavinia.laserGunR = Gdx.files.internal("sprites/laser_gun_point.png").exists() ? a.one("laser_gun_point") : null;
        lavinia.gammaRedF = Gdx.files.internal("sprites/gamma_red.png").exists() ? a.strip("gamma_red", 16) : null;
        lavinia.hatchF = levelIdx == 2 && Gdx.files.internal("sprites/lavinia_boss_hatch.png").exists() ? a.strip("lavinia_boss_hatch", 16) : null;
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
        artilleryGreenR = Sprites.keyedDark("sprites/artillery_green.png");   // artillery: Op 4 and Lavinia (any stage that has it)
        artilleryRedR = Sprites.keyedDark("sprites/artillery_red.png");
        artilleryGreenF = Sprites.squareFrames(artilleryGreenR);
        artilleryRedF = Sprites.squareFrames(artilleryRedR);
        // blue rock clumps: Op 4 and stage 2 (~120-130 s in the stage 2 recording)
        blueAsteroidR = (levelIdx == 1 || levelIdx == 5) && Gdx.files.internal("sprites/blue_asteroid.png").exists() ? a.one("blue_asteroid") : null;
        if (levelIdx == 5) {                          // Op 4 (Albert's sprites; black is see-through)
            exodusCyanF = Sprites.keyedFrames("sprites/exodus_cyan.png", 16);
            exodusRedF = Sprites.keyedFrames("sprites/exodus_red.png", 16);
            base6F = Gdx.files.internal("sprites/stage_6_back_ground_base.png").exists() ? a.strip("stage_6_back_ground_base", 22) : null;
            moaiF = Gdx.files.internal("sprites/moai.png").exists() ? a.strip("moai", 50) : null;
            orbR = Gdx.files.internal("sprites/orb.png").exists() ? a.one("orb") : null;
            ring6R = Sprites.keyedDark("sprites/ring_laser.png");
            optionEaterF = Gdx.files.internal("sprites/option_eater.png").exists() ? a.strip("option_eater", 16) : null;
        }
        batterySmallR = Sprites.keyedDark("sprites/laser_battery_small.png");
        // the middle of the Eioneus base, 4 frames of 20x21 (closed, opening, open, destroyed); the rest of the base is map art
        baseF = Gdx.files.internal("sprites/eioneus_base_core.png").exists() ? a.strip("eioneus_base_core", 20) : null;
        mineWhiteF = Sprites.keyedFrames("sprites/flying_mines.png", 16);
        mineRedF = Sprites.keyedFrames("sprites/flying_mines_red.png", 16);
        gremlinYellowF = Gdx.files.internal("sprites/gremlin_yellow.png").exists() ? a.strip("gremlin_yellow", 16) : null;
        gremlinRedF = Gdx.files.internal("sprites/gremlin_red.png").exists() ? a.strip("gremlin_red", 16) : null;
        batteryBigR = Sprites.keyedDark("sprites/laser_battery_big.png");
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
        amoebaF = Sprites.framesOuterBlackKeyed("sprites/amoeba.png", 16);
        rugalF = a.strip("rugal", 16);
        rugalRedF = Gdx.files.internal("sprites/rugal_red.png").exists() ? a.strip("rugal_red", 16) : null;
        rippleTex = a.texFile("sprites/ripple.png");   // drawn as it is (its own transparency)
        ffieldR = a.one("ffield");
        pix = a.one("pixel");
        bossR = loadBossPicture();                     // (also sets bossMask)
        barBosses.loadBarBoss();
        eioneusRoom.loadPods();
        if (levelIdx == 3 && orbR == null && Gdx.files.internal("sprites/orb.png").exists()) orbR = a.one("orb");   // (the pod room's bubbles)
        kierkeBoss.loadKierkeBoss();
        boolean v = level.vertical;   // pictures only the vertical stage uses
        playerVF = v ? Sprites.verticalShipFrames(Sprites.keyedBackground("sprites/player_blue_vertical.png")) : null;
        playerVRedF = v && Gdx.files.internal("sprites/player_red_vertical.png").exists()
                ? Sprites.verticalShipFrames(Sprites.keyedBackground("sprites/player_red_vertical.png")) : playerVF;   // player 2
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
        rockEyeR = v ? Sprites.keyedBackground("sprites/rock_eye.png", eyeMask) : null;
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
        renderer.unbakeBgLights();
        renderer.loadWeb();
        brickTex = a.texFile("maps/brick_pattern.png");
        brickTex.setWrap(Texture.TextureWrap.Repeat, Texture.TextureWrap.Repeat);
        for (Level.Tooth t : level.teeth) {
            if (!masks.containsKey(t.image)) masks.put(t.image, PixelMask.load(t.image));
        }
        for (Level.Tower t : level.towers) {
            if (!masks.containsKey(t.image)) masks.put(t.image, PixelMask.load(t.image));
            if (t.flip && !masks.containsKey(t.image + "#flipV")) masks.put(t.image + "#flipV", masks.get(t.image).flippedV());
        }
        if (levelIdx == 0) brain.loadBossEye("sprites/boss" + levelIdx + ".png");   // (only stage 1's brain has an eye)
        brain.loadBossCore();

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

    int playersInGame() {
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
    final int[] choosing = {-1, -1};
    /** Mid-game joiners then pick their guard: 0 = not yet (still on the ship type), else Player.SHIELD / FFIELD. */
    final int[] choosingGuard = {0, 0};

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
        lavinia.resetDarkness(sx);
        pBullets.clear(); eBullets.clear(); enemies.clear(); caps.clear(); groups.clear();
        weapons.reset();
        level.obstacles.clear();                      // rubble goes (the eye rocks come back)
        pendingHazards.clear();
        meteors.clear(); pendingMeteors.clear();
        stormLeft = 0f; stormSmallRun = 0;
        nextStorm = 0;                                // a restart further on: storms the screen already passed are over
        while (t > 0f && nextStorm < level.storms.size() && level.storms.get(nextStorm)[0] <= fr + viewLen()) nextStorm++;
        boss = null;
        bossSpawned = false;
        bossPart = 0;
        op4.op4State = op4.OP4_NONE; op4.op4T = 0f; op4.bossDissolving = false;   // (Op 4's end: the terrain back, if it had gone)
        if (op4.terrainGone) { op4.terrainGone = false; level.restoreGrid(); }
        for (Dissolving d : dissolving) d.d.dispose();
        dissolving.clear();
        bossGuns.gunLasers.clear();
        if (op4.gunsAppear != null) { op4.gunsAppear.dispose(); op4.gunsAppear = null; }
        eioneusRoom.resetPods();                                  // (Eioneus: the pod room starts over)
        buildHazards();
        // the last room of stage 3: past it (a restart beyond the hold), its walls are already gone
        roomT = 0f;
        roomState = level.holdT >= 0f && t > level.holdT ? ROOM_DONE : ROOM_NONE;
        laviniaRoom.resetWalls(roomState == ROOM_DONE);
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
                if (stateT > GameRenderer.INTRO_BANNER + GameRenderer.INTRO_BLACK + GameRenderer.INTRO_HUD) setState(State.PLAY);
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
        renderer.draw();
    }

    private void submitScores() {
        for (Player p : players) if (p != null) game.submitScore(p.score);
    }

    /** Game over menu entries (Albert, 2026-10-27); gameOverSel = the one under the cursor. */
    static final String[] GAMEOVER_MENU = {"CONTINUE", "END"};
    int gameOverSel;

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
    static final String[] PAUSE_MENU = {"CONTINUE", "TITLE SCREEN"};
    int pauseSel;

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

    void setState(State s) {
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
    static final class Dissolving { Dissolver d; float x, y, t, time; }
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

    void drawDissolving() {
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
        if (!clearing) laviniaRoom.updateRoom(dt);
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
        if (op4.op4End()) op4.updateOp4End(dt);              // (Op 4: the terrain dissolves, then its two bosses come)
        else if (eioneusRoom.eioEnd()) eioneusRoom.updateEioneusEnd(dt);     // (Eioneus: the gun pods, the terrain dissolves, then the boss)
        else if (scrollT >= level.stopT && !bossSpawned) spawnBoss();
        // a boss that sits in the map (map object "boss" with "fixed" on) wakes up as soon as it comes on screen
        if (op4.op4End()) {
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
            if (!p.dead) weapons.updateOptions(p, dt);
        }

        // --- world objects
        for (Bullet b : pBullets) {
            if (b.homing) weapons.aimHoming(b);
            boolean fresh = b.fresh;
            b.update(dt, level);
            if (!fresh && b.ridesScreen()) {          // moves relative to the screen, like the ships
                if (b.kind == Bullet.Kind.LASER && b.attached) { if (b.vert) b.h += dSy; else b.w += dS; }   // (only its front)
                else { b.x += dS; b.y += dSy; }
            }
            if (b.kind == Bullet.Kind.RIPPLE) growRipple(b, dt);
            if (b.kind == Bullet.Kind.LASER) weapons.detachLaser(b);
            // a player shot is gone once it is completely off screen (so the on-screen limits really mean on screen)
            if (b.x > scrollX + W || b.x + b.w < scrollX || b.y > scrollY + H || b.y + b.h < scrollY) b.dead = true;
        }
        laviniaRoom.steerGuardBeams(dt);
        for (Bullet b : eBullets) {
            boolean fresh = b.fresh;
            b.update(dt, level);
            if (!fresh && b.ridesScreen()) { b.x += dS; b.y += dSy; }   // moves relative to the screen, as on the MSX
            if (b.omenLaser) laviniaRoom.growOmenLaser(b, dt);
            if (b.x > scrollX + W + 80f || b.x < scrollX - 60f || b.y < scrollY - 30f || b.y > scrollY + H + 60f) b.dead = true;
        }
        laviniaRoom.updateGuardVolley(dt);
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
        if (boss != null) { boss.update(this, dt); brain.updateCoreBlocks(); }
        if (boss != null && boss.dying && boss.dissolve && !op4.bossDissolving) op4.bossDissolveStart();
        bossGuns.updateGunLasers(dt, clearing);
        lavinia.updateLedZeps(dt);
        regrowBricks(dt);
        updateFx(dt);

        collisions.collide();

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
    static final int VOLCANO_ROCK_SCORE = 50;

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
    void splitMeteor(Meteor m) {
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
    void shatterMeteor(Meteor m) {
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

        weapons.recordTrail(p, dt);
        weapons.steerLasers(p);
        weapons.emitAimBeams(p, dt);
        boolean fire = Controls.held(id, Action.FIRE);
        if (fire && p.fireCd <= 0f && weapons.shoot(p)) p.fireCd = PlayerWeapons.FIRE_INTERVAL;
        if (fire && p.missile && p.missCd <= 0f && weapons.launchMissiles(p)) p.missCd = PlayerWeapons.FIRE_INTERVAL;
        if (Controls.pressed(id, Action.POWER)) activateMeter(p);
        if (Controls.pressed(id, Action.OPT_ORBIT)) weapons.toggleOptMode(p, Player.OPT_ORBIT);
        if (Controls.pressed(id, Action.OPT_V)) weapons.toggleOptMode(p, Player.OPT_V);
        if (Controls.pressed(id, Action.OPT_FREEZE)) weapons.toggleOptMode(p, Player.OPT_FREEZE);
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
        weapons.recordTrail(p, dt);
        weapons.steerLasers(p);
    }

    float optX(Player p, int i) { return scrollX + (p.optReady && i < p.optKnown ? p.optSX[i] : p.trailX(10 * (i + 1)) + 10f); }
    float optY(Player p, int i) { return pathY + (p.optReady && i < p.optKnown ? p.optSY[i] : p.trailY(10 * (i + 1)) + 2f); }


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
            if (sd.seedT < 0f && !sd.seedOpen) { sd.seedT = Enemy.SEED_OPENING; sd.seedOpening = true; }   // it comes on: opens (once)
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
                if (lavinia.heathenMasks != null) e.heathenMask = lavinia.heathenMasks[e.heathenKind];
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
        TextureRegion[] f = Sprites.scanFrames(a, "sprites/bamda.png");
        Hazard.bamdaW = new float[f.length];
        Hazard.bamdaH = new float[f.length];
        for (int i = 0; i < f.length; i++) {
            Hazard.bamdaW[i] = f[i].getRegionWidth();
            Hazard.bamdaH[i] = f[i].getRegionHeight();
        }
        return f;
    }

    /** Does the rectangle touch the boss: its sprite's solid pixels, or its eye (drawn over a see-through gap)? */
    boolean touchesBoss(float x, float y, float w, float h) {
        if (boss.appearing) return false;                // (Op 4's gun units coming together: not solid yet)
        if (kierkeBoss.kierkeBossNow()) return kierkeBoss.kierkeTouches(x, y, w, h);   // (its column slides out of the boss box)
        if (!overlap(x, y, w, h, boss.x, boss.y, boss.w, boss.h)) return false;
        if (boss.hasEye && overlap(x, y, w, h, boss.x + boss.eyeX, boss.y + boss.eyeY, boss.eyeW, boss.eyeH)) return true;
        if (boss.coreTouches(x, y, w, h)) return true;
        return boss.mask == null || boss.mask.overlaps(boss.x, boss.y, x, y, w, h);
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
    TextureRegion[] bossPartR;
    private PixelMask[] bossPartMask;
    int bossPart;
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
    TextureRegion curBossR() {
        if (barBosses.barBossNow()) return barBosses.barR;                   // (a bar-wall boss: the picture with the parts shot away)
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

    void spawnBoss() {
        bossSpawned = true;
        op4.bossDissolving = false;
        int[] hp = {70, 100, 120, 140, 160, 180, 220};
        TextureRegion br = curBossR();
        boss = new Boss(levelIdx, scrollX + W + 20f, hp[levelIdx], br.getRegionWidth(), br.getRegionHeight());
        boss.mask = bossPartMask != null ? bossPartMask[Math.min(bossPart, bossPartMask.length - 1)] : bossMask;
        brain.coreBlocksShown = brain.CORE_BLOCKS;
        barBosses.resetBarBoss();                               // (bar-wall bosses: all bars, orbs and the core shield back)
        if (level.bossFixed) {                        // sits where the map has it (stages 5-7 for now)
            if (bossPartR != null) { bossPartBox(); boss.x = partBox[0]; boss.y = partBox[1]; }
            else { boss.x = level.bossX; boss.y = level.bossY; }
            boss.fixed = true;
            if (levelIdx == 4 && kierkeBoss.kWallBase != null) {     // Kierke's machine wall: top-aligned in the map's boss box
                boss.y = level.bossY + level.bossH - boss.h;
                boss.initKierke(level.bossY + level.bossH);
                kierkeBoss.kResetWall();                             // (a whole wall, e.g. after a retry)
                kierkeBoss.kCompose();
            }
            boss.entering = false;
            if (op4.op4End()) op4.op4Boss();                  // Op 4: the brain sphere rises in, the gun units appear
        } else if (level.vertical) {                  // comes down from the top, in the middle
            boss.x = scrollX + W / 2f - boss.w / 2f;
            boss.y = scrollY + H + (levelIdx == 1 ? 70f : 10f);   // (Tetran's tentacles reach 60+ px around it)
            if (levelIdx == 1) boss.initTetran();
            if (eioneusRoom.eioEnd()) boss.initEioneus(this);     // (Eioneus: its own movement and lasers, from the recording)
        }
        if (levelIdx == 0) {
            // the brain's two arms, as attached in the original map (sprite 100x100, y up from its bottom):
            // {anchor x, anchor y, direction x, direction y, ball spacing}
            boss.armSpec = new float[][]{
                    {29f, 96f, -16f, -16f, 22.6f},   // from the top left, down at 45 degrees
                    {37f, 32f, -16f, -8f, 17.9f},    // from under the eye, down and to the left
            };
        }
        if (brain.coreR != null) {                          // only the core takes damage
            boss.hasCore = true;
            boss.coreMask = brain.coreMask;
            boss.coreX = brain.coreOX; boss.coreY = brain.coreOY;
            boss.coreW = brain.coreR.getRegionWidth(); boss.coreH = brain.coreR.getRegionHeight();
        }
        if (lavinia.hatchF != null && !level.bossFixed && !level.vertical) {   // Lavinia warship: in from the left, the hatch on its nose
            boss.hasHatch = true;
            boss.hatchW = lavinia.hatchF[0].getRegionWidth(); boss.hatchH = lavinia.hatchF[0].getRegionHeight();
            boss.hatchX = 1f; boss.hatchY = boss.h - 16f - boss.hatchH;   // (16 px below the top of boss2.png)
            boss.x = scrollX - boss.w;
            boss.y = scrollY + H / 2f - boss.h / 2f;
        }
        if (brain.eyeF != null) {
            boss.hasEye = true;
            boss.eyeFrames = brain.eyeF.length;
            boss.eyeX = brain.eyeOX; boss.eyeY = brain.eyeOY;
            boss.eyeW = brain.eyeF[0].getRegionWidth(); boss.eyeH = brain.eyeF[0].getRegionHeight();
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
            op4.op4T = 0f;
            bossPart++;
            bossSpawned = false;                      // (the scrolling goes on; the next one wakes on screen)
            return;
        }
        for (Player p : players) if (p != null && !p.out) p.score += stageBonus();
        eBullets.clear();
        bossGuns.gunLasers.clear();
        if (!boss.dissolve) {
            boom(boss.x + boss.w / 2f, boss.y + boss.h / 2f, 2.5f);   // a last big blast, then the boss is gone
            a.play("boom", 0.5f);
        }
        boss = null;
        setState(State.CLEAR);
    }

    int stageBonus() { return 2000 + 1000 * (levelIdx + 1); }

    // =============================================================== shooting helpers (used by enemies/bosses)

    /** Is the point (x, y) on screen, at least `margin` px inside its edges? Things off screen never shoot. */
    boolean inView(float x, float y, float margin) {
        return x >= scrollX + margin && x <= scrollX + W - margin && y >= scrollY + margin && y <= scrollY + H - margin;
    }

    /** Op 4 moai rings flash between yellow and white this many times a second (Albert). */
    static final float RING_FLASH = 15f;
    /** Points for shooting down an Op 4 moai ring. */
    static final int RING_SCORE = 10;

    /** Op 4 moai rings: the yellow ones (#e0e027, as on the MSX); ring_laser.png is white. */
    static final Color RING_YELLOW = new Color(0xe0 / 255f, 0xe0 / 255f, 0x27 / 255f, 1f);
    /** The red rings (they leave a capsule) flash between AMOEBA_RED and this. */
    static final Color RING_RED_DARK = new Color(0xb0 / 255f, 0x20 / 255f, 0x20 / 255f, 1f);

    /** Red amoeba colour (#ff2727): the white of amoeba.png is tinted with it. */
    static final Color AMOEBA_RED = new Color(1f, 0x27 / 255f, 0x27 / 255f, 1f);
    /**
     * Amoebas all change colour together (as on the MSX): yellow, cream, white, green, AMOEBA_COLOUR_TIME s each
     * (3 frames at 60 fps). amoebaClock runs while playing.
     */
    static final Color[] AMOEBA_COLOURS = {new Color(224 / 255f, 224 / 255f, 39 / 255f, 1f),
            new Color(224 / 255f, 224 / 255f, 155 / 255f, 1f), new Color(1f, 1f, 1f, 1f), new Color(115 / 255f, 1f, 115 / 255f, 1f)};
    static final float AMOEBA_COLOUR_TIME = 0.05f;
    float amoebaClock;
    /** Other capsule carriers are tinted light red. */
    static final Color CARRIER_TINT = new Color(1f, 0.45f, 0.45f, 1f);

    /** A tower spits out an amoeba at (x, y) (bottom-left), thrown with (vx, vy). A red one leaves a power capsule. */
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
    boolean sceneryStops(float x, float y, float w, float h) {
        for (Hazard hz : hazards) if (!hz.dead && hz.type == Hazard.Type.TOWER && hz.touches(x, y, w, h)) return true;
        if (eioneusRoom.podTouches(x, y, w, h)) return true;
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
    void hitSpark(Bullet b, float tx, float ty, float tw, float th) {
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
        if (b.diag) {   // aimed diagonally: the ring just grows (up to PlayerWeapons.RIPPLE_DIAG_MAX), its box kept around its middle
            float cx = b.x + b.w / 2f, cy = b.y + b.h / 2f;
            b.len = Math.min(PlayerWeapons.RIPPLE_DIAG_MAX, b.len + Bullet.RIPPLE_GROW * dt);
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
    void drawRipple(Bullet b) {
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

    /** A missile that hit something bursts where it is (shots and lasers show a hit spark instead). */
    void missileBurst(Bullet b) {
        if (b.kind == Bullet.Kind.MISSILE) boom(b.x, b.y, 0.6f);
    }

    static void addScore(Player p, int pts) { if (p != null) p.score += pts; }

    // =============================================================== collisions

    static boolean overlap(float ax, float ay, float aw, float ah, float bx, float by, float bw, float bh) {
        return ax < bx + bw && ax + aw > bx && ay < by + bh && ay + ah > by;
    }
}
