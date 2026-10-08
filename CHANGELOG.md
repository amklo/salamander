# Changelog

All notable changes to this Salamander remake. Newest first.

## 2026-10-07

### Changed
- The brain boss's arms can be destroyed. Shoot any of an arm's blue balls (not the claw): an arm takes 12 hits, like the claw arms in the stage, and bursts for 500 points. 2 s later it grows back out of the boss. Phase 3 (chasing the ships) starts as before once both arms are fully out the first time.
- When the shield or the F.FIELD takes a hit from an enemy running into it, that enemy takes 1 damage, which kills weak ones outright. This also applies to Bamdas touching the F.FIELD. Claw arms and the boss aren't affected.
- The laser beam takes its thickness from `laser.png`, now 2 px instead of 4, and stays centred on the gun that fires it. It still stretches out to its full length as it flies.
- Enemy bullets use the new `ebullet.png` at its real size (4×4), with a matching hit box, and spin in quarter turns (12 per second, `EBULLET_SPIN` in `GameScreen`).
- Tiled: the objects tileset has two bio walker tiles, blue and red (`drop` on). The 26 walkers in `stage1.tmx` alternate red and blue from left to right: 13 red ones that drop a capsule and 13 blue ones that don't. Their positions and other properties are unchanged.
- A dropped power capsule stays where it was dropped and only moves with the scrolling. It no longer drifts left or bobs up and down.
- Walkers on screen move relative to the screen, like the ships: the scrolling is added on top of their own speed, so they keep up with it. Their speeds are halved: 60 px/s while chasing, 30 px/s while leaving (`Enemy.CHASE_SPEED`, `Enemy.LEAVE_SPEED`). A walker that waits under a ship stays in the same spot on screen. A walker that stops to aim stands on its spot of ground and scrolls along with the terrain.
- Stage 1 walkers use the new `bio walker red.png` / `bio walker blue.png`. The 5 frames play forward, then backward. They have no left / right facing, are flipped upside down on the ceiling and still tilt at steps. Red ones carry a power capsule (`drop` on) and blue ones don't. Stages 2 and 3 keep `walker.png`.
- Towers can be flipped vertically in Tiled. Any of the six tower tiles works on the floor or on the ceiling. The picture, the hit area, the green tip and the lava direction all turn over with it.
- A laser beam now does 7 damage to each thing it hits (was 1), so it makes up for its slower fire against tough targets. The value is `Bullet.LASER_DAMAGE`.
- The pulsing asteroid is now called **Bamda** everywhere: the Tiled class is `bamda`, the sprite is `sprites/bamda.png` and the map icon is `icons/bamda.png`. The game still reads objects marked `asteroid`.
- Bamdas play the same hit sound as other enemies when a shot doesn't destroy them.
- Enemies, turrets, Bamdas, lava pits and towers only fire or spit lava while they are on screen.
- Shot-away bricks in the brick maze now fade in over 1 s instead of popping back. While a brick fades in it is harmless and can be shot away again. It turns solid once it is fully visible and no ship is inside it.
- Power capsules use the new `sprites/PowerCapsule.png` (2 frames, separated by an empty column). Their size and hit box come from the picture, and they drop from the centre of the last enemy of the wave. The old `capsule.png` is no longer used.
- The ship can now touch every edge of the screen.
- Power meter: a highlighted slot is pale yellow, as in the original. A slot turns red once its power-up can't be taken any more (maxed out or already active), and has its own look when it is both highlighted and taken. The pieces are in your expanded `hud.png`.
- Walkers stop for 1 s to aim before every shot.
- Shots take their size from `shot.png`, so the new, smaller shot is drawn and hits at its real size.
- When the highlighted power-up is occupied, its name box in the HUD is dimmed.
- Missiles take their size from `missile.png`. One speed, `Bullet.MISSILE_SPEED`, sets how fast they fly (a 45° dive), crawl and climb.
- `Bullet.SHOT_SPEED` sets the speed of both the normal shot and DOUBLE's angled shot.
- Missiles explode when they hit an asteroid or a falling rock.

### Added
- Rugal (`sprites/rugal.png`): comes in from the right edge and flies left across the screen at 60 px/s, independent of the scrolling. It steers up or down toward the nearest ship, and its picture tilts down or up while it dives or climbs at 45° or more. It only shoots once it has flown past a ship, firing back at it. It takes 1 hit and is worth 100 points. Stage 1's opening has two solo Rugals and two trios. In Tiled the class is `rugal`, triggered like the flying waves; a trio is just three of them placed a little apart.
- Amoeba (`sprites/amoeba.png`, 3 frames looped at 6 fps): the stage 1 towers now spit these out instead of lava. A fully grown tower sprays 4 at a time, fanned out left to right, every 2.5–4 s. The spray slows down, then the amoebas float around lazily in place, drifting through rock as well, and block the way. They don't shoot, die in 1 hit and are worth 50 points each. At most 16 float around at once. The black around each frame of the picture is drawn see-through; black inside the outline stays.
- F.FIELD power-up for ship type 4, replacing its SHIELD (meter: SPEED, MISSILE, F.FIELD, LASER, OPTION, RIPPLE). It puts an 8×16 force field just in front of the ship's nose, animated from `sprites/ffield.png` (four 8×16 frames with their own transparency, looped at 12 fps). It blocks enemy shots and lava; each blocked shot pops with a small burst and makes it flicker. With 3 hits or fewer left it turns red (`sprites/ffield_red.png`, `Player.FFIELD_LOW`). After 10 shots it's gone and the slot can be taken again. Hits are detected on the field's own 8×16 picture: shots, enemies, Bamdas, claw arms and the boss that touch the field wear it down, while anything that reaches the ship itself (from behind, above or below) hits the ship. Every hit it takes (a blocked shot or an enemy) makes the ship invincible for 0.6 s, the same as the bubble shield. Terrain still destroys the ship during that time. Shots that land during those 0.6 s don't wear it down. Shots that reach the ship from behind, above or below get past it.
- Missing Link (`sprites/missing_link.png`, 7-frame barrel roll looped at 12 fps): a single enemy that comes in from the right, drifts left at 40 px/s while bobbing up and down 50 px (most of the screen height), and shoots at the nearest ship every 2–3 s. It takes 1 hit and is worth 100 points. Six of them fly through the opening of stage 1, between the Celtic Frost waves, bobbing around the middle of the screen. In Tiled the class is `link`; its y sets the middle of the bob.
- Celtic Frost (`sprites/celtic frost.png`): a new flying enemy that comes in groups of six and flies a "Z" across the screen, independent of the scrolling. It comes in from the right, low on the screen, flies left, then diagonally up and back to the right, then left along the top and out the left edge. Upper groups fly the mirror image. They fly at 135 px/s, about 15 px apart. The Z spans 15–85% of the screen height and 20–80% of its width, so it never touches the top or bottom edge. Shoot down the whole group for a capsule. Stage 1 opens with 12 waves, alternating low and upper. A new wave comes in about every 2.3 s, so two or three are on screen at once. Each wave flies its own lines: the height of the Z's bars comes from where the wave is placed in Tiled, so no two waves leave on the same line. The open space before the terrain was lengthened by 416 px to fit them, so the terrain starts after about 30 s. Everything else in the stage moved right with the terrain. They replace the six fans and two rushers that were there. In Tiled the class is `frost`.
- Bio turrets in stage 1: the 12 turrets from the original map (5 on the floor, 7 under the ceiling), using your `bio turret red.png` / `bio turret blue.png`. A turret points its barrel diagonally left, straight up, or diagonally right (the left frame mirrored), whichever is closest to the nearest ship, and shoots from the barrel tip. Ceiling turrets are flipped. Red turrets drop a power capsule when destroyed. The seven old placeholder turrets were removed: six of them sat where the towers are now. The baked-in turret pixels are cut out of the stage art, and the collision under them is cleared. In Tiled, the turret now has a blue tile and a red tile (`drop` on).
- Debug stage select: press S on the title screen to pick the stage (or OFF) and the checkpoint to start from. The choice shows in the top-right corner of the title screen. Dying rewinds to that checkpoint, and clearing the stage carries on to the next one. Picking a boss test (B) turns it off, and the other way round.
- Green-tipped towers in stage 1 (six, placed as in the original map). Each starts hidden in the rock, grows out of the floor or ceiling once it scrolls on screen, and spits lava like the stage 2 pits when fully grown (ceiling towers drip it down). Shoot the green tip (12 HP, 500 points) to destroy it; the rest of the column just stops shots. Touching it costs a ship. Sprites are `sprites/tower0–5.png`; the tower objects are in the hazards layer of `stage1.tmx`.
- Claw arms in stage 1 (three pairs, placed as in the original map). Each is a chain of claw, blue ball and two orange balls. When a ship comes within 120 px the claw slowly reaches for it, and the chain follows. Shoot the blue ball (12 HP, 500 points) to destroy the arm; the other parts just stop shots. Touching any part costs a ship.
- The brain boss fights in two phases. Above half HP it behaves as before. Below half HP it grows its two arms (4 blue balls and a claw each, indestructible) from the spots where they are attached in the original map, then chases the ships once both arms are fully out.
- Pulsing asteroids (`sprites/asteroid.png`, 3 frames) replace the old asteroids. They take 25 hits, shoot aimed bullets like walkers, and their hit box pulses with the picture.
- Debug: I on the title screen makes the ships invincible.
- Pause menu (P / Esc, or Start on the gamepad): CONTINUE or TITLE SCREEN. Use up and down to choose and fire to confirm. Start or the back button continues the game.
- Back button on menus: X / Backspace on the keyboard, B on the gamepad.
  - On the title screen it goes one step back: from READY to choosing a ship again, and from choosing to not joined.
  - It also cancels a mid-game ship choice.

### Fixed
- Enemies, asteroids and the boss could be hit before they were on screen. Only the on-screen part of a shot can hit anything now, and player shots disappear as soon as they leave the screen.
- Missiles and walkers treated the top (and bottom) edge of the map as ground where there was no terrain.

## 2026-10-06

### Added
- Brain boss eye (`sprites/boss eye.png`), drawn where a small green marker sits in `boss0.png`. It opens when the boss arrives, and after each hit it closes, stays shut for 250 ms, then opens again. Only the eye takes damage, and only while it isn't fully closed.
- Boss test mode: press B on the title screen to pick a boss (1, 2, 3, off). The game starts at that boss and returns to the title once it's beaten.
- 7th power-up slot "!" on every ship: one extra ship (up to 9).

### Changed
- MSX resolution: the screen is now 256×192, with a 256×168 playfield and the original 24 px score / power meter strip along the bottom, using graphics taken from the original HUD.
- The levels were laid out for a 272 px tall view, so the camera now drifts up and down to follow the ships.
- New pixel fonts for all text; the in-game counters use the original MSX digits.
- Title screen and ship select redone for the smaller screen.
- The window opens at 1024×768 (4× scale).
- The boss sits closer to the right edge.
- Stage 1 rebuilt at its true 1:1 scale. The old version was enlarged 1.62× to fit the 272 px screen.
  - New terrain art (4 strips instead of 7), teeth, asteroids and brick pattern.
  - The brick maze now uses the original 8 px blocks, exactly on the collision grid.
  - All enemies, asteroids, checkpoints and the camera path were scaled into place.
  - Scroll speed is 28 px/s, so the pace through the level stays the same.
  - The camera no longer drifts in stage 1 (new map property `lane`).
  - The hand-erased filler areas are kept by the build script.

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
