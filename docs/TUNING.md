# Ink Mayhem: tuning

Every gameplay number and where it lives in the code (`core/src/main/kotlin/com/pranshuparmar/inkmayhem/`,
shortened to `core/...` below). For what the game is, see the [full tour](FEATURES.md); for how
it's built, [Under the hood](TECH.md).

## Physics (`core/.../physics/InkPhysics.kt`, `Ink.kt`)

| Constant | Value | In code |
|---|---|---|
| Gravity | (0, +10) m/s², y down | `GRAVITY` |
| Resample spacing | 0.30 m | `SEG` |
| Thickness | t = 0.06 + 0.39 p | `Ink.T_MIN`, `Ink.T_MAX` |
| Rope / spring / beam thresholds | p < 0.33 / < 0.62 / ≥ 0.62 | `Ink.ROPE_MAX`, `Ink.SPRING_MAX` |
| Segment box | half-length L/2 + 0.15 t, half-thickness t/2 | `buildStroke` |
| Density | rope 6, spring and beam 1.5 | `DENS_ROPE`, `DENS_INK` |
| Friction / restitution | 0.8 / spring 0.25, others 0.05 | `buildStroke` |
| Damping | angular 0.2, linear 0.02 | `buildStroke` |
| Joint strength | 2400 × t N | `K` |
| Anchor strength | 60000 × t N | `K_ANCHOR` |
| Anchor reach | the ink must touch: a stroke end hooks within half its thickness + 0.12 m of a surface (a light rope about 0.19 m, a beam about 0.31 m); an end that doesn't is free, and a stroke with neither end held falls. The physics tests use the looser reach of any surface within 0.45 m | `CONTACT`, `ANCHOR_R` |
| Spring weld | 5 Hz, damping ratio 0.5 | `SPRING_HZ`, `SPRING_DAMP` |
| Torque weight in load | 0.6 × \|torque\| / t (not for rope joints) | `TORQUE_W` |
| Load filter | load = 0.8 load + 0.2 raw | `step` |
| Break grace | 0.3 s | `GRACE` |
| Long rope | > 6.8 m of rope segments: middle rope joint gets strength 1, age −0.35 | `ROPE_MAX_LEN` |
| Step | 1/60 s in 2 substeps, 10 velocity / 8 position iterations | `step`, `VELOCITY_ITERATIONS`, `POSITION_ITERATIONS` |
| Cull fallen ink | y > 400 | `CULL_Y` |
| Ground / ceiling friction | 0.9 | `addRect` |
| Wet ink | a stroke still being drawn: static pieces ≥ 0.15 m, box as a segment's, friction 0.8, restitution as the ink; collides with the runner only. Laid only for a stroke begun within 0.45 m of the ground or a ceiling; rope stops being laid past 6.8 m (with the "Too long for rope" toast); a hazard crossed on wet ink is judged only once both its ends would be pinned | `GameSim.WET_STEP`, `growWet` |

## Game (`core/.../game/GameSim.kt`)

| Constant | Value |
|---|---|
| Ink start / max | 70 / 100 |
| Stroke cost | 30 × Σ L t² over the resampled stroke; drawing stops ("Out of ink") when the live estimate would reach 0 |
| Pen-down needs | more than 0.5 ink |
| Erase refund | 50 % of the stroke's cost; eraser reach 0.3 m |
| Drops | 12 ink (a high one 25); pickup radius 0.85 m |
| Runner | r 0.4, density 2, friction 1, restitution 0.05, bullet |
| Runner drive | torque clamp(3 (speed/r − ω), ±7) |
| Stall hop | \|vx\| < 0.4 and \|vy\| < 0.6 for 0.45 s → impulse m × (1.6, −4.6) |
| Spring bounce | v = (max(vx, 0.95 speed), −9.6), 0.3 s cooldown |
| Boulder | r 0.9, density 2.5, friction 1, restitution 0.02; torque clamp(25 (1.4 speed/r − ω), ±90); spawns 10 m behind at 1.3 × speed when the runner passes runway start + 10.5 m |
| Boulder bounce | v = (max(vx, 1.3 speed), −9.6), 0.3 s cooldown |
| Death time scale | eases to 0.35 at rate 10/s |
| Fell | y > deepest ground within ±10 m + 4 |
| Flattened | boulder centre within 0.9 + 0.4 + 0.04 m |
| Stuck | no +0.5 m progress in 5 s of sim time |
| Combo | +1 at or under par, cap 9; lighter than par adds 50 × combo; heavier resets to 1 |
| Judging | once the runner passes x1 + 0.6, heaviest stroke overlapping [x0 − 3, x1 + 1] |
| Camera | runner at 28 % / 60 % (22 % across on a view narrower than 18 m); lerp x 4/s, y 4/s with a vertical look-ahead of 0.3 s of the runner's fall or rise (clamped −2…4 m; the tutorial: y 2.5/s, no look-ahead). A boulder within 14 m behind the runner is kept on screen: the runner slides up to 50 % across |
| Scale | clamp(min(min(W, H) / 12, W / 15), 22, 64) dp per metre, so a portrait screen still shows at least 15 m across |
| Notes | stay whole on screen while relevant: they come in up to 4 m early at the right edge, ride the page, wait at the left edge until the runner is 2 m past their end, then fade over 2 m. One that would overlap another goes above it (10 dp apart), or waits for room |
| Cull | every 0.5 s, everything more than 25 m behind the camera (notes 35 m) |

## Input (`core/.../input/PenCalibration.kt`, `Pressure.kt`, `GameSim.addPoint`)

| | |
|---|---|
| Pen tuning | the player draws a rope, a spring and a beam line along a guide. Each line's pressure is the median of its middle 70 % (samples > 0.001, at least 8); it must run half the guide, and each must be at least 1.2× and 0.004 (spring) / 0.008 (beam) raw harder than the last (a beam at ≥ 0.98 only has to be harder). Then a free try, with live ink colour and a chip per ink seen in a stroke of 40 dp or more. From "Tune your pen" in settings. The lessons' first one tunes the pen the same way (see [Tutorial](#tutorial)); a pen still untuned is tuned by 1-1's first three obstacles (see [Stars, Blots and boxes](#stars-blots-and-boxes)) |
| Normalized pressure | piecewise linear: 0 → 0, √(rope·spring) → 0.33, √(spring·beam) → 0.62, beam + (beam − √(spring·beam)) → 1, clamped. Never tuned: a straight line, p = 0.45 × raw / 0.32. An early build's one-number calibration carries over as that straight line (it in place of 0.32) |
| Fingers and mouse (debug) | fixed p = 0.45 |
| Smoothing | p = 0.7 prev + 0.3 new (thickness and the pressure before the pen has landed) |
| Stroke ink | one per stroke: the band of the median normalized pressure after the first 150 ms, without the last 70 ms once finished (a stroke too short for both: its middle 70 % of samples). Segments are built with their pressure clamped into that band | `SteadyPressure`, `GameSim.finishStroke` |
| Point spacing | points closer than 0.02 m are dropped (raising the last point to the smoothed pressure) |
| Ramps | the first and last min(3, n / 4) points are raised to their inner neighbour's pressure |

## Presentation

| | |
|---|---|
| Rules | every 30 dp, scroll with the camera; no margin line |
| Scratch | volume min(0.09, speed px/s × 0.00012) × (0.5 + p); pitch 1800 + (1 − p) 2600 Hz; smoothing 0.03 s / 0.05 s |
| Shake | 0.35 on death, 0.15 boulder spawn, 0.12 snaps; off with reduced motion |
| Particles | capped at 4 per burst with reduced motion |
| Haptics | a snap: a crackle of three pulses (6, 6 and 8 ms at 170, 120 and 150, 22 and 18 ms apart; at most one every 0.12 s); 45 ms at 255 on death. Each ink as it goes down: see [Menus and pausing](#menus-and-pausing) |

## Stages

`core/.../game/Stage.kt` (layout and builder), `World1.kt` … `World5.kt` (the stages),
`Stages.kt` (the worlds in order), `StageSession.kt` (lives, retries, stars). Each stage runs on
its own `GameSim`. Lengths are start to finish.

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
| Finish | the next page: a sheet of ruled paper 2.2 m wide and 3 m tall standing on the ground, its top corner folded over (0.65 m) and an arrow on it; its left edge is the line, and past it the runner brakes to a stop. A stage with a boss only finishes once the boss is beaten |
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
| Arena | 13 m of flat ground with walls 12 m high at each end and a plain rock roof on them, a closed room; the Big Smudge's roof is lower, 8.2 m up, and spiked. A runner that somehow gets out while the boss stands is put back in the middle |

### Stage rules

| | |
|---|---|
| Speed | fixed per stage (the table above) |
| Drive braking | the drive torque brakes at most 1.5 (accelerates up to 7), so downhill rolls build speed (1-1's hills reach 4.5 m/s, a 27° slope 5.6 m/s); in a boss arena ±7 |
| Pausing | the settings sheet, pen tuning, the pause screen and leaving the app (which opens the pause screen, so coming back is paused) all hold the stage |

### Lives, retries and stars

| | |
|---|---|
| Lives | 3 at the start of a stage from the list; carried into Next; +1 per heart and per 50 stars, max 9 |
| Intro | 2.2 s (stage name and lives) before the first try, 1.2 s before a retry; the runner waits, and during a retry's intro ink can already go down |
| Death | −1 life; after 1.6 s, retry from the checkpoint (or the start) with the score, combo, ink and hazard judgements the player had there |
| Game over | no lives left: Try again restarts the stage with 3 lives |
| Clear | 1 s after the finish: score + ink left × 10 = total |
| Stars | 1 for clearing, 1 for no deaths in the stage, 1 for no hazard cleared with heavier ink than it needed |
| Unlock | a stage opens once the one before has at least one star |

Every stage has a scripted solution (rope, spring and beam at mid-band pressure, drawn 8 m
ahead); `StageTest` plays each one through with no deaths and light ink, and checks that wrong
ink or no ink fails.

## Stars, Blots and boxes

`GameSim` (`takeStars`, `updateBlots`, `crushBlots`, `hitBox`, `updateCaps`, `hurt`,
`updateMood`), `Level.kt` (the pieces), `InkMayhemGame.learnGate` (tuning from 1-1's first
obstacles), `MusicSynth` and `SfxSynth` (sound). `World1Test` covers each rule below.

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
| Mood | what the sim tells Dot's face (see [Dot](#dot-the-runners-character)): hurt, scared (a boulder within 14 m behind), happy (1.2 s after a star, a squash or a clean judgement, or a combo of 4+), dizzy (0.9 s after a bounce's 1.5 s), worried (no ground, lift, seesaw or ink 1.2 m ahead) |
| Tuning gates | while the pen is untuned, 1-1's rope gap, spring wall and beam chasm make a stroke begun within 1.5 m of them that ink whatever its pressure, and once all three are drawn their raw settled pressures tune the pen if they rise by the tuning lines' rule (each ≥ 1.2× and 0.004 / 0.008 raw harder) |
| Waiting | 1-1 until it has been cleared once: from 8 m before a wait point the drive aims for √(2 × 3 m/s² × distance left), so the runner stops there (1.4 m before the rope and beam gaps, 5 m before the spring wall, 12.5 m before the first Blot, before it wakes, and 4.5 m before the box) and holds until a stroke that does the job is there; it never counts as stuck. Nudge after 7 s |
| Doing the job | a bridge: hooked within 0.9 m of each end of the guide (over the beam chasm, a beam); a spring: a spring, hooked, its lowest point within 0.6 m and its top within 1.2 m of the ground, reaching to within 0.8 m before the guide's start and 0.4 m past its end (the box: 0.1 / 0.05 m). Sweeps of where a newcomer might draw: every spring accepted at the wall clears it, at the Blot stomps it, and at the box pops the cap and catches it |
| Music | Ink Rush: 168 bpm, 8 bars, square lead (3.2 kHz low-pass), triangle bass, noise drums; one 11.4 s loop at 22.05 kHz mono, 504 KB (under SoundPool's 1 MB), played at 0.42 volume |

## Worlds

`GameSim` (`updateBlots`, `chew`, `updateArena`, `updateBoss`, `bossAct`, `hitBoss`,
`applyPower`, `highlightSwipe`, `updateWarps`, `updatePlanes`, `updateCrumbles`, `updatePools`,
`updateTide`), `Level.kt` (the pieces), `StageBot.fight` (the test bot's boss tactics).
`StageTest`, `BossTest` and `World5Test` cover them.

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
| Marker cap | Bold (see [Stars, Blots and boxes](#stars-blots-and-boxes)), +500 |
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

`core/.../game/Tutorial.kt`, on its own `GameSim` in sandbox mode (the player's progress is
untouched). Ground level 20, run speed 3.4 m/s, unlimited ink, ink coloured by type.

| Lesson | Scene | Guide | Passes when |
|---|---|---|---|
| 1 Your touch | flat ground, the runner held | dots on the ground ahead, 5 m long | three lines along it, light, normal and hard, each drawn as that ink whatever its pressure and wiped once accepted. A line has to run at least half the guide within 1.5 m of it; the normal and hard ones have to press harder than the one before (by the tuning lines' rule, on the raw pressure each settled at), or it's asked for again; after 2 such misses in a row it is taken anyway and the pen stays as it was. Three rising lines tune the pen (as the tuning lines do) for the rest of the lessons and the game |
| 2 Light touch: rope | 3.2 m gap | dots edge to edge | the runner is 1.2 m past it; with heavier ink it asks for rope twice, then accepts |
| 3 Write normally: spring | 2.4 m wall | dots on the ground 2.4–3.2 m before it | the runner is on top; without a spring bounce it asks for one twice, then accepts |
| 4 Press hard: beam | 7.6 m gap (too long for rope) | dots edge to edge | the runner is 1.2 m past it |
| 5 Side button: erase | a 2.6 m beam post standing in the way | a dashed ring round the post | the post is erased and the runner is 1.5 m past it |

The guide pulses in the lesson's ink colour until the runner goes. A finished stroke that can't
do the lesson's job is wiped with a "That one missed" hint and the runner keeps waiting: a bridge
has to come within 1.2 m of the ground and start and end within 0.8 m of the edges; a spring has
to lie near the ground before the wall. In lessons 2–4 the runner waits until a fitting stroke
is finished and the pen lifted; in lesson 5 until the post is gone. A waiting runner isn't
launched by springs drawn under it. While drawing in the lessons or a stage, a pill 16 dp right
of and 44 dp above the pen names the ink being laid. A death, or 3 s without 0.3 m of progress,
fails the lesson with a hint for what went wrong; the scene resets 2.2 s later. A pass moves on
after 1.6 s.

**On the first launch** the lessons follow Dot's welcome and are a must: no Skip button, and
Android back only says to finish them first. They end in 1-1, and only then is the welcome
marked as seen, so closing the app halfway brings both back next time. From **How to play**
(the title screen or Settings) they have a Skip button, and Skip or Android back returns to the
title or the stage list, wherever they were opened from.

**Dot presents them** (`ui/TutorialCard.kt`). The lesson card is Dot's speech balloon at the top,
up to the card width (`cardW`) and fitting beside Dot on a phone; a lesson title too wide for it
shrinks from 28 sp, in 2 sp steps, to no less than 20. Dot steps out of the lesson's runner
(0.45 s, 26 dp across, its shadow on the page; the balloon fades in from 0.25 s) to say each
thing, and dives back in (0.35 s, then an ink splash) while the player draws and while the
runner rolls, so the runner has its face then; it comes out again for each pass, miss or new
lesson. Out, it looks at the pen's tip while it draws, else at the guide, else the runner, and
its face is curious while it waits, worried at a hint or a failed try, proud for 0.9 s when a
lesson passes, then happy. A pen-down on Dot or its balloon draws nothing.

## Menus and pausing

`ui/MenuCards.kt` (the title, the pause screen, the countdown), `InkMayhemGame` (where each leads).

| | |
|---|---|
| Title | what the game opens on: the name (64 dp, smaller if it and the runner beside it don't fit), "Draw the way ahead with your S Pen.", the three inks drawn with their pressures, then **Start** (nothing cleared yet) or **Play**, naming the first stage not yet cleared (with every stage cleared it opens the list), and **Stages**, **How to play**, **Settings**, and **Notebook** in the footer (on a phone too narrow for both, the footer's note shortens to "S PEN ONLY"). Fades in over 0.5 s; Android back leaves the app |
| Stage list | a **Menu** button at the top left goes back to the title, as does Android back. Every world's tab opens, locked or not (a padlock marks a locked world), to show its stage names; only open stages play (in debug builds, and the desktop launcher, every stage is open). Opened from a stage it shows that stage's world |
| World peek | behind the stage list, the tab's world: its finale stage, built but never stepped, without notes or the runner, panned at 2.6 m/s from start to finish and round again (a fade at the turn), its ground 84 % down a tall screen (under the card) or 70 % down a wide one; the page and palette are the world's (Midnight Ink's dark page), the veil over it 30 %, and the world's tune plays. Still with reduced motion |
| Menus | the title screen and the ending show only the ruled paper; Ink Rush plays (the Music slider turns it down or off) |
| Pause | the top-right button in a stage (two bars; ⋯ on the stage list), Android back, or leaving the app, during a stage's intro, play or a lost life's moment. A stroke being drawn is let go. The page dims; the card has the stage, **Resume**, **Ask Dot**, **Restart** (the stage from the start, keeping the lives), **Settings**, **Stage list** and **Tear out this page**. Android back on it resumes |
| Countdown | Resume mid-play counts 3, 2, 1, 0.5 s each, with a rising chime; the stage, its music and the pen stay held until it ends. Resuming during an intro skips it |
| Screen changes | the new screen fades in from the paper over 0.3 s (not with reduced motion) |
| Repository link | under the title screen's footer and near the end of the credits (`ui/Links.kt`); a tap opens it in the browser, or says the address in a toast if no browser opens |
| Ending | after 5-5 the clear card's button reads **The end**: the page clean, the runner rolling along a line that draws itself (5.5 s held), then the player's notebook (stages cleared, stars, gold stars, best scores added up) and the credits roll up at 42 dp/s to "Thanks for playing!". **Skip** jumps to the end; then **Back to the title**. **Credits** in the Notebook rolls the credits alone |
| Ask Dot | the next 2 strokes of the stage's scripted solution that reach past the runner and are due within 30 m, dotted every 0.28 m (0.08 m dots, pulsing) in their ink's colour with an arrow; each goes once the runner is 0.5 m past its end. In a boss fight, a toast with how that boss is beaten. With **Dot's hints** on, a second life lost within 6 m of an earlier one in the same attempt shows them by itself once play is back. Free |
| Boss face-off | when a boss's fight starts, once per go at the stage (not again after a lost life), until that boss is beaten; never under the desktop's `--autoplay`: bars over the top (0.55 × the bar) and bottom (16 % of the height, at least 64 dp), sliding in over 0.35 s; four lines, typed at 38 letters a second with a babble blip every 3 letters (Dot's higher, the boss's lower, at 35 % of the effects' volume). A tap finishes a line or moves on; **Skip** or Android back ends it. The stage holds meanwhile |
| Stickers | 16; slapped into the top-right margin 128 dp under the top of the safe area, sliding in over 0.25 s, on for 3.4 s, a gold-star chime; several queue. Ones the save already earns go in quietly at launch. None while doodling |
| Notebook | from the title: Stats, Stickers, Pages, Music and Doodle tabs, Credits and Done. Up to 420 dp wide (720 dp on a wide screen, where lists go in two columns); a tap outside closes it |
| Album | torn-out pages and shared result cards as PNGs in the app's storage, at most 1600 px on the long side with a 360 px copy for the grid; the newest 24 are kept. Written on a worker thread |
| Result card | 360 × 450 dp at the screen's density: the moment of the clear (the frame the clear card first shows, before the HUD and cards), framed round Dot, then the stage, three stars, score (and new best), lives lost and gold stars, signed by Dot. Sharing the same clear again offers the same card |
| Jukebox | "Each world's own", or a tune: a world's once its boss has a star, the boss theme once any boss has; the pick plays on the menus, in stages and in boss fights |
| Doodle mode | after the ending (from the start in debug builds and the desktop launcher): Endless ink, Half speed (stages run at 0.5×), Always Bold, Highlighter (everything hidden shows from the start), Cloud chalk, Gold nib and Star nib held all run. While any is on, a "DOODLE MODE" tag in the bottom-left corner, and stars, bests, gold stars and stickers aren't kept |
| Ink feel | on a stroke's pen-up: rope 8 ms at 60, a spring two 10 ms pulses at 120 (45 ms apart), a beam 30 ms at 230. Off with **Haptics**; nothing on the desktop |
| Theme | Settings → **Theme**: Auto (the phone's dark mode, followed live), Light or Dark; saved as `theme`. Midnight Ink's page stays dark whatever is picked |
| Volumes | Sound effects and Music sliders, 0 to 100 % in steps of 5 (snapping to off near the left); the music plays at 0.42 × its slider, effects at their slider. On/off switches saved by early builds carry over as 100 % or off |
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
| Helping | on a world's first stage (its first run), once the runner has stopped (under 0.3 m/s) at a gate it waits at, with nothing drawn, Dot steps out: 0.45 s from the runner up to a top corner below the HUD (the corner whose balloon stays 40 dp clear of the guide), growing to 30 dp across with its shadow falling on the page, the balloon fading in from 0.25 s. It says the stage's note for that gate (the last note within 30 m before it) and what to draw, with a dotted pointer to the guide, and looks at it. Once per gate per attempt. The runner on the page is a dashed outline meanwhile (an empty spot where Dot was, with a faint pool of ink), and notes wait. While the runner waits at a gate, its guide shows in full however far ahead it is, and the camera brings the whole guide into view with 0.8 m to spare (where a narrow screen can't show both, as at 2-1's and 4-1's long waits in portrait, the runner slips off the left edge until the stroke is drawn) |
| Going back in | a pen-down anywhere but its balloon (the stroke goes on as normal), **Got it**, Android back, or the gate done, the runner moving on or the stage's play ending: 0.35 s back to the runner, then an ink splash |
| Misses and waits | a stroke that wouldn't do the job is wiped and Dot steps out worried with why (the wipe's tip); 7 s waiting with nothing drawn and Dot knocks twice on the glass ("Hello? Draw along the dotted line...") unless it's already out explaining |
| Welcome | the very first launch, 1 s after the title appears: Dot steps off the title (a dashed outline left there) over a veil, looking at the player, in four pages (hello; it rolls, you draw; pressure makes the ink; first your touch, then a few lessons), any tap (or Android back) moving on. Then the lessons, which can't be skipped, then 1-1 (see [Tutorial](#tutorial)). Never again once the lessons are done (saved as `metDot`) |
| Title | sat at the end of the name (it rolls in), looking about, at the pen when it hovers, pleased while a button is held; "Hi! Grab your S Pen." after 1.1 s, then a tip every 8.5 s; dozes after 30 s alone |
| Pause | peeks over the top of the pause card with a line ("Take your time."), dozes after 6 s |
| Ending | rolls along the line wide-mouthed, then "We did it!" and a wink |


