package com.example.salamander;

import java.util.ArrayList;
import java.util.function.Function;

/**
 * One stage, loaded from a Tiled map: assets/maps/stage1.tmx ... stage7.tmx.
 * See the README ("Editing levels in Tiled") for how the layers and objects are used.
 *
 * World coordinates: pixels, origin at the bottom-left of the map, y pointing up (Tiled's y points down;
 * the conversion happens here). Collision is a grid with one cell per map tile.
 */
public class Level {
    public static class Spawn {
        public float x, y, phase;
        /** Nests: what comes out ("ug", "creeping death", "mines" or "none"). */
        public String what = "";
        public Enemy.Type type;
        public int group, groupSize;
        public boolean carrier;
        /** Walkers: walks upside down on the ceiling / runs in from behind (left edge). */
        public boolean ceiling, behind;
        /**
         * Walkers (Kierke): leaps in from a screen edge at its object's height and drops onto the floor (or, with
         * "ceiling", "falls" up onto the ceiling): 1 = from the left edge, -1 = from the right edge, 0 = an ordinary walker.
         */
        public int leap;
        /** Celtic Frost: flies the Z mirrored (starts in the upper half); seconds it waits behind the one in front. */
        public boolean upper;
        public float delay;
        /** Legacy: hovers where it was placed (as on the MSX) instead of flying by in a wave. */
        public boolean hover;
        /** Possessed: a "row" group (Eioneus). */
        public boolean row;
        /** Stage 2 wall turrets: mounted on a wall to their right (they face left); barrel starts diagonal (up / down). */
        public boolean right, diag, diagUp;
        /** Laser Guards: Enemy.GUARD_CEIL / GUARD_FLOOR / GUARD_WALL. */
        public int guardSide;
        /**
         * Vertical stages: fly-by Legacies of a wave come in one after another, this far apart (the first at 0); a
         * Celtic Frost wave enters along the column through midX (the middle of the object).
         */
        public float gap, midX;
        /** Possessed: which member of its group, and whether it is a red one. */
        public int member;
        public boolean red;
        /** Lavinia wall lasers: the beam (world rectangle, bottom-left). Possessed rows: place / row depth (NaN: default). */
        public float beamX, beamY, beamW, beamH, place = Float.NaN, rowDown = Float.NaN, side;
        /** Possessed: colours of the members in order ("r" / "c"), overriding "reds". */
        public String colours = "";
        /** Whiplash: comes up from the bottom edge (left edge on a horizontal stage) instead of the top (right). */
        public boolean fromBottom;
        /** MSX Missing Link (Kierke): full swing height (0 = the old link) and shots in its one burst; "upper" = starts at the bottom. */
        public float linkSwing;
        public int linkBurst;
        /** Op 4 moai heads: stay at their wall (don't step out). Op 4 domes: flyers per burst. */
        public boolean still;
        public int count;
        /** Op 4 domes: what comes out ("" = homing green flyers, "creeping death"). */
        public String spawns = "";
        /**
         * Spawns when the camera's right edge reaches this x (spawns are sorted by it). Vertical stages: when the
         * camera's top edge reaches this y.
         */
        public float trigger;
        /** Blue asteroids: sink speed (px/s, < 0 = random, as on Op 4) and sideways drift (px/s, + = right; stage 2). */
        public float sink = -1f, drift;
    }

    /** A background picture from an image layer (the stage art), bottom-left corner in world coordinates. */
    public static class Art { public String image; public float x, y, w, h; }

    /** Stage 1 teeth: position when fully out, size, picture and timing. */
    public static class Tooth { public float x, y, w, h, delay, period, holdOut, rest, shortBy, sinkWait; public boolean ceiling, switchSides, sink; public String image; }

    /** Floating shootable rock: bottom-left corner, size, picture. */
    public static class Bamda { public float x, y, w, h; public String image; }


    private static final int W = SalamanderGame.W, H = SalamanderGame.H;

    public final int index;
    public String name = "", music, bossMusic = "boss.mid", background;
    /**
     * The intro banner (as on the MSX: "OPERATION THREE / -SET THE LEEK SYSTEM- / PLANET LAVINIA"): map properties
     * "bannerTitle" (default: "name"), "subtitle" and "planet" (both optional).
     */
    public String bannerTitle = "", subtitle = "", planet = "";
    /**
     * Where no stars are drawn, plain black instead (map property "noStars"): a world range along the scrolling, x on a
     * horizontal stage, y on a vertical one (noStarsLo < noStarsHi). NaN = stars everywhere.
     */
    public float noStarsLo = Float.NaN, noStarsHi = Float.NaN;
    /**
     * Map property "bossMusicAt": the boss music already starts when the middle of the screen reaches this x (stage 1:
     * the brick maze). -1 = only when the boss comes.
     */
    public float bossMusicAt = -1f;
    /**
     * Map object "bossmusic" (a point on the camera path, like a checkpoint): when the screen gets there, the stage music
     * fades out and the boss music follows. Distance along the path; -1 = no such object.
     */
    public float bossMusicT = -1f;
    /**
     * Map object "hold" (a point on the camera path, like a checkpoint): the scrolling stops there and waits for the
     * room fight (stage 3's Laser Guard room) to end: holdTime seconds (object property "time", default 60), then the
     * guards retreat and the "dematwall" walls dissolve, and the scrolling goes on. Distance along the path; -1 = none.
     */
    public float holdT = -1f, holdTime = 60f;

    /**
     * Map object "dematwall" (a rectangle): a wall that dissolves when the hold is over. Its picture (property "image",
     * a path in assets, drawn at the rectangle) is not part of the stage art; its collision cells are cleared when it is
     * gone (cells completely inside the rectangle).
     */
    public static class DematWall { public float x, y, w, h; public String image; }
    public final ArrayList<DematWall> dematWalls = new ArrayList<>();
    /** The collision of the dematwall cells as loaded (to put them back on a restart before the room). */
    private byte[] gridBackup;
    /**
     * Map property "musicLoop": loop length of the stage song in seconds. The song plays to just before the end of its
     * file and then jumps back this far, seamlessly, instead of starting over. -1 = loop the whole song.
     */
    public float musicLoop = -1f;
    /**
     * Map property "musicLoopEnd": where (seconds from the start of the song) the loop ends and jumps back musicLoop
     * seconds, before the song fades out. -1 = just before the end of the file.
     */
    public float musicLoopEnd = -1f;
    /** Map properties "bossMusicLoop" / "bossMusicLoopEnd": the same for the boss music. */
    public float bossMusicLoop = -1f, bossMusicLoopEnd = -1f;
    public float baseSpeed = 46f;
    /**
     * Title screen style: true = MSX, false = REMAKE. Read when a stage is loaded: the map property "speedMsx"
     * replaces "speed", the path property "speedsMsx" replaces "speeds". Objects whose property "style" is "remake"
     * only appear in the REMAKE (its extras); all others, "msx" ones included, appear in both styles.
     */
    public static boolean originalStyle;

    /**
     * MSX-style behaviours (arms, bamdas, towers, walkers) are on: MSX style, on the stages measured from a recording
     * so far (stage 1).
     */
    public boolean msx() { return originalStyle && index == 0; }
    /**
     * Map object "boss" (optional): where the boss picture is in the map (bottom-left, world coordinates) and its size.
     * With "fixed" on, the boss stays there instead of flying in, and wakes up once it comes on screen.
     */
    public boolean bossFixed;
    public float bossX, bossY, bossW, bossH;
    /**
     * Height of the "lane" the level was laid out for (map property "lane", default 272). When it is taller
     * than the 168px view, the camera drifts up and down within it to follow the ships.
     */
    public float laneH = SalamanderGame.LANE_H;
    public float worldW, worldH;

    // ---- collision grid (one cell per map tile), row 0 = bottom
    public float cell;
    public int gCols, gRows;
    private byte[] grid;

    // ---- drawing
    public TiledMap tiled;
    public final ArrayList<Art> art = new ArrayList<>();
    /** Visible terrain tiles (layer "terrain"), gid per cell, row 0 = bottom; null if the map has none. */
    public int[] terrain;

    // ---- destructible bricks (layer "bricks"): one per cell, row 0 = bottom
    public boolean[] bricks;
    /** Seconds until a shot-away brick grows back (0 = it stays as it is). */
    public float[] brickRegrow;
    public static final float BRICK_REGROW_TIME = 3f;
    /** This stage's regrow time (map property "brickRegrow"; 0 = shot-away bricks are gone for good). */
    public float brickRegrowTime = BRICK_REGROW_TIME;
    /**
     * A regrowing brick first materializes over BRICK_GROW_TIME seconds (brickGrow counts 0 -> 1). While it does,
     * it is harmless and not solid, but shots still blast it away again.
     */
    public float[] brickGrow;
    public static final float BRICK_GROW_TIME = 1f;

    // ---- objects
    public final ArrayList<Spawn> spawns = new ArrayList<>();
    public final ArrayList<Bamda> bamdas = new ArrayList<>();
    public final ArrayList<Tooth> teeth = new ArrayList<>();
    public final ArrayList<float[]> rocks = new ArrayList<>();       // {left x, bottom y}
    /** Eioneus's boss room: the gun pods (map objects "gunpod", gunpod.png): {left, bottom} in the world. */
    public final ArrayList<float[]> pods = new ArrayList<>();
    public final ArrayList<float[]> volcanoes = new ArrayList<>();   // {centre x, floor y, 0}; side volcano: {centre x, centre y, -1 / +1}
    /** Stage 1 claw arms: bottom-left of the resting picture (64x40), hanging from the ceiling or standing on the floor. */
    public static class Arm { public float x, y; public boolean ceiling; }
    public final ArrayList<Arm> arms = new ArrayList<>();
    /** Kierke chains: the top-left corner of the first ball (y up: its top edge) and how many balls it grows. */
    public static class Chain { public float x, top; public int count; }
    public final ArrayList<Chain> chains = new ArrayList<>();
    /** Stage 1 green-tipped towers: where the picture is when fully grown, and whether it hangs from the ceiling. */
    public static class Tower { public float x, y, w, h; public boolean ceiling, flip; public String image; }
    public final ArrayList<Tower> towers = new ArrayList<>();
    /** Stage 2 domes on the side walls (bottom-left of the picture, 18x32; right = on the right wall, facing left). */
    /** A dome: bottom-left corner of its object; on the right wall; stage 3: on the floor / ceiling (top = the object's top edge). */
    /** gunHos: a floor hatch (both styles) that lets out this many Gun Hos instead of spitting amoebas (Lavinia). */
    public static class Dome { public float x, y, top; public boolean right, floor, ceiling; public int gunHos; }
    public final ArrayList<Dome> domes = new ArrayList<>();
    /** Lavinia: where Led Zeps keep coming ({x0, x1, mean seconds between them}; the camera's right edge in x0..x1). */
    public final ArrayList<float[]> ledZepZones = new ArrayList<>();
    /**
     * Lavinia's darkness (map object "dark_zone", a rectangle): {x0, x1}. The lights go off when the camera's left edge
     * reaches x0 and stay off until a ship picks up an item_spark (or, if it was missed, the camera's left edge reaches x1).
     */
    public final ArrayList<float[]> darkZones = new ArrayList<>();
    /** Map objects "item_spark" (tile, bottom-left {x, y}): touching one turns the lights back on. */
    public final ArrayList<float[]> sparks = new ArrayList<>();
    /**
     * Background lights (map objects "back_ground_lights"; Op 4 and Lavinia): {left x, bottom y, first frame}. The sprite
     * is the stage's sprites/stage_<N>_back_ground_lights.png (square frames in a row). Decoration only: drawn just over
     * the stage art, under everything else; never hit, never in the way.
     */
    public final ArrayList<float[]> bgLights = new ArrayList<>();
    /** Seconds each frame of the background lights is shown (map property "backGroundLightsFrame", default 0.1). */
    public float bgLightsFrame = 0.1f;
    /** Stage 2 eye rocks: bottom-left of the 32x32 rock with the eye. */
    public final ArrayList<float[]> eyes = new ArrayList<>();
    /**
     * Asteroid storms (map object "meteors"): {trigger, seconds, speed, xMin, xMax, straight}. The storm starts when the front edge of the screen
     * (the top on a vertical stage, the right edge otherwise) reaches the object, and lasts its "time" property.
     */
    public final ArrayList<float[]> storms = new ArrayList<>();

    /**
     * Vertical stage (map property "vertical"): the camera path runs upward, the ships fly up and fire upward,
     * and spawns / activation go by the top edge of the screen instead of the right edge.
     */
    public boolean vertical;
    /**
     * Picture of the bricks (map property "brickArt", same size as the map): each standing brick shows its own
     * 8x8 piece of it. Without it the bricks use the repeating maps/brick_pattern.png.
     */
    public String brickArt;

    // ---- camera path: points {left x, bottom y, speed multiplier for the segment starting there}
    private float[][] path;
    private float[] pathT;
    /** Distance along the path where scrolling stops and the boss arena starts. */
    public float stopT;
    /** Checkpoints as distance travelled along the path. */
    public float[] checkpoints;

    public static Level build(int idx) {
        return load(idx, "maps/stage" + (idx + 1) + ".tmx", p -> com.badlogic.gdx.Gdx.files.internal(p).readBytes());
    }

    /** Loads a stage; reader reads files relative to the assets folder (so tools and tests can use it too). */
    public static Level load(int idx, String path, Function<String, byte[]> reader) {
        TiledMap m = TiledMap.load(path, reader);
        Level l = new Level(idx);
        l.tiled = m;
        if (m.tileWidth != m.tileHeight) throw new IllegalArgumentException(path + ": tiles must be square");
        l.cell = m.tileWidth;
        l.gCols = m.width;
        l.gRows = m.height;
        l.worldW = l.gCols * l.cell;
        l.worldH = l.gRows * l.cell;
        l.name = m.props.getOrDefault("name", "STAGE " + (idx + 1));
        l.bannerTitle = m.props.getOrDefault("bannerTitle", l.name);
        l.subtitle = m.props.getOrDefault("subtitle", "");
        l.planet = m.props.getOrDefault("planet", "");
        l.baseSpeed = Float.parseFloat(m.props.getOrDefault("speed", "46"));
        if (originalStyle && m.props.containsKey("speedMsx")) l.baseSpeed = Float.parseFloat(m.props.get("speedMsx"));
        l.bgLightsFrame = Float.parseFloat(m.props.getOrDefault("backGroundLightsFrame", "0.1"));
        l.laneH = Float.parseFloat(m.props.getOrDefault("lane", String.valueOf(SalamanderGame.LANE_H)));
        l.music = m.props.get("music");
        l.bossMusic = m.props.getOrDefault("bossMusic", "boss.mid");
        l.bossMusicAt = Float.parseFloat(m.props.getOrDefault("bossMusicAt", "-1"));
        l.musicLoop = Float.parseFloat(m.props.getOrDefault("musicLoop", "-1"));
        l.bossMusicLoop = Float.parseFloat(m.props.getOrDefault("bossMusicLoop", "-1"));
        l.musicLoopEnd = Float.parseFloat(m.props.getOrDefault("musicLoopEnd", "-1"));
        l.bossMusicLoopEnd = Float.parseFloat(m.props.getOrDefault("bossMusicLoopEnd", "-1"));
        l.background = m.props.get("background");
        l.vertical = Boolean.parseBoolean(m.props.getOrDefault("vertical", "false"));
        // map property "noStars" ("a,b" in Tiled coordinates along the scrolling: x on a horizontal stage, y on a
        // vertical one): plain black between a and b, where the terrain covers the sky. Just "a": from a to the end.
        if (m.props.containsKey("noStars")) {
            String[] ns = m.props.get("noStars").split(",");
            float a = Float.parseFloat(ns[0].trim());
            float b = ns.length > 1 ? Float.parseFloat(ns[1].trim()) : (l.vertical ? 0f : Float.MAX_VALUE);
            if (l.vertical) { a = l.worldH - a; b = l.worldH - b; }   // (Tiled y runs down)
            l.noStarsLo = Math.min(a, b); l.noStarsHi = Math.max(a, b);
        }
        l.brickArt = m.props.get("brickArt");
        l.brickRegrowTime = Float.parseFloat(m.props.getOrDefault("brickRegrow", String.valueOf(BRICK_REGROW_TIME)));

        // tile layers: terrain (drawn + solid), collision (solid, not drawn), bricks (destructible)
        l.grid = new byte[l.gCols * l.gRows];
        for (TiledMap.TileLayer tl : m.tileLayers) {
            String n = tl.name.toLowerCase();
            boolean isTerrain = n.equals("terrain"), isCollision = n.equals("collision"), isBricks = n.equals("bricks");
            if (!isTerrain && !isCollision && !isBricks) continue;
            if (isTerrain) l.terrain = new int[l.gCols * l.gRows];
            if (isBricks) { l.bricks = new boolean[l.gCols * l.gRows]; l.brickRegrow = new float[l.bricks.length]; l.brickGrow = new float[l.bricks.length]; }
            for (int tr = 0; tr < l.gRows; tr++) {
                int r = l.gRows - 1 - tr;   // Tiled row 0 is the top
                for (int c = 0; c < l.gCols; c++) {
                    int gid = tl.gids[tr * l.gCols + c];
                    if ((gid & TiledMap.GID_MASK) == 0) continue;
                    int i = r * l.gCols + c;
                    if (isTerrain) { l.terrain[i] = gid; l.grid[i] = 1; }
                    if (isCollision) l.grid[i] = 1;
                    if (isBricks) l.bricks[i] = true;
                }
            }
        }
        for (TiledMap.ImageLayer il : m.imageLayers) {
            if (!il.visible) continue;
            Art a = new Art();
            a.image = il.image;
            a.w = il.width; a.h = il.height;
            a.x = il.offsetX;
            a.y = l.worldH - il.offsetY - il.height;
            l.art.add(a);
        }
        l.readObjects();
        return l;
    }

    private Level(int idx) { index = idx; }

    // ---------------------------------------------------------------- objects

    private void readObjects() {
        ArrayList<float[]> cpPoints = new ArrayList<>();
        float[] bossMusicPoint = null, holdPoint = null;
        int group = 1;
        for (TiledMap.ObjectLayer layer : tiled.objectLayers) {
            for (TiledMap.MapObject o : layer.objects) {
                TiledMap.Tile tile = o.gid != 0 ? tiled.tile(o.gid) : null;
                String type = !o.type.isEmpty() ? o.type : (tile != null ? tile.type : "");
                if (type.isEmpty()) type = o.name;
                type = type.toLowerCase();
                // tile properties are the defaults, the object's own properties win
                TiledMap.MapObject p = new TiledMap.MapObject();
                if (tile != null) p.props.putAll(tile.props);
                p.props.putAll(o.props);
                String style = p.props.getOrDefault("style", "").trim().toLowerCase();
                // "remake" objects are the REMAKE's own extras; "msx" ones are in both styles (Albert, 2026-10-27:
                // the REMAKE always has everything the MSX style has, plus its own)
                if (originalStyle && style.equals("remake")) continue;
                boolean flippedV = (o.gid & TiledMap.FLIP_V) != 0;
                boolean flippedH = (o.gid & TiledMap.FLIP_H) != 0;

                float w = o.width, h = o.height;
                if (o.gid != 0 && tile != null && w == 0) { w = tile.width; h = tile.height; }
                // bottom-left corner in world coordinates (tile objects are anchored bottom-left in Tiled)
                float left = o.x;
                float bottom = o.gid != 0 ? worldH - o.y : worldH - o.y - h;
                float top = bottom + h;

                if (o.polyline != null && (type.equals("path") || o.name.equalsIgnoreCase("path"))) {
                    readPath(o);
                    continue;
                }
                switch (type) {
                    case "whiplash": {                           // slides in, stops, darts off diagonally
                        Spawn s = spawn(Enemy.Type.WHIPLASH, left, bottom + h / 2f, group++, 1, true);   // every Whiplash leaves a capsule
                        s.fromBottom = p.prop("fromBottom", false);
                        s.midX = left + w / 2f;
                        break;
                    }
                    case "laserbattery": case "laserbattery_big": {   // Eioneus laser battery: a beam from its lens
                        boolean big = type.equals("laserbattery_big");
                        Spawn s = spawn(Enemy.Type.LASER_BATTERY, left, bottom, group++, 1, p.prop("drop", false));
                        s.what = big ? "big" : "small";
                        s.right = !flippedH;                     // small: beam to the right (flipped: to the left)
                        s.ceiling = flippedV;                    // big: hangs from the ceiling, its beam goes down
                        DematWall dw = new DematWall();          // it is an enemy now, not rock: clear its cells
                        dw.x = left; dw.y = bottom; dw.w = w; dw.h = h;
                        setWallGone(dw, true);
                        break;
                    }
                    case "seed": {                               // Eioneus seed: lets flying mines out
                        // the object is the 44x46 base picture (bottom-left), which is in the map art (destroyed);
                        // the enemy is only its 20x21 middle, 12 px in from the left and 12 px up from the bottom
                        spawn(Enemy.Type.SEED, left + 12f, bottom + 12f, group++, 1, p.prop("drop", false));
                        break;
                    }
                    case "gremlin": {                            // Eioneus wall walker: comes up from the bottom edge
                        Spawn s = spawn(Enemy.Type.GREMLIN, left, bottom, group++, 1, p.prop("drop", false) || p.prop("red", false));   // (a red one leaves a capsule)
                        s.right = flippedH || p.prop("right", left + w / 2f > W / 2f);
                        s.red = p.prop("red", false);
                        s.gap = H + 40f;                         // (vertical stage: when its place reaches the bottom edge)
                        break;
                    }
                    case "exodus": {                             // Op 4: a row of (3) that comes in at the top edge
                        // the object is the first (left) one, at the height where it comes in; the others follow
                        // to its right, "gap" px apart; "colours" = c / r per member (r = red, leaves a capsule)
                        int n = Math.max(1, Math.min(8, p.prop("count", 3)));
                        float gap = p.prop("gap", Enemy.EXODUS_GAP);
                        String cols = p.prop("colours", p.prop("colors", ""));
                        for (int i = 0; i < n; i++) {
                            boolean red = cols.length() > i && Character.toLowerCase(cols.charAt(i)) == 'r';
                            Spawn s = spawn(Enemy.Type.EXODUS, left + i * gap, bottom + h / 2f, group++, 1, red || p.prop("drop", false));
                            s.red = red;
                        }
                        break;
                    }
                    case "artillery": {                          // Op 4 wall gun: flip it (X) for the right wall
                        Spawn s = spawn(Enemy.Type.ARTILLERY, left, bottom, group++, 1, p.prop("red", false) || p.prop("drop", false));
                        s.right = flippedH || p.prop("right", false);
                        s.red = p.prop("red", false);
                        if (!vertical) s.what = flippedV || p.prop("ceiling", false) ? "ceiling" : "floor";   // (Lavinia: on the floor / ceiling)
                        break;
                    }
                    case "blue_asteroid": {                       // blue rock clump (Op 4, stage 2): sinks slowly
                        Spawn s = spawn(Enemy.Type.BLUE_ASTEROID, left, bottom, group++, 1, p.prop("drop", false));
                        s.sink = p.prop("sink", -1f);             // (stage 2: each one's speed from the recording)
                        s.drift = p.prop("drift", 0f);            // (stage 2: two of them cut across diagonally)
                        break;
                    }
                    case "moai": {                               // Op 4 moai on the left wall; flip it (X) for the right wall
                        Spawn s = spawn(Enemy.Type.MOAI, left, bottom, group++, 1, p.prop("drop", false));
                        s.right = flippedH || p.prop("right", false);
                        s.still = !p.prop("walk", true);
                        break;
                    }
                    case "gunpod":                                // Eioneus boss room: a gun pod (GameScreen's pod room)
                        pods.add(new float[]{left, bottom});
                        break;
                    case "orb6": {                               // Op 4 bouncing ball: comes in at the top edge at its x
                        spawn(Enemy.Type.ORB, left, bottom + h / 2f, group++, 1, p.prop("drop", false));
                        break;
                    }
                    case "back_ground_lights":                   // background light (decoration)
                        bgLights.add(new float[]{left, bottom, p.prop("frame", 0)});
                        break;
                    case "base6": {                              // Op 4 base: lets one burst of mines out
                        Spawn s = spawn(Enemy.Type.SEED, left, bottom, group++, 1, p.prop("drop", false));
                        s.what = "op4";
                        break;
                    }
                    case "dome_type4": {                         // Op 4 wall dome: flip it (X) for the right wall
                        Spawn s = spawn(Enemy.Type.BLUE_DOME, left, bottom, group++, 1, p.prop("drop", false));
                        s.right = flippedH || p.prop("right", false);
                        s.what = "op4";
                        s.spawns = p.prop("spawns", "").toLowerCase().replace("_", " ").trim();
                        boolean creep = s.spawns.startsWith("creeping");
                        s.count = p.prop("burst", creep ? Enemy.CREEP_BURST : Enemy.BLUE_DOME_BURST);
                        if (originalStyle) s.count = p.prop("burstMsx", s.count);
                        break;
                    }
                    case "dome_type3": {                         // Eioneus wall dome: lets green flyers out
                        Spawn s = spawn(Enemy.Type.BLUE_DOME, left, bottom, group++, 1, p.prop("drop", false));
                        s.right = flippedH || p.prop("right", false);   // flipped in Tiled: on the right wall
                        break;
                    }
                    case "gamma": {                              // Kierke blue ball: in at the right edge, bouncing
                        Spawn s = spawn(Enemy.Type.GAMMA, left, bottom, group++, 1, p.prop("drop", false) || p.prop("red", false));
                        s.ceiling = p.prop("ceiling", false);
                        s.red = p.prop("red", false);
                        s.fromBottom = p.prop("fromLeft", false);   // (in at the left edge instead)
                        break;
                    }
                    case "aberrant": {                         // Kierke plant: stays put; the root of a chain
                        spawn(Enemy.Type.ABERRANT, left, bottom, group++, 1, p.prop("drop", false));
                        break;
                    }
                    case "nest": {                               // Kierke nest: lets bugs out of its mouth
                        // the picture's mouth faces down (a ceiling nest); flip it vertically in Tiled for the floor.
                        // The object is the whole 38x22 picture; the nest itself is the 32x17 inside it.
                        Spawn s = spawn(Enemy.Type.NEST, left + 3f, flippedV ? bottom + 2f : bottom + 3f, group++, 1, p.prop("drop", false));
                        s.ceiling = !flippedV;
                        s.what = p.prop("spawns", "ug");
                        break;
                    }
                    case "chain": {                              // grows a ball at a time toward the nearest ship
                        Chain c = new Chain();
                        c.x = left; c.top = top; c.count = Math.max(1, p.prop("count", 7));
                        chains.add(c);
                        break;
                    }
                    case "battleaxe": {                          // a rigid row that flies a square zigzag
                        int n = Math.max(1, Math.min(12, p.prop("count", Enemy.BATTLEAXE_COUNT)));
                        boolean high = p.prop("high", false);
                        for (int i = 0; i < n; i++) {
                            Spawn s = spawn(Enemy.Type.BATTLEAXE, left, bottom + h / 2f, group, n, p.prop("drop", false));
                            s.member = i; s.upper = high;
                        }
                        group++;
                        break;
                    }
                    case "possessed": {                          // a group that clusters near the top and shoots
                        int n = Math.max(1, p.prop("count", 4)), reds = Math.max(0, Math.min(n, p.prop("reds", n / 2)));
                        for (int i = 0; i < n; i++) {
                            Spawn s = spawn(Enemy.Type.POSSESSED, left, bottom + h / 2f, group, n, p.prop("drop", true));
                            s.member = i; s.delay = i * Enemy.POSSESSED_GAP; s.midX = left + w / 2f;
                            s.red = (i + 1) * reds / n > i * reds / n;   // the red ones spread evenly through the group
                            if (p.prop("row", false)) {          // a row group (Eioneus, Lavinia)
                                s.row = true; s.delay = i * p.prop("gap", Enemy.POSS_ROW_DELAY);
                                s.place = p.prop("place", Float.NaN);   // horizontal stages: x of the first place on the screen
                                s.rowDown = p.prop("down", Float.NaN);
                                String side = p.prop("side", "");      // "left" / "right"; empty: toward the nearest ship
                                s.side = side.equalsIgnoreCase("left") ? -1f : side.equalsIgnoreCase("right") ? 1f : 0f;
                            }
                            String cols = p.prop("colours", p.prop("colors", ""));
                            if (cols.length() > i) s.red = Character.toLowerCase(cols.charAt(i)) == 'r';
                        }
                        group++;
                        break;
                    }
                    case "legacy": {
                        if (!p.prop("flyby", false)) {           // MSX Legacy: one that hovers where it is placed
                            Spawn s = spawn(Enemy.Type.LEGACY, left, bottom + h / 2f, group++, 1, true);   // every Legacy drops a capsule (Albert, 2026-10-29)
                            s.hover = true;
                            s.phase = (o.id * 2.39996f) % 6.2832f;
                            s.midX = left + w / 2f;
                            break;
                        }
                        // "flyby" = the old Legacy wave that weaves across the screen (4 by default)
                        int n = p.prop("count", 4);
                        float phase = p.prop("phase", (o.id * 2.39996f) % 6.2832f);
                        for (int i = 0; i < n; i++) {
                            Spawn s = spawn(Enemy.Type.LEGACY, left + i * 18f,   // each one its own capsule (Albert, 2026-10-29)
                                    bottom + h / 2f, group++, 1, true);
                            s.phase = phase;
                            s.gap = i * 18f;
                            s.midX = left + w / 2f;
                        }
                        group++;
                        break;
                    }
                    case "rugal":                                // Rugal: enters at the right edge, at this height
                        spawn(Enemy.Type.RUGAL, left, bottom + h / 2f, group++, 1, p.prop("drop", false));
                        break;
                    case "block_small": case "lavinia_block_small":   // Lavinia: slides up and down between ceiling and floor
                    case "block_big": case "lavinia_block_big": {      // Lavinia: slides left through its slot
                        boolean big = type.endsWith("big");
                        spawn(big ? Enemy.Type.BLOCK_BIG : Enemy.Type.BLOCK_SMALL, left, bottom, group++, 1, p.prop("drop", false));
                        break;
                    }
                    case "laser_gun_point": case "lasergun": {   // Lavinia shaft: pulsed laser; "twin" = a second one that far right, facing the other way
                        Spawn s = spawn(Enemy.Type.LASER_GUN, left, bottom, group++, 1, false);
                        s.right = !flippedH;                     // the picture points right; flipped: left
                        s.delay = p.prop("delay", 0f);
                        float twin = p.prop("twin", 0f);
                        if (twin != 0f) {
                            Spawn s2 = spawn(Enemy.Type.LASER_GUN, left + twin, bottom, group++, 1, false);
                            s2.right = !s.right; s2.delay = s.delay; s2.trigger = left;
                        }
                        break;
                    }
                    case "dark_zone":
                        darkZones.add(new float[]{left, left + w});
                        break;
                    case "item_spark": case "spark":
                        sparks.add(new float[]{left, bottom});
                        break;
                    case "ledzep_zone": {                        // Lavinia: Led Zeps come at random while the camera's right edge is in here
                        ledZepZones.add(new float[]{left, left + w, p.prop("interval", 2.5f)});
                        break;
                    }
                    case "heathen":                              // Lavinia: a pillar that rushes in at the right edge (its picture is random)
                        spawn(Enemy.Type.HEATHEN, left, bottom, group++, 1, false);
                        break;
                    case "gunho": case "gun ho": case "gun_ho":
                        spawn(Enemy.Type.GUN_HO, left, bottom, group++, 1, p.prop("drop", false));
                        break;
                    case "omen": {                               // Omen: flip it (Y) for the ceiling
                        Spawn s = spawn(Enemy.Type.OMEN, left, top, group++, 1, p.prop("drop", false));   // y: its top edge
                        s.ceiling = flippedV || p.prop("ceiling", false);
                        break;
                    }
                    case "link": {                               // Missing Link: a single enemy (y = the middle of its bob)
                        float swing = p.prop("swing", 0f);
                        if (swing > 0f) {
                            // MSX style (Kierke): the object is where it comes in, at the right edge with LINK_MSX_IN px
                            // showing, at the top of its swing ("up": the bottom). See Enemy.updateLinkMsx.
                            Spawn s = spawn(Enemy.Type.LINK, left, bottom, group++, 1, p.prop("drop", false));
                            s.linkSwing = swing;
                            s.upper = p.prop("up", false);
                            s.linkBurst = Math.max(1, p.prop("burst", 1));
                            s.trigger = left + Enemy.LINK_MSX_IN + 40f;   // (spawned once the camera's right edge + 40 reaches it)
                            break;
                        }
                        spawn(Enemy.Type.LINK, left, bottom + h / 2f, group++, 1, p.prop("drop", false)).phase = (o.id * 2.39996f) % 6.2832f;
                        break;
                    }
                    case "green_mine": {                         // Kierke green mine: in at the right edge, homing on the ship
                        Spawn s = spawn(Enemy.Type.GREEN_MINE, left, bottom, group++, 1, p.prop("drop", false));
                        s.trigger = left + Enemy.GMINE_IN + 40f;   // (vertical stages: when the top edge reaches it)
                        s.fromBottom = p.prop("fromBottom", false);   // vertical stages: in at the bottom edge
                        break;
                    }
                    case "frost": {                              // Celtic Frost: a group flying a "Z" across the screen
                        int n = p.prop("count", 6);
                        boolean upper = flippedV || p.prop("upper", false);
                        for (int i = 0; i < n; i++) {
                            Spawn s = spawn(Enemy.Type.FROST, left, bottom + h / 2f, group, n, p.prop("drop", true));   // y: entry line
                            s.upper = upper;
                            s.delay = i * Enemy.FROST_GAP;
                            s.midX = left + w / 2f;
                        }
                        group++;
                        break;
                    }
                    case "walker": {
                        boolean ceiling = p.prop("ceiling", flippedV);
                        String leap = p.prop("leap", "").trim().toLowerCase();
                        if (leap.equals("left") || leap.equals("right")) {
                            // Kierke: the object sits at the screen edge it leaps in from (2 px off the left edge, or its
                            // last 12 px on the right edge, as on the MSX), at the height it comes in at; flipped
                            // vertically (or "ceiling") = it heads for the ceiling. See Enemy.updateLeaper.
                            Spawn s = spawn(Enemy.Type.WALKER, left, bottom, group++, 1, p.prop("drop", true));
                            s.ceiling = flippedV || p.prop("ceiling", false);
                            s.leap = leap.equals("left") ? 1 : -1;
                            // (spawned once the camera's right edge + 40 reaches the trigger)
                            s.trigger = s.leap > 0 ? left + Enemy.LEAP_IN_LEFT + W + 40f : left - Enemy.LEAP_IN_RIGHT + W + 40f;
                            break;
                        }
                        Spawn s = spawn(Enemy.Type.WALKER, left, ceiling ? top - 4f : bottom + 4f, group++, 1, p.prop("drop", true));
                        s.ceiling = ceiling;
                        s.behind = p.prop("behind", false);
                        if (s.behind) s.trigger = left + 20f + W + 40f;   // runs in once the camera's left edge passes it
                        break;
                    }
                    case "turret": {                             // bio turret; "drop" = red one that leaves a capsule
                        boolean ceiling = flippedV || p.prop("ceiling", false);
                        spawn(ceiling ? Enemy.Type.TURRET_CEIL : Enemy.Type.TURRET_FLOOR, left,
                                ceiling ? top - 4f : bottom + 4f, group++, 1, p.prop("drop", false));
                        break;
                    }
                    case "wallturret": {                         // stage 2 wall turret; "drop" = red one that leaves a capsule
                        Spawn s = spawn(Enemy.Type.TURRET_WALL, left, bottom, group++, 1, p.prop("drop", false));
                        s.right = flippedH || p.prop("right", false);   // flipped in Tiled: on the right wall
                        s.diag = p.prop("diag", false);
                        s.diagUp = flippedV;
                        break;
                    }
                    case "dome_type1": {                         // stage 2 dome on a side wall: flip it (X) for the right wall
                        Dome d = new Dome();
                        d.x = left; d.y = bottom; d.right = flippedH || p.prop("right", false);
                        domes.add(d);
                        break;
                    }
                    case "dome_type2": {                         // stage 3: the same dome on the floor; flip it (Y) for the ceiling
                        Dome d = new Dome();
                        d.x = left; d.y = bottom; d.top = top;
                        d.ceiling = flippedV || p.prop("ceiling", false);
                        d.floor = !d.ceiling;
                        d.gunHos = p.prop("gunhos", 0);                      // a Gun Ho hatch (both styles)
                        domes.add(d);
                        break;
                    }
                    case "eye": eyes.add(new float[]{left, bottom}); break;
                    case "meteors": case "asteroids": case "storm":   // asteroid storm: rocks rain down for "time" seconds
                        // optional (Op 4): "speed" (px/s; 0 = the usual mix), "straight" (no slanting rocks), and
                        // "xMin" / "xMax": where on the screen the rocks come in (px from the left edge; 0 = anywhere)
                        storms.add(new float[]{vertical ? bottom : left, p.prop("time", 15f), p.prop("speed", 0f),
                                p.prop("xMin", 0f), p.prop("xMax", 0f), p.prop("straight", false) ? 1f : 0f});
                        break;
                    case "bamda": {
                        Bamda a = new Bamda();
                        a.x = left; a.y = bottom; a.w = w; a.h = h;
                        a.image = tile != null && tile.image != null ? tile.image : "sprites/bamda.png";
                        bamdas.add(a);
                        break;
                    }
                    case "tooth": {
                        Tooth t = new Tooth();
                        t.x = left; t.y = bottom; t.w = w; t.h = h;
                        t.ceiling = p.prop("ceiling", true);
                        t.delay = p.prop("delay", 1f);
                        t.period = Math.max(0.5f, p.prop("period", 3.5f));
                        t.holdOut = Math.max(0f, p.prop("holdOut", 0.3f));   // seconds it stays fully out
                        t.rest = Math.max(0f, p.prop("rest", 8f));          // px of its tip that show while it rests
                        t.shortBy = Math.max(0f, p.prop("short", 0f));      // px it stops short of the opposite rock
                        t.switchSides = p.prop("switch", false);            // goes right through and comes back from there
                        t.sink = p.prop("sink", false);                     // goes right into the opposite rock and back
                        t.sinkWait = Math.max(0f, p.prop("sinkWait", 0.5f)); // seconds it stays sunk in there
                        t.image = tile != null ? tile.image : null;
                        if (t.image != null) teeth.add(t);
                        break;
                    }
                    case "rock": rocks.add(new float[]{left, bottom}); break;
                    case "volcano": {                            // spits = up (lava, on the floor), left / right (red rocks, Eioneus)
                        String sp = p.prop("spits", "up");
                        int dir = sp.equalsIgnoreCase("left") ? -1 : sp.equalsIgnoreCase("right") ? 1 : 0;
                        volcanoes.add(dir == 0 ? new float[]{left + w / 2f, bottom, 0f} : new float[]{left + w / 2f, bottom + h / 2f, dir});
                        break;
                    }
                    case "tower": {                              // the object marks the fully grown tower
                        Tower tw = new Tower();
                        tw.x = left; tw.y = bottom; tw.w = w; tw.h = h;
                        // the tile's "ceiling" property says which way its picture points; flipping the object
                        // vertically in Tiled turns it over (a floor tower becomes a ceiling tower and back)
                        tw.flip = flippedV;
                        tw.ceiling = p.prop("ceiling", false) != flippedV;
                        tw.image = tile != null ? tile.image : null;
                        if (tw.image != null) towers.add(tw);
                        break;
                    }
                    case "arm": {                                // tile picture = floor arm; flip it (Y) to hang it from the ceiling
                        Arm a = new Arm();
                        a.x = left; a.y = bottom; a.ceiling = flippedV || p.prop("ceiling", false);
                        arms.add(a);
                        break;
                    }
                    case "boss":                                 // where the boss sits in the map
                        bossX = left; bossY = bottom; bossW = w; bossH = h;
                        bossFixed = p.prop("fixed", false);
                        break;
                    case "checkpoint": cpPoints.add(new float[]{o.x + w / 2f, worldH - o.y - (o.gid != 0 ? -h / 2f : h / 2f)}); break;
                    case "hold":
                        holdPoint = new float[]{o.x + w / 2f, worldH - o.y - (o.gid != 0 ? -h / 2f : h / 2f)};
                        holdTime = p.prop("time", 60f);
                        if (originalStyle) holdTime = p.prop("timeMsx", holdTime);
                        break;
                    case "laser": {                              // Lavinia: a beam from a wall emitter (part of the art)
                        // the rectangle's y is the bottom of the beam (as written by tools/stagebuild.py); the
                        // emitter is at its right end ("emitter" = right, the default) or its left end
                        Spawn s = spawn(Enemy.Type.LASER_BATTERY, left, worldH - o.y, group++, 1, p.prop("drop", false));
                        s.what = "wall";
                        s.right = !p.prop("emitter", "right").equalsIgnoreCase("left");   // (emitter on the right: beam goes left)
                        s.beamX = left; s.beamY = worldH - o.y; s.beamW = w; s.beamH = h;
                        s.x = s.right ? left + w : left - Enemy.WALL_EMITTER_W;
                        s.y = s.beamY + h / 2f - Enemy.WALL_EMITTER_H / 2f;
                        s.trigger = left;
                        DematWall dw = new DematWall();          // shots must reach the emitter: clear the rock cell it sits in
                        dw.x = (float) Math.floor((s.x + Enemy.WALL_EMITTER_W / 2f) / cell) * cell;
                        dw.y = (float) Math.floor((s.beamY + h / 2f) / cell) * cell;
                        dw.w = cell; dw.h = cell;
                        setWallGone(dw, true);
                        break;
                    }
                    case "dematwall": {
                        DematWall dw = new DematWall();
                        dw.x = left; dw.y = bottom; dw.w = w; dw.h = h;
                        dw.image = p.prop("image", "");
                        if (!dw.image.isEmpty()) dematWalls.add(dw);
                        break;
                    }
                    case "laserguard": {                         // Laser Guard: on the ceiling; flip it (Y) for the floor
                        Spawn s = spawn(Enemy.Type.LASER_GUARD, left, flippedV ? bottom : top, group++, 1, false);   // y: the wall line
                        s.guardSide = flippedV || p.prop("floor", false) ? Enemy.GUARD_FLOOR : Enemy.GUARD_CEIL;
                        break;
                    }
                    case "laserguard_wall": {                    // Laser Guard on a right-hand wall (faces left)
                        Spawn s = spawn(Enemy.Type.LASER_GUARD, left + w, bottom, group++, 1, false);   // x: the wall line
                        s.guardSide = Enemy.GUARD_WALL;
                        break;
                    }
                    case "bossmusic": bossMusicPoint = new float[]{o.x + w / 2f, worldH - o.y - (o.gid != 0 ? -h / 2f : h / 2f)}; break;
                    default: break;   // anything else is a note for the level designer
                }
            }
        }
        if (path == null) {   // no path drawn: scroll straight along the middle of the map
            setPath(new float[][]{{0f, Math.max(0f, (worldH - H) / 2f), 1f}, {Math.max(0f, worldW - W), Math.max(0f, (worldH - H) / 2f), 0f}});
        }
        if (bossMusicPoint != null) bossMusicT = project(bossMusicPoint[0] - W / 2f, bossMusicPoint[1] - H / 2f);
        if (holdPoint != null) holdT = project(holdPoint[0] - W / 2f, holdPoint[1] - H / 2f);
        gridBackup = grid.clone();
        ArrayList<Float> cps = new ArrayList<>();
        cps.add(0f);                                  // the start is always a checkpoint
        for (float[] pt : cpPoints) {
            float t = project(pt[0] - W / 2f, pt[1] - H / 2f);
            if (t > 1f) cps.add(t);
        }
        cps.sort(Float::compare);
        checkpoints = new float[cps.size()];
        for (int i = 0; i < cps.size(); i++) checkpoints[i] = cps.get(i);
        if (vertical) for (Spawn s : spawns) s.trigger = s.y + s.gap;   // the top edge of the screen reaches it
        spawns.sort((a, b) -> Float.compare(a.trigger, b.trigger));
        storms.sort((a, b) -> Float.compare(a[0], b[0]));
    }

    private Spawn spawn(Enemy.Type t, float x, float y, int group, int size, boolean carrier) {
        Spawn s = new Spawn();
        s.type = t; s.x = x; s.y = y; s.group = group; s.groupSize = size; s.carrier = carrier; s.trigger = x;
        spawns.add(s);
        return s;
    }

    /** The polyline marks the centre of the screen; property "speeds" = multiplier per segment, e.g. "1,0.8,1" ("speedsMsx" in MSX style). */
    private void readPath(TiledMap.MapObject o) {
        int n = o.polyline.length / 2;
        String speeds = o.prop("speeds", "");
        if (originalStyle && o.props.containsKey("speedsMsx")) speeds = o.prop("speedsMsx", speeds);   // MSX style
        String[] sp = speeds.split(",");
        float[][] p = new float[n][3];
        for (int i = 0; i < n; i++) {
            float cx = o.x + o.polyline[i * 2], cy = worldH - (o.y + o.polyline[i * 2 + 1]);
            p[i][0] = cx - W / 2f;
            p[i][1] = cy - H / 2f;
            float mul = 1f;
            if (i < sp.length && !sp[i].trim().isEmpty()) mul = Float.parseFloat(sp[i].trim());
            p[i][2] = i == n - 1 ? 0f : mul;
        }
        setPath(p);
    }

    private void setPath(float[][] p) {
        path = p;
        pathT = new float[p.length];
        for (int i = 1; i < p.length; i++) {
            float dx = p[i][0] - p[i - 1][0], dy = p[i][1] - p[i - 1][1];
            pathT[i] = pathT[i - 1] + (float) Math.sqrt(dx * dx + dy * dy);
        }
        stopT = pathT[p.length - 1];
    }

    /** Distance along the path of the path point closest to camera position (x, y). */
    private float project(float x, float y) {
        float bestT = 0f, bestD = Float.MAX_VALUE;
        for (int i = 1; i < path.length; i++) {
            float ax = path[i - 1][0], ay = path[i - 1][1], bx = path[i][0], by = path[i][1];
            float dx = bx - ax, dy = by - ay, len2 = dx * dx + dy * dy;
            float f = len2 == 0f ? 0f : Math.max(0f, Math.min(1f, ((x - ax) * dx + (y - ay) * dy) / len2));
            float px = ax + f * dx - x, py = ay + f * dy - y, d = px * px + py * py;
            if (d < bestD) { bestD = d; bestT = pathT[i - 1] + f * (pathT[i] - pathT[i - 1]); }
        }
        return bestT;
    }

    /** Camera bottom-left at distance t along the path -> out[0] = x, out[1] = y. */
    public void camAt(float t, float[] out) {
        if (path.length == 1) { out[0] = path[0][0]; out[1] = path[0][1]; return; }
        for (int i = 1; i < path.length; i++) {
            if (t <= pathT[i] || i == path.length - 1) {
                float len = pathT[i] - pathT[i - 1];
                float f = len <= 0f ? 1f : Math.max(0f, Math.min(1f, (t - pathT[i - 1]) / len));
                out[0] = path[i - 1][0] + f * (path[i][0] - path[i - 1][0]);
                out[1] = path[i - 1][1] + f * (path[i][1] - path[i - 1][1]);
                return;
            }
        }
    }

    /** Scroll-speed multiplier at distance t (from the path's "speeds"). */
    public float speedAtT(float t) {
        for (int i = 1; i < path.length; i++) if (t < pathT[i]) return path[i - 1][2];
        return 0f;
    }

    /** Width of one terrain column (crawlers stop at column edges). */
    public float colWidth() { return cell; }

    // ---------------------------------------------------------------- terrain queries

    /**
     * Height of the ground surface at column wx, seen from height wy: if wy is inside solid ground
     * this is the top of that ground, otherwise the top of the first ground below wy.
     */
    public float floorTop(float wx, float wy) {
        int c = (int) Math.floor(wx / cell), r = (int) Math.floor(wy / cell);
        if (groundCell(c, r)) {
            while (groundCell(c, r + 1)) r++;
            return (r + 1) * cell;
        }
        while (r >= 0 && !groundCell(c, r)) r--;
        return r < 0 ? NO_FLOOR : (r + 1) * cell;   // nothing below: open space, not ground
    }

    /** floorTop() when there is no ground below; ceilBottom() when there is nothing above. */
    public static final float NO_FLOOR = -100000f, NO_CEILING = 100000f;

    /** A cell that can be walked on / hung from: only real map cells (all around the map is open space). */
    private boolean groundCell(int c, int r) { return c >= 0 && c < gCols && r >= 0 && r < gRows && solidCell(c, r); }

    /**
     * Height of the ceiling's underside at column wx, seen from height wy: if wy is inside solid
     * ground this is the bottom of that ground, otherwise the underside of the first ground above wy.
     */
    public float ceilBottom(float wx, float wy) {
        int c = (int) Math.floor(wx / cell), r = (int) Math.floor(wy / cell);
        if (groundCell(c, r)) {
            while (groundCell(c, r - 1)) r--;
            return r * cell;
        }
        while (r < gRows && !groundCell(c, r)) r++;
        return r >= gRows ? NO_CEILING : r * cell;   // nothing above: open space, not a ceiling
    }

    /** Is grid cell (c, r) solid (terrain, collision or a brick)? Outside the map counts as solid. */
    private boolean solidCell(int c, int r) {
        if (c < 0 || r < 0 || c >= gCols || r >= gRows) return true;
        int i = r * gCols + c;
        return grid[i] != 0 || (bricks != null && bricks[i]);
    }

    /** True if the rectangle touches terrain (or a brick). */
    /** A dematwall: clears its collision (gone = true) or puts the collision back as loaded. */
    /** Op 4's end: the whole terrain is gone (dissolved), nothing left to run into. */
    public void clearGrid() { java.util.Arrays.fill(grid, (byte) 0); }

    /** ...and back as loaded (a restart before it). */
    public void restoreGrid() { System.arraycopy(gridBackup, 0, grid, 0, grid.length); }

    public void setWallGone(DematWall dw, boolean gone) {
        int c0 = (int) Math.ceil(dw.x / cell - 0.001f), c1 = (int) Math.floor((dw.x + dw.w) / cell + 0.001f);
        int r0 = (int) Math.ceil(dw.y / cell - 0.001f), r1 = (int) Math.floor((dw.y + dw.h) / cell + 0.001f);
        for (int r = Math.max(0, r0); r < Math.min(gRows, r1); r++)
            for (int c = Math.max(0, c0); c < Math.min(gCols, c1); c++)
                grid[r * gCols + c] = gone ? 0 : gridBackup[r * gCols + c];
    }

    public boolean hits(float x, float y, float w, float h) {
        for (Obstacle o : obstacles)                 // (rubble left in the stage while playing)
            if (x < o.x + o.mask.w && x + w > o.x && y < o.y + o.mask.h && y + h > o.y && o.mask.overlaps(o.x, o.y, x, y, w, h)) return true;
        int c0 = (int) Math.floor(x / cell), c1 = (int) Math.floor((x + w - 0.001f) / cell);
        int r0 = (int) Math.floor(y / cell), r1 = (int) Math.floor((y + h - 0.001f) / cell);
        for (int r = r0; r <= r1; r++)
            for (int c = c0; c <= c1; c++)
                if (solidCell(c, r)) return true;
        return false;
    }

    /**
     * Like hits(), but only what the map really has (collision, terrain, bricks, rubble): everything outside the map
     * is open space. Missiles use this, so where the screen edge is also the map edge they just fly off the screen.
     */
    public boolean hitsMap(float x, float y, float w, float h) {
        for (Obstacle o : obstacles)
            if (x < o.x + o.mask.w && x + w > o.x && y < o.y + o.mask.h && y + h > o.y && o.mask.overlaps(o.x, o.y, x, y, w, h)) return true;
        int c0 = Math.max(0, (int) Math.floor(x / cell)), c1 = Math.min(gCols - 1, (int) Math.floor((x + w - 0.001f) / cell));
        int r0 = Math.max(0, (int) Math.floor(y / cell)), r1 = Math.min(gRows - 1, (int) Math.floor((y + h - 0.001f) / cell));
        for (int r = r0; r <= r1; r++)
            for (int c = c0; c <= c1; c++)
                if (solidCell(c, r)) return true;
        return false;
    }

    /**
     * Solid things added to the stage while playing (stage 2: the rubble a destroyed eye rock leaves behind). They count
     * as terrain for everything that asks hits(): ships crash into them, shots stop, missiles crawl along them. Their
     * exact shape is the picture's. Cleared when the stage restarts from a checkpoint.
     */
    public static class Obstacle {
        public final float x, y;
        public final PixelMask mask;
        public Obstacle(float x, float y, PixelMask mask) { this.x = x; this.y = y; this.mask = mask; }
    }
    public final ArrayList<Obstacle> obstacles = new ArrayList<>();

    /** Is there a solid map cell (not a brick) at cell (c, r)? For the debug overlay. */
    public boolean solidTerrainCell(int c, int r) {
        return c >= 0 && r >= 0 && c < gCols && r < gRows && grid[r * gCols + c] != 0;
    }

    /** Destroys the bricks the rectangle (plus a little blast radius) touches; returns how many. */
    public int breakBricks(float x, float y, float w, float h) {
        if (bricks == null) return 0;
        float m = 4f;
        int c0 = Math.max(0, (int) Math.floor((x - m) / cell)), c1 = Math.min(gCols - 1, (int) Math.floor((x + w + m) / cell));
        int r0 = Math.max(0, (int) Math.floor((y - m) / cell)), r1 = Math.min(gRows - 1, (int) Math.floor((y + h + m) / cell));
        boolean touched = false;
        for (int r = r0; r <= r1 && !touched; r++)
            for (int c = c0; c <= c1; c++)
                if ((bricks[r * gCols + c] || brickGrow[r * gCols + c] > 0f) && x < (c + 1) * cell && x + w > c * cell && y < (r + 1) * cell && y + h > r * cell) { touched = true; break; }
        if (!touched) return 0;
        int n = 0;
        for (int r = r0; r <= r1; r++)
            for (int c = c0; c <= c1; c++) {
                int i = r * gCols + c;
                if (bricks[i] || brickGrow[i] > 0f) { bricks[i] = false; brickGrow[i] = 0f; brickRegrow[i] = brickRegrowTime; n++; }
            }
        return n;
    }
}
