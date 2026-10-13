# SALAMANDER – a Gradius-style side-scrolling shooter (Java + LibGDX)

Seven stages, a power-up meter, bosses, and lots of scrolling terrain hazards.
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
| D (title screen) | Debug loadout on/off: start every stage with 4 options, missiles, F.FIELD (type 4: SHIELD), DOUBLE and LASER highlighted |
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
  A capsule only advances the meter of the player who catches it. Player 2 flies the red ship (`player_red.png`); its options are tinted orange.
* A ship that is shot down re-enters on the spot after a moment, also when playing alone: the stage carries on
  and never rewinds.
* A player who runs out of ships drops out; while the other player is still going, they can press fire to
  continue with a fresh set of ships. It's game over when nobody is left.

## How it plays
* Shoot **every flying wave** completely (and don't let any escape) to release a capsule; every walker drops one too. Each capsule moves the
  highlight along the meter: `SPEED · MISSILE · DOUBLE · LASER · OPTION · F.FIELD`. Press X to cash it in. SPEED is always open and steps through SPEED#1 … SPEED#5, then SPEED#0 (back to the starting speed). F.FIELD and SHIELD open again as soon as they have taken a hit, and taking them refills them.
  Up to 4 options; they trail your flight path and stay put while the ship holds still.
* **Terrain kills.** Touching floor, ceiling or a crusher destroys you even with an F.FIELD or SHIELD.
* Hazards: falling stalactites (they drop when you fly beneath them), erupting volcanoes, piston crushers,
  scroll-speed surges in tight tunnels, and ground/ceiling turrets.
* **MISSILE** goes toward the nearer edge of the screen: down when your ship (or option) is in the lower half, up along
  the ceiling when it is in the upper half. 2-WAY always sends one each way.
* Stage ends with a boss. Any weapon damages it anywhere (a laser beam hits it once).
* **Stage 2 is vertical**: the screen scrolls up and the ship flies and fires upward. The blue bubble rocks can be shot
  away (they don't grow back); the orange rocks can't. MISSILE flies diagonally up toward the nearer side of the
  screen and then crawls up along the rock walls (with no rock on that side it flies off the screen); 2-WAY sends one
  up each side. Wall turrets, domes that spit amoebas, and rocks with an eye that
  shoots back guard the way to **Tetran**, whose four tentacles turn slowly around it like a revolving door; the rings
  block shots and the tips fire at you. Shoot its body.
* With **DOUBLE**, hold Enter (pad: R1) and press a direction to aim the extra shot in one of 8 directions; the ship holds still while you aim.
* When you die, your ship's options are left behind as green capsules (`powercapsule_green.png`) where they were
  flying: catch one to get an option back at once. A green capsule that leaves the screen comes back once at the
  opposite edge, for one more chance. On **NORMAL** the ship also comes back without its other power-ups (the power meter
  keeps its highlight); on **EASY** it keeps them. Every new stage starts without power-ups.
* **Difficulty**: on the title screen, left / right switches between NORMAL and EASY (remembered between runs). EASY
  starts (and continues) with 5 ships instead of 3.

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
All seven stages are [Tiled](https://www.mapeditor.org) maps: `assets/maps/stage1.tmx` … `stage7.tmx`.
Open one in Tiled (File ▸ Open), edit, save (Ctrl/Cmd+S) and run the game – nothing needs converting.
The tilesets next to them (`collision.tsx`, `bricks.tsx`, `terrain.tsx`, `objects.tsx`) are shared by all stages.

**Map grid:** 8×8 px tiles; one tile = one collision cell. The playfield is 256×168 px (32×21 tiles), as on the MSX.
Keep the map *not* infinite and keep layers at the top level (no layer groups).

### Map properties (Map ▸ Map Properties)
| Property | Meaning |
|---|---|
| `name` | stage title shown in the intro and HUD |
| `speed` | base scroll speed in px/s |
| `music`, `bossMusic` | music files in `assets/bgm/` (MP3, OGG, WAV or MIDI), e.g. `02. Power of Anger.mp3` |
| `bossMusicAt` | the boss music already starts when the middle of the screen reaches this x, and plays on into the boss fight (stage 1: 3984, the brick maze) |
| `background` | parallax backdrop sprite (`bg1`, `bg2`; leave it out for plain black space) |
| `lane` | height the stage was laid out for (default 272). If it is taller than the 168px view, the camera drifts up and down within it to follow the ships. Stages 1 and 2 use 168 (no drift) |
| `vertical` | `true` = a vertical stage (stages 2, 4 and 6): the path runs upward, the ship (`player_blue_vertical.png`) points up and every weapon fires up. Objects are triggered when the top of the screen reaches them |
| `brickArt` | picture the bricks are cut from, as big as the map (stage 2: `sprites/stage2_bricks.png`, the blue bubble rocks). Each brick shows its own 8×8 piece of it. Leave it out for the repeating brick pattern |
| `brickRegrow` | seconds until a shot-away brick grows back (default 3; `0` = never, as in stage 2) |

### Layers (names matter, upper/lower case doesn't)
| Layer | Kind | What it does |
|---|---|---|
| `art 0` … `art 3` | image layers | the stage picture (the cleaned-up original map, 1:1; stages 3–7 have up to six strips, `art 0` … `art 5`). Only drawn, never solid |
| `terrain` | tile layer, `terrain` tileset | stage 2: drawn **and** solid. Each 16 px rock tile is four 8 px pieces – paint them as 2×2 stamps |
| `collision` | tile layer, `collision` tileset | solid but invisible (red in Tiled). Paint it over the art in stage 1, or to add invisible walls |
| `bricks` | tile layer, `bricks` tileset | destructible bricks: every shot breaks them, each grows back after 3 s (map property `brickRegrow`). In game they show the brick pattern, or their piece of `brickArt` |
| `enemies`, `hazards`, `camera` | object layers | see below. Any object layer works; the game goes by each object's kind |

### Objects (drag them from the `objects` tileset; set options in the Properties panel)
| Object | Placement | Options |
|---|---|---|
| `fan` / `rusher` | where the wave enters the screen (its height = the wave's height). Vertical stage: fans come in from the top, one after another, and weave down around the object's column; y = where the top of the screen triggers them | `count` (5 / 3), `drop` (capsule when the whole wave is shot) |
| `walker` | stage 1 uses the bio walker tiles (blue, and red = `drop` on), the other stages the old walker tile; standing on the floor – or flip it vertically (Y) to hang it from the ceiling | `ceiling`, `behind` (runs in from the left edge once the screen has passed this spot), `drop` |
| `frost` | Celtic Frost wave. x = where it is triggered; y = the line it enters on (10–42% of the screen height from the bottom, or from the top when flipped); it leaves on the mirrored line. It flies a "Z": in from the right, left, diagonally back, then left and out. Flip vertically (or `upper`) to mirror the Z top to bottom. Vertical stage: the Z is turned a quarter: the wave comes down from the top along the object's column (10–42% of the screen width from the left edge; flipped = from the right edge), flies diagonally back up to the other side and leaves at the bottom | `count` (6), `drop`, `upper` |
| `legacy` | Legacy wave (stage 2): placed and flying exactly like a `fan` wave, 4 by default. A Legacy only shoots once it has flown past a ship (below it on a vertical stage, left of it otherwise) | `count` (4), `drop` |
| `link` | Missing Link, a single enemy: x = where it is triggered, y = the middle of its bob. It comes in from the right edge, drifts left bobbing up and down and shoots at the ships | `drop` (off) |
| `rugal` | Rugal: comes in from the right edge at this height (triggered like the flying waves) and flies left, steering toward the nearest ship and shooting back at a ship once it has flown past it. Place three a little apart for a trio. Two tiles: normal, and red (`drop` on: `rugal_red.png`, leaves a power capsule) | `drop` (off) |
| `turret` | bio turret on the floor, or flipped vertically under the ceiling. Two tiles: blue, and red (`drop` on: leaves a power capsule) | `ceiling`, `drop` |
| `bamda` | anywhere (the pulsing rock; its size comes from `sprites/bamda.png`) | – |
| `tower` | where the fully grown tower stands, its root end 8 px into the rock. Flip vertically to turn it over (floor ↔ ceiling); don't resize | – |
| `tooth` (`fang full.png`) | its x, and its tip somewhere in the passage: the game makes it come out of the ceiling (or floor) until the tip touches the floor (or ceiling) | `ceiling`, `delay` (s before the first bite), `period` (s per cycle) |
| `rock` | hanging under the ceiling; falls when a ship flies underneath | – |
| `volcano` | on the floor | – |
| `crusher` | on the ceiling or floor surface it pushes out from | `ceiling`, `minLen`, `maxLen`, `speed`, `phase` |
| `wallturret` | stage 2 turret on a rock wall to its left; flip it horizontally (X) for a wall to its right. It turns its barrel toward the ship: straight out, or diagonally up / down. Two tiles: green, and red (`drop` on: leaves a power capsule). `diag` (or a vertical flip for up) only sets the barrel it starts with | `drop`, `diag` |
| `dome` | stage 2 dome against the left wall; flip it horizontally (X) for the right wall. Closed until it has been on screen for 1 s, then it opens for good and spits sprays of 4 amoebas (one red) sideways. 12 hits, 500 points | – |
| `boss` | where the boss sits in the map (the box = its picture, `sprites/boss<N-1>.png`). With `fixed` on (stages 5–7), the boss stays there instead of flying in, and wakes up as soon as it comes on screen. Without it, the boss flies in as usual | `fixed` |
| `hatch`, `pod`, `jumper`, `dart`, `dart_side`, `spark`, `laser`, `spider`, `nest`, `chain`, `seed`, `bluedome`, `cluster`, `mine`, `crab`, `gunpod`, `orb`, `vent`, `coregate` | the enemies and hazards of stages 3–7, where the original map has them (see *Stages 3–7 art*). **Not used by the game yet**: they get their behaviour stage by stage | (`open`, `red`, `blue`, `vertical`, `count`, `diag` – notes from the map) |
| `eye` | stage 2 eye rock (`sprites/rock_eye.png`, 32×32): the whole rock with its eye, placed where it should be (it is not part of the map art or collision). The eye shoots at the ships like a bamda; the rock is solid (its exact shape). 3 hits destroy the whole rock (300 points); it leaves rubble (`sprites/rubbles.png`) that stays as solid, indestructible terrain until the stage restarts from a checkpoint | – |

**Camera** (`camera` layer):
* `path` – a polyline drawn through the **centre of the screen**. The game scrolls along it, so a diagonal or
  vertical stretch scrolls the screen diagonally or up/down (as in stage 1). It must stay at least half a screen
  (128 px / 84 px) inside the map edges. Its `speeds` property lists a speed multiplier per segment,
  e.g. `1,0.8,1,0.45,0.8`. Where the path ends, scrolling stops and the boss arrives.
* `checkpoint` – point objects on (or near) the path: where stage select (S on the title screen) can start a stage.

Tip: in the game, **H** shows the solid cells of the map in red, to check your collision painting.

### Stage 1 art
`tools/stage1_build.py` recreates stage 1's pictures (terrain strips, teeth, asteroids, brick pattern) at 1:1 from
`reference matterial/salamanderstage1.png` (needs numpy, scipy, pillow). Normally you edit the art PNGs
directly (e.g. in GIMP) and adjust the `collision` layer in Tiled to match. The script's `NO_FILL` and `REMOVE`
lists hold areas to leave empty, so they survive a rebuild. `tools/stage1_tmx.py` was the one-off conversion of
`stage1.tmx` from the old 1.62x scale (it refuses to run on a map that is already 1:1).

### Stage 2 art
`tools/stage2_build.py` rebuilds everything for stage 2 from `reference material/salamander_stage_2.png`: the rock art
(`stage2_t0–t3.png`), the blue bubble rocks (`stage2_bricks.png`), the wall turret and Tetran sprites (not
`dome.png`, which is hand-drawn),
the Tiled icons, and `stage2.tmx` itself (turrets, domes and eye rocks are found by matching the sprites against the
map). It overwrites `stage2.tmx`, so run it only to start over; after that, edit the map in Tiled.

### Stages 3–7 art
`tools/stages_build.py` (with `tools/stagebuild.py`) builds stages 3–7 from the maps in `reference material/`:

| Stage | Map | Source | Scrolls | Music |
|---|---|---|---|---|
| 3 OPERATION THREE | Lavinia | `salamander_stage_3_lavinia.png` | right; drops down a shaft at x 3480 to a lower corridor | `04. Combat.mp3` |
| 4 OPERATION FOUR | Eioneus | `salamander_stage_3_eioneus.png` | up | `05. Starfield.mp3` |
| 5 OPERATION FIVE | Kierke | `salamander_stage_3_kierke.png` | right | `06. Burn the Wind.mp3` |
| 6 OPERATION SIX | Op 4 | `salamander_stage_4.png` | up (shaft at x 256–512) | `07. Destroy Them All.mp3` |
| 7 OPERATION X | Op X | `salamander_stage_x.png` | right | `09. Prophet Fire.mp3` |

For each stage it writes the art strips (`stageN_t*.png`), the destructible bricks (`stageN_bricks.png`: Lavinia's blue
spools, X blocks and red gates, Eioneus' bubbles, Op X's pink brick walls), the collision layer, the camera path,
checkpoints, a few waves of the existing flying enemies, and every enemy and boss it finds in the map: each one is cut
out as a sprite (`hatch.png`, `jumper.png`, `seed.png`, `spider.png`, `crab.png`, `vent_red.png`, `boss2.png` …
`boss6.png` …), taken out of the art and placed as an object. Run it with stage numbers to rebuild only those
(`python3 tools/stages_build.py 3 5`), add `--preview` for check pictures. It overwrites the maps, so after
that, edit them in Tiled.

### Other tuning
Boss HP: `GameScreen.spawnBoss()`; boss attack patterns: `Boss.update()`; enemy behaviour: `Enemy.java`.

## Swapping the art
Overwrite any PNG in `assets/sprites/` keeping the same frame size (animated sprites are horizontal strips):

| File | Size | Frames |
|---|---|---|
| player, player_red | 96×16 | 3 × 32×16 (level, up, down); player_red = player 2's ship |
| player_blue_vertical, player_red_vertical | 3 frames side by side | the ship on a vertical stage, pointing up: banking left, level, banking right (equal widths; a dark background is made see-through). A single square frame also works (player_red_vertical has one for now); player_red = player 2 |
| wall turret red, wall turret green | 32×16 | 2 × 16×16: barrel straight out, barrel diagonally down; facing right (mirrored / flipped for the other directions); red = drops a capsule |
| dome | 36×32 | 2 × 18×32: closed, open; facing right (mirrored on the right wall). The dome takes its size from the picture |
| rock_eye | 32×32 | 1: stage 2's rock with an eye (a dark background is made see-through) |
| rubbles | 32×32 | 1: what a destroyed eye rock leaves behind; its drawn pixels are solid like terrain |
| tetran ring | 8×8 | 1 (one ring of Tetran's tentacles) |
| option | 24×12 | 2 × 12×12 |
| fan, walker (stage 2) | 32×16 | 2 × 16×16 |
| bio walker red, bio walker blue (stage 1 walkers) | 80×16 | 5 × 16×16, played forward then backward; no left / right facing; red = drops a capsule |
| celtic frost | 13×15 | 1 |
| Legacy | 64×16 | 4 × 16×16, played forward then backward (0 1 2 3 2 1 …); drawn white, painted green (#73ff73) in the game |
| missing_link | 112×16 | 7 × 16×16 (barrel roll, looped) |
| rugal, rugal_red | 48×16 | 3 × 16×16: level, diving (45° or more down), climbing (45° or more up); rugal_red = one that carries a capsule |
| rusher | 36×12 | 2 × 18×12 |
| bio turret red, bio turret blue | 16×16 | 2: barrel diagonally left, barrel up (mirrored to aim right, flipped for ceilings) |
| rock, crusher_shaft | 16×16 | 1 |
| crusher_head | 32×16 | 1 |
| capsule | 24×12 | 2 × 12×12 |
| explosion | 120×24 | 5 × 24×24 |
| shot / laser / missile / ebullet | 12×4 / 40×4 / 8×6 / 6×6 | 1 |
| lava | 16×8 | 2 × 8×8 |
| ffield (F.FIELD bubble) | 40×32 | 1 |
| shield, shield_red (SHIELD in front of the nose; red = 3 hits or fewer left) | 32×16 | 4 × 8×16 |
| boss0, boss1, boss2 | any | 1 (the boss's size and hit shape come from the picture; boss1 = Tetran's body) |
| boss1_core | any | 1: Tetran's core, its only weak spot. The game puts it over the hole cut out of `boss1.png` for it (wherever that hole is), so the body must have the core cut out. Works for any boss: `bossN_core.png` |
| core_block | 8×2 | 1: one of the 4 blocks under Tetran's core, drawn in the dark space below the red core of `boss1_core.png` (rows 26, 30, 34, 38 from its top, `CORE_BLOCK_ROWS` in `GameScreen`). One disappears for every 20% of health the boss loses, lowest first |
| tiles | 48×48 | terrain tileset for Tiled (8×8 pieces): row pair = stage, column pair = fill / floor surface / ceiling underside |
| bg0–bg2 | 480×272 | parallax backdrop, must tile horizontally (the game shows a 256×168 part) |
| stars | 480×272 | transparent star layer |
| hud | 128×8 | bottom HUD pieces cut from the original: meter slot, highlighted slot (1P / 2P), ship icons, E icon, yellow box |

Sounds live in `assets/sfx/*.wav` (`shoot, hit, boom, pickup, power`).
