# Decisions

Short ADR-style notes. Newest last.

## 1. Box2D binding: gdx-box2d 1.14.2, not 3.1.1-0

- **Context.** The newest `gdx-box2d` on Maven Central is `3.1.1-0`, a new jnigen binding for
  **Box2D v3.1.1** (soft-step solver, sub-stepping, no `getReactionForce(inv_dt)`, weld joints
  configured by linear/angular hertz). `gdx-box2d 1.14.2` ships with libGDX 1.14.2 and wraps the
  classic **Box2D 2.3** C++ engine.
- **Decision.** Use `gdx-box2d 1.14.2`. It has `Joint.getReactionForce(inv_dt)` /
  `getReactionTorque(inv_dt)` (checked in the sources jar), `WeldJointDef.frequencyHz` /
  `dampingRatio`, velocity/position iterations, bullets and group-index filtering: exactly the
  model the POC tuned on Planck.js 1.5.0 (itself a port of Box2D 2.3).
- **Consequence.** The tuned numbers port unchanged. Moving to Box2D v3 later would mean retuning
  joint strengths and solver settings.

## 2. Versions (checked 2026-10-26)

| Component | Version | Note |
|---|---|---|
| libGDX (gdx, backend-android, backend-lwjgl3, freetype, box2d) | 1.14.2 | latest on Maven Central |
| Kotlin | 2.4.21 | latest stable (2.5.0 is in beta) |
| Android Gradle Plugin | 9.4.0 | latest stable; needs Gradle ≥ 9.6, JDK 17 |
| Gradle | 9.8.2 | current |
| compileSdk / targetSdk | 37 | highest API AGP 9.4 supports |
| minSdk | 28 | per brief |
| JUnit | 5.14.4 (Jupiter) + kotlin-test | test scope only (approved) |

AGP 9 compiles Kotlin itself ("built-in Kotlin"), so `:android` applies only
`com.android.application`. AGP and KGP 2.4.21 both sit on the root buildscript classpath so they
share a classloader; AGP's own runtime dependency on KGP 2.2.10 resolves up to 2.4.21.

## 3. The Android module is only configured when an SDK is present

Google Maven and the Android SDK are unreachable from the sandbox this was developed in, so
`settings.gradle.kts` includes `:android` only when `ANDROID_HOME`, `ANDROID_SDK_ROOT` or
`local.properties` points at an SDK (always true on GitHub Actions). `-Pinkmayhem.android=false`
forces it off. Core, tests and the desktop launcher build anywhere.

## 4. One input path: the pen bridge owns every touch

`AndroidLauncher` overrides `dispatchTouchEvent` / `dispatchGenericMotionEvent`; nothing reaches
libGDX's `Input`. Samples (with all historical points) go into a lock-free SPSC ring of primitive
arrays (`PenQueue`) that the GL thread drains each frame, so the input path allocates nothing. The
core does its own hit-testing: fingers may press UI buttons (as in the POC, where the HTML buttons
took any pointer) but never draw. `PenPhase` adds `HOVER_EXIT` and `CANCEL` to the brief's
`HOVER | DOWN | MOVE | UP` so the core knows when the nib leaves or a gesture is aborted.

The side button is read from `BUTTON_STYLUS_PRIMARY`, and also from `BUTTON_SECONDARY`, which some
Samsung firmware reports for the S Pen. `TOOL_TYPE_ERASER` erases too.

## 5. Orientation follows the sensor but respects rotation lock

`screenOrientation="fullUser"`: all four orientations from the sensor unless the player has
locked rotation. `fullSensor` would ignore the lock, which is hostile while holding a pen.
`configChanges` covers orientation, size, layout and `uiMode`, so rotation and dark-mode switches
never recreate the activity and a run survives them.

## 6. Debug and release builds install side by side

Debug builds use `applicationIdSuffix = ".debug"` and the label "Ink Mayhem Debug". CI signs every
debug APK with the same key so each `latest-debug` APK installs over the previous one (see ADR 17).
Release builds are not minified: APK size doesn't matter here, and R8 would need keep rules for
Box2D's JNI callbacks.

## 7. versionCode scheme

Tags `vMAJOR.MINOR.PATCH[-pre]` give `versionName = MAJOR.MINOR.PATCH[-pre]` and
`versionCode = MAJOR × 1 000 000 + MINOR × 1 000 + PATCH`. Debug builds from CI use the CI run
number.

## 8. Test scenarios are validated on the POC's own engine first

The brief lists behaviours, not setups, and the setup decides the outcome (a crate set down gently
on a rope behaves very differently from one dropped onto it). `reference/harness` runs the POC's
`InkCore`, extracted verbatim from the HTML, on planck.js 1.5.0 under Node, and each test scenario
was chosen there first. The Kotlin tests then check the same scenarios on libGDX Box2D. The
runner and boulder tests drive the real `GameSim.tick()` and pen-input path through a sandbox mode
(hand-built terrain) rather than a copy of the logic.

## 9. Sound effects are generated offline and committed

`SfxSynth` re-creates the POC's WebAudio recipes (exponential frequency and gain ramps,
oscillator shapes, biquad filters) as 16-bit WAVs; `./gradlew :core:generateSfx` writes them to
the shared assets and `AssetsTest` fails if the committed files drift from the generator. The
alternative, synthesizing at startup, would need a writable file per sound for `Sound` and adds
startup work for no gain. The scratch is a one-second band-passed noise loop whose volume and
pitch (0.6–1.47× of 3 kHz) glide like the POC's `setTargetAtTime`. Combo chimes are one file per
level rather than pitch-shifted, so their length matches the POC.

## 10. Render interpolation over the fixed 60 Hz step

The simulation keeps the POC's fixed 1/60 s tick (max four per frame, dt clamped at 0.05 s), so
gameplay is identical at any refresh rate. Each tick first snapshots the camera and every body;
frames between ticks draw at `alpha = accumulator / step`. On a 120 Hz panel this turns
"each tick shown twice, with occasional judder" into smooth motion. Pen samples map to the world
through the same interpolated camera the player sees.

## 11. One immediate-mode UI instead of scene2d

The HUD, cards, settings sheet and calibration pad are drawn directly with ShapeRenderer and
FreeType bitmap fonts, laid out from the POC's CSS numbers in dp. All input already arrives
through the pen router, which hit-tests these few widgets itself (a tap activates when it starts
and ends on the same widget, like a DOM click). Scene2d would need a skin and a second input path
for no gain at this size.

## 12. Small behaviour choices

- **Settings persist.** The POC persisted only Sound; every setting is now in Preferences, as the
  brief asks.
- **Haptics toggle** added to the settings sheet (the brief asks for haptics "with a toggle").
- **A second pointer can't hijack a stroke.** In the POC a second pointer-down mid-stroke
  silently replaced the stroke. Now pen-downs from other pointers are ignored until the stroke
  ends. This only matters with "Allow finger and mouse" on.
- **The runner is never culled.** InkCore destroys any dynamic body below y = 400, which in the POC
  could hit the runner after a long fall on the death screen. libGDX recycles destroyed bodies, so
  only ink is culled that way.
- **No allocation per frame.** Hot loops are index-based (`fastEach`), fonts are cached by a
  primitive key, particles and pops are pooled and HUD strings are rebuilt only when their
  values change, so drawing doesn't feed the garbage collector.
- **"Allow finger and mouse" is debug-only.** The POC offered it in every build; the brief asks
  for it in debug builds only, so release builds hide the row and ignore the stored setting.
- **The scratch volume uses real sample times.** The POC timed the scratch with
  `performance.now()` at handler time, so every coalesced pointer event after the first in a batch
  (and the second point of every stroke) looked 1 ms apart and hit the volume cap. The port uses
  each sample's own timestamp, so the scratch follows actual pen speed as the brief describes.

## 13. Audit against the POC

After the port was complete, an independent pass compared the POC and the Kotlin line by line.
It found the gameplay logic faithful (constants, tick order, generation RNG order, scoring,
judging, ink economy, deaths, contacts, joint breaking, culling) and a handful of presentation
differences, all fixed to match the POC: pen input and the ruled lines use the unshaken camera;
spring-bounce particles keep their upward puff (JS `0 || x`); particles and pops clear on a new
run; a replacing toast doesn't blink; a one-point live stroke draws nothing; the settings sheet
spans the width with a 55 % backdrop; gauge labels dim with their band and the marker drops as the
gauge fades; pops draw under the boulder warning and the hover nib; and the vanishingly rare
hazard-pool rounding case yields a plain flat, as in the POC.

## 14. Wet ink: strokes are solid while they're drawn

- **Context.** Playtesting on a phone: the runner often rolled straight through a bridge that was
  still being drawn, because (as in the POC) a stroke only became physical on pen-up. It read as
  the game reacting late.
- **Options.** (a) Build the real stroke incrementally while drawing: its joints, anchors and
  par judging all assume a finished stroke, and appending to a jointed chain mid-simulation
  would change how strokes break. (b) Finish the stroke automatically when the runner reaches
  it: a half-drawn bridge would then be anchored at one end only and fall. (c) Lay the stroke
  so far as static "wet" pieces, solid for the runner alone, and swap in the real stroke on
  pen-up.
- **Decision.** (c). Static pieces can't sag or snap, so the guards keep holding the pen from
  beating lifting it: only strokes begun where they'll be pinned are laid; wet rope stops past
  the 6.8 m a finished rope can hold; boulders (and everything but the runner) pass through wet
  ink, via a collision category of its own; and a crossing on a live stroke only scores once
  both its ends would be pinned. What remains is that held ink is rigid, so an unfinished rope
  ramp can carry the runner while held. That can save a run but never scores, and closing it
  would also break the bridge-in-progress case the change exists for.
- **Consequence.** A rule change from the POC, listed in `tuning.md`. Fast strokes that finish
  before the runner arrives behave exactly as before.

## 15. The tunnel rock

- **Context.** In the POC any contact with the tunnel's ceiling slab was fatal, including landing
  on top of it, which looked wrong; but a safe top makes the tunnel skippable with one spring.
- **Decision.** The rock is one rect for drawing and anchoring with two fixtures: plain rock,
  and a thin spiked layer along the underside (inset from the sides, matching where spikes are
  drawn). The rock rises 40 m, beyond any view and any bounce, so the only way through is
  underneath. The renderer already clips it to the screen.
- **Consequence.** Generation draws exactly the same random numbers, so seeds still reproduce;
  high drops that would land inside a rock are simply not placed.

## 16. The tutorial runs on its own sim

The tutorial builds its lessons with the sandbox mode the tests use (hand-built terrain, fixed
speed) on a second `GameSim`, so the player's run, score and best are untouched and nothing in
the game loop needs a "tutorial" branch beyond choosing which sim to tick, draw and feed input.
The sim gained four small hooks for it: hold the runner, frame a point instead of the runner,
place the runner lower in the view (the lesson card covers the top), and unlimited ink. Lesson
logic lives in `game/Tutorial.kt` and is tested headlessly through the same pen path as play.

## 17. A stable CI debug key without a secret

Every new debug APK has to carry the same signing key or Android refuses to update the app, and
uninstalling first wipes the calibration, tutorial progress and best score. That is what made
the game "ask for calibration every time" on the phone. The `ANDROID_DEBUG_KEYSTORE_BASE64`
secret still gives a permanent key; without it CI now generates one on `main` and keeps it in the
GitHub Actions cache (`actions/cache` restore and save), which holds it as long as there is a
build at least every 7 days. Caches are scoped per branch, but every branch and PR can read
`main`'s, so only `main` saves the key; other runs first delete any copy saved under their own
branch (which would shadow `main`'s), and before `main` has a key they fall back to a throwaway
one. Committing a debug keystore would be simpler but the brief rules out committing any
keystore.

## 18. Stages: hand-built, fixed, on their own sim

- **Context.** An endless flat line got repetitive; the owner asked for vertical play and
  "stages like Mario", for the familiarity of numbered worlds, a finish flag and lives.
- **Decision.** A Stages mode beside Endless. Stages are laid out by hand with a small builder
  (`StageBuilder`: flat, slope, gap, wall, climb, slide, spike pit, lift, seesaw, checkpoint,
  finish) into plain data (`StageLayout`), the same every time so they can be learned.
  `GameSim.loadStage` turns the data into physics; a stage session gets its own `GameSim`, so
  Endless (and its POC numbers and best score) is untouched.
- **Familiar, not copied.** World–stage numbers, a dark "WORLD 1-1 × 3" card between lives, a
  flag at the end with a little castle, hearts as extra lives, a checkpoint, a castle stage with
  a boulder chase, chiptune jingles. All are genre conventions; the characters, pipes, blocks
  and music of any existing game are not used, and the jingles are original.
- **Proof.** Every stage carries a scripted solution. Tests play each stage with it (no deaths,
  every hazard Clean), and with wrong or no ink (deaths), so a level can't silently become
  impossible.

## 19. Stage terrain in the physics

Slopes are convex quads on the static ground body, so the existing anchoring (now a
point-to-polygon distance), cull and render paths keep working. Lifts are kinematic bodies
driven by velocity (never teleported, so the runner rides them); a waiting lift starts when the
runner touches it and parks at the top. Seesaws are dynamic planks on a revolute joint with
limits and damping. Spike strips reuse the ceiling's fatal tag. None of it changes how strokes
are built or break.

## 20. Lives with checkpoint retries

The owner chose lives combined with checkpoint retries: a death costs a life and restarts from
the midway bookmark, and running out is game over, which restarts the stage with three lives
(not the world, which would be harsher than a phone game needs). Retrying rebuilds the stage
from scratch and restores the score, combo, ink and hazard judgements saved at the checkpoint,
so a retry can't farm points or stars.

## 21. Stars reward the game's own skill

Stars are clear, no deaths, and light ink (no hazard cleared with heavier ink than it needed),
so the third star asks for exactly what Endless's combo rewards. A stage unlocks once the one
before has a star.

## 22. Pen tuning by drawing, not writing

The POC calibrates from a signature: the median pressure of writing becomes 0.45, and the
rest is a straight line. A screen recording of a first run showed why that fails. Writing a
phrase is not drawing a line: the player's pen read about 0.076 raw while writing (below the
old 0.08 floor), so rope, spring and beam all came out of a narrow, guessed range, and the
player had no way to learn what "hard" meant to the game. Tuning now asks for each ink in turn
(a rope line, a spring line, a beam line, each along a guide in that ink's colour and width),
checks each is a real line and heavier than the last, and maps the three medians to the
middles of their bands with edges at the geometric means between them, since pressure steps
feel like ratios. A free try follows, with live colour, so the player sees the three inks come
out of their own hand before the tutorial starts. Old one-number calibrations still load, as
the POC's straight line.

The same recording showed the tutorial's first lessons passing or failing for the wrong
reasons: a tap lit an ink chip, pressing in place passed the pressure lesson, and a stroke
drawn in mid-air released the runner into the gap. So the pressure lesson went (tuning's free
try replaces it), each lesson shows a dotted guide where its stroke belongs, a stroke that
can't do the lesson's job is wiped with a hint instead of releasing the runner, and a small
label beside the pen names the ink being laid, since the gauge sits far from where the
player looks.

## 23. Input recordings that replay exactly

Screen recordings show what happened but not why: not the pressure under the pen, which frame
a sample landed in, or what the game decided about a stroke. Debug builds therefore log every
launch's input to a text file: the frame clock and time step, every pen sample, resizes,
insets, pauses, the back key, the world seed and the preferences at launch, plus human-readable
notes and a probe of the runner's position every second. Everything the game reads from outside
now comes through one seam (`FrameSource` for the clock, screen and system flags; the pen
queue for samples; `Platform.preferences()` for settings), so the desktop launcher can feed a
recording back and the game can't tell it from the original. Floats are written in their
shortest exact form, so they read back to the same bits.

On one machine a replay is exact: re-recording a replay gives a byte-identical log (checked on
an Endless autoplay, a stage run and a pen-tuning session). Between a phone's ARM chip and a
desktop, Box2D's floating point rounds differently. In the first phone recording the runner
stayed within 0.1 mm for 90 s, then spring bounces grew that to over a metre and the replay
died where the player hadn't. So the probe (eight a second) carries the runner's position,
velocity, angle and spin and the camera, and a replay puts them back wherever a probe differs;
with that, the same recording replays to the same clear, within 0.1 mm throughout. Text widths come from the same fonts and FreeType on both, so card hit tests match.
Recording is debug-only and on by default, so there is nothing to remember to switch on before
the interesting moment. A debug-only "Record input" switch stops it at once; switching it back
on takes effect at the next launch, since a replay has to start from one. The file is plain text so it can be attached to a chat and read directly.
Sharing copies it into Downloads through MediaStore and opens the share sheet, which needs no
new permission or dependency. Android 9, which has no MediaStore Downloads, saves to the app's
own folder instead.

## 24. One ink per stroke

The POC inks each segment by its own pressure. Pen Lab (a browser page of S Pen tests: range,
steadiness, speed, and game-style prompts with and without live colour) showed what that does
to a real hand on a fast pen. The player's pressure took 110–150 ms to settle after
touchdown (up to 200 ms) and 50–70 ms to fade at lift-off, a third of a 0.45 s rope stroke, and
it rose 1.5–4× along slow lines. So per-segment ink gave almost every stroke light tips and a
heavier tail: in the colour round, where the player aimed with live feedback and hit the
asked band by stroke median 11 times in 12, only 4 of 12 strokes came out a single, right ink.
Trimming the ramps or adding hysteresis barely helped; choosing one ink per stroke from its
settled pressure gave 12 of 12 in both orientations. A stroke's ink is that median, and its
thickness still follows the pen inside the band, so strokes keep their hand-drawn look and the
ink economy (cost grows with thickness) is little changed. Wet ink is laid in the ink the
stroke is heading for and laid again if that changes. Mixed strokes (a bridge with a spring in
its middle) are no longer possible; no stage or tutorial step uses one.

The same data says what this does not fix: which ink a stroke gets still depends on the
tuning. In the player's first session their tuning put rope and spring 25 % apart, and their
play pressure in beam; strokes that passed lessons by containing a little of the right ink
now come out wholly the wrong one, so re-tuning matters more. Pen Lab also found that, without
colour, the player's three inks overlap (14 or 16 of 18 separable at best), while with live
colour they separate cleanly; tuning with colour practice, and separate portrait and landscape
tunings (the player presses about 1.4× harder in portrait), are the next candidates.


## 25. Slice 1: the page fights back

The owner asked for the game to be more engaging, the way Mario was: something happening in
the first ten seconds, a reason to grin every few seconds, a tune to hum. A design pitch offered
three themes and a plan in slices; the owner picked Ink Rush, approved Slice 1 as listed, said
Endless could go, and chose straight into play over a short tuning step.

- **Straight into play.** The first stage not yet cleared starts at once, with no intro card.
  The POC's endless generator stays in `GameSim`, because the reference tests and the physics
  checks against the POC play it, but nothing in the game reaches it.
- **Tuning by playing.** Pen tuning was the first thing a new player saw. Now 1-1's first three
  obstacles each ask for one ink with a guide; while the pen is untuned, a stroke begun there is
  forced to that ink (so a new player always succeeds), and the three strokes' raw settled
  pressures, from a second `SteadyPressure` fed raw samples, feed `PenCalibration.fromLines`
  just as the tuning lines do. The forcing is in the sim (a `Drawing` remembers its gate); the
  learning is in the game, which alone sees raw pressure. If the three don't rise, nothing is
  tuned and the gates ask again next time.
- **Music as a looping sound.** Ink Rush is synthesized like the effects and committed: a
  seamless 11.4 s loop (tails folded onto the start) at 22.05 kHz, 504 KB, played as a looping
  `Sound` rather than streamed `Music`, so it starts in sync and costs no decoder. Android's
  SoundPool holds sounds under 1 MB; a test keeps the file under that.
- **Blots are walkers, not obstacles.** They collide with the world (ground, ink, boulders) but
  not with the runner, and the sim decides what a touch means: a stomp from above or during a
  spring bounce, Bold rolling through, or a lost life. A sweep of where a player's spring lands
  showed a strict "from above" rule turned a 1.5 m stretch of near-misses into a coin flip
  between a stomp and a death, so anything met during a bounce counts as a stomp: a spring
  drawn 3–9 m before an oncoming Blot is always safe.
- **Bold keeps the runner's mass.** A bigger runner of the same weight rolls the same and
  doesn't snap ropes a normal one wouldn't; only its radius (and so its drive target) changes.


## 26. Five worlds of five stages

The owner approved five worlds (The Notebook, The Cellar, Up High, The Castle, Midnight Ink),
each opening with a stage that teaches its new things and ending with a boss, and agreed six
changes to the plan: power-ups change the ink, one idea per stage, no difficulty from finer
pressure, a boss in every world, three hidden gold stars a stage, and fresh-player playtests.
They also asked for each world to have its own music.

- **One registry, one save format.** `Stages.WORLDS` lists each world's number, name, page
  theme, tune and five stages; stage ids stay `world-number`, so saved stars and scores from
  World 1 carry over. Gold stars are a 3-bit mask per stage, added to (never replaced) on each
  clear.
- **Teaching is the same everywhere.** 1-1's first-run waiting became a rule for every world's
  first stage: until it has a star, the runner stops before each new thing until a stroke that
  works is there. Gates gained what later worlds need: an ink that won't do, an ink that must,
  and "squash what's under the guide".
- **Power-ups change the ink, not just the runner.** The marker cap stays (Bold). The others
  last 9 s and change what a stroke is: Highlighter swipes find hidden things, Cloud chalk
  floats, Gold nib ink is free and unbreakable, and the Star nib makes the runner untouchable.
  Cloud chalk replaced the Feather from the pitch: a lighter runner overlapped with what rope
  and springs already do, while ink that floats opens new shapes (a light line across 11 m).
- **The pressure bands never narrow.** Rope, spring and beam are the same three bands in every
  world. Later worlds are harder through layout, speed (3.4 m/s in 1-1 up to 3.9 m/s), what
  walks the page and what chases you, never through finer pressure control a pen and a hand
  can't give reliably.
- **Bosses are walkers in an arena.** An arena is flat ground between two walls; the near one
  closes behind the runner, which then rolls back and forth between them. Each boss is weak
  to one thing the world taught (a bounce, a beam drop, a bounce from below, a spring under
  it). The test bot can't script a fight by position, so `StageBot.fight` reacts to where the
  boss and its creatures are.
- **Music is synthesized per world** with the same `MusicSynth` as Ink Rush (now a small
  theme description: tempo, melody, chords, lead wave, drums, filter, echo, arpeggio), each a
  seamless loop under SoundPool's 1 MB. Only the playing tune is loaded, so six tunes cost no
  more memory than one.
- **Ink pools swallow ink.** A pit that only killed the runner would just be a spike pit in
  another colour. Swallowing ink as well means a bridge has to stay above the surface, and
  islands, sloping bridges and springs up to higher banks make the sea a puzzle of its own.
- **The ink tide is a rubber band.** First built to chase at 90 % of the run speed and never
  fall more than 10 m behind, it never came within 8.5 m on a clean run, off screen in both
  orientations. Now its pace depends on the gap: it settles 5 m behind a runner at full speed,
  runs faster when further back and slows as it closes, so a clean run sees it looming at the
  edge of the screen and a stall of a second and a half is caught. While it is up the camera
  gives it room, as it does a boulder.
- **Fresh-player checks.** Every world's first stage is played on a first run (waiting at
  each new thing) in `StageTest`. A probe of all 25 stages with every scripted stroke drawn 3 m
  later than its script (about 0.8 s less warning) cleared every one; at 5 m late the failures
  were strokes whose script already draws them only 3.5 to 4 m ahead (springs onto creatures,
  a gap bridged after an Eraser falls in), where 5 m late means after the runner is there.
- **A boss flying upwards doesn't carry the runner.** Anything met within a spring bounce
  counts as a stomp, so a runner meeting the Big Smudge as a spring threw it up was stomped
  upwards every step, all the way into the spikes. A boss flying up now brushes the runner
  aside.


## 27. A title screen and a pause screen

Slice 1 opened the game straight into 1-1, so a newcomer had something to do in the first
seconds. On a phone that read the other way: opening the app threw the player straight in,
with nothing to say what it was or how to stop. The owner asked for a greeting and a pause
screen.

- **The title screen is the way in, not a lobby.** One big button picks up at the first stage
  not yet cleared (so a newcomer still starts 1-1 with a single tap, now with its name card
  first); the stage list, How to play and Settings sit under it. The three inks are drawn on
  it, so the one idea of the game is on the first screen.
- **Pausing holds everything.** The sim stops stepping, the music holds its place and the pen
  lays no ink. A stroke half drawn is let go first, so pausing never leaves wet ink hanging.
- **A countdown back in.** The player's pen is off the screen while paused; restarting the
  runner at once would cost them the next obstacle. Three half-second beats give the hand time
  to come back.
- **One button, two jobs.** The top-right button pauses in a stage (two bars) and opens
  Settings elsewhere (⋯), so the HUD doesn't grow; Settings moved into the pause screen, and the
  settings sheet lost its "Stage list" button.
- **Android back is gentle.** In a stage it pauses instead of quitting, on the pause screen it
  resumes, on the stage list it goes to the title, and only the title leaves the app.
- **Recordings** now start on the title screen, so ones made before this don't replay
  identically (they began in a stage).


## 28. An ending, versioned saves, and a runner with a personality

Before playtesting, the owner asked for what would keep the game from feeling unfinished: it
stays S Pen only (said on the title screen and in the README), it gets an ending, a polish pass,
a save version and a smooth boss fight, and the runner becomes a character "like the old
Office paperclip: pure fun". It will be source-available on GitHub (PolyForm Strict: free to
read, build and play, not to change, redistribute or sell), not on Google Play.

- **The ending is earned and skippable.** It follows the last clear card rather than replacing
  it, so the tally still shows; it holds on the clean page and the runner first, then rolls the
  player's totals and the credits. The credits alone are on the title screen.
- **Saves carry a version.** The five worlds reused stage ids 1-2 to 1-4 for new stages. A
  save without a version and without gold-star keys (every clear since the worlds writes one)
  is from before them; only those three stages' results are dropped. Future changes to what a
  saved value means bump the version and add a step.
- **Tunes are loaded before they're needed.** Android's SoundPool loads in the background, so
  a tune loaded when the boss arena closed started late; on desktop the load stalled a frame. A
  stage's tunes now load during its intro card.
- **The runner is a character, not a ball with a face.** The old face was small and the shine
  on the ball turned with the roll. The character is original (an ink blot with googly eyes and
  floating eyebrows, the paperclip's best trick), upright whatever way the ball rolls, and drawn
  by one drawer everywhere it appears. What it feels and says is read from the sim each frame
  and never written back, so recordings still replay exactly. Its remarks are rationed (a
  cooldown, and chance on common events) because the paperclip's real lesson is that a
  helper who talks too much gets turned off; there's a setting for that anyway.
- **Volume, not just on and off.** Sound and music became sliders; the old switches carry over.


## 29. Dot: the runner, and the helper

The owner named the runner Dot and asked for it to welcome players and to explain the
first-run teaching itself, breaking the fourth wall the way the old Office assistant did.

- **It steps out of the page, not onto a card.** At a teaching stop Dot rises off the paper
  towards the player: it grows, its shadow falls on the page below, and the runner left behind
  is a plain dot of ink until it dives back in with a splash. The speech balloon, the pointer
  and the knock on the glass are drawn over the HUD, in the player's space, not the page's.
- **It only comes out when the game is already waiting.** The paperclip was loved for its face
  and hated for interrupting. Dot steps out only at a first-run stop, after a stroke that
  missed, after a long wait, or on the first launch; never in live play. Drawing sends it back
  in (the stroke still draws), and once a world's first stage is cleared its lessons don't
  return.
- **It says what the page already says, plus what to draw.** The stage's own note for the
  obstacle (the last one before it) is quoted in the hand font, then a line for the gate's job
  in plain words (rope, spring, beam, a beam dropped on an Eraser, no springs under spikes), so
  the levels needed no new text. Page notes wait while it talks.
- **The sim only asks; the game decides.** Missed strokes and long waits reach the game as a
  `teach` event (a toast by default, so tests and the tutorial are unchanged); in a first-run
  stage the game has Dot say it.
- **Its corner keeps the drawing area clear.** Dot sits in the top corner whose balloon stays
  clear of the guide (the right one in landscape, where the runner waits on the left).
