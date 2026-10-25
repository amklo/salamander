# Changelog

All notable changes to this Salamander remake. Newest first.

## 2026-10-25

### Added
- **Lavinia (stage 3), MSX style, from `reference material/recordings/stage lavinia.avi`**:
  - Scroll speed 20 px/s (`speedMsx`), with the drop down the shaft at 0.8× and the lower corridor at 1.1× (`speedsMsx` `1,0.8,1.1`), as measured. The Laser Guard room holds for 52.5 s before the guards retreat (new `hold` property `timeMsx`; REMAKE keeps 60 s).
  - The opening has the six **Battleaxe** rows of the recording (low, high, low, high, low, high; objects 81–86, the first leaves a capsule) and five **Possessed** row groups near the terrain start (objects 87–91). There are no Celtic Frost, Missing Links or Rugals on the MSX: the REMAKE opening (objects 53–78) and the three Celtic Frost waves in the base (41, 43, 45) are now `style=remake`.
  - **Gun Ho hatches**: on the MSX the Gun Hos come out of four of the blue floor domes (x 1440, 1728, 2680 and 4448): one each time the dome's left edge reaches x 208, 144, 80 and 16 on the screen (every 64 px of scrolling). Each hops out (up 48 px, 24 px left, back down to the floor, 16 px left), then lines up with the nearest ship, fires and flies off to the left at 80 px/s. New `floordome` property `gunhosMsx` (4 on those domes); the four standalone Gun Ho objects (3, 4, 11, 25) are now `style=remake`. The other four domes spit amoebas as before.
- Possessed row groups work on horizontal stages too (in from the top edge, as in the Lavinia recording), with new properties `place`, `down`, `side`, `gap` and `colours`.
- **Lavinia wall lasers** (both styles): the eight `laser` objects are now beams between the pillars, on all the time; they hurt a ship and let shots through. Each beam's emitter (part of the wall art, at the pillar end; new property `emitter`, `left` for the three on the right-hand side of the first pillar) can be shot (6 hits) to put the beam out, as beams go out in the recording.
- **Kierke walkers** (both styles), from the MSX recording (77–126 s): the red / cyan hoppers are the stage 1 bio walkers (Albert's `bio walker red/blue.png`). 23 placed where they come in (objects 91–113), in five waves at ~77, 84, 92, 100 and 107 s: pairs leap in from the left and right screen edges at mid height, one dropping onto the floor and one "falling" up onto the ceiling, drifting inward at 40 px/s (gravity 88 px/s², the floor ones glide level for 0.2–0.8 s first). Those that land far from the ship (more than 112 px) walk toward it to about mid-screen; then each keeps walking away from the ship (16–40 px/s relative to the screen, 0.9–3 s) and hopping back toward it, 32–56 px high, or up to the ship's height when it is higher (a hop that reaches the ceiling grabs it, and the walker carries on up there). About one hop in six goes the other way, as seen. They don't shoot, and walking off a screen edge they are gone. Red ones leave a capsule. New `walker` property `leap` (`left` / `right`); a leaper flipped vertically in Tiled heads for the ceiling (for leapers the flip wins over the tile's `ceiling=false`) (`Enemy.updateLeaper`, `LEAP_*`).

### Changed
- **Eioneus bases** (Albert): the base around the middle is background. The destroyed frame of `eioneus_base.png` is painted into `stage4_t0–3.png` at all 28 bases (lined up with the reference map), and the enemy is now only the middle, `sprites/eioneus_base_core.png` (4 frames of 20×21, cut from x 12–31, y 12–32 of each frame): its hit box is the middle only, and the mines still leave from its centre. `stage4.tmx`: base 22 moved onto its picture (86, 1092, 44×48); base 25 removed (it doubled base 97).
- **Fangs**, stage 1, both styles: the 3rd, 5th and 6th (x 3448, 3632, 3680) reach across the passage, hold, and carry on until they have sunk right into the opposite rock; they wait there 0.5 s, then come back out of it and slide all the way home (the right way up), and rest as usual. New `tooth` properties `sink` and `sinkWait` (`Hazard.updateThroughFang`). The 5th and 6th used `switch` (they lived in the opposite rock from then on, upside down); `switch` still works for any tooth that has it.
- **Eioneus bases** (the seeds) use Albert's `sprites/eioneus_base.png` (closed, opening, open, destroyed) instead of `seed.png` / `seed_open.png`: a base opens for 0.4 s and stays open, letting its bursts of 4 flying mines out of its middle. Bases are drawn under the other enemies (they are part of the ground). It is ground now: ships fly over it, but shots hit it; destroyed, it shows its last frame and stays.
- Flying mines show their three take-off frames a third of the take-off each (the first one was only on screen for a moment).
- Laser battery beams are the Laser Guards' beam (the red / blue `enemy laser` pattern, 5 px).
- **Whiplash** (stage 2): as it stops it fires one expanding laser like the Omen's, toward the far edge of the screen.

### Fixed
- Gremlins on the left wall were drawn upside down; they are now the right-wall picture mirrored.

## 2026-10-24

### Added
- **Seeds and flying mines** (Eioneus, both styles), with `sprites/seed.png` / `seed_open.png` and Albert's `sprites/flying_mines.png` / `flying_mines_red.png`: the 29 seeds (one was missing from the map and was added, object 97) let out bursts of 4 flying mines once they are a little way onto the screen (0.5 s apart, every 4 s). As in the recording, a mine takes off just above the seed (frames 0–2, 1 s, while it can't hit or be hit), loops round to the right, down and up to the left, then homes in on the nearest ship flickering between frames 3 and 4. About one in six is red and leaves a power capsule (`SEED_*`, `MINE_*` in `Enemy`).
- **Eioneus, from the recording (2026-10-24, second round)**:
  - **Celtic Frost** (MSX style): the blue "jellyfish" columns of the opening are the vertical-stage Celtic Frost. Seven columns placed where they come in (183 at 7.2 s, 79 at 10.2, 47 at 11.4, 215 + 183 at 14.6, 79 at 16.8, 47 at 19.0; objects 72–78), the same sequence as the stage 2 opening. They fly the existing vertical path.
  - **Laser batteries** (both styles), with Albert's `sprites/laser_battery_small.png` / `laser_battery_big.png`: the 8 emitters of the map are now enemies (objects 79–86; their pictures and rock cells were taken out of the map art / collision). Small ones on the walls shoot a beam sideways to the rock across; a big one on the floor shoots it up to the big one hanging above it. The beam is on all the time, flickers red / blue, hurts a ship and lets shots through; it goes out when a battery is shot down (6 hits). New Tiled classes `laserbattery`, `laserbattery_big` (tiles 59, 60).
  - **Gremlins** (both styles), with Albert's `sprites/gremlin_yellow.png` / `gremlin_red.png`: they come up from the bottom edge climbing a side wall (48 px/s), walk 2 s, stop 3 s to aim and fire an aimed shot, and walk on, as in the recording. Nine placed where they come in (objects 88–96). New Tiled class `gremlin` (tile 61, properties `red`, `right`).
  - **Asteroid storm** (both styles): from camera top ~1576 for 27 s (~140–167 s in the recording), with big rocks that split (object 87).
- **Eioneus (stage 4), MSX style, from the recording**: scroll speed 18 (`speedMsx`). The opening has four Possessed "row" groups (new property `row`; objects 68–71): six members each come in diagonally from the top one after another, stop for a moment in a row near the top edge shooting, then fly on diagonally up and out, as in the recording. The REMAKE fly-by Legacy and Celtic Frost waves (objects 53–61) are now `style=remake`.
- **Blue domes and green flyers** (Eioneus, both styles), with `sprites/bluedome.png` / `bluedome_open.png` and `sprites/green_flyer.png`: the 13 wall domes let out bursts of 4 green flyers once they are a little way onto the screen (0.5 s apart, a burst every 5.3 s), and each flyer flies the double loop measured in the recording, mirrored for the right wall (`BLUE_DOME_*`, `GREEN_*` in `Enemy`).
- **Gamma** (new enemy, both styles), with Albert's `sprites/gamma.png` (4 frames, shown 0, 2, 3, 1 every 0.2 s as in the recording): the blue balls of the Kierke cave. Each comes in at the right edge and drifts left through the world at 16 px/s, bouncing slowly on the floor (or on the ceiling) about 35 px high every ~2.3 s; it rides up over rock and doesn't shoot (`GAMMA_*` in `Enemy`). New Tiled class `gamma` (objects.tsx tile 58, properties `ceiling`, `drop`). Nine placed in stage 5 where they come in in the recording (126.8–161.4 s; objects 82–90, two of them on the ceiling).
- **Whiplash** (new enemy, both styles), with Albert's `sprites/whiplash.png` (15×16, drawn as it is). As in the stage 2 recording, it slides in from the top edge (or up from the bottom edge, `fromBottom`) at 28 px/s until 42 px inside the screen, stops for 0.6 s, then darts off diagonally (40 px/s on in the same direction, 24 px/s sideways toward the nearest ship) and leaves the screen; it doesn't shoot (`WHIP_*` in `Enemy`). New Tiled class `whiplash` (objects.tsx tile 56). Two placed in stage 2 where they show up in the recording, at ~21 s: one rising from the bottom at x 104, one coming down from the top at x 168 (objects 120, 121).
- **Battleaxe** (new enemy), with Albert's `sprites/battleaxe.png` (16×16, drawn as it is): the rows of six in the Kierke opening, as measured in the recording. A rigid row (16 px apart) comes in from the right edge at 40 px/s, goes straight up (a high row: down) at 80 px/s when its front reaches x 136 on the screen, left again to x 16, straight back down (up) and out to the left; it doesn't shoot (`BATTLEAXE_*` in `Enemy`). New Tiled class `battleaxe` (objects.tsx tile 57, properties `count`, `high`, `drop`). Seven rows placed in MSX style where they come in in the recording (10.7, 18.1, 23.5, 29.3, 35.1, 38.9, 44.5 s; objects 75–81); the first one leaves a capsule. The REMAKE opening (Rugals, fans, red Rugal; objects 60–65) is now `style=remake`.
- **Kierke chains** (both styles), with Albert's `sprites/chain_balls.png`: as in the recording, a chain starts when its first ball comes to x 216 on the screen and grows a ball every 0.25 s (4, 6, then 8 px), each one 8 px further right and 8 px up or down toward the nearest ship's height; rock in the way makes it go straight on. The balls stay put, can't be destroyed, stop shots and lasers, and hurt the ship (`CHAIN_*` in `Enemy`). The ten chain objects of stage 5 are moved to the measured first balls, with `count` 7 (from the middle) or 10 (from the top); the old `diag` property is gone.
- **Aberrant** (new enemy, both styles), with Albert's `sprites/aberrant.png` (alive / withered frames): the plants of Kierke (the old `spider` objects, now Tiled class `aberrant`, moved onto the picture's 16×16). A chain grows from the aberrant under its first ball, and only while it is alive; the balls it already grew stay when it is shot. Shot down, it withers to the blue frame and stays: shots pass through it, but it still hurts a ship that touches it.
- **Nests** (both styles), with `sprites/nest.png`: the 13 nests of Kierke stand where they are (6 hits, 500 points). New property `spawns` says what comes out: `ug` (the default) or `bug`.
- **Ug** (new enemy, both styles), with Albert's `sprites/ug.png` (3 frames cycled): as in the recording, a nest lets 6 Ugs out of its mouth, 0.2 s apart, once it comes to x 232 on the screen. Each flies diagonally out to a lane 40 px from the ceiling or floor, left along it at 90 px/s to x 64, straight toward the middle to its row (ceiling Ugs above, floor Ugs below), then back right at 70 px/s and out; they don't shoot (`UG_*` in `Enemy`).
- **Creeping Death** (new enemy, both styles), with Albert's `sprites/creeping death.png` (4 frames, white on see-through; any black pixels left are made see-through too, as the MSX sprite is one colour): the small bugs of the floor nest at x 1242 (`spawns=creeping death`). As in the recording, the nest lets them out in bursts of 4 (0.15 s apart, every 2.5 s) while it is on the screen; each rises out of the mouth at 50 px/s for 1.3 s, then homes in on the nearest ship at 45 px/s. They play the frames back and forth (0.2 s each) and flicker between cyan and light green, or (every other one) red and dark green, every 0.1 s (`CREEP_*` in `Enemy`).

### Changed
- Kierke chains now grow toward the nearest ship sideways as well as up or down (diagonally when both), instead of always to the right; rock in the way makes a ball step only sideways or only up / down, and a chain stops once it reaches the ship.
- Tetran's tentacles turn clockwise (the other way round).
- The Tiled icon of the Possessed is `possessed_red.png`.
- The Possessed now use Albert's own pictures, `sprites/possessed_red.png` and `sprites/possessed_cyan.png`, drawn as they are, instead of tinting `possessed.png` in the code.

### Fixed
- The Whiplash never came on screen: its starting place just off the edge was set again every frame, so it stayed there. It now slides in as it should.
- The Possessed were drawn with the bio turret picture: enemies without a regular frame fell through to the bio turret drawing before the Possessed's own drawing. They are now drawn first (tinted body, red eye).

## 2026-10-23

### Added
- **Possessed** (new enemy, both styles), with Albert's `sprites/possessed.png`: the white body is tinted red or cyan in the game and the red eye is drawn on top untinted. As in the stage 2 recording, a group comes in from the top edge one after another (0.3 s apart), each flies to its spot in a small cluster near the top of the screen, hovers there for 2.2 s shooting aimed shots at the nearest ship, then flies back up and out. New Tiled class `possessed` (objects.tsx tile 55, properties `count`, `reds`, `drop`). Nine groups placed in stage 2 where they show up in the recording: four in the opening (12.8, 20.9, 24.7, 27.9 s) and five around 85–95 s (objects 111–119). In MSX style they replace the opening fly-by Legacy waves that stood in for them (objects 47, 49, 65, 66, 75 are now `style=remake`).
- **Legacy as on the MSX** (both styles), with Albert's new `sprites/legacy.png` (7 frames of 16×16: straight on in the middle, leaning left / right to the sides). A Legacy now hangs in the world where it is placed and scrolls with the scenery. It drifts or darts sideways toward the nearest ship (a new speed every 0.6–1.6 s, up to 45 px/s), sometimes dives at a ship below it (60 px/s for 1.4 s), and doesn't shoot. It leans with its sideways speed and flickers magenta, magenta, blue, magenta, magenta, red (2 frames each, each Legacy out of step), as measured in the stage 2 recording. 24 placed in stage 2 where they show up in the recording (objects 87–110, camera top ~1650–1150, around the eye rocks), in both styles.
- `legacy` property `flyby`: the old Legacy wave that flies like a fan and shoots once past a ship. The existing Legacy objects of stages 2, 4 and 6 have it on, so they fly as before. (The loading code now reads `sprites/legacy.png`, lower case.)

### Changed
- **Celtic Frost on vertical stages** (both styles): instead of the old Z they fly a "V" like in the stage 2 recording: down their column, at 48 px below the top 45° down toward the middle for 48 px, then 45° back up and out through the top (`Enemy.FROST_V_TURN`, `FROST_V_DIP`). Same 80 px/s per axis and 16 px spacing as on stage 1; a frost that has left through the top is removed.
- **Stage 2, MSX style** (from `reference material/recordings/salamander stage 2 playthrough.avi`): scrolls at 21 px/s (`speedMsx`; REMAKE stays 26). The opening has the 8 Celtic Frost columns of the recording, timed from the start (objects 78–85, `style=msx`). The REMAKE opening waves (48, 61, 63, 64, 67, 72–74) are now `style=remake`. The asteroid storm starts a little later (camera top at y 3460) and lasts 27 s as on the MSX (object 86, `style=msx`; the REMAKE storm 77 is unchanged).
- **Celtic Frost** path rebuilt from waves 2–4 measured frame by frame (the earlier numbers used the blue band at the top of the sprite instead of its centre): in on the entry line, left to 64% of the width, 45° back toward the middle for 64 px, left to 28%, 45° back again until the sprite is flush with the far edge, then right along the edge and out. Nothing is cut off by the screen edge any more. MSX waves now enter with their centre 23.5 px above the bottom / 32.5 px below the top.
- **Fangs**: the 5th and 6th (x 3632, 3680) no longer pull back. They cross the passage, hold, and carry on right into the opposite rock; next time they come out of that side (upside down), and so on, back and forth (new `tooth` property `switch`; `Hazard.updateSwitchFang`). Their `period` is 4.6 s. Both styles.
- **Towers**, MSX style: sprays come in bursts of three, 0.85 s apart (was two), still 4 amoebas each; one red per burst (`TOWER_BURSTS`, `TOWER_BURST_GAP`). The MSX sprite limit isn't copied (the remake's cap of 16 amoebas stays).
- **Bamdas** don't drift any more (in either style): the drift seen in the recording was the MSX's 8 px character scrolling.
- **Claw arms**, both styles: they start tucked into the rock with only the claw showing and grow out once their anchor is on screen (REMAKE along its resting direction, MSX straight out). Their balls stop the laser, including the blue weak ball, where the laser now hits it (it used to go through it).
- **Claw arms**, MSX style: after growing out, the chain stays straight and swings to point at the nearest ship (at most 65° from straight out, 150°/s). The blue weak ball moves along the chain with the lean: next to the rock when it points straight out, one ball further from 22°, two from 44° (`ARM_MSX_*`, `Hazard.updateStageArm`). Replaces yesterday's "lean 45° and stay".
- **Towers**, MSX style: 4 amoebas per spray as in REMAKE, but every spray is followed by a second one 0.8 s later (`TOWER_SECOND_WAVE`); one red amoeba per pair. Replaces yesterday's 10 per spray.
- **Walkers**, MSX style: they jump about half a screen high (48–84 px) toward the ship and come back down onto their own floor / ceiling (a ceiling stops them on the way up); 1.5–3.5 s between jumps (`WALKER_JUMP_*`). Replaces yesterday's leap to the opposite surface.
- **Amoebas**, both styles: all change colour together: yellow, cream, white, green, 3 frames each (`AMOEBA_COLOURS`).
- **Celtic Frost** path re-measured from the recording: turns at x 160, 224, 80 and 152; middle line 93 px above the bottom for a low wave; it leaves along the very top (bottom) edge. Distance now counts per axis (a diagonal step moves as far across as along, as on the MSX) at 80 px/s, 16 px apart. MSX style: the waves enter on the MSX lines (29 px above the bottom / below the top).

- **Fangs**, both styles, measured in the recording: they slide out and back in linearly at 85 px/s (no easing), hold fully out (0.2–0.7 s), then rest with a bit of their tip showing (6–24 px) instead of hiding completely. Five of the six reach right across to the opposite rock; the fourth (x 3597) stops 8 px short. Each of stage 1's six has its own measured `delay`, `period` (3.0–4.5 s), `holdOut`, `rest` and `short` (new `tooth` properties; `Hazard.fangExtension`).

### Added
- Seven red walkers that run in from the left in the blue tentacle passage, as in the recording (objects 207–213, `behind`), in both styles.

## 2026-10-22

### Changed
- Styles renamed: ORIGINAL → **MSX**, ARRANGED → **REMAKE** (object property `style` = `msx` / `remake`, map property `speedMsx`, path property `speedsMsx`).
- Celtic Frost flies at the MSX speed: 96 px/s (was 135), 16 px apart (`FROST_GAP` 0.167 s). Both styles.
- MSX style, stage 1 (`Level.msx()`), enemy behaviour as seen in the recording; REMAKE is unchanged:
  - Claw arms start tucked into the rock with only the claw showing; once on screen they grow straight out (`ARM_GROW_SPEED`), then lean over to 45° toward the side the nearest ship is on and stay that way (`Hazard.updateArmMsx`).
  - Bamdas drift slowly through the cave (and through rock), 12–24 px/s, up to 45° either side of straight left (`BAMDA_DRIFT_*`).
  - Towers throw out sprays of 10 amoebas (fanned 20° apart) every 4.5–5.5 s instead of 4 every 2.5–4 s (`TOWER_SPIT_*_MSX`).
  - Walkers leap across to the opposite surface while chasing: when the nearest ship is closer to the other side and the gap is 24–150 px, they fly straight across (120 px/s, drifting toward the ship) and land on the floor / ceiling there; 1.5–3.5 s between leaps (`WALKER_JUMP_*`).
- Celtic Frost flies the MSX zigzag (from the stage 1 recording): in from the right on its entry line, left, diagonally back toward the middle line, left along it, diagonally back again to the mirrored line, then right and off the right edge (it used to leave on the left). It is removed as soon as it is past the right edge again (an escaped wave still leaves no capsule). Vertical stages keep the old Z (`Enemy.FROST_PATH_V`).

### Added
- Title screen: **style** REMAKE / MSX (first called ARRANGED / ORIGINAL) (up / down picks difficulty or style, left / right changes it; remembered between runs; shown next to the difficulty on the ship select screen). ORIGINAL plays stage 1 as measured from `reference material/recordings/salamander stage 1 playthrough.avi`: scrolling at 19.2 px/s on every stretch (map property `speedOriginal`, path property `speedsOriginal` = `1,1,1,1,1`) and the MSX opening: 8 Celtic Frost waves alternating low / high, 2 Missing Links, 2 Rugal trios and 2 single Rugals, placed so that they arrive the same number of seconds after the start as in the recording. ARRANGED is unchanged (28.4 px/s, the remake's 12 waves).
- Object property `style` (`original` / `arranged`): the object only appears in that style. `Level.originalStyle`, `SalamanderGame.setOriginal`.

## 2026-10-20

### Changed
- Stage 2 domes (on the side walls) no longer spit amoebas: they send out waves of **7 green flyers** (new enemy `DOME_FLYER`, `sprites/green_flyer.png`: 4 frames of 16×16 taken from `reference material/Salamander_0068.png`, played 0 1 2 3 2 1 …). The flyers leave the dome's mouth one after the other (0.4 s apart) and fly to the bottom of a circle (radius 40 px) above the dome, 48 px out from the wall (kept on screen); they go round it twice — counter-clockwise from a left-wall dome, clockwise from a right-wall one — evenly spread so the seven form a ring, then fly straight on away from the dome and off the screen. They fly in screen coordinates (they keep their place while the screen scrolls) and through rock. Each one starts shooting aimed shots (every 2.2–3.4 s) once it has finished its first circle. 1 hit, 100 points; shooting down all 7 of a wave leaves a power capsule. The dome sends its next wave 2.5–4 s after the last flyer of the previous one is gone, and only while it is on screen. Stage 3's floor / ceiling domes still spit amoebas. Tuning: `DOME_FLYER_*` in `Enemy`, `DOME_WAVE_*` in `Hazard`.

### Added
- Stage 2: **asteroid storm**. Where the scenery starts (the top of the screen at world y 528, about 14 s in) rocks rain down the screen for 15 s: small rocks (`sprites/rock_small.png`, 4 frames of 15×15, tumbling) and about one big rock (`sprites/rock_big.png`, 30×27) for every five small ones. They fall from the top edge toward the bottom of the screen (55–75 px/s; most almost straight down, 45 % slant 12–40° to a side); big rocks are slower (32 px/s) and come nearly straight down. The rocks move relative to the screen and bounce off the scenery (collision, bricks, eye-rock rubble, eye rocks, domes): off a wall they bounce back sideways (keeping 80 % of their speed against it); off a ledge below they hop up a little (45 %) and are pushed off toward the more open side (at least 35 px/s), and they are always pulled back to their falling speed (260 px/s²), so they keep heading for the bottom edge. Bounces are worked out against the scenery as it scrolls; a rock the scrolling pushes into rock rides along with it or, if that doesn't free it, passes through until it is clear (also used for rocks that can't spawn in the open). Tuning: `BOUNCE_*`, `PULL`, `KICK` in `Meteor`. They are not enemies: no points, and shots go straight through the small ones. Only a guard destroys them: the SHIELD breaks any rock that touches it (and wears down by one, unless the ship is still invincible from its last hit), and when a rock reaches the ship the F.FIELD takes the hit and the rock breaks. Without a guard a rock that reaches the ship destroys it and flies on; a blinking (invincible) ship lets them pass. Shooting a big rock splits it into three small ones that fly 3× as fast as the big one did: one straight down, the others 50° to the left and right (a laser splits it once and keeps going). New class `Meteor` (all tuning constants there).
- New map object `meteors` (`objects.tsx` tile 54, icon `sprites/rock_big.png`, property `time` = 15): the storm starts when the front edge of the screen reaches it (the top edge reaching the bottom of the icon on a vertical stage) and lasts `time` seconds. Restarting from a later checkpoint skips storms the screen already passed. Stage 2 has one at x 113, y 3600 in Tiled; `tools/stage2_build.py` writes it too.

## 2026-10-30

### Added
- Stage 3's last room (x 4632..4856, a dead end) is now a fight: the scrolling stops there for 60 s (new map object `hold`, a point on the camera path with property `time`). Three **Laser Guards** (new enemy, the old "dart": `sprites/Laser Guard.png`) sit on the ceiling, the floor and the right-hand wall, where the darts are in the reference picture. They can't be destroyed (shots just stop on them). The ceiling and floor ones slide left / right, the wall one up / down; After every volley one guard, picked at random, chases the nearest ship and stays lined up with it (140 px/s); sometimes (30 %) two: the right-hand wall one plus the ceiling or the floor one, never ceiling and floor together. The others slide to random spots (90 px/s). The three fire together: a volley every 1.6–3 s, or as soon as one of them is lined up (`GameScreen.updateGuardVolley`). Their laser (the flashing red / blue `enemy laser` pattern) behaves like the ships' laser: it grows out of the gun for 0.6 s and then lets go, and it stays in line with the gun. The guards stop for 0.3 s before every volley (`GUARD_WIND_UP`) and stands still while its laser is on screen (and only fires again once it is gone). Rock stops its front. After the 60 s the guards slide back into their walls, the right-hand wall dissolves pixel by pixel (1.5 s) and the scrolling goes on to the boss. Dying in the room starts it over from the last checkpoint with the wall back. Tuning: `GUARD_*` in `Enemy`, `WALL_DEMAT_TIME` in `GameScreen`.
- New map object `dematwall` (a rectangle with property `image`): a wall whose picture is not part of the stage art and that dissolves when the hold is over (its collision cells go with it). Stage 3's is `maps/stage3_endwall.png`, cut out of `stage3_t4.png`.

### Changed
- `sprites/dart.png` is now `sprites/Laser Guard.png`; Tiled classes `laserguard` (tile 38; flip Y for the floor) and `laserguard_wall` (tile 39, icon `maps/icons/laserguard_wall.png`). `tools/stages_build.py` / `stagebuild.py` write the new names, the `hold` / `dematwall` objects and cut the wall out of the art.

## 2026-10-29

### Added
- Stage 3: the stage 2 domes, at the eight spots of the blue domes in `reference material/salamander_stage_3_lavinia.png`: on the floor at x 1440, 1728, 1824, 2680, 3472 and 4448 (lower corridor), on the ceiling at x 2008 and 2560. They are the same enemy as in stage 2 (closed until on screen, then open and spit sprays of amoebas, one red; 12 HP, 500 points, exact-pixel hits), drawn with `sprites/dome.png` turned a quarter so they face up (floor) or down (ceiling) and spit into the corridor. Tiled: the old "pod" tile (objects.tsx tile 36) is now `floordome`, with a turned dome icon (`maps/icons/floordome.png`); flip it (Y) to hang it from the ceiling. `Hazard.domeSide`, `PixelMask.rotatedCCW / rotatedCW`.

### Changed
- Omen keeps firing: a burst of 3 lasers, then the next burst 1.5 s after the last laser of the previous one, for as long as it is on screen (`Enemy.OMEN_REPEAT`).
- Stage 3 opening: the open space before the terrain (x < 1048) now has the same enemies in the same places as the open space before stage 1's terrain (12 Celtic Frost waves alternating low / high, 6 Missing Links, Rugals single and in threes), shifted 26 px left because stage 3's camera path starts 26 px earlier. The old opening waves there (Rugals, fans, a red Rugal, a Celtic Frost) are gone. `tools/stages_build.py` has the same waves.

## 2026-10-28

### Added
- Stage 3 (Lavinia): new enemy **Gun Ho** (the robot that was the "jumper" object), at the four spots where it stands in `reference material/salamander_stage_3_lavinia.png` (x 1463, 1703, 2695 in the upper lane and 4495 in the lower corridor). It scrolls in with the terrain; once it is well on screen it keeps its place on the screen and moves up / down (not into rock) to line its gun up with the nearest ship. As soon as it is lined up it fires one shot straight at the ship (horizontal; picture `sprites/Gun Ho bullet.png`, its size is the hit box; stopped by rock and the SHIELD), waits a moment and flies off the left edge. It turns round if a ship is behind it. If it can't line up within 6 s it leaves without firing. 3 HP, 300 points. Tuning: `GUNHO_*` constants in `Enemy` (climb speed, beam speed / length, leave speed, give-up time).

- Stage 3 (Lavinia): new enemy **Omen** (the orange-topped turret that was the "hatch" object), at the six spots where it sits in `reference material/salamander_stage_3_lavinia.png`: on the floor at x 1248, 3040 and 3464, on the ceiling at x 2912, 3152 and 3472 (drawn upside down). It never moves and has no animation. 0.3 s after it comes on screen it fires one burst of 3 lasers (0.15 s apart) and then stays quiet. The lasers fly straight up (down from a ceiling Omen, 120 px/s) and widen sideways as they go, like the Ripple on the vertical stages: the 8 px pattern of `sprites/enemy laser red.png` / `enemy laser blue.png` is repeated sideways from the middle, flashing between red and blue 15 times a second. They grow to 96 px wide at most; an end stops at rock and at the screen edge, and only the middle of the laser is stopped by rock. Tuning: `OMEN_LASER_*` in `Enemy`. 3 HP, 200 points. Tuning: `OMEN_*` constants in `Enemy`.

### Changed
- `sprites/hatch.png` is now `sprites/Omen.png`; the Tiled object class is `omen` (`objects.tsx` tile 35, `tools/stages_build.py`). Old maps with `hatch` objects still load as Omen.
- `sprites/jumper.png` is now `sprites/Gun Ho.png`; the Tiled object class is `gunho` (`objects.tsx` tile 37, `tools/stages_build.py`). Old maps with `jumper` objects still load as Gun Ho.

## 2026-10-27

### Added
- New missile power-up HOMING, in slot 2 of ship TYPE 3's meter (replacing MISSILE). It flies like 2-WAY's missiles, but only one per gun is on screen at a time (`MAX_HOMING` = 1 in `GameScreen`), fired toward the nearer screen edge like MISSILE (down / up; left / right on vertical stages). The ship and each option have their own. As soon as an enemy is on screen it leaves its path and flies at the nearest one (enemies, Bamdas / falling rocks, domes / rock eyes, the green tips of towers, the weak blue balls of claw arms (stage and boss arms), the boss's weak spot), keeping that target until it is gone. It never crawls: it explodes the moment it touches anything, map collision included. `Bullet.HOMING_SPEED` (150 px/s) and `Bullet.HOMING_TURN` (360 deg/s) tune the chase.

### Fixed
- Missiles no longer treat the edge of the map as rock. Where the screen edge was also the map edge (the top and bottom on most stages, the sides of vertical stages, the end of a stage), missiles crawled along it or vanished there. Now they just fly off the screen unless the .tmx has collision there. Missiles use the new `Level.hitsMap()` (outside the map is open space); ships, shots and enemies still use `Level.hits()` as before. Floor / ceiling lookups (`floorTop` / `ceilBottom`) also treat the columns left and right of the map as open space.

## 2026-10-26

### Changed
- Tetran (stage 2 boss): the tentacles turn 50% faster (`TETRAN_SPIN` 0.675 rad/s, was 0.45).
- Tetran no longer sways from side to side: once it has come down, it flies round in a circle (counter-clockwise, 32 px radius, about one lap every 10 s), starting from the spot where it stops. `TETRAN_CIRCLE_R` / `TETRAN_CIRCLE_SPEED` in `Boss` set the size and speed. Other vertical-stage bosses still sway as before.

## 2026-10-25

### Changed
- The ship starts slower: 75 px/s at speed 0, 60% of the old 125 px/s (`updatePlayer` in `GameScreen`). Each SPEED power-up still adds 34 px/s, so every speed level is 50 px/s slower than before (top speed SPEED#5: 245 px/s, was 295).

## 2026-10-24

### Changed
- The stage music now starts fading out before the boss arrives, at a new map object `bossmusic`: a point on the camera path, like a checkpoint. Every stage has one, about 4 s of scrolling before the boss arrives (for the bosses that sit in the map: before they come on screen). Stage 1's is at the start of the brick maze, where the old map property `bossMusicAt` had it. Move it in Tiled to change the timing.

## 2026-10-23

### Changed
- The stage music now fades out over 3 s (was 2 s), followed by 1 s of silence before the boss music starts (`BOSS_MUSIC_FADE`, `BOSS_MUSIC_GAP` in `GameScreen`).

## 2026-10-22

### Changed
- The stage music fades out over 2 s before the boss music starts (also in stage 1's brick maze), done by the music player: the song files are untouched. `BOSS_MUSIC_FADE` in `GameScreen` sets the time. Pausing the game also pauses the fade.
- The boss music is decoded in the background when a stage starts, so starting it doesn't make the game stall for a moment.

## 2026-10-21

### Changed
- All stages and the boss fights play the original songs again (`02. Power of Anger.mp3` … `12. Last Exit.mp3`, `08. Poison of Snake.mp3`). The `(loop)` copies aren't needed any more: every map sets where its song's loop ends (`musicLoopEnd` / `bossMusicLoopEnd`, now also on stage 1: 59.881 s), so the fade-out in the original files is never heard.

## 2026-10-20

### Fixed
- The music of stages 2–7 and the boss music faded out before looping: their `(loop).mp3` files are full length (with the fade), and the loop only ended at the end of the file. New map properties `musicLoopEnd` / `bossMusicLoopEnd` say where the loop ends, before the fade: Fly High 56.02 s (the file ends sooner), Combat 47.20 s, Starfield 55.14 s, Burn the Wind 59.48 s, Destroy Them All 70.55 s, Last Exit 67.18 s, Poison of Snake 67.06 s. The rest of the file is never played.

## 2026-10-19

### Changed
- Stage 7 plays `12. Last Exit` (was `09. Prophet Fire`).
- Every stage song and the boss music now loop seamlessly, like stage 1: each map uses a `(loop).mp3` copy of its song that stops before the original fades out, with its loop length in `musicLoop` (boss music: new map property `bossMusicLoop`). Loop lengths found in the songs: Fly High 25.601 s, Combat 22.405 s, Starfield 25.599 s, Burn the Wind 27.198 s, Destroy Them All 33.600 s, Last Exit 32.001 s, Poison of Snake 35.207 s. The `(loop).wav` versions to convert to MP3 are in `Claude outputs/loops/`.

## 2026-10-18

### Changed
- Stage 1's music no longer fades out after a minute: it loops seamlessly. `02. Power of Anger.mp3` repeats its main part every 25.599 s and fades out from 62 s. Stage 1 now plays `bgm/02. Power of Anger (loop).mp3`, a copy that stops before the fade, and jumps back 25.599 s from just before its end.
- New map property `musicLoop`: loop length in seconds. Such a song is decoded into memory once and played through Java Sound: sample-exact loop, no gap, and the last 30 ms before the jump are blended into the loop start. Works for MP3, OGG and WAV; everything else plays as before.

## 2026-10-17

### Changed
- The stage pictures moved from `assets/sprites/` to `assets/maps/`, next to the maps: the art strips `stage1_t*.png` … `stage7_t*.png`, the brick art `stage1_bricks.png` … `stage7_bricks.png`, the terrain tileset `tiles.png` and `brick_pattern.png`. `assets/sprites/` now only holds sprites. The maps (image layers, `brickArt`), `terrain.tsx`, the game and the build tools point at the new place.

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
