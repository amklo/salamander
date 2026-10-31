package com.example.salamander;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Music;

/**
 * Plays the music through libGDX's Music, which every platform has. A looping song always starts over from the very
 * beginning of the file (loop points are ignored) and MIDI files are not played.
 */
public class GdxMusicBackend implements MusicBackend {
    /** The song playing (null: none). */
    protected Music music;
    protected float volume = 1f;

    @Override
    public boolean start(String name, boolean loop, float loopLength, float loopEnd) {
        if (name.toLowerCase().endsWith(".mid")) return false;
        try {
            music = Gdx.audio.newMusic(Gdx.files.internal("bgm/" + name));
            music.setLooping(loop);
            music.setVolume(volume);
            music.play();
            return true;
        } catch (Exception e) {
            Gdx.app.error("Bgm", "Cannot play bgm/" + name, e);
            if (music != null) music.dispose();
            music = null;
            return false;
        }
    }

    @Override
    public void preload(String name, float loopLength, float loopEnd) {
    }

    @Override
    public void setVolume(float v) {
        volume = v;
        if (music != null) music.setVolume(v);
    }

    @Override
    public void pause() {
        if (music != null) music.pause();
    }

    @Override
    public void resume() {
        if (music != null && !music.isPlaying() && music.isLooping()) music.play();   // (a song played once stays stopped)
    }

    @Override
    public void stop() {
        if (music != null) { music.stop(); music.dispose(); music = null; }
    }

    @Override
    public void update() {
    }

    @Override
    public void dispose() {
        if (music != null) { music.dispose(); music = null; }
    }
}
