package com.example.salamander;

import com.badlogic.gdx.math.MathUtils;

import java.util.ArrayList;

/**
 * A wave of enemies let out of a spawner, a "base" (Albert, 2026-10-30): stage 1's green-tipped towers, the domes
 * (stage 2, Lavinia, the Eioneus / Op 4 blue domes), Kierke's nests and the Eioneus / Op 4 ground bases. Every spawner
 * holds one Wave and asks it, frame by frame, when its next member is due (tick); GameScreen.spawnWaveMember makes the
 * member of whatever kind it is, out of the spawner's mouth, the same way for every spawner (only the stage's
 * orientation and the stage's own flight path make a difference). The capsule rule is the wave's too.
 *
 * Kinds: AMOEBA (sprays: a whole volley at once), GREEN_FLYER (the Eioneus path everywhere; Op 4's home in), CREEPING_DEATH, FLYING_MINE, UG. (Lavinia's Gun Ho hatches let out one Gun Ho at a time: not a wave.)
 *
 * Timing: count members, volley of them at a time (1 = one by one), gap s between volleys, then restMin..restMax s to
 * the next wave (restAfterGone: counted from when the last member is gone), or just one wave (once).
 */
public final class Wave {
    public enum Kind { AMOEBA, GREEN_FLYER, CREEPING_DEATH, FLYING_MINE, UG }
    /** RED_ONE: one member (at random) is red and leaves a capsule. WHOLE: shoot down the whole wave for a capsule. */
    public enum Capsule { RED_ONE, WHOLE }

    public final Kind kind;
    public final Capsule capsule;
    public final int count, volley;
    public final float gap, restMin, restMax;
    public boolean once, restAfterGone;

    /** Members of this wave still to come (0: between waves), time to the next volley, waves finished. */
    public int left;
    public float t;
    public int done;
    /** The red member of this wave (RED_ONE), and the wave's carrier group (WHOLE). */
    private int redAt;
    private Enemy.Group group;
    /** The members let out of this wave so far. */
    public final ArrayList<Enemy> members = new ArrayList<>();

    public Wave(Kind kind, int count, int volley, float gap, float restMin, float restMax, Capsule capsule) {
        this.kind = kind; this.count = count; this.volley = Math.max(1, volley);
        this.gap = gap; this.restMin = restMin; this.restMax = restMax; this.capsule = capsule;
    }

    /** One by one, gap s apart, rest s between waves. */
    public static Wave of(Kind kind, int count, float gap, float rest, Capsule capsule) {
        return new Wave(kind, count, 1, gap, rest, rest, capsule);
    }

    /** Just one wave. */
    public Wave once() { once = true; return this; }

    /** Is a wave coming out right now (members still to come)? */
    public boolean active() { return left > 0; }

    /** Has the one wave of a once-wave been let out? */
    public boolean finished() { return once && done > 0 && left <= 0; }

    /** Is any member of the current (or last) wave still flying? */
    public boolean anyAlive() {
        for (Enemy e : members) if (!e.dead) return true;
        return false;
    }

    /**
     * Advances the clock by dt. Returns the index (0..count-1) of a member due now, or -1. Call it again with dt 0
     * until it returns -1 to get the rest of a volley. may = the spawner may let something out now (on screen, open,
     * ...): while it may not, a due member just waits.
     */
    public int tick(float dt, boolean may) {
        if (finished()) return -1;
        if (left <= 0 && restAfterGone && anyAlive()) { t = rest(); return -1; }   // (counted from the last one gone)
        t -= dt;
        if (t > 0f || !may) { if (t < 0f) t = 0f; return -1; }
        if (left <= 0) start();
        int k = count - left;
        left--;
        if (left == 0) { done++; t = rest(); }
        else t = (count - left) % volley == 0 ? gap : 0f;   // (the next one in the same volley: at once)
        return k;
    }

    private float rest() { return restMax > restMin ? MathUtils.random(restMin, restMax) : restMin; }

    private void start() {
        left = count;
        members.clear();
        redAt = MathUtils.random(count - 1);
        group = null;
        if (capsule == Capsule.WHOLE) { group = new Enemy.Group(); group.size = count; group.carrier = true; }
    }

    /** Is member k the red one (RED_ONE)? */
    public boolean red(int k) { return capsule == Capsule.RED_ONE && k == redAt; }

    /** The capsule group member k belongs to: the whole wave's (WHOLE), its own when it is the red one, or none. */
    public Enemy.Group groupFor(int k) {
        if (capsule == Capsule.WHOLE) return group;
        if (!red(k)) return null;
        Enemy.Group g = new Enemy.Group();
        g.size = 1;
        g.carrier = true;
        return g;
    }
}
