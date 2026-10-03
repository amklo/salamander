# SALAMANDER – a Gradius-style side-scrolling shooter (Java + LibGDX)

Three stages, a power-up meter, bosses, and lots of scrolling terrain hazards.
All art and sound is generated (`tools/generate_assets.py`) and is easy to swap out.

## Open in IntelliJ IDEA
1. **File ▸ Open…** and pick this folder (the one containing `build.gradle`). Choose *Open as Project* / *Trust Project*.
2. Wait for the Gradle import to finish (first run downloads Gradle 8.5 and LibGDX 1.12.1 – internet required).
3. Make sure the project JDK is **11 or newer** (File ▸ Project Structure ▸ Project SDK; 17 or 21 is fine).
4. Run the shared configuration **"Salamander (Gradle run)"** (top-right run dropdown), or open
   `lwjgl3/src/main/java/com/example/salamander/lwjgl3/Lwjgl3Launcher.java` and click the green ▶ next to `main`.
   * macOS: if you run `Lwjgl3Launcher` directly, add the VM option `-XstartOnFirstThread` (Run ▸ Edit Configurations).
     The Gradle run configuration already does this for you.

Command line (after IntelliJ or `gradle wrapper` has created the wrapper): `./gradlew lwjgl3:run`
Build a runnable jar: `./gradlew lwjgl3:dist` → `lwjgl3/build/libs/salamander-1.0.0.jar`

## Controls
| Key | Action |
|---|---|
| Arrows / WASD | Move |
| Z / Space (hold) | Fire (also drops missiles once you own them) |
| X / Shift | Activate the highlighted power-up slot |
| P / Esc | Pause |
| Enter | Start / continue; in play, hold + direction to aim the DOUBLE shot |
| 1 / 2 | Music volume down / up (saved) |
| 3 / 4 | Sound-effect volume down / up (saved) |
| H (or fn+F1) | Show the player hitbox (debug) |
| D (title screen) | Debug loadout on/off: start every stage with 4 options, missiles, shield, DOUBLE and LASER highlighted |

### Gamepad
Any controller SDL recognises (Xbox, PlayStation, Switch Pro, most USB pads) works, plugged in before or during play.

| Button | Action |
|---|---|
| Left stick / D-pad | Move |
| A or X (hold) | Fire |
| B or Y | Activate the highlighted power-up slot |
| R1 (hold) + direction | Aim the DOUBLE shot |
| Start | Pause / start / continue (A also starts from menus) |
| Back/Select + D-pad up/down | Music volume up / down |
| Back/Select + D-pad left/right | Sound-effect volume down / up |

### Two players (co-op)
* **Player 1** = keyboard and/or gamepad #1. **Player 2** = gamepad #2 only (pads are numbered in connection order).
* Title screen: each player presses **fire** to join, then a joined player presses **fire again** to start.
* Player 2 can also join a game already in progress at any moment by pressing fire on pad #2.
* Each player has their own ships, score and power-up meter (player 1 bottom-left, player 2 bottom-right).
  A capsule only advances the meter of the player who catches it. Player 2's ship is tinted orange.
* If one player is shot down while the other is still flying, they re-enter on the spot after a moment.
  If both are down, the stage rewinds to the last checkpoint.
* A player who runs out of ships drops out; while the other player is still going, they can press fire to
  continue with a fresh set of ships. It's game over when nobody is left.

## How it plays
* Shoot **every flying wave** completely (and don't let any escape) to release a capsule; every walker drops one too. Each capsule moves the
  highlight along the meter: `SPEED · MISSILE · DOUBLE · LASER · OPTION · SHIELD`. Press X to cash it in.
  Up to 4 options; they trail your flight path and stay put while the ship holds still.
* **Terrain kills.** Touching floor, ceiling or a crusher destroys you even with a shield; checkpoints respawn you
  with all your power-ups.
* Hazards: falling stalactites (they drop when you fly beneath them), erupting volcanoes, piston crushers,
  scroll-speed surges in tight tunnels, and ground/ceiling turrets.
* Stage ends with a boss. Any weapon damages it anywhere (a laser beam hits it once).
* With **DOUBLE**, hold Enter (pad: R1) and press a direction to aim the extra shot in one of 8 directions; the ship holds still while you aim.
* You keep your power-ups when you die; every new stage starts without them.

## Project layout
```
build.gradle / settings.gradle    Gradle multi-module build (core + lwjgl3)
core/src/main/java/com/example/salamander/
  SalamanderGame   entry Game class, virtual resolution (480x272)
  TitleScreen     title / attract screen
  GameScreen      scrolling, spawning, collisions, power-up meter, HUD, state machine
  Level           terrain segments, hazard placement, enemy wave generator  <-- design stages here
  Enemy, Boss, Hazard, Bullet, Player   entities
  Assets          lazy sprite/sound loader
lwjgl3/…/Lwjgl3Launcher.java      desktop launcher
assets/sprites, assets/sfx        art and sound (classpath resources)
tools/generate_assets.py          regenerates all assets (needs Python + Pillow)
```

## Designing / tuning stages (Level.java)
`l1()`, `l2()`, `l3()` are lists of terrain segments:
* `open(len, floor, ceil)` – flat corridor (heights in 16px tiles; the screen is 17 tiles tall)
* `hills(len, floorBase, floorAmp, ceilBase, ceilAmp, period, phase)` – rolling cave
* `tunnel(len, gap, amplitude, period, scrollSpeedMultiplier)` – narrow snaking corridor
* `peak(len, base, height, volcano)` – mountain; `volcano=true` makes it erupt
* `crushTunnel(len, gap, spacing, speedMul)` – corridor with alternating piston crushers
* `rocks(startCol, endCol, spacing)` – stalactites that fall when you pass under
Terrain slope is limited to one tile per column so everything stays flyable. Base scroll speeds are in
`SPEEDS`, wave mixes in `genSpawns()` (`weights`), boss HP in `GameScreen.spawnBoss()`, boss patterns in `Boss.update()`.

## Swapping the art
Overwrite any PNG in `assets/sprites/` keeping the same frame size (animated sprites are horizontal strips):

| File | Size | Frames |
|---|---|---|
| player | 96×16 | 3 × 32×16 (level, up, down) |
| option | 24×12 | 2 × 12×12 |
| fan, walker | 32×16 | 2 × 16×16 |
| rusher | 36×12 | 2 × 18×12 |
| turret, rock, crusher_shaft | 16×16 | 1 (turret is flipped for ceilings) |
| crusher_head | 32×16 | 1 |
| capsule | 24×12 | 2 × 12×12 |
| explosion | 120×24 | 5 × 24×24 |
| shot / laser / missile / ebullet | 12×4 / 40×4 / 8×6 / 6×6 | 1 |
| lava | 16×8 | 2 × 8×8 |
| shield | 40×32 | 1 |
| boss0, boss1, boss2 | 64×64 | 1 (the whole 64×64 is the hitbox) |
| tiles | 48×48 | 3×3 grid: row = stage, columns = fill / floor surface / ceiling underside |
| bg0–bg2 | 480×272 | parallax backdrop, must tile horizontally |
| stars | 480×272 | transparent star layer |

Sounds live in `assets/sfx/*.wav` (`shoot, hit, boom, pickup, power`).
