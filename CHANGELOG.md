# Changelog

All notable changes to this Salamander remake. Newest first.

## 2026-10-16

### Added
- **Stages 3–7**, built from the original maps cut out of `salamander maps.png`:
  - Stage 3 (OPERATION THREE) from **Lavinia**: space, then a green base. Halfway it drops down a shaft into a lower corridor, and the boss, a warship, waits in space at the end. Music `04. Combat.mp3`.
  - Stage 4 (OPERATION FOUR) from **Eioneus**: vertical, red rock with blue bubble bricks. Music `05. Starfield.mp3`.
  - Stage 5 (OPERATION FIVE) from **Kierke**: the blue organic cave full of web threads; the boss is the machine wall at the end. Music `06. Burn the Wind.mp3`.
  - Stage 6 (OPERATION SIX) from **Op 4**: vertical, rock and then a shaft of blue pipes; the boss is the four gun units with the sphere. Music `07. Destroy Them All.mp3`.
  - Stage 7 (OPERATION X) from **Op X**: the fortress with pink brick walls to shoot through; the boss sits on the far wall. Music `09. Prophet Fire.mp3`.
  - Boss fights keep `08. Poison of Snake.mp3`. Clearing stage 7 wins the game. Boss test and stage select offer all seven stages.
- Every enemy, hazard and boss of the new maps is cut out as a sprite and placed in the map as an object: hatches, domes, jumping robots, darts and lasers (Lavinia); seeds, wall domes, beam emitters, a mine and the boss's gun pods (Eioneus); spiders, nests and ball chains (Kierke); crabs, blue clusters, mines and beam barriers (Op 4); flame vents and the core gate (Op X). **They don't do anything yet**: their behaviour, and each boss's own attack pattern, will be added stage by stage. Until then the stages have waves of the existing enemies (Rugals, fans, rushers, Celtic Frost, Legacy), and the bosses use a shared pattern.
- Map object `boss` with `fixed`: the boss sits at its spot in the map (stages 5–7) and wakes up as soon as it comes on screen.
- `tools/stages_build.py` (with `tools/stagebuild.py`) rebuilds stages 3–7 from the reference maps.

### Changed
- `reference material/salamander_stage_3_eioneus.png` and `salamander_stage_4.png`: the spots where the original's text boxes covered the map are patched with rock and pipe art.
- `sprites/boss2.png` is now Lavinia's warship (the old stage 3 boss picture is gone).

## 2026-10-15

### Removed
- The old stage 3. The game now has two stages: clearing stage 2 wins. Boss test and stage select only offer stages 1 and 2.

### Added
- Reference maps for the remaining original stages, cut out of `salamander maps.png` into `reference material/`: `salamander_stage_3_lavinia.png`, `salamander_stage_3_kierke.png`, `salamander_stage_3_eioneus.png`, `salamander_stage_4.png` and `salamander_stage_x.png`.

### Changed
- New music, MP3 files from `assets/bgm/`:
  - Stage 1: `02. Power of Anger.mp3`. Stage 2: `03. Fly High.mp3`.
  - Boss fights on every stage, and stage 1 from the brick maze on: `08. Poison of Snake.mp3`. In stage 1 it starts when the middle of the screen reaches the maze (new map property `bossMusicAt`, x 3984) and plays on into the boss fight.
  - Game over screen: `14. Crystal Forever.mp3`, played once.
- The music player (`Bgm`) plays MP3, OGG and WAV through libGDX as well as MIDI. Volume, pause and the 1 / 2 keys work for both.

## 2026-10-14

### Added
- Difficulty: **NORMAL** or **EASY**, picked on the title screen with left / right before anyone joins. It is shown on the ship select screen too and remembered between runs. On EASY a destroyed ship keeps all its power-ups except the options, which still turn into green capsules. On NORMAL it starts over without power-ups as before.
- A green capsule that leaves the screen comes back once at the opposite edge, for one more chance to catch it: the right edge on side-scrolling stages, the top on vertical ones. The second time it leaves, it is gone.

### Changed
- On side-scrolling stages a missile flying down to the floor is drawn nose 45° down, and one flying up to the ceiling nose 45° up. Before, they were drawn level until they landed. Crawling is unchanged. The choice between up and down for MISSILE is made at the middle of the playfield, the area above the HUD.
- Stage 1: the 21 fan and rusher waves are replaced by Rugal trios at the same places. Each trio is three Rugals 18 px apart around the wave's old height, and the middle one is red. A red Rugal uses `sprites/rugal_red.png` instead of a red tint, and leaves a power capsule on its own, so each trio still gives one capsule like the wave it replaces. Tiled: new red Rugal tile (`drop` on).
- Stage 2: the 7 fan waves are now Legacy waves (4 Legacies each), at the same places. `tools/stage2_build.py` lists them as Legacies too, matching the map as laid out in Tiled.
- MISSILE (not 2-WAY) now goes toward the nearer edge of the screen, chosen for each gun (the ship and every option) at the moment it fires. On side-scrolling stages it goes down from the lower half of the screen and up (crawling along the ceiling, like 2-WAY's upper missile) from the upper half. On vertical stages it goes left from the left half and right from the right half. The on-screen limit of 2 per gun counts both directions together.
- Vertical stages: a missile with no rock on its side flies off the side of the screen and is gone. The screen edges no longer count as walls to crawl along.
- EASY starts with 5 ships instead of 3, and so does continuing on EASY (also for a player who joins mid-game).
- Every ship type now offers SHIELD first when picking the guard, instead of F.FIELD on types 1–3. F.FIELD is still one left / right press away.
- Bamdas have 12 HP instead of 25.
- The laser beam does 3 damage per target instead of 7 (`Bullet.LASER_DAMAGE`). It still hits each target only once per beam.
- The ripple laser does 3 damage per hit instead of 1 (`Bullet.RIPPLE_DAMAGE`).
- This applies to everything these weapons hit: enemies, towers, claw arms, domes, eye rocks and the bosses.

## 2026-10-13

### Added
- The mouse pointer hides itself when it hasn't moved for 3 seconds (in a window and full screen) and comes back as soon as the mouse moves. On the desktop the game asks GLFW to hide it over the game window, which also works in macOS full screen. The first attempt, which only hid it in full screen, didn't work there.

- Dying no longer rewinds the stage, also when playing alone. The ship re-enters on the spot after a moment, as it already did in co-op, and the stage carries on. It's game over when nobody has ships left. Checkpoints are now only used by stage select.
- A destroyed ship comes back without power-ups: its speed, missiles, weapon, options and guard are reset. The power meter's highlight stays where it was. Its options are left behind as green power capsules (`sprites/powercapsule_green.png`), each where an option was flying. Catching one gives back one option at once, without touching the power meter. With 4 options already, it counts as a normal capsule. Either player can catch them.

### Changed
- Player 2 flies its own red ship: `player_red.png` on side-scrolling stages and `player_red_vertical.png` on vertical ones, instead of the blue ship tinted orange. Its options are still tinted orange.
- Shots use the new `shot.png` (6×3). The size and hit box come from the picture as before. The shot is now centred on whole pixels on the gun, rows 7–9 of the 16 px ship.
- (Tried and taken back the same day: an Energy currency for the power meter, and switching main weapons with L1. The power meter and the weapons work as before.)

## 2026-10-11

### Changed
- Tetran's tentacles are drawn under its body instead of over it.
- Tetran's core has 4 blocks (`sprites/core_block.png`) in the bars below the red core. Every time the boss loses 20% of its health, the lowest block left disappears with a small burst. At 80% health 3 are left, and at 20% none. `boss1_core.png` no longer has bars of its own, so a missing block shows the dark inside of the core. The blocks only show the damage: the core is still the weak spot as before.

## 2026-10-10

### Added
- New enemy **Legacy** (`sprites/Legacy.png`: 4 frames of 16×16). It plays the frames forward, then backward, then forward again, and so on. The game paints the white of the picture green (#73ff73).
  - It flies exactly like a fan. On stage 2 it comes in from the top one after another and weaves down the screen.
  - It only shoots once it has flown past a ship: below the ship on a vertical stage, to the left of it on a side-scrolling stage. Then it fires an aimed shot about every 1.6 s while it is on screen.
  - A wave of 4 appears in the starting section of stage 2, right after the opening waves. Shooting all 4 drops a capsule.
- Tiled: new `legacy` object (green icon), placed like a `fan` wave, with `count` (4) and `drop`.

### Changed
- Tetran's core is now its only weak spot: `sprites/boss1_core.png` (24×50), drawn over the hole cut out of the body for it. Shots and lasers that hit the body only stop there and show a spark; only hits on the core's drawn pixels do damage. The game finds where the core goes by looking for the hole in `boss1.png`, so the body can be redrawn or resized freely. Any boss can get a core this way (`bossN_core.png`).
- A destroyed eye rock leaves rubble behind (`sprites/rubbles.png`), centred where the rock was. The rubble works like terrain: it can't be destroyed, ships crash into it, shots and enemy bullets stop on it, and missiles crawl along it. Only its drawn pixels are solid. When the stage restarts from a checkpoint the rubble is gone and the eye rocks are back.
- The rocks with an eye in stage 2 are now the new hand-drawn sprite `sprites/rock_eye.png` (32×32): the whole rock, eye included. They take 3 hits, and then the whole rock is destroyed (300 points). The eye still shoots at the ships like a bamda. The rock is solid to the ships, and ships and shots hit only its drawn pixels.
  - The 13 eye rocks were taken out of the stage art (`stage2_t0–t2.png`) and the collision layer, so nothing is left where a destroyed one stood. The eye objects in `stage2.tmx` are now 32×32 and cover the whole rock.
  - The old eye sprite `rock eye.png` is no longer used. `tools/stage2_build.py` no longer makes it, and leaves the eye rocks out of the art too.
- `tools/stage2_build.py`: its `WAVES` list now matches the waves as laid out in Tiled, plus the Legacy wave, so a rebuild keeps them.

## 2026-10-09

### Added
- Flying waves in stage 2, in the open stretches without terrain: 3 at the start (fans, Celtic Frost, fans) and 6 in the middle section (Celtic Frost and fans taking turns). They are listed in `WAVES` in `tools/stage2_build.py`, and placed in `stage2.tmx` as normal `fan` and `frost` objects, so they can be moved in Tiled.
- On vertical stages these enemies fly a turned version of their path:
  - **Fans** come in from the top one after another and weave down the screen around the object's column.
  - **Celtic Frost** flies its "Z" turned a quarter: down from the top along its column, diagonally back up to the other side, then down and out at the bottom. A flipped object enters on the right side.
- **Stage 2, OPERATION TWO, rebuilt from the original map** (`reference material/salamander_stage_2.png`) as the game's first vertical stage. The screen scrolls upward at 26 px/s from the bottom of the map to Tetran at the top, with 3 checkpoints. It replaces the old placeholder stage 2.
  - The orange rocks are solid terrain. The blue bubble rocks are destructible bricks, like stage 1's, but they don't grow back.
  - 26 wall turrets, 17 red and 9 green, on the rock walls. They turn their barrel toward the ship: straight out, or diagonally up or down. Red ones leave a power capsule. 4 hits each.
  - 7 domes on the side walls. A dome is closed until it has been on screen for 1 s, then it opens for good and spits a spray of 4 amoebas sideways every 2.5–4 s, one of them red. 12 hits, 500 points.
  - 13 rocks with a green eye. The eye shoots at the ships like a bamda. 15 hits destroy it (300 points) and the rock becomes plain rock.
  - **Tetran**, the boss: it comes down from the top and sways from side to side. Its 4 tentacles (3 rings and a bent tip each, laid out as in the original) turn slowly around it like a revolving door. The rings can't be hurt: they block shots and hurt ships that touch them. Each tip fires aimed shots in turn. Shoot the body: 100 HP.
- Vertical stages (map property `vertical`). The ship uses the new `sprites/player_blue_vertical.png` and points up, and every weapon fires upward:
  - Shots, DOUBLE, LASER and RIPPLE are turned a quarter. The laser stays in line with its gun; the ripple widens sideways.
  - MISSILE flies diagonally up and to the right. When it touches a rock wall it crawls up along it, goes around the corner where the wall ends, and steps away from rock in front of it. 2-WAY sends one up each side. A missile that runs into rock bursts there and blasts the bricks it touches.
  - The SHIELD sits across the top of the nose.
  - Spawns, enemies and hazards go by the top and bottom of the screen instead of the right and left.
- New sprites cut from the map: `wall turret red.png`, `wall turret green.png` (sideways and diagonal frames), `rock eye.png`, `boss1.png` (Tetran's body) and `tetran ring.png`. New `dome.png` with a closed and an open frame. The open frame was drawn for this game: the middle of the shell is gone and a red mouth shows.
- Tiled: new objects `wallturret` (green and red tiles), `dome` and `eye`. Flip a turret or dome horizontally to put it on the right-hand wall. New map properties `vertical`, `brickArt` (a picture the bricks are cut from) and `brickRegrow` (0 = bricks never grow back).
- `tools/stage2_build.py` rebuilds all stage 2 art and `stage2.tmx` from the original map.

### Changed
- The ship on vertical stages banks while it moves sideways. `player_blue_vertical.png` is now a sheet of 3 frames: banking left, level, banking right. The level frame shows while it isn't moving sideways, and moving straight up or down keeps it level. Each frame is centred on the hit box.
- Domes use the reworked `sprites/dome.png`: two frames of 18×32, closed and open. The game takes the dome's size from the picture, and its hit area grows with it. In `stage2.tmx` the domes are 18 px wide, and the ones on the right wall have moved 6 px left so they still sit against the rock. `tools/stage2_build.py` no longer overwrites `dome.png`; it makes the Tiled icon from its closed frame.
- Domes collide by their exact shape: the ship and shots only hit the pixels that are drawn, using the frame that is showing (closed or open), mirrored on the right wall. The see-through part of the open dome can be flown and shot through.
- Stars in stage 2 are white instead of orange.
- Enemy bullets fired from inside rock, by the eye rocks, get out of the rock before the rock can stop them.

## 2026-10-07

### Changed
- New power meters for the ship types. Slot 6, "?", holds the guard picked after the ship type: SHIELD or F.FIELD.

  | Type | Meter |
  |---|---|
  | 1 | SPEED, MISSILE, DOUBLE, LASER, OPTION, ?, ! |
  | 2 | SPEED, 2-WAY, DOUBLE, RIPPLE, OPTION, ?, ! |
  | 3 | SPEED, MISSILE, DOUBLE, RIPPLE, OPTION, ?, ! |
  | 4 | SPEED, 2-WAY, DOUBLE, LASER, OPTION, ?, ! |
- Renamed the two guards. **F.FIELD** (force field) is now the bubble around the ship: 5 hits, on ship types 1–3, picture `sprites/ffield.png`. **SHIELD** is now the guard in front of the nose: 10 hits, on ship type 4, pictures `sprites/shield.png` and `sprites/shield_red.png`. The code uses the same names.
- F.FIELD and SHIELD slots open up again as soon as the guard has taken a hit. Taking one refills it to full.
- SPEED is always open and its box shows the level it gives: SPEED#1 … SPEED#5, then SPEED#0, which goes back to the starting speed.
- One amoeba in each spray of 4 is red (#ff2727, the white of the picture tinted) and leaves a power capsule when shot, like the other red enemies. Which one is random.
- The six teeth in stage 1 all use the new `sprites/fang full.png` (45×160). Each one comes out of the ceiling or floor until its tip touches the opposite surface, then pulls back in, with the same timing as before. The game measures the passage at each tooth from the collision map, so a tooth always reaches right across, whatever the height there. `fang0–5.png` are no longer used.
- The ripple laser uses the new `sprites/ripple.png` (8×16) as its smallest form, and only 1 can be on screen per gun, like the laser. It flies at 240 px/s, half its old 480. As it flies, its top and bottom ends stretch away at 110 px/s up to the full playfield height. Fired from the left edge with nothing in the way, it is about 80% of the screen height when it reaches the right edge. An end stops where terrain blocks it, and pulls back when the ring flies into a narrower passage. The 6-pixel tips of the picture stay as drawn and the straight sides stretch, so the pixels stay crisp. As before, only terrain at its core stops it.
- The brain boss's arms can be destroyed. Shoot any of an arm's blue balls (not the claw): an arm takes 60 hits, 5 times a stage claw arm, and bursts for 500 points. 1 s later it grows back out of the boss. Phase 3 (chasing the ships) starts as before once both arms are fully out the first time.
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
- Guard select: after picking a ship type, each player picks a guard with left / right: SHIELD (front guard, 10 hits) or F.FIELD (bubble, 5 hits). It goes into the meter slot where that ship type had its guard. The ship type's own guard is offered first, and X / pad B steps back. Players joining mid-game pick theirs in the meter area the same way.
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
