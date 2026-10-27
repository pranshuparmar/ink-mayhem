# Ink Mayhem

An S Pen-only Android game in the Mayhem series. A blot of ink rolls across an endless ruled page
and you draw the way ahead. How hard you press decides what the ink becomes:

| Pressure | Ink | |
|---|---|---|
| Light touch | **Rope** | Nearly free. Holds the runner, drops boulders, snaps over long gaps. |
| Normal writing | **Spring** | Bounces you (and boulders) high. Fatal under spiked ceilings. |
| Press hard | **Beam** | Spans anything, carries anything, costs far more ink. |

Clear each hazard with the lightest ink that works to grow the combo. Ink is solid the moment
it's drawn, so the runner can roll onto a stroke you're still finishing. Hover to see where the
nib will land; hold the side button while touching to erase. Fingers don't leave ink.

It started as a native port of the playtested HTML proof of concept in
[`reference/ink-mayhem-poc.html`](reference/ink-mayhem-poc.html); a few rules have since changed
from it after playtesting on a phone. See [`docs/tuning.md`](docs/tuning.md) for every gameplay
number and every change from the POC, and [`docs/decisions.md`](docs/decisions.md) for the
design notes.

## Install a build

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

On first launch the game asks you to **write “Ink Mayhem”** so it learns your everyday writing
pressure (that becomes the middle of the spring band), then runs a short **tutorial**: five
lessons on rope, spring, beam and erasing, on real terrain. Both happen once. The ⋯ menu has
**How to play** (the tutorial again), **Recalibrate**, and the **Pen readout** (tool type, raw
and normalized pressure, buttons, tilt, hover and event rates) for checking the S Pen.

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
./gradlew :lwjgl3:run -Pargs="--emulate-pen --skip-calibration --skip-tutorial"
```

| Option | |
|---|---|
| *(none)* | The mouse is a mouse: like a finger on Android, it only draws with **Allow finger and mouse** on (⋯ menu). |
| `--emulate-pen` | The mouse acts as an S Pen. Keys `1` / `2` / `3` give light / normal / hard pressure; the right button is the side button. |
| `--autoplay` | The correct-ink bot plays (`--seed N` for a fixed level). |
| `--wrong-ink TUNNEL=SPRING` | The bot uses the wrong ink for a hazard type, to reach a death. |
| `--landscape`, `--size WxH`, `--density D` | Window size in dp and pixels per dp. |
| `--dark`, `--reduced-motion` | Simulate the system settings. |
| `--settings`, `--calibration`, `--skip-calibration` | Open or skip overlays at start. |
| `--tutorial N`, `--skip-tutorial` | Open the tutorial at lesson N (1–5), or don't open it on first run. |
| `--rotate-after S` | Swap width and height after S seconds, like rotating the phone mid-run. |
| `--screenshot out.png --after S` | Save a screenshot after S seconds and quit. |

### Sound effects

The sounds are synthesized from the POC's WebAudio recipes and committed as WAVs. After changing
`SfxSynth`, regenerate them (a test fails if they drift):

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
| Every push and pull request | `ci.yml`: `./gradlew lint test assembleDebug` | The debug APK as a workflow artifact |
| **Run workflow** on any branch | `ci.yml` (manual) | The same, on demand |
| Push to `main`, or a manual run on `main` | `ci.yml` | Moves the `latest-debug` tag and replaces the prerelease's APK |
| Tag `v*` | `release.yml` | Signed release APK on a GitHub Release |

To build without pushing (from a phone too): on GitHub open **Actions → CI → Run workflow**, pick
the branch and run it. The APK is under **Artifacts** on the finished run's page (a zip holding
the `.apk`); a run on `main` also refreshes `latest-debug`.

## Project layout

| Path | |
|---|---|
| `core/` | Pure Kotlin game: physics (`physics/`), simulation and generation (`game/`), pen input model (`input/`), rendering (`render/`), UI (`ui/`), audio (`audio/`). Headless tests in `core/src/test`. |
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
