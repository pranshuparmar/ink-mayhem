// The verified behaviours from the brief, as run on the POC engine. core/src/test mirrors these.
const { bridge } = require('./bridge.js');
const { cross } = require('./runner.js');
const P = { rope: [0.12, 0.2, 0.28], spring: [0.38, 0.47, 0.56], beam: [0.68, 0.8, 0.95] };
const line = (label, ok) => console.log(`${ok ? 'PASS' : 'FAIL'}  ${label}`);

// Crates of 0.5 m dropped 2 m onto a 5 m bridge drawn 0.4 m past each edge.
const crate = (k, m) => P[k].map(p => bridge(p, 5, m, 0.5, 2).held);
line('rope holds 0.25 kg', crate('rope', 0.25).every(Boolean));
line('rope snaps under 1.9 kg', crate('rope', 1.9).every(h => !h));
line('spring holds 1.9 kg', crate('spring', 1.9).every(Boolean));
line('spring snaps under 9.7 kg', crate('spring', 9.7).every(h => !h));
line('beam holds 9.7 kg', crate('beam', 9.7).every(Boolean));

// Edge-to-edge strokes 0.05 m above ground (a thick beam lying on the ground is a wall; see docs).
const all = [...P.rope, ...P.spring, ...P.beam];
line('runner crosses rope/spring/beam bridges of 5.5–6.0 m',
  [5.5, 5.7, 6.0].every(g => all.every(p => cross({ p, gap: g, strokeX0: 0, strokeX1: g, strokeY: -0.05 }).crossed)));
line('boulder at 4.4–6.5 m/s falls through rope bridges of 4.8–5.8 m',
  [4.8, 5.3, 5.8].every(g => [4.4, 5.4, 6.5].every(v => [0.15, 0.25].every(p =>
    cross({ p, gap: g, speed: v / 1.3, boulder: -8, boulderV: v, startX: g + 3, secs: 6 }).bo.fell))));
line('8 m beam carries runner and boulder',
  [7.6, 8.0].every(g => [0.7, 0.85, 1.0].every(p => {
    const r = cross({ p, gap: g, strokeX0: 0, strokeX1: g, strokeY: -0.05, startX: -2, boulder: -14, secs: 7 });
    return r.crossed && !r.bo.fell;
  })));
line('springs launch the boulder (vy reaches -9.6)',
  [4.8, 5.8].every(g => P.spring.every(p => [4.4, 6.5].every(v =>
    cross({ p, gap: g, strokeX0: 0, strokeX1: g, strokeY: -0.05, speed: v / 1.3, startX: g + 10, boulder: -8, boulderV: v, secs: 5 }).bo.maxVy <= -9.59))));
