package com.example.salamander;

/**
 * Shared "walk along the floor" movement for ground missiles and walker enemies.
 *
 * The floor is made of columns (16px, or small cells on map stages), so its height changes in steps. Instead of snapping to the
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
        float T = lv.colWidth();
        // Map stages have bumpy, pixel-shaped floors: bumps up to two cells high are walked over like
        // a slope; only real steps make the crawler stop and climb / drop straight up or down.
        float small = T + 0.5f;   // bumps of one cell are walked over like a slope
        float ref = y + 1f;                                        // just above the feet
        float ahead = dir > 0 ? x + w + 0.1f : x - 0.1f;           // just in front of the nose
        float target = lv.floorTop(ahead, ref);                    // highest floor under the body or just ahead
        for (float sx = x + 0.5f; sx < x + w; sx += T) target = Math.max(target, lv.floorTop(sx, ref));
        target = Math.max(target, lv.floorTop(x + w - 0.5f, ref));

        if (target > y + small + 0.01f) {                          // step ahead: climb straight up
            // a gap too thin to stand in (e.g. between rock pieces) is not a ledge: climb past it
            for (int k = 0; k < 4; k++) {
                float roof = lv.ceilBottom(ahead, target + 0.5f);
                if (target + h <= roof) break;
                target = lv.floorTop(ahead, roof + 0.5f);
            }
            if (target - y > maxStep || target + h > lv.ceilBottom(ahead, target + 0.5f)) return BLOCKED;
            pos[1] = Math.min(target, y + climbSpeed * dt);
            return CLIMBING;
        }
        if (target < y - small - 0.01f) {                          // floor fell away under the whole body
            pos[1] = Math.max(target, y - climbSpeed * dt);
            return DROPPING;
        }
        if (small > 0f) {                                          // small bump: follow it while moving on
            float k = speed * 1.5f * dt;
            pos[1] = y + Math.max(-k, Math.min(k, target - y));
        } else {
            pos[1] = target;
        }
        float nx = x + dir * speed * dt;
        float wall = pos[1] + small + 0.01f;                       // anything higher than this is a step
        if (dir > 0) {
            float edge = (float) Math.floor(ahead / T + 1f) * T;   // next column boundary in front
            if (nx + w > edge && lv.floorTop(edge + 0.1f, ref) > wall) nx = edge - w;   // stop against the step
            pos[0] = Math.max(x, nx);
        } else {
            float edge = (float) Math.floor(ahead / T) * T;
            if (nx < edge && lv.floorTop(edge - 0.1f, ref) > wall) nx = edge;
            pos[0] = Math.min(x, nx);
        }
        return LEVEL;
    }

    /**
     * Same as step(), upside down: the crawler hangs from the ceiling (pos = bottom-left, its top
     * touches the ceiling). CLIMBING = the ceiling comes lower ahead and it moves down to follow,
     * DROPPING = the ceiling rises away and it moves up.
     */
    public static int stepCeiling(Level lv, float[] pos, float w, float h, int dir, float speed, float climbSpeed,
                                  float maxStep, float dt) {
        float x = pos[0], top = pos[1] + h;
        float T = lv.colWidth();
        float small = T + 0.5f;   // bumps of one cell are walked over like a slope
        float ref = top - 1f;                                      // just below the head
        float ahead = dir > 0 ? x + w + 0.1f : x - 0.1f;
        float target = lv.ceilBottom(ahead, ref);                  // lowest ceiling over the body or just ahead
        for (float sx = x + 0.5f; sx < x + w; sx += T) target = Math.min(target, lv.ceilBottom(sx, ref));
        target = Math.min(target, lv.ceilBottom(x + w - 0.5f, ref));

        if (target < top - small - 0.01f) {                        // ceiling steps down ahead: move straight down
            for (int k = 0; k < 4; k++) {                          // thin gaps between pieces don't count
                float ground = lv.floorTop(ahead, target - 0.5f);
                if (target - h >= ground) break;
                target = lv.ceilBottom(ahead, ground - 0.5f);
            }
            if (top - target > maxStep || target - h < lv.floorTop(ahead, target - 0.5f)) return BLOCKED;
            pos[1] = Math.max(target, top - climbSpeed * dt) - h;
            return CLIMBING;
        }
        if (target > top + small + 0.01f) {                        // ceiling rose away above the whole body
            pos[1] = Math.min(target, top + climbSpeed * dt) - h;
            return DROPPING;
        }
        float nt;
        if (small > 0f) {
            float k = speed * 1.5f * dt;
            nt = top + Math.max(-k, Math.min(k, target - top));
        } else {
            nt = target;
        }
        pos[1] = nt - h;
        float nx = x + dir * speed * dt;
        float wall = nt - small - 0.01f;                           // anything lower than this is a step
        if (dir > 0) {
            float edge = (float) Math.floor(ahead / T + 1f) * T;
            if (nx + w > edge && lv.ceilBottom(edge + 0.1f, ref) < wall) nx = edge - w;
            pos[0] = Math.max(x, nx);
        } else {
            float edge = (float) Math.floor(ahead / T) * T;
            if (nx < edge && lv.ceilBottom(edge - 0.1f, ref) < wall) nx = edge;
            pos[0] = Math.min(x, nx);
        }
        return LEVEL;
    }
}
