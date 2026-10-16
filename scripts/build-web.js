// Copies the plain web files into www/ so Capacitor ships only the app,
// not node_modules, android/, or repo files. No bundler needed.
const fs = require('fs');
const path = require('path');

const root = path.join(__dirname, '..');
const out = path.join(root, 'www');
const files = [
  'index.html',
  'core.js',
  'manifest.json',
  'icon-192.png',
  'icon-512.png',
  'icon-512-maskable.png'
];

fs.rmSync(out, { recursive: true, force: true });
fs.mkdirSync(out, { recursive: true });
for (const f of files) fs.copyFileSync(path.join(root, f), path.join(out, f));
console.log('www/ ready (' + files.length + ' files)');
