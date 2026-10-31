# Salamander – notes for working on the code

A personal remake of Konami's MSX Salamander in Java + LibGDX 1.14.2 (Gradle, Java 11+). Player-facing docs, controls
and the Tiled level-editing guide are in `README.md`; this file is the map of the code.

## Build and run
- `./gradlew lwjgl3:run` – play (the Gradle run task adds `-XstartOnFirstThread` on macOS).
- `./gradlew lwjgl3:dist` – one runnable jar with all assets: `lwjgl3/build/libs/salamander-1.0.0.jar`
  (on a Mac: `java -XstartOnFirstThread -jar salamander-1.0.0.jar`).
- IntelliJ can write its open copy of a file back over changes made outside it: after editing files outside the IDE,
  use **File ▸ Reload All from Disk** (or close the file first).

## Code map (`core/src/main/java/com/example/salamander/`)
GameScreen runs one stage; the big subsystems were moved out of it into classes that each hold `g` (the GameScreen)
and reach the world through it. Many GameScreen fields are package-private for that reason.

| File | What it holds |
|---|---|
| `GameScreen` | One stage: state machine (intro / play / pause / dead / clear / game over), scrolling and camera, spawning, waves (`spawnWaveMember`), shared boss logic (`loadBossPicture`, `spawnBoss`, `bossDefeated`, multi-part bosses), shared helpers (`boom`, `enemyBullet`, `aimedShot`, `ringShot`, `hitSpark`, ripples, `overlap`, `addScore`), all sprite fields |
| `Sprites` | Static helpers that cut sprite sheets into frames and key out dark backgrounds |
| `PlayerWeapons` (`g.weapons`) | Firing ship + options within on-screen limits; shot / DOUBLE / LASER / RIPPLE (straight and aimed), MISSILE / 2-WAY / HOMING and targeting; option trail and formations |
| `Collisions` (`g.collisions`) | `collide()`: player bullets vs enemies, hazards, wall targets, meteors, boss; lasers stopping; ships vs everything (SHIELD, F.FIELD, `hurt`, capsules) |
| `GameRenderer` (`g.renderer`) | Drawing each frame: stars, background lights, Kierke web, terrain, hazards, enemies, boss, bullets, ships, HUD, menus |
| `BrainBoss` (`g.brain`) | Stage 1 boss: eye and core blocks |
| `LaviniaStage` (`g.lavinia`) | Stage 3 set pieces: Gun Ho hatches, Heathens, blocks, darkness + spark, Led Zeps, Rages, lasers reflecting off big blocks |
| `LaviniaRoom` (`g.laviniaRoom`) | Stage 3 last room: hold, dissolving walls, Laser Guard volleys / beams, Omen laser |
| `EioneusRoom` (`g.eioneusRoom`) | Stage 4 boss room: gun pods, their bars, bubbles |
| `BarBosses` (`g.barBosses`) | Bar-wall bosses: Eioneus (boss3) and Op 4's gun units (boss5b) |
| `KierkeBoss` (`g.kierkeBoss`) | Stage 5 machine wall |
| `Op4End` (`g.op4`) | Stage 6 end: terrain breaking up, brain sphere, option eaters, edge mines |
| `GunLasers` (`g.bossGuns`) | Falling boss lasers (Op 4 gun units, Eioneus) and the gun units' creature |
| `Enemy`, `Boss`, `Hazard`, `Bullet`, `Player`, `Meteor` | Entities (`Enemy` is the largest file, ~2,050 lines) |
| `Level`, `TiledMap`, `Wave` | Stage loading from Tiled maps, terrain / collision queries; enemy waves out of spawners |
| `Bgm` (`game.bgm`), `MusicBackend`, `GdxMusicBackend` | Music: `Bgm` keeps the current song, fade-outs into the boss music, pause; a `MusicBackend` (given to `SalamanderGame` by the launcher) makes the sound. `GdxMusicBackend`: plain libGDX Music, any platform. Desktop: `lwjgl3/.../DesktopMusicBackend` (seamless loop points through Java Sound, MIDI). No `javax.*` in core (needed for the web build) |
| `SalamanderGame`, `TitleScreen`, `Controls`, `Assets`, `PixelFont`, `PixelMask`, `Dissolver` | App, title screen, input, asset loading, text, pixel-exact shapes, pixel dissolve effect |

Stages by index (`levelIdx`): 0 stage 1, 1 stage 2 (vertical), 2 Lavinia, 3 Eioneus, 4 Kierke, 5 Op 4, 6 stage 7.

## Conventions
- A new stage boss or set piece goes in its own file following the same pattern (constructor takes the GameScreen,
  a field on GameScreen), not into GameScreen.
- Two styles: MSX (`Level.originalStyle`) and REMAKE. REMAKE always has everything the MSX style has, plus its own
  extras (Tiled objects with `style = remake`).
- Levels are edited in Tiled (`assets/maps`); see README.md.
- Changes are logged in `CHANGELOG.md`.
