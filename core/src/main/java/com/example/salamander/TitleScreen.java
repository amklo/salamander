package com.example.salamander;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
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
    private final FitViewport vp = new FitViewport(SalamanderGame.SCREEN_W, SalamanderGame.SCREEN_H, cam);
    private final Texture bg, stars, logo;
    private final TextureRegion pix;
    private static final Color WHITE = new Color(0.88f, 0.88f, 0.88f, 1f), DIM = new Color(0.6f, 0.6f, 0.7f, 1f);
    private static final int OUT = 0, CHOOSING = 1, READY = 2;
    private final int[] stage = new int[Controls.PLAYERS];        // OUT / CHOOSING / READY per player
    private final int[] shipType = new int[Controls.PLAYERS];     // 0..Player.SHIP_TYPES-1
    private float t;
    // S: stage select (debug). Row 0 = stage (OFF, 1..3), row 1 = checkpoint.
    private boolean selectOpen;
    private int selectRow;
    private static int[] checkpointCount;          // per stage, read from the maps the first time S is pressed

    public TitleScreen(SalamanderGame game) {
        this.game = game;
        bg = game.assets.tex("bg0");
        stars = game.assets.tex("stars");
        logo = game.assets.tex("logo");      // assets/sprites/logo.png: the MSX title logo (transparent background)
        bg.setWrap(Texture.TextureWrap.Repeat, Texture.TextureWrap.Repeat);
        stars.setWrap(Texture.TextureWrap.Repeat, Texture.TextureWrap.Repeat);
        cam.position.set(SalamanderGame.SCREEN_W / 2f, SalamanderGame.SCREEN_H / 2f, 0);
        pix = game.assets.one("pixel");
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
        if (Gdx.input.isKeyJustPressed(Keys.I)) {   // debug: invincible ships
            GameScreen.debugInvincible = !GameScreen.debugInvincible;
            game.assets.play(GameScreen.debugInvincible ? "power" : "hit", 0.4f);
        }
        if (Gdx.input.isKeyJustPressed(Keys.B)) {   // debug: boss test. Each press picks the next boss: 1, 2, 3, off
            GameScreen.bossTest = GameScreen.bossTest + 1 >= GameScreen.STAGES ? -1 : GameScreen.bossTest + 1;
            if (GameScreen.bossTest >= 0) GameScreen.startStage = -1;   // the two don't mix
            game.assets.play(GameScreen.bossTest >= 0 ? "power" : "hit", 0.4f);
        }
        if (Gdx.input.isKeyJustPressed(Keys.S)) {   // debug: stage / checkpoint select
            if (selectOpen) closeSelect();
            else {
                if (checkpointCount == null) {
                    checkpointCount = new int[GameScreen.STAGES];
                    for (int s = 0; s < GameScreen.STAGES; s++) checkpointCount[s] = Level.build(s).checkpoints.length;
                }
                selectOpen = true;
                selectRow = 0;
                if (GameScreen.startStage < 0) { GameScreen.startStage = 0; GameScreen.startCheckpoint = 0; }
                GameScreen.bossTest = -1;
                game.assets.play("pickup", 0.4f);
            }
        }
        if (selectOpen) updateSelect();             // the menu takes the controls while it is open
        else
        for (int i = 0; i < Controls.PLAYERS; i++) {
            if (Controls.pressed(i, Action.CANCEL) && stage[i] != OUT) {   // back one step: READY -> CHOOSING -> not joined
                stage[i] = stage[i] == READY ? CHOOSING : OUT;
                game.assets.play("hit", 0.3f);
                continue;
            }
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
        int W = SalamanderGame.SCREEN_W, H = SalamanderGame.SCREEN_H;
        game.batch.draw(bg, 0, 0, W, H, (int) (t * 10), 0, W, H, false, false);
        game.batch.draw(stars, 0, 0, W, H, (int) (t * 30), 0, W, H, false, false);
        boolean blink = (int) (t * 2) % 2 == 0;

        if (selectOpen) {
            drawSelect(blink);
        } else if (stage[0] == OUT && stage[1] == OUT) {
            // ---- attract: logo, join prompts, hi-score
            game.batch.draw(logo, (W - logo.getWidth()) / 2, H - 4 - logo.getHeight());
            joinPrompt(0, 8, 62, Color.CYAN, blink);
            joinPrompt(1, W / 2 + 4, 62, P2_TINT, blink);
            text("HI-SCORE  " + String.format("%06d", game.hiScore), 0, 48, W, Align.center, PixelFont.NORMAL, WHITE);
        } else {
            // ---- ship select: one column per player
            text("SELECT YOUR SHIP", 0, 174, W, Align.center, PixelFont.BOLD, WHITE);
            drawColumn(0, 4, Color.CYAN, blink);
            drawColumn(1, W / 2 + 4, P2_TINT, blink);
            if (stage[0] == READY || stage[1] == READY) {
                if (anyChoosing()) text("WAITING FOR SHIP SELECT", 0, 58, W, Align.center, PixelFont.NORMAL, DIM);
                else if (blink) text("PRESS FIRE AGAIN TO START", 0, 58, W, Align.center, PixelFont.NORMAL, Color.YELLOW);
            }
            text("L/R: CHOOSE  FIRE: OK  X / PAD B: BACK", 0, 46, W, Align.center, PixelFont.SMALL, DIM);
        }

        text("ARROWS MOVE  Z FIRE  X POWER  ENTER AIM", 0, 32, W, Align.center, PixelFont.SMALL, DIM);
        text("PAD: STICK  A FIRE  B POWER  R1 AIM", 0, 24, W, Align.center, PixelFont.SMALL, DIM);
        text("P/START PAUSE  1/2 MUSIC -/+  3/4 SFX -/+", 0, 14, W, Align.center, PixelFont.SMALL, DIM);
        text(game.volumeText(), 0, 4, W, Align.center, PixelFont.SMALL, game.volumeMsgT > 0f ? Color.YELLOW : Color.GRAY);
        if (GameScreen.debugLoadout) text("DEBUG LOADOUT (D)", 2, H - 7, W, Align.left, PixelFont.SMALL, Color.MAGENTA);
        if (GameScreen.debugInvincible) text("INVINCIBLE (I)", 2, H - 15, W, Align.left, PixelFont.SMALL, Color.CYAN);
        if (GameScreen.startStage >= 0 && !selectOpen) {
            text("START: STAGE " + (GameScreen.startStage + 1) + " CP " + (GameScreen.startCheckpoint + 1) + " (S)",
                    0, H - 7, W - 2, Align.right, PixelFont.SMALL, Color.GREEN);
        }
        if (GameScreen.bossTest >= 0) {
            text("BOSS TEST: " + (GameScreen.bossTest + 1) + " (B)", 0, H - 7, W - 2, Align.right, PixelFont.SMALL, Color.ORANGE);
        }
        game.batch.end();
    }

    private void text(String s, float x, float y, float w, int align, int style, Color c) {
        PixelFont.draw(game.batch, pix, s, x, y, w, align, style, 1, c);
    }

    /** Attract screen: "1P PRESS FIRE" / "2P CONNECT PAD 2". */
    private void joinPrompt(int i, float x, float y, Color c, boolean blink) {
        float w = SalamanderGame.SCREEN_W / 2f - 12f;
        if (!Controls.hasInput(i)) text((i + 1) + "P  CONNECT PAD " + (i + 1), x, y, w, Align.center, PixelFont.SMALL, Color.GRAY);
        else if (blink) text((i + 1) + "P  PRESS FIRE", x, y, w, Align.center, PixelFont.NORMAL, c);
    }

    /** Ship select column: status, TYPE picker, and that type's meter order (moved slots in yellow). */
    private void drawColumn(int i, float x, Color c, boolean blink) {
        float w = SalamanderGame.SCREEN_W / 2f - 8f;
        String who = (i + 1) + "P";
        if (stage[i] == OUT) {
            if (!Controls.hasInput(i)) text(who + " CONNECT PAD " + (i + 1), x, 156, w, Align.center, PixelFont.SMALL, Color.GRAY);
            else if (blink) text(who + "  PRESS FIRE", x, 156, w, Align.center, PixelFont.NORMAL, c);
            return;
        }
        boolean choosing = stage[i] == CHOOSING;
        text(who + (choosing ? "  SELECT" : "  READY"), x, 156, w, Align.center, PixelFont.NORMAL, c);
        text(choosing ? "< TYPE " + (shipType[i] + 1) + " >" : "TYPE " + (shipType[i] + 1), x, 142, w, Align.center,
                PixelFont.BOLD, choosing && blink ? Color.YELLOW : WHITE);
        int[] slots = Player.slotsFor(shipType[i]);
        for (int k = 0; k < slots.length; k++) {
            boolean moved = slots[k] != Player.CLASSIC_SLOTS[k];
            text((k + 1) + " " + Player.POWER_NAMES[slots[k]], x + 34, 128 - k * 9, w, Align.left, PixelFont.SMALL,
                    moved ? Color.YELLOW : Color.LIGHT_GRAY);
        }
    }

    /** Stage select menu: up / down picks the row, left / right changes it, fire / X / S closes it. */
    private void updateSelect() {
        if (GameScreen.startStage < 0) selectRow = 0;
        if (Gdx.input.isKeyJustPressed(Keys.S)) return;   // S is also "down" on the keyboard: not a row change
        for (int i = 0; i < Controls.PLAYERS; i++) {
            if (Controls.pressed(i, Action.UP) || Controls.pressed(i, Action.DOWN)) {
                if (GameScreen.startStage >= 0) { selectRow = 1 - selectRow; game.assets.play("hit", 0.2f); }
            }
            int d = Controls.pressed(i, Action.LEFT) ? -1 : Controls.pressed(i, Action.RIGHT) ? 1 : 0;
            if (d != 0) {
                if (selectRow == 0) {                    // OFF, 1, 2, 3
                    int n = GameScreen.STAGES + 1;
                    GameScreen.startStage = (GameScreen.startStage + 1 + d + n) % n - 1;
                    GameScreen.startCheckpoint = 0;
                } else {
                    int n = checkpointCount[GameScreen.startStage];
                    GameScreen.startCheckpoint = (GameScreen.startCheckpoint + d + n) % n;
                }
                game.assets.play("hit", 0.2f);
            }
            if (Controls.pressed(i, Action.FIRE) || Controls.pressed(i, Action.CONFIRM) || Controls.pressed(i, Action.CANCEL)) {
                closeSelect();
                return;
            }
        }
    }

    private void closeSelect() {
        selectOpen = false;
        game.assets.play(GameScreen.startStage >= 0 ? "power" : "hit", 0.4f);
    }

    private void drawSelect(boolean blink) {
        int W = SalamanderGame.SCREEN_W;
        text("STAGE SELECT", 0, 160, W, Align.center, PixelFont.BOLD, WHITE);
        int s = GameScreen.startStage;
        String stageTxt = s < 0 ? "OFF" : String.valueOf(s + 1);
        text((selectRow == 0 ? "< STAGE " : "STAGE ") + stageTxt + (selectRow == 0 ? " >" : ""), 0, 130, W, Align.center,
                PixelFont.NORMAL, selectRow == 0 && blink ? Color.YELLOW : WHITE);
        if (s >= 0) {
            int n = checkpointCount[s];
            String cp = (GameScreen.startCheckpoint + 1) + " OF " + n;
            text((selectRow == 1 ? "< CHECKPOINT " : "CHECKPOINT ") + cp + (selectRow == 1 ? " >" : ""),
                    0, 114, W, Align.center, PixelFont.NORMAL, selectRow == 1 && blink ? Color.YELLOW : WHITE);
            if (GameScreen.startCheckpoint == 0) text("(STAGE START)", 0, 102, W, Align.center, PixelFont.SMALL, DIM);
        } else {
            text("NORMAL START", 0, 114, W, Align.center, PixelFont.NORMAL, DIM);
        }
        text("U/D: ROW  L/R: CHANGE  FIRE / S: DONE", 0, 58, W, Align.center, PixelFont.SMALL, DIM);
    }

    private boolean anyChoosing() {
        for (int s : stage) if (s == CHOOSING) return true;
        return false;
    }

    private void startGame() {
        Player[] ps = new Player[Controls.PLAYERS];
        for (int i = 0; i < ps.length; i++) if (stage[i] == READY) ps[i] = new Player(i, shipType[i]);
        game.assets.play("power", 0.5f);
        // boss test: straight to that stage's boss
        int first = GameScreen.bossTest >= 0 ? GameScreen.bossTest : Math.max(0, GameScreen.startStage);   // debug starts
        game.setScreen(new GameScreen(game, first, ps));
    }
}
