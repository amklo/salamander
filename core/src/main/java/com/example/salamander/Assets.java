package com.example.salamander;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.TextureRegion;

import java.util.HashMap;
import java.util.Map;

/**
 * Loads sprites lazily from assets/sprites/NAME.png. Animated sprites are horizontal strips,
 * so to swap art just overwrite the PNG with one of the same frame size.
 */
public class Assets {
    private final Map<String, Texture> textures = new HashMap<>();
    private final Map<String, TextureRegion[]> strips = new HashMap<>();
    private final Map<String, Sound> sounds = new HashMap<>();
    public final BitmapFont font;
    /** Master sound-effect volume, 0..1 (multiplied into every play() call). */
    public float sfxVolume = 1f;
    /** tiles[level][0 = fill, 1 = floor surface, 2 = ceiling underside] */
    public final TextureRegion[][] tiles;

    public Assets() {
        font = new BitmapFont();
        font.getRegion().getTexture().setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        font.setUseIntegerPositions(false);
        tiles = TextureRegion.split(tex("tiles"), 16, 16);
    }

    public Texture tex(String name) {
        Texture t = textures.get(name);
        if (t == null) {
            t = new Texture(Gdx.files.internal("sprites/" + name + ".png"));
            t.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
            textures.put(name, t);
        }
        return t;
    }

    /** Texture by path inside the assets folder (e.g. "sprites/stage1_t0.png"), loaded once. */
    public Texture texFile(String path) {
        Texture t = textures.get(path);
        if (t == null) {
            t = new Texture(Gdx.files.internal(path));
            t.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
            textures.put(path, t);
        }
        return t;
    }

    public TextureRegion[] strip(String name, int frameW) {
        String key = name + ":" + frameW;
        TextureRegion[] r = strips.get(key);
        if (r == null) {
            Texture t = tex(name);
            int n = Math.max(1, t.getWidth() / frameW);
            r = new TextureRegion[n];
            for (int i = 0; i < n; i++) r[i] = new TextureRegion(t, i * frameW, 0, frameW, t.getHeight());
            strips.put(key, r);
        }
        return r;
    }

    public TextureRegion one(String name) {
        return strip(name, tex(name).getWidth())[0];
    }

    public void play(String name, float volume) {
        if (!sounds.containsKey(name)) {
            Sound s = null;
            try {
                s = Gdx.audio.newSound(Gdx.files.internal("sfx/" + name + ".wav"));
            } catch (Exception ignored) {
                // no audio device or missing file: stay silent
            }
            sounds.put(name, s);
        }
        Sound s = sounds.get(name);
        if (s != null && sfxVolume > 0f) s.play(volume * sfxVolume);
    }

    public void dispose() {
        for (Texture t : textures.values()) t.dispose();
        for (Sound s : sounds.values()) if (s != null) s.dispose();
        font.dispose();
    }
}
