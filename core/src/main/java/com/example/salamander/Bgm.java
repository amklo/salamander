package com.example.salamander;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Music;

import javax.sound.midi.MidiMessage;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.Receiver;
import javax.sound.midi.Sequencer;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.Synthesizer;
import javax.sound.midi.SysexMessage;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.HashMap;

/**
 * Background music player for the files in assets/bgm: MP3 / OGG / WAV through libGDX's Music, MIDI through the
 * JDK's built-in sequencer and synthesizer (desktop only). Loops the current song until stopped (play(name, false)
 * plays it once). If a song can't be played the game simply stays silent.
 *
 * A song with a loop length (play(name, true, loopLength)) is decoded into memory once and played through Java Sound's
 * Clip instead: it plays from the start to (just before) the end of the file, then jumps back loopLength seconds,
 * sample-exact and without a gap (libGDX's Music can only loop back to the very start). The last LOOP_BLEND seconds
 * before the jump are blended into the audio just before the loop start, so the seam is smooth.
 */
public class Bgm {
    private Sequencer seq;
    private Synthesizer synth;
    private VolumeFilter filter;
    private String current;
    private boolean broken;
    private float volume = 1f;
    /**
     * Fade-out in progress (see fadeOutThen): 1 = full volume ... 0 = silent; the song to play once it is silent, and
     * the seconds of silence left before it starts (-1 = still fading).
     */
    private float fade = 1f, fadeSpeed, gapLeft = -1f, gap;
    private String nextName;
    private boolean nextLoop, paused;
    private float nextLoopLength, nextLoopEnd;

    /** The volume the current song plays at: the music volume times the fade-out. */
    private float vol() { return volume * fade; }
    /** The song playing when it is an MP3 / OGG / WAV (null for MIDI). */
    private Music music;
    /** The song playing when it has a loop length (Java Sound). */
    private Clip clip;
    /** Decoded, loop-ready songs (they are decoded only once): name -> loop. */
    private static final java.util.concurrent.ConcurrentHashMap<String, Loop> loops = new java.util.concurrent.ConcurrentHashMap<>();
    /** Songs being decoded in the background (see preload); touched on the game thread only. */
    private final HashMap<String, Thread> loading = new HashMap<>();
    /** Decoders the background threads are done with: disposed on the game thread (in update). */
    private final java.util.ArrayList<Music> finished = new java.util.ArrayList<>();
    /** Length of the blend at the loop seam, and how far before the end of the decoded file the loop ends (seconds). */
    static final float LOOP_BLEND = 0.03f, LOOP_END_MARGIN = 0.06f;

    /** A song ready to loop: 16-bit PCM cut at the loop end, and the frame it jumps back to. */
    static final class Loop {
        final AudioFormat format;
        final byte[] pcm;
        final int startFrame;

        Loop(AudioFormat format, byte[] pcm, int startFrame) { this.format = format; this.pcm = pcm; this.startFrame = startFrame; }
    }

    /** Starts NAME (e.g. "02. Power of Anger.mp3") from the beginning and loops it, unless it is already playing. */
    public void play(String name) { play(name, true); }

    /** Starts NAME from the beginning (looping or once), unless it is already the current song. */
    public void play(String name, boolean loop) { play(name, loop, -1f); }

    /** As play(name, loop, loopLength, -1): the loop ends just before the end of the file. */
    public void play(String name, boolean loop, float loopLength) { play(name, loop, loopLength, -1f); }

    /**
     * As play(name, loop); with loopLength > 0 (seconds) the song plays to loopEnd (seconds from its start; <= 0 = just
     * before the end of the file) and then jumps back loopLength seconds, instead of starting over (MP3 / OGG / WAV).
     * Whatever comes after loopEnd in the file (a fade-out) is never heard.
     */
    public void play(String name, boolean loop, float loopLength, float loopEnd) {
        nextName = null;                             // (a song asked for directly cancels a fade-out)
        if (fade < 1f) { fade = 1f; applyVolumes(); }
        paused = false;
        if (name.equals(current)) { resume(); return; }
        stop();
        if (loop && loopLength > 0f && !name.toLowerCase().endsWith(".mid") && playLooped(name, loopLength, loopEnd)) return;
        if (!name.toLowerCase().endsWith(".mid")) {
            try {
                music = Gdx.audio.newMusic(Gdx.files.internal("bgm/" + name));
                music.setLooping(loop);
                music.setVolume(vol());
                music.play();
                current = name;
            } catch (Exception e) {
                Gdx.app.error("Bgm", "Cannot play bgm/" + name, e);
                music = null;
            }
            return;
        }
        if (broken) return;
        try {
            if (seq == null) open();
            try (InputStream in = new BufferedInputStream(Gdx.files.internal("bgm/" + name).read())) {
                seq.setSequence(in);
            }
            seq.setLoopStartPoint(0);
            seq.setLoopEndPoint(-1);
            seq.setLoopCount(loop ? Sequencer.LOOP_CONTINUOUSLY : 0);
            seq.setTickPosition(0);
            filter.resetChannels();
            seq.start();
            current = name;
        } catch (Exception e) {
            Gdx.app.error("Bgm", "Cannot play bgm/" + name, e);
            broken = seq == null;
        }
    }

    /** Plays the song through a Clip that loops back loopLength seconds from its end. false = could not do it. */
    private boolean playLooped(String name, float loopLength, float loopEnd) {
        try {
            Thread t = loading.remove(name);
            if (t != null) t.join();                 // (being preloaded: wait for it, it is usually done)
            Loop l = loops.get(name);
            if (l == null) {
                l = decodeLoop(name, loopLength, loopEnd);
                loops.put(name, l);
            }
            clip = AudioSystem.getClip();
            clip.open(l.format, l.pcm, 0, l.pcm.length);
            clip.setLoopPoints(l.startFrame, -1);   // -1: the last frame
            applyClipVolume();
            clip.setFramePosition(0);
            clip.loop(Clip.LOOP_CONTINUOUSLY);
            current = name;
            return true;
        } catch (Throwable e) {
            Gdx.app.error("Bgm", "Cannot loop bgm/" + name + " with Java Sound, playing it the normal way", e);
            if (clip != null) { clip.close(); clip = null; }
            return false;
        }
    }

    /**
     * Decodes a song for a seamless loop in the background, so that playing it later (e.g. the boss music) doesn't
     * stall the game for the moment decoding takes. Call it on the game thread.
     */
    public void preload(String name, float loopLength, float loopEnd) {
        if (name == null || loopLength <= 0f || name.toLowerCase().endsWith(".mid") || loops.containsKey(name) || loading.containsKey(name)) return;
        try {
            final Music m = name.toLowerCase().endsWith(".wav") ? null : Gdx.audio.newMusic(Gdx.files.internal("bgm/" + name));
            Thread t = new Thread(() -> {
                try {
                    loops.put(name, makeLoop(decodePcm(name, m), loopLength, loopEnd));
                } catch (Throwable e) {
                    Gdx.app.error("Bgm", "Cannot preload bgm/" + name, e);   // (playing it will try again)
                } finally {
                    if (m != null) synchronized (finished) { finished.add(m); }
                }
            }, "bgm-preload");
            t.setDaemon(true);
            loading.put(name, t);
            t.start();
        } catch (Throwable e) {
            Gdx.app.error("Bgm", "Cannot preload bgm/" + name, e);
        }
    }

    /** Decodes the whole song (on this thread) and makes it loop-ready. */
    private static Loop decodeLoop(String name, float loopLength, float loopEnd) throws Exception {
        Music m = name.toLowerCase().endsWith(".wav") ? null : Gdx.audio.newMusic(Gdx.files.internal("bgm/" + name));
        try {
            return makeLoop(decodePcm(name, m), loopLength, loopEnd);
        } finally {
            if (m != null) m.dispose();
        }
    }

    /**
     * The whole song as 16-bit PCM {format, bytes}: a WAV through Java Sound, anything else through libGDX's own decoder
     * m (its desktop Music has a public read(byte[]) that returns little-endian 16-bit PCM). Safe on any thread.
     */
    private static Object[] decodePcm(String name, Music m) throws Exception {
        if (m == null) {
            try (AudioInputStream in = AudioSystem.getAudioInputStream(new BufferedInputStream(Gdx.files.internal("bgm/" + name).read()))) {
                AudioFormat fmt = in.getFormat();
                if (fmt.getSampleSizeInBits() != 16 || fmt.isBigEndian()) throw new IllegalArgumentException("needs 16-bit little-endian PCM");
                return new Object[]{fmt, readAll(in)};
            }
        }
        Method read = m.getClass().getMethod("read", byte[].class);
        int channels = (Integer) m.getClass().getMethod("getChannels").invoke(m);
        int rate = (Integer) m.getClass().getMethod("getRate").invoke(m);
        ByteArrayOutputStream out = new ByteArrayOutputStream(rate * channels * 2 * 64);
        byte[] buf = new byte[4096 * channels * 2];
        for (int n; (n = (Integer) read.invoke(m, (Object) buf)) > 0; ) out.write(buf, 0, n);
        return new Object[]{new AudioFormat(rate, 16, channels, true, false), out.toByteArray()};
    }

    private static Loop makeLoop(Object[] decoded, float loopLength, float loopEnd) {
        return makeLoop((AudioFormat) decoded[0], (byte[]) decoded[1], loopLength, loopEnd);
    }

    private static byte[] readAll(InputStream in) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[65536];
        for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
        return out.toByteArray();
    }

    /**
     * Cuts the PCM at loopEnd (seconds; <= 0 or past the end: LOOP_END_MARGIN before the end of the file, past any
     * silence a decoder adds there), sets the loop start loopLength earlier and blends the last LOOP_BLEND seconds before the cut into the audio just before the loop
     * start: at the jump the sound then carries straight on.
     */
    static Loop makeLoop(AudioFormat fmt, byte[] pcm, float loopLength, float loopEnd) {
        int ch = fmt.getChannels(), frameBytes = 2 * ch;
        float rate = fmt.getSampleRate();
        int frames = pcm.length / frameBytes;
        int end = frames - Math.round(LOOP_END_MARGIN * rate);
        if (loopEnd > 0f) end = Math.min(end, Math.round(loopEnd * rate));
        int start = end - Math.round(loopLength * rate);
        int blend = Math.round(LOOP_BLEND * rate);
        if (start < blend) throw new IllegalArgumentException("song too short for a loop of " + loopLength + " s");
        for (int k = 0; k < blend; k++) {
            double t = (k + 0.5) / blend;
            double fadeOut = Math.cos(t * Math.PI / 2), fadeIn = Math.sin(t * Math.PI / 2);
            int a = (end - blend + k) * frameBytes, b = (start - blend + k) * frameBytes;
            for (int c = 0; c < ch; c++) {
                int ia = a + 2 * c, ib = b + 2 * c;
                int sa = (short) ((pcm[ia] & 0xff) | (pcm[ia + 1] << 8)), sb = (short) ((pcm[ib] & 0xff) | (pcm[ib + 1] << 8));
                int v = (int) Math.round(sa * fadeOut + sb * fadeIn);
                v = Math.max(-32768, Math.min(32767, v));
                pcm[ia] = (byte) v;
                pcm[ia + 1] = (byte) (v >> 8);
            }
        }
        return new Loop(fmt, java.util.Arrays.copyOf(pcm, end * frameBytes), start);
    }

    private void applyClipVolume() {
        if (clip == null || !clip.isControlSupported(FloatControl.Type.MASTER_GAIN)) return;
        FloatControl g = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
        float db = vol() <= 0.0001f ? g.getMinimum() : (float) (20.0 * Math.log10(vol()));
        g.setValue(Math.max(g.getMinimum(), Math.min(g.getMaximum(), db)));
    }

    /** The sequencer feeds the synthesizer through a filter that scales every channel-volume message. */
    private void open() throws Exception {
        synth = MidiSystem.getSynthesizer();
        synth.open();
        filter = new VolumeFilter(synth.getReceiver());
        try {
            seq = MidiSystem.getSequencer(false);   // not auto-connected: we wire it ourselves
            seq.getTransmitter().setReceiver(filter);
            seq.open();
        } catch (Exception e) {
            synth.close();
            synth = null;
            seq = null;
            throw e;
        }
    }

    /** Music volume, 0 (silent) .. 1 (as the MIDI file was written). */
    public void setVolume(float v) {
        volume = Math.max(0f, Math.min(1f, v));
        applyVolumes();
    }

    private void applyVolumes() {
        if (filter != null) filter.applyAll();
        if (music != null) music.setVolume(vol());
        applyClipVolume();
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
        synchronized (finished) {                    // decoders the preload threads are done with
            for (Music m : finished) m.dispose();
            finished.clear();
        }
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
        if (clip != null) { clip.stop(); return; }
        if (music != null) { music.pause(); return; }
        if (seq != null && seq.isOpen() && seq.isRunning()) seq.stop();
    }

    /** Continues the current song after pause(). */
    public void resume() {
        paused = false;
        if (clip != null) { if (!clip.isRunning()) clip.loop(Clip.LOOP_CONTINUOUSLY); return; }   // (from where it stopped)
        if (music != null) { if (!music.isPlaying() && music.isLooping()) music.play(); return; }
        if (seq != null && seq.isOpen() && current != null && !seq.isRunning()) seq.start();
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
        if (clip != null) { clip.stop(); clip.close(); clip = null; }
        if (music != null) { music.stop(); music.dispose(); music = null; }
        if (seq != null && seq.isOpen() && seq.isRunning()) seq.stop();
        current = null;
    }

    public void dispose() {
        if (clip != null) { clip.close(); clip = null; }
        if (music != null) { music.dispose(); music = null; }
        if (seq != null) seq.close();
        if (synth != null) synth.close();
        seq = null;
        synth = null;
        current = null;
    }

    /** Remembers each channel's volume (CC7) as set by the song and forwards it scaled by {@link #volume}. */
    private class VolumeFilter implements Receiver {
        private final Receiver out;
        private final int[] songVol = new int[16];

        VolumeFilter(Receiver out) {
            this.out = out;
            resetChannels();
        }

        void resetChannels() {
            java.util.Arrays.fill(songVol, 100);   // General MIDI default channel volume
            applyAll();
        }

        void applyAll() {
            for (int ch = 0; ch < 16; ch++) sendVol(ch);
        }

        private void sendVol(int ch) {
            try {
                ShortMessage m = new ShortMessage(ShortMessage.CONTROL_CHANGE, ch, 7, Math.round(songVol[ch] * vol()));
                out.send(m, -1);
            } catch (Exception ignored) {
            }
        }

        @Override
        public void send(MidiMessage msg, long timeStamp) {
            if (msg instanceof ShortMessage) {
                ShortMessage sm = (ShortMessage) msg;
                if (sm.getCommand() == ShortMessage.CONTROL_CHANGE && sm.getData1() == 7) {
                    songVol[sm.getChannel()] = sm.getData2();
                    sendVol(sm.getChannel());
                    return;
                }
            }
            out.send(msg, timeStamp);
            if (msg instanceof SysexMessage) resetChannels();   // e.g. a GM reset restores default volumes
        }

        @Override
        public void close() {
        }
    }
}
