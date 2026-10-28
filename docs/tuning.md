# Tuning

Every gameplay constant, where it lives, and any retune made while porting from the POC
(Planck.js 1.5.0) to libGDX Box2D 2.3. The POC ([`reference/ink-mayhem-poc.html`](../reference/ink-mayhem-poc.html))
was the source of truth for the port; the rules changed since are listed under
[Changes from the POC](#changes-from-the-poc).

## Retunes

**None.** Every number the POC has is unchanged. All verified behaviours pass on libGDX Box2D
unchanged (see [Verified behaviours](#verified-behaviours)).

## Changes from the POC

Made after playtesting the port on a phone, at the owner's request.

1. **Ink is solid while it's being drawn ("wet ink").** In the POC a stroke only became physical
   on pen-up, so a runner reaching a bridge that was still being drawn fell straight through it.
   Now the stroke so far is laid as static pieces (at least 0.15 m long, the stroke's own
   thickness and friction) that only the runner collides with; pen-up replaces them with the
   real stroke. Guards so that holding the pen down doesn't beat lifting it:
   - only a stroke begun within anchor reach (0.45 m) of the ground or a ceiling is laid; one
     begun in mid-air would fall when finished;
   - rope past 6.8 m in one stroke isn't laid (a finished rope that long gives way), and the
     "Too long for rope" toast shows at once;
   - boulders, crates and finished strokes pass through wet ink, so it never carries a boulder
     a real rope would drop;
   - a hazard crossed mid-stroke is judged on the live stroke only once both its ends would be
     pinned (a finished bridge, or ink lying on the ground); crossing on half-drawn ink scores
     nothing.
   Wet springs bounce the runner like finished ones. One gap remains by design: held ink is
   rigid, so a skilled player can, say, hold a rope ramp pinned at one end and launch off it.
   It can save the runner but never scores.
2. **The tunnel ceiling is a rock spiked only underneath.** The POC's ceiling was a 1.2 m slab
   that killed on any contact, including landing on its top. Now the rock rises 40 m (out of
   sight), only a 0.12 m layer along its underside is spiked (stopping 0.1 m short of each
   side, where no spikes are drawn), and its top and sides are plain rock. Because it can't be
   bounced over, the tunnel can't be skipped. Its gap, height and 3.5 m overhangs are unchanged.
   High drops that would sit inside the rock are left out (the level's random draws are still
   made, so the rest of a seeded level is unchanged), and the tunnel's intro note wraps to fit
   in front of the rock.
3. **Calibration asks for "Ink Mayhem"** instead of a signature, with the phrase as a faint
   guide. The maths is the same.
4. **A first-run tutorial** (new; see [Tutorial](#tutorial)).
5. **Stages** (new; see [Stages](#stages)): a second mode with hand-built levels. Endless keeps
   every POC number.
6. **"Colour ink by type" is on by default** (the POC's default was plain ink), so the ink
   colours the tutorial teaches carry into the game. It can still be turned off in settings.

## Physics (`core/.../physics/InkPhysics.kt`, `Ink.kt`)

| Constant | Value | POC name |
|---|---|---|
| Gravity | (0, +10) m/s², y down | `gravity: V(0, 10)` |
| Resample spacing | 0.30 m | `C.SEG` |
| Thickness | t = 0.06 + 0.39 p | `C.T_MIN`, `C.T_MAX` |
| Rope / spring / beam thresholds | p < 0.33 / < 0.62 / ≥ 0.62 | `C.ROPE_MAX`, `C.SPRING_MAX` |
| Segment box | half-length L/2 + 0.15 t, half-thickness t/2 | `buildStroke` |
| Density | rope 6, spring and beam 1.5 | `C.DENS_ROPE`, `C.DENS_INK` |
| Friction / restitution | 0.8 / spring 0.25, others 0.05 | `buildStroke` |
| Damping | angular 0.2, linear 0.02 | `buildStroke` |
| Joint strength | 2400 × t N | `C.K` |
| Anchor strength | 60000 × t N | `C.K_ANCHOR` |
| Anchor reach | 0.45 m (InkCore defaults to 0.3; the game passes 0.45) | `ANCHOR_R` |
| Spring weld | 5 Hz, damping ratio 0.5 | `C.SPRING_HZ`, `C.SPRING_DAMP` |
| Torque weight in load | 0.6 × \|torque\| / t (not for rope joints) | `C.TORQUE_W` |
| Load filter | load = 0.8 load + 0.2 raw | `step` |
| Break grace | 0.3 s | `C.GRACE` |
| Long rope | > 6.8 m of rope segments: middle rope joint gets strength 1, age −0.35 | `ROPE_MAX_LEN` |
| Step | 1/60 s in 2 substeps, 10 velocity / 8 position iterations | `ink.step(sdt, 2)` |
| Cull fallen ink | y > 400 | `CULL_Y` |
| Ground / ceiling friction | 0.9 | `addRect` |
| Wet ink (new) | static pieces ≥ 0.15 m (`WET_STEP`), box as a segment's, friction 0.8, restitution as the ink; collides with the runner only | — |

## Game (`core/.../game/GameSim.kt`)

| Constant | Value |
|---|---|
| Ink start / max | 70 / 100 |
| Stroke cost | 30 × Σ L t² over the resampled stroke; drawing stops ("Out of ink") when the live estimate would reach 0 |
| Pen-down needs | more than 0.5 ink |
| Erase refund | 50 % of the stroke's cost; eraser reach 0.3 m |
| Drops | 12 ink at ground level + 0.75 m (60 % of flats ≥ 4 m); "high" 25 ink at 4.2 m (22 %); pickup radius 0.85 m |
| Runner | r 0.4, density 2, friction 1, restitution 0.05, bullet |
| Runner drive | torque clamp(3 (speed/r − ω), ±7) |
| Stall hop | \|vx\| < 0.4 and \|vy\| < 0.6 for 0.45 s → impulse m × (1.6, −4.6) |
| Spring bounce | v = (max(vx, 0.95 speed), −9.6), 0.3 s cooldown |
| Boulder | r 0.9, density 2.5, friction 1, restitution 0.02; torque clamp(25 (1.4 speed/r − ω), ±90); spawns 10 m behind at 1.3 × speed when the runner passes runway start + 10.5 m |
| Boulder bounce | v = (max(vx, 1.3 speed), −9.6), 0.3 s cooldown |
| Speed | 3.4 + 2.6 × min(1, dist / 600) m/s; toast every 100 m |
| Death time scale | eases to 0.35 at rate 10/s |
| Fell | y > deepest ground within ±10 m + 4 |
| Flattened | boulder centre within 0.9 + 0.4 + 0.04 m |
| Stuck | no +0.5 m progress in 5 s of sim time |
| Restart | pen-down 0.7 s after dying; death card after 0.6 s |
| Combo | +1 at or under par, cap 9; lighter than par adds 50 × combo; heavier resets to 1 |
| Judging | once the runner passes x1 + 0.6, heaviest stroke overlapping [x0 − 3, x1 + 1] |
| Camera | runner at 28 % / 60 %; lerp x 4/s, y 2.5/s; a new run starts at 62 % |
| Scale | clamp(min(W, H) / 12, 22, 64) dp per metre |
| Cull | every 0.5 s, everything more than 25 m behind the camera (notes 35 m) |

### World generation (`genSection`)

| | |
|---|---|
| Start | level 20, ground from −40 to 8 m |
| Flats | max(4.5, rnd(8, 12) − 4 min(1, genX / 700)) |
| Intro | gapS, gapM, wall, tunnel, gapL, boulder, each with its note |
| Weights | gapS 1.6, gapM 2.6, gapL 2 + 1.5 d, wall 2.4, tunnel 1.8 + d, drop 1, boulder 1.6 + d (0 for 3 sections after a boulder); a repeat becomes gapM |
| Level bounds | wall below 11 becomes a drop; drop above 27 becomes a wall |
| Sizes | gapS 2.2–3.2, gapM 4.2–5.8, gapL 7.6–9.2, wall up 2.1–2.8, drop down 1.6–2.6, tunnel gap 3.8–5.2 under a rock whose underside is 2.1 m up, 3.5 m past each end (the POC's slab was 1.2 m thick; the rock now rises 40 m with a 0.12 m spiked layer underneath); boulder runway 18 m then a 4.8–5.8 m gap |
| Generated ahead | until genX ≥ camera x + view width + 30 m |

Generation draws from a seeded `kotlin.random.Random`; cosmetic randomness (tips, particle
spread, drop bob phase) uses a separate generator so it never changes the level. As in the POC,
the difficulty term `d` uses the runner's distance when a section is generated, so a seed
reproduces exactly for the same viewport and the same play.

## Input (`core/.../input/Pressure.kt`, `GameSim.addPoint`)

| | |
|---|---|
| Calibration | median raw pressure of the player writing "Ink Mayhem" (samples > 0.02, at least 30), clamped 0.08–0.9; 0.32 if skipped. Asked once, stored; "Recalibrate" in settings |
| Normalized pressure | p = clamp(0.45 × raw / cal) |
| Fingers and mouse (debug) | fixed p = 0.45 |
| Smoothing | p = 0.7 prev + 0.3 new |
| Point spacing | points closer than 0.02 m are dropped (raising the last point to the smoothed pressure) |
| Ramps | the first and last min(3, n / 4) points are raised to their inner neighbour's pressure |

## Presentation

| | |
|---|---|
| Rules / margin | every 30 dp, scroll with the camera / margin at min(56, 12 % W) dp |
| Scratch | volume min(0.09, speed px/s × 0.00012) × (0.5 + p); pitch 1800 + (1 − p) 2600 Hz; smoothing 0.03 s / 0.05 s |
| Shake | 0.35 on death, 0.15 boulder spawn, 0.12 snaps; off with reduced motion |
| Particles | capped at 4 per burst with reduced motion |
| Haptics (new) | 12 ms at amplitude 140 per snap (at most every 80 ms), 45 ms at 255 on death |

## Verified behaviours

Headless tests in `core/src/test`, each scenario first run on the POC's own `InkCore` under
Planck.js 1.5.0 (`reference/harness`), where it passes too:

| Behaviour | Scenario | Test |
|---|---|---|
| Rope holds 0.25 kg, snaps under 1.9 kg | 5 m gap, stroke from 0.4 m before to 0.4 m past the edges, 0.5 m crate dropped 2 m onto it; p 0.12 / 0.2 / 0.28 | `InkStrengthTest` |
| Spring holds 1.9 kg, snaps under 9.7 kg | same, p 0.38 / 0.47 / 0.56 | `InkStrengthTest` |
| Beam holds 9.7 kg | same, p 0.68 / 0.8 / 0.95 | `InkStrengthTest` |
| Runner crosses ~5.7 m bridges | gaps 5.5 / 5.7 / 6.0 m, edge-to-edge strokes 5 cm above ground, nine pressures | `RunnerBehaviourTest` |
| Boulder falls through rope | gaps 4.8 / 5.3 / 5.8 m, boulder 4.4 / 5.4 / 6.5 m/s, p 0.15 / 0.25 | `RunnerBehaviourTest` |
| 8 m beam carries runner and boulder | gaps 7.6 / 8.0 m, p 0.7 / 0.85 / 1.0 | `RunnerBehaviourTest` |
| Springs launch the boulder | boulder vy reaches −9.6 | `RunnerBehaviourTest` |
| Long rope snaps | 7.6 m rope snaps within ~0.65 s; 6 m rope doesn't | `RunnerBehaviourTest` |
| Correct-ink bot | five seeds: six Clean intro judgements, ×8–9 combo past 120 m, no death to 170 m | `ScriptedRunTest` |
| Wrong ink | spring in the tunnel dies "Spiked"; rope over the long gap dies "Fell in" | `ScriptedRunTest` |
| Tunnel rock (new) | the roof and sides are safe, the underside spikes; springs 1–8 m before it at 3.4 and 6 m/s never get the runner above it; no drop inside a rock over 1.5 km of eight seeds | `TunnelTest` |
| Wet ink (new) | the runner crosses a gap on a stroke still being drawn; pen-up builds the real stroke under it; a held rope past 6.8 m drops the runner; wet springs bounce; boulders fall through; ink begun in mid-air isn't solid; finished-bridge crossings are judged, half-drawn ones score nothing | `WetInkTest` |
| Tutorial (new) | each lesson passes with the right ink and fails, with its hint, with the wrong one; the runner waits for the pen; the whole tutorial can be played through | `TutorialTest` |
| Stages (new) | every World 1 stage clears with its scripted solution, no deaths and only Clean judgements, passing its checkpoint and picking up its hearts; a spring in the Cellar is Spiked, rope over Up High's beam gap falls in, no ink dies everywhere | `StageTest` |
| Stage session (new) | the intro holds the runner; a death costs a life and retries from the checkpoint with its score; game over after three; stars, best and unlocks are recorded; lives carry on and cap at 9 | `StageSessionTest` |

Two notes on scenario design, both true of the POC as well:

- **Crate drops.** A crate set down gently is held by rope up to 1.9 kg and by spring up to
  9.7 kg. The brief's thresholds appear when the crate falls about 2 m onto the bridge (1 m is
  the borderline), so the tests drop it from 2 m.
- **Beams on the ground are walls.** A beam drawn lying on the ground before the gap, at p ≥ 0.8,
  makes a step taller than the runner's radius. The stall hop fires, but the step's face cancels
  its forward impulse and the runner dies "Stuck" (the POC's tip: "Thick beams make walls").
  Bridges drawn edge to edge, as the crossing tests do, are crossed at every pressure.

## Stages

`core/.../game/Stage.kt` (layout and builder), `World1.kt` (the stages), `StageSession.kt` (lives,
retries, stars). Each stage runs on its own `GameSim`; Endless is untouched.

### World 1

| Stage | Theme | Speed | Length | What's in it |
|---|---|---|---|---|
| 1-1 First Page | ruled paper | 3.4 m/s | 179 m | gaps (rope), hills, a wall (spring), a high road with a heart, a seesaw, a hill-top gap |
| 1-2 Cellar | graph paper, cave roof 3.6 m up | 3.5 m/s | 126 m | tunnels, a rope slide down a shaft, spike pits, a lift up a 5 m wall |
| 1-3 Up High | dot grid, islands 5 m deep | 3.6 m/s | 116 m | a three-spring climb, a 7.6 m beam gap, a seesaw, sloping bridges down, a cloud with a heart |
| 1-4 Castle | brickwork | 3.8 m/s | 129 m | spike pits, a tunnel, a 6 m lift, a seesaw over spikes, a climb, a beam gap over spikes, a boulder runway |

Ground starts at level 20; blocks are 40 m deep unless the theme says otherwise.

### Pieces

| Piece | Numbers |
|---|---|
| Slope | a quad from the current level up or down by `rise` over `len` |
| Ledge | floating block, 0.6 m thick |
| Spike strip | 0.3 m deep, fatal ("Spiked") anywhere |
| Spike pit | the gap's floor 3 m down, solid, spiked |
| Slide | a 5 m shaft to ground 4 m lower; spikes 10 m down. A rope edge to edge is 6.4 m (under rope's 6.8 m) |
| Lift | 0.5 m thick kinematic platform in a 3 m slot; waits at the bottom until the runner lands on it, rises at 1.6 m/s to the top and stays. Riding it doesn't count as being stuck |
| Seesaw | plank = gap + 0.6 m, 0.3 m thick, density 0.6, pinned 0.6 m above the ground at the gap's centre, resting on the near edge, angular damping 1.5 |
| Checkpoint | a bookmark; a retry starts 1 m past it |
| Finish | a flag; past it the runner brakes to a stop |
| Heart | +1 life (max 9), pickup radius 0.85 m like drops |

### Rules that differ from Endless

| | Endless | Stages |
|---|---|---|
| Speed | 3.4 → 6 m/s with distance | fixed per stage |
| Drive braking | torque clamp ±7 | −1.5 / +7, so downhill rolls build speed (1-1's hills reach 4.5 m/s, a 27° slope 5.6 m/s) |
| Camera | lerp y 2.5/s | lerp y 4/s with a vertical look-ahead of 0.3 s of the runner's fall or rise (clamped −2…4 m) |
| Speed toasts | every 100 m | none |
| Settings sheet | doesn't pause (POC) | pauses |

### Lives, retries and stars

| | |
|---|---|
| Lives | 3 at the start of a stage from the list; carried into Next; +1 per heart, max 9 |
| Intro | 2.2 s (stage name and lives) before the first try, 1.2 s before a retry; the runner waits |
| Death | −1 life; after 1.6 s, retry from the checkpoint (or the start) with the score, combo, ink and hazard judgements the player had there |
| Game over | no lives left: Try again restarts the stage with 3 lives |
| Clear | 1 s after the flag: score + ink left × 10 = total |
| Stars | 1 for clearing, 1 for no deaths in the stage, 1 for no hazard cleared with heavier ink than it needed |
| Unlock | a stage opens once the one before has at least one star |

Every stage has a scripted solution (rope, spring and beam at mid-band pressure, drawn 8 m
ahead); `StageTest` plays each one through with no deaths and light ink, and checks that wrong
ink or no ink fails.

## Tutorial

`core/.../game/Tutorial.kt`, on its own `GameSim` in sandbox mode (the player's run and best
score are untouched). Ground level 20, run speed 3.4 m/s, unlimited ink, ink coloured by type.

| Lesson | Scene | Passes when |
|---|---|---|
| 1 Feel the pressure | flat ground | rope, spring and beam have each been drawn |
| 2 Light touch: rope | 3.2 m gap | the runner is 1.2 m past it; with heavier ink it asks for rope twice, then accepts |
| 3 Write normally: spring | 2.4 m wall | the runner is on top; without a spring bounce it asks for one twice, then accepts |
| 4 Press hard: beam | 7.6 m gap (too long for rope) | the runner is 1.2 m past it |
| 5 Side button: erase | a 2.6 m beam post standing in the way | the post is erased and the runner is 1.5 m past it |

In lessons 2–4 the runner waits until a stroke is finished and the pen lifted; in lesson 5 until
the post is gone. A waiting runner isn't launched by springs drawn under it. A death, or 3 s without 0.3 m of progress, fails the lesson with a hint for
what went wrong; the scene resets 2.2 s later. A pass moves on after 1.6 s. The tutorial runs
once after calibration on first launch and again from "How to play"; Skip (or Android back)
ends it.

## Where the brief and the POC differ

The POC won in each case; none of these changed behaviour.

1. **Pen-down/up ramps.** The brief says the end points *take* their inner neighbour's pressure;
   the POC *raises* them to it (`max`), so a heavy start is never lightened.
2. **Dropped points.** The POC also raises the previous point to the smoothed pressure when it
   drops a point closer than 0.02 m.
3. **Beam cost.** The brief says beams cost about 12× rope; the title card says "Ten times". The
   real ratio is (t_beam / t_rope)², anywhere from ~4× (p 0.32 vs 0.62) to ~19× (p 0 vs 1); at
   the bot's mid-band pressures (0.2 vs 0.8) it is 7.3×. The card text is kept.
4. **"Too heavy" message.** The brief quotes "Too heavy: rope was enough"; the POC names the
   hazard's par, so walls say "spring was enough".
5. **Springs launch "anything".** Only the runner and boulders get the −9.6 m/s launch; other
   bodies just see the spring's 0.25 restitution.
6. **Death time scale.** It eases towards 0.35 (rate 10/s) rather than switching instantly.
7. **Camera on a new run** starts with the runner at 62 % from the top, then eases to 60 %.
8. **Long-rope test** counts only the stroke's rope segments (× 0.3 m), and weakens the rope
   joint nearest the middle rope segment.
9. **Rules the brief doesn't mention** but the POC has, all ported: pen-down needs > 0.5 ink; drop
   pickup radius 0.85 m; calibration needs 30 samples above 0.02; the settings sheet doesn't pause
   the run.
