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
