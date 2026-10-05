package com.example.salamander;

import com.badlogic.gdx.utils.XmlReader;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.function.Function;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

/**
 * A small reader for maps saved by the Tiled editor (.tmx with external .tsx tilesets).
 * It only reads what the game needs: tile layers (CSV or Base64, optionally zlib/gzip compressed),
 * image layers, object layers (tile objects, points, rectangles, polylines) and custom properties.
 * Coordinates are kept as Tiled stores them (pixels, y pointing down); Level converts them.
 * Does not need a graphics context, so it also works in tools and tests.
 */
public final class TiledMap {
    public static final int FLIP_H = 0x80000000, FLIP_V = 0x40000000, FLIP_D = 0x20000000;
    public static final int GID_MASK = 0x1fffffff;

    public int width, height, tileWidth, tileHeight;
    public final HashMap<String, String> props = new HashMap<>();
    public final ArrayList<Tileset> tilesets = new ArrayList<>();
    public final ArrayList<TileLayer> tileLayers = new ArrayList<>();
    public final ArrayList<ImageLayer> imageLayers = new ArrayList<>();
    public final ArrayList<ObjectLayer> objectLayers = new ArrayList<>();

    public static class Tileset {
        public int firstGid, tileWidth, tileHeight, tileCount, columns;
        public String name, image;                 // image: path relative to the assets folder (single-image tilesets)
        public final HashMap<Integer, Tile> tiles = new HashMap<>();
    }

    public static class Tile {
        public String type = "", image;            // image: per-tile picture (image-collection tilesets)
        public int width, height;
        public final HashMap<String, String> props = new HashMap<>();
    }

    public static class TileLayer {
        public String name;
        public boolean visible = true;
        public int[] gids;                         // row-major, row 0 = top (as in Tiled), flip bits included
    }

    public static class ImageLayer {
        public String name, image;
        public float offsetX, offsetY;
        public int width, height;                  // picture size as saved by Tiled
        public boolean visible = true;
    }

    public static class ObjectLayer {
        public String name;
        public final ArrayList<MapObject> objects = new ArrayList<>();
    }

    public static class MapObject {
        public int id, gid;                        // gid != 0: tile object (x, y = bottom-left corner)
        public String name = "", type = "";
        public float x, y, width, height;
        public boolean point;
        public float[] polyline;                   // relative points x0,y0,x1,y1,...
        public final HashMap<String, String> props = new HashMap<>();

        public String prop(String key, String def) { String v = props.get(key); return v == null ? def : v; }
        public float prop(String key, float def) { String v = props.get(key); return v == null ? def : Float.parseFloat(v); }
        public int prop(String key, int def) { String v = props.get(key); return v == null ? def : Math.round(Float.parseFloat(v)); }
        public boolean prop(String key, boolean def) { String v = props.get(key); return v == null ? def : Boolean.parseBoolean(v); }
    }

    /**
     * @param path   map path relative to the assets folder, e.g. "maps/stage1.tmx"
     * @param reader reads a file relative to the assets folder
     */
    public static TiledMap load(String path, Function<String, byte[]> reader) {
        TiledMap m = new TiledMap();
        XmlReader.Element root = new XmlReader().parse(new String(reader.apply(path), StandardCharsets.UTF_8));
        String dir = parent(path);
        m.width = root.getIntAttribute("width");
        m.height = root.getIntAttribute("height");
        m.tileWidth = root.getIntAttribute("tilewidth");
        m.tileHeight = root.getIntAttribute("tileheight");
        if (root.getIntAttribute("infinite", 0) != 0)
            throw new IllegalArgumentException(path + ": infinite maps are not supported (Map > Map Properties > Infinite: off)");
        readProps(root, m.props);

        for (int i = 0; i < root.getChildCount(); i++) {
            XmlReader.Element e = root.getChild(i);
            switch (e.getName()) {
                case "tileset": m.tilesets.add(readTileset(e, dir, reader)); break;
                case "layer": m.tileLayers.add(readTileLayer(e, m.width, m.height)); break;
                case "imagelayer": {
                    ImageLayer l = new ImageLayer();
                    l.name = e.getAttribute("name", "");
                    l.offsetX = e.getFloatAttribute("offsetx", 0f);
                    l.offsetY = e.getFloatAttribute("offsety", 0f);
                    l.visible = e.getIntAttribute("visible", 1) != 0;
                    XmlReader.Element img = e.getChildByName("image");
                    if (img != null) {
                        l.image = resolve(dir, img.getAttribute("source"));
                        l.width = img.getIntAttribute("width", 0);
                        l.height = img.getIntAttribute("height", 0);
                        m.imageLayers.add(l);
                    }
                    break;
                }
                case "objectgroup": m.objectLayers.add(readObjectLayer(e)); break;
                case "group":
                    throw new IllegalArgumentException(path + ": layer groups are not supported, keep layers at the top level");
                default: break;
            }
        }
        return m;
    }

    // ---------------------------------------------------------------- lookups

    /** Tileset that owns this gid (flip bits are ignored). */
    public Tileset tilesetFor(int gid) {
        gid &= GID_MASK;
        Tileset best = null;
        for (Tileset t : tilesets) if (t.firstGid <= gid && (best == null || t.firstGid > best.firstGid)) best = t;
        return best;
    }

    /** Tile info for a gid (null if the tileset defines nothing special for it). */
    public Tile tile(int gid) {
        Tileset t = tilesetFor(gid);
        return t == null ? null : t.tiles.get((gid & GID_MASK) - t.firstGid);
    }

    public TileLayer tileLayer(String name) {
        for (TileLayer l : tileLayers) if (l.name.equalsIgnoreCase(name)) return l;
        return null;
    }

    public ObjectLayer objectLayer(String name) {
        for (ObjectLayer l : objectLayers) if (l.name.equalsIgnoreCase(name)) return l;
        return null;
    }

    // ---------------------------------------------------------------- parsing

    private static Tileset readTileset(XmlReader.Element e, String dir, Function<String, byte[]> reader) {
        Tileset t = new Tileset();
        t.firstGid = e.getIntAttribute("firstgid");
        String source = e.getAttribute("source", null);
        if (source != null) {                       // external .tsx
            String tsxPath = resolve(dir, source);
            e = new XmlReader().parse(new String(reader.apply(tsxPath), StandardCharsets.UTF_8));
            dir = parent(tsxPath);
        }
        t.name = e.getAttribute("name", "");
        t.tileWidth = e.getIntAttribute("tilewidth", 0);
        t.tileHeight = e.getIntAttribute("tileheight", 0);
        t.tileCount = e.getIntAttribute("tilecount", 0);
        t.columns = e.getIntAttribute("columns", 0);
        XmlReader.Element img = e.getChildByName("image");
        if (img != null) t.image = resolve(dir, img.getAttribute("source"));
        for (XmlReader.Element te : e.getChildrenByName("tile")) {
            Tile tile = new Tile();
            tile.type = te.getAttribute("class", te.getAttribute("type", ""));
            XmlReader.Element ti = te.getChildByName("image");
            if (ti != null) {
                tile.image = resolve(dir, ti.getAttribute("source"));
                tile.width = ti.getIntAttribute("width", 0);
                tile.height = ti.getIntAttribute("height", 0);
            }
            readProps(te, tile.props);
            t.tiles.put(te.getIntAttribute("id"), tile);
        }
        return t;
    }

    private static TileLayer readTileLayer(XmlReader.Element e, int w, int h) {
        TileLayer l = new TileLayer();
        l.name = e.getAttribute("name", "");
        l.visible = e.getIntAttribute("visible", 1) != 0;
        l.gids = new int[w * h];
        XmlReader.Element data = e.getChildByName("data");
        if (data == null) return l;
        if (data.getChildByName("chunk") != null)
            throw new IllegalArgumentException("layer '" + l.name + "': chunked (infinite) layers are not supported");
        String enc = data.getAttribute("encoding", "");
        String text = data.getText() == null ? "" : data.getText().trim();
        if (enc.equals("csv")) {
            String[] parts = text.split("[,\\s]+");
            for (int i = 0; i < parts.length && i < l.gids.length; i++)
                if (!parts[i].isEmpty()) l.gids[i] = (int) Long.parseLong(parts[i]);
        } else if (enc.equals("base64")) {
            byte[] bytes = Base64.getDecoder().decode(text.replaceAll("\\s", ""));
            String comp = data.getAttribute("compression", "");
            try {
                if (comp.equals("zlib")) bytes = readAll(new InflaterInputStream(new ByteArrayInputStream(bytes)));
                else if (comp.equals("gzip")) bytes = readAll(new GZIPInputStream(new ByteArrayInputStream(bytes)));
                else if (!comp.isEmpty())
                    throw new IllegalArgumentException("layer '" + l.name + "': compression '" + comp + "' is not supported (use CSV, or Base64 with zlib/gzip)");
            } catch (java.io.IOException ex) {
                throw new RuntimeException(ex);
            }
            for (int i = 0; i < l.gids.length && i * 4 + 3 < bytes.length; i++) {
                l.gids[i] = (bytes[i * 4] & 0xff) | (bytes[i * 4 + 1] & 0xff) << 8
                        | (bytes[i * 4 + 2] & 0xff) << 16 | (bytes[i * 4 + 3] & 0xff) << 24;
            }
        } else {   // old XML format: <tile gid=".."/>
            int i = 0;
            for (XmlReader.Element t : data.getChildrenByName("tile")) {
                if (i < l.gids.length) l.gids[i] = (int) Long.parseLong(t.getAttribute("gid", "0"));
                i++;
            }
        }
        return l;
    }

    private static ObjectLayer readObjectLayer(XmlReader.Element e) {
        ObjectLayer l = new ObjectLayer();
        l.name = e.getAttribute("name", "");
        for (XmlReader.Element oe : e.getChildrenByName("object")) {
            MapObject o = new MapObject();
            o.id = oe.getIntAttribute("id", 0);
            o.gid = (int) Long.parseLong(oe.getAttribute("gid", "0"));
            o.name = oe.getAttribute("name", "");
            o.type = oe.getAttribute("class", oe.getAttribute("type", ""));
            o.x = oe.getFloatAttribute("x", 0f);
            o.y = oe.getFloatAttribute("y", 0f);
            o.width = oe.getFloatAttribute("width", 0f);
            o.height = oe.getFloatAttribute("height", 0f);
            o.point = oe.getChildByName("point") != null;
            XmlReader.Element poly = oe.getChildByName("polyline");
            if (poly != null) {
                String[] pts = poly.getAttribute("points").trim().split("\\s+");
                o.polyline = new float[pts.length * 2];
                for (int i = 0; i < pts.length; i++) {
                    String[] xy = pts[i].split(",");
                    o.polyline[i * 2] = Float.parseFloat(xy[0]);
                    o.polyline[i * 2 + 1] = Float.parseFloat(xy[1]);
                }
            }
            readProps(oe, o.props);
            l.objects.add(o);
        }
        return l;
    }

    private static void readProps(XmlReader.Element e, HashMap<String, String> into) {
        XmlReader.Element ps = e.getChildByName("properties");
        if (ps == null) return;
        for (XmlReader.Element p : ps.getChildrenByName("property")) {
            String v = p.getAttribute("value", null);
            if (v == null) v = p.getText() == null ? "" : p.getText();
            into.put(p.getAttribute("name"), v);
        }
    }

    private static byte[] readAll(InputStream in) throws java.io.IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        return out.toByteArray();
    }

    private static String parent(String path) {
        int i = path.lastIndexOf('/');
        return i < 0 ? "" : path.substring(0, i);
    }

    /** Joins a relative path onto a folder and folds "../" parts (paths stay relative to the assets folder). */
    static String resolve(String dir, String rel) {
        rel = rel.replace('\\', '/');
        ArrayList<String> parts = new ArrayList<>();
        if (!dir.isEmpty()) for (String s : dir.split("/")) parts.add(s);
        for (String s : rel.split("/")) {
            if (s.equals("..")) { if (!parts.isEmpty()) parts.remove(parts.size() - 1); }
            else if (!s.equals(".") && !s.isEmpty()) parts.add(s);
        }
        return String.join("/", parts);
    }
}
