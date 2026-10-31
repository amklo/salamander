package com.example.salamander.lwjgl3;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Music;
import com.example.salamander.GdxMusicBackend;

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
 * Desktop music: MP3 / OGG / WAV through libGDX's Music (GdxMusicBackend), MIDI through the JDK's built-in sequencer
 * and synthesizer.
 *
 * A song with a loop length is decoded into memory once and played through Java Sound's Clip instead: it plays from
 * the start to (just before) the end of the file, then jumps back loopLength seconds, sample-exact and without a gap
 * (libGDX's Music can only loop back to the very start). The last LOOP_BLEND seconds before the jump are blended into
 * the audio just before the loop start, so the seam is smooth.
 */
public class DesktopMusicBackend extends GdxMusicBackend {
    private Sequencer seq;
    private Synthesizer synth;
    private VolumeFilter filter;
    private boolean broken;
    /** A MIDI song is loaded in the sequencer (so resume() may start it again). */
    private boolean midiLoaded;
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

    @Override
    public boolean start(String name, boolean loop, float loopLength, float loopEnd) {
        boolean midi = name.toLowerCase().endsWith(".mid");
        if (loop && loopLength > 0f && !midi && playLooped(name, loopLength, loopEnd)) return true;
        if (!midi) return super.start(name, loop, loopLength, loopEnd);
        if (broken) return false;
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
            midiLoaded = true;
            return true;
        } catch (Exception e) {
            Gdx.app.error("Bgm", "Cannot play bgm/" + name, e);
            broken = seq == null;
            return false;
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
            return true;
        } catch (Throwable e) {
            Gdx.app.error("Bgm", "Cannot loop bgm/" + name + " with Java Sound, playing it the normal way", e);
            if (clip != null) { clip.close(); clip = null; }
            return false;
        }
    }

    /**
     * Decodes a song for a seamless loop in the background, so that playing it later (e.g. the boss music) doesn't
     * stall the game for the moment decoding takes. Called on the game thread.
     */
    @Override
    public void preload(String name, float loopLength, float loopEnd) {
        if (name.toLowerCase().endsWith(".mid") || loops.containsKey(name) || loading.containsKey(name)) return;
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
        float db = volume <= 0.0001f ? g.getMinimum() : (float) (20.0 * Math.log10(volume));
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

    @Override
    public void setVolume(float v) {
        volume = v;
        if (filter != null) filter.applyAll();
        super.setVolume(v);
        applyClipVolume();
    }

    @Override
    public void update() {
        synchronized (finished) {                    // decoders the preload threads are done with
            for (Music m : finished) m.dispose();
            finished.clear();
        }
    }

    @Override
    public void pause() {
        if (clip != null) { clip.stop(); return; }
        if (music != null) { super.pause(); return; }
        if (seq != null && seq.isOpen() && seq.isRunning()) seq.stop();
    }

    @Override
    public void resume() {
        if (clip != null) { if (!clip.isRunning()) clip.loop(Clip.LOOP_CONTINUOUSLY); return; }   // (from where it stopped)
        if (music != null) { super.resume(); return; }
        if (seq != null && seq.isOpen() && midiLoaded && !seq.isRunning()) seq.start();
    }

    @Override
    public void stop() {
        if (clip != null) { clip.stop(); clip.close(); clip = null; }
        super.stop();
        if (seq != null && seq.isOpen() && seq.isRunning()) seq.stop();
        midiLoaded = false;
    }

    @Override
    public void dispose() {
        if (clip != null) { clip.close(); clip = null; }
        super.dispose();
        if (seq != null) seq.close();
        if (synth != null) synth.close();
        seq = null;
        synth = null;
        midiLoaded = false;
    }

    /** Remembers each channel's volume (CC7) as set by the song and forwards it scaled by the music volume. */
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
                ShortMessage m = new ShortMessage(ShortMessage.CONTROL_CHANGE, ch, 7, Math.round(songVol[ch] * volume));
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
