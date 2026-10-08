package com.example.salamander.lwjgl3;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.example.salamander.SalamanderGame;

/** Desktop launcher. The window opens at 4x the MSX resolution (1024 x 768) and can be resized. */
public class Lwjgl3Launcher {
    public static void main(String[] args) {
        Lwjgl3ApplicationConfiguration cfg = new Lwjgl3ApplicationConfiguration();
        cfg.setTitle("Salamander");
        cfg.setWindowedMode(SalamanderGame.SCREEN_W * 4, SalamanderGame.SCREEN_H * 4);
        cfg.useVsync(true);
        cfg.setForegroundFPS(60);
        new Lwjgl3Application(new SalamanderGame(), cfg);
    }
}
