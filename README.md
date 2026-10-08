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
| I (title screen) | Invincible ships on/off |
| B (title screen) | Boss test: each press picks the next boss (1, 2, 3, off) |
| S (title screen) | Stage select: pick the stage and checkpoint to start from (up / down row, left / right change, fire or S closes) |

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
  SalamanderGame   entry Game class, MSX resolution (256x192: 256x168 playfield + 24px HUD)
  TitleScreen     title / attract screen
  GameScreen      scrolling, spawning, collisions, power-up meter, HUD, state machine
  PixelFont       pixel fonts for all text (5x5, 5x7, MSX digits)
  Level           loads a stage from its Tiled map; terrain / collision queries
  TiledMap        reader for Tiled .tmx / .tsx files
  Enemy, Boss, Hazard, Bullet, Player   entities
  Assets          lazy sprite/sound loader
lwjgl3/…/Lwjgl3Launcher.java      desktop launcher
assets/sprites, assets/sfx        art and sound (classpath resources)
assets/maps                       the stages as Tiled maps + tilesets  <-- design stages here
tools/generate_assets.py          regenerates all assets (needs Python + Pillow)
```

## Editing levels in Tiled
All three stages are [Tiled](https://www.mapeditor.org) maps: `assets/maps/stage1.tmx`, `stage2.tmx`, `stage3.tmx`.
Open one in Tiled (File ▸ Open), edit, save (Ctrl/Cmd+S) and run the game – nothing needs converting.
The tilesets next to them (`collision.tsx`, `bricks.tsx`, `terrain.tsx`, `objects.tsx`) are shared by all stages.

**Map grid:** 8×8 px tiles; one tile = one collision cell. The playfield is 256×168 px (32×21 tiles), as on the MSX.
Keep the map *not* infinite and keep layers at the top level (no layer groups).

### Map properties (Map ▸ Map Properties)
| Property | Meaning |
|---|---|
| `name` | stage title shown in the intro and HUD |
| `speed` | base scroll speed in px/s |
| `music`, `bossMusic` | MIDI files in `assets/bgm/` |
| `background` | parallax backdrop sprite (`bg1`, `bg2`; leave it out for plain black space) |
| `lane` | height the stage was laid out for (default 272). If it is taller than the 168px view, the camera drifts up and down within it to follow the ships. Stage 1 uses 168 (no drift) |

### Layers (names matter, upper/lower case doesn't)
| Layer | Kind | What it does |
|---|---|---|
| `art 0` … `art 3` | image layers | stage 1's picture (the cleaned-up original map, 1:1). Only drawn, never solid |
| `terrain` | tile layer, `terrain` tileset | stages 2–3: drawn **and** solid. Each 16 px rock tile is four 8 px pieces – paint them as 2×2 stamps |
| `collision` | tile layer, `collision` tileset | solid but invisible (red in Tiled). Paint it over the art in stage 1, or to add invisible walls |
| `bricks` | tile layer, `bricks` tileset | destructible bricks: every shot breaks them, each grows back after 3 s. In game they show the brick pattern |
| `enemies`, `hazards`, `camera` | object layers | see below. Any object layer works; the game goes by each object's kind |

### Objects (drag them from the `objects` tileset; set options in the Properties panel)
| Object | Placement | Options |
|---|---|---|
| `fan` / `rusher` | where the wave enters the screen (its height = the wave's height) | `count` (5 / 3), `drop` (capsule when the whole wave is shot) |
| `walker` | stage 1 uses the bio walker tiles (blue, and red = `drop` on), the other stages the old walker tile; standing on the floor – or flip it vertically (Y) to hang it from the ceiling | `ceiling`, `behind` (runs in from the left edge once the screen has passed this spot), `drop` |
| `frost` | Celtic Frost wave. x = where it is triggered; y = the line it enters on (10–42% of the screen height from the bottom, or from the top when flipped); it leaves on the mirrored line. It flies a "Z": in from the right, left, diagonally back, then left and out. Flip vertically (or `upper`) to mirror the Z top to bottom | `count` (6), `drop`, `upper` |
| `link` | Missing Link, a single enemy: x = where it is triggered, y = the middle of its bob. It comes in from the right edge, drifts left bobbing up and down and shoots at the ships | `drop` (off) |
| `rugal` | Rugal: comes in from the right edge at this height (triggered like the flying waves) and flies left, steering toward the nearest ship and shooting back at a ship once it has flown past it. Place three a little apart for a trio | `drop` (off) |
| `turret` | bio turret on the floor, or flipped vertically under the ceiling. Two tiles: blue, and red (`drop` on: leaves a power capsule) | `ceiling`, `drop` |
| `bamda` | anywhere (the pulsing rock; its size comes from `sprites/bamda.png`) | – |
| `tower` | where the fully grown tower stands, its root end 8 px into the rock. Flip vertically to turn it over (floor ↔ ceiling); don't resize | – |
| `tooth` (fang0–5) | where it is when fully out, its root at the floor or ceiling | `ceiling`, `delay` (s before the first bite), `period` (s per cycle) |
| `rock` | hanging under the ceiling; falls when a ship flies underneath | – |
| `volcano` | on the floor | – |
| `crusher` | on the ceiling or floor surface it pushes out from | `ceiling`, `minLen`, `maxLen`, `speed`, `phase` |

**Camera** (`camera` layer):
* `path` – a polyline drawn through the **centre of the screen**. The game scrolls along it, so a diagonal or
  vertical stretch scrolls the screen diagonally or up/down (as in stage 1). It must stay at least half a screen
  (128 px / 84 px) inside the map edges. Its `speeds` property lists a speed multiplier per segment,
  e.g. `1,0.8,1,0.45,0.8`. Where the path ends, scrolling stops and the boss arrives.
* `checkpoint` – point objects on (or near) the path; when every ship is down, the stage restarts from the last one passed.

Tip: in the game, **H** shows the solid cells of the map in red, to check your collision painting.

### Stage 1 art
`tools/stage1_build.py` recreates stage 1's pictures (terrain strips, teeth, asteroids, brick pattern) at 1:1 from
`reference matterial/salamanderstage1.png` (needs numpy, scipy, pillow). Normally you edit the art PNGs
directly (e.g. in GIMP) and adjust the `collision` layer in Tiled to match. The script's `NO_FILL` and `REMOVE`
lists hold areas to leave empty, so they survive a rebuild. `tools/stage1_tmx.py` was the one-off conversion of
`stage1.tmx` from the old 1.62x scale (it refuses to run on a map that is already 1:1).

### Other tuning
Boss HP: `GameScreen.spawnBoss()`; boss attack patterns: `Boss.update()`; enemy behaviour: `Enemy.java`.

## Swapping the art
Overwrite any PNG in `assets/sprites/` keeping the same frame size (animated sprites are horizontal strips):

| File | Size | Frames |
|---|---|---|
| player | 96×16 | 3 × 32×16 (level, up, down) |
| option | 24×12 | 2 × 12×12 |
| fan, walker (stages 2–3) | 32×16 | 2 × 16×16 |
| bio walker red, bio walker blue (stage 1 walkers) | 80×16 | 5 × 16×16, played forward then backward; no left / right facing; red = drops a capsule |
| celtic frost | 13×15 | 1 |
| missing_link | 112×16 | 7 × 16×16 (barrel roll, looped) |
| rugal | 48×16 | 3 × 16×16: level, diving (45° or more down), climbing (45° or more up) |
| rusher | 36×12 | 2 × 18×12 |
| bio turret red, bio turret blue | 16×16 | 2: barrel diagonally left, barrel up (mirrored to aim right, flipped for ceilings) |
| rock, crusher_shaft | 16×16 | 1 |
| crusher_head | 32×16 | 1 |
| capsule | 24×12 | 2 × 12×12 |
| explosion | 120×24 | 5 × 24×24 |
| shot / laser / missile / ebullet | 12×4 / 40×4 / 8×6 / 6×6 | 1 |
| lava | 16×8 | 2 × 8×8 |
| shield | 40×32 | 1 |
| boss0, boss1, boss2 | any | 1 (the boss's size and hit shape come from the picture) |
| tiles | 48×48 | terrain tileset for Tiled (8×8 pieces): row pair = stage, column pair = fill / floor surface / ceiling underside |
| bg0–bg2 | 480×272 | parallax backdrop, must tile horizontally (the game shows a 256×168 part) |
| stars | 480×272 | transparent star layer |
| hud | 128×8 | bottom HUD pieces cut from the original: meter slot, highlighted slot (1P / 2P), ship icons, E icon, yellow box |

Sounds live in `assets/sfx/*.wav` (`shoot, hit, boom, pickup, power`).
