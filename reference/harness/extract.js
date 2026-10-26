// Pulls the POC's InkCore (the first inline <script>) out of ../ink-mayhem-poc.html into inkcore.js.
const fs = require('fs');
const path = require('path');
const html = fs.readFileSync(path.join(__dirname, '..', 'ink-mayhem-poc.html'), 'utf8');
const m = html.match(/<script>\s*(\/\/ Inkwell physics core[\s\S]*?)<\/script>/);
if (!m) throw new Error('InkCore script block not found');
fs.writeFileSync(path.join(__dirname, 'inkcore.js'), m[1]);
console.log('wrote inkcore.js (%d bytes)', m[1].length);
