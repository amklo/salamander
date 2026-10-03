package com.example.salamander;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.example.salamander.Controls.Action;
import com.badlogic.gdx.Preferences;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;

public class SalamanderGame extends Game {
    /** Virtual resolution: 30 x 17 tiles of 16px. */
    public static final int W = 480, H = 272, T = 16;

    public SpriteBatch batch;
    public Assets assets;
    public Bgm bgm;
    public int hiScore;
    private Preferences prefs;

    /** Volume levels in steps of 10%: 0 = off, 10 = full. Saved between runs. */
    public int bgmLevel, sfxLevel;
    /** Seconds left to show the volume read-out after a change. */
    public float volumeMsgT;

    @Override
    public void create() {
        batch = new SpriteBatch();
        assets = new Assets();
        bgm = new Bgm();
        prefs = Gdx.app.getPreferences("salamander");
        hiScore = prefs.getInteger("hi", 0);
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
        super.render();
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
    }
}
