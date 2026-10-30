# Changelog

All notable changes to this Salamander remake. Newest first.

## 2026-10-30

### Changed (Tetran, from Albert)
- **No gap between Tetran's body and its tentacles**: each tentacle has an extra ring at its root (5 rings: 28 px out from the body centre instead of 36).
- **Tetran's tentacles meet in the middle**: each one also runs inward from its root ring to the body's centre (4 more rings, evenly spaced), drawn under the body, so they show through its gaps and the core's hole once its bars are shot away. These inner rings don't stop shots or hurt the ships (the core sits on top of them). `Boss.INNER_RINGS`, `innerX` / `innerY`.
- **At half health the tentacles grow and curl**: they grow to twice their length (10 rings, one more every 0.12 s), and each curls in (the way its tip is bent) and out again on its own rhythm (3.2 s, out of step with the others), fading in over 1 s. Each ring bends a little more than the one before (up to 0.24 rad inward, 0.12 rad outward), so the tentacle rolls up and unrolls. The tips keep firing from their new ends. `Boss.RINGS` / `RINGS_MAX`, `TETRAN_LONG_HP`, `TETRAN_GROW_T`, `CURL_IN`, `CURL_OUT`, `CURL_PERIOD`.

### Added (from Albert): Lavinia's darkness
- **The lights go out** as in the MSX recording: when the camera's left edge reaches x 1816 (just before the small blocks) the stage goes dark at once: no background, stars or terrain are drawn (the rock is still there and still solid); only the ships, enemies, domes, shots and the spark show.
- **The `item_spark` turns them back on** (`sprites/item_spark.png`, near the ceiling at x 2304, where the old spark marker was): a ship that touches it gets 300 points and the lights come straight back. If it is missed, the lights come back when the camera's left edge reaches the end of the dark zone (x 2700, my choice; the MSX recording doesn't show what happens then). After a death the darkness and the spark come back unless the restart point is past the spark.
- Map: new object `dark_zone` (rectangle) and new `objects.tsx` tile `item_spark`; tuning `SPARK_SIZE` and `lightsOut()` / `resetDarkness()` in `GameScreen`.

### Code (from Albert): one wave system for every spawner
- **New `Wave` class**: a wave of enemies let out of a spawner, a "base" (stage 1's green-tipped towers, the stage 2 / Lavinia domes, the Eioneus / Op 4 blue domes, Kierke's nests, the Eioneus / Op 4 ground bases). It holds the kind (AMOEBA, GREEN_FLYER, CREEPING_DEATH, FLYING_MINE, UG), the timing (count, volley, gap, rest between waves or one wave only, rest counted from the last one gone) and the capsule rule (RED_ONE: one red member; WHOLE: shoot down the whole wave), and keeps the wave's members. Every spawner asks its Wave when the next member is due (`tick`) and only decides when it may let something out (on screen, open, grown).
- **One maker for every member: `GameScreen.spawnWaveMember`**: the same code for each kind, whatever spawner it comes from; the only differences are the orientation (out of a side wall or a floor / ceiling) and a stage's own flight path (stage 2's circling flyers, Op 4's homing ones, Kierke's mine path). Replaces `spawnAmoeba`, `spawnDomeCreep`, `spawnDomeFlyer` and the per-spawner code in `releaseNestBugs`, `Hazard.updateTower` / `updateDome` / `updateCreepDome` / `updateFlyerDome`; the old per-spawner counters (`nestLeft`, `domeLeft`, `seedLeft`, `creepRedIdx`, `domeWave`, `flyGroup`, ...) are gone. Lavinia's Gun Ho hatches stay as they were (one Gun Ho at a time, not a wave).
- Same behaviour as before, with one exception: an MSX-style tower's burst of three sprays now has its one red amoeba anywhere in the burst (before: always in the first spray).
- **Stage 2's dome flyers fly the Eioneus path** (Albert): they are green flyers now (out of the mouth where the Eioneus domes let theirs out), no longer circling above the dome twice; stage 2 keeps its waves (DOME_FLYER_COUNT, DOME_FLYER_GAP apart, the next wave once the last one is gone) and its capsule (shoot down the whole wave). `Enemy.Type.DOME_FLYER` is no longer used.
- **Stage 1's towers spray as on the MSX in both styles** (REMAKE too): bursts of three sprays, 0.85 s apart.

### Changed (from Albert)
- **Lavinia: no more Celtic Frost**: the last three waves (REMAKE extras at x 1594, 2994 and 4094) are removed from `stage3.tmx` (the map before is in `_to_delete/`).
- **Gun Ho fires a burst of 3 shots**, 0.2 s apart, once it is lined up (it keeps frame 2 until the last one), then pauses and leaves as before (`Enemy.GUNHO_BURST`, `GUNHO_BURST_GAP`, state `GUNHO_FIRING`).
- **Gamma: more time between the two shots of a burst**: 0.5 s (was 0.15 s).
- **Artillery animates**: `artillery_green.png` / `artillery_red.png` are 2 frames of 16x16 side by side; it cycles through them every 0.4 s (`GameScreen.ART_FRAME`).
- **Gun Ho: two frames** (`Gun Ho.png`, two frames side by side, as soon as the picture is wider than tall): frame 2 while it lines up with a ship to fire, frame 1 the rest of the time (coming in, hopping out of its hatch, after the shot, leaving). With the old one-frame picture nothing changes.
- **Gamma shoots**: now and then a burst of 2 shots at the nearest ship, 0.15 s apart; the first burst 1.5–3.5 s after it came, then one every 2.5–4.5 s (`Enemy.GAMMA_BURST`, `GAMMA_*`; 80 px/s).
- **Artillery fires fixed spreads, not aimed shots**: by turns a 3-way (straight out from its wall / floor / ceiling toward the opposite side, and 45° to either side) and a 4-way (no middle shot: 22.5° and 67.5° to either side), every 1.3 s as before, whatever the ships do (`Enemy.ART_SPREAD_3`, `ART_SPREAD_4`).
- **Every wave of flying mines has at least one red one** (it leaves a capsule): the Eioneus bases now pick one red one per wave (others are still red about one in six times, as in the recording); Kierke's web nests' mines were never red, now one per wave is; Op 4's bases already had one per wave.
- **Kierke's Ug nests: shoot down the whole wave of Ugs for a capsule** (a carrier group; the Ugs keep their yellow colours).
- **Smooth movement instead of 8 px steps**: the Eioneus gun pods come down at a steady 20 px/s, and Kierke's boss wall moves up / down and rams at the same average speeds as its MSX steps.
- **Kierke's boss shoots in bursts**: 7 bullets (counting the shots really fired by its pods), then 2 s without shooting (`Boss.K_BURST`, `K_BURST_REST`); it used to fire every 0.28 s without a pause.
- **Eioneus and Op 4: the scrolling stops 8 px earlier**, at camera top 168 / 384, where the stage art begins: it stopped 8 px above it and showed an empty black line at the top (path polylines end at −3880 / −4120). Stage 2 ends in open space and is unchanged.
- **Eioneus: the gun pods come down as soon as the scrolling stops** (`POD_WAIT` 0, was 6 s).
- **A big storm rock that hits scenery splits**, as when it is shot (its pieces may meet the rock at once and burst too).
- **Every red Creeping Death leaves a capsule**: Kierke's nests let out every other one red, but only in colour; now one of each burst (at random) is red and carries a capsule, as out of Lavinia's and Op 4's domes (already so).
- **No enemy bullet passes scenery**: besides the rock, bricks and rubble (as before), stage 1's towers, Eioneus's gun pods and Lavinia's blocks and Heathen pillars stop them (`GameScreen.sceneryStops`). A shot fired from inside rock (a rock eye) still gets out of that rock, but once it has been in the open any rock stops it (`Bullet.leftRock`; it could cross a second wall within its 0.3 s grace). The Laser Guards' beams and Omen's laser keep their own rules.

## 2026-10-29

### Changed (stage 1, from Albert)
- **Stage 1 banner subtitle**: "OPERATION ONE / -TOP FLIGHT SPEED TO LATIS-" (map property `subtitle` in `stage1.tmx`).

### Changed (Legacy, from Albert)
- **Every Legacy drops a power capsule** when shot down: the hovering MSX ones (whatever their `drop` property says) and each member of a fly-by wave (before: only a whole red wave, once all were shot). Each Legacy is now its own one-member carrier group (`Level`, case `legacy`). They keep their own colours (no red carrier tint).
- **Animation: all 7 frames in a loop** (0 1 2 … 6 0 1 …, `Enemy.LEGACY_FPS` 10/s), whichever way they're heading. Before: the hovering ones leaned with their sideways speed and the fly-by ones went forward and back. `Enemy.legacyFrame()` is no longer used.
- Not compiled or play-tested yet.

### Added (stage 2 blue asteroids, from the recording)
- **Blue rock clumps on stage 2** (both styles; `sprites/blue_asteroid.png`, the Op 4 one), objects 122–126. Five of them, between camera top ~1490 and ~1380 (≈119.6–129.4 s in the recording). As on Op 4 they can't be destroyed (shots stop on them).
  - Two cut across diagonally: object 122 enters at x ≈ 15 going down-right (`drift` 50, `sink` 26 px/s); object 123 enters at x ≈ 192 going down-left (`drift` −36, `sink` 14).
  - Three sink straight down side by side at x 39 / 119 / 191 (`sink` 1 / 5 / 14).
  - Each one waits until it is on the screen, then moves through the world. Simulated paths fit the tracked rocks within ~8 px (the MSX moves in 8 px steps).
- New Tiled properties for `blue_asteroid`: `sink` (px/s; leave it out for Op 4's random 5–30) and `drift` (px/s sideways, + = right). Code: `Level.Spawn.sink / drift`, `Enemy.blueAsteroidSink / blueAsteroidDrift`. The blue asteroid picture now loads on stage 2 as well as Op 4.
- Not compiled or play-tested yet.

## 2026-10-29

### Changed (from Albert)
- **Asteroid storm rocks stop at walls**: a storm rock that runs into rock, bricks or other solid scenery is destroyed (a small blast); it no longer flies through. A rock never starts over solid rock at the top edge (another place is picked, or none this time).
- **Storm rocks fall straight down everywhere except stage 2** (Eioneus, Op 4); only stage 2's rocks slant to the sides.
- **Every boss body is solid**: a ship that touches it crashes, and every shot and laser stops on it (only its weak spots take damage). Op 4's brain sphere and gun units were the exceptions (the ships flew over them and shots passed everywhere but the weak spots); now they are solid too (`Boss.ghost` off). The gun units are not solid while they come together pixel by pixel. The other bosses already were.
- **Everything that lets enemies out is three times as tough**: domes (stage 2, Lavinia's Creeping Death domes and Gun Ho hatches, the amoeba domes) 36 (`Hazard.DOME_HP`), stage 1's green-tipped towers 36 (`Hazard.TOWER_HP`), the Eioneus / Op 4 blue domes 36, Kierke nests 36, the Eioneus / Op 4 bases 36 (were all 12). The Eioneus gun pods stay as they were (10 per bar).
- **Eioneus side volcanoes spit twice as much**: a burst every 0.9–1.5 s (was 1.8–3 s); the bursts themselves are unchanged (3–5 rocks, 0.12 s apart). `Meteor.SIDE_EVERY_MIN / MAX`.
- **Every wave out of a dome can give a capsule, on every stage**: the green-flyer waves of the Eioneus blue domes and Op 4's shaft domes are now carrier groups, as stage 2's flyer waves already were: shoot down the whole wave for a capsule (they keep their own colours). `Enemy.domeWave`. Already so: stage 2's flyer domes (whole wave), the amoeba domes and Lavinia's / Op 4's Creeping Death domes (one red one per wave).
- **Lavinia: both Missing Links leave a power capsule** (`drop` on objects 108 and 109 in `stage3.tmx`; they are tinted as carriers).
- **Destroyed enemies that stay are background** (Eioneus and Op 4 bases, Kierke's withered roots): they keep their destroyed frame, but a HOMING missile no longer goes after them (it dropped a target only once it was gone; now also once it is destroyed or has no health left), and they are drawn as ground (`GameScreen.drawGround`), before power capsules, hazards and every enemy still in play, so nothing hides under them.

## 2026-10-28

### Changed (walkers and Gremlins, from Albert)
- **Stage 1 walkers now behave like the Kierke leapers** (both styles, all of them, the runners from behind too; `Enemy.leapGround`, `startLeapOnGround`). Each stands where it was placed (scrolling in with the rock) until it is on screen, then carries on as a leaper does after landing: approaches the ship, walks away, hops toward it (and can grab the other surface), walks off the screen edges. All of it relative to the screen. Like the leapers they don't shoot. The old walk / chase / MSX hop code stays for stages that still use it.
- **Gremlins**:
  - They climb at their speeds relative to the screen (48 px/s; before: through the world). Standing to aim they stay on their spot of the wall.
  - **They aim for 1 s and fire at the end of it** (`GREMLIN_AIM` 3 → 1, `GREMLIN_FIRE` 0.4 → 1).
  - **Every other shot they first get ahead of the ship**: climb at `GREMLIN_RUN` 80 px/s until their middle is `GREMLIN_AHEAD` 40 px above the nearest ship's, then aim. They stop early 24 px from the top edge or after 4 s. The first shot is the usual one.
  - Not compiled or play-tested yet.

### Changed (scrolling, from Albert: behave like a classic on-rails shooter)
- **Free-flying things now move relative to the screen**, so they look equally fast whichever way they fly (before, they flew at a fixed speed through the world, and the scrolling made shots from behind look slower and shots from the front faster). `GameScreen.scrollVY` is new (the path's up / down speed, like `scrollVX` sideways; the camera's follow drift is left out, as for the ships).
  - **All enemy shots**: aimed shots, Gun Ho beams, moai rings, lasers reflected by Lavinia blocks (`Bullet.ridesScreen`; GameScreen adds the frame's scroll to them). Not: lava, Omen lasers, Laser Guard beams. The MSX Missing Links' own fix for their shots is removed (it would count twice).
  - **The ships' shots, lasers and ripples** (an attached laser's front rides along, its tail stays at the gun; a laser pinned at a point of impact doesn't move). Missiles still crawl through the world.
  - **Homing flying mines** (Eioneus / Op 4 bases, Kierke web nests) once they home in. Their curl out of the base / nest stays in the world.
  - **Fly-by Legacy waves** and the **old-style Missing Links** (stages 1 and 3). Their speeds (60 / 40 px/s) are now screen speeds, so they look slower than before by the scroll speed.
  - Unchanged: amoebas (to check against the stage 1 recording), and enemies that use only `scrollVX` (Rugal, Heathen, Rage…) don't yet follow up / down scrolling.
  - Checked: the three files parse; not compiled or play-tested yet.

### Added (Op 4, from Albert)
- **Background lights** (`sprites/stage_6_back_ground_lights.png`, 3 frames of 7×7, sprite name `back_ground_lights`): every place in the stage 6 art that shows one of its frames (found by exact match: 365 lights, all on empty background) now has a `back_ground_lights` object in `stage6.tmx` (own object layer "back_ground_lights"; new tile 72 in `objects.tsx`, icon `maps/icons/back_ground_lights.png`). Each cycles its frames endlessly, 0.1 s per frame, starting from the frame the art showed there (object property `frame`, 0–2; 0 if left out). Decoration only: no hits, no collision. Drawn right after the stage art, so under every other sprite. The lights painted into the art are taken out at load time (only pixels that match a frame, so a light moved in Tiled never cuts into other art); the PNGs are unchanged. `Level.bgLights`, `GameScreen.unbakeBgLights` / `drawBgLights`.
- **Lavinia background lights** (`sprites/stage_3_back_ground_lights.png`, 3 frames of 8×8), done the same way: 103 lights found in the stage 3 art (all on empty background), placed as `back_ground_lights` objects in `stage3.tmx` (own object layer; tile 73 in `objects.tsx`, icon `maps/icons/back_ground_lights_3.png`), 0.5 s per frame. The lights code is now per stage: it uses `sprites/stage_<N>_back_ground_lights.png` (frame size = picture height) and the map property `backGroundLightsFrame` (seconds per frame, default 0.1; stage 6 = 0.1, stage 3 = 0.5).

### Changed (Kierke boss, from Albert)
- **The machine wall is rebuilt from Albert's new pictures** (replaces `boss4.png`, which is no longer used). `boss4_main.png` is the body behind the wall (drawn 89 px right of the wall, fixed); `boss4_wall_without_shield.png` is the wall (89×320, five units 64 px apart). Each part is placed where it sits in the pictures (found by exact match): in every unit 11 overlapping bars (`boss4_wall_bar.png`, at x 1, 5, … 41, 16 px tall), in the middle unit also the two core bars (`boss4_wall_bar_core.png`, x 49 and 57), the main orb (`boss4_wall_main_orb.png`, x 65) and the shield (`boss4_wall_shield.png`, x 0). The game cuts the parts out of the wall picture and draws whatever is still standing on top. Black and (6,6,6) are see-through.
  - **The wall loops**: it moves up and down as before (8 px steps, 152 px each way, random stop, ramming), but no longer stops at an end: going off the top it comes back in at the bottom and the other way round, so it can scroll on forever.
  - **Bars**: 10 HP each, shot from the front (left) one first. With all bars of a unit gone, its orb is open: 20 HP; destroyed, the orb is erased from the wall.
  - **Shield** over the middle unit: can't be hurt (shots spark off it); it disappears (with an explosion) once the four outer orbs are destroyed.
  - **Middle unit**: then the two core bars (10 HP each, front one first) and the middle orb (20 HP). Destroying it removes the main orb picture and opens a gap through the wall to the **giant core** of the main body (box x 10, y 82, 24×14 in `boss4_main.png`): 40 HP, only hurt once the gap is open; 5000 points.
  - Points: 100 per bar / core bar, 1000 per orb.
  - **No enemy shot gets through any part of the boss** (wall or main body); the pods' own shots start just left of the pods.
  - The bars under the shield are ordinary bars (10 HP, reached only once the shield is gone).

## 2026-10-28

### Added (from Albert)
- **Option formations** (pad R2 / L1 / L2; keyboard C / V / F; `Controls.Action.OPT_ORBIT / OPT_V / OPT_FREEZE`, `GameScreen.updateOptions`, `Player.optMode`). Each button toggles its formation; another formation's button switches straight to it; the options glide to their new places at 260 px/s.
  - **R2, orbit**: the options circle the ship's middle, 26 px out, evenly spaced, at 3.2 rad/s (about half a turn a second), starting where the first option is.
  - **L1, V formation**: in pairs, one on each side, each pair 14 px further back and 16 px further out: above and below the ship on a horizontal stage, beside it on a vertical one.
  - **L2, freeze**: the options stay where they are around the ship and move with it 1 to 1 instead of trailing.
  - The triggers (R2, L2) are read as buttons or, on the desktop (where gdx-controllers reports them as axes 4 / 5), as axes pressed more than halfway. A new option joins the formation at once; a respawned ship starts with the normal trail.
- **AIM works with LASER and RIPPLE too** (hold R1 / Enter + a direction, as with DOUBLE; the ship holds still while aiming, the dotted line shows the direction). The whole weapon fires the aimed way, in any of the 8 directions; straight ahead is the normal beam / ring. LASER and RIPPLE share one aim (`Player.laserDir`, starts straight ahead); DOUBLE keeps its own.
  - **Aimed LASER**: a stream of short beam pieces, one out of each gun every frame for as long as the front takes to reach the screen's edge (like the straight beam), all flying the aimed way at the laser's speed (`GameScreen.AimBeam`, `Bullet.seg`). The pieces of one beam share their hits, so it hurts each target once, as a beam does. A piece stops at whatever stops a laser (the beam flows into it). Counts toward the gun's laser limit.
  - **Aimed RIPPLE**: straight up / down / back it flies like the normal ring turned that way (stretching across its way, stopped by rock at its core); diagonally it grows to 96 px across its way (`Bullet.diag`, `RIPPLE_DIAG_MAX`).

## 2026-10-27

### Added (Kierke, from the MSX recording)
- **The web animates** (`maps/stage5_web.png`, `GameScreen.loadWeb` / `drawWeb`). Analysed frame by frame: the web's strands don't change shape freely; 2572 of its pixels flicker as the screen scrolls — strand pieces hop 1 px back and forth. One cycle per 8 px of scrolling, in 8 states (one per pixel scrolled; in the recording a new state every ~3 frames at ~20 px/s, and the period follows the scroll speed along the stage), each pixel lit for 4 states in a row and dark for the other 4, from its own starting state. Captured from the recording in map coordinates and stored as a picture the size of the map art: colour = the pixel's colour when lit, alpha = 255 − 16 × phase (−8 more if the map art doesn't show that pixel). The game draws them right after the map art, lit or black, from the camera's x (state = (x − 5) mod 8; the MSX's 8 px steps fall on x ≡ 5). The rest of the web stays as it is in the art. With the recording's frames this removes about a third of the differences between the art and the MSX web (the rest is timing within a frame and sprites).

### Changed (Op 4 / Eioneus, from Albert)
- **Eioneus' boss fires the enemies' laser too**: its four falling lasers now use `enemy laser red.png` / `blue.png` (flashing, upright), like the gun units. The dotted lasers are gone from the code (`GameScreen.drawGunLasers`).
- **Green mines in the gun units' fight**: once the gun units have appeared, a green mine (Kierke's homing mine) flies in from a random edge of the screen (left, right, top or bottom) after 3 s, then every 2.5–4.5 s. `GameScreen.GMINE5_*`, `spawnEdgeMine`.

### Changed (Op 4, from Albert)
- **The gun units (boss5b) move down and up every now and then**, not only down once after the first orb: every 3–6 s they go down to 40–56 px lower or back up to at most 16 px below where they started (16 px/s). The first-orb trigger is gone. `Boss.GUNS_WAIT_MIN / MAX`, `GUNS_UP_MAX`.
- **The gun units fire the enemies' laser** (`enemy laser red.png` / `blue.png`, flashing, turned upright as the Rages fire it; as wide as the picture for hits) instead of dotted lasers. Eioneus keeps its dotted lasers. `GameScreen.GunLaser.beam`.

### Fixed (Kierke boss, from Albert)
- **Barrels left a ring behind**: shooting off a barrel cleared one row too few, so its bottom ring stayed on screen. Now all 8 rows go.
- **The light-blue cannon can be shot** like the others (barrel, then ball); it still isn't needed to open the core.
- **Every pod fires** (all five, anywhere on the screen; `Boss.K_POD_ROWS`, `K_FIRE_BANDS`), not just the two upper ones in two bands as in the recording. About 3 times as many shots; `Boss.K_SHOT_T` (0.28 s) sets the rate.

### Changed (all bosses, from Albert)
- **No WARNING text when a boss appears** (it used to flash in red for 2.6 s). The boss sound and the boss music are unchanged.

### Fixed (Celtic Frost, re-checked frame by frame against the stage 2 MSX recording)
- **Vertical-stage Celtic Frost path** (both styles; `Enemy.frostAt`, `FROST_V_TURN` 112, `FROST_V_STEP` 16, `FROST_V_DROP` 72, `FROST_V_OUT` 136, new `Enemy.frostIdx` = place in the wave): down the column to 112 px below the top, 45° up toward the middle (16 px for the first frost, 32 for the second, … 16 more for each one behind), straight down 72 px, 45° up toward the middle again until 136 px across from the entry column, then straight up and out. The wave turns from a column into a diagonal line and leaves up a column on the far side. Replaces the "V" (48 px turn, 48 px dip). Matches the recording to ~4 px (median; the MSX moves in 8 px steps). Also used by the other vertical stages' frost.
- **Stage 2 has only the 4 Celtic Frost waves of the recording** (both styles): x 184 at 7.8 s, x 80 at 10.9 s, x 184 at 15.4 s, x 80 at 17.4 s; 6 frosts each (objects 78, 79, 82, 83). The "columns" at x 48 and x 216 are not waves: they are these waves leaving (the x 184 waves leave up x 48, the x 80 waves up x 216). Removed: objects 80, 81, 84, 85 (x 48 / 216) and the four mid-stage waves the recording doesn't have (52, 54, 70, 71). No frost appears in the recording after 23 s. Map before: `Claude outputs/stage2.tmx.before-frost-cleanup`.
- **Kierke (stage 5): no Celtic Frost** (Albert): objects 66, 67, 69 removed. Map before: `Claude outputs/stage5.tmx.before-frost-removal`.

### Changed (from Albert, evening)
- **Op 4 starts with stars again**: `noStars` back to `0,4214` (it had become `0,4672`, the whole stage, which made the opening black). The stars scroll away with the open space at the start, as before; the end fight at the top is still in black space.
- **Health of the non-moving enemies** (Albert): every turret (stage 1 floor / ceiling turrets, stage 2 wall turrets) 1 (was 3 + stage index); Omen 12 (was 3); every dome 12 (Eioneus / Op 4 blue domes were 5; the stage 2 and Lavinia domes were already 12); Kierke nests 12 (was 6); bases (Eioneus and Op 4) 12 (was 8); stage 2 eye rocks 12 (was 3). `Enemy` (turret case, `OMEN`, `NEST_HP`, `BLUE_DOME_HP`, `SEED_HP`), `Hazard.EYE_HP`.
- **Lavinia: the Laser Guard room lasts 30 s in both styles** (MSX style was 52.5 s): `timeMsx` removed from the `hold` object in `stage3.tmx`.
- **Small storm rocks stop every shot** (before, only lasers stopped on them; other shots flew through). Big rocks still split, red volcano rocks still break.
- **Eioneus: no points for the boss's bars and orbs, nor for a gun pod's bars** (as on the MSX). A beaten pod still gives 10,000, the boss still 5,000; Op 4's gun units keep their points.
- **Op 4's banner**: "OPERATION FOUR / -CRUSH SALAMANDER-" (map properties `bannerTitle`, `subtitle` in `stage6.tmx`).

### Added (Eioneus, from the MSX recording 216-292 s; Albert)
- **Boss room: the three gun pods** (`gunpod.png`, the map's `gunpod` objects). When the scrolling stops at the top of the cave, the pods wait 6 s out of sight and then come down out of the ceiling, 8 px every 0.4 s, into their places (in the recording they are in place 9.8 s after the stop). Each has a column of 4 bars (`boss_bar.png`, found in `gunpod.png`) in front of its red orb: a shot into the column hurts the lowest bar left (10 HP each, 100 points); when its last bar goes the pod is beaten (10,000 points, exactly when the bars are gone, as in the recording; the orb stays). The rest of a pod stops shots and lasers and hurts the ships; the inside of a column is free once its bars are gone (the ship flies up into it in the recording). `GameScreen` pod room (`POD_*`, `podShot`, `podTouches`, `drawPods`); `Level.pods`.
- **Bouncing bubbles** (`orb.png`; `Enemy.bubble`): while the pods hang there they let bubbles out from under their middle, the first 1.5 s after they are in place, then one every 0.8 s while there are fewer than 4. A bubble moves diagonally at 57 / 44 px/s and bounces off the rock, the screen's edges and the line under the pods. Like the Op 4 orbs it can't be shot (a guard takes one).
- **Then the boss**: 2 s after the last pod is beaten the bubbles go and everything on the screen (rock and pods) breaks up pixel by pixel in 2 s (`Dissolver`, as on Op 4), leaving black space; 4 s later the boss comes down.
- **Eioneus boss movement and lasers** (`Boss.initEioneus`, `EIO_*`): it comes down through the top edge in the middle (72 px/s) until its top is 41 px above the top edge; every 0.25 s it steps 8 px sideways toward the nearest ship (its middle over the ship; its left edge stays between 35 px off the left of the screen and 89 px in). 8 s after it came it steps down (8 px every 0.17 s) until its top is 7 px below the top edge, and from then on goes 8 px up and back every 4 s. Every 11 s (the first 5.6 s after it came) four dotted lasers come out of its leg tips (x 45 / 76 / 116 / 155, 121 / 137 / 137 / 121 px down in `boss3.png`), grow down at 160 px/s to 100 px, flash red / blue and fall on (the gun units' lasers, now shared: `GameScreen.bossLasers`). No other shots. Beaten, it breaks up pixel by pixel (in the recording the red orb goes last, then the boss breaks up for ~3 s). It stays solid, but the ships can fly up into a column as far as it is shot away (it was solid all the way). It no longer uses the shared boss pattern.
- In the recording the score doesn't change for the boss's bars and orbs; the game keeps its points for them (100 / 1000).

### Added (Op 4's end, from the MSX recording 220–268 s; Albert)
- **Asteroid storm** (`meteors` object in `stage6.tmx` at y 3768): 11.5 s of small grey rocks (no big ones), straight down at 160 px/s, coming in only between screen x 73 and 177, as in the recording (41.8–53 s). New optional `meteors` properties `speed`, `straight`, `xMin`, `xMax`; `Level.storms`, `GameScreen.updateStorm`.
- **Two more Gremlins**: yellow at x 320, y 1872 and red (capsule) at x 288, y 1744 (156.7 s and 163.6 s in the recording).
- **The terrain dissolves**: the path now ends at camera top 376 (the end of the shaft, as on the MSX). There the whole terrain on the screen breaks up pixel by pixel in 3 s and leaves black space; the collision goes with it (`Level.clearGrid`, back on a restart). `noStars` is now `0,4672`, so the fight is in black space. `GameScreen.updateOp4End`, `dissolveTerrain`.
- **Reusable pixel-by-pixel death**: `Dissolver` (new class: a picture losing its pixels one by one in a random order, faster and faster; `Dissolver.appear` does it in reverse) and `GameScreen.dissolveDeath(picture / sprite frame / enemy, x, y, seconds)`. Used for the terrain and both Op 4 bosses; any enemy can use it.
- **Brain sphere (boss5a) moves as on the MSX** (replaces the bouncing): it rises in through the bottom edge (70 px/s), parks with its top at the top edge (bobbing 4 px) and slides toward the nearest ship (16 px/s). The ships fly over it; only its eye takes damage (60 HP), the rest of it lets shots through. Beaten, it breaks up pixel by pixel. `Boss.initSphere`, `Boss.ghost`, `Boss.weakX..weakH`, `Boss.dissolve`.
- **Option eater** (`sprites/option_eater.png`, cut from the recording at 233 s: the white / red flashing lattice mine under the sphere). During the sphere fight one comes in through the bottom edge every 5 s and makes for the nearest ship's last option (or the ship) at 30 px/s; touching an option eats it (the ship loses it). 2 hits, 300 points. `Enemy.Type.OPTION_EATER`, `GameScreen.eatOption`.
- **Gun units (boss5b)**: appear where they are, pixel by pixel (2.2 s), top 48 px above the screen; sway 4 px; once the first outer orb is gone they come down 56 px (16 px/s), then bob a little. The ships can fly over every part of them to reach any orb (no collision with the body; only bars, orbs and the core shield stop shots). Attacks as in the recording: every 6 s dotted lasers (blue / red flashing) grow down from under the columns at five places and fall down the screen (80 px/s, 80 px long), and every 6 s a green creature comes out of the middle and homes in on the nearest ship. Beaten, they break up pixel by pixel. `Boss.initGuns`, `GameScreen.gunLasers`, `gunCreature`. (The "pink crawlers" I noted on the left column turned out to be the hit sparks of the shots on its bars, so there is no such attack.)

### Added (Kierke boss, from the MSX recording, 3:05–4:51)
- **The machine wall** (`boss4.png`, behaviour `Boss.kierke`, pictures `GameScreen.loadKierkeBoss`). On the right a fixed frame with the core; on the left a column of 10 units (light-blue cannon, pod, white cannon, pod, red unit, pod, yellow cannon, pod, green cannon, pod; 32 px each, 320 px in all), built from `boss4.png` (the light-blue and green cannons are the yellow one, recoloured to the recording's colours). As measured:
  - Once the scrolling stops, the column moves up and down in 8 px steps every 17 frames (~28 px/s) within a 152 px range: each round it goes to the far end, waits 2 s, goes to a random spot (16 px grid), waits 1.7 s, then rams 96 px left toward the ships and straight back (8 px every 8 frames, ~60 px/s) and waits 0.45 s.
  - The two upper pods fire slow aimed shots (42 px/s) every 0.28 s, except while ramming, from the top band (y 14–62) and the band at y 94–126 of the screen, as in the recording.
  - The cannons' barrels and balls can be shot (barrel 16, then ball 12; the red unit has only a ball; the light-blue cannon can't be hurt); no points, as in the recording.
  - The core (the eye in the frame) only takes damage once the four balls are gone (in the recording it was shot from the gap between the column and the frame for minutes and only died 5 s after the last ball): 40 HP, 5000 points. The rest of the boss stops shots and lasers and destroys a ship that touches it.

### Changed (Op 4, from Albert)
- **The gun units (boss5b) work like the Eioneus boss**: 5 columns of 5 bars (10 HP each), each guarding an orb (30 HP): blue, grey, red (middle), yellow, green. The core shield (`boss3_core_shield.png`, painted into `boss5b.png` at 113, 152) closes the middle column until the other four orbs are destroyed; destroying the red orb beats it. Health bar: 400. The Eioneus code is now shared: `GameScreen.BAR_BOSSES` (one entry per bar-wall boss: picture, boss part, bars, orbs, middle column, shield), `barBossShot`, `barPartDestroyed`.
- **Red moai rings drop a capsule**: in every burst of 6 rings a moai head spits, one (at random) is red (flashing red / dark red) and leaves a power capsule where it is shot down. `Enemy.moaiRedIdx`, `Bullet.ring` 3, `GameScreen.RING_RED_DARK`.
- **Red Creeping Death drops a capsule**: of every burst of Creeping Death the two rock-opening domes let out, one (at random) is red and leaves a power capsule when shot down; the others are the cyan / green kind (before, every other one was red-coloured and none left a capsule). `Enemy.creepRedIdx`. Kierke's nests are unchanged.

### Changed (all stages, from Albert)
- **REMAKE style = everything the MSX style has, plus its own extras**: map objects with `style` = `msx` now appear in both styles; `style` = `remake` objects stay REMAKE-only (its extras). MSX style is unchanged. The REMAKE objects that covered the same stretch as the MSX ones (every stage's REMAKE opening waves, and stage 2's second asteroid storm) are removed: 79 objects (stage 1: 26, 2: 14, 3: 26, 4: 3, 5: 4, 6: 6); the maps before the cut are in `_to_delete/`. REMAKE extras that stay: stage 3 Frost at x 1594 / 2994 / 4094, stage 4 Legacy / Frost waves from y 3608 up, stage 6 Frost at y 2407 and Legacy at y 1308. Only enemy placements: speeds (`speedMsx`, `speedsMsx`), `timeMsx`, `burstMsx` and the MSX behaviours stay MSX-only. `Level` (object loading).
- **Lasers stop on everything that can't be destroyed**: every enemy for which `Enemy.indestructible()` is true (now also Laser Guards, Op 4 laser guns and orbs, and Lavinia's Rages; before only blocks, Heathen pillars, chain balls and blue asteroids) and the asteroid storm's small rocks. The beam stops in front of them instead of passing through. `GameScreen.laserBlocked`.

### Added (all stages, from Albert)
- **Continue at game over**: the game over screen now has a menu, CONTINUE / END (up / down to choose, fire or Enter / Start to pick, after 1 s). CONTINUE puts every player who was in the game back in with a full set of ships at the last checkpoint, score kept (as with the mid-game continue), stage music again. END puts the scores in the hi-score table and goes back to the title screen (scores are no longer entered the moment the game is over). `GameScreen.GAMEOVER_MENU`, `updateGameOverMenu`.

### Changed (Eioneus, from Albert)
- **Eioneus boss: bar walls, orbs and the core shield** (Albert's `boss_bar.png` and `boss3_core_shield.png`). The boss has three columns of 5 bars (found in `boss3.png` by matching `boss_bar.png`), each guarding an orb above it: blue (left), red (middle), grey (right). A shot into a column hurts its lowest bar left (10 HP each, 100 points), then the orb (30 HP, 1000 points); the rest of the body only stops shots. The middle column is closed by the core shield until both side orbs are destroyed: then the shield is blown away (with the bottom of the middle column), and its bars and the red orb can be shot. Destroying the red orb beats the boss (5000 points). Shot-away parts leave the dark inside of the column. The health bar shows what is left of all parts (240). Lasers hit each part once. `GameScreen.BAR_BOSSES`, `barBossShot`, `barPartDestroyed`; `PixelMask.clear`. Its movement and shots are still the shared boss pattern.
- **Side volcano rocks can be shot**: one hit destroys a red rock (small explosion, 50 points; a laser goes on through it). Before, shots passed through them. `GameScreen.VOLCANO_ROCK_SCORE`.

### Fixed (Eioneus, from Albert)
- **Red Artillery at the start of the cave (left and right wall)**: it was painted into the background art (`stage4_t3.png`, at x 31, y 3553), so it looked like scenery. It is cut out of the art (original in `_to_delete/`) and is now a real red Artillery (object 101 in `stage4.tmx`, `red` = leaves a capsule), pointing right off the wall. The second painted one, on the right wall at (225, 3489), is fixed the same way: a real red Artillery (object 102, flipped), pointing left.

### Changed (all stages, from Albert)
- **Shorter stage intro**: the operation banner now shows for 2 s (was 3 s) and the HUD on the black playfield for 1 s (was 2.5 s); the 0.6 s of black between them is unchanged. `GameScreen.INTRO_BANNER`, `INTRO_HUD`.

### Changed (Lavinia, from Albert)
- **Rage is indestructible**: shots stop on the Rages the warship spits out (as on the Laser Guards), homing shots ignore them, and an option guard can't destroy them. `Enemy.indestructible()`.
- **Lavinia warship: the hatch has its own health (30)**. Shots on the hatch, open or closed, hurt only the hatch; when its health is gone it breaks (with an explosion) for 10 s (was 5.1 s), and only then can the boss itself be hurt, anywhere on it. After 10 s the hatch is back, whole again (30). Replaces "the hatch breaks once at half the boss's health". `Boss.HATCH_HP`, `hatchHp`, `damageHatch`, `hatchBroken`.
- **Lavinia warship moves at 75% speed**: up and down at 36 px/s (was 48) and, while the hatch is broken, 187.5 px/s (was 250). `Boss.HATCH_MOVE`, `HATCH_RAGE_MOVE`.
- **Laser Guard room is shorter: 30 s** (was 60 s) before the guards retreat and the walls dissolve: the `hold` object's `time` in `stage3.tmx`.
- **Boss music starts before the Laser Guard room**: the `bossmusic` marker in `stage3.tmx` moved from x 4893 (past the room, so the music only came after the guard fight) to x 4492. The stage music now fades out when the room's entrance wall (x 4600) is on the right of the screen, and the boss music plays through the guard fight into the boss.
- **Led Zep always jumps right across** to the other surface (floor to ceiling, ceiling to floor). It no longer makes "half" jumps up to the ship's height and back (was 60% of its jumps). `LEDZEP_CROSS` removed.

### Fixed (Lavinia, from Albert)
- **Artillery is drawn on Lavinia**: its pictures (`artillery_green.png` / `artillery_red.png`) were only loaded on Op 4, so on Lavinia it was invisible (it could still be hit and left its capsule). They now load on every stage.

### Changed (all stages, from Albert)
- **Every Whiplash leaves a power capsule** when shot down (before, only ones with the map property `drop`). The `drop` property on `whiplash` objects is now ignored. `Level` (case `"whiplash"`).

## 2026-10-26

### Changed (all bosses, from Albert)
- **Lasers never pass through a boss**: a ship's laser now stops at any part of a boss (its drawn pixels, eye, core, Lavinia's hatch, Tetran's rings, and every ball of stage 1's boss arms; before, it went through everything but the weak spot and the claw). It still hurts the boss where it stops if that is a weak spot (once per beam, as before); Tetran's rings and armour just stop it. `GameScreen.bossBlocksLaser`, `LASER_REACH`.

### Changed (Lavinia, from Albert)
- **Led Zep frames**: frame 0 while it crawls along the floor or ceiling, frame 1 only while it is in the air (it used to swap frames all the time).

### Changed (Op 4, from Albert; for now)
- **The brain sphere (`boss5a`) moves**: instead of sitting still it drifts diagonally (42 px/s across, 30 px/s up/down) and bounces off the screen edges, its ball (the solid part of the picture) staying 2 px inside the screen. It still shoots as before. `Boss.initBounce` / `BOUNCE_*`.

### Changed (all stages, from Albert)
- **Stage intro as on the MSX** (measured in the five recordings): the operation banner alone on a black screen for 3 s (no HUD, no stars, no ship), 0.6 s of black, then the HUD on a black playfield for 2.5 s, then the stage starts (was: 2.2 s of "STAGE n" over the stage). The banner is white MSX-style lettering, three lines at 76 / 92 / 116 px below the top: new map properties `bannerTitle` (default: `name`), `subtitle` and `planet`. Set from the recordings: stage 2 "OPERATION TWO / -CONDUCT TO SPACE STATION ZOT-"; stages 3, 4, 5 "OPERATION THREE / -SET THE LEEK SYSTEM- / PLANET LAVINIA / EIONEUS / KIERKE". Stages 1, 6 and 7 show their `name` only (no recording of their banners yet). The boss test shows "BOSS TEST" as the subtitle.

### Changed (Lavinia, from Albert; round 6)
- **Lavinia's domes let Creeping Death out exactly as the Kierke nests do** (replaces round 5's "organic" version): while a dome's middle is between x 16 and 200 on the screen it is open and lets them out of its mouth in bursts of 4, 0.15 s apart, a burst every 2.5 s; each rises out of the mouth (sinks out of a ceiling dome) for 1.3 s and then homes in on the ship. One of each burst is red and leaves a power capsule (`Hazard.updateCreepDome`, the `CREEP_*` constants in `Enemy`).

### Changed (Eioneus, from Albert)
- **Gremlins fire sooner**: the aimed shot now comes 0.4 s after a gremlin stops (`Enemy.GREMLIN_FIRE`) instead of at the end of its 3 s stop, when it had often already scrolled off the screen (no shot then).

### Changed / fixed (Lavinia, from Albert; round 5)
- **Creeping Death out of the domes is livelier**: they come out one after another (0.08–0.3 s apart) instead of all at once, each with its own rise speed and time, swaying from side to side as it rises and slowing at the top, then turning smoothly toward the ship (at most 140°/s) at a speed that swells and fades a little (`CREEPO_*` in `Enemy`; the Kierke nests' Creeping Death is unchanged).
- Fixed: of the six laser gun points in the shaft only four showed. Enemies that stay where the map has them (Omens, laser guns and batteries, artillery, the blocks) were removed as soon as they came, when they were further below the camera's lane than 60 px (the bottom row of the shaft: it comes in while the camera is still in the upper lane). They are no longer culled for that (`Enemy.anchored`).
- **The two old wall lasers by the Laser Guard room** (objects 26, 27) are now laser gun points too (objects 140, 141, pointing left, pulses 0.9 s apart).

### Fixed (Eioneus)
- **Huge lasers next to the laser batteries**: `stage4.tmx` still had the six old beam markers from the map build (`laser` objects, 4–84 px wide / tall). Since `laser` became the Lavinia wall laser they turned into real beams on top of the batteries' own. Removed them (the batteries draw their beams), and `tools/stages_build.py` no longer writes them for Eioneus (it still erases the beams from the art).

### Added (Kierke, from the MSX recording)
- **Capsules (Albert)**: every battleaxe row (objects 75–81: shoot the whole row) and every Missing Link (objects 114–130) leaves a power capsule (`drop` on).
- **Missing Links** (`missing_link.png`): the "red crabs" in the opening and the cave were Missing Links. Opening (MSX style, objects 114–117): four at 10.4, 21.1, 25.6 and 32.7 s, coming in at the top or bottom of their swing; each fires a burst of 3 aimed shots in a line once; the first leaves a capsule (one was seen at ~16 s). Cave (both styles, objects 118–130): a stream of 13 from ~125 to ~162 s, each coming in at mid height every ~2.4 s (gaps at ~130–135 and ~152 s, as seen), swinging down to just off the bottom of the screen, one aimed shot each. As measured: 40 px/s left relative to the screen, a 72 px swing in ~2.4 s, the picture following the swing (frame 0 at the ends, 1, 2, 3 half-way; frames 4–6 are not used), slow shots (60 px/s relative to the screen). New `link` properties `swing`, `up`, `burst` (`Enemy.updateLinkMsx`, `LINK_MSX_*`); links without `swing` (stages 1 and 3) are unchanged.
- **Green mines** (new enemy, Albert's `sprites/green_mine.png`, 2 frames swapped every 0.21 s; both styles, objects 131–134): four come in at the right edge at 36.7, 40.0, 43.2 and 46.3 s and home in on the ship at 33 px/s relative to the screen (in the recording their heading stayed within a few degrees of the ship's direction, even while it moved), through rock; 1 hit, 100 points, no shots. New Tiled class `green_mine` (tile 71, icon `maps/icons/green_mine.png`); `Enemy.Type.GREEN_MINE`, `GMINE_*`.

### Changed (Albert, 2026-10-26)
- **Op 4 clusters renamed to blue asteroids**: Tiled class `blue_asteroid` (tile 47), `sprites/blue_asteroid.png` (was `cluster.png`), `Enemy.Type.BLUE_ASTEROID`, `BLUE_ASTEROID_*`; `tools/stages_build.py` uses the new name. No map used the class name directly (they take it from the tileset).

### Added / changed (Lavinia, from Albert; round 4)
- **Gammas from the left** (both styles): the eight that come in at the left edge in the recording (~57–80 s and ~144 s; two red), objects 132–139. New gamma property `fromLeft`: it comes in at the left edge and drifts right through the world at 56 px/s (`Enemy.GAMMA_VX_LEFT`), bouncing like the others.
- **Lavinia's domes let out Creeping Death** instead of amoebas (the floor and ceiling domes that aren't Gun Ho hatches): each wave is 4, out of the mouth, rising (sinking from a ceiling dome) and then homing in as from the Kierke nests; one of each wave is a red / dark green one and leaves a power capsule.
- **Five Heathens** confirmed (Albert).

### Added / changed (Lavinia, from Albert; round 3)
- **Gun Ho hatches** open first (0.35 s) and the Gun Ho comes out of the middle of the dome; the dome shuts again 0.6 s later.
- **Missing Links and Rugals** in the MSX opening, where the recording has them: two Missing Links at ~20.6 / 23.0 s and a column of three Rugals (two blue, one red) at ~40.5 s (objects 108–112, `style=msx`; the REMAKE opening keeps its own).
- **Artillery** in Lavinia (both styles), as in the recording: four green ones on the floor (x 1304, 1320, 1528, 1576) and four red ones (floor 2840, 2968; ceiling 2952, 3032, flipped in Tiled), objects 113–120. Artillery can now stand on a floor or hang from a ceiling on a horizontal stage (points up / down, fires aimed shots from its muzzle). **Every Artillery now takes 1 hit** (all stages).
- **Gamma** in Lavinia (both styles): seven where they come in at the right edge in the recording (~54–80 s and ~138–144 s; objects 121–127), two of them red. Gammas now use Albert's `sprites/gamma_blue.png`, and `gamma_red.png` for the red ones (new property `red`: it leaves a power capsule); `gamma.png` is still used when the blue one is missing.
- **Led Zep** (new enemy, both styles), with Albert's `sprites/led_zep.png` (2 frames): the yellow crawlers of the lower corridor. As in the recording each crawls along the floor or the ceiling (45 px/s across the screen) and every ~1 s leaps away from it (80 px/s), to the nearest ship's height and back, or (4 times in 10) right across to the other side. They don't shoot. As Albert suggested they come at random: while the camera's right edge is inside the new rectangle object `ledzep_zone` (x 3720–4620), one every ~1.3–3.8 s (`interval` 2.5), at the right edge (or 3 times in 10 at the left), on the floor or the ceiling. Tuning: `LEDZEP_*` in `Enemy`.
- **Laser gun points** (new enemy, both styles), with Albert's `sprites/laser_gun_point.png`: the three emitters in the middle pillar of the shaft, each firing to both sides (property `twin` 72 = the second one, across the pillar, facing the other way). Albert: unlike the MSX beams they can't be destroyed and fire in short pulses so a ship can slip through: every 1.8 s the laser grows out to the wall across at 260 px/s, stays 0.25 s and is pulled in after it; the three rows are 0.6 s apart (`delay`). They replace the six shaft `laser` objects (15–17, 21–23); the two `laser` beams lower down stay as they were. New Tiled class `laser_gun_point` (objects.tsx tile 70; flip it horizontally to point left). Tuning: `LGUN_*` in `Enemy`. Albert may want these to move like the Laser Guards at the end of the stage later.
- **Every Battleaxe row leaves a capsule** (Lavinia, objects 82–86 too).
- **Red Possessed** (all stages): every red one leaves its own capsule when it is shot down; the rest of its group doesn't have to be.

### Added (Lavinia, from Albert; both styles)
- **Lavinia blocks**, with Albert's `sprites/lavinia_block_small.png` (32×16) and `lavinia_block_big.png` (32×32), measured in the MSX recording. They were bricks in the map; the 6 small and 2 big ones are now enemies (objects 100–107; new Tiled classes `block_small`, `block_big`, objects.tsx tiles 68, 69; their brick cells are cleared).
  - **Small** (x 2088–2488): once 32 px inside the right edge, each slides up and down between the ceiling and the floor at 40 px/s, starting from one end (they take turns: top, bottom, top …). They can't be destroyed: shots and lasers stop on them, and they hurt a ship.
  - **Big** (the slots at x 2776 and 3328): once on the screen it slides left through its slot at 20 px/s. 20 hits destroy it (500 points). A laser still hurts it, but bounces straight back to the left as a laser that hurts the ships.
- **Lavinia warship** (boss), with Albert's `sprites/lavinia_boss_hatch.png` (closed / open / destroyed, 16×30, on its nose). As in the recording it comes in from the left edge behind the ships (40 px/s) to the right of the screen, then moves straight up and down between the top and the bottom (48 px/s). Every 1.33 s the hatch opens for 0.4 s and launches a **Rage** (Albert's `sprites/rage.png`): it flies straight left at 77 px/s while its laser (the red / blue enemy laser pattern) grows up and down from it at 40 px/s until it reaches the screen edges; the laser hurts a ship, shots pass it. Only the open hatch can be hurt. At half health the hatch is destroyed: for 5.1 s no Rages come and the warship rushes up and down (250 px/s); then the hatch is back. The second time the warship is beaten. It no longer uses the shared boss shots. Tuning: `HATCH_*` in `Boss`, `RAGE_*` in `Enemy`.

### Changed (Lavinia, from Albert)
- **Five Heathens** instead of three: the recording has five passes, at ~196.0, 200.8, 205.5, 210.4 and 215.3 s (objects 95–99 replace 92–94), each picture still chosen at random.

### Added (Lavinia, from Albert)
- **Heathen** (new enemy, both styles), with Albert's `sprites/heathen_top.png`, `heathen_center.png`, `heathen_bottom.png` (40×120) and `heathen_bar.png`: the pillars with red bars in the lower corridor. As in the MSX recording (~196, ~206 and ~215 s) a Heathen comes in at the right edge and rushes left across the screen at 100 px/s. It can't be destroyed: its only way through is the gap with 4 bars, which break like Tetran's core blocks but are much weaker (3 hits each, 100 points; shots break the front one first, a laser hurts each bar once). The pillar stops shots and lasers and hurts a ship; once the bars are gone a ship can fly through the gap. Which of the three pictures comes (gap at the top, centre or bottom) is random every time. Three are placed (objects 92–94, new Tiled class `heathen`, objects.tsx tile 67); the three pillars that stood in the map art and collision there are gone. Tuning: `HEATHEN_*` in `Enemy`.

### Changed
- **One way to leave out the stars**: the map property `noStars` (`a,b`, Tiled coordinates along the scrolling: x on a horizontal stage, y on a vertical one) now does it for every stage; `starsBelow` is gone (Eioneus and Op 4 use `noStars` instead). One `GameScreen.drawStars` draws the star field around that range, for both directions.

### Changed (Lavinia, from Albert)
- **Lavinia: no stars once the ceiling begins**, as on the MSX: new map property `noStars` (`x0,x1`, Tiled x) leaves the sky plain black between x 1752 (where the ceiling starts) and 4888 (the end of the base). The open space at the start and the boss's space still have stars; the stars stop at that line and scroll away with it.

### Changed (Eioneus, from Albert)
- **Eioneus: no stars once the rock begins**: `noStars` = `0,3624` (from just below the lowest rock of the opening to the top), so the stars scroll away with the open space at the start, as on the MSX.
- **Asteroid storms: big rocks only in stage 2.** The big rocks that split are stage 2's own; every other stage's storm (Eioneus) is small rocks only.
- **Side volcanoes** (new; measured from the MSX recording): three rock tips in Eioneus throw red rocks out sideways — (121, 1900) and (178, 1071) to the left, (79, 1086) to the right (objects 98–100, `volcano` with the new `spits` property). Bursts of 3–5 rocks, 0.12 s apart, every 1.8–3 s while the tip is on screen; each flies out at 45–90 px/s, drifting up or down through the world, slows down and falls back into its wall (pulled at 85 px/s²; measured 85–105). They keep one picture each, ignore shots, hurt a ship, and a guard breaks them, like storm rocks. Picture: `sprites/rock_small_red.png`, Albert's `rock_small.png` recoloured to the stage's rock red (247, 104, 104); replace it with your own if you like. Tuning: `Meteor.SIDE_*`.

### Changed (Op 4, from Albert)
- **Red Gremlins leave a power capsule** (all stages, Eioneus too).
- **The two domes in the rock opening let out Creeping Death** (new `dome_type4` property `spawns` = `creeping death`), timed like a Kierke nest: bursts of 4, 0.15 s apart, a burst every 2.5 s while the dome is on screen. Each one flies out of the dome's mouth (sideways, away from the wall) for 1.3 s, then homes in on the ship; every other one is red / dark green. The dome shows its open frame during a burst. (Replaces yesterday's `burst` 0.)
- **Gremlins in Op 4** (both styles): eight on the left wall of the shaft, where they come up from the bottom edge in the recording (56–111 s; three red, objects 86–93). They behave as in Eioneus. Gremlins now find their rock face on stages whose screen isn't at the left of the map (they looked at the wrong part of the map there).
- **Moai rings flash** yellow / white (15 times a second) instead of a whole burst being one colour.
- **The two domes in the rock opening** let nothing out in either style (`burst` 0; was `burstMsx` 0), so they never open.
- **Op 4 bases** let out waves of 5 mines while they are on screen, the next 2.5 s after the last (so at least two waves), and one mine of every wave, at random, is red and leaves a power capsule.
- **Op 4 orbs can't be destroyed**: shots stop on them; a SHIELD or F.FIELD absorbs an orb that runs into it (the guard takes the hit as usual).
- **Green mines in Op 4** (both styles): the green spiky mines of the rock opening are Kierke's green mines. Nine placed as in the recording (41.8–54.5 s, objects 77–85): pairs come in at the top edge (screen x 0 / 160 at 41.8 and 53.0 s, x 128 at 51.2 s) and four at the bottom edge (new property `fromBottom`), and home in on the ship at 33 px/s. On a vertical stage a green mine comes in at the top (or bottom) edge at its object's x and moves relative to the screen.
- **Op 4 lasers are laser gun points now** (as in Lavinia): the six `laser` beams between the shaft walls and the middle pillar (objects 4–6, 15, 16, 19) are replaced by three `laser_gun_point` objects with `twin` (objects 74–76), sitting on the emitters drawn in the map: rows 1 and 3 fire from both outer walls toward the pillar (`twin` 185), row 2 from the pillar out to both walls (`twin` 73). They pulse every 1.8 s, the rows 0.6 s apart (`delay` 0, 0.6, 1.2), can't be destroyed and let shots through.
- **No stars once the terrain begins**: Op 4 has `noStars` = `0,4214` (from the lowest rock of the opening to the top), so the stars scroll away with the open space at the start, as on the MSX.
- **Clusters stop lasers** (as they already stopped shots): a laser ends where it meets a cluster.
- **Moai heads**: `sprites/moai.png` now has 2 frames (50×38): mouth shut, mouth open. A moai starts against its wall (the game slides it to the rock if the map has it a little away) and still steps out and back. Its mouth is open from 0.3 s before a burst of rings until 0.3 s after it, and **only the open mouth takes damage**; shots that hit the rest of it stop with a spark (a laser goes on).
- **Moai rings can be shot down**: any shot that touches one destroys it (10 points); shots, missiles and ripples are used up, a laser goes on.
- **Two bosses, one after the other**: boss5a (the brain sphere) first, then boss5b (the gun units above it). The scrolling stops once the sphere is on screen and goes on when it is beaten; the gun units wake up as they come on screen, and beating them clears the stage. Each has the stage's boss HP and the shared pattern for now. Boss test (B) starts just before the sphere. (`GameScreen.BOSS_PARTS`; the combined picture from yesterday is gone.)

## 2026-10-25

### Changed (Kierke nests, from the recording)
- **What each Kierke nest lets out** (checked against `stage kierke.avi`): the floor nests at x 1850 and 2298 and the ceiling nests at x 1946 and 2586 let out **Creeping Death** (the ceiling ones going down); the four nests floating in the web (x 2074, 2458, 2810, 3258) let out **flying mines** (Albert: the white spiky "orbs" are the Op 4 flying mines; new `spawns` value `mines`): one burst of 5, 0.5 s apart, once the nest's middle reaches screen x 208; each appears right of the nest, curls anticlockwise up and over it (the loop measured in the recording, `MINEK_PATH`) and dives at the nearest ship at up to 180 px/s (`Enemy.mineKierke`, `MINEK_*`). The nests at x 1554 (both) and both at x 3066 let out Ugs (Albert: the floor one at x 3066 too, though none were seen leaving it in the recording); new value `none` for a nest that lets nothing out, x 1242 stays Creeping Death.

### Changed (later, from Albert's answers)
- **Op 4 crabs are now moai heads** (they represent the moai): Tiled class `moai` (tile 49), `sprites/moai.png` / `moai.xcf` (were `crab.*`), `Enemy.Type.MOAI`, `MOAI_*`. They take as many hits as a stage 1 bamda (`Hazard.BAMDA_HP`, 12).
- **Op 4 artillery** takes as many hits as the turrets (3 + stage index, i.e. 8 on Op 4; was 4).
- **Op 4 clusters can't be destroyed**: shots stop on them with a spark, homing shots and the F.FIELD / shield ignore them (new `Enemy.indestructible()`, shared with the Laser Guards and chain balls).
- **Lavinia Gun Ho hatches in both styles**: the `dome_type2` property is now `gunhos` (was `gunhosMsx`); the four standalone REMAKE Gun Hos (objects 3, 4, 11, 25) are removed, as the hatches let theirs out.
- **Eioneus Possessed rows in both styles**: objects 68–71 lost `style=msx`.
- **Old Tiled class names removed**: `crab`, `spider`, `hatch`, `jumper`, `pod`, `floordome`, `dome`, `bluedome`, `bluedome6`, `asteroid`, `dart`, `dart_side` no longer load (no map used them). The `dome_type2` icon is `maps/icons/dome_type2.png` (was `floordome.png`). `tools/stages_build.py` uses the current names (`aberrant`, `moai`), its tile list matches `objects.tsx`, and it now refreshes those tiles in place (keeping tile properties and tiles 54+) instead of rewriting them; it no longer cuts the unused `pod`, `pod_open`, `dart_side` and `spider` sprites.

### Added
- **Op 4 (stage 6), from `reference material/recordings/stage6.avi`** (the MSX "OPERATION FOUR / CRUSH SALAMANDER"):
  - **Camera**: the path now takes the detour through the side room on the left, as on the MSX (up the shaft to y 2188, left to the room, up 192 px, right back into the shaft). MSX style scrolls at 21 px/s (`speedMsx`; measured average), faster on the detour (`speedsMsx` `1,1.32,1.19,1.1,1`). REMAKE keeps 26.
  - **Exodus** (new enemy, MSX style; Albert's `sprites/exodus_cyan.png` / `exodus_red.png`): the cyan / red insects of the opening, in rows of three (columns 16 / 34 / 52 or 208 / 226 / 244 on the screen). They drop in from the top edge, then make for the nearest ship at 42 px/s per axis. Nine rows placed as in the recording (16–28 s); red ones leave a capsule. New Tiled class `exodus` (tile 62). The REMAKE fly-by Legacy / Celtic Frost waves (objects 25–32) are now `style=remake`.
  - **Whiplash** (MSX style): four, as in the recording (16.3, 19.4, 73.7 and 79.6 s).
  - **Artillery** (new enemy, both styles; Albert's `sprites/artillery_green.png`): the nine wall guns of the pipe shaft (72–110 s), turned to point away from their wall. Each fires an aimed shot 1.4 s after it comes on screen, then every 1.3 s (80 px/s), as measured. New Tiled class `artillery` (tile 63).
  - **Clusters** (both styles): the blue rock clumps sink slowly once they are on screen (5–30 px/s each). Five, where they came in on the MSX (the two from the map art are replaced).
  - **Domes** (both styles): the four wall domes are now `bluedome6` (tile 64, `sprites/bluedome6.png`). They let out bursts of 4 green flyers like Eioneus' domes, but as in the recording (66–74 s) the flyers fly straight out of the mouth and curve round toward the nearest ship. The two domes in the rock opening let nothing out on the MSX (`burstMsx` 0).
  - **Bases** (both styles; Albert's `sprites/stage_6_back_ground_base.png`): the three floor bases (the old `mine` objects) open and let out one burst of 5 flying mines, 0.55 s apart. The mines rise 24 px out of the base, fly the loop measured in the recording (up to the left, round to the right), then home in. New Tiled class `base6` (tile 65).
  - **Crabs** (both styles): fire bursts of 6 rings (`sprites/ring_laser.png`, yellow or white by turns) aimed at the nearest ship, 0.4 s apart, a burst every 3.7 s; all but the first two step out 32 px from their wall and back, as in the recording (171–210 s).
  - **Orbs** (both styles; `sprites/orb.png`): four blue balls that bounce up and down the screen in the side room (117–150 s). New Tiled class `orb6` (tile 66).
- **Lavinia (stage 3), MSX style, from `reference material/recordings/stage lavinia.avi`**:
  - Scroll speed 20 px/s (`speedMsx`), with the drop down the shaft at 0.8× and the lower corridor at 1.1× (`speedsMsx` `1,0.8,1.1`), as measured. The Laser Guard room holds for 52.5 s before the guards retreat (new `hold` property `timeMsx`; REMAKE keeps 60 s).
  - The opening has the six **Battleaxe** rows of the recording (low, high, low, high, low, high; objects 81–86, the first leaves a capsule) and five **Possessed** row groups near the terrain start (objects 87–91). There are no Celtic Frost, Missing Links or Rugals on the MSX: the REMAKE opening (objects 53–78) and the three Celtic Frost waves in the base (41, 43, 45) are now `style=remake`.
  - **Gun Ho hatches**: on the MSX the Gun Hos come out of four of the blue floor domes (x 1440, 1728, 2680 and 4448): one each time the dome's left edge reaches x 208, 144, 80 and 16 on the screen (every 64 px of scrolling). Each hops out (up 48 px, 24 px left, back down to the floor, 16 px left), then lines up with the nearest ship, fires and flies off to the left at 80 px/s. New `floordome` property `gunhosMsx` (4 on those domes); the four standalone Gun Ho objects (3, 4, 11, 25) are now `style=remake`. The other four domes spit amoebas as before.
- Possessed row groups work on horizontal stages too (in from the top edge, as in the Lavinia recording), with new properties `place`, `down`, `side`, `gap` and `colours`.
- **Lavinia wall lasers** (both styles): the eight `laser` objects are now beams between the pillars, on all the time; they hurt a ship and let shots through. Each beam's emitter (part of the wall art, at the pillar end; new property `emitter`, `left` for the three on the right-hand side of the first pillar) can be shot (6 hits) to put the beam out, as beams go out in the recording.
- **Kierke walkers** (both styles), from the MSX recording (77–126 s): the red / cyan hoppers are the stage 1 bio walkers (Albert's `bio walker red/blue.png`). 23 placed where they come in (objects 91–113), in five waves at ~77, 84, 92, 100 and 107 s: pairs leap in from the left and right screen edges at mid height, one dropping onto the floor and one "falling" up onto the ceiling, drifting inward at 40 px/s (gravity 88 px/s², the floor ones glide level for 0.2–0.8 s first). Those that land far from the ship (more than 112 px) walk toward it to about mid-screen; then each keeps walking away from the ship (16–40 px/s relative to the screen, 0.9–3 s) and hopping back toward it, 32–56 px high, or up to the ship's height when it is higher (a hop that reaches the ceiling grabs it, and the walker carries on up there). About one hop in six goes the other way, as seen. They don't shoot, and walking off a screen edge they are gone. Red ones leave a capsule. New `walker` property `leap` (`left` / `right`); a leaper flipped vertically in Tiled heads for the ceiling (for leapers the flip wins over the tile's `ceiling=false`) (`Enemy.updateLeaper`, `LEAP_*`).

### Changed
- **Op 4 boss picture**: stage 6 no longer looks for `sprites/boss5.png`. It puts Albert's `boss5b.png` (the gun units) and `boss5a.png` (the brain sphere, 229 px lower, 5 px in) together into one picture, laid out as in the map, with its hit mask from both (`GameScreen.loadBossPicture`, `BOSS_PARTS`). The `boss` object in `stage6.tmx` now covers both (x 266, 250×361). It still uses the shared boss pattern.
- **Dome classes in Tiled** renamed by type instead of picture: `dome_type1` (stage 2, was `dome`), `dome_type2` (Lavinia floor / ceiling, was `floordome`), `dome_type3` (Eioneus, was `bluedome`), `dome_type4` (Op 4, was `bluedome6`). Only the tileset changed (the maps take the class from it); the old names still work. `tools/stages_build.py` uses the new names.
- **Wall domes of Eioneus (stage 4) and Op 4 (stage 6)** now use stage 2's `sprites/dome.png` (18×32, closed / open while letting flyers out) instead of `bluedome.png`, `bluedome_open.png` and `bluedome6.png`, which are no longer used. What each dome lets out and when is unchanged (the flyers still leave from the same spot). The 17 dome objects were resized to 18×32, keeping their wall side and their middle; the `bluedome` / `bluedome6` tiles show the dome icon. `tools/stages_build.py` no longer writes the blue dome sprites.
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
