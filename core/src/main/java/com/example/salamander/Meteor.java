package com.example.salamander;

import com.badlogic.gdx.math.MathUtils;

/**
 * Asteroid storm rock (stage 2): falls toward the bottom edge of the screen, some at an angle to the sides. Not an
 * enemy: it ignores the terrain, gives no points, and only a guard (SHIELD / F.FIELD) can destroy it. Shots go
 * straight through the small ones; a big one that is shot splits into three fast small ones.
 *
 * Positions are SCREEN coordinates (relative to the camera's bottom-left), so the rocks fall the same way however the
 * screen is scrolling; GameScreen adds scrollX / scrollY for collisions and drawing.
 */
public class Meteor {
    /** sprites/rock_small.png: 4 frames of 15x15 (tumbling). sprites/rock_big.png: one 30x27 picture. */
    public static final float SMALL_FPS = 8f;

    // ---- the storm (map object "meteors": starts when the screen reaches it, lasts its "time" property)
    /** Time between rocks during a storm (random in this range, s). */
    public static final float SPAWN_MIN = 0.3f, SPAWN_MAX = 0.65f;
    /** About one big rock for every BIG_EVERY small ones (1 : 5). */
    public static final int BIG_EVERY = 5;

    // ---- speeds (px/s, relative to the screen)
    public static final float SMALL_SPEED_MIN = 55f, SMALL_SPEED_MAX = 75f;
    /** Big rocks are slower than the small ones. */
    public static final float BIG_SPEED = 32f;
    /** The three pieces of a big rock fly this many times as fast as the big rock did. */
    public static final float SPLIT_SPEED_MUL = 3f;
    /** The two side pieces fly this many degrees away from straight down (one left, one right). */
    public static final float SPLIT_ANGLE = 50f;
    /** Small rocks: most come almost straight down; the rest slant up to SIDE_MAX degrees to a side. */
    public static final float STRAIGHT_MAX = 8f, SIDE_MIN = 12f, SIDE_MAX = 40f, SIDE_SHARE = 0.45f;
    public static final float BIG_ANGLE_MAX = 12f;

    // ---- side volcanoes (Eioneus; map object "volcano" with spits = left / right): red rocks thrown out of a rock tip
    // sideways, pulled back toward their wall, as measured in the MSX recording (3 volcanoes, ~35 rocks)
    /** Sideways launch speed (random in this range, px/s) and the pull back toward the wall (px/s^2). */
    public static final float SIDE_SPEED_MIN = 45f, SIDE_SPEED_MAX = 90f, SIDE_PULL = 85f;
    /** Up / down speed through the world (random in this range, px/s; + = up). */
    public static final float SIDE_VY_MIN = -40f, SIDE_VY_MAX = 80f;
    /** A burst of SIDE_BURST_MIN..MAX rocks, SIDE_GAP s apart, every SIDE_EVERY_MIN..MAX s while the tip is on screen. */
    public static final int SIDE_BURST_MIN = 3, SIDE_BURST_MAX = 5;
    public static final float SIDE_GAP = 0.12f, SIDE_EVERY_MIN = 1.8f, SIDE_EVERY_MAX = 3.0f;

    public final boolean big;
    /**
     * A side volcano's red rock: it moves through the WORLD (wx, wy = bottom-left; GameScreen works out sx / sy from
     * them), vx changes by ax every second (the pull back toward its wall), and it keeps one picture (frame; no tumbling).
     */
    public boolean red;
    public float wx, wy, ax;
    public int frame = -1;
    /** Bottom-left on the screen, velocity, size, animation clock. */
    public float sx, sy, vx, vy, w, h, t;
    public boolean dead;

    public Meteor(boolean big, float w, float h) {
        this.big = big;
        this.w = w;
        this.h = h;
        t = MathUtils.random(0f, 1f);   // not all tumbling in step
    }

    /** Sets the velocity: SPEED px/s at ANGLE degrees from straight down (positive = to the right). */
    public void aim(float speed, float angleDeg) {
        vx = speed * MathUtils.sinDeg(angleDeg);
        vy = -speed * MathUtils.cosDeg(angleDeg);
    }

    /** Random direction for a new small rock (from the top of the screen). */
    public static float randomSmallAngle() {
        float a = MathUtils.randomBoolean(SIDE_SHARE) ? MathUtils.random(SIDE_MIN, SIDE_MAX) : MathUtils.random(0f, STRAIGHT_MAX);
        return MathUtils.randomBoolean() ? a : -a;
    }

    public void update(float dt) {
        t += dt;
        if (red) {                                  // (GameScreen places it on the screen and ends it)
            vx += ax * dt;
            wx += vx * dt;
            wy += vy * dt;
            return;
        }
        sx += vx * dt;
        sy += vy * dt;
        // gone once it has left the screen (the bottom, or a side)
        if (sy + h < -4f || sx + w < -24f || sx > SalamanderGame.W + 24f) dead = true;
    }

    /** Hit box inset a little from the picture (the rocks are round). */
    public float inset() { return big ? 3f : 2f; }
}
