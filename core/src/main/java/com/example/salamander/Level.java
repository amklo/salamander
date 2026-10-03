package com.example.salamander;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Random;

/**
 * A level is a strip of 16px columns, each with a floor height and a ceiling height (in tiles).
 * Terrain is assembled from segments (open, hills, tunnel, peak, crusher tunnel); hazards and
 * enemy waves are then generated on top of it. Tweak the segment lists in l1()/l2()/l3() to
 * redesign a stage.
 */
public class Level {
    public static final int ROWS = SalamanderGame.H / SalamanderGame.T;   // 17
    private static final String[] NAMES = {"CAVERN RUN", "MAGMA RIDGE", "IRON CITADEL"};
    private static final float[] SPEEDS = {46f, 52f, 56f};

    public static class Spawn {
        public float x, y, phase;
        public Enemy.Type type;
        public int group, groupSize;
        public boolean carrier;
    }

    public final int index;
    public final String name;
    public final float baseSpeed;
    public int cols;
    public int[] floor, ceil;
    public float[] speedMul;
    public float stopX;                 // scrolling stops here; the boss arena starts
    public float[] checkpoints;
    public final ArrayList<Spawn> spawns = new ArrayList<>();
    public final ArrayList<float[]> rocks = new ArrayList<>();       // {x}
    public final ArrayList<float[]> volcanoes = new ArrayList<>();   // {x}
    public final ArrayList<float[]> crushers = new ArrayList<>();    // {x, fromCeil, phase, minLen, maxLen, speed}

    private final ArrayList<Integer> fl = new ArrayList<>(), ce = new ArrayList<>();
    private final ArrayList<Float> sm = new ArrayList<>();
    private int minGap = 7;
    private float curMul = 1f;

    public static Level build(int idx) {
        Level l = new Level(idx);
        switch (idx) {
            case 0: l.l1(); break;
            case 1: l.l2(); break;
            default: l.l3(); break;
        }
        l.finish();
        return l;
    }

    private Level(int idx) {
        index = idx;
        name = NAMES[idx];
        baseSpeed = SPEEDS[idx];
    }

    // ---------------------------------------------------------------- terrain queries

    private int colAt(float wx) {
        int c = (int) Math.floor(wx / SalamanderGame.T);
        return c < 0 ? 0 : (c >= cols ? cols - 1 : c);
    }

    public float floorTop(float wx) { return floor[colAt(wx)] * (float) SalamanderGame.T; }

    public float ceilBottom(float wx) { return SalamanderGame.H - ceil[colAt(wx)] * (float) SalamanderGame.T; }

    public float speedAt(float wx) { return speedMul[colAt(wx)]; }

    /** True if the rectangle touches floor or ceiling terrain. */
    public boolean hits(float x, float y, float w, float h) {
        int c0 = colAt(x), c1 = colAt(x + w);
        for (int c = c0; c <= c1; c++) {
            if (floor[c] * 16f > y || SalamanderGame.H - ceil[c] * 16f < y + h) return true;
        }
        return false;
    }

    /** Open vertical range {lo, hi} (pixels) that is free across columns c0..c1. */
    private float[] openRange(int c0, int c1) {
        c0 = Math.max(0, c0);
        c1 = Math.min(cols - 1, c1);
        int f = 0, c = 0;
        for (int i = c0; i <= c1; i++) { f = Math.max(f, floor[i]); c = Math.max(c, ceil[i]); }
        return new float[]{f * 16f, SalamanderGame.H - c * 16f};
    }

    // ---------------------------------------------------------------- segment builders

    private void col(int tf, int tc) {
        if (!fl.isEmpty()) {   // limit slope to one tile per column so terrain stays flyable
            int pf = fl.get(fl.size() - 1), pc = ce.get(ce.size() - 1);
            tf = Math.max(pf - 1, Math.min(pf + 1, tf));
            tc = Math.max(pc - 1, Math.min(pc + 1, tc));
        }
        tf = Math.max(0, tf);
        tc = Math.max(0, tc);
        int limit = ROWS - minGap;
        while (tf + tc > limit) { if (tf >= tc) tf--; else tc--; }
        fl.add(tf); ce.add(tc); sm.add(curMul);
    }

    private void open(int len, int f, int c) {
        minGap = 7;
        for (int i = 0; i < len; i++) col(f, c);
    }

    private void hills(int len, int fb, int fa, int cb, int ca, int period, double ph) {
        minGap = 7;
        for (int i = 0; i < len; i++) {
            double a = 2 * Math.PI * i / period;
            col((int) Math.round(fb + fa * Math.sin(a + ph)),
                (int) Math.round(cb + ca * Math.sin(a * 0.7 + ph + 1.3)));
        }
    }

    private void tunnel(int len, int gap, int amp, int period, float mul) {
        minGap = gap;
        curMul = mul;
        double mid = ROWS / 2.0;
        for (int i = 0; i < len; i++) {
            double c = mid + amp * Math.sin(2 * Math.PI * i / period);
            col((int) Math.round(c - gap / 2.0), (int) Math.round(ROWS - (c + gap / 2.0)));
        }
        curMul = 1f;
        minGap = 7;
    }

    private void peak(int len, int base, int height, boolean volcano) {
        minGap = 7;
        int start = fl.size();
        for (int i = 0; i < len; i++) {
            double tri = 1.0 - Math.abs(2.0 * i / (len - 1) - 1.0);
            col(base + (int) Math.round(height * Math.min(1.0, tri * 1.25)), 1);
        }
        if (volcano) volcanoes.add(new float[]{(start + len / 2) * 16f + 8f});
    }

    private void crushTunnel(int len, int gap, int spacing, float mul) {
        minGap = gap;
        curMul = mul;
        int f = (ROWS - gap) / 2, c = ROWS - gap - f;
        int start = fl.size();
        for (int i = 0; i < len; i++) col(f, c);
        int k = 0;
        for (int i = 5; i < len - 4; i += spacing) {
            crushers.add(new float[]{(start + i) * 16f, k % 2 == 0 ? 1f : 0f, k * 1.7f, 12f, gap * 16f - 26f, 1.6f});
            k++;
        }
        curMul = 1f;
        minGap = 7;
    }

    private void rocks(int fromCol, int toCol, int spacing) {
        for (int c = fromCol + 3; c < toCol - 2; c += spacing) rocks.add(new float[]{c * 16f});
    }

    // ---------------------------------------------------------------- the three stages

    private void l1() {
        open(24, 1, 1);
        hills(40, 2, 2, 1, 1, 28, 0);
        int s = fl.size(); tunnel(28, 9, 1, 22, 1f); rocks(s, fl.size(), 6);
        peak(26, 1, 6, false);
        hills(36, 3, 2, 2, 2, 22, 1.0);
        s = fl.size(); tunnel(34, 7, 2, 17, 1f); rocks(s, fl.size(), 5);
        open(16, 2, 2);
        hills(32, 2, 3, 2, 2, 18, 0.5);
        peak(24, 2, 5, false);
        open(14, 1, 1);
    }

    private void l2() {
        open(20, 1, 2);
        peak(28, 1, 7, true);
        hills(28, 3, 3, 1, 1, 16, 0);
        peak(30, 2, 7, true);
        peak(26, 1, 6, true);
        int s = fl.size(); tunnel(28, 8, 2, 14, 1f); rocks(s, fl.size(), 5);
        hills(26, 3, 3, 2, 2, 12, 0.4);
        peak(30, 2, 6, true);
        hills(24, 3, 3, 2, 2, 12, 0.7);
        peak(34, 1, 8, true);
        open(14, 1, 1);
    }

    private void l3() {
        open(20, 2, 2);
        tunnel(30, 7, 2, 16, 1.25f);
        open(8, 4, 5);
        crushTunnel(48, 8, 10, 1f);
        open(8, 4, 5);
        tunnel(36, 6, 2, 12, 1.3f);
        open(8, 5, 5);
        crushTunnel(40, 7, 9, 1f);
        open(10, 2, 2);
        hills(30, 3, 3, 3, 3, 14, 0);
        int s = fl.size(); tunnel(32, 6, 3, 10, 1.35f); rocks(s, fl.size(), 6);
        open(16, 1, 1);
    }

    // ---------------------------------------------------------------- finishing: arena, checkpoints, waves

    private void finish() {
        open(10, 1, 1);
        int arenaStart = fl.size();
        open(40, 1, 1);          // flat boss arena
        cols = fl.size();
        floor = new int[cols]; ceil = new int[cols]; speedMul = new float[cols];
        for (int i = 0; i < cols; i++) { floor[i] = fl.get(i); ceil[i] = ce.get(i); speedMul[i] = sm.get(i); }
        stopX = arenaStart * 16f;
        checkpoints = new float[]{0f, stopX * 0.28f, stopX * 0.52f, stopX * 0.76f, stopX - 640f};
        genSpawns();
    }

    private int nearestFlat(int col, boolean onFloor) {
        for (int d = 0; d < 14; d++) {
            for (int s = -1; s <= 1; s += 2) {
                int c = col + d * s;
                if (c < 2 || c >= cols - 2) continue;
                int[] a = onFloor ? floor : ceil;
                if (a[c] == a[c - 1] && a[c] == a[c + 1]) return c;
            }
        }
        return Math.max(2, Math.min(cols - 3, col));
    }

    private void genSpawns() {
        Random r = new Random(77 + index * 31L);
        int[][] weights = {{6, 3, 2, 3}, {4, 2, 3, 4}, {4, 2, 3, 4}};   // FAN, WALKER, TURRET, RUSHER
        int[] w = weights[index];
        int total = w[0] + w[1] + w[2] + w[3];
        float x = 560f;
        int gid = 1, wave = 0;
        while (x < stopX - 420f) {
            int col = Math.min(cols - 1, (int) ((x + SalamanderGame.W) / 16f));
            float[] gap = openRange(col - 6, col + 24);
            float lo = gap[0], hi = gap[1], span = hi - lo;
            float mid = (lo + hi) / 2f;
            int pick = r.nextInt(total), kind = 0;
            while (pick >= w[kind]) { pick -= w[kind]; kind++; }
            boolean forced = wave++ % 3 == 0;               // turrets: a steady share carry capsules

            if (kind == 0) {                                   // sine-wave fan formation
                flyingWave(r, x + 520f, false, lo, hi, gid++);
            } else if (kind == 3) {                            // rushers
                flyingWave(r, x + 520f, true, lo, hi, gid++);
            } else {                                           // ground / ceiling emplacement
                addGround(r, x + 520f, kind == 1, kind == 2 && r.nextBoolean(), gid++, forced || r.nextInt(4) == 0);
                // ...always escorted by a flying wave
                flyingWave(r, x + 520f + 110f, r.nextInt(3) == 0, lo, hi, gid++);
            }
            // companion emplacement a little further on, so there is always something on the terrain
            if ((kind == 0 || kind == 3) && r.nextInt(3) != 0) {
                addGround(r, x + 520f + 230f, r.nextBoolean(), r.nextBoolean(), gid++, r.nextInt(4) == 0);
            }
            x += (index == 2 ? 200f : 240f) + r.nextInt(160);
        }
        Collections.sort(spawns, (p, q) -> Float.compare(p.x, q.x));
    }

    /** A flying wave (5 sine-wave fans or 3 rushers). Destroying the whole wave always drops a capsule. */
    private void flyingWave(Random r, float worldX, boolean rushers, float lo, float hi, int gid) {
        float span = hi - lo, mid = (lo + hi) / 2f;
        if (rushers) {
            for (int i = 0; i < 3; i++) {
                float y = lo + 12f + r.nextFloat() * Math.max(1f, hi - lo - 40f);
                spawn(Enemy.Type.RUSHER, worldX + i * 40f, y, gid, 3, true);
            }
        } else {
            float y = clamp(mid + (r.nextFloat() - 0.5f) * 50f, lo + Math.min(40f, span * 0.3f), hi - Math.min(56f, span * 0.4f));
            float ph = r.nextFloat() * 6f;
            for (int i = 0; i < 5; i++) {
                Spawn s = spawn(Enemy.Type.FAN, worldX + i * 18f, y, gid, 5, true);
                s.phase = ph;
            }
        }
    }

    private void addGround(Random r, float worldX, boolean walker, boolean ceilTurret, int gid, boolean carrier) {
        int c = nearestFlat((int) (worldX / 16f), !ceilTurret || walker);
        Enemy.Type t = walker ? Enemy.Type.WALKER : (ceilTurret ? Enemy.Type.TURRET_CEIL : Enemy.Type.TURRET_FLOOR);
        spawn(t, c * 16f, 0f, gid, 1, carrier || walker);   // walkers always drop a capsule
    }

    private Spawn spawn(Enemy.Type t, float x, float y, int gid, int size, boolean carrier) {
        Spawn s = new Spawn();
        s.type = t; s.x = x; s.y = y; s.group = gid; s.groupSize = size; s.carrier = carrier;
        spawns.add(s);
        return s;
    }

    private static float clamp(float v, float lo, float hi) { return Math.max(lo, Math.min(hi, v)); }
}
