package com.example.salamander;

/**
 * Background music for the files in assets/bgm. Loops the current song until stopped (play(name, false) plays it
 * once), fades it out into the next one (stage music -> boss music) and pauses / resumes it. If a song can't be played
 * the game simply stays silent.
 *
 * The sound itself comes from a {@link MusicBackend}, one per platform: on the desktop (lwjgl3 module,
 * DesktopMusicBackend) a song with a loop length plays to its loop end and then jumps back loopLength seconds,
 * sample-exact and without a gap, and MIDI files play through the JDK's synthesizer.
 */
public class Bgm {
    private final MusicBackend out;
    private String current;
    private float volume = 1f;
    /**
     * Fade-out in progress (see fadeOutThen): 1 = full volume ... 0 = silent; the song to play once it is silent, and
     * the seconds of silence left before it starts (-1 = still fading).
     */
    private float fade = 1f, fadeSpeed, gapLeft = -1f, gap;
    private String nextName;
    private boolean nextLoop, paused;
    private float nextLoopLength, nextLoopEnd;

    public Bgm(MusicBackend out) { this.out = out; }

    /** The volume the current song plays at: the music volume times the fade-out. */
    private float vol() { return volume * fade; }

    /** Starts NAME (e.g. "02. Power of Anger.mp3") from the beginning and loops it, unless it is already playing. */
    public void play(String name) { play(name, true); }

    /** Starts NAME from the beginning (looping or once), unless it is already the current song. */
    public void play(String name, boolean loop) { play(name, loop, -1f); }

    /** As play(name, loop, loopLength, -1): the loop ends just before the end of the file. */
    public void play(String name, boolean loop, float loopLength) { play(name, loop, loopLength, -1f); }

    /**
     * As play(name, loop); with loopLength > 0 (seconds) the song plays to loopEnd (seconds from its start; <= 0 = just
     * before the end of the file) and then jumps back loopLength seconds, instead of starting over (if the platform
     * can). Whatever comes after loopEnd in the file (a fade-out) is never heard.
     */
    public void play(String name, boolean loop, float loopLength, float loopEnd) {
        nextName = null;                             // (a song asked for directly cancels a fade-out)
        if (fade < 1f) { fade = 1f; applyVolumes(); }
        paused = false;
        if (name.equals(current)) { resume(); return; }
        stop();
        out.setVolume(vol());
        if (out.start(name, loop, loopLength, loopEnd)) current = name;
    }

    /**
     * Gets a song with a loop length ready in the background, so that playing it later (e.g. the boss music) doesn't
     * stall the game. Call it on the game thread.
     */
    public void preload(String name, float loopLength, float loopEnd) {
        if (name == null || loopLength <= 0f) return;
        out.preload(name, loopLength, loopEnd);
    }

    /** Music volume, 0 (silent) .. 1 (full). */
    public void setVolume(float v) {
        volume = Math.max(0f, Math.min(1f, v));
        applyVolumes();
    }

    private void applyVolumes() {
        out.setVolume(vol());
    }

    /**
     * Fades the current song out over `seconds`, then starts NAME (as play(name, loop, loopLength, loopEnd)). Nothing
     * playing: NAME starts right away. Asking again for the song that is already playing or coming is ignored.
     */
    public void fadeOutThen(String name, boolean loop, float loopLength, float loopEnd, float seconds) {
        fadeOutThen(name, loop, loopLength, loopEnd, seconds, 0f);
    }

    /** As above, with `silence` seconds of quiet between the end of the fade-out and the start of NAME. */
    public void fadeOutThen(String name, boolean loop, float loopLength, float loopEnd, float seconds, float silence) {
        if (name.equals(current) || name.equals(nextName)) return;
        if (current == null || seconds <= 0f) { play(name, loop, loopLength, loopEnd); return; }
        nextName = name;
        nextLoop = loop;
        nextLoopLength = loopLength;
        nextLoopEnd = loopEnd;
        fadeSpeed = 1f / seconds;
        gap = Math.max(0f, silence);
        gapLeft = -1f;
    }

    /** Call once per frame: runs a fade-out (it waits while the music is paused). */
    public void update(float dt) {
        out.update();
        if (nextName == null || paused) return;
        if (gapLeft < 0f) {                          // fading out
            fade -= dt * fadeSpeed;
            if (fade > 0f) { applyVolumes(); return; }
            stopSong();                              // silent now: stop it, then wait out the gap
            fade = 1f;
            gapLeft = gap;
        }
        gapLeft -= dt;
        if (gapLeft > 0f) return;
        String n = nextName;
        stop();
        play(n, nextLoop, nextLoopLength, nextLoopEnd);
    }

    /** Pauses the current song (keeps its position). */
    public void pause() {
        paused = true;
        out.pause();
    }

    /** Continues the current song after pause(). */
    public void resume() {
        paused = false;
        if (current != null) out.resume();
    }

    /** Stops the music; the next play() starts from the beginning. */
    public void stop() {
        nextName = null;
        fade = 1f;
        gapLeft = -1f;
        stopSong();
    }

    /** Stops the current song (a pending fadeOutThen song stays pending). */
    private void stopSong() {
        out.stop();
        current = null;
    }

    public void dispose() {
        out.dispose();
        current = null;
    }
}
