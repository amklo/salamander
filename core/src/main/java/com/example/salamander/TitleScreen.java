package com.example.salamander;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.example.salamander.Controls.Action;

/**
 * Title / join screen. Each player presses fire to join (P1: keyboard or pad #1, P2: pad #2), picks a
 * ship type with left / right and confirms with fire. Once everyone who joined is ready, any of them
 * presses fire again to start.
 */
public class TitleScreen extends ScreenAdapter {
    private static final Color P2_TINT = new Color(1f, 0.7f, 0.4f, 1f);

    private final SalamanderGame game;
    private final OrthographicCamera cam = new OrthographicCamera();
    private final FitViewport vp = new FitViewport(SalamanderGame.W, SalamanderGame.H, cam);
    private final Texture bg, stars, logo;
    private static final int OUT = 0, CHOOSING = 1, READY = 2;
    private final int[] stage = new int[Controls.PLAYERS];        // OUT / CHOOSING / READY per player
    private final int[] shipType = new int[Controls.PLAYERS];     // 0..Player.SHIP_TYPES-1
    private float t;

    public TitleScreen(SalamanderGame game) {
        this.game = game;
        bg = game.assets.tex("bg0");
        stars = game.assets.tex("stars");
        logo = game.assets.tex("logo");      // assets/sprites/logo.png: the MSX title logo (transparent background)
        bg.setWrap(Texture.TextureWrap.Repeat, Texture.TextureWrap.Repeat);
        stars.setWrap(Texture.TextureWrap.Repeat, Texture.TextureWrap.Repeat);
        cam.position.set(SalamanderGame.W / 2f, SalamanderGame.H / 2f, 0);
    }

    @Override
    public void resize(int w, int h) { vp.update(w, h, false); }

    @Override
    public void render(float dt) {
        t += dt;
        if (Gdx.input.isKeyJustPressed(Keys.D)) {   // debug: start with options, missiles, shield, DOUBLE
            GameScreen.debugLoadout = !GameScreen.debugLoadout;
            game.assets.play(GameScreen.debugLoadout ? "power" : "hit", 0.4f);
        }
        for (int i = 0; i < Controls.PLAYERS; i++) {
            if (stage[i] == CHOOSING) {
                if (Controls.pressed(i, Action.LEFT)) { shipType[i] = (shipType[i] + Player.SHIP_TYPES - 1) % Player.SHIP_TYPES; game.assets.play("hit", 0.2f); }
                if (Controls.pressed(i, Action.RIGHT)) { shipType[i] = (shipType[i] + 1) % Player.SHIP_TYPES; game.assets.play("hit", 0.2f); }
            }
            if (!Controls.pressed(i, Action.FIRE) && !Controls.pressed(i, Action.CONFIRM)) continue;
            if (stage[i] == OUT) {
                stage[i] = CHOOSING;                 // first press: join, then pick a ship
                game.assets.play("pickup", 0.4f);
            } else if (stage[i] == CHOOSING) {
                stage[i] = READY;                    // second press: ship confirmed
                game.assets.play("power", 0.4f);
            } else if (!anyChoosing()) {
                startGame();                         // a ready player presses again: start (once nobody is still choosing)
                return;
            }
        }

        ScreenUtils.clear(0, 0, 0, 1);
        vp.apply();
        cam.update();
        game.batch.setProjectionMatrix(cam.combined);
        game.batch.begin();
        int W = SalamanderGame.W, H = SalamanderGame.H;
        game.batch.draw(bg, 0, 0, W, H, (int) (t * 10), 0, W, H, false, false);
        game.batch.draw(stars, 0, 0, W, H, (int) (t * 30), 0, W, H, false, false);

        BitmapFont f = game.assets.font;
        boolean blink = (int) (t * 2) % 2 == 0;
        // logo at 1:1 pixel scale, centred near the top
        int lx = (W - logo.getWidth()) / 2, ly = H - 8 - logo.getHeight();
        game.batch.draw(logo, lx, ly);

        // join status per player
        f.getData().setScale(0.65f);
        drawSlot(f, 0, 20, 128, Color.CYAN, blink);
        drawSlot(f, 1, W / 2 + 4, 128, P2_TINT, blink);

        if ((stage[0] == READY || stage[1] == READY) && blink) {
            f.setColor(1f, 0.9f, 0.3f, 1f);
            f.getData().setScale(0.75f);
            f.draw(game.batch, anyChoosing() ? "WAITING FOR SHIP SELECT" : "PRESS FIRE AGAIN TO START", 0, 84, W, Align.center, false);
        }

        f.getData().setScale(0.5f);
        f.setColor(0.8f, 0.8f, 0.9f, 1f);
        f.draw(game.batch, "ARROWS / WASD  MOVE    Z / SPACE  FIRE    X / SHIFT  POWER-UP    ENTER  AIM DOUBLE    P  PAUSE",
                0, 66, W, Align.center, false);
        f.draw(game.batch, "PAD:  STICK  MOVE    A  FIRE    B  POWER-UP    R1  AIM DOUBLE    START  PAUSE",
                0, 54, W, Align.center, false);
        f.draw(game.batch, "HI-SCORE  " + String.format("%06d", game.hiScore), 0, 38, W, Align.center, false);
        f.setColor(game.volumeMsgT > 0f ? Color.YELLOW : Color.GRAY);
        f.draw(game.batch, game.volumeText() + "      1/2 MUSIC  -/+      3/4 SFX  -/+", 0, 18, W, Align.center, false);
        if (GameScreen.debugLoadout) {
            f.getData().setScale(0.5f);
            f.setColor(Color.MAGENTA);
            f.draw(game.batch, "DEBUG LOADOUT ON  (D)", 6, H - 6, W, Align.left, false);
        }
        f.getData().setScale(1f);
        f.setColor(Color.WHITE);
        game.batch.end();
    }

    private void drawSlot(BitmapFont f, int i, float x, float y, Color c, boolean blink) {
        float w = SalamanderGame.W / 2f - 24f;
        String who = (i + 1) + "P";
        String device = i == 0 ? "KEYBOARD / PAD 1" : "PAD 2";
        if (stage[i] != OUT) {
            boolean choosing = stage[i] == CHOOSING;
            f.setColor(c);
            f.draw(game.batch, who + (choosing ? "  SELECT SHIP  -  FIRE TO CONFIRM" : "  READY"), x, y, w, Align.center, false);
            f.setColor(choosing && blink ? Color.YELLOW : Color.WHITE);
            f.draw(game.batch, (choosing ? "<  TYPE " + (shipType[i] + 1) + "  >" : "TYPE " + (shipType[i] + 1)), x, y - 14, w, Align.center, false);
            drawMeterPreview(f, shipType[i], x, y - 28, w);
            return;
        } else if (!Controls.hasInput(i)) {
            f.setColor(Color.GRAY);
            f.draw(game.batch, who + "  CONNECT " + device, x, y, w, Align.center, false);
        } else if (blink) {
            f.setColor(Color.WHITE);
            f.draw(game.batch, who + "  PRESS FIRE TO JOIN", x, y, w, Align.center, false);
        }
        f.setColor(0.6f, 0.6f, 0.7f, 1f);
        f.getData().setScale(0.45f);
        f.draw(game.batch, "(" + device + ")", x, y - 14, w, Align.center, false);
        f.getData().setScale(0.65f);
    }

    /** The ship type's meter order; the two slots it swapped are shown in yellow. */
    private void drawMeterPreview(BitmapFont f, int type, float x, float y, float w) {
        int[] slots = Player.slotsFor(type);
        float cw = w / slots.length;
        f.getData().setScale(0.38f);
        for (int k = 0; k < slots.length; k++) {
            boolean moved = slots[k] != Player.CLASSIC_SLOTS[k];
            f.setColor(moved ? Color.YELLOW : Color.LIGHT_GRAY);
            f.draw(game.batch, Player.POWER_NAMES[slots[k]], x + k * cw, y, cw, Align.center, false);
        }
        f.getData().setScale(0.65f);
    }

    private boolean anyChoosing() {
        for (int s : stage) if (s == CHOOSING) return true;
        return false;
    }

    private void startGame() {
        Player[] ps = new Player[Controls.PLAYERS];
        for (int i = 0; i < ps.length; i++) if (stage[i] == READY) ps[i] = new Player(i, shipType[i]);
        game.assets.play("power", 0.5f);
        game.setScreen(new GameScreen(game, 0, ps));
    }
}
