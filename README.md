# Ink Mayhem

An S Pen-only Android game in the Mayhem series. A blot of ink rolls across a ruled page and
you draw the way ahead. How hard you press decides what the ink becomes:

> **S Pen only.** Everything is drawn with a pressure-sensitive pen: a Galaxy phone or tablet
> with an S Pen. Fingers can tap buttons, but they never draw. Ink Mayhem is open source and isn't
> on Google Play: get the APK from this repository's [Releases](../../releases), or build it
> yourself.

| Pressure | Ink | |
|---|---|---|
| Light touch | **Rope** | Nearly free. Holds the runner, drops boulders, snaps over long gaps. |
| Normal writing | **Spring** | Bounces you (and boulders) high. Fatal under spiked ceilings. |
| Press hard | **Beam** | Spans anything, carries anything, costs far more ink. |

The game is **25 hand-built stages in five worlds**, each stage with lives, a checkpoint, a
finish flag, up to three stars and three hidden **gold stars**. Each world's first stage teaches
what's new in it, each stage after that is built round one idea, and each world ends with a boss:

| World | Page | What's new | Power-up | Boss | Music |
|---|---|---|---|---|---|
| 1 The Notebook | ruled paper | rope, spring and beam; hills and seesaws; Blots | marker cap (Bold) | **The Big Blot**: bounce on it | Ink Rush |
| 2 The Cellar | graph paper underground | low roofs, spiked tunnels, slides, lifts; Erasers; inkwells down to bonus pages | Highlighter | **The Giant Eraser**: drop beams on it | Cellar |
| 3 Up High | dot grid in the sky | floating islands, bouncy clouds; paper planes | Cloud chalk | **The Paper Airliner**: bounce up into it | Sky |
| 4 The Castle | brickwork | boulders, crumbling bricks; ink beetles | Gold nib | **The Beetle Queen**: bounce on her | Castle |
| 5 Midnight Ink | a dark, ink-soaked page | ink pools, the ink tide; every creature at once | Star nib | **The Big Smudge**: spring it into its own spiked roof | Midnight |

The page fights back and pays out:

- **Star trails** mark the good line, over a spring's bounce or along a bridge. Stars taken
  close together ring a note higher each; every 50 is an extra life.
- **Creatures** walk the page. **Blots** waddle at you: bounce on one with a spring, or drop a
  beam on it. **Erasers** rub out rope and springs they touch and are too rubbery to stomp; a
  dropped beam squashes them. **Ink beetles** hop up to rope and springs and eat them; beams
  they can't bite. Touching any of them otherwise costs a life.
- **Doodle boxes** hang overhead. Bounce up into one and it pops a power-up. The **marker cap**
  makes the runner **Bold**: bigger, rolling creatures flat and surviving one hit. The others
  change your ink for 9 s: the **Highlighter** turns strokes into swipes that show hidden
  ledges, boxes, stars and inkwells; **Cloud chalk** lines float where you draw them, so even a
  light one holds across a long gap; **Gold nib** ink is free, never snaps and beetles can't
  bite it; with the **Star nib** nothing hurts you.
- **Gold stars**, three a stage, sit off the obvious line: at the top of a risky bounce, on a
  high road, on a bonus page, or hidden until a highlighter finds them. The stage list shows
  which ones you have.
- **Ink pools** swallow anything that touches them, ink included, so a bridge has to stay
  above them. The **ink tide** rises behind you and chases you: keep rolling and it hangs back,
  stall and it catches you.
- The runner is a **character**: a blot of ink with googly eyes, eyebrows floating over them
  and a face for every moment. It grins off a spring, wobbles on rope, struts on a beam, smirks
  and winks after a stomp, sweats with a boulder or the tide behind, watches your pen while you
  draw, and dozes off if you keep it waiting. Now and then it says something in a speech bubble
  (Settings → **Runner's remarks** turns that off). It greets you on the title screen with tips,
  peeks over the pause screen, and is there at the end.
- **The end**: beat the Big Smudge and the page comes clean, your notebook is tallied (stages,
  stars, gold stars, scores) and the credits roll. **Credits** is also on the title screen.
- **Music**: an original chiptune theme for each world and one for the boss fights; the
  jingles come from Ink Rush, the first world's.

Clear each hazard with the lightest ink that works to grow the combo. Ink is solid the moment
it's drawn, so the runner can roll onto a stroke you're still finishing. Hover to see where the
nib will land; hold the side button while touching to erase. Fingers don't leave ink.

It started as a native port of the playtested HTML proof of concept in
[`reference/ink-mayhem-poc.html`](reference/ink-mayhem-poc.html); a few rules have since changed
from it after playtesting on a phone. See [`docs/tuning.md`](docs/tuning.md) for every gameplay
number and every change from the POC, and [`docs/decisions.md`](docs/decisions.md) for the
design notes.

## Install a build

Ink Mayhem isn't on Google Play: install it from an APK.

**Debug builds**: every push to `main` replaces the APK on the
[`latest-debug`](../../releases/tag/latest-debug) prerelease (`ink-mayhem-debug-<sha>.apk`).

**Releases**: every `v*` tag publishes a signed `ink-mayhem-<version>.apk` on the
[Releases](../../releases) page.

To sideload on the phone:

1. Open the release page in the phone's browser and download the APK.
2. Open it. When Android asks, allow the browser (or Files) to install unknown apps, then
   install.

Or from a computer with USB debugging on: `adb install -r ink-mayhem-debug-<sha>.apk`.

The debug app is called **Ink Mayhem Debug** (`com.pranshuparmar.inkmayhem.debug`) and installs
alongside the release app (`com.pranshuparmar.inkmayhem`). A new debug APK only installs over the
previous one if both were signed with the same debug key. CI keeps one (see
[Debug signing](#debug-signing)); if Android still says the app conflicts, uninstall the old one
first, which clears its best score, calibration and tutorial progress.

The game opens on its **title screen**: the name, a reminder of the three inks, and **Start**
(**Play** once something is cleared, naming the stage it picks up at: the first not yet
cleared), **Stages**, **How to play**, **Settings** and **Credits**, with a reminder that it's S Pen
only. The stage list has a **Menu** button
back to it. In a stage the button at the top right **pauses**: Resume (the runner is counted
back in, 3‑2‑1), Restart, Settings or Stage list. Android back pauses too (and on the pause
screen resumes), and so does leaving the app. The title and the stage list play Ink Rush.

Each stage opens with its name and the lives left. A short handwritten note introduces each new thing
just before it. The first three obstacles each ask for one ink with a dotted guide and a word:
a rope gap, a spring wall and a beam chasm. On a first run (until 1-1 has been cleared once)
the runner stops before each new thing, those three, the first Blot and the doodle box, and
waits until a stroke that will work is there; a miss is wiped, with a tip. Notes past the next
stop stay hidden, so one thing comes at a time. Every world's first stage does the same for
that world's new things on its first run (the ink tide holds still while the runner waits). Until the pen has been tuned, a stroke drawn at
one of the three is that ink whatever the pressure, and the pressures of those three strokes
**tune the pen** (if they rise from rope to beam). Each stroke becomes
one ink, from the pressure it settles at once the pen has landed. **Settings** (on the title,
the pause screen, or ⋯ on the stage list) has volume sliders for **Sound effects** and
**Music**, **Runner's remarks**, **Tune your pen** (draw a rope, a spring and a
beam line along a guide, then try a few strokes), **How to play** (four short lessons on rope,
spring, beam and erasing), and the **Pen readout** (tool type, raw and normalized
pressure, buttons, tilt, hover and event rates) for checking the S Pen. The **stage list** is a
notebook with a tab per world: every world's tab opens, even a locked one (a padlock marks it),
so you can read its stage names before you get there, though only open stages play. Behind
the list, the world on the tab scrolls slowly past on its own page, to its own tune.

### Sending a recording

Debug builds record every session's input from launch (Settings → **Record input**, on by default
and shown only in debug builds; off stops recording at once, on starts at the next launch): each frame's clock and every pen sample
(position, pressure, tilt, buttons), plus notes on what happened (strokes, the tutorial's hints,
deaths, toasts, pen-tuning lines). When something goes wrong, open Settings → **Share input
recording** (shown only while recording): it saves a copy to **Downloads** and opens the share
sheet, so you can attach it in a chat or send it anywhere. The last five sessions are kept in the app's storage. A
recording holds the device model, Android version, app version and settings at launch, and
nothing else about you. Release builds don't record.

A recording replays on the desktop exactly as it was played (see `--replay` below), so it can
be stepped through, rendered to video, and turned into a test.

## Build

Requirements: JDK 17 or newer, and the Android SDK with platform 37 for the APK (Android Studio
installs it; or point `ANDROID_HOME` / `local.properties` `sdk.dir` at an SDK).

```sh
./gradlew lint test assembleDebug     # what CI runs; APK in android/build/outputs/apk/debug/
./gradlew :core:test                  # headless physics and gameplay tests only
./gradlew installDebug                # build and install on a connected device
```

Without an Android SDK, Gradle skips the `:android` module, so `core` and the desktop launcher
still build. `-Pinkmayhem.android=false` skips it explicitly.

### Desktop launcher

For fast iteration with a mouse (`lwjgl3` module):

```sh
./gradlew :lwjgl3:run -Pargs="--emulate-pen"
```

| Option | |
|---|---|
| *(none)* | The mouse is a mouse: like a finger on Android, it only draws with **Allow finger and mouse** on (Settings). |
| `--emulate-pen` | The mouse acts as an S Pen. Keys `1` / `2` / `3` give light / normal / hard pressure; the right button is the side button. |
| `--autoplay` | Each stage's scripted solution plays it, then the next. |
| `--wrong-ink ROPE=SPRING` | The bot draws the second ink wherever its solution has the first, to reach a death. |
| `--landscape`, `--size WxH`, `--density D` | Window size in dp and pixels per dp. |
| `--dark`, `--reduced-motion` | Simulate the system settings. |
| `--settings`, `--calibration` | Open the settings sheet or pen tuning at start. |
| `--demo-tune N` | With `--calibration`: draws N tuning lines with a light pen's pressures (4 adds a free stroke). |
| `--tutorial N` | Open the tutorial at lesson N (1–4). |
| `--stage 1-2`, `--select` | Start straight into a stage (with `--autoplay`, its scripted solution plays it), or open the stage list, instead of the title screen. |
| `--pause-at S` | Pause the stage S seconds in (screenshots of the pause screen). |
| `--ending`, `--credits` | Open the ending (with your totals) or just the credits. |
| `--faces` | A gallery of the runner's expressions, for tuning its face. |
| `--speed N` | Run the game N times faster (to screenshot late moments). |
| `--rotate-after S` | Swap width and height after S seconds, like rotating the phone mid-run. |
| `--screenshot out.png --after S` | Save a screenshot after S seconds and quit. |
| `--toast "text"` | Show a toast at start (to check how long ones lay out). |
| `--record out.txt` | Record this session's input, as debug builds do on the phone. |
| `--replay rec.txt` | Play a recording back exactly, printing what happened and checking it doesn't drift. Add `--frames dir --every N --from S --to S` to save every Nth frame between two times as PNGs (`ffmpeg -framerate 30 -i dir/f%06d.png -pix_fmt yuv420p clip.mp4` makes a video), `--screenshot out.png --after S` for one frame, `--replay-height H` for the window size, `--no-resync` to see raw drift (below). |

### Sound effects

The sounds are synthesized from the POC's WebAudio recipes (and, for the new ones, from the Ink
Rush motif) and committed as WAVs; so is the music (`MusicSynth`: Ink Rush, Cellar, Sky, Castle,
Midnight and Boss, each one seamless loop at 22.05 kHz, under 1 MB). After changing either,
regenerate them (a test fails if they drift):

```sh
./gradlew :core:generateSfx
```

### Reference harness

[`reference/harness`](reference/harness) runs the POC's own physics on planck.js 1.5.0, the
engine the POC shipped with, to compare against the Kotlin port:

```sh
cd reference/harness && npm install && npm run all
```

## Signing

Release builds are signed in CI from four repository secrets. Never commit a keystore (`*.jks`,
`*.keystore` and `*.p12` are git-ignored).

### 1. Create the release keystore (once; keep it safe)

```sh
keytool -genkeypair -v \
  -keystore ink-mayhem-release.jks -storetype PKCS12 \
  -alias ink-mayhem -keyalg RSA -keysize 4096 -validity 10000
```

keytool asks for a password and your name and organisation. PKCS12 keystores use one password for
the store and the key. **Back up the file and the password**: every future update must be signed
with this key.

### 2. Base64-encode it

```sh
# macOS
base64 -i ink-mayhem-release.jks -o ink-mayhem-release.jks.b64
# Linux
base64 -w0 ink-mayhem-release.jks > ink-mayhem-release.jks.b64
# Windows (PowerShell)
[Convert]::ToBase64String([IO.File]::ReadAllBytes("ink-mayhem-release.jks")) | Out-File -Encoding ascii ink-mayhem-release.jks.b64
```

### 3. Add the secrets

In GitHub: **Settings → Secrets and variables → Actions → New repository secret**:

| Secret | Value |
|---|---|
| `ANDROID_KEYSTORE_BASE64` | contents of `ink-mayhem-release.jks.b64` |
| `ANDROID_KEYSTORE_PASSWORD` | the keystore password |
| `ANDROID_KEY_ALIAS` | `ink-mayhem` |
| `ANDROID_KEY_PASSWORD` | the same password (PKCS12) |

Or with the GitHub CLI:

```sh
gh secret set ANDROID_KEYSTORE_BASE64 < ink-mayhem-release.jks.b64
gh secret set ANDROID_KEYSTORE_PASSWORD
gh secret set ANDROID_KEY_ALIAS --body ink-mayhem
gh secret set ANDROID_KEY_PASSWORD
```

Then delete the `.b64` file.

### Debug signing

Debug APKs from CI must all carry the same key, or Android won't install a new one over the last.
Without any setup, CI makes a debug key once on `main` and keeps it in the GitHub Actions cache;
builds of other branches (including **Run workflow**) use the same key. The cache drops it after
7 days without a build, and the next APK then needs one uninstall. For a key that never changes,
add it as a secret:

```sh
keytool -genkeypair -v -keystore debug.keystore -storetype PKCS12 \
  -alias androiddebugkey -storepass android -keypass android \
  -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Android Debug,O=Android,C=US"
base64 -w0 debug.keystore > debug.keystore.b64     # macOS: base64 -i debug.keystore -o debug.keystore.b64
gh secret set ANDROID_DEBUG_KEYSTORE_BASE64 < debug.keystore.b64
```

## Cut a release

```sh
git tag v0.1.0
git push origin v0.1.0
```

The **Release** workflow checks the four secrets (it fails with a message naming any that are
missing), runs lint and tests, builds and signs the APK, verifies the signature, and creates a
GitHub Release for the tag with `ink-mayhem-0.1.0.apk` and auto-generated notes.

- `versionName` is the tag without the `v` (`v1.2.3-beta.1` → `1.2.3-beta.1`, published as a
  prerelease).
- `versionCode` is `major × 1 000 000 + minor × 1 000 + patch`, so later tags always upgrade.
  Minor and patch must stay below 1000.

## CI

| Trigger | Workflow | Output |
|---|---|---|
| Push to any other branch, pull request | `ci.yml`: `./gradlew lint test` | Pass or fail; no APK |
| Push to `main` | `ci.yml`: `./gradlew lint test assembleDebug` | The debug APK as a workflow artifact; moves the `latest-debug` tag and replaces the prerelease's APK |
| **Run workflow** on any branch | `ci.yml` (manual), the same as `main` | The debug APK as a workflow artifact (and `latest-debug` when run on `main`) |
| Tag `v*` | `release.yml` | Signed release APK on a GitHub Release |

APKs are built automatically only for `main`. To get one for another branch (from a phone too):
on GitHub open **Actions → CI → Run workflow**, pick the branch and run it. The APK is under **Artifacts** on the finished run's page (a zip holding
the `.apk`); a run on `main` also refreshes `latest-debug`.

## Project layout

| Path | |
|---|---|
| `core/` | Pure Kotlin game: physics (`physics/`), simulation and the stages (`game/`: `GameSim`, `Stage.kt` for the builder, `World1.kt` … `World5.kt`, `Stages.kt` for the list of worlds), pen input model (`input/`), rendering (`render/`), UI (`ui/`), audio (`audio/`). Headless tests in `core/src/test`. |
| `android/` | `AndroidLauncher` and the S Pen bridge (`AndroidPenInput`), resources, and the shared `assets/` (fonts with their OFL licences, sounds). |
| `lwjgl3/` | Desktop launcher. |
| `reference/` | The HTML proof of concept and the Planck.js harness. |
| `docs/` | `tuning.md` (constants, retunes, POC notes) and `decisions.md` (ADRs). |

## Licence

Copyright (c) 2026 Pranshu Parmar. All rights reserved. See [LICENSE](LICENSE).

Fonts: [Kalam](https://fonts.google.com/specimen/Kalam),
[Atkinson Hyperlegible](https://fonts.google.com/specimen/Atkinson+Hyperlegible) and
[IBM Plex Mono](https://fonts.google.com/specimen/IBM+Plex+Mono), under the SIL Open Font License
1.1 (licence texts in `android/src/main/assets/fonts/licenses/`).
