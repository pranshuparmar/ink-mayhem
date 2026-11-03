# Ink Mayhem: under the hood

How the game is built, how to build it, and the design choices behind it. For what's in the
game see the [full tour](FEATURES.md); for every gameplay number, [Tuning](TUNING.md).

- [Stack](#stack)
- [Project layout](#project-layout)
- [Building](#building)
- [Architecture](#architecture)
- [Design choices](#design-choices)
- [Tests](#tests)
- [Desktop launcher](#desktop-launcher)
- [Input recordings and replays](#input-recordings-and-replays)
- [Sound and music](#sound-and-music)
- [CI, signing and releases](#ci-signing-and-releases)

## Stack

| Component | Version |
|---|---|
| libGDX (gdx, backend-android, backend-lwjgl3, freetype, box2d) | 1.14.2 |
| Kotlin | 2.4.21 |
| Android Gradle Plugin | 9.4.0 (compiles Kotlin itself, so `:android` applies only `com.android.application`) |
| Gradle | 9.8.2, on JDK 17 |
| compileSdk / targetSdk / minSdk | 37 / 37 / 28 (Android 9) |
| JUnit | 5 (Jupiter) with kotlin-test, tests only |

No other libraries. The app asks for one permission, `VIBRATE`, and makes no network requests
(the repository link opens the browser).

## Project layout

| Path | |
|---|---|
| `core/` | The whole game in plain Kotlin: `physics/` (ink as Box2D bodies), `game/` (the simulation, the stage builder, `World1.kt` … `World5.kt`, `Stages.kt`, the stage session, the tutorial, the bot), `input/` (pen samples, pressure, tuning), `render/` (the page, Dot's face, particles, fonts, palettes), `ui/` (HUD, cards, menus, settings, Dot the helper), `audio/` (the synthesizers and playback), `record/` (input recordings). Headless tests in `core/src/test`. |
| `android/` | `AndroidLauncher` and the S Pen bridge (`AndroidPenInput`), resources, and the shared `assets/` (fonts with their licences, sound effects, music). |
| `lwjgl3/` | The desktop launcher, for development: mouse-as-pen, screenshots, scripted play and replays. |
| `docs/` | This file, the [tour](FEATURES.md) and [Tuning](TUNING.md). |
| `media/` | The README's screenshots and GIF and the repository's social preview, rendered from the game. |

## Building

You need JDK 17 or newer, and for the APK the Android SDK with platform 37 (Android Studio
installs it; or point `ANDROID_HOME` / `local.properties` `sdk.dir` at an SDK).

```sh
./gradlew lint test assembleDebug     # what CI runs; the APK lands in android/build/outputs/apk/debug/
./gradlew :core:test                  # the headless gameplay and physics tests only
./gradlew installDebug                # build and install on a connected device
./gradlew :lwjgl3:run -Pargs="--emulate-pen"   # play on the desktop with the mouse as a pen
```

Without an Android SDK, Gradle leaves the `:android` module out, so `core`, the tests and the
desktop launcher build anywhere. `-Pinkmayhem.android=false` leaves it out explicitly.

Debug builds are **Ink Mayhem Debug** (`com.pranshuparmar.inkmayhem.debug`) and install beside
the release app (`com.pranshuparmar.inkmayhem`). Release builds aren't minified: size doesn't
matter here, and R8 would need keep rules for Box2D's JNI callbacks.

## Architecture

**One game, three shells.** `core` holds everything that decides what happens and how it looks.
`android` supplies the window, the pen and the platform services (preferences, vibration,
opening links, sharing a recording); `lwjgl3` supplies the same on the desktop. Everything the
game reads from outside comes through one seam (`FrameSource` for the clock, screen and system
flags; the pen queue for samples; `Platform` for the rest), which is what makes recordings
replay exactly.

**Input.** `AndroidLauncher` overrides `dispatchTouchEvent` / `dispatchGenericMotionEvent`, so
nothing reaches libGDX's own `Input`. Every sample, with all its historical points, goes into a
lock-free single-producer, single-consumer ring of primitive arrays (`PenQueue`) that the GL
thread drains each frame, so the input path allocates nothing. The game does its own
hit-testing: fingers may press buttons but never draw. The side button is read from
`BUTTON_STYLUS_PRIMARY` and also `BUTTON_SECONDARY` (some Samsung firmware reports the S Pen's
there); `TOOL_TYPE_ERASER` erases too. Pen phases add `HOVER_EXIT` and `CANCEL` to hover, down,
move and up, so the game knows when the nib leaves or a gesture is aborted.

**Pressure.** Raw pressure goes through the player's tuning (`PenCalibration`: piecewise
linear, from the pressures of their light, normal and hard lines) to the game's 0..1, whose
bands are rope below 0.33, spring below 0.62, beam above. A stroke takes one ink, from the
median of its tuned pressure once the pen has landed (`SteadyPressure`).

**Physics.** `InkPhysics` turns a stroke into a chain of Box2D bodies, resampled every 0.3 m,
joined by welds (springs are soft welds). Each joint's load is the filtered reaction force (plus
torque, except for rope), and a joint breaks when its load passes its strength (2400 × the
stroke's thickness). A stroke's ends hook to the ground or a ceiling where its ink touches it.
World units are metres, y down, gravity 10.

**The simulation.** `GameSim` is the game's rules: the runner, ink, creatures, power-ups,
bosses, scoring and deaths, on a fixed 1/60 s tick (at most four per frame). Stages are written
with a small builder (`StageBuilder`: flat, slope, gap, wall, climb, slide, lift, seesaw,
creatures, items, checkpoint, finish…) into plain data (`StageLayout`), which `GameSim.loadStage`
turns into physics. A `StageSession` runs lives, intros, retries from the checkpoint, game over
and the clear tally, each stage on its own `GameSim`. The tutorial runs on another, in sandbox
mode (hand-built terrain), so nothing in the game loop needs a tutorial branch beyond choosing
which sim to tick, draw and feed input.

**Rendering.** Each tick snapshots the camera and every body; frames between ticks draw at
`alpha = accumulator / step`, so motion is smooth on 120 Hz screens and gameplay is identical at
any refresh rate. Pen samples map to the world through the same interpolated camera the player
sees. The HUD, cards, menus and settings are one small immediate-mode UI drawn with
`ShapeRenderer` and FreeType bitmap fonts, laid out in dp; with all input already coming through
the pen router, scene2d would add a skin and a second input path for nothing. Hot loops are
index-based, fonts are cached by a primitive key, particles and pops are pooled and HUD strings
are rebuilt only when their values change, so drawing doesn't feed the garbage collector.

**Dot.** Its face (`render/Buddy.kt`) and what drives it in a stage (`render/Persona.kt`) only
read the simulation and never write to it, so the character can't change how a run plays and
recordings still replay exactly. Dot the helper (`ui/DotHelper.kt`) and the lesson card
(`ui/TutorialCard.kt`) are drawn in screen space, over the HUD.

**Saves.** libGDX `Preferences` hold settings, the pen tuning and each stage's stars, best score
and gold stars. They carry a version (`saveVersion`); a change to what a saved value means bumps
it and adds a migration step.

**Orientation.** `screenOrientation="fullUser"`: all four orientations from the sensor, unless
the player has locked rotation (`fullSensor` would ignore the lock, which is hostile while
holding a pen). Rotation, resizes and dark-mode switches are handled in-process, so a run
survives them.

## Design choices

- **Box2D 2.3, not v3.** `gdx-box2d 1.14.2` wraps the classic Box2D 2.3, with joint reaction
  forces, weld frequency and damping, iteration counts, bullets and group filtering: exactly the
  model the ink was tuned on. The newer `3.x` binding wraps Box2D v3 (a different solver, no
  `getReactionForce`), which would mean retuning every joint.
- **One ink per stroke.** Measured on a real S Pen, pressure takes 110–150 ms to settle after
  the pen lands, 50–70 ms to fade at lift-off, and rises along slow lines. Inking each segment
  by its own pressure gave nearly every stroke light tips and a heavy tail; taking one ink from
  the settled pressure made aimed strokes come out as the ink intended. Thickness still follows
  the pen inside that ink's band, so strokes keep their hand-drawn look.
- **Wet ink.** A stroke becomes real physics on pen-up, so a stroke still being drawn is laid as
  static "wet" pieces that only the runner collides with, swapped for the real stroke on pen-up.
  Guards keep holding the pen from beating lifting it: only strokes begun where they'll be
  pinned are laid, wet rope stops where a finished rope would give way, everything else passes
  through wet ink, and a hazard crossed on half-drawn ink scores nothing.
- **Hooks need a surface.** A stroke end hooks only where its ink touches a surface, so a line
  drawn in mid-air falls instead of hanging from nothing.
- **Learn the player's touch first.** Pressure is personal: one player's writing is another's
  light touch. The first launch's lessons begin by asking for three lines, light, normal and
  hard, and tune the pen from them, so the lessons and the game read the same hand the same way.
  Tuning maps the three pressures to the middles of their bands, with the band edges at the
  geometric means between them, since pressure steps feel like ratios. A pen that can't tell
  them apart moves on untuned after a few tries rather than trapping the player.
- **The pressure bands never narrow.** Rope, spring and beam are the same three bands in every
  world. Later worlds get harder through layout, speed (3.4 m/s in 1-1 up to 3.9 m/s), what
  walks the page and what chases you, never through finer pressure control than a pen and a
  hand can give reliably.
- **Lives with checkpoint retries.** A death restarts from the midway bookmark with the score,
  combo, ink and judgements saved there, so a retry can't farm points or stars; game over
  restarts the stage, not the world.
- **Stars reward the game's own skill**: clear it, don't die, and use the lightest ink that
  works.
- **Dot only comes out when the game is already waiting.** A helper that interrupts gets turned
  off. Dot steps out at a first-run stop, after a stroke that missed, after a long wait, or to
  present a lesson, never in live play, and drawing sends it back in. Its remarks are rationed,
  and there's a setting for them.
- **The sim asks; the game decides.** Missed strokes and long waits reach the game as a `teach`
  event; in a first-run stage the game has Dot say it, elsewhere it's a toast.
- **The ink tide is a rubber band.** Its pace depends on how far behind it is: it settles 5 m
  behind a runner at full speed and catches one that stalls for about a second and a half, so a
  clean run sees it looming at the edge of the screen.
- **Bosses are walkers in a closed arena**, each weak to one thing its world taught. Walls and a
  roof seal the room and the finish waits for the win, so there is no way round a boss. The test bot
  can't script a fight by position, so it reacts to where the boss and its creatures are.
- **Music as looping sounds.** Each tune is a seamless loop under 1 MB, played as a looping
  `Sound` rather than streamed `Music`, so it starts in sync and costs no decoder; only the
  playing tune (and, during a boss stage's intro, the boss theme) is loaded.

## Tests

`./gradlew :core:test` runs about 240 headless tests through the real pen-input path, with no
window:

| Behaviour | Test |
|---|---|
| Rope holds 0.25 kg and snaps under 1.9 kg; spring holds 1.9 kg and snaps under 9.7 kg; beam holds 9.7 kg (a 0.5 m crate dropped 2 m onto a 5 m bridge, at light, middle and heavy pressure in each band) | `InkStrengthTest` |
| The runner crosses bridges up to about 5.7 m; a boulder falls through rope; an 8 m beam carries the runner and a boulder; springs launch boulders; a 7.6 m rope snaps and a 6 m one doesn't | `RunnerBehaviourTest` |
| Ends hook only where their ink touches | `HookTest` |
| One ink per stroke from its settled pressure | `OneInkStrokeTest`, `SteadyPressureTest` |
| Wet ink: crossing a stroke still being drawn, the swap on pen-up, the guards | `WetInkTest` |
| The tunnel rock: safe on top and at the sides, spiked underneath, never bounced over | `TunnelTest` |
| Every stage clears with its scripted solution, no deaths and only clean judgements; wrong ink or no ink fails where it should; first runs wait at each new thing | `StageTest`, `World1Test`, `World5Test`, `ProbeStageTest` |
| Bosses: each falls to its weakness | `BossTest` |
| Lives, retries from the checkpoint, game over, stars, unlocks, gold stars | `StageSessionTest` |
| The lessons: the touch lesson tunes the pen from rising lines (and moves on for a pen that can't tell them apart); each lesson passes with the right ink, fails with a hint with the wrong one; the whole tutorial plays through | `TutorialTest` |
| Pen tuning and pressure mapping | `PenInputTest` |
| Settings and save migration | `SettingsTest`, `SaveVersionTest` |
| Recordings write and read back exactly | `InputLogTest` |
| The committed sounds and music match their synthesizers, and each tune fits SoundPool | `AssetsTest` |

Two things worth knowing when writing physics scenarios: a crate set down gently is held by
rope up to 1.9 kg, and the snapping thresholds appear when it falls about 2 m onto the bridge,
so the tests drop it from 2 m; and a beam drawn lying on the ground before a gap makes a step
taller than the runner's radius, so the runner gets stuck on it. Bridges are drawn edge to edge.

## Desktop launcher

```sh
./gradlew :lwjgl3:run -Pargs="--emulate-pen --stage 2-1"
```

| Option | |
|---|---|
| *(none)* | The mouse is a mouse: like a finger on Android, it only draws with the debug setting **Allow finger and mouse** on. |
| `--emulate-pen` | The mouse acts as an S Pen. Keys `1` / `2` / `3` give light / normal / hard pressure; the right button is the side button. |
| `--autoplay` | Each stage's scripted solution plays it, then the next. |
| `--wrong-ink ROPE=SPRING` | The bot draws the second ink wherever its solution has the first, to reach a death. |
| `--stage 1-2`, `--select` | Start straight into a stage (with `--autoplay`, its solution plays it), or open the stage list. |
| `--tutorial N` | Open the lessons at lesson N (1–5). |
| `--settings`, `--calibration` | Open the settings sheet or pen tuning at start. |
| `--demo-tune N` | With `--calibration`: draws N tuning lines with a light pen's pressures (4 adds a free stroke). |
| `--pause-at S` | Pause the stage S seconds in. |
| `--ending`, `--credits` | Open the ending (with your totals) or just the credits. |
| `--faces` | A gallery of Dot's expressions. |
| `--landscape`, `--size WxH`, `--density D` | Window size in dp and pixels per dp. |
| `--dark`, `--reduced-motion` | Simulate the system settings. |
| `--speed N` | Run the game N times faster. |
| `--rotate-after S` | Swap width and height after S seconds, like rotating the phone. |
| `--toast "text"` | Show a toast at start. |
| `--screenshot out.png --after S` | Save a screenshot after S seconds and quit. |
| `--record out.txt` | Record this session's input, as debug builds do on the phone. |
| `--replay rec.txt` | Play a recording back exactly (below). |

The desktop keeps its own save (libGDX's desktop preferences, `~/.prefs/ink-mayhem`).

## Input recordings and replays

Debug builds record every session's input from launch: each frame's clock and time step, every
pen sample (position, pressure, tilt, buttons), resizes, insets, pauses, the back key and the
preferences at launch, plus readable notes on what happened (strokes and their ink, the lessons'
hints, deaths, toasts, pen tuning) and a probe of the runner every eighth of a second. Settings
→ **Record input** (debug builds only, on by default) turns it off at once; turned back on, it
starts at the next launch, since a replay has to start from one. **Share input recording** saves
a copy to Downloads and opens the share sheet. The last five sessions are kept. A recording holds
the device model, Android version, app version and settings at launch, and nothing else. Release
builds don't record.

On the desktop, `--replay rec.txt` plays a recording back exactly, printing what happened.
Between a phone's ARM chip and a desktop, Box2D's floating point can round differently and
spring bounces amplify the difference, so a replay puts the runner back wherever a probe
differs (`--no-resync` shows the raw drift). Add `--frames dir --every N --from S --to S` to save
every Nth frame as a PNG (`ffmpeg -framerate 30 -i dir/f%06d.png -pix_fmt yuv420p clip.mp4`
makes a video), `--screenshot out.png --after S` for one frame, and `--replay-height H` for the
window size.

## Sound and music

The sound effects (`SfxSynth`: oscillators with exponential frequency and gain ramps, filtered
noise) and the six tunes (`MusicSynth`: a small description per theme of tempo, melody, chords,
lead wave, drums, filter, echo and arpeggio, rendered to a seamless loop at 22.05 kHz) are
synthesized offline and committed as WAVs in `android/src/main/assets/`. After changing either,
regenerate them:

```sh
./gradlew :core:generateSfx
```

`AssetsTest` fails if the committed files drift from their synthesizers, and keeps every tune
under SoundPool's 1 MB.

## CI, signing and releases

| Trigger | Workflow | Output |
|---|---|---|
| Push to any branch other than `main`, pull request | `ci.yml`: `./gradlew lint test` | Pass or fail |
| Push to `main` | `ci.yml`: `./gradlew lint test assembleDebug` | The debug APK as a workflow artifact, and on the [`latest-debug`](https://github.com/pranshuparmar/ink-mayhem/releases/tag/latest-debug) prerelease |
| **Run workflow** on any branch | `ci.yml`, as for `main` | The debug APK as a workflow artifact |
| **Run workflow** on **Release** (manual only, from `main`) | `release.yml` | A `vX.Y.Z` tag and a GitHub Release with the signed APK |

### Debug signing

Every debug APK must carry the same key, or Android won't install a new one over the last.
Without any setup, CI makes a debug key once on `main` and keeps it in the GitHub Actions cache;
other branches use the same key. The cache drops it after 7 days without a build, and the next
APK then needs one uninstall. For a key that never changes, add it as the
`ANDROID_DEBUG_KEYSTORE_BASE64` secret. It needs no Java: OpenSSL (built into macOS and Linux,
and in Git Bash on Windows) makes the keystore:

```sh
export MSYS_NO_PATHCONV=1    # Git Bash only: keeps "/CN=..." from being read as a path
openssl req -x509 -newkey rsa:2048 -nodes -days 10000 \
  -subj "/CN=Android Debug/O=Android/C=US" -keyout debug-key.pem -out debug-cert.pem
openssl pkcs12 -export -inkey debug-key.pem -in debug-cert.pem \
  -name androiddebugkey -passout pass:android -out debug.keystore
rm debug-key.pem debug-cert.pem
base64 < debug.keystore | tr -d '\n' > debug.keystore.b64
```

(With a JDK, `keytool -genkeypair -keystore debug.keystore -storetype PKCS12 -alias
androiddebugkey -storepass android -keypass android -keyalg RSA -keysize 2048 -validity 10000
-dname "CN=Android Debug,O=Android,C=US"` makes the same.) Paste the contents of
`debug.keystore.b64` into a new repository secret named `ANDROID_DEBUG_KEYSTORE_BASE64`, or
`gh secret set ANDROID_DEBUG_KEYSTORE_BASE64 < debug.keystore.b64`. The first APK signed with it
needs one uninstall of the debug app; every one after installs over the last.

### Release signing

Release builds are signed in CI from four repository secrets. Never commit a keystore (`*.jks`,
`*.keystore` and `*.p12` are git-ignored).

1. Create the release keystore once, and back up the file and its password: every future update
   must be signed with it. With OpenSSL (no Java needed; in Git Bash run
   `export MSYS_NO_PATHCONV=1` first), the second command asks for the password:

   ```sh
   openssl req -x509 -newkey rsa:4096 -nodes -days 10000 \
     -subj "/CN=Pranshu Parmar" -keyout release-key.pem -out release-cert.pem
   openssl pkcs12 -export -inkey release-key.pem -in release-cert.pem \
     -name ink-mayhem -out ink-mayhem-release.jks
   rm release-key.pem release-cert.pem
   ```

   Or with a JDK: `keytool -genkeypair -v -keystore ink-mayhem-release.jks -storetype PKCS12
   -alias ink-mayhem -keyalg RSA -keysize 4096 -validity 10000`.

2. Base64-encode it: `base64 < ink-mayhem-release.jks | tr -d '\n' > ink-mayhem-release.jks.b64` (Windows PowerShell:
   `[Convert]::ToBase64String([IO.File]::ReadAllBytes("ink-mayhem-release.jks")) | Out-File -Encoding ascii ink-mayhem-release.jks.b64`).
3. Add the secrets under **Settings → Secrets and variables → Actions**, then delete the `.b64`
   file:

   | Secret | Value |
   |---|---|
   | `ANDROID_KEYSTORE_BASE64` | the contents of `ink-mayhem-release.jks.b64` |
   | `ANDROID_KEYSTORE_PASSWORD` | the keystore password |
   | `ANDROID_KEY_ALIAS` | `ink-mayhem` |
   | `ANDROID_KEY_PASSWORD` | the same password (PKCS12 uses one) |

### Cutting a release

Releases are made by hand, never automatically: on GitHub open **Actions → Release → Run
workflow** (the GitHub mobile app has the same button), leave **Use workflow from** on `main`,
and pick what to bump:

| Input | |
|---|---|
| **bump** | `patch` (1.0.0 → 1.0.1), `minor` (→ 1.1.0) or `major` (→ 2.0.0), from the last stable release. The first release is 1.0.0 whatever is picked |
| **version** | or an exact version instead, such as `1.2.0` or `1.3.0-beta.1` (a `-suffix` makes it a prerelease); leave it empty to bump |

The workflow checks the four signing secrets (naming any that are missing) and that the version
isn't taken, runs lint and the tests, builds and signs the APK and verifies the signature. Only
then does it create the `vX.Y.Z` tag on the commit it built and publish a GitHub Release with
`ink-mayhem-X.Y.Z.apk` and generated notes, so a failed run leaves no tag behind.

- `versionName` is the version (`1.2.3-beta.1` stays as it is).
- `versionCode` is `major × 1 000 000 + minor × 1 000 + patch`, so later versions always upgrade
  (minor and patch stay below 1000; a prerelease shares its version's code). Debug builds from CI
  use the CI run number.
