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
3. **Calibration is three drawing tasks** instead of a signature: a rope, a spring and a beam
   line, then a free try. The POC's one number put the player's writing pressure at 0.45 on a
   straight line; now each of the player's three pressures lands in the middle of its own band
   (see Input below). The bands themselves (rope below
   0.33, spring below 0.62) are unchanged. Asked by the player after a recording showed writing
   pressure and drawing pressure differ, and a light pen (raw ~0.076 writing) reading as rope.
4. **A first-run tutorial** (new; see [Tutorial](#tutorial)).
5. **Stages** (new; see [Stages](#stages)): a second mode with hand-built levels. Endless keeps
   every POC number.
6. **"Colour ink by type" is on by default** (the POC's default was plain ink), so the ink
   colours the tutorial teaches carry into the game. It can still be turned off in settings.
7. **No margin line.** The POC's notebook margin, a pinkish-red vertical line fixed on screen
   while the page scrolls, is gone in every orientation: in play it looked like a line
   following the runner. Visual only.
8. **One ink per stroke.** The POC gives each segment the ink of its own pressure, so a stroke
   can be rope at its tips and beam in the middle. Now a whole stroke takes the ink of the
   pressure it settles at: the median of its samples after the first 150 ms (the pen landing)
   and, once finished, before the last 70 ms (the pen lifting). Thickness still follows the
   pen, kept inside that ink's band. While drawing, the stroke, its wet ink, the ink tag and the
   gauge show the ink it is heading for (until the pen has landed, the pressure right now).
   Asked for by the player after Pen Lab: their pressure took 110–150 ms to settle after
   touchdown and rose along long strokes, so under per-segment ink only 4 of 12 aimed strokes
   came out one, right ink; by settled pressure, 12 of 12. The band numbers are unchanged.
9. **Hooks need a surface.** In the POC a stroke end hooks to anything within 0.45 m of a
   surface, so a rope drawn half a metre above a ledge hung from thin air. Now an end hooks only
   where its ink touches a surface (half its thickness plus 0.12 m: a light rope about 0.19 m, a
   beam about 0.31 m); an end that doesn't is free, and a stroke with neither end held falls.
   While drawing, a ring marks a hooked start and pulses at the pen's end where lifting would
   hook. Asked for by the player. The physics reference tests keep the POC's reach.
10. **Portrait framing.** The POC's scale is set by the short side, so a portrait phone showed
   only ~12 m across with ~3 m behind the runner: a chasing boulder appeared when it was almost
   there. A portrait view now shows at least 15 m, the runner sits at 22 % across, and the camera
   slides to keep a chasing boulder in view (that last part applies in landscape too; otherwise
   landscape is unchanged). Long toasts wrap to two lines, and notes stay whole on screen.

Slice 1 of the reimagining ("Your doodle is alive. The page fights back."), approved by the
owner: the Ink Rush theme, Slice 1 as pitched, no Endless, straight into play. Numbers under
[Slice 1](#slice-1).

11. **No Endless.** The game opens on a title screen of its own (see [Menus and
    pausing](#menus-and-pausing)); at first it opened straight into play, but players were
    taken by surprise. The endless run, its title and death cards and its best score are gone
    from the game. `GameSim` keeps the POC's generator, which
    the reference tests still play.
12. **Music.** Ink Rush (original, 168 bpm, A minor, eight bars) loops through a stage's intro
    and play and restarts after a lost life; pausing, the settings sheet and leaving the app hold it.
    The stage start, clear, extra-life and game-over jingles are cut from its motif, and a lost
    life plays a falling jingle instead of the POC's death thud. Off with "Music" in settings.
13. **Silent tuning.** First launch no longer asks for pen tuning or runs the tutorial (both
    stay in settings). Instead 1-1's first three obstacles are tuning gates: until the pen is
    tuned, a stroke begun at one is that gate's ink whatever its pressure, and once a rope, a
    spring and a beam stroke have been drawn there, their raw settled pressures tune the pen,
    exactly as the tuning lines would, if they rise enough (otherwise a toast points to "Tune
    your pen" and the gates keep asking on the next try).
14. **Star trails**, **Blots**, **doodle boxes** with the **marker cap** (Bold), and the
    **runner's face** are new.
15. **1-1 is rebuilt** around them: rope gap, spring wall, beam chasm (the three gates), a Blot,
    a doodle box with a cap, a second Blot, then the old page's hills, high road, seesaw and
    gaps. Each new thing gets a dotted guide with the ink's name and a short handwritten note
    saying what to do with the pen (the first version had only the guides; a first-time player
    couldn't tell what rope or spring meant).
16. **A first-timer's runner waits** (asked for by the owner). Until 1-1 has been cleared once,
    the runner brakes to a stop before each new thing on it (the rope gap, the spring wall, the
    beam chasm, the first Blot and the doodle box) and waits until a stroke that does the job
    is there. A stroke that wouldn't (a bridge not hooked on both sides, a spring off the
    ground or off the guide, rope or a spring over the beam chasm) is wiped, its ink given
    back, with a tip saying what to do; after 7 s with nothing drawn a nudge says to draw along
    the dotted line. Notes and guides past the next wait stay hidden, so one thing is
    introduced at a time. 1-1 has a second checkpoint right after the beam chasm, so a later
    death doesn't send a newcomer back through the three inks. Notes held on screen that would
    overlap stack upwards, or wait for room. (The first version waited only while the pen was
    untuned, for any stroke; a misplaced first stroke still cost a life.)

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
| Anchor reach | The game: the ink must touch, so a stroke end hooks within its half thickness + 0.12 m of a surface. The POC (and the reference tests): 0.45 m (InkCore defaults to 0.3; the POC passes 0.45) | `InkPhysics.reach`, `CONTACT`, `ANCHOR_R` |
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
| Camera | runner at 28 % / 60 % (22 % across on a view narrower than 18 m); lerp x 4/s, y 2.5/s; a new run starts at 62 %. A boulder within 14 m behind the runner is kept on screen: the runner slides up to 50 % across |
| Scale | clamp(min(min(W, H) / 12, W / 15), 22, 64) dp per metre: the POC's, except that a portrait screen zooms out to show at least 15 m across |
| Notes | stay whole on screen while relevant: they come in up to 4 m early at the right edge, ride the page, wait at the left edge until the runner is 2 m past their end, then fade over 2 m. One that would overlap another goes above it (10 dp apart), or waits for room |
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

## Input (`core/.../input/PenCalibration.kt`, `Pressure.kt`, `GameSim.addPoint`)

| | |
|---|---|
| Pen tuning | the player draws a rope, a spring and a beam line along a guide. Each line's pressure is the median of its middle 70 % (samples > 0.001, at least 8); it must run half the guide, and each must be at least 1.2× and 0.004 (spring) / 0.008 (beam) raw harder than the last (a beam at ≥ 0.98 only has to be harder). Then a free try, with live ink colour and a chip per ink seen in a stroke of 40 dp or more. From "Tune your pen" in settings; an untuned pen is tuned by 1-1's gates instead (see [Slice 1](#slice-1)) |
| Normalized pressure | piecewise linear: 0 → 0, √(rope·spring) → 0.33, √(spring·beam) → 0.62, beam + (beam − √(spring·beam)) → 1, clamped. Skipped or never tuned: the POC's straight line p = 0.45 × raw / 0.32. An old one-number calibration carries over as that straight line (cal in place of 0.32), and the game asks for tuning once more |
| Fingers and mouse (debug) | fixed p = 0.45 |
| Smoothing | p = 0.7 prev + 0.3 new (thickness and the pressure before the pen has landed) |
| Stroke ink | one per stroke: the band of the median normalized pressure after the first 150 ms, without the last 70 ms once finished (a stroke too short for both: its middle 70 % of samples). Segments are built with their pressure clamped into that band | `SteadyPressure`, `GameSim.finishStroke` |
| Point spacing | points closer than 0.02 m are dropped (raising the last point to the smoothed pressure) |
| Ramps | the first and last min(3, n / 4) points are raised to their inner neighbour's pressure |

## Presentation

| | |
|---|---|
| Rules | every 30 dp, scroll with the camera. The POC's fixed margin line at min(56, 12 % W) dp is left out |
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
| Tutorial (new) | each lesson passes with the right ink and fails, with its hint, with the wrong one; the runner waits for the pen; strokes that can't do the lesson's job are wiped and the runner keeps waiting; the whole tutorial can be played through | `TutorialTest` |
| Pen tuning (new) | an untuned pen maps as the POC did; a light pen's three lines land in their own bands, monotonically; each line must be long enough and harder than the last; a pen that tops out still gets a beam | `PenInputTest` |
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

`core/.../game/Stage.kt` (layout and builder), `World1.kt` … `World5.kt` (the stages),
`Stages.kt` (the worlds in order), `StageSession.kt` (lives, retries, stars). Each stage runs on
its own `GameSim`. Lengths are start to flag.

| Stage | Speed | Length | The idea |
|---|---|---|---|
| **World 1, The Notebook** (ruled paper) | | | |
| 1-1 First Page | 3.4 m/s | 232 m | the three inks (the tuning gates), a Blot, the marker cap, a second Blot, a seesaw |
| 1-2 Doodle Hills | 3.5 m/s | 191 m | hills: gaps on hilltops and in valleys, a Blot rolling downhill, a long run down to a beam gap |
| 1-3 Seesaw Lane | 3.5 m/s | 139 m | seesaws, longer, over spikes, two in a row |
| 1-4 Blot Parade | 3.5 m/s | 159 m | Blots: a pair for one bounce, a parade to roll flat while Bold, one on top of a wall |
| 1-5 The Big Blot | 3.6 m/s | 146 m | a run through the world, then the Big Blot |
| **World 2, The Cellar** (graph paper, cave roofs) | | | |
| 2-1 Cellar Door | 3.5 m/s | 166 m | low roof (rope, not springs), a spiked tunnel, a slide, an Eraser to drop a beam on, the Highlighter, an inkwell |
| 2-2 Rubber Tunnels | 3.5 m/s | 139 m | Erasers: beams dropped on them, ones that walk off edges, one in a tunnel |
| 2-3 Hidden Ways | 3.5 m/s | 168 m | two Highlighters: a hidden bridge, hidden stars, an inkwell, gold stars only a swipe shows |
| 2-4 Mine Lifts | 3.6 m/s | 164 m | down slides and up lifts, spike pits between |
| 2-5 The Giant Eraser | 3.6 m/s | 126 m | a run through the cellar, then the Giant Eraser |
| **World 3, Up High** (dot grid, islands 5 m deep) | | | |
| 3-1 Cloud Steps | 3.6 m/s | 176 m | a bouncy cloud, a flight of paper planes, Cloud chalk across an 11 m gap |
| 3-2 Flight Path | 3.7 m/s | 156 m | paper planes, flight after flight |
| 3-3 Drifting | 3.6 m/s | 153 m | bouncy clouds over the void, plain ones to bridge between |
| 3-4 Thin Air | 3.6 m/s | 139 m | Cloud chalk over long gaps, gold stars where only a chalk path reaches |
| 3-5 The Paper Airliner | 3.7 m/s | 118 m | a run across the sky, then the Paper Airliner |
| **World 4, The Castle** (brickwork) | | | |
| 4-1 Castle Gate | 3.8 m/s | 193 m | a boulder runway (rope), a crumbling bridge, a beetle pit (beam), the Gold nib |
| 4-2 Rolling Stones | 3.8 m/s | 199 m | boulders, runway after runway |
| 4-3 Crumble Keep | 3.8 m/s | 154 m | crumbling bridges, longer each time, one into a gap |
| 4-4 Beetle Bridges | 3.8 m/s | 121 m | beetle pits: beams, or the Gold nib's rope |
| 4-5 The Beetle Queen | 3.8 m/s | 141 m | a run through the castle, then the Beetle Queen |
| **World 5, Midnight Ink** (dark page) | | | |
| 5-1 Lights Out | 3.8 m/s | 180 m | an ink pool, the Star nib before a crowd of creatures, then the ink tide |
| 5-2 Tide Run | 3.9 m/s | 185 m | two long tide chases: gaps, climbs, a tunnel, crumbling bricks, a seesaw |
| 5-3 Ink Sea | 3.8 m/s | 150 m | island to island across pools: sloping bridges down, springs up, a beam over the widest |
| 5-4 All Hands | 3.9 m/s | 165 m | every creature and paper planes, and a Star nib for the crowd |
| 5-5 The Big Smudge | 3.9 m/s | 135 m | a tide chase and the sea, then the Big Smudge |

Ground starts at level 20; blocks are 40 m deep unless the theme says otherwise. Every stage
has three gold stars, all but 2-1 and 2-3 a heart, and every world's last stage a boss.

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
| Checkpoint | a bookmark; the snapshot is taken 0.6 m past it (once the hazard just before is judged) and a retry starts 1 m past the last one passed, with at least 8 m of flat before the next hazard. One per stage, two in 1-1 |
| Finish | a flag; past it the runner brakes to a stop |
| Heart | +1 life (max 9), pickup radius 0.85 m like drops |
| Hint | a dotted guide in an ink's colour with an arrow and the ink's name 1.35 m above it; fades in from 12 m to 9 m before it and out over 0.5 s once passed. A tuning gate is a hint that also tunes |
| High road | a 0.6 m ledge 3.2 m up, reached by a spring 2.6 m before it; a gold star and a heart sit on it |
| Bounce gold | a gold star at the top of a spring's bounce: 2 m on and 4.6 m up from the spring |
| Tunnel | a spiked rock 2.1 m over the ground, overhanging the gap by 3.5 m each side: rope under it, never a spring |
| Doodle box | 0.9 m square, its underside 2.6 m up; holds the marker cap, a power-up or 5 stars; a hidden one shows only once highlighted |
| Cave | plain rock overhead (the Cellar's roof is 3.6 m up) |
| Hidden ledge | solid only once a highlighter swipe finds it |
| Inkwell | a jar on the ground (hidden until highlighted, or not); rolling over its mouth at under 2 m/s up or down drops the runner to a bonus page 55 m below: a floor with a wall at each end and a row of stars, 18 m long, whose own inkwell lifts it back up further along |
| Cloud | 0.7 m thick; a bouncy one launches the runner like a spring |
| Planes | a flight of 3 paper planes 1.4 m apart at head height (0.55 m up), flying in from 16 m ahead at 4 m/s once the runner passes its mark |
| Crumbling bridge | bricks about 1.2 m long and 0.5 m thick over spikes 3 m down; each falls 0.45 s after the runner touches it |
| Boulder runway | a boulder starts rolling 10 m behind the runner once it is 10.5 m along, at 1.3 × the run speed; rope lets it drop through the gap at the end |
| Beetle pit | a gap 5 m deep with ink beetles on its floor |
| Ink pool | a gap whose ink surface lies 1.5 m below the ground (or below the lower side) |
| Pool wall | a 2.5 m pool whose far bank is 2.4 m higher; a spring 0.2–1 m before the edge carries the runner over |
| Tide | the ink tide rises once the runner passes its start and breaks at its end |
| Arena | 13 m of flat ground with walls 12 m high at each end; the Big Smudge's has a spiked roof 8.2 m up |

### Rules that differ from the POC's endless run

| | Endless (POC) | Stages |
|---|---|---|
| Speed | 3.4 → 6 m/s with distance | fixed per stage |
| Drive braking | torque clamp ±7 | −1.5 / +7, so downhill rolls build speed (1-1's hills reach 4.5 m/s, a 27° slope 5.6 m/s) |
| Camera | lerp y 2.5/s | lerp y 4/s with a vertical look-ahead of 0.3 s of the runner's fall or rise (clamped −2…4 m) |
| Speed toasts | every 100 m | none |
| Settings sheet | doesn't pause (POC) | pauses, as does recalibrating, the pause screen and leaving the app (which opens the pause screen, so coming back is paused) |

### Lives, retries and stars

| | |
|---|---|
| Lives | 3 at the start of a stage from the list; carried into Next; +1 per heart and per 50 stars, max 9 |
| Intro | 2.2 s (stage name and lives) before the first try, 1.2 s before a retry; the runner waits, and during a retry's intro ink can already go down |
| Death | −1 life; after 1.6 s, retry from the checkpoint (or the start) with the score, combo, ink and hazard judgements the player had there |
| Game over | no lives left: Try again restarts the stage with 3 lives |
| Clear | 1 s after the flag: score + ink left × 10 = total |
| Stars | 1 for clearing, 1 for no deaths in the stage, 1 for no hazard cleared with heavier ink than it needed |
| Unlock | a stage opens once the one before has at least one star |

Every stage has a scripted solution (rope, spring and beam at mid-band pressure, drawn 8 m
ahead); `StageTest` plays each one through with no deaths and light ink, and checks that wrong
ink or no ink fails.

## Slice 1

`GameSim` (`takeStars`, `updateBlots`, `crushBlots`, `hitBox`, `updateCaps`, `hurt`,
`updateMood`), `Level.kt` (the pieces), `InkMayhemGame.learnGate` (silent tuning),
`MusicSynth` and `SfxSynth` (sound). `Slice1Test` covers each rule below.

| | |
|---|---|
| Star | pickup radius 0.32 m + the runner's; +20 points. Taken within 0.9 s of the last, it rings one step higher on the A-minor pentatonic (8 steps, then stays on the top one); every 50 in a session (reset with fresh lives) is +1 life. Stars behind a checkpoint stay taken on a retry |
| Blot | r 0.38, density 1, upright; stands on ground, ink and boulders, passes through the runner and other Blots. Wakes when the runner is within 11 m and walks at 1.1 m/s towards it, turning at walls (and at its patrol ends, if it has any); falls off edges. Gone 10 m below the ground |
| Stomp | the runner meets a Blot coming down from above (falling faster than 0.5 m/s, centre 0.15 m higher; reach +0.3 m) or anywhere within 2.3 s of a spring bounce: the Blot is squashed (+100) and the runner hops at 6.5 m/s |
| Beam drop | a beam stroke falling faster than 0.5 m/s onto a Blot squashes it (+100); so does a boulder |
| Touch | any other touch: Bold rolls the Blot flat (+100); otherwise "Blotted", a life |
| Doodle box | 0.9 m square, solid (ink hooks to it); a runner touching its underside pops what it holds, once: the marker cap, or 5 stars |
| Marker cap | r 0.24, hops out at (2.2, −4) m/s and slides at 2.2 m/s, turning at walls; once the runner is ahead of it, it slides after it at the run speed + 1.5 m/s (the runner can't turn back); caught within 0.1 m of touching: +500, Bold |
| Box corner | a runner that meets a doodle box's underside outside its width is carried on at 0.95 × the run speed and starts falling, so it can't drop back onto the spring under it and bounce into the corner for ever |
| Bold | runner r 0.52 instead of 0.4, the same mass (density scaled); rolls Blots flat; a Blot or spike hit takes Bold instead of a life, knocks the runner up at 7.5 m/s (down at 2 m/s if the nearest spikes hang above it) and nothing hurts for 1.5 s (it blinks). Falls and boulders still cost a life. Lost with a life |
| Face | by priority: hurt or dead (× eyes), a boulder within 14 m behind (sweat), 1.5 s after a spring bounce (grin) then 0.9 s dizzy, on the ground with no ground, lift, seesaw or ink 1.2 m ahead (wide eyes), 1.2 s after a star, a squash or a clean judgement, or a combo of 4+ (grin) |
| Tuning gates | a stroke begun within 1.5 m of a gate the runner hasn't passed. Tuned when rope, spring and beam rise by the tuning lines' rule (each ≥ 1.2× and 0.004 / 0.008 raw harder) |
| Waiting | 1-1 until it has been cleared once: from 8 m before a wait point the drive aims for √(2 × 3 m/s² × distance left), so the runner stops there (1.4 m before the rope and beam gaps, 5 m before the spring wall, 12.5 m before the first Blot, before it wakes, and 4.5 m before the box) and holds until a stroke that does the job is there; it never counts as stuck. Nudge after 7 s |
| Doing the job | a bridge: hooked within 0.9 m of each end of the guide (over the beam chasm, a beam); a spring: a spring, hooked, its lowest point within 0.6 m and its top within 1.2 m of the ground, reaching to within 0.8 m before the guide's start and 0.4 m past its end (the box: 0.1 / 0.05 m). Sweeps of where a newcomer might draw: every spring accepted at the wall clears it, at the Blot stomps it, and at the box pops the cap and catches it |
| Music | Ink Rush: 168 bpm, 8 bars, square lead (3.2 kHz low-pass), triangle bass, noise drums; one 11.4 s loop at 22.05 kHz mono, 504 KB (under SoundPool's 1 MB), played at 0.42 volume |

## Worlds

`GameSim` (`updateBlots`, `chew`, `updateArena`, `updateBoss`, `bossAct`, `hitBoss`,
`applyPower`, `highlightSwipe`, `updateWarps`, `updatePlanes`, `updateCrumbles`, `updatePools`,
`updateTide`), `Level.kt` (the pieces), `StageBot.fight` (the test bot's boss tactics).
`StageTest`, `BossTest` and `World5Test` cover them. None of these is in the POC; every number
here is new.

### First runs

| | |
|---|---|
| Learning | each world's first stage until it has a star: the runner stops before each new thing and waits for a stroke that does the job, as on 1-1 above |
| Gates | besides a rope, spring or beam guide, a gate can forbid an ink (a spring under a spiked roof), need one (rope to let a boulder drop, a beam over beetles) or want a creature squashed (a beam dropped on an Eraser) |

### Creatures

| | Radius | Walk | Wakes within | |
|---|---|---|---|---|
| Blot | 0.38 m | 1.1 m/s | 11 m | squashed by a stomp, a beam drop, Bold or the Star nib |
| Eraser | 0.45 m | 1.25 m/s | 18 m | rubs out rope and springs it touches; a stomp bounces off it; a beam drop squashes it |
| Ink beetle | 0.3 m | 1.3 m/s | 14 m | hops up to rope or a spring up to 7 m above it (and within 3 m to the side) and eats it after 0.9 s on it; leaves beams and Gold nib ink alone; stompable |

A squash is +100. A beam that squashes a creature crumbles away 0.8 s later (it would lie about
as an obstacle), unless it is hooked to something or hit a boss.

### Power-ups

| | |
|---|---|
| Marker cap | Bold (see Slice 1), +500 |
| Timed powers | last 9 s, shown by a ring round the runner; +500 when caught |
| Highlighter | strokes lay no ink; a swipe finds hidden ledges, boxes, stars, gold stars and inkwells within 1.1 m of it, and fades over 1.6 s |
| Cloud chalk | strokes are static: they stay where they're drawn, so even rope spans an 11 m gap |
| Gold nib | strokes cost no ink, their joints never break and beetles can't eat them |
| Star nib | nothing hurts: creatures, a boss's touch, planes and spikes; creatures touched are zapped (+100). Falls, boulders, ink pools and the tide still cost a life |

### Gold stars

Three a stage, pickup radius 0.5 m + the runner's, +250 each. Ones taken before a checkpoint
stay taken on a retry. A clear saves the ones taken, added to those from earlier clears; the
stage list and the clear card show them.

### Bosses

| | Hit points | Radius | Walk | Hit it by | Every 3.5 s |
|---|---|---|---|---|---|
| The Big Blot | 3 | 1.1 m | 1.4 m/s | a stomp (a spring's bounce onto it) | |
| The Giant Eraser | 3 | 1.0 m | 1.0 m/s | a beam dropped on it (falling faster than 0.5 m/s); it rubs out rope and springs, and stomps bounce off | |
| The Paper Airliner | 3 | 0.9 m | flies 4.4 m up at 2.6 m/s | bouncing up into it | dives a paper plane at the runner at 4.5 m/s |
| The Beetle Queen | 3 | 1.0 m | 1.0 m/s | a stomp | lays a beetle (2 alive at most) |
| The Big Smudge | 4 | 1.2 m | 1.2 m/s | a spring under it throws it up at 15 m/s into its spiked roof | spits a Blot (2 alive at most) |

A boss walks 20 % faster for each hit taken. After a hit it is knocked back at 4 m/s (up at
5 m/s) and flashes for 1.2 s, when it neither hurts nor can be hurt. Touching it otherwise costs
a life, except that one flying up brushes the runner aside rather than carrying it into the
roof. +200 a hit, +1000 for the win; then the arena's ink is cleared and the far wall opens.
The near wall closes once the runner is 1.5 m in, the camera holds the arena, the runner turns
at each wall and the boss theme plays.

### Paper planes

Radius 0.28 m; a flight flies level at 4 m/s. A spring's bounce through a plane (or Bold, or the
Star nib) crumples it (+50); so does spring or beam ink in its way; it tears through rope.
Touching one otherwise costs a life.

### Ink pools and the ink tide

| | |
|---|---|
| Ink pool | the runner goes under 0.1 m past the surface; ink with any point below the surface is swallowed 0.35 s later (Cloud chalk floats); creatures sink. A 4.5 m rope bridge drawn edge to edge stays clear of it |
| Ink tide | rises 9 m behind the runner. Its pace is the run speed × (1 + 0.08 × (gap − 5 m)), kept within 0.6–1.25, so it settles 5 m behind a runner at full speed and catches one that stalls for about 1.5 s; on clean runs it comes within 2.4–3.7 m. It holds still while a first-timer waits at a gate. Ink and creatures behind its front go under. Past its end it drains back at 4 m/s, fading over 1.5 s |
| Framing | while the tide is up the runner may slide up to 40 % across the view so the tide stays in sight; when it's off screen, a TIDE marker gives its distance |

### Music

| Theme | Key | Tempo | |
|---|---|---|---|
| Ink Rush (World 1) | A minor | 168 bpm | square lead, triangle bass, noise drums |
| Cellar (World 2) | D minor | 120 bpm | a hollow pulse lead that echoes, slow bass, soft drums |
| Sky (World 3) | C major | 150 bpm | a triangle lead over arpeggios |
| Castle (World 4) | E minor | 140 bpm | a march with snare rolls |
| Midnight (World 5) | C minor | 176 bpm | a dark pulse lead with a short echo, four on the floor |
| Boss | A minor | 184 bpm | racing arpeggios |

Each is an 8-bar seamless loop at 22.05 kHz mono under 1 MB (SoundPool's limit; a test checks).
Only the playing tune is loaded.

## Tutorial

`core/.../game/Tutorial.kt`, on its own `GameSim` in sandbox mode (the player's run and best
score are untouched). Ground level 20, run speed 3.4 m/s, unlimited ink, ink coloured by type.

| Lesson | Scene | Guide | Passes when |
|---|---|---|---|
| 1 Light touch: rope | 3.2 m gap | dots edge to edge | the runner is 1.2 m past it; with heavier ink it asks for rope twice, then accepts |
| 2 Write normally: spring | 2.4 m wall | dots on the ground 2.4–3.2 m before it | the runner is on top; without a spring bounce it asks for one twice, then accepts |
| 3 Press hard: beam | 7.6 m gap (too long for rope) | dots edge to edge | the runner is 1.2 m past it |
| 4 Side button: erase | a 2.6 m beam post standing in the way | a dashed ring round the post | the post is erased and the runner is 1.5 m past it |

(The old first lesson, "Feel the pressure", is gone: pen tuning's free try does its job.) The
guide pulses in the lesson's ink colour until the runner goes. A finished stroke that can't do
the lesson's job is wiped with a "That one missed" hint and the runner keeps waiting: a bridge
has to come within 1.2 m of the ground and start and end within 0.8 m of the edges; a spring
has to lie near the ground before the wall. In lessons 1–3 the runner waits until a fitting
stroke is finished and the pen lifted; in lesson 4 until the post is gone. A waiting runner
isn't launched by springs drawn under it. While drawing in the tutorial or a stage, a pill
16 dp right of and 44 dp above the pen names the ink being laid. A death, or 3 s without 0.3 m of progress, fails the lesson with a hint for
what went wrong; the scene resets 2.2 s later. A pass moves on after 1.6 s. The tutorial runs
from "How to play" (no longer on first launch: 1-1 teaches by playing); Skip (or Android back)
ends it, back to the title or the stage list, wherever it was opened from.

## Menus and pausing

`ui/MenuCards.kt` (the title, the pause screen, the countdown), `InkMayhemGame` (where each leads).

| | |
|---|---|
| Title | what the game opens on: the name (64 dp, smaller if it and the runner beside it don't fit), "Draw the way ahead with your S Pen.", the three inks drawn with their pressures, then **Start** (nothing cleared yet) or **Play**, naming the first stage not yet cleared (with every stage cleared it opens the list), and **Stages**, **How to play**, **Settings**. Fades in over 0.5 s; Android back leaves the app |
| Stage list | a **Menu** button at the top left goes back to the title, as does Android back. Every world's tab opens, locked or not (a padlock marks a locked world), to show its stage names; only open stages play. Opened from a stage it shows that stage's world |
| World peek | behind the stage list, the tab's world: its finale stage, built but never stepped, without notes or the runner, panned at 2.6 m/s from start to flag and round again (a fade at the turn), its ground 84 % down a tall screen (under the card) or 70 % down a wide one; the page and palette are the world's (Midnight Ink's dark page), the veil over it 30 %, and the world's tune plays. Still with reduced motion |
| Menus | the title screen and the ending show only the ruled paper; Ink Rush plays (the Music slider turns it down or off) |
| Pause | the top-right button in a stage (two bars; ⋯ on the stage list), Android back, or leaving the app, during a stage's intro, play or a lost life's moment. A stroke being drawn is let go. The page dims; the card has the stage, **Resume**, **Restart** (the stage from the start, keeping the lives), **Settings** and **Stage list**. Android back on it resumes |
| Countdown | Resume mid-play counts 3, 2, 1, 0.5 s each, with a rising chime; the stage, its music and the pen stay held until it ends. Resuming during an intro skips it |
| Screen changes | the new screen fades in from the paper over 0.3 s (not with reduced motion) |
| Repository link | under the title screen's footer and near the end of the credits (`ui/Links.kt`); a tap opens it in the browser, or says the address in a toast if no browser opens |
| Ending | after 5-5 the clear card's button reads **The end**: the page clean, the runner rolling along a line that draws itself (5.5 s held), then the player's notebook (stages cleared, stars, gold stars, best scores added up) and the credits roll up at 42 dp/s to "Thanks for playing!". **Skip** jumps to the end; then **Back to the title**. **Credits** on the title screen rolls the credits alone |
| Volumes | Sound effects and Music sliders, 0 to 100 % in steps of 5 (snapping to off near the left); the music plays at 0.42 × its slider, effects at their slider. The old on/off switches carry over as 100 % or off |
| Saves | stamped with a version (`saveVersion`, now 1). An unversioned save with no gold-star keys is from before the five worlds: its 1-2 to 1-4 results (then Cellar, Up High and Castle) are dropped |
| Tunes | a stage's own tune and, if it has a boss, the boss's are loaded during its intro card, so the boss music starts the moment the arena closes; the menus keep Ink Rush loaded |

## Dot, the runner's character

`render/Buddy.kt` (the face and its drawer), `render/Persona.kt` (what drives it in a stage),
`ui/DotHelper.kt` (Dot stepping out of the page to help). The face and remarks are looks only:
they read the sim and never change it.

| | |
|---|---|
| Face | upright whatever way the ball rolls. Googly eyes 0.37 r across, centred 0.5 r up and 0.39 r apart, nudged 0.14 r the way it faces; pupils 0.46 r of an eye; eyebrows 0.36 r long floating 0.16 r over the body; a mouth 1.35 × the face's scale. Eyes are white with black pupils on every page, rimmed in the page's colour; the mouth is the page's colour |
| Body | squashes up to 0.28 on a landing (falls faster than 4 m/s) or a bounce, stretches up to 0.22 rising faster than 2.5 m/s; it keeps its base on the ground |
| Pupils | a spring to where it looks (stiffness 140, damping 9), kicked by the body's acceleration, so they wobble like googly eyes; still with reduced motion |
| Blinks | every 2.2 to 5.4 s, 0.16 s long |
| Faces | hurt or dead: crossed-out eyes; scared (a boulder, or the tide within 6 m): wide eyes, sweat; 0.9 s after a squash: proud, with a wink; off a spring, rising: whee; dizzy after a bounce; waiting at a gate: curious, dozing (z's) after 7 s; while the pen draws: curious; in a boss fight: determined; at an edge: worried; on rope: wobbly; on a beam: proud; happy on a combo or with the Star nib; otherwise calm |
| Looks at | the pen's tip while it draws, the gate's guide while it waits, the boss in a fight, back at a boulder or the tide, else the nearest awake creature (up to 8 m ahead) or gold star (5 m), else ahead |
| Remarks | a speech bubble over its head, leaning away from where it's going and below the HUD, for 1.5 s + 0.04 s a letter; at most one every 4.5 s unless it matters (death, clear, gold star, a power-up, a boss starting or beaten, the tide). On a stage's start (80 %), a stomp (70 %), a bounce (35 %), a snap (60 %), a checkpoint (60 %), a hit on a boss (60 %), onto rope or a beam (20 %) and an edge (20 %). Off with "Dot's remarks"; never in the tutorial, nor while Dot is out helping |
| Helping | on a world's first stage (its first run), once the runner has stopped (under 0.3 m/s) at a gate it waits at, with nothing drawn, Dot steps out: 0.45 s from the runner up to a top corner below the HUD (the corner whose balloon stays 40 dp clear of the guide), growing to 30 dp across with its shadow falling on the page, the balloon fading in from 0.25 s. It says the stage's note for that gate (the last note within 30 m before it) and what to draw, with a dotted pointer to the guide, and looks at it. Once per gate per attempt. The runner on the page is a plain dot meanwhile, and notes wait |
| Going back in | a pen-down anywhere but its balloon (the stroke goes on as normal), **Got it**, Android back, or the gate done, the runner moving on or the stage's play ending: 0.35 s back to the runner, then an ink splash |
| Misses and waits | a stroke that wouldn't do the job is wiped and Dot steps out worried with why (the wipe's tip); 7 s waiting with nothing drawn and Dot knocks twice on the glass ("Hello? Draw along the dotted line...") unless it's already out explaining |
| Welcome | the very first launch, 1 s after the title appears: Dot steps off the title (a plain dot left there) over a veil, looking at the player, in four pages (hello; it rolls, you draw; pressure makes the ink; tap Start), any tap moving on. Never again once seen (saved as `metDot`) |
| Title | sat at the end of the name (it rolls in), looking about, at the pen when it hovers, pleased while a button is held; "Hi! Grab your S Pen." after 1.1 s, then a tip every 8.5 s; dozes after 30 s alone |
| Pause | peeks over the top of the pause card with a line ("Take your time."), dozes after 6 s |
| Ending | rolls along the line wide-mouthed, then "We did it!" and a wink |


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
   pickup radius 0.85 m; calibration needs 30 samples above 0.02 (since replaced by pen tuning);
   the settings sheet doesn't pause the run.
