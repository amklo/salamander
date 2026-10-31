package com.example.salamander;

/**
 * What actually plays the background music for {@link Bgm}, one song at a time. Bgm keeps the platform-neutral part
 * (which song is current, fade-outs, the queued boss song, pausing); a backend only makes sound. Each platform passes
 * its own to SalamanderGame: the desktop one (lwjgl3 module) adds seamless loop points and MIDI; GdxMusicBackend
 * works everywhere.
 */
public interface MusicBackend {
    /**
     * Starts bgm/NAME from the beginning, at the volume last given to setVolume. loop: play it again and again (else
     * once). loopLength > 0 (seconds): when looping, jump back loopLength seconds from loopEnd (seconds from the
     * start; <= 0 = just before the end of the file) instead of to the start, if this backend can. false = it could
     * not be played (the game then stays silent).
     */
    boolean start(String name, boolean loop, float loopLength, float loopEnd);

    /** Gets a song with loop points ready ahead of time (e.g. the boss music), if this backend needs to. */
    void preload(String name, float loopLength, float loopEnd);

    /** Volume of the current and later songs, 0 (silent) .. 1. */
    void setVolume(float v);

    /** Pauses the current song (keeps its position). */
    void pause();

    /** Continues the current song after pause(). */
    void resume();

    /** Stops and releases the current song. */
    void stop();

    /** Called once per frame on the game thread. */
    void update();

    void dispose();
}
