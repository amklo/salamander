package com.example.salamander;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.example.salamander.Controls.Action;
import com.badlogic.gdx.Preferences;
import com.badlogic.gdx.graphics.Cursor;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;

public class SalamanderGame extends Game {
    /** Virtual screen: the MSX resolution, 256 x 192. */
    public static final int SCREEN_W = 256, SCREEN_H = 192;
    /** Score / power meter strip along the bottom of the screen, as in the original. */
    public static final int HUD_H = 24;
    /** The playfield (what the game camera shows) sits above the HUD: 256 x 168. */
    public static final int W = SCREEN_W, H = SCREEN_H - HUD_H, T = 16;
    /**
     * Stages 2 and 3 were laid out for a 272px tall view. The camera shows 168px of that "lane" and drifts
     * up and down to follow the ships, so the whole lane can still be reached. A map can set its own
     * lane height with the property "lane" (stage 1 uses 168: it is the original MSX map, no drift).
     */
    public static final int LANE_H = 272;

    public SpriteBatch batch;
    public Assets assets;
    public Bgm bgm;
    /** What plays the music on this platform (given by the launcher). */
    private final MusicBackend musicBackend;
    public int hiScore;
    private Preferences prefs;

    /**
     * Difficulty, picked on the title screen (saved between runs). EASY: a destroyed ship keeps its power-ups, except
     * the options (they are still left behind as green capsules). NORMAL: it starts over without power-ups.
     */
    public boolean easy;

    public void setEasy(boolean e) {
        easy = e;
        prefs.putBoolean("easy", e);
        prefs.flush();
    }

    /**
     * Style, picked on the title screen (saved between runs). MSX: the stages play as measured from the MSX
     * (map property "speedMsx", objects with style "msx"). REMAKE: the remake's own pacing and waves.
     */
    public boolean original;

    public void setOriginal(boolean o) {
        original = o;
        Level.originalStyle = o;
        prefs.putBoolean("original", o);
        prefs.flush();
    }

    /** Volume levels in steps of 10%: 0 = off, 10 = full. Saved between runs. */
    public int bgmLevel, sfxLevel;
    /** Seconds left to show the volume read-out after a change. */
    public float volumeMsgT;

    /** With plain libGDX music (no loop points, no MIDI): works on every platform. */
    public SalamanderGame() { this(new GdxMusicBackend()); }

    public SalamanderGame(MusicBackend musicBackend) { this.musicBackend = musicBackend; }

    @Override
    public void create() {
        batch = new SpriteBatch();
        assets = new Assets();
        bgm = new Bgm(musicBackend);
        prefs = Gdx.app.getPreferences("salamander");
        hiScore = prefs.getInteger("hi", 0);
        easy = prefs.getBoolean("easy", false);
        original = prefs.getBoolean("original", false);
        Level.originalStyle = original;
        bgmLevel = clampLevel(prefs.getInteger("bgmVolume", 7));
        sfxLevel = clampLevel(prefs.getInteger("sfxVolume", 10));
        applyVolumes();
        setScreen(new TitleScreen(this));
    }

    @Override
    public void render() {
        Controls.update();   // keyboard + gamepads, once per frame

        // Volume works on every screen: keys 1/2 = music, 3/4 = sound effects (pad: Back + d-pad)
        int b = bgmLevel, s = sfxLevel;
        if (Controls.pressed(Action.MUSIC_DOWN)) b--;
        if (Controls.pressed(Action.MUSIC_UP)) b++;
        if (Controls.pressed(Action.SFX_DOWN)) s--;
        if (Controls.pressed(Action.SFX_UP)) s++;
        b = clampLevel(b);
        s = clampLevel(s);
        if (b != bgmLevel || s != sfxLevel) {
            boolean sfxChanged = s != sfxLevel;
            bgmLevel = b;
            sfxLevel = s;
            applyVolumes();
            prefs.putInteger("bgmVolume", bgmLevel);
            prefs.putInteger("sfxVolume", sfxLevel);
            prefs.flush();
            volumeMsgT = 2f;
            if (sfxChanged) assets.play("pickup", 0.35f);   // let the player hear the new level
        }
        if (volumeMsgT > 0f) volumeMsgT -= Gdx.graphics.getDeltaTime();
        updateCursor();
        bgm.update(Gdx.graphics.getDeltaTime());   // music fade-outs (stage music -> boss music)
        super.render();
    }

    /** The mouse pointer hides itself after this many seconds without moving, and comes back as soon as it moves. */
    private static final float CURSOR_HIDE_DELAY = 3f;
    private float cursorIdleT;
    private int lastMouseX = Integer.MIN_VALUE, lastMouseY;
    private boolean cursorHidden;
    /** Invisible pointer, the fallback when GLFW can't be asked directly to hide it. */
    private Cursor blankCursor;

    /** Hides the mouse pointer once it has been still for CURSOR_HIDE_DELAY seconds (window or full screen). */
    private void updateCursor() {
        int mx = Gdx.input.getX(), my = Gdx.input.getY();
        boolean moved = mx != lastMouseX || my != lastMouseY || Gdx.input.getDeltaX() != 0 || Gdx.input.getDeltaY() != 0;
        lastMouseX = mx;
        lastMouseY = my;
        if (moved) cursorIdleT = 0f;
        else cursorIdleT += Gdx.graphics.getDeltaTime();
        boolean hide = cursorIdleT >= CURSOR_HIDE_DELAY;
        if (hide != cursorHidden) setCursorVisible(!hide);
    }

    private void setCursorVisible(boolean visible) {
        cursorHidden = !visible;
        if (glfwCursor(visible)) return;
        try {   // fallback: a see-through pointer picture
            if (visible) {
                Gdx.graphics.setSystemCursor(Cursor.SystemCursor.Arrow);
            } else {
                if (blankCursor == null) {
                    Pixmap pm = new Pixmap(16, 16, Pixmap.Format.RGBA8888);   // all see-through
                    blankCursor = Gdx.graphics.newCursor(pm, 0, 0);
                    pm.dispose();
                }
                Gdx.graphics.setCursor(blankCursor);
            }
        } catch (RuntimeException e) {
            Gdx.app.error("Salamander", "Could not change the mouse pointer", e);
        }
    }

    /**
     * Desktop (LWJGL3): tells GLFW to hide / show the pointer over the game window, which also works in macOS full
     * screen. Done by reflection so the core module doesn't depend on the desktop backend. Returns false if not possible.
     */
    private boolean glfwCursor(boolean visible) {
        try {
            Object window = Gdx.graphics.getClass().getMethod("getWindow").invoke(Gdx.graphics);
            long handle = (Long) window.getClass().getMethod("getWindowHandle").invoke(window);
            Class<?> glfw = Class.forName("org.lwjgl.glfw.GLFW");
            final int GLFW_CURSOR = 0x00033001, NORMAL = 0x00034001, HIDDEN = 0x00034002;
            glfw.getMethod("glfwSetInputMode", long.class, int.class, int.class).invoke(null, handle, GLFW_CURSOR, visible ? NORMAL : HIDDEN);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /** e.g. "MUSIC 70%   SFX 100%" */
    public String volumeText() {
        return "MUSIC " + bgmLevel * 10 + "%   SFX " + sfxLevel * 10 + "%";
    }

    private void applyVolumes() {
        bgm.setVolume(bgmLevel / 10f);
        assets.sfxVolume = sfxLevel / 10f;
    }

    private static int clampLevel(int v) { return Math.max(0, Math.min(10, v)); }

    public void submitScore(int s) {
        if (s > hiScore) {
            hiScore = s;
            prefs.putInteger("hi", s);
            prefs.flush();
        }
    }

    @Override
    public void dispose() {
        super.dispose();
        batch.dispose();
        assets.dispose();
        bgm.dispose();
        if (blankCursor != null) blankCursor.dispose();
    }
}
