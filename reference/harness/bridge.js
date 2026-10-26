const pl = require('planck');
const InkCore = require('./inkcore.js');
function thick(p){return 0.06+0.39*Math.max(0,Math.min(1,p));}
function bridge(p, gap, mass, size=0.5, drop=0.05) {
  const ink = InkCore(pl, { ANCHOR_R: 0.45, CULL_Y: 400, INK_K: 30 });
  ink.addRect(-10, 0, 10, 40); ink.addRect(gap, 0, 10, 40);
  const t = thick(p), y = -(t/2 + 0.04), pts = [];
  for (let x = -0.4; x <= gap + 0.4 + 1e-9; x += 0.05) pts.push({x, y, p});
  const s = ink.buildStroke(pts);
  for (let i = 0; i < 60; i++) ink.step(1/60, 2);
  let midY = -1e9; // lowest point of the stroke near the middle
  for (const sg of s.segs) { const a = sg.body.getWorldPoint(sg.la); if (Math.abs(a.x - gap/2) < 0.4) midY = Math.max(midY, a.y); }
  const settleSnaps = ink.events.length; ink.events.length = 0;
  const crate = ink.addCrate(gap/2, midY - t/2 - size/2 - drop, size, mass/(size*size));
  let snaps = 0, minLoadRatio = 0;
  for (let i = 0; i < 300; i++) { ink.step(1/60, 2); snaps += ink.events.length; ink.events.length = 0; }
  // peak load ratio across joints
  let peak = 0; for (const j of s.joints) { const u = j.getUserData(); if (!u.broken) peak = Math.max(peak, u.peak / u.strength); }
  const cy = crate.body.getPosition().y;
  return { kind: ink.kindOf(p), p, gap, mass, segs: s.segs.length, bodies: s.bodies.length, joints: s.joints.length, anchors: s.anchors.length, settleSnaps, snaps, cy: +cy.toFixed(2), held: cy < 2, peak: +peak.toFixed(2) };
}
module.exports = { bridge, thick };
if (require.main === module) {
  for (const p of [0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.85, 1.0])
    for (const m of [0.25, 1.9, 9.7])
      console.log(JSON.stringify(bridge(p, 5, m)));
}
