package com.example.salamander;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.controllers.Controller;
import com.badlogic.gdx.controllers.ControllerMapping;
import com.badlogic.gdx.controllers.Controllers;
import com.badlogic.gdx.utils.Array;

import java.util.Arrays;

/**
 * All player input, split per player. Call {@link #update()} once per frame (SalamanderGame does this).
 *
 *   Player 1 = keyboard + gamepad #1 (merged)
 *   Player 2 = gamepad #2 only
 * Pads are numbered in the order the system reports them (normally the order they were connected).
 *
 * Gamepad layout (standard / Xbox naming):
 *   left stick or d-pad = move, A or X = fire, B / Y = activate power-up,
 *   R1 (hold) + direction = aim the DOUBLE shot,
 *   Start = pause / start, A = confirm on menus, B = cancel / back on menus,
 *   Back/Select + d-pad up/down = music volume, Back/Select + d-pad left/right = SFX volume.
 */
public final class Controls {
    public enum Action { UP, DOWN, LEFT, RIGHT, FIRE, POWER, AIM, PAUSE, CONFIRM, CANCEL, DEBUG, MUSIC_DOWN, MUSIC_UP, SFX_DOWN, SFX_UP }

    public static final int PLAYERS = 2;
    private static final int N = Action.values().length;
    private static final boolean[][] now = new boolean[PLAYERS][N], before = new boolean[PLAYERS][N];
    private static final float STICK_DEADZONE = 0.4f;
    private static boolean padsAvailable = true;
    private static int padCount;

    private Controls() {}

    // ---------------------------------------------------------------- per player

    /** True while PLAYER (0 or 1) holds the action down. */
    public static boolean held(int player, Action a) { return now[player][a.ordinal()]; }

    /** True only on the frame PLAYER first pressed the action. */
    public static boolean pressed(int player, Action a) {
        return now[player][a.ordinal()] && !before[player][a.ordinal()];
    }

    // ---------------------------------------------------------------- any player (menus, pause, volume)

    public static boolean held(Action a) {
        for (int p = 0; p < PLAYERS; p++) if (held(p, a)) return true;
        return false;
    }

    public static boolean pressed(Action a) {
        for (int p = 0; p < PLAYERS; p++) if (pressed(p, a)) return true;
        return false;
    }

    /** Player 1 always has the keyboard; player 2 needs a second gamepad. */
    public static boolean hasInput(int player) { return player == 0 || padCount >= player + 1; }

    // ---------------------------------------------------------------- polling

    public static void update() {
        for (int p = 0; p < PLAYERS; p++) {
            System.arraycopy(now[p], 0, before[p], 0, N);
            Arrays.fill(now[p], false);
        }
        readKeyboard(0);
        padCount = 0;
        if (padsAvailable) {
            try {
                Array<Controller> pads = Controllers.getControllers();
                for (int i = 0; i < pads.size; i++) {
                    Controller c = pads.get(i);
                    if (!c.isConnected()) continue;
                    if (padCount < PLAYERS) readPad(c, padCount);   // 1st pad -> player 1, 2nd pad -> player 2
                    padCount++;
                }
            } catch (Throwable t) {          // controller backend missing or failed: keyboard only
                padsAvailable = false;
                padCount = 0;
                Gdx.app.error("Controls", "Gamepad support unavailable", t);
            }
        }
    }

    private static void readKeyboard(int pl) {
        set(pl, Action.LEFT, key(Keys.LEFT) || key(Keys.A));
        set(pl, Action.RIGHT, key(Keys.RIGHT) || key(Keys.D));
        set(pl, Action.UP, key(Keys.UP) || key(Keys.W));
        set(pl, Action.DOWN, key(Keys.DOWN) || key(Keys.S));
        set(pl, Action.FIRE, key(Keys.Z) || key(Keys.SPACE));
        set(pl, Action.POWER, key(Keys.X) || key(Keys.SHIFT_LEFT) || key(Keys.SHIFT_RIGHT));
        set(pl, Action.AIM, key(Keys.ENTER));
        set(pl, Action.PAUSE, key(Keys.P) || key(Keys.ESCAPE));
        set(pl, Action.CONFIRM, key(Keys.ENTER) || key(Keys.SPACE));
        set(pl, Action.CANCEL, key(Keys.X) || key(Keys.BACKSPACE));   // menus: back one step
        set(pl, Action.DEBUG, key(Keys.H) || key(Keys.F1));
        set(pl, Action.MUSIC_DOWN, key(Keys.NUM_1));
        set(pl, Action.MUSIC_UP, key(Keys.NUM_2));
        set(pl, Action.SFX_DOWN, key(Keys.NUM_3));
        set(pl, Action.SFX_UP, key(Keys.NUM_4));
    }

    private static void readPad(Controller c, int pl) {
        ControllerMapping m = c.getMapping();
        float ax = axis(c, m.axisLeftX), ay = axis(c, m.axisLeftY);   // stick Y is negative when pushed up
        boolean dUp = btn(c, m.buttonDpadUp), dDown = btn(c, m.buttonDpadDown);
        boolean dLeft = btn(c, m.buttonDpadLeft), dRight = btn(c, m.buttonDpadRight);
        boolean back = btn(c, m.buttonBack);

        if (back) {   // Back/Select held: the d-pad adjusts volume instead of moving
            set(pl, Action.MUSIC_UP, dUp);
            set(pl, Action.MUSIC_DOWN, dDown);
            set(pl, Action.SFX_DOWN, dLeft);
            set(pl, Action.SFX_UP, dRight);
            dUp = dDown = dLeft = dRight = false;
        }
        set(pl, Action.UP, dUp || ay < -STICK_DEADZONE);
        set(pl, Action.DOWN, dDown || ay > STICK_DEADZONE);
        set(pl, Action.LEFT, dLeft || ax < -STICK_DEADZONE);
        set(pl, Action.RIGHT, dRight || ax > STICK_DEADZONE);
        set(pl, Action.FIRE, btn(c, m.buttonA) || btn(c, m.buttonX));
        set(pl, Action.POWER, btn(c, m.buttonB) || btn(c, m.buttonY));
        set(pl, Action.AIM, btn(c, m.buttonR1));
        set(pl, Action.PAUSE, btn(c, m.buttonStart));
        set(pl, Action.CONFIRM, btn(c, m.buttonStart) || btn(c, m.buttonA));
        set(pl, Action.CANCEL, btn(c, m.buttonB));                    // menus: back one step
    }

    /** OR-merges, so the keyboard and pad #1 can both drive player 1. */
    private static void set(int pl, Action a, boolean down) { if (down) now[pl][a.ordinal()] = true; }

    private static boolean key(int k) { return Gdx.input.isKeyPressed(k); }

    private static boolean btn(Controller c, int code) {
        return code != ControllerMapping.UNDEFINED && c.getButton(code);
    }

    private static float axis(Controller c, int code) {
        return code == ControllerMapping.UNDEFINED ? 0f : c.getAxis(code);
    }
}
