package com.example.salamander.lwjgl3;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.example.salamander.SalamanderGame;

/** Desktop entry point. */
public class Lwjgl3Launcher {
    public static void main(String[] args) {
        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("Salamander");
        config.setWindowedMode(SalamanderGame.W * 2, SalamanderGame.H * 2);
        config.useVsync(true);
        config.setForegroundFPS(60);
        new Lwjgl3Application(new SalamanderGame(), config);
    }
}
