package com.example.salamander;

import com.badlogic.gdx.math.MathUtils;

import java.util.ArrayList;

/**
 * The ships' guns and options (moved out of GameScreen): firing every gun (ship + options) within its on-screen
 * limits, the normal / DOUBLE shots, LASER and RIPPLE (straight and aimed), MISSILE / 2-WAY / HOMING missiles and
 * their targets, and the option trail and formations. The bullets, enemies and camera stay in GameScreen (g).
 */
final class PlayerWeapons {
    private static final int W = SalamanderGame.W, H = SalamanderGame.H;
    private final GameScreen g;

    PlayerWeapons(GameScreen g) { this.g = g; }

    /** A new start (resetWorld): no aimed beams coming out. */
    void reset() { aimBeamList.clear(); }

    /** Options follow the ship's path. Only record when the ship actually moved on screen,
     *  so the options stay where they are while the ship stands still. */
    void recordTrail(Player p, float dt) {
        p.trailAcc += dt;
        while (p.trailAcc >= 1f / 60f) {
            float sx = p.x - g.scrollX, sy = p.y - g.pathY;     // path-relative, so options ride along with the camera path
            if (Math.abs(sx - p.trailX(0)) > 0.25f || Math.abs(sy - p.trailY(0)) > 0.25f) p.record(sx, sy);
            p.trailAcc -= 1f / 60f;
        }
    }

    // ---- option formations (Albert, 2026-10-28): R2 orbit, L1 V formation, L2 freeze; the same button again = normal
    /** How fast an option glides to its new place when the formation changes (px/s). */
    private static final float OPT_GATHER = 260f;
    /** Orbit: radius (px, ship's middle to option's middle) and turning speed (rad/s). */
    private static final float ORBIT_R = 26f, ORBIT_SPEED = 3.2f;
    /** V formation: each pair of options V_BACK px further back and V_SIDE px further out than the one before. */
    private static final float V_BACK = 14f, V_SIDE = 16f;
    /** An option's middle in its picture (16x10). */
    private static final float OPT_MID_X = 8f, OPT_MID_Y = 5f;

    /** Switches to a formation, or back to the normal trail if it is the one already on. */
    void toggleOptMode(Player p, int mode) {
        int m = p.optMode == mode ? Player.OPT_TRAIL : mode;
        float scx = p.x - g.scrollX + 16f, scy = p.y - g.pathY + 8f;   // the ship's middle (path-relative)
        for (int i = 0; i < Player.MAX_OPTIONS; i++) p.optLock[i] = false;
        if (m == Player.OPT_FREEZE)                        // held where they are now
            for (int i = 0; i < p.optKnown; i++) { p.optFX[i] = p.optSX[i] - scx; p.optFY[i] = p.optSY[i] - scy; }
        if (m == Player.OPT_ORBIT && p.optKnown > 0)       // the circle starts where the first option is
            p.orbitAng = MathUtils.atan2(p.optSY[0] + OPT_MID_Y - scy, p.optSX[0] + OPT_MID_X - scx);
        p.optMode = m;
        g.a.play("hit", 0.2f);
    }

    /** Every frame: where each option should be in the formation, and gliding there (or there already). */
    void updateOptions(Player p, float dt) {
        if (p.optMode == Player.OPT_ORBIT) p.orbitAng += ORBIT_SPEED * dt;
        float scx = p.x - g.scrollX + 16f, scy = p.y - g.pathY + 8f;
        for (int i = 0; i < p.options; i++) {
            float tx, ty;
            if (p.optMode == Player.OPT_ORBIT) {
                float ang = p.orbitAng + i * MathUtils.PI2 / p.options;
                tx = scx + MathUtils.cos(ang) * ORBIT_R - OPT_MID_X;
                ty = scy + MathUtils.sin(ang) * ORBIT_R - OPT_MID_Y;
            } else if (p.optMode == Player.OPT_V) {           // pairs, one on each side, further back each time
                int k = i / 2 + 1, side = (i & 1) == 0 ? 1 : -1;
                if (g.level.vertical) { tx = scx + side * V_SIDE * k - OPT_MID_X; ty = scy - V_BACK * k - OPT_MID_Y; }   // beside it
                else { tx = scx - V_BACK * k - OPT_MID_X; ty = scy + side * V_SIDE * k - OPT_MID_Y; }               // above / below
            } else if (p.optMode == Player.OPT_FREEZE && i < p.optKnown) {
                tx = scx + p.optFX[i]; ty = scy + p.optFY[i];
            } else {
                if (p.optMode == Player.OPT_FREEZE) {          // (a new option while frozen: it joins at its trail place)
                    p.optFX[i] = p.trailX(10 * (i + 1)) + 10f - scx; p.optFY[i] = p.trailY(10 * (i + 1)) + 2f - scy;
                }
                tx = p.trailX(10 * (i + 1)) + 10f; ty = p.trailY(10 * (i + 1)) + 2f;
            }
            if (!p.optReady || i >= p.optKnown || p.optLock[i]) {   // (new, or in its place: just there)
                p.optSX[i] = tx; p.optSY[i] = ty; p.optLock[i] = true;
                continue;
            }
            float dx = tx - p.optSX[i], dy = ty - p.optSY[i], d = (float) Math.sqrt(dx * dx + dy * dy), step = OPT_GATHER * dt;
            if (d <= step) { p.optSX[i] = tx; p.optSY[i] = ty; p.optLock[i] = true; }
            else { p.optSX[i] += dx / d * step; p.optSY[i] += dy / d * step; }
        }
        p.optKnown = p.options;
        p.optReady = true;
    }

    // Shots are fired from the "nose": the front tip of the ship art (x 7..23 of the 32px frame,
    // centre line at y 8) or the right edge of an option.
    private static final float NOSE_X = 24f, NOSE_Y = 8f;

    /** Fastest a ship or option may fire: 8 shots per second, as long as it is under its on-screen limit. */
    public static final float FIRE_INTERVAL = 1f / 8f;
    /** On-screen limits per gun (the ship and each option count separately). With DOUBLE the forward
     *  and the angled shots each get their own, smaller limit. */
    public static final int MAX_SHOTS = 4, MAX_DOUBLE_SHOTS = 2, MAX_LASERS = 1, MAX_RIPPLES = 1, MAX_MISSILES = 2, MAX_TWO_WAY = 1, MAX_HOMING = 1;

    /** Fires every gun (ship + options) that is under its limit. Returns true if anything was fired. */
    boolean shoot(Player p) {
        boolean fired;
        if (g.level.vertical) {   // vertical stage: the nose is at the top of the picture, options fire from their top
            fired = fireGun(p, 0, p.x + 16f, p.y + Player.VART_T);
            for (int i = 0; i < p.options; i++) fired |= fireGun(p, i + 1, g.optX(p, i) + 6f, g.optY(p, i) + 14f);
        } else {
            fired = fireGun(p, 0, p.x + NOSE_X, p.y + NOSE_Y);
            for (int i = 0; i < p.options; i++) fired |= fireGun(p, i + 1, g.optX(p, i) + 14f, g.optY(p, i) + 6f);
        }
        if (fired) g.a.play("shoot", 0.15f);
        return fired;
    }

    /** DOUBLE aimed straight forward works exactly like the normal shot (one shot, limit MAX_SHOTS). */
    static boolean doubleActive(Player p) { return p.weapon == 1 && p.doubleDir != 0; }

    /** One gun: fires whichever of its shots are under their on-screen limit. */
    boolean fireGun(Player p, int src, float nx, float ny) {
        if (p.weapon == 2) {
            if (onScreen(p, src, Bullet.Kind.LASER, false) + aimBeams(p, src) >= MAX_LASERS) return false;
            fireLaser(p, src, nx, ny);
            return true;
        }
        if (p.weapon == Player.W_RIPPLE) {
            if (onScreen(p, src, Bullet.Kind.RIPPLE, false) >= MAX_RIPPLES) return false;
            fireRipple(p, src, nx, ny);
            return true;
        }
        boolean dbl = doubleActive(p);
        int max = dbl ? MAX_DOUBLE_SHOTS : MAX_SHOTS;
        boolean fired = false;
        if (onScreen(p, src, Bullet.Kind.SHOT, false) < max) { fireShot(p, src, nx, ny); fired = true; }
        if (dbl && onScreen(p, src, Bullet.Kind.SHOT, true) < MAX_DOUBLE_SHOTS) { fireAngled(p, src, nx, ny); fired = true; }
        return fired;
    }

    boolean launchMissiles(Player p) {
        // down missiles leave from the bottom of the ship / option, 2-WAY's up missiles from the top
        if (g.level.vertical) {   // vertical stage: from the sides of the ship / option (y = the missile's middle)
            boolean fired = launchFromV(p, 0, p.x + Player.VART_R - 2f, p.x + Player.VART_L + 2f, p.y + 6f, p.x + 16f);
            for (int i = 0; i < p.options; i++)
                fired |= launchFromV(p, i + 1, g.optX(p, i) + 10f, g.optX(p, i) + 2f, g.optY(p, i) + 6f, g.optX(p, i) + 6f);
            return fired;
        }
        float mh = g.missR.getRegionHeight();
        boolean fired = launchFrom(p, 0, p.x + 14f, p.y + Player.ART_B, p.y + Player.ART_T - mh, p.y + 8f);
        for (int i = 0; i < p.options; i++) fired |= launchFrom(p, i + 1, g.optX(p, i) + 2f, g.optY(p, i), g.optY(p, i) + 12f - mh, g.optY(p, i) + 6f);
        return fired;
    }

    /**
     * One gun's missiles. Normal: one missile toward the nearer edge of the screen (down if the gun is in the lower
     * half, up if in the upper half), up to MAX_MISSILES on screen; HOMING the same, up to MAX_HOMING. 2-WAY: one down and one up, each direction with
     * its own limit of MAX_TWO_WAY. yDown / yUp = launch heights, gunY = the gun's middle.
     */
    boolean launchFrom(Player p, int src, float x, float yDown, float yUp, float gunY) {
        if (!p.twoWay() || p.homing()) {   // (HOMING: one missile at a time, MAX_HOMING, fired like the normal one)
            int max = p.homing() ? MAX_HOMING : MAX_MISSILES;
            if (onScreen(p, src, Bullet.Kind.MISSILE, false) + onScreen(p, src, Bullet.Kind.MISSILE, true) >= max) return false;
            // nearer the top edge? (the playfield runs from scrollY, the top of the HUD strip, to scrollY + H: the
            // middle of what you can fly in, not of the whole screen with the HUD)
            boolean up = gunY > g.scrollY + H / 2f;
            launchMissile(p, src, x, up ? yUp : yDown, up);
            return true;
        }
        boolean fired = false;
        if (onScreen(p, src, Bullet.Kind.MISSILE, false) < MAX_TWO_WAY) { launchMissile(p, src, x, yDown, false); fired = true; }
        if (onScreen(p, src, Bullet.Kind.MISSILE, true) < MAX_TWO_WAY) { launchMissile(p, src, x, yUp, true); fired = true; }
        return fired;
    }

    /**
     * Vertical stage: one missile up and to the right, up to MAX_MISSILES on screen; 2-WAY: one to the right and one
     * to the left, each with its own limit of MAX_TWO_WAY. xr / xl = launch points (centre) on the right / left side.
     */
    boolean launchFromV(Player p, int src, float xr, float xl, float y, float gunX) {
        if (!p.twoWay() || p.homing()) {   // normal MISSILE / HOMING: toward the nearer side edge (left half: left, right half: right)
            int max = p.homing() ? MAX_HOMING : MAX_MISSILES;
            if (onScreen(p, src, Bullet.Kind.MISSILE, false) + onScreen(p, src, Bullet.Kind.MISSILE, true) >= max) return false;
            boolean left = gunX < g.scrollX + W / 2f;
            launchMissileV(p, src, left ? xl : xr, y, left);
            return true;
        }
        boolean fired = false;
        if (onScreen(p, src, Bullet.Kind.MISSILE, false) < MAX_TWO_WAY) { launchMissileV(p, src, xr, y, false); fired = true; }
        if (onScreen(p, src, Bullet.Kind.MISSILE, true) < MAX_TWO_WAY) { launchMissileV(p, src, xl, y, true); fired = true; }
        return fired;
    }

    /** Vertical missile: a small square hit box (it turns as it goes), see Bullet.wallMove. left = 2-WAY's second one. */
    private static final float VMISSILE_BOX = 4f;

    void launchMissileV(Player p, int src, float cx, float cy, boolean left) {
        float s = VMISSILE_BOX;
        Bullet m = new Bullet(Bullet.Kind.MISSILE, cx - s / 2f, cy - s / 2f, s, s);
        m.vx = left ? -1f : 1f;                             // (direction only: Bullet.MISSILE_SPEED sets the speed)
        m.vy = 1f;
        m.dmg = 3;
        m.up = left;
        m.vert = true;
        m.homing = p.homing();
        m.owner = p; m.src = src;
        if (g.level.hitsMap(m.x, m.y, m.w, m.h)) return;      // launched inside rock (outside the map is open space)
        g.pBullets.add(m);
    }

    /**
     * Lasers stay level with the gun that fired them: when the ship or option moves up or down, so does its beam.
     * While a beam is still attached (growing out of the gun) its tail also stays at the muzzle; the front keeps its
     * own position, so the beam gets longer or shorter as the gun moves back or forward.
     */
    void steerLasers(Player p) {
        for (int i = 0, k = g.pBullets.size(); i < k; i++) {
            Bullet b = g.pBullets.get(i);
            if (b.dead || b.owner != p || b.kind != Bullet.Kind.LASER || b.stuck || b.seg) continue;   // (stuck: pinned where it hit; seg: aimed)
            if (b.src > p.options) { b.attached = false; continue; }   // its option is gone: the beam flies on alone
            if (g.level.vertical) {   // (vertical stage: in line with it, left / right)
                b.x = (b.src == 0 ? p.x + 16f : g.optX(p, b.src - 1) + 6f) - b.w / 2f;
                if (b.attached) {
                    float top = b.y + b.h, tail = b.src == 0 ? p.y + Player.VART_T : g.optY(p, b.src - 1) + 14f;
                    b.y = tail;
                    b.h = Math.max(1f, top - tail);
                }
                continue;
            }
            b.y = (b.src == 0 ? p.y + NOSE_Y : g.optY(p, b.src - 1) + 6f) - b.h / 2f;
            if (b.attached) {
                float front = b.x + b.w, tail = b.src == 0 ? p.x + NOSE_X : g.optX(p, b.src - 1) + 14f;
                b.x = tail;
                b.w = Math.max(1f, front - tail);
            }
        }
    }

    /** An attached laser whose front has reached the edge of the screen (or whose ship is gone) lets go of its gun. */
    void detachLaser(Bullet b) {
        if (!b.attached) return;
        if (b.owner == null || b.owner.dead || b.owner.out
                || (b.vert ? b.y + b.h >= g.scrollY + H : b.x + b.w >= g.scrollX + W)) b.attached = false;
    }

    /**
     * How many of this gun's bullets of this kind are still flying. alt = count the second stream
     * instead: DOUBLE's angled shots, or 2-WAY's upward missiles.
     */
    int onScreen(Player p, int src, Bullet.Kind kind, boolean alt) {
        int n = 0;
        for (int i = 0, k = g.pBullets.size(); i < k; i++) {
            Bullet b = g.pBullets.get(i);
            if (!b.dead && !b.seg && b.owner == p && b.src == src && b.kind == kind && (b.angled || b.up) == alt) n++;
        }
        return n;
    }

    // (nx, ny) = the muzzle: shots start with their tip there, so they come out of the ship, not ahead of it.
    void fireLaser(Player p, int src, float nx, float ny) {
        if (p.laserDir != 0) { startAimBeam(p, src, nx, ny); return; }   // (aimed: see AimBeam)
        float lh = g.laserR.getRegionHeight();   // beam thickness (and hit height) from sprites/laser.png
        if (g.level.vertical) {
            Bullet b = new Bullet(Bullet.Kind.LASER, nx - lh / 2f, ny, lh, Bullet.LASER_START_LEN);
            b.dmg = Bullet.LASER_DAMAGE;
            b.vy = Bullet.LASER_SPEED;
            b.vert = true;
            b.attached = true;
            b.owner = p; b.src = src;
            g.pBullets.add(b);
            return;
        }
        Bullet b = new Bullet(Bullet.Kind.LASER, nx, ny - lh / 2f, Bullet.LASER_START_LEN, lh);
        b.dmg = Bullet.LASER_DAMAGE;
        b.vx = Bullet.LASER_SPEED;
        b.attached = true;
        b.owner = p; b.src = src;
        g.pBullets.add(b);
    }

    void fireRipple(Player p, int src, float nx, float ny) {
        if (p.laserDir != 0) { fireAimedRipple(p, src, nx, ny); return; }
        if (g.level.vertical) {   // lies across the way up: widens sideways as it climbs
            Bullet b = new Bullet(Bullet.Kind.RIPPLE, nx - Bullet.RIPPLE_START_H / 2f, ny - Bullet.RIPPLE_START_W,
                    Bullet.RIPPLE_START_H, Bullet.RIPPLE_START_W);
            b.vy = Bullet.RIPPLE_SPEED;
            b.dmg = Bullet.RIPPLE_DAMAGE;
            b.vert = true;
            b.owner = p; b.src = src;
            g.pBullets.add(b);
            return;
        }
        Bullet b = new Bullet(Bullet.Kind.RIPPLE, nx - Bullet.RIPPLE_START_W, ny - Bullet.RIPPLE_START_H / 2f,
                Bullet.RIPPLE_START_W, Bullet.RIPPLE_START_H);
        b.vx = Bullet.RIPPLE_SPEED;
        b.dmg = Bullet.RIPPLE_DAMAGE;
        b.owner = p; b.src = src;
        g.pBullets.add(b);
    }

    // Shots take their size from sprites/shot.png (the picture is the hitbox), tip at the muzzle.
    void fireShot(Player p, int src, float nx, float ny) {
        float sw = g.shotR.getRegionWidth(), sh = g.shotR.getRegionHeight();
        if (g.level.vertical) {   // straight up (drawn turned)
            Bullet b = new Bullet(Bullet.Kind.SHOT, nx - (int) (sh / 2f), ny - sw, sh, sw);
            b.vy = Bullet.SHOT_SPEED;
            b.vert = true;
            b.owner = p; b.src = src;
            g.pBullets.add(b);
            return;
        }
        Bullet b = new Bullet(Bullet.Kind.SHOT, nx - sw, ny - (int) (sh / 2f), sw, sh);
        b.vx = Bullet.SHOT_SPEED;
        b.owner = p; b.src = src;
        g.pBullets.add(b);
    }

    /** DOUBLE's extra shot, in the direction chosen with AIM. */
    void fireAngled(Player p, int src, float nx, float ny) {
        float ang = doubleAngle(p);
        float s = g.shotR.getRegionWidth();               // square hitbox as wide as the shot, so any angle is covered
        Bullet d = g.level.vertical ? new Bullet(Bullet.Kind.SHOT, nx - s / 2f, ny - s, s, s)
                : new Bullet(Bullet.Kind.SHOT, nx - s, ny - s / 2f, s, s);
        d.vx = MathUtils.cos(ang) * Bullet.SHOT_SPEED;      // same speed as the normal shot, in the aimed direction
        d.vy = MathUtils.sin(ang) * Bullet.SHOT_SPEED;
        d.owner = p; d.src = src; d.angled = true;
        g.pBullets.add(d);
    }

    /** DOUBLE's direction in radians: doubleDir steps of 45 degrees counter-clockwise from straight ahead. */
    float doubleAngle(Player p) {
        return (p.doubleDir + (g.level.vertical ? 2 : 0)) * MathUtils.PI / 4f;
    }

    /** An aim direction (0 = straight ahead, then every 45 degrees counter-clockwise) in radians, 0 = right. */
    float aimAngle(int dir) { return (dir + (g.level.vertical ? 2 : 0)) * MathUtils.PI / 4f; }

    // ---- aimed LASER and RIPPLE (AIM held; Albert, 2026-10-27) -----------------------------------------------------
    /**
     * An aimed laser beam: while it is coming out (emit s left; as long as the front takes to reach the screen's edge,
     * like the straight beam, which lets go of its gun there) its gun lets out a piece every frame (Bullet.seg),
     * AIM_PIECE px of box, all flying the aimed way at LASER_SPEED. The pieces share hits (each target is hurt once
     * per beam). A beam counts toward its gun's MAX_LASERS while it is coming out or any piece is left.
     */
    private static final class AimBeam { Player p; int src; float dx, dy, emit; ArrayList<Object> hits = new ArrayList<>(); }
    private final ArrayList<AimBeam> aimBeamList = new ArrayList<>();
    static final float AIM_PIECE = 8f, AIM_PIECE_LEN = Bullet.LASER_SPEED / 60f + 3f;
    /** A diagonal ripple grows to this size across its way. */
    static final float RIPPLE_DIAG_MAX = 96f;

    void startAimBeam(Player p, int src, float nx, float ny) {
        AimBeam ab = new AimBeam();
        ab.p = p; ab.src = src;
        float ang = aimAngle(p.laserDir);
        ab.dx = MathUtils.cos(ang); ab.dy = MathUtils.sin(ang);
        // how long until the front reaches the edge of the screen
        float tx = ab.dx > 0.01f ? (g.scrollX + W - nx) / ab.dx : ab.dx < -0.01f ? (g.scrollX - nx) / ab.dx : Float.MAX_VALUE;
        float ty = ab.dy > 0.01f ? (g.scrollY + H - ny) / ab.dy : ab.dy < -0.01f ? (g.scrollY - ny) / ab.dy : Float.MAX_VALUE;
        ab.emit = Math.max(0.1f, Math.min(tx, ty) / Bullet.LASER_SPEED);
        aimBeamList.add(ab);
        aimPiece(ab, nx, ny);
    }

    /** One piece of an aimed beam, its middle at (cx, cy). */
    void aimPiece(AimBeam ab, float cx, float cy) {
        float s = AIM_PIECE;
        Bullet b = new Bullet(Bullet.Kind.LASER, cx - s / 2f, cy - s / 2f, s, s);
        b.seg = true;
        b.aimBeam = ab;
        b.hits = ab.hits;
        b.dmg = Bullet.LASER_DAMAGE;
        b.vx = ab.dx * Bullet.LASER_SPEED;
        b.vy = ab.dy * Bullet.LASER_SPEED;
        b.owner = ab.p; b.src = ab.src;
        g.pBullets.add(b);
    }

    /** Aimed beams still coming out of this player's guns: a piece from each gun's muzzle (where it is now). */
    void emitAimBeams(Player p, float dt) {
        if (aimBeamList.isEmpty()) return;
        for (AimBeam ab : aimBeamList) {
            if (ab.p != p || ab.emit <= 0f) continue;
            if (ab.src > p.options) { ab.emit = 0f; continue; }   // its option is gone
            ab.emit -= dt;
            float nx, ny;
            if (g.level.vertical) {
                nx = ab.src == 0 ? p.x + 16f : g.optX(p, ab.src - 1) + 6f;
                ny = ab.src == 0 ? p.y + Player.VART_T : g.optY(p, ab.src - 1) + 14f;
            } else {
                nx = ab.src == 0 ? p.x + NOSE_X : g.optX(p, ab.src - 1) + 14f;
                ny = ab.src == 0 ? p.y + NOSE_Y : g.optY(p, ab.src - 1) + 6f;
            }
            aimPiece(ab, nx, ny);
        }
        aimBeamList.removeIf(ab -> ab.emit <= 0f && !aimBeamLive(ab));
    }

    boolean aimBeamLive(AimBeam ab) {
        for (int i = 0, k = g.pBullets.size(); i < k; i++) {
            Bullet b = g.pBullets.get(i);
            if (b.aimBeam == ab && !b.dead) return true;
        }
        return false;
    }

    /** How many aimed beams of this gun are still coming out or flying. */
    int aimBeams(Player p, int src) {
        int n = 0;
        for (AimBeam ab : aimBeamList) if (ab.p == p && ab.src == src && (ab.emit > 0f || aimBeamLive(ab))) n++;
        return n;
    }

    /**
     * An aimed ripple: straight up / down / back flies like the normal ring turned that way (it stretches across its
     * way up to the playfield, stopping at rock); a diagonal one grows to RIPPLE_DIAG_MAX across its way.
     */
    void fireAimedRipple(Player p, int src, float nx, float ny) {
        float ang = aimAngle(p.laserDir);
        int deg = Math.round(ang * MathUtils.radiansToDegrees) % 360;
        float sw = Bullet.RIPPLE_START_W, sh = Bullet.RIPPLE_START_H;
        Bullet b;
        if (deg == 0 || deg == 180) {                    // across: right / left
            b = new Bullet(Bullet.Kind.RIPPLE, deg == 0 ? nx - sw : nx, ny - sh / 2f, sw, sh);
            b.vx = deg == 0 ? Bullet.RIPPLE_SPEED : -Bullet.RIPPLE_SPEED;
        } else if (deg == 90 || deg == 270) {            // up / down
            b = new Bullet(Bullet.Kind.RIPPLE, nx - sh / 2f, deg == 90 ? ny - sw : ny, sh, sw);
            b.vy = deg == 90 ? Bullet.RIPPLE_SPEED : -Bullet.RIPPLE_SPEED;
            b.vert = true;
        } else {                                         // diagonal
            b = new Bullet(Bullet.Kind.RIPPLE, nx - sh / 2f, ny - sh / 2f, sh, sh);
            b.vx = MathUtils.cos(ang) * Bullet.RIPPLE_SPEED;
            b.vy = MathUtils.sin(ang) * Bullet.RIPPLE_SPEED;
            b.diag = true;
            b.len = sh;
        }
        b.dmg = Bullet.RIPPLE_DAMAGE;
        b.owner = p; b.src = src;
        g.pBullets.add(b);
    }

    void launchMissile(Player p, int src, float x, float y, boolean up) {
        Bullet m = new Bullet(Bullet.Kind.MISSILE, x, y, g.missR.getRegionWidth(), g.missR.getRegionHeight());   // size of missile.png
        m.vx = Bullet.MISSILE_SPEED;                        // speed: Bullet.MISSILE_SPEED (45 degree dive)
        m.vy = up ? Bullet.MISSILE_SPEED : -Bullet.MISSILE_SPEED;
        m.dmg = 3;
        m.up = up;
        m.homing = p.homing();
        m.owner = p; m.src = src;
        g.pBullets.add(m);
    }

    // ---- HOMING missiles

    /**
     * Gives a HOMING missile its target for this frame: keeps the one it is chasing while that is alive and on screen,
     * otherwise picks the nearest enemy on screen (enemies, Bamdas / falling rocks, domes / rock eyes, tower tips,
     * the weak blue balls of claw arms, the boss's weak spot).
     */
    void aimHoming(Bullet b) {
        if (b.dead || b.burst) { b.hasTarget = false; return; }
        homingFromX = b.x + b.w / 2f; homingFromY = b.y + b.h / 2f;
        if (b.target == null || !targetPoint(b.target)) {
            b.target = null;
            float cx = b.x + b.w / 2f, cy = b.y + b.h / 2f, best = Float.MAX_VALUE;
            for (Enemy e : g.enemies) if (!e.indestructible() && !e.withered && !e.intangible()) best = consider(b, e, cx, cy, best);   // (not the indestructible guards)
            for (Hazard h : g.hazards)
                if (h.shootable() || h.type == Hazard.Type.DOME || h.type == Hazard.Type.EYE
                        || h.type == Hazard.Type.TOWER || h.type == Hazard.Type.ARM) best = consider(b, h, cx, cy, best);
            if (g.boss != null) best = consider(b, g.boss, cx, cy, best);
        }
        b.hasTarget = b.target != null && targetPoint(b.target);
        if (b.hasTarget) { b.tx = homingPt[0]; b.ty = homingPt[1]; }
    }

    private final float[] homingPt = new float[2];
    /** The missile being aimed (its middle): a claw arm is aimed at its weak ball nearest to it. */
    private float homingFromX, homingFromY;

    float consider(Bullet b, Object o, float cx, float cy, float best) {
        if (!targetPoint(o)) return best;
        float dx = homingPt[0] - cx, dy = homingPt[1] - cy, d = dx * dx + dy * dy;
        if (d < best) { b.target = o; return d; }
        return best;
    }

    /** Puts the target's aim point in homingPt; false if it can't be chased (gone, dying or off screen). */
    boolean targetPoint(Object o) {
        float x, y;
        if (o instanceof Enemy) {
            Enemy e = (Enemy) o;
            if (e.dead || e.withered || e.hp <= 0) return false;   // (a destroyed base / root stays, but is no target)
            x = e.x + e.w / 2f; y = e.y + e.h / 2f;
        } else if (o instanceof Hazard) {
            Hazard h = (Hazard) o;
            if (h.dead || h.hp <= 0) return false;
            if (h.type == Hazard.Type.TOWER) {          // the green tip, once it is out of the rock (see towerTipHit)
                if (h.grown < Hazard.TOWER_ROOT + Hazard.TOWER_TIP) return false;
                float ty = h.fromCeil ? h.y : h.y + h.h - Hazard.TOWER_TIP;
                x = h.x + h.w / 2f; y = ty + Hazard.TOWER_TIP / 2f;
            } else if (h.type == Hazard.Type.ARM) {     // a blue ball that can be damaged (as in collide), nearest first
                int n = h.armX.length, pick = -1;
                float bestD = Float.MAX_VALUE;
                for (int i = 0; i < n; i++) {
                    boolean weak = h.bossArm ? i < n - 1 : i == h.armWeak;
                    if (!weak || !h.armBallOut(i)) continue;
                    float bx = h.armX[i], by = h.armY[i];
                    if (bx < g.scrollX || bx > g.scrollX + W || by < g.scrollY || by > g.scrollY + H) continue;
                    float dx = bx - homingFromX, dy = by - homingFromY, d = dx * dx + dy * dy;
                    if (d < bestD) { bestD = d; pick = i; }
                }
                if (pick < 0) return false;
                x = h.armX[pick]; y = h.armY[pick];
            } else {
                x = h.x + h.w / 2f; y = h.y + h.h / 2f;
            }
        } else if (o instanceof Boss) {
            Boss bs = (Boss) o;
            if (bs != g.boss || bs.dying) return false;
            if (bs.hasCore) { x = bs.x + bs.coreX + bs.coreW / 2f; y = bs.y + bs.coreY + bs.coreH / 2f; }   // its weak spot
            else if (bs.hasEye) { x = bs.x + bs.eyeX + bs.eyeW / 2f; y = bs.y + bs.eyeY + bs.eyeH / 2f; }
            else { x = bs.x + bs.w / 2f; y = bs.y + bs.h / 2f; }
        } else return false;
        if (x < g.scrollX || x > g.scrollX + W || y < g.scrollY || y > g.scrollY + H) return false;   // only what's on screen
        homingPt[0] = x; homingPt[1] = y;
        return true;
    }
}
