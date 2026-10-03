package com.example.salamander;

import com.badlogic.gdx.Gdx;

import javax.sound.midi.MidiMessage;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.Receiver;
import javax.sound.midi.Sequencer;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.Synthesizer;
import javax.sound.midi.SysexMessage;
import java.io.BufferedInputStream;
import java.io.InputStream;

/**
 * Background music player for MIDI files in assets/bgm, using the JDK's built-in
 * MIDI sequencer and synthesizer (desktop only). Loops the current song until stopped.
 * If MIDI is unavailable the game simply stays silent.
 */
public class Bgm {
    private Sequencer seq;
    private Synthesizer synth;
    private VolumeFilter filter;
    private String current;
    private boolean broken;
    private float volume = 1f;

    /** Starts NAME (e.g. "Starfield_3.mid") from the beginning, unless it is already the current song. */
    public void play(String name) {
        if (broken) return;
        if (name.equals(current)) { resume(); return; }
        stop();
        try {
            if (seq == null) open();
            try (InputStream in = new BufferedInputStream(Gdx.files.internal("bgm/" + name).read())) {
                seq.setSequence(in);
            }
            seq.setLoopStartPoint(0);
            seq.setLoopEndPoint(-1);
            seq.setLoopCount(Sequencer.LOOP_CONTINUOUSLY);
            seq.setTickPosition(0);
            filter.resetChannels();
            seq.start();
            current = name;
        } catch (Exception e) {
            Gdx.app.error("Bgm", "Cannot play bgm/" + name, e);
            broken = seq == null;
        }
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
        if (filter != null) filter.applyAll();
    }

    /** Pauses the current song (keeps its position). */
    public void pause() {
        if (seq != null && seq.isOpen() && seq.isRunning()) seq.stop();
    }

    /** Continues the current song after pause(). */
    public void resume() {
        if (seq != null && seq.isOpen() && current != null && !seq.isRunning()) seq.start();
    }

    /** Stops the music; the next play() starts from the beginning. */
    public void stop() {
        if (seq != null && seq.isOpen() && seq.isRunning()) seq.stop();
        current = null;
    }

    public void dispose() {
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
