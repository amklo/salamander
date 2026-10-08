package com.example.salamander;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Align;

import java.util.HashMap;

/**
 * Pixel fonts drawn from a 1x1 white texture, so text stays crisp at the 256x192 MSX resolution.
 * SMALL = 5x5 capitals (6px advance), NORMAL = 5x7 (6px advance), BOLD = MSX-style 7x7 (8px advance:
 * the digits and P are copied from the original game's HUD, letters are NORMAL drawn twice, 1px apart).
 * Lower-case letters are drawn as capitals.
 */
public final class PixelFont {
    public static final int SMALL = 0, NORMAL = 1, BOLD = 2;
    private static final int[] ADVANCE = {6, 6, 8}, HEIGHT = {5, 7, 7};

    private static final HashMap<Character, String[]> SMALL_G = new HashMap<>(), NORMAL_G = new HashMap<>(), BOLD_G = new HashMap<>();

    private PixelFont() {}

    public static int height(int style) { return HEIGHT[style]; }

    /** Width in pixels of s at the given scale (no trailing gap). */
    public static float width(String s, int style, int scale) {
        return s.isEmpty() ? 0f : (s.length() * ADVANCE[style] - (ADVANCE[style] - glyphW(style))) * scale;
    }

    private static int glyphW(int style) { return style == BOLD ? 7 : 5; }

    /**
     * Draws s with its bottom-left at (x, y); align = Align.left / center / right within width w.
     */
    public static void draw(SpriteBatch b, TextureRegion pix, String s, float x, float y, float w, int align,
                            int style, int scale, Color c) {
        float tw = width(s, style, scale);
        if ((align & Align.center) != 0 && (align & (Align.left | Align.right)) == 0) x += (w - tw) / 2f;
        else if ((align & Align.right) != 0) x += w - tw;
        x = Math.round(x);
        y = Math.round(y);
        b.setColor(c);
        int h = HEIGHT[style];
        for (int i = 0; i < s.length(); i++) {
            String[] g = glyph(Character.toUpperCase(s.charAt(i)), style);
            if (g != null) {
                for (int row = 0; row < h; row++) {
                    String r = g[row];
                    float gy = y + (h - 1 - row) * scale;
                    int col = 0;
                    while (col < r.length()) {          // one rectangle per horizontal run of pixels
                        if (r.charAt(col) != '#') { col++; continue; }
                        int start = col;
                        while (col < r.length() && r.charAt(col) == '#') col++;
                        b.draw(pix, x + start * scale, gy, (col - start) * scale, scale);
                    }
                }
            }
            x += ADVANCE[style] * scale;
        }
        b.setColor(Color.WHITE);
    }

    /** Same as draw(), with a 1px black shadow below-right, for text over the playfield. */
    public static void drawShadow(SpriteBatch b, TextureRegion pix, String s, float x, float y, float w, int align,
                                  int style, int scale, Color c) {
        draw(b, pix, s, x + scale, y - scale, w, align, style, scale, Color.BLACK);
        draw(b, pix, s, x, y, w, align, style, scale, c);
    }

    private static String[] glyph(char ch, int style) {
        if (style == SMALL) return SMALL_G.get(ch);
        if (style == NORMAL) return NORMAL_G.get(ch);
        String[] g = BOLD_G.get(ch);
        if (g != null) return g;
        String[] n = NORMAL_G.get(ch);             // bold letters: the 5x7 glyph drawn twice, 1px apart
        if (n == null) return null;
        g = new String[7];
        for (int r = 0; r < 7; r++) {
            StringBuilder sb = new StringBuilder();
            String row = "." + n[r] + ".";
            for (int cI = 0; cI < 7; cI++) {
                boolean on = (cI >= 1 && row.charAt(cI) == '#') || (cI >= 1 && row.charAt(cI - 1) == '#' && cI - 1 >= 1);
                sb.append(on ? '#' : '.');
            }
            g[r] = sb.toString();
        }
        BOLD_G.put(ch, g);
        return g;
    }

    private static void put(HashMap<Character, String[]> m, char c, String rows) { m.put(c, rows.split("\\|")); }

    static {
        // ---- 5x7
        HashMap<Character, String[]> n = NORMAL_G;
        put(n, ' ', ".....|.....|.....|.....|.....|.....|.....");
        put(n, 'A', ".###.|#...#|#...#|#####|#...#|#...#|#...#");
        put(n, 'B', "####.|#...#|#...#|####.|#...#|#...#|####.");
        put(n, 'C', ".###.|#...#|#....|#....|#....|#...#|.###.");
        put(n, 'D', "####.|#...#|#...#|#...#|#...#|#...#|####.");
        put(n, 'E', "#####|#....|#....|####.|#....|#....|#####");
        put(n, 'F', "#####|#....|#....|####.|#....|#....|#....");
        put(n, 'G', ".###.|#...#|#....|#.###|#...#|#...#|.####");
        put(n, 'H', "#...#|#...#|#...#|#####|#...#|#...#|#...#");
        put(n, 'I', ".###.|..#..|..#..|..#..|..#..|..#..|.###.");
        put(n, 'J', "..###|...#.|...#.|...#.|...#.|#..#.|.##..");
        put(n, 'K', "#...#|#..#.|#.#..|##...|#.#..|#..#.|#...#");
        put(n, 'L', "#....|#....|#....|#....|#....|#....|#####");
        put(n, 'M', "#...#|##.##|#.#.#|#.#.#|#...#|#...#|#...#");
        put(n, 'N', "#...#|#...#|##..#|#.#.#|#..##|#...#|#...#");
        put(n, 'O', ".###.|#...#|#...#|#...#|#...#|#...#|.###.");
        put(n, 'P', "####.|#...#|#...#|####.|#....|#....|#....");
        put(n, 'Q', ".###.|#...#|#...#|#...#|#.#.#|#..#.|.##.#");
        put(n, 'R', "####.|#...#|#...#|####.|#.#..|#..#.|#...#");
        put(n, 'S', ".####|#....|#....|.###.|....#|....#|####.");
        put(n, 'T', "#####|..#..|..#..|..#..|..#..|..#..|..#..");
        put(n, 'U', "#...#|#...#|#...#|#...#|#...#|#...#|.###.");
        put(n, 'V', "#...#|#...#|#...#|#...#|#...#|.#.#.|..#..");
        put(n, 'W', "#...#|#...#|#...#|#.#.#|#.#.#|#.#.#|.#.#.");
        put(n, 'X', "#...#|#...#|.#.#.|..#..|.#.#.|#...#|#...#");
        put(n, 'Y', "#...#|#...#|.#.#.|..#..|..#..|..#..|..#..");
        put(n, 'Z', "#####|....#|...#.|..#..|.#...|#....|#####");
        put(n, '0', ".###.|#...#|#..##|#.#.#|##..#|#...#|.###.");
        put(n, '1', "..#..|.##..|..#..|..#..|..#..|..#..|.###.");
        put(n, '2', ".###.|#...#|....#|...#.|..#..|.#...|#####");
        put(n, '3', "#####|...#.|..#..|...#.|....#|#...#|.###.");
        put(n, '4', "...#.|..##.|.#.#.|#..#.|#####|...#.|...#.");
        put(n, '5', "#####|#....|####.|....#|....#|#...#|.###.");
        put(n, '6', "..##.|.#...|#....|####.|#...#|#...#|.###.");
        put(n, '7', "#####|....#|...#.|..#..|.#...|.#...|.#...");
        put(n, '8', ".###.|#...#|#...#|.###.|#...#|#...#|.###.");
        put(n, '9', ".###.|#...#|#...#|.####|....#|...#.|.##..");
        put(n, '!', "..#..|..#..|..#..|..#..|..#..|.....|..#..");
        put(n, '-', ".....|.....|.....|#####|.....|.....|.....");
        put(n, '.', ".....|.....|.....|.....|.....|.##..|.##..");
        put(n, ':', ".....|.##..|.##..|.....|.##..|.##..|.....");
        put(n, '/', "....#|....#|...#.|..#..|.#...|#....|#....");
        put(n, '<', "...#.|..#..|.#...|#....|.#...|..#..|...#.");
        put(n, '>', ".#...|..#..|...#.|....#|...#.|..#..|.#...");
        put(n, '?', ".###.|#...#|....#|...#.|..#..|.....|..#..");
        put(n, '%', "##...|##..#|...#.|..#..|.#...|#..##|...##");
        put(n, '+', ".....|..#..|..#..|#####|..#..|..#..|.....");
        put(n, ',', ".....|.....|.....|.....|.##..|..#..|.#...");
        put(n, '\'', "..#..|..#..|.#...|.....|.....|.....|.....");
        put(n, '(', "...#.|..#..|.#...|.#...|.#...|..#..|...#.");
        put(n, ')', ".#...|..#..|...#.|...#.|...#.|..#..|.#...");
        put(n, '=', ".....|.....|#####|.....|#####|.....|.....");

        // ---- 5x5
        HashMap<Character, String[]> s = SMALL_G;
        put(s, ' ', ".....|.....|.....|.....|.....");
        put(s, 'A', ".###.|#...#|#####|#...#|#...#");
        put(s, 'B', "####.|#...#|####.|#...#|####.");
        put(s, 'C', ".####|#....|#....|#....|.####");
        put(s, 'D', "####.|#...#|#...#|#...#|####.");
        put(s, 'E', "#####|#....|####.|#....|#####");
        put(s, 'F', "#####|#....|####.|#....|#....");
        put(s, 'G', ".####|#....|#..##|#...#|.####");
        put(s, 'H', "#...#|#...#|#####|#...#|#...#");
        put(s, 'I', "#####|..#..|..#..|..#..|#####");
        put(s, 'J', "....#|....#|....#|#...#|.###.");
        put(s, 'K', "#...#|#..#.|###..|#..#.|#...#");
        put(s, 'L', "#....|#....|#....|#....|#####");
        put(s, 'M', "#...#|##.##|#.#.#|#...#|#...#");
        put(s, 'N', "#...#|##..#|#.#.#|#..##|#...#");
        put(s, 'O', ".###.|#...#|#...#|#...#|.###.");
        put(s, 'P', "####.|#...#|####.|#....|#....");
        put(s, 'Q', ".###.|#...#|#.#.#|#..#.|.##.#");
        put(s, 'R', "####.|#...#|####.|#..#.|#...#");
        put(s, 'S', ".####|#....|.###.|....#|####.");
        put(s, 'T', "#####|..#..|..#..|..#..|..#..");
        put(s, 'U', "#...#|#...#|#...#|#...#|.###.");
        put(s, 'V', "#...#|#...#|#...#|.#.#.|..#..");
        put(s, 'W', "#...#|#...#|#.#.#|##.##|#...#");
        put(s, 'X', "#...#|.#.#.|..#..|.#.#.|#...#");
        put(s, 'Y', "#...#|.#.#.|..#..|..#..|..#..");
        put(s, 'Z', "#####|...#.|..#..|.#...|#####");
        put(s, '0', ".###.|#..##|#.#.#|##..#|.###.");
        put(s, '1', ".##..|..#..|..#..|..#..|.###.");
        put(s, '2', "####.|....#|.###.|#....|#####");
        put(s, '3', "####.|....#|.###.|....#|####.");
        put(s, '4', "#..#.|#..#.|#####|...#.|...#.");
        put(s, '5', "#####|#....|####.|....#|####.");
        put(s, '6', ".###.|#....|####.|#...#|.###.");
        put(s, '7', "#####|....#|...#.|..#..|..#..");
        put(s, '8', ".###.|#...#|.###.|#...#|.###.");
        put(s, '9', ".###.|#...#|.####|....#|.###.");
        put(s, '!', "..#..|..#..|..#..|.....|..#..");
        put(s, '-', ".....|.....|.###.|.....|.....");
        put(s, '.', ".....|.....|.....|.....|..#..");
        put(s, ':', ".....|..#..|.....|..#..|.....");
        put(s, '/', "....#|...#.|..#..|.#...|#....");
        put(s, '<', "...#.|..#..|.#...|..#..|...#.");
        put(s, '>', ".#...|..#..|...#.|..#..|.#...");
        put(s, '%', "#...#|...#.|..#..|.#...|#...#");
        put(s, '+', ".....|..#..|.###.|..#..|.....");
        put(s, '?', ".###.|....#|..##.|.....|..#..");
        put(s, ',', ".....|.....|.....|..#..|.#...");
        put(s, '\'', "..#..|..#..|.....|.....|.....");
        put(s, '(', "..#..|.#...|.#...|.#...|..#..");
        put(s, ')', "..#..|...#.|...#.|...#.|..#..");
        put(s, '=', ".....|#####|.....|#####|.....");

        // ---- MSX HUD digits (7x7), copied from the original game's score / lives / stage counters
        HashMap<Character, String[]> d = BOLD_G;
        put(d, ' ', ".......|.......|.......|.......|.......|.......|.......");
        put(d, '0', "..###..|.#...#.|##...##|##...##|##...##|.#...#.|..###..");
        put(d, '1', "..##...|.###...|..##...|..##...|..##...|..##...|######.");
        put(d, '2', ".#####.|##...##|.....##|...###.|.####..|###....|#######");
        put(d, '3', ".#####.|##...##|.....##|...###.|.....##|##...##|.#####.");
        put(d, '4', "...###.|..####.|.##.##.|##..##.|#######|....##.|....##.");
        put(d, '5', "######.|##.....|######.|.....##|.....##|##...##|.#####.");
        put(d, '6', "..####.|.##....|##.....|######.|##...##|##...##|.#####.");
        put(d, '7', "#######|##...##|....##.|...##..|..##...|..##...|..##...");
        put(d, '8', ".#####.|##...##|##...##|.#####.|##...##|##...##|.#####.");
        put(d, '9', ".#####.|##...##|##...##|.######|.....##|....##.|.####..");
        put(d, 'P', "######.|#....#.|#....#.|######.|##.....|##.....|##.....");
    }
}
