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

Debug builds use `applicationIdSuffix = ".debug"` and the label "Ink Mayhem Debug". CI signs debug
APKs with an optional fixed key (`ANDROID_DEBUG_KEYSTORE_BASE64`) so each `latest-debug` APK
installs over the previous one; without it every CI run has a fresh throwaway key. Release builds
are not minified: APK size doesn't matter here, and R8 would need keep rules for Box2D's JNI
callbacks.

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
