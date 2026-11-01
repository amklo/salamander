package com.example.salamander.teavm;

import com.example.salamander.SalamanderGame;
import com.github.xpenatan.gdx.teavm.backends.web.WebApplication;
import com.github.xpenatan.gdx.teavm.backends.web.WebApplicationConfiguration;

/**
 * Web launcher (TeaVM): the game fills the browser window. Music through WebMusicBackend (Web Audio, with the songs'
 * loop points, like the desktop).
 */
public class WebLauncher {
    public static void main(String[] args) {
        WebApplicationConfiguration cfg = new WebApplicationConfiguration();
        cfg.width = 0;                      // 0 x 0: use the whole browser window
        cfg.height = 0;
        cfg.usePhysicalPixels = true;       // full Retina resolution, so the pixels stay sharp
        cfg.antialiasing = false;
        cfg.useGL30 = true;                 // WebGL 2: repeating textures of any size (stars.png, bg0..2 are 480 x 272,
                                            // the Omen laser 8 x 5); WebGL 1 draws non-power-of-two repeats as nothing.
                                            // (A browser without WebGL 2 falls back to WebGL 1.)
        cfg.showDownloadLogs = true;        // asset download progress in the browser console
        cfg.storagePrefix = "salamander";   // hi-score and settings in the browser's local storage
        new WebApplication(new SalamanderGame(new WebMusicBackend()), cfg);
    }
}
