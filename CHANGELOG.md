# Changelog

All notable changes to this Salamander remake. Newest first.

## 2026-10-05

Everything since the first push to GitHub.

### Added

**Title and ship select**
- MSX Salamander logo on the title screen.
- Ship select: after joining, each player picks one of 4 ship types with left / right and confirms with fire. Once everyone who joined is ready, fire starts the game. P2 joining mid-game gets the same picker in their meter area.
- Each ship type has its own power-up meter order:

  | Type | Meter |
  |---|---|
  | 1 | SPEED, MISSILE, LASER, DOUBLE, OPTION, SHIELD |
  | 2 | SPEED, OPTION, RIPPLE, LASER, 2-WAY, SHIELD |
  | 3 | SPEED, LASER, DOUBLE, MISSILE, OPTION, SHIELD |
  | 4 | SPEED, MISSILE, SHIELD, LASER, OPTION, RIPPLE |

**New weapons**
- Ripple Laser (types 2 and 4, instead of DOUBLE):
  - An oval ring that grows from 4×8 to 14×48 px as it travels.
  - Limit of 2 on screen per gun.
  - Only terrain at the ring's centre stops it.
- 2-WAY missiles (type 2, instead of MISSILE): one missile drops to the floor and one rises to the ceiling and crawls along it. Each direction has a limit of 1 on screen per gun.

**Stage 1, recreated from the original map**
- Vertical scrolling sections.
- Destructible brick maze:
  - It has an indestructible outer layer.
  - Bricks regrow after 3 s.
- Asteroids.
- Six teeth that move in and out individually and start hidden.
- Brain boss.
- More enemy waves.
- More walkers, including walkers coming from behind and walking upside down on the ceiling.

**Levels and editor**
- All three stages are editable in Tiled (`assets/maps/stage1–3.tmx`):
  - Separate layers for terrain art, collision, bricks, enemies, hazards and the camera path.
  - Collision uses 8×8 px cells.
  - Map properties set the stage name, speed, stage and boss music, and background.
  - See "Editing levels in Tiled" in the README.

**Effects**
- A small explosion where a shot, laser or ripple hits something that survives the hit. This includes shots the boss absorbs during its hit cooldown.
- Boss health bar shows the numbers, e.g. `60/500`.

### Changed

**Firing**
- Every gun (the ship and each option) has its own limits on shots in flight, and fires up to 8 shots per second while under them:
  - Normal shot: 4
  - DOUBLE: 2 forward plus 2 angled
  - Laser: 1
  - Ripple: 2
  - Missile: 2
- DOUBLE aimed straight forward behaves exactly like the normal shot.
- A laser beam follows its gun up and down.

**Players and enemies**
- The shield blinks when it absorbs a hit. The ship no longer blinks. On its last charge, the shield blinks out.
- After a revive, the ship can't be hurt by terrain, teeth or crushers while it's blinking.
- Walkers chase the nearest player for 5 s, then give up and walk off screen.
- The boss can't take damage for 50 ms after each hit.

### Fixed
- Stage 1 art:
  - Fang tips no longer show through the rock.
  - Rock crevices are no longer see-through.
  - Worms, ducks and asteroids were wrongly baked into the terrain; they've been removed from it.
- A thin brick strip on the maze's left edge looked destructible but was solid terrain.
- Walkers no longer get stuck on stalks with gaps in them.
- A duplicate checkpoint at the start of stages loaded from Tiled.

### Removed
- The hard-coded stage 1 layout, replaced by the Tiled maps. These old files are now empty stubs and can be deleted:
  - `core/.../Stage1Map.java`
  - `tools/stage1_emit.py`
  - `assets/maps/stage1.grid`
  - `assets/sprites/stage1_bricks.png`
