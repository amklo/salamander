package com.example.salamander.teavm;

import com.badlogic.gdx.Gdx;
import com.example.salamander.GdxMusicBackend;
import org.teavm.jso.JSBody;
import org.teavm.jso.typedarrays.Int8Array;

/**
 * Web music with loop points, on the browser's Web Audio API: a song is decoded once and played by an
 * AudioBufferSourceNode, whose loopStart / loopEnd make the jump back sample-exact and gapless (like the desktop's
 * Java Sound Clip). It uses Howler's AudioContext (the one libGDX's web sounds use), so the browser's "no sound before
 * the first key press / click" rule is handled the same way, and Howler is told not to switch the audio off after
 * 30 s without sound effects.
 *
 * A decoded song takes ~60 MB (3 minutes of 32-bit float stereo), so only the current song and the last preloaded one
 * (the boss music) are kept. If Web Audio fails, the song plays through plain libGDX Music instead (GdxMusicBackend:
 * loops back to the start).
 */
public class WebMusicBackend extends GdxMusicBackend {
    /** How far before the end of the decoded file the loop ends when no loop end is given (seconds; as on the desktop). */
    static final float LOOP_END_MARGIN = 0.06f;
    private static boolean installed;

    @Override
    public boolean start(String name, boolean loop, float loopLength, float loopEnd) {
        if (name.toLowerCase().endsWith(".mid")) return false;
        try {
            install();
            decode(name);
            jsStart(name, loop, loopLength, loopEnd, LOOP_END_MARGIN, volume);
            return true;
        } catch (Throwable e) {
            Gdx.app.error("Bgm", "Web Audio cannot play bgm/" + name + ", playing it the normal way", e);
            return super.start(name, loop, loopLength, loopEnd);
        }
    }

    @Override
    public void preload(String name, float loopLength, float loopEnd) {
        if (name.toLowerCase().endsWith(".mid")) return;
        try {
            install();
            jsKeep(name);
            decode(name);
        } catch (Throwable e) {
            Gdx.app.error("Bgm", "Cannot preload bgm/" + name, e);
        }
    }

    /** Hands the song's bytes to the browser to decode (in the background), unless it has them already. */
    private static void decode(String name) {
        if (jsHas(name)) return;
        byte[] bytes = Gdx.files.internal("bgm/" + name).readBytes();
        jsDecode(name, Int8Array.copyFromJavaArray(bytes));
    }

    @Override
    public void setVolume(float v) {
        super.setVolume(v);
        if (installed) jsVolume(v);
    }

    @Override
    public void pause() {
        super.pause();
        if (installed) jsPause();
    }

    @Override
    public void resume() {
        super.resume();
        if (installed) jsResume();
    }

    @Override
    public void stop() {
        super.stop();
        if (installed) jsStop();
    }

    @Override
    public void dispose() {
        super.dispose();
        if (installed) jsStop();
    }

    private static void install() {
        if (installed) return;
        jsInstall();
        installed = true;
    }

    // ---------------------------------------------------------------- JavaScript side (window.salamanderMusic)

    @JSBody(script = """
            if (window.salamanderMusic) return;
            var M = {buffers: {}, decoding: {}, keep: null, cur: null, src: null, gain: null, vol: 1, c: null, out: null};
            M.ctx = function () {
                if (M.c) return M.c;
                if (typeof Howler !== 'undefined' && Howler.usingWebAudio) {
                    Howler.volume();                           // (makes Howler create its AudioContext if it hasn't yet)
                    if (Howler.ctx) {
                        Howler.autoSuspend = false;            // else it suspends the audio 30 s after the last sound effect
                        M.c = Howler.ctx;
                        M.out = Howler.masterGain || Howler.ctx.destination;
                    }
                }
                if (!M.c) {
                    var C = window.AudioContext || window.webkitAudioContext;
                    M.c = new C();
                    M.out = M.c.destination;
                }
                var unlock = function () { if (M.c.state !== 'running') M.c.resume(); };
                ['keydown', 'mousedown', 'touchend'].forEach(function (t) { window.addEventListener(t, unlock, true); });
                return M.c;
            };
            M.trim = function () {                             // keep only the current and the preloaded song
                for (var n in M.buffers) if (n !== M.keep && !(M.cur && M.cur.name === n)) delete M.buffers[n];
            };
            M.decode = function (name, bytes) {
                if (M.buffers[name] || M.decoding[name]) return;
                M.decoding[name] = true;
                var ab = bytes.buffer.slice(bytes.byteOffset, bytes.byteOffset + bytes.byteLength);
                M.ctx().decodeAudioData(ab, function (buf) {
                    delete M.decoding[name];
                    if (name !== M.keep && !(M.cur && M.cur.name === name)) return;   // no longer wanted
                    M.buffers[name] = buf;
                    var c = M.cur;
                    if (c && c.name === name && !M.src && !c.paused && !c.done) M.play(c.offset);
                }, function (e) {
                    delete M.decoding[name];
                    console.error('Bgm: cannot decode bgm/' + name, e);
                });
            };
            M.start = function (name, loop, loopLength, loopEnd, margin, vol) {
                M.stop();
                M.vol = vol;
                M.cur = {name: name, loop: loop, loopLength: loopLength, loopEnd: loopEnd, margin: margin,
                         paused: false, done: false, offset: 0, ls: 0, le: 0, t0: 0};
                M.trim();
                if (M.buffers[name]) M.play(0);                // (else it starts once decoded)
            };
            M.play = function (offset) {
                var c = M.cur, buf = M.buffers[c.name], ctx = M.ctx();
                var s = ctx.createBufferSource(), g = ctx.createGain();
                s.buffer = buf;
                g.gain.value = M.vol;
                s.connect(g);
                g.connect(M.out);
                if (c.loop) {
                    s.loop = true;
                    if (c.loopLength > 0) {
                        var end = buf.duration - c.margin;
                        if (c.loopEnd > 0) end = Math.min(end, c.loopEnd);
                        var start = end - c.loopLength;
                        if (start > 0) { s.loopStart = start; s.loopEnd = end; c.ls = start; c.le = end; }
                    }
                }
                s.onended = function () { if (M.src === s) { M.src = null; M.gain = null; c.done = true; } };
                s.start(0, offset);
                M.src = s;
                M.gain = g;
                c.t0 = ctx.currentTime - offset;
            };
            M.position = function () {                         // seconds into the file, loops taken into account
                var c = M.cur, buf = M.buffers[c.name], t = M.ctx().currentTime - c.t0;
                if (c.loop) {
                    if (c.le > 0 && t > c.le) t = c.ls + (t - c.le) % (c.le - c.ls);
                    else if (c.le === 0 && buf) t = t % buf.duration;
                }
                return t;
            };
            M.halt = function () {
                if (!M.src) return;
                M.src.onended = null;
                try { M.src.stop(); } catch (e) {}
                M.src.disconnect();
                M.gain.disconnect();
                M.src = null;
                M.gain = null;
            };
            M.pause = function () {
                var c = M.cur;
                if (!c || c.paused) return;
                if (M.src) c.offset = M.position();
                M.halt();
                c.paused = true;
            };
            M.resume = function () {
                var c = M.cur;
                if (!c || !c.paused) return;
                c.paused = false;
                if (!c.done && M.buffers[c.name]) M.play(c.offset);
            };
            M.stop = function () { M.halt(); M.cur = null; };
            M.volume = function (v) { M.vol = v; if (M.gain) M.gain.gain.value = v; };
            window.salamanderMusic = M;
            """)
    private static native void jsInstall();

    @JSBody(params = "name", script = "var M = window.salamanderMusic; return !!(M.buffers[name] || M.decoding[name]);")
    private static native boolean jsHas(String name);

    @JSBody(params = {"name", "bytes"}, script = "window.salamanderMusic.decode(name, bytes);")
    private static native void jsDecode(String name, Int8Array bytes);

    @JSBody(params = "name", script = "window.salamanderMusic.keep = name;")
    private static native void jsKeep(String name);

    @JSBody(params = {"name", "loop", "loopLength", "loopEnd", "margin", "vol"},
            script = "window.salamanderMusic.start(name, loop, loopLength, loopEnd, margin, vol);")
    private static native void jsStart(String name, boolean loop, float loopLength, float loopEnd, float margin, float vol);

    @JSBody(params = "v", script = "window.salamanderMusic.volume(v);")
    private static native void jsVolume(float v);

    @JSBody(script = "window.salamanderMusic.pause();")
    private static native void jsPause();

    @JSBody(script = "window.salamanderMusic.resume();")
    private static native void jsResume();

    @JSBody(script = "window.salamanderMusic.stop();")
    private static native void jsStop();
}
