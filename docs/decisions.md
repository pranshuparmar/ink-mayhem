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
