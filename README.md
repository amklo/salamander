# SALAMANDER – a Gradius-style side-scrolling shooter (Java + LibGDX)

Seven stages, a power-up meter, bosses, and lots of scrolling terrain hazards.
All art and sound is generated (`tools/generate_assets.py`) and is easy to swap out.

## Open in IntelliJ IDEA
1. **File ▸ Open…** and pick this folder (the one containing `build.gradle`). Choose *Open as Project* / *Trust Project*.
2. Wait for the Gradle import to finish (first run downloads Gradle 9.6.1 and LibGDX 1.14.2 – internet required).
3. Make sure the project JDK is **17 or newer** (File ▸ Project Structure ▸ Project SDK; 21 is fine) – Gradle 9 needs it.
   Gradle's own JDK is under Settings ▸ Build, Execution, Deployment ▸ Build Tools ▸ Gradle ▸ Gradle JVM.
4. Run the shared configuration **"Salamander (Gradle run)"** (top-right run dropdown), or open
   `lwjgl3/src/main/java/com/example/salamander/lwjgl3/Lwjgl3Launcher.java` and click the green ▶ next to `main`.
   * macOS: if you run `Lwjgl3Launcher` directly, add the VM option `-XstartOnFirstThread` (Run ▸ Edit Configurations).
     The Gradle run configuration already does this for you.

Command line: `./gradlew lwjgl3:run`
Build a runnable jar: `./gradlew lwjgl3:dist` → `lwjgl3/build/libs/salamander-1.0.0.jar`

## Play in a web browser
`./gradlew teavm:gdx_teavm_web_js_run`, then open http://localhost:8080 (stop it with Ctrl+C / the stop button).
The first build takes a while; the game loads all its assets before the title screen appears.
`./gradlew teavm:gdx_teavm_web_js_build` only builds it: `teavm/build/dist/js/webapp` is a folder of plain web files
that any web server can serve. Music and sound start with the first key press (a browser rule). Gamepads: Safari
works; Chrome on a Mac may not see the pad at all.

### Putting it on itch.io
1. `./gradlew teavm:itchZip` → `teavm/build/salamander-web.zip` (the web build with `index.html` at the top).
2. itch.io ▸ *Create new project* (later: *Edit game*): **Kind of project: HTML**; upload the zip and tick
   **This file will be played in the browser**.
3. *Embed options*: size **1024 × 768**, tick **Fullscreen button**.
4. *Visibility & access*: **Restricted** (only you and people you add can open it), then Save.
To update it: run `itchZip` again, upload the new zip on the same page and delete the old upload.

## Controls
| Key | Action |
|---|---|
| Arrows / WASD | Move |
| Z / Space (hold) | Fire (also drops missiles once you own them) |
| X / Shift | Activate the highlighted power-up slot |
| P / Esc | Pause |
| Enter | Start / continue; in play, hold + direction to aim the DOUBLE shot, the LASER or the RIPPLE |
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
| R1 (hold) + direction | Aim the DOUBLE shot, the LASER or the RIPPLE |
| R2 | Options orbit the ship (again: normal) |
| L1 | Options in a V formation (again: normal) |
| L2 | Options frozen around the ship, moving with it 1 to 1 (again: normal) |
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
  up each side. Where the scenery starts, a 15 s **asteroid storm** rains rocks down the screen: they aren't enemies,
  they bounce off the scenery and keep falling, shots pass through the small ones, and only a SHIELD or F.FIELD breaks them; shooting a big one splits it into three
  fast small ones. Wall turrets, domes that send out waves of 7 green flyers (they circle twice, shooting
  after the first lap, then leave), and rocks with an eye that
  shoots back guard the way to **Tetran**, whose four tentacles turn slowly around it like a revolving door; the rings
  block shots and the tips fire at you. Shoot its body.
* With **DOUBLE**, hold Enter (pad: R1) and press a direction to aim the extra shot in one of 8 directions; the ship holds still while you aim.
* With **LASER** or **RIPPLE**, the same aims the whole weapon: it fires in the chosen direction (one of 8) until you aim it somewhere else.
* **Option formations** (pad R2 / L1 / L2, keyboard C / V / F): the options circle the ship, fly in a V (above and below it; on a vertical stage beside it), or freeze where they are around the ship and move with it 1 to 1. The same button again puts them back in the normal trail; another formation's button switches straight to that one. They glide to their new places.
* When you die, your ship's options are left behind as green capsules (`powercapsule_green.png`) where they were
  flying: catch one to get an option back at once. A green capsule that leaves the screen comes back once at the
  opposite edge, for one more chance. On **NORMAL** the ship also comes back without its other power-ups (the power meter
  keeps its highlight); on **EASY** it keeps them. Every new stage starts without power-ups.
* **Difficulty**: on the title screen, left / right switches between NORMAL and EASY (remembered between runs). EASY
  starts (and continues) with 5 ships instead of 3.
* **Style**: on the title screen, up / down moves between difficulty and style, left / right switches the style between
  REMAKE (the remake's own pacing, waves and enemy behaviour) and MSX (as measured from an MSX recording; remembered
  between runs). So far only stage 1 differs. MSX scrolls at the MSX speed (19.2 px/s, the same on every stretch), has
  the MSX opening (8 Celtic Frost waves, 2 Missing Links, 2 Rugal trios and 2 single Rugals, timed as in the recording)
  and MSX enemy behaviour: claw arms grow straight out of the rock, then keep a straight chain that swings to point at
  the ship (up to 65° from straight out), and their blue weak ball moves along the chain the further it leans; towers
  throw their sprays in bursts of three, 0.85 s apart; walkers jump about half a screen high toward the ship and come
  back down. See map property `speedMsx`, path
  property `speedsMsx`, object property `style` and `Level.msx()`.
* In both styles: claw arms start tucked into the rock (only the claw shows) and grow out once they are on screen;
  their balls stop the laser, the blue weak ball too (the laser hits it there); all amoebas change colour together
  (yellow, cream, white, green, 3 frames each); seven red walkers run in from the left in the blue tentacle passage;
  the fangs slide in and out as measured on the MSX (see `tooth`).

## Project layout
A fuller map of the code (what each class holds, conventions) is in [CLAUDE.md](CLAUDE.md).
```
build.gradle / settings.gradle    Gradle multi-module build (core + lwjgl3)
core/src/main/java/com/example/salamander/
  SalamanderGame   entry Game class, MSX resolution (256x192: 256x168 playfield + 24px HUD)
  TitleScreen     title / attract screen
  GameScreen      one stage: state machine, scrolling, spawning, power-up meter (the subsystems below are its helpers)
  PlayerWeapons, Collisions, GameRenderer   the ships' guns, collisions, drawing
  BrainBoss, LaviniaStage, LaviniaRoom, EioneusRoom, BarBosses, KierkeBoss, Op4End, GunLasers   bosses and stage set pieces
  PixelFont       pixel fonts for all text (5x5, 5x7, MSX digits)
  Level           loads a stage from its Tiled map; terrain / collision queries
  TiledMap        reader for Tiled .tmx / .tsx files
  Enemy, Boss, Hazard, Bullet, Player   entities
  Assets          lazy sprite/sound loader
lwjgl3/…/Lwjgl3Launcher.java      desktop launcher
assets/sprites, assets/sfx        sprites (ships, enemies, bosses...) and sound (classpath resources)
assets/maps                       the stages as Tiled maps + tilesets, and the stage pictures: art strips
                                  (stageN_t*.png), brick art (stageN_bricks.png), tiles.png, brick_pattern.png
                                  <-- design stages here
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
| `bannerTitle`, `subtitle`, `planet` | the intro banner on a black screen before the stage (as on the MSX): first line (default `name`), the line under it (e.g. `-SET THE LEEK SYSTEM-`) and, a little lower, the planet (e.g. `PLANET LAVINIA`); empty lines are left out |
| `speed` | base scroll speed in px/s |
| `speedMsx` | base scroll speed in MSX style (stage 1: 19.2, measured from the MSX); without it `speed` is used |
| `noStars` | `a,b` in Tiled coordinates along the scrolling (x on a horizontal stage, y on a vertical one): no stars between a and b, plain black instead, where the terrain covers the sky. Just `a`: from a to the end of the stage. Lavinia `1752,4888` (from where its ceiling starts to the end of the base; space and the boss still have stars); Eioneus `0,3624` (black from just below the lowest rock of the opening to the top) and Op 4 `0,4672` (the whole stage: after its terrain dissolves the bosses are fought in black space, as on the MSX). Without it the stars cover the whole stage |
| `music`, `bossMusic` | music files in `assets/bgm/` (MP3, OGG, WAV or MIDI), e.g. `02. Power of Anger.mp3` |
| `musicLoop`, `bossMusicLoop` | loop length of the stage song / boss song in seconds: when the song reaches the loop end it jumps back this far and carries on seamlessly (the game blends the seam). Works with MP3, OGG and WAV. Without it the song starts over from the beginning. The original songs are used as they are: `02. Power of Anger.mp3` 25.599002 (stage 1), `03. Fly High.mp3` 25.601270 (2), `04. Combat.mp3` 22.405057 (3), `05. Starfield.mp3` 25.598730 (4), `06. Burn the Wind.mp3` 27.198390 (5), `07. Destroy Them All.mp3` 33.600181 (6), `12. Last Exit.mp3` 32.000771 (7), boss fights `08. Poison of Snake.mp3` 35.207120 |
| `musicLoopEnd`, `bossMusicLoopEnd` | where the loop ends (seconds from the start of the song), before the song fades out: there it jumps back `musicLoop` seconds, so the fade is never heard. Without it the loop ends just before the end of the file. Power of Anger 59.881, Fly High 55.998, Combat 47.174, Starfield 55.113, Burn the Wind 59.459, Destroy Them All 70.525, Last Exit 67.158, Poison of Snake 67.036 |
| `bossMusicAt` | (old) the boss music starts when the middle of the screen reaches this x. Use the `bossmusic` object instead (see *Camera*); a map with that object ignores this property |
| `background` | parallax backdrop sprite (`bg1`, `bg2`; leave it out for plain black space) |
| `lane` | height the stage was laid out for (default 272). If it is taller than the 168px view, the camera drifts up and down within it to follow the ships. Stages 1 and 2 use 168 (no drift) |
| `vertical` | `true` = a vertical stage (stages 2, 4 and 6): the path runs upward, the ship (`player_blue_vertical.png`) points up and every weapon fires up. Objects are triggered when the top of the screen reaches them |
| `brickArt` | picture the bricks are cut from, as big as the map (stage 2: `maps/stage2_bricks.png`, the blue bubble rocks; the path is relative to `assets/`). Each brick shows its own 8×8 piece of it. Leave it out for the repeating brick pattern |
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
| `walker` | stage 1 and Kierke use the bio walker tiles (blue, and red = `drop` on), the other stages the old walker tile; standing on the floor – or flip it vertically (Y) to hang it from the ceiling. **Kierke leapers** (`leap` = `left` / `right`, as on the MSX): put the object at the screen edge it comes in from (2 px left of the screen's left edge, or 12 px inside its right edge) at the height it comes in at; it appears when that edge reaches it, drifts in and drops onto the floor (flipped / `ceiling`: "falls" up onto the ceiling), walks toward the ship to about mid-screen, then keeps walking away from it and hopping back toward it (higher when the ship is higher up; a hop that reaches the ceiling grabs it). It doesn't shoot and is gone once it walks off an edge. Tuning: `LEAP_*` in `Enemy` | `ceiling`, `behind` (runs in from the left edge once the screen has passed this spot), `leap`, `drop` |
| `frost` | Celtic Frost wave. x = where it is triggered; y = the line it enters on (10–42% of the screen height from the bottom, or from the top when flipped); it leaves on the mirrored line. It flies a zigzag (measured on the MSX): in from the right, left to 64% of the screen width, diagonally (45°) back toward the middle for 64 px, left to 28%, diagonally back again until the whole sprite is flush with the far screen edge, then right along that edge and out (`Enemy.frostAt`; 80 px/s on each axis, so the diagonals move as fast across as along). MSX style places them on the MSX lines: centre 23.5 px above the bottom (low) / 32.5 px below the top (high). Flip vertically (or `upper`) to mirror the Z top to bottom. Vertical stage: the wave comes down from the top along the object's column (10–42% of the screen width from the left edge; flipped = from the right edge) to 112 px below the top, flies 45° up toward the middle (16 px for the first frost, 16 more for each one behind it), straight down 72 px, 45° up toward the middle again until 136 px across from its column, then straight up and out (measured in the stage 2 recording) | `count` (6), `drop`, `upper` |
| `legacy` | Legacy (stage 2, as on the MSX): one Legacy that hangs in the world where it is placed (it scrolls with the scenery), drifts or darts sideways toward the nearest ship (up to 45 px/s) and now and then dives at it; it doesn't shoot. It leans with its sideways speed (frames 0–6 of `legacy.png`) and flickers magenta / blue / red. With `flyby` on it is the old Legacy wave instead: placed and flying like a `fan` wave (4 by default), shooting once it has flown past a ship. Tuning: `LEGACY_*` in `Enemy` | `drop` (off for a hovering one), `flyby`, `count` (4, fly-by only) |
| `possessed` | Possessed group (stage 2, as on the MSX): x = the middle of the cluster across the screen; triggered when the top edge reaches it. The members come in from the top edge one after another (0.3 s apart), each to its spot in a small cluster near the top, hover there 2.2 s shooting aimed shots at the nearest ship (every 0.6–0.9 s), then fly back up and out. Red and cyan ones (`reds` of them red, spread through the group); shooting down the whole group leaves a capsule (`drop`). Tuning: `POSSESSED_*` in `Enemy` With `row` (Eioneus): the members come in one after another (0.25 s apart) diagonally from the top, stop for 0.6 s in a row 45 px below the top edge (16 px apart, x = the first one's place; the row runs toward the nearest ship's side), shooting, then fly on diagonally up and out. On a horizontal stage (Lavinia) a row comes in from the top edge too: `place` = the first place's x on the screen, `down` = how far below the top edge the row is (default 45), `side` = `left` / `right` (default: toward the nearest ship), `gap` = seconds between members (0.25), `colours` = the members' colours in order, e.g. `ccrrc`. | `count` (4), `reds` (2), `drop`, `row`, `place`, `down`, `side`, `gap`, `colours` |
| `whiplash` | Whiplash (stage 2, as on the MSX): x = its column; triggered when the top edge reaches it. It slides in from the top edge (or up from the bottom edge with `fromBottom`), stops 42 px inside the screen for 0.6 s, then darts off diagonally: on in the same direction and sideways toward the nearest ship, until it has left the screen. As it stops it fires one expanding laser like the Omen's, toward the far edge. Tuning: `WHIP_*` in `Enemy` | `fromBottom`, `drop` |
| `battleaxe` | Battleaxe row (Kierke opening, as on the MSX): triggered like other objects (x); its height comes from `high`, not from where the object is. A rigid row of 6, 16 px apart, comes in from the right edge at 40 px/s; when its front place reaches x 136 on the screen the whole row goes straight up (a high row: down) at 80 px/s to the other side, left again to x 16, straight back down (up) and out through the left edge. Members that are shot leave a gap; the rest keep their places. It doesn't shoot. Tuning: `BATTLEAXE_*` in `Enemy` | `count` (6), `high`, `drop` |
| `chain` | Kierke chain of green balls (as on the MSX): the object is the first ball (8×8, top-left). When it comes to x 216 on the screen it grows a ball every 0.25 s (each growing 4, 6, 8 px) up to `count`: each one 8 px toward the nearest ship, sideways and up or down (diagonally when both); with rock in the way it steps only sideways or only up / down, a wall ends it, and it stops once it reaches the ship. It only grows while its root, the aberrant just under the first ball, is alive. The balls stay put (also once the aberrant is shot), can't be destroyed, stop shots (lasers too) and hurt a ship. Tuning: `CHAIN_*` in `Enemy` | `count` (7) |
| `aberrant` | Aberrant (Kierke): the plant at the root of a chain (the chain object's first ball sits just above it). Stays where it is placed and doesn't shoot. Shot down it withers (the blue frame) and stays: shots pass through it, but a ship still crashes into it. | `drop` |
| `nest` | Kierke nest: the object is the whole 38×22 picture; as drawn it hangs from the ceiling, flip it vertically for the floor. 6 hits. `spawns` says what comes out: `ug` (default) – once its middle comes to x 232 on the screen it lets 6 Ugs out of its mouth, 0.2 s apart; `creeping death` – while its middle is between x 16 and 200 on the screen it lets out bursts of 4 Creeping Death (0.15 s apart, a burst every 2.5 s): each rises out of the mouth at 50 px/s for 1.3 s, then homes in on the nearest ship at 45 px/s (a ceiling nest's come out going down); `mines` – the four nests floating in the web: once its middle comes to x 208 on the screen it lets out one burst of 5 flying mines (`sprites/flying_mines.png`), 0.5 s apart; each appears to the right of the nest, curls anticlockwise up and over it, then dives at the nearest ship (up to 180 px/s; `MINEK_*` in `Enemy`); `none` – nothing comes out. Tuning: `NEST_*`, `UG_*`, `CREEP_*` in `Enemy` | `spawns` (ug), `drop` |
| `gamma` | Gamma (Kierke blue ball, as on the MSX): triggered like other objects (x); it comes in at the right edge at the object's height and drifts left through the world at 16 px/s, bouncing slowly on the floor (`ceiling`: on the ceiling) about 35 px high every ~2.3 s; it rides up over rock it runs into and doesn't shoot. Tuning: `GAMMA_*` in `Enemy` | `ceiling`, `drop` |
| `dome_type3` | Eioneus dome on a side wall (flip it horizontally for the right wall), drawn with stage 2's `dome.png` (18×32): once its middle is 40 px below the top edge it lets out bursts of 4 green flyers (0.5 s apart, a burst every 5.3 s while it is on the screen), showing the open frame meanwhile. Each green flyer flies the loop measured in the recording (along the top, a big loop, a smaller one, then up and out; mirrored from the right wall). 5 hits. Tuning: `BLUE_DOME_*`, `GREEN_*` in `Enemy` | `drop` |
| `heathen` | Lavinia pillar with a gap of breakable bars: triggered when the camera reaches it, it comes in at the right edge and rushes left at 100 px/s; the picture (gap at the top, centre or bottom) is random each time. Indestructible; its 4 bars take 3 hits each and break front first; once they are gone a ship fits through the gap. Tuning: `HEATHEN_*` in `Enemy` | – |
| `block_small` | Lavinia small block: once 32 px inside the right edge it slides up and down between the ceiling and the floor around it (40 px/s), starting toward the far end; indestructible, stops shots and lasers | – |
| `block_big` | Lavinia big block in its slot: once on screen it slides left (20 px/s); 20 hits; a laser that hits it is reflected back to the left and can hit a ship | – |
| `laser_gun_point` | Lavinia: an indestructible laser emitter pointing right (flip it to point left); fires a laser pulse to the rock across every 1.8 s (`delay` shifts it); `twin` = also one that many px to the right, facing the other way | `delay`, `twin` |
| `dark_zone` | Lavinia rectangle: the lights go off when the camera's left edge reaches its left side (no background, stars or terrain drawn; it all stays solid) and come back when a ship touches an `item_spark` inside it, or when the camera's left edge reaches its right side | – |
| `item_spark` | Lavinia: the spark item (`sprites/item_spark.png`); touching it turns the lights of its `dark_zone` back on (300 points) | – |
| `ledzep_zone` | rectangle: while the camera's right edge is inside it, Led Zeps come at random (every `interval` s on average) at either edge, on the floor or ceiling | `interval` (2.5) |
| `laser` | Lavinia wall laser: the beam between two pillars (the object is the beam; written by `tools/stages_build.py`). The emitter is part of the wall art at its right end (`emitter` = `left` for the other end); its rock cell is cleared so shots reach it. The beam (the Laser Guards' pattern) is on all the time, hurts a ship and lets shots through; shooting the emitter (6 hits) puts it out. | `emitter` |
| `dome_type2` with `gunhos` | Lavinia (both styles): the dome is a Gun Ho hatch instead of spitting amoebas. It lets out this many Gun Hos, one each time its left edge reaches x 208, 144, 80, 16 on the screen; each hops out (up 48 px, 24 px left, down to the floor, 16 px left), then lines up with the nearest ship, fires and flies off left (80 px/s). Tuning: `GUNHO_HOP_*` in `Enemy`, `GUNHO_HATCH_*` in `Hazard` | `gunhosMsx` |
| `laserbattery`, `laserbattery_big` | Eioneus laser battery (the object is its picture; its rock cells are cleared, it is an enemy). Small: on a wall, its beam goes sideways to the rock across (flip it horizontally + vertically to face left). Big: on the floor its beam goes up to the big battery hanging above it (flip that one horizontally + vertically). The beam (the Laser Guards' red / blue pattern) is on all the time, hurts a ship and lets shots through; it goes out when either battery is shot down (6 hits each). Tuning: `BATTERY_*` in `Enemy` | `drop` |
| `gremlin` | Eioneus gremlin (yellow; `red` = the red one): the object marks where it comes up through the bottom edge (it appears when that place reaches the bottom of the screen) on the left wall (`right`, or placed in the right half: the right wall). It climbs its wall keeping to the rock face at 48 px/s, walks for 2 s, stops for 3 s to aim (frame 0), firing an aimed shot 0.4 s into the stop, then walks on. Tuning: `GREMLIN_*` in `Enemy` | `red`, `right`, `drop` |
| `seed` | Eioneus base (the big red pod; the object is its 44×46 picture, bottom-left). The whole base is part of the map art (`stage4_t*.png`, painted as destroyed); the enemy is only its 20×21 middle (`sprites/eioneus_base_core.png`, 4 frames: closed, opening, open, destroyed), 12 px in from the object's left and 12 px up from its bottom. Once its middle is 50 px below the top edge it opens (0.4 s) and stays open, letting out bursts of 4 flying mines from its middle (0.5 s apart, a burst every 4 s while on screen). It is drawn under the other enemies. It is ground: ships fly over it, shots hit it (8 hits); destroyed it shows its last frame and stays. Each flying mine first takes off (frames 0–2, a third of a second each; it can't hit or be hit), flying the loop measured in the recording, then (frames 3 / 4) finishes the loop and homes in on the nearest ship. About one in six is red and leaves a power capsule. Tuning: `SEED_*`, `MINE_*` in `Enemy` | `drop` |
| `link` | Missing Link, a single enemy: x = where it is triggered, y = the middle of its bob. It comes in from the right edge, drifts left bobbing up and down and shoots at the ships. **MSX style** (`swing` > 0, Kierke): the object is where it comes in (at the right edge, 14 px of it showing) at the top of its swing (`up`: the bottom); it drifts left at 40 px/s relative to the screen, swinging `swing` px up and down (~2.4 s for 72 px), the picture following the swing (frame 0 at the ends, 3 half-way), and fires `burst` aimed shots in a line (60 px/s) once, 1.3–2 s after it appears. Tuning: `LINK_MSX_*` in `Enemy` | `drop` (off), `swing` (0), `up`, `burst` (1) |
| `green_mine` | Green mine (Kierke, Op 4): the object is where it comes in (at the right edge, 11 px of it showing; vertical stages: at the top edge at its x when the top edge is 40 px below the object, or at the bottom edge with `fromBottom`); it homes in on the nearest ship at 33 px/s relative to the screen, through rock, and doesn't shoot. Tuning: `GMINE_*` in `Enemy` | `drop`, `fromBottom` |
| `rugal` | Rugal: comes in from the right edge at this height (triggered like the flying waves) and flies left, steering toward the nearest ship and shooting back at a ship once it has flown past it. Place three a little apart for a trio. Two tiles: normal, and red (`drop` on: `rugal_red.png`, leaves a power capsule) | `drop` (off) |
| `turret` | bio turret on the floor, or flipped vertically under the ceiling. Two tiles: blue, and red (`drop` on: leaves a power capsule) | `ceiling`, `drop` |
| `bamda` | anywhere (the pulsing rock; its size comes from `sprites/bamda.png`) | – |
| `tower` | where the fully grown tower stands, its root end 8 px into the rock. Flip vertically to turn it over (floor ↔ ceiling); don't resize | – |
| `tooth` (`fang full.png`) | its x, and its tip somewhere in the passage: the game makes it come out of the ceiling (or floor) until the tip touches the floor (or ceiling). As on the MSX (both styles) it slides out and back at 85 px/s, holds fully out, then rests with its tip showing; stage 1's six are timed from the recording | `ceiling`, `delay` (s before the first push), `period` (s per cycle), `holdOut` (s fully out, 0.3), `rest` (px of tip showing while resting, 8), `short` (px it stops short of the opposite rock, 0), `switch` (goes right through into the opposite rock and comes out of that side next time, back and forth; stage 1's 5th and 6th) |
| `rock` | hanging under the ceiling; falls when a ship flies underneath | – |
| `volcano` | on the floor: spits lava up. `spits=left` / `right` (Eioneus): a side volcano on a rock tip (the object's middle is where the rocks come out). It throws bursts of 3–5 red rocks (`sprites/rock_small_red.png`, 0.12 s apart, a burst every 1.8–3 s while it is on screen) out sideways at 45–90 px/s, drifting up or down; they slow down and fall back into their wall (pulled at 85 px/s²), as in the MSX recording. Like storm rocks they ignore shots, hurt a ship, and a guard breaks them. Tuning: `Meteor.SIDE_*` | `spits` |
| `crusher` | on the ceiling or floor surface it pushes out from | `ceiling`, `minLen`, `maxLen`, `speed`, `phase` |
| `wallturret` | stage 2 turret on a rock wall to its left; flip it horizontally (X) for a wall to its right. It turns its barrel toward the ship: straight out, or diagonally up / down. Two tiles: green, and red (`drop` on: leaves a power capsule). `diag` (or a vertical flip for up) only sets the barrel it starts with | `drop`, `diag` |
| `dome_type2` | stage 3 dome on the floor, or flipped vertically (Y) on the ceiling: the stage 2 dome turned a quarter; it spits amoebas (or Gun Hos: see `gunhos` above) | `gunhos` |
| `dome_type1` | stage 2 dome against the left wall; flip it horizontally (X) for the right wall. Closed until it has been on screen for 1 s, then it opens for good and sends out waves of 7 green flyers (`sprites/green_flyer.png`), one after the other: they fly a circle (radius 40) above the dome twice, shoot at the ships from the second lap on, then fly on away from the dome and off the screen. The next wave comes 2.5–4 s after the last flyer of the previous one is gone; shooting down all 7 leaves a capsule. (Stage 3's floor / ceiling domes still spit amoebas.) 12 hits, 500 points. Tuning: `DOME_FLYER_*` in `Enemy`, `DOME_WAVE_*` in `Hazard` | – |
| `boss` | where the boss sits in the map (the box = its picture, `sprites/boss<N-1>.png`). With `fixed` on (stages 5–7), the boss stays there instead of flying in, and wakes up as soon as it comes on screen. Without it, the boss flies in as usual | `fixed` |
| `exodus` | Op 4 (stage 6) Exodus row, as on the MSX (`sprites/exodus_cyan.png`, `exodus_red.png`): the object is the first (left) one; the others follow to its right, `gap` px apart. Triggered when the top edge reaches it (the object sits 32 px above where the top edge is when they come in). They come in at the top edge, drop straight down (80 px/s) until 24 px inside, then make for the nearest ship at 42 px/s on each axis (straight, sideways or 45°), relative to the screen; after 10 s they give up and drop out. `colours` = `c` / `r` per member; a red one leaves a capsule. Tuning: `EXODUS_*` in `Enemy` | `count` (3), `gap` (18), `colours` (ccc), `drop` |
| `artillery` | Op 4 wall gun (`sprites/artillery_green.png`; `red` = `artillery_red.png`, leaves a capsule) on the left wall; flip it horizontally for the right wall. It stays put and fires an aimed shot (80 px/s) 1.4 s after it comes on screen, then every 1.3 s. As many hits as a turret (3 + stage index). Tuning: `ARTILLERY_*` in `Enemy` | `red` |
| `blue_asteroid` | Op 4 blue rock clump (`sprites/blue_asteroid.png`), placed in the world: once on the screen it sinks slowly down (5–30 px/s, its own speed). It can't be destroyed: shots stop on it with a spark and homing shots ignore it. Tuning: `BLUE_ASTEROID_*` in `Enemy` | – |
| `moai` | Op 4 moai head (`sprites/moai.png`) on the left wall; flip it horizontally for the right wall. It fires rings (`sprites/ring_laser.png`, yellow or white by turns) from under its mouth: bursts of 6, 0.4 s apart, a burst every 3.7 s, each ring aimed at the nearest ship (52 px/s). With `walk` (default) it also steps out from its wall 32 px and back (24 px/s, waiting 1 s at the wall). As many hits as a bamda (12). Tuning: `MOAI_*` in `Enemy` | `walk` (on) |
| `dome_type4` | Op 4 wall dome, drawn with stage 2's `dome.png` (18×32, open while letting flyers out); flip it horizontally for the right wall. Like `dome_type3` it lets out bursts of green flyers once its middle is 40 px below the top edge, but these fly straight out of its mouth, then curve round toward the nearest ship. `burst` = flyers per burst (0 = none; `burstMsx` for MSX style). Tuning: `GREEN6_*` in `Enemy` | `burst` (4), `burstMsx` |
| `base6` | Op 4 base (`sprites/stage_6_back_ground_base.png`, 22×22: closed, opening, open, destroyed), the whole picture, bottom-left. Like the Eioneus base it is ground and opens once its middle is 50 px below the top edge, but it lets out a single burst of 5 flying mines (0.55 s apart). Each mine rises 24 px out of it during its take-off, flies a loop up to the left and round to the right (measured on the MSX), then homes in on the nearest ship. Tuning: `SEED6_*`, `MINE6_*` in `Enemy` | `drop` |
| `orb6` | Op 4 bouncing ball (`sprites/orb.png`): x = where it comes in at the top edge; triggered like `exodus`. It bounces up and down fast (150 px/s) between the screen's top and bottom edges and rock, drifting sideways toward the nearest ship (12 px/s); after 15 s it drops out. 3 hits. Tuning: `ORB_*` in `Enemy` | – |
| `spark`, `mine`, `gunpod`, `orb`, `vent`, `coregate` | the enemies and hazards of stages 3–7, where the original map has them (see *Stages 3–7 art*). **Not used by the game yet**: they get their behaviour stage by stage | (`open`, `red`, `blue`, `vertical` – notes from the map) |
| `meteors` | asteroid storm (icon: `sprites/rock_big.png`): starts when the top of the screen reaches the bottom of the icon (on a horizontal stage: when the right edge reaches its left side) and lasts `time` seconds. Small rocks (`rock_small.png`) and about one big rock (`rock_big.png`) for every five small ones fall from the top of the screen; most small ones come straight down, the rest slant up to 40° to a side; the big ones are slower. They bounce off the scenery (rock, bricks, rubble, eye rocks, domes) and are always pulled back down to their falling speed, so they keep heading for the bottom of the screen. They aren't enemies: no points, shots go through the small ones. Only a guard breaks them (the SHIELD wears down by one per rock; the F.FIELD takes a hit when one reaches the ship); without one, a rock that reaches the ship destroys it. A shot big rock splits into three small ones (straight down and 50° to either side) at 3× the big rock's speed. Big rocks only on stage 2. Optional (Op 4): `speed` = one speed for every rock (px/s), `straight` = all straight down, `xMin` / `xMax` = they come in only between these screen x (px from the left edge). Tuning: constants in `Meteor.java` | `time` (15), `speed`, `straight`, `xMin`, `xMax` |
| `eye` | stage 2 eye rock (`sprites/rock_eye.png`, 32×32): the whole rock with its eye, placed where it should be (it is not part of the map art or collision). The eye shoots at the ships like a bamda; the rock is solid (its exact shape). 3 hits destroy the whole rock (300 points); it leaves rubble (`sprites/rubbles.png`) that stays as solid, indestructible terrain until the stage restarts from a checkpoint | – |

**Domes**: `dome_type1` = stage 2 (green flyers in circles), `dome_type2` = Lavinia floor / ceiling dome (amoebas or Gun Hos), `dome_type3` = Eioneus (green flyers, measured loop), `dome_type4` = Op 4 (homing green flyers). All use `sprites/dome.png`.

**Style**: any object can have the property `style` = `msx` or `remake`; it then only appears in that style
(title screen). Objects without it appear in both. Stage 1's opening: the remake's waves (objects 119–144) are
`remake`, the MSX ones (from object 189 on) `msx`.

**Camera** (`camera` layer):
* `path` – a polyline drawn through the **centre of the screen**. The game scrolls along it, so a diagonal or
  vertical stretch scrolls the screen diagonally or up/down (as in stage 1). It must stay at least half a screen
  (128 px / 84 px) inside the map edges. Its `speeds` property lists a speed multiplier per segment,
  e.g. `1,0.8,1,0.45,0.8` (`speedsMsx`: the same for MSX style; stage 1 has `1,1,1,1,1`).
  Where the path ends, scrolling stops and the boss arrives.
* `checkpoint` – point objects on (or near) the path: where stage select (S on the title screen) can start a stage.
* `bossmusic` – one point object on (or near) the path: when the screen gets there, the stage music fades out (`BOSS_MUSIC_FADE` in `GameScreen`), a short silence follows (`BOSS_MUSIC_GAP`) and then the boss music starts and plays on into the boss fight. Every stage has one, about 4 s of scrolling before the boss (stage 1: at the start of the brick maze); drag it in Tiled to change when the music changes. Without it the change happens when the boss arrives.

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
| 6 OPERATION SIX | Op 4 | `salamander_stage_4.png` | up (shaft at x 256–512, with a detour through the side room on the left at y ~1900–2190) | `07. Destroy Them All.mp3` |
| 7 OPERATION X | Op X | `salamander_stage_x.png` | right | `12. Last Exit.mp3` |

For each stage it writes the art strips (`stageN_t*.png`), the destructible bricks (`stageN_bricks.png`: Lavinia's blue
spools, X blocks and red gates, Eioneus' bubbles, Op X's pink brick walls), the collision layer, the camera path,
checkpoints, a few waves of the existing flying enemies, and every enemy and boss it finds in the map: each one is cut
out as a sprite (`Omen.png`, `Gun Ho.png`, `nest.png`, `moai.png`, `vent_red.png`, `boss2.png` …
`boss6.png` …), taken out of the art and placed as an object. Run it with stage numbers to rebuild only those
(`python3 tools/stages_build.py 3 5`), add `--preview` for check pictures. It overwrites the maps, so after
that, edit them in Tiled.

### Other tuning
Boss HP: `GameScreen.spawnBoss()`; boss attack patterns: `Boss.update()`; enemy behaviour: `Enemy.java`.

## Swapping the art
The stage pictures are in `assets/maps/` next to the maps: the art strips `stageN_t*.png` and brick art `stageN_bricks.png`
(edit them at the same size), `tiles.png` (48×48, the terrain tileset for Tiled in 8×8 pieces: row pair = stage, column
pair = fill / floor surface / ceiling underside) and `brick_pattern.png` (the repeating brick picture).

Overwrite any PNG in `assets/sprites/` keeping the same frame size (animated sprites are horizontal strips):

| File | Size | Frames |
|---|---|---|
| player, player_red | 96×16 | 3 × 32×16 (level, up, down); player_red = player 2's ship |
| player_blue_vertical, player_red_vertical | 3 frames side by side | the ship on a vertical stage, pointing up: banking left, level, banking right (equal widths; a dark background is made see-through). A single square frame also works (player_red_vertical has one for now); player_red = player 2 |
| wall turret red, wall turret green | 32×16 | 2 × 16×16: barrel straight out, barrel diagonally down; facing right (mirrored / flipped for the other directions); red = drops a capsule |
| dome | 36×32 | 2 × 18×32: closed, open; facing right (mirrored on the right wall). The dome takes its size from the picture |
| green_flyer | 64×16 | 4 × 16×16, played forward then backward (0 1 2 3 2 1 …): the flyers stage 2's domes send out; drawn as they are (already green) |
| rock_eye | 32×32 | 1: stage 2's rock with an eye (a dark background is made see-through) |
| rock_small | 60×15 | 4 × 15×15, looped (the asteroid storm's small rock, tumbling) |
| rock_big | 30×27 | 1: the asteroid storm's big rock (any size: the hit box follows the picture) |
| rubbles | 32×32 | 1: what a destroyed eye rock leaves behind; its drawn pixels are solid like terrain |
| tetran ring | 8×8 | 1 (one ring of Tetran's tentacles) |
| option | 24×12 | 2 × 12×12 |
| fan, walker (stage 2) | 32×16 | 2 × 16×16 |
| bio walker red, bio walker blue (stage 1 and Kierke walkers) | 80×16 | 5 × 16×16, played forward then backward; no left / right facing; red = drops a capsule |
| celtic frost | 13×15 | 1 |
| legacy | 112×16 | 7 × 16×16, white with a black outline: frame 3 = straight on, 0–2 leaning left, 4–6 leaning right. A hovering Legacy shows the frame for its lean and is tinted with the MSX cycle magenta, magenta, blue, magenta, magenta, red (2 frames each); the old fly-by wave plays the frames forward and back, painted green (#73ff73) |
| possessed_red, possessed_cyan | 16×16 each | 1 frame each, drawn as they are: the red Possessed and the cyan one (`possessed.png` is no longer used) |
| whiplash | 15×16 | 1 frame, drawn as it is |
| battleaxe | 16×16 | 1 frame, drawn as it is (Kierke opening rows) |
| chain_balls | 26×8 | the Kierke chain ball as it grows: 8×8 at x 0, 6×6 at (10, 1), 4×4 at (20, 2), drawn as they are |
| aberrant | 32×16 | 2 frames of 16×16: alive, withered (blue) |
| nest | 38×22 | 1 frame, the nest hanging from the ceiling (drawn upside down on the floor) |
| ug | 48×16 | 3 frames of 16×16, cycled every 0.1 s (the Ugs that fly out of the nests) |
| gamma | 64×16 | 4 frames of 16×16, shown in the order 0, 2, 3, 1 (as in the recording), 0.2 s each |
| laser_battery_small, laser_battery_big | 24×16, 32×16 | 1 frame each, drawn as they are; their near-black background box is made see-through |
| gremlin_yellow, gremlin_red | 48×16 | 3 frames of 16×16: 0 = standing still to aim, 1–2 = walking (cycled); drawn turned a quarter to stand on its wall |
| eioneus_base | 178×46 | 4 frames of 44×46 (on a 44.5 px pitch): closed, opening, open, destroyed. Not used by the game any more: its destroyed frame is painted into the stage 4 art, and the middles are `eioneus_base_core.png` |
| eioneus_base_core | 80×21 | the middle of `eioneus_base.png` (x 12–31, y 12–32 of each frame): 4 frames of 20×21, the base enemy |
| flying_mines, flying_mines_red | 80×16 | 5 frames of 16×16, white / red, on black (the black is see-through): 0–2 taking off, then 3 and 4 by turns (0.1 s) |
| creeping death | 60×15 | 4 frames of 15×15, white on a see-through background (any black pixels left in it are made see-through too): shown back and forth every 0.2 s, flickering cyan / light green or red / dark green every 0.1 s |
| missing_link | 112×16 | 7 × 16×16 (barrel roll, looped) |
| green_mine (Kierke) | 32×16 | 2 × 16×16, swapped every 0.21 s (as in the recording) |
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
| boss4_main (Kierke) | 48×170 | the body behind the wall, drawn 89 px right of it; the giant core is the box x 10, y 82, 24×14 (hit only once the middle orb is gone). Behaviour: `Boss.kierke` (`K_*`), pictures: `GameScreen.loadKierkeBoss` |
| boss4_wall_without_shield | 89×320 | the wall: five units 64 px apart (rows 24 + 64k); it loops up / down forever. Black and (6,6,6) are see-through |
| boss4_wall_bar | 7×16 | one bar (10 HP); 11 per unit, at x 1 + 4i |
| boss4_wall_bar_core | 8×16 | the middle unit's two core bars (10 HP), at x 49 and 57 |
| boss4_wall_main_orb | 17×16 | the middle orb's cover at x 65; removed when the middle orb (20 HP) dies, opening the way to the core |
| boss4_wall_shield | 49×16 | indestructible shield over the middle unit's bars (x 0); gone once the four outer orbs are destroyed |
| maps/stage5_web.png (Kierke) | 3888×168 (the map art's size) | not a sprite: the web pixels that flicker as the screen scrolls, captured from the MSX recording. Colour = the pixel's colour when lit; alpha = 255 − 16 × phase (0–7: the first of the 4 scroll states, out of 8 per 8 px, it is lit in), 8 less if the map art doesn't show the pixel. Any stage can have one (`maps/stage<N>_web.png`); see `GameScreen.loadWeb` |
| boss5a, boss5b (Op 4) | 242×132, 250×200 | 1 each: the brain sphere, then the gun units (see *Op 4's end*) |
| option_eater (Op 4) | 32×16 | 2 × 16×16: white, red (it flashes between them). Cut from the MSX recording (233 s) |
| bg0–bg2 | 480×272 | parallax backdrop, must tile horizontally (the game shows a 256×168 part) |
| stars | 480×272 | transparent star layer |
| hud | 128×8 | bottom HUD pieces cut from the original: meter slot, highlighted slot (1P / 2P), ship icons, E icon, yellow box |

### Op 4's end (stage 6)

As in the MSX recording (220–268 s): the scrolling stops at the end of the shaft (camera top 376 in the map, the end of the path) and the whole terrain on the screen breaks up pixel by pixel in 3 s, leaving black space with nothing to run into. Then:

* **Brain sphere** (`boss5a.png`): rises in through the bottom edge (70 px/s) until its top is at the top edge, bobs 4 px there and slides sideways toward the nearest ship (16 px/s). The ships fly over it; only its eye can be hit (60 HP) and the rest lets shots through. It doesn't shoot, but **option eaters** come in through the bottom edge (the first after 4 s, then every 5 s): white / red flashing lattice mines that make for the nearest ship's last option (or the ship) at 30 px/s; one that touches an option eats it (the ship loses an option and the eater is used up). 2 hits, 300 points. Beaten, the sphere breaks up pixel by pixel (3 s).
* **Gun units** (`boss5b.png`, the bar-wall boss: bars, orbs, core shield, as in Eioneus): they appear where they are, pixel by pixel (2.2 s), their top 48 px above the screen, and sway 4 px left and right. Every 3–6 s they move down or back up (16 px/s): down to 40–56 px lower (the MSX's 56 px drop, which brings the upper orbs on screen), up to at most 16 px below where they started. The ships fly over them, so every orb can be reached; only the bars, orbs and the core shield stop shots. Every 6 s lasers (the enemies' laser, `enemy laser red / blue.png`, flashing) grow down from under the columns at five places and fall down the screen (80 px/s, 80 px long); every 6 s a green creature comes out of the middle and goes for the nearest ship. Green mines (Kierke's, homing) fly in from a random edge of the screen every 2.5–4.5 s (the first 3 s after they have appeared). Beaten, they break up pixel by pixel.

Tuning: `OP4_*`, `OEATER_*`, `SPHERE_*`, `GUN_*` in `GameScreen`; `SPHERE_*`, `GUNS_*` in `Boss`; `OEATER_*` in `Enemy`.

**The pixel-by-pixel death** is one reusable piece: `Dissolver` (a picture losing, or with `appear`, gaining its pixels in a random order) and `GameScreen.dissolveDeath(picture or sprite frame or enemy, x, y, seconds)`, which leaves the picture breaking up where it was. Any enemy can die this way by calling `dissolveDeath(enemy, frame, seconds)` instead of `killEnemy`'s explosion.

Sounds live in `assets/sfx/*.wav` (`shoot, hit, boom, pickup, power`).
