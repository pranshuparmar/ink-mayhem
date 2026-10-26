const pl = require('planck'); const V = pl.Vec2;
const InkCore = require('./inkcore.js');
function thick(p){return 0.06+0.39*Math.max(0,Math.min(1,p));}
// Mini port of the POC tick for runner + boulder over a single gap.
function cross({ p, gap, speed = 3.4, boulder = null, boulderV = null, secs = 8, startX = -6, strokeX0 = -0.4, strokeX1 = null, strokeY = null }) {
  const ink = InkCore(pl, { ANCHOR_R: 0.45, CULL_Y: 400, INK_K: 30 });
  ink.addRect(-40, 0, 40, 40); ink.addRect(gap, 0, 40, 40);
  let pendingBounce = false; const bb = new Set();
  ink.world.on('begin-contact', c => {
    const ua = c.getFixtureA().getUserData(), ub = c.getFixtureB().getUserData();
    let other = null; if (ua && ua.kind === 'runner') other = ub; else if (ub && ub.kind === 'runner') other = ua;
    if (other && other.kind === 'spring') pendingBounce = true;
    if (ua && ua.kind === 'boulder' && ub && ub.kind === 'spring') bb.add(c.getFixtureA().getBody());
    if (ub && ub.kind === 'boulder' && ua && ua.kind === 'spring') bb.add(c.getFixtureB().getBody());
  });
  let s = null;
  if (p != null) {
    const t = thick(p), y = strokeY ?? -(t/2 + 0.04), pts = [], x1 = strokeX1 ?? gap + 0.4;
    for (let x = strokeX0; x <= x1 + 1e-9; x += 0.05) pts.push({ x, y, p });
    s = ink.buildStroke(pts);
  }
  const R = 0.4, rb = ink.world.createDynamicBody({ position: V(startX, -R - 0.02), bullet: true });
  rb.createFixture(pl.Circle(R), { density: 2, friction: 1, restitution: 0.05, userData: { kind: 'runner' } });
  let bo = null;
  if (boulder != null) {
    bo = ink.world.createDynamicBody({ position: V(boulder, -0.9 - 0.05) });
    bo.createFixture(pl.Circle(0.9), { density: 2.5, friction: 1, restitution: 0.02, userData: { kind: 'boulder' } });
    bo.setLinearVelocity(V(boulderV ?? speed * 1.3, 0));
  }
  let stuck = 0, bounceCd = 0, simT = 0, maxBoVy = 0, flattened = false, snaps = 0, bounces = 0, boMinY = 0;
  for (let i = 0; i < secs * 60; i++) {
    const dt = 1/60; simT += dt;
    const w = rb.getAngularVelocity(), wt = speed / R;
    rb.applyTorque(Math.max(-7, Math.min(7, 3 * (wt - w))));
    const v = rb.getLinearVelocity();
    if (Math.abs(v.x) < 0.4 && Math.abs(v.y) < 0.6) { stuck += dt; if (stuck > 0.45) { const m = rb.getMass(); rb.applyLinearImpulse(V(1.6*m, -4.6*m), rb.getWorldCenter(), true); stuck = 0; } } else stuck = 0;
    if (bo) { const bw = bo.getAngularVelocity(), bt = speed * 1.4 / 0.9; bo.applyTorque(Math.max(-90, Math.min(90, 25 * (bt - bw)))); }
    ink.step(dt, 2);
    bounceCd -= dt;
    if (pendingBounce && bounceCd <= 0) { const v2 = rb.getLinearVelocity(); rb.setLinearVelocity(V(Math.max(v2.x, speed * 0.95), -9.6)); bounceCd = 0.3; bounces++; }
    pendingBounce = false;
    for (const b of bb) { if (b.bounceCd && simT < b.bounceCd) continue; const v3 = b.getLinearVelocity(); b.setLinearVelocity(V(Math.max(v3.x, speed * 1.3), -9.6)); b.bounceCd = simT + 0.3; }
    bb.clear();
    snaps += ink.events.length; ink.events.length = 0;
    if (bo) { const q = bo.getPosition(), pp = rb.getPosition(); maxBoVy = Math.min(maxBoVy, bo.getLinearVelocity().y); boMinY = Math.max(boMinY, q.y); if (Math.hypot(q.x - pp.x, q.y - pp.y) < 0.9 + 0.4 + 0.04) flattened = true; }
  }
  const pp = rb.getPosition();
  return { p, kind: p == null ? '-' : ink.kindOf(p), gap, rx: +pp.x.toFixed(2), ry: +pp.y.toFixed(2), crossed: pp.x > gap + 1 && pp.y < 1, fell: pp.y > 4, snaps, bounces,
    bo: bo ? { x: +bo.getPosition().x.toFixed(2), y: +bo.getPosition().y.toFixed(2), fell: bo.getPosition().y > 3, maxVy: +maxBoVy.toFixed(2) } : null, flattened };
}
module.exports = { cross };
if (require.main === module) {
  console.log('-- runner crossing 5.7 m');
  for (const p of [0.12, 0.2, 0.28, 0.38, 0.47, 0.56, 0.68, 0.8, 0.95]) console.log(JSON.stringify(cross({ p, gap: 5.7 })));
  console.log('-- boulder on rope (no runner interference: runner far ahead)');
  for (const g of [4.8, 5.3, 5.8]) for (const v of [4.4, 5.4, 6.5]) for (const p of [0.15, 0.25]) {
    const r = cross({ p, gap: g, speed: v / 1.3, boulder: -8, boulderV: v, startX: g + 3, secs: 6 });
    console.log(g, v, p, JSON.stringify(r.bo), 'snaps', r.snaps);
  }
}
