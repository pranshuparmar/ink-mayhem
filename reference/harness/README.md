# Planck reference harness

Runs the POC's own physics core (`InkCore`, extracted verbatim from `../ink-mayhem-poc.html`) on
planck.js 1.5.0 under Node. It produced the scenario definitions that `core/src/test` checks on
libGDX Box2D, and it is the first place to look when the two engines disagree.

```sh
cd reference/harness
npm install        # planck 1.5.0 only
npm run all        # extract InkCore, then run the crate, crossing and boulder scenarios
```

`scenarios.js` prints PASS/FAIL for each verified behaviour from the brief. `bridge.js` and
`runner.js` print raw numbers when run directly. The runner script is a minimal port of the POC's
`tick()` (torque drive, stall hop, spring bounce, boulder drive); the full game logic lives only in
the HTML.
