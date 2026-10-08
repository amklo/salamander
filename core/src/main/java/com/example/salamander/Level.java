package com.example.salamander;

import java.util.ArrayList;
import java.util.function.Function;

/**
 * One stage, loaded from a Tiled map: assets/maps/stage1.tmx, stage2.tmx, stage3.tmx.
 * See the README ("Editing levels in Tiled") for how the layers and objects are used.
 *
 * World coordinates: pixels, origin at the bottom-left of the map, y pointing up (Tiled's y points down;
 * the conversion happens here). Collision is a grid with one cell per map tile.
 */
public class Level {
    public static class Spawn {
        public float x, y, phase;
        public Enemy.Type type;
        public int group, groupSize;
        public boolean carrier;
        /** Walkers: walks upside down on the ceiling / runs in from behind (left edge). */
        public boolean ceiling, behind;
        /** Celtic Frost: flies the Z mirrored (starts in the upper half); seconds it waits behind the one in front. */
        public boolean upper;
        public float delay;
        /** Spawns when the camera's right edge reaches this x (spawns are sorted by it). */
        public float trigger;
    }

    /** A background picture from an image layer (the stage art), bottom-left corner in world coordinates. */
    public static class Art { public String image; public float x, y, w, h; }

    /** Stage 1 teeth: position when fully out, size, picture and timing. */
    public static class Tooth { public float x, y, w, h, delay, period; public boolean ceiling; public String image; }

    /** Floating shootable rock: bottom-left corner, size, picture. */
    public static class Bamda { public float x, y, w, h; public String image; }

    /** Piston crusher: left x, the surface it is mounted on (ceiling underside or floor top), motion. */
    public static class Crusher { public float x, baseY, phase, minLen, maxLen, speed; public boolean ceiling; }

    private static final int W = SalamanderGame.W, H = SalamanderGame.H;

    public final int index;
    public String name = "", music, bossMusic = "boss.mid", background;
    public float baseSpeed = 46f;
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
    public final ArrayList<float[]> volcanoes = new ArrayList<>();   // {centre x, floor y}
    public final ArrayList<Crusher> crushers = new ArrayList<>();
    /** Stage 1 claw arms: bottom-left of the resting picture (64x40), hanging from the ceiling or standing on the floor. */
    public static class Arm { public float x, y; public boolean ceiling; }
    public final ArrayList<Arm> arms = new ArrayList<>();
    /** Stage 1 green-tipped towers: where the picture is when fully grown, and whether it hangs from the ceiling. */
    public static class Tower { public float x, y, w, h; public boolean ceiling, flip; public String image; }
    public final ArrayList<Tower> towers = new ArrayList<>();

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
        l.baseSpeed = Float.parseFloat(m.props.getOrDefault("speed", "46"));
        l.laneH = Float.parseFloat(m.props.getOrDefault("lane", String.valueOf(SalamanderGame.LANE_H)));
        l.music = m.props.get("music");
        l.bossMusic = m.props.getOrDefault("bossMusic", "boss.mid");
        l.background = m.props.get("background");

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
                boolean flippedV = (o.gid & TiledMap.FLIP_V) != 0;

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
                    case "fan":
                    case "rusher": {
                        boolean fan = type.equals("fan");
                        int n = p.prop("count", fan ? 5 : 3);
                        float phase = p.prop("phase", (o.id * 2.39996f) % 6.2832f);
                        for (int i = 0; i < n; i++) {
                            Spawn s = spawn(fan ? Enemy.Type.FAN : Enemy.Type.RUSHER, left + i * (fan ? 18f : 40f),
                                    bottom + h / 2f, group, n, p.prop("drop", true));
                            s.phase = phase;
                        }
                        group++;
                        break;
                    }
                    case "rugal":                                // Rugal: enters at the right edge, at this height
                        spawn(Enemy.Type.RUGAL, left, bottom + h / 2f, group++, 1, p.prop("drop", false));
                        break;
                    case "link":                                 // Missing Link: a single enemy (y = the middle of its bob)
                        spawn(Enemy.Type.LINK, left, bottom + h / 2f, group++, 1, p.prop("drop", false)).phase = (o.id * 2.39996f) % 6.2832f;
                        break;
                    case "frost": {                              // Celtic Frost: a group flying a "Z" across the screen
                        int n = p.prop("count", 6);
                        boolean upper = flippedV || p.prop("upper", false);
                        for (int i = 0; i < n; i++) {
                            Spawn s = spawn(Enemy.Type.FROST, left, bottom + h / 2f, group, n, p.prop("drop", true));   // y: entry line
                            s.upper = upper;
                            s.delay = i * Enemy.FROST_GAP;
                        }
                        group++;
                        break;
                    }
                    case "walker": {
                        boolean ceiling = p.prop("ceiling", flippedV);
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
                    case "bamda": case "asteroid": {           // ("asteroid" = its old name)
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
                        t.image = tile != null ? tile.image : null;
                        if (t.image != null) teeth.add(t);
                        break;
                    }
                    case "rock": rocks.add(new float[]{left, bottom}); break;
                    case "volcano": volcanoes.add(new float[]{left + w / 2f, bottom}); break;
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
                    case "crusher": {
                        Crusher c = new Crusher();
                        c.x = left;
                        c.ceiling = p.prop("ceiling", true);
                        c.baseY = c.ceiling ? top : bottom;
                        c.phase = p.prop("phase", 0f);
                        c.minLen = p.prop("minLen", 12f);
                        c.maxLen = p.prop("maxLen", 102f);
                        c.speed = p.prop("speed", 1.6f);
                        crushers.add(c);
                        break;
                    }
                    case "checkpoint": cpPoints.add(new float[]{o.x + w / 2f, worldH - o.y - (o.gid != 0 ? -h / 2f : h / 2f)}); break;
                    default: break;   // anything else is a note for the level designer
                }
            }
        }
        if (path == null) {   // no path drawn: scroll straight along the middle of the map
            setPath(new float[][]{{0f, Math.max(0f, (worldH - H) / 2f), 1f}, {Math.max(0f, worldW - W), Math.max(0f, (worldH - H) / 2f), 0f}});
        }
        ArrayList<Float> cps = new ArrayList<>();
        cps.add(0f);                                  // the start is always a checkpoint
        for (float[] pt : cpPoints) {
            float t = project(pt[0] - W / 2f, pt[1] - H / 2f);
            if (t > 1f) cps.add(t);
        }
        cps.sort(Float::compare);
        checkpoints = new float[cps.size()];
        for (int i = 0; i < cps.size(); i++) checkpoints[i] = cps.get(i);
        spawns.sort((a, b) -> Float.compare(a.trigger, b.trigger));
    }

    private Spawn spawn(Enemy.Type t, float x, float y, int group, int size, boolean carrier) {
        Spawn s = new Spawn();
        s.type = t; s.x = x; s.y = y; s.group = group; s.groupSize = size; s.carrier = carrier; s.trigger = x;
        spawns.add(s);
        return s;
    }

    /** The polyline marks the centre of the screen; property "speeds" = multiplier per segment, e.g. "1,0.8,1". */
    private void readPath(TiledMap.MapObject o) {
        int n = o.polyline.length / 2;
        String[] sp = o.prop("speeds", "").split(",");
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

    /** A cell that can be walked on / hung from: only real map cells (above and below the map is open space). */
    private boolean groundCell(int c, int r) { return r >= 0 && r < gRows && solidCell(c, r); }

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
    public boolean hits(float x, float y, float w, float h) {
        int c0 = (int) Math.floor(x / cell), c1 = (int) Math.floor((x + w - 0.001f) / cell);
        int r0 = (int) Math.floor(y / cell), r1 = (int) Math.floor((y + h - 0.001f) / cell);
        for (int r = r0; r <= r1; r++)
            for (int c = c0; c <= c1; c++)
                if (solidCell(c, r)) return true;
        return false;
    }

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
                if (bricks[i] || brickGrow[i] > 0f) { bricks[i] = false; brickGrow[i] = 0f; brickRegrow[i] = BRICK_REGROW_TIME; n++; }
            }
        return n;
    }
}
