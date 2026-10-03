package com.example.salamander;

/**
 * Shared "walk along the floor" movement for ground missiles and walker enemies.
 *
 * The floor is made of 16px columns, so its height changes in steps. Instead of snapping to the
 * new height, a crawler stops at a step up and climbs straight up; at a step down it keeps going
 * until its whole body is over the lower floor and then drops straight down. Only when it is level
 * again does it carry on forward.
 */
public final class FloorCrawler {
    public static final int LEVEL = 0, CLIMBING = 1, DROPPING = -1, BLOCKED = 2;

    private FloorCrawler() {}

    /**
     * Advances one step. pos = {x, y} (bottom-left, updated in place), w/h = size,
     * dir = +1 moving right / -1 moving left. maxStep = tallest wall it may climb (taller = BLOCKED).
     * Returns LEVEL, CLIMBING, DROPPING or BLOCKED.
     */
    public static int step(Level lv, float[] pos, float w, float h, int dir, float speed, float climbSpeed,
                           float maxStep, float dt) {
        float x = pos[0], y = pos[1];
        float tail = dir > 0 ? x + 0.5f : x + w - 0.5f;          // back end of the body
        float ahead = dir > 0 ? x + w + 0.1f : x - 0.1f;           // just in front of the nose
        float target = Math.max(lv.floorTop(tail), lv.floorTop(ahead));

        if (target > y + 0.01f) {                                  // step ahead: climb straight up
            if (target - y > maxStep || target + h > lv.ceilBottom(ahead)) return BLOCKED;
            pos[1] = Math.min(target, y + climbSpeed * dt);
            return CLIMBING;
        }
        if (target < y - 0.01f) {                                  // floor fell away under the whole body
            pos[1] = Math.max(target, y - climbSpeed * dt);
            return DROPPING;
        }
        pos[1] = target;
        float T = SalamanderGame.T;
        float nx = x + dir * speed * dt;
        if (dir > 0) {
            float edge = (float) Math.floor(ahead / T + 1f) * T;   // next column boundary in front
            if (nx + w > edge && lv.floorTop(edge + 0.1f) > target + 0.01f) nx = edge - w;   // stop against the step
            pos[0] = Math.max(x, nx);
        } else {
            float edge = (float) Math.floor(ahead / T) * T;
            if (nx < edge && lv.floorTop(edge - 0.1f) > target + 0.01f) nx = edge;
            pos[0] = Math.min(x, nx);
        }
        return LEVEL;
    }
}
