#!/usr/bin/env node
/**
 * coc-platform ops/seed : generate the demo project payloads
 *
 * Writes:
 *   .data/projects/201/v1/source/index.html   published html (playable Canvas game)
 *   .data/projects/202/v1/source/index.html   html waiting for review
 *   .data/projects/203/v1/source/project.sb3  scratch (valid minimal sb3)
 *   .data/projects/203/v1/build/index.html    scratch run target (placeholder if not packed yet)
 *   .data/projects/204/v1/source/project.sb3  scratch
 *   .data/projects/204/v1/build/index.html
 *   .data/projects/205/v1/source/tavern.mcstructure  structure
 *   .data/projects/206/v1/source/index.html   downed html
 * and then emits ops/seed/assets/files.generated.sql with the real
 * file_size / sha256 / storage_path of every source file, so seed.sql stays
 * consistent no matter when it runs.
 *
 * No external dependency: the tiny zip writer below uses STORE (no deflate),
 * exactly the way the script runs.
 *
 * Usage: node gen-demo-files.js [repoRoot]
 */
const fs = require('fs');
const path = require('path');
const crypto = require('crypto');

const repoRoot = process.argv[2] || path.resolve(__dirname, '..', '..');
const DATA = path.join(repoRoot, '.data');
const DEMO = path.join(__dirname, 'assets', 'demo');

const sha256 = (buf) => crypto.createHash('sha256').update(buf).digest('hex');
const md5 = (buf) => crypto.createHash('md5').update(buf).digest('hex');

function pv(projectId, versionNo, kind) {
  const dir = path.join(DATA, 'projects', String(projectId), 'v' + versionNo, kind);
  fs.mkdirSync(dir, { recursive: true });
  return dir;
}

function write(abs, buf, { keepIfExists = false } = {}) {
  fs.mkdirSync(path.dirname(abs), { recursive: true });
  if (keepIfExists && fs.existsSync(abs)) return false;
  fs.writeFileSync(abs, buf);
  return true;
}

// ---------------------------------------------------------------- zip (store)
const CRC_TABLE = (() => {
  const t = new Int32Array(256);
  for (let n = 0; n < 256; n++) {
    let c = n;
    for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
    t[n] = c;
  }
  return t;
})();
function crc32(buf) {
  let c = 0xffffffff;
  for (let i = 0; i < buf.length; i++) c = CRC_TABLE[(c ^ buf[i]) & 0xff] ^ (c >>> 8);
  return (c ^ 0xffffffff) >>> 0;
}
function zip(files) {
  const locals = [], centrals = [];
  let offset = 0;
  for (const f of files) {
    const name = Buffer.from(f.name, 'utf8');
    const data = Buffer.isBuffer(f.data) ? f.data : Buffer.from(f.data, 'utf8');
    const crc = crc32(data);
    const lh = Buffer.alloc(30);
    lh.writeUInt32LE(0x04034b50, 0); lh.writeUInt16LE(20, 4); lh.writeUInt16LE(0x0800, 6);
    lh.writeUInt16LE(0, 8); lh.writeUInt16LE(0, 10); lh.writeUInt16LE(0x21, 12);
    lh.writeUInt32LE(crc, 14); lh.writeUInt32LE(data.length, 18); lh.writeUInt32LE(data.length, 22);
    lh.writeUInt16LE(name.length, 26); lh.writeUInt16LE(0, 28);
    locals.push(lh, name, data);
    const ch = Buffer.alloc(46);
    ch.writeUInt32LE(0x02014b50, 0); ch.writeUInt16LE(20, 4); ch.writeUInt16LE(20, 6);
    ch.writeUInt16LE(0x0800, 8); ch.writeUInt16LE(0, 10); ch.writeUInt16LE(0, 12); ch.writeUInt16LE(0x21, 14);
    ch.writeUInt32LE(crc, 16); ch.writeUInt32LE(data.length, 20); ch.writeUInt32LE(data.length, 24);
    ch.writeUInt16LE(name.length, 28); ch.writeUInt16LE(0, 30); ch.writeUInt16LE(0, 32);
    ch.writeUInt16LE(0, 34); ch.writeUInt16LE(0, 36); ch.writeUInt32LE(0, 38); ch.writeUInt32LE(offset, 42);
    centrals.push(ch, name);
    offset += 30 + name.length + data.length;
  }
  const cd = Buffer.concat(centrals);
  const eocd = Buffer.alloc(22);
  eocd.writeUInt32LE(0x06054b50, 0);
  eocd.writeUInt16LE(files.length, 8); eocd.writeUInt16LE(files.length, 10);
  eocd.writeUInt32LE(cd.length, 12); eocd.writeUInt32LE(offset, 16);
  return Buffer.concat([...locals, cd, eocd]);
}

// ------------------------------------------------------------ minimal sb3
function svgStage() {
  return '<svg version="1.1" width="480" height="360" xmlns="http://www.w3.org/2000/svg">' +
    '<defs><linearGradient id="g" x1="0" y1="0" x2="1" y2="1">' +
    '<stop offset="0%" stop-color="#0b1026"/><stop offset="100%" stop-color="#1b2a5e"/></linearGradient></defs>' +
    '<rect width="480" height="360" fill="url(#g)"/>' +
    '<circle cx="380" cy="80" r="34" fill="#FFD666" opacity="0.9"/>' +
    '</svg>';
}
function svgSprite() {
  return '<svg version="1.1" width="96" height="96" xmlns="http://www.w3.org/2000/svg">' +
    '<circle cx="48" cy="48" r="30" fill="#4F6EF7"/>' +
    '<circle cx="38" cy="41" r="7" fill="#fff"/><circle cx="58" cy="41" r="7" fill="#fff"/>' +
    '<path d="M34 60 q14 14 28 0" stroke="#fff" stroke-width="4" fill="none" stroke-linecap="round"/>' +
    '</svg>';
}
function makeSb3(title) {
  const stageSvg = Buffer.from(svgStage(), 'utf8');
  const spriteSvg = Buffer.from(svgSprite(), 'utf8');
  const stageMd5 = md5(stageSvg), spriteMd5 = md5(spriteSvg);

  const project = {
    targets: [
      {
        isStage: true, name: 'Stage',
        variables: {}, lists: {}, broadcasts: {}, blocks: {}, comments: {},
        currentCostume: 0,
        costumes: [{ assetId: stageMd5, name: 'backdrop1', md5ext: stageMd5 + '.svg', dataFormat: 'svg', rotationCenterX: 240, rotationCenterY: 180 }],
        sounds: [], volume: 100, layerOrder: 0, tempo: 60, videoTransparency: 50,
        videoState: 'off', textToSpeechLanguage: null
      },
      {
        isStage: false, name: title,
        variables: {}, lists: {}, broadcasts: {}, comments: {},
        currentCostume: 0,
        costumes: [{ assetId: spriteMd5, name: 'costume1', md5ext: spriteMd5 + '.svg', dataFormat: 'svg', rotationCenterX: 48, rotationCenterY: 48 }],
        sounds: [], volume: 100, layerOrder: 1,
        visible: true, x: 0, y: 0, size: 100, direction: 90, draggable: false, rotationStyle: 'all around',
        blocks: {
          b1: { opcode: 'event_whenflagclicked', next: 'b2', parent: null, inputs: {}, fields: {}, shadow: false, topLevel: true, x: 60, y: 60 },
          b2: { opcode: 'motion_gotoxy', next: 'b3', parent: 'b1', inputs: { X: [1, [4, '0']], Y: [1, [4, '0']] }, fields: {}, shadow: false, topLevel: false },
          b3: { opcode: 'motion_glidesecstoxy', next: 'b4', parent: 'b2', inputs: { SECS: [1, [4, '1']], X: [1, [4, '120']], Y: [1, [4, '80']] }, fields: {}, shadow: false, topLevel: false },
          b4: { opcode: 'looks_sayforsecs', next: 'b5', parent: 'b3', inputs: { MESSAGE: [1, [10, '你好，我是造物集 demo 项目！']], SECS: [1, [4, '2']] }, fields: {}, shadow: false, topLevel: false },
          b5: { opcode: 'motion_glidesecstoxy', next: null, parent: 'b4', inputs: { SECS: [1, [4, '1']], X: [1, [4, '-120']], Y: [1, [4, '-60']] }, fields: {}, shadow: false, topLevel: false }
        }
      }
    ],
    monitors: [], extensions: [],
    meta: { semver: '3.0.0', vm: '0.2.0', agent: 'coc-platform ops seed' }
  };

  return zip([
    { name: 'project.json', data: Buffer.from(JSON.stringify(project), 'utf8') },
    { name: stageMd5 + '.svg', data: stageSvg },
    { name: spriteMd5 + '.svg', data: spriteSvg }
  ]);
}

// ------------------------------------------- .mcstructure (NBT-ish, little endian)
function makeMcStructure(name) {
  const buf = Buffer.alloc(16 + name.length);
  buf.writeUInt8(10, 0);              // TAG_Compound
  buf.writeUInt16LE(name.length, 1);
  buf.write(name, 3, 'utf8');         // root name
  // placeholder body (the real format is little-endian NBT; the demo never parses it)
  buf.writeUInt8(3, 3 + name.length); // TAG_Int
  buf.writeUInt16LE(4, 4 + name.length);
  buf.write('size', 6 + name.length, 'utf8');
  buf.writeInt32LE(11, 10 + name.length);
  buf.writeUInt8(0, 14 + name.length); // TAG_End
  return buf;
}

// ------------------------------------------------------------------ run
const files = [
  { id: 201, type: 'html', version: 1, src: 'html-published.html', name: 'index.html' },
  { id: 202, type: 'html', version: 1, src: 'html-pending.html', name: 'index.html' },
  { id: 203, type: 'scratch', version: 1, sb3Name: '小猫走迷宫', name: 'project.sb3' },
  { id: 204, type: 'scratch', version: 1, sb3Name: '打地鼠小游戏', name: 'project.sb3' },
  { id: 205, type: 'structure', version: 1, structure: 'tavern', name: 'tavern.mcstructure' },
  { id: 206, type: 'html', version: 1, src: 'html-downed.html', name: 'index.html' }
];

const rows = [];
const placeholders = [];

for (const f of files) {
  const dir = pv(f.id, f.version, 'source');
  const abs = path.join(dir, f.name);
  let buf;
  if (f.src) buf = fs.readFileSync(path.join(DEMO, f.src));
  else if (f.sb3Name) buf = makeSb3(f.sb3Name);
  else buf = makeMcStructure(f.structure);
  write(abs, buf);
  rows.push({ id: f.id, path: abs, size: buf.length, sha: sha256(buf) });

  if (f.type === 'scratch') {
    const buildAbs = path.join(pv(f.id, f.version, 'build'), 'index.html');
    const wrote = write(buildAbs, fs.readFileSync(path.join(DEMO, 'scratch-run-placeholder.html')), { keepIfExists: true });
    placeholders.push(path.basename(path.dirname(path.dirname(buildAbs))) + ' ' + (wrote ? '(placeholder written)' : '(existing build kept)'));
  }
}

// ---------------------------------------------- files.generated.sql
let sql = '-- AUTO-GENERATED by ops/seed/gen-demo-files.js -- do not edit by hand\n';
for (const r of rows) {
  sql += `SET @f${r.id}_path := '${r.path.replace(/\\/g, '/')}';\n`;
  sql += `SET @f${r.id}_size := ${r.size};\n`;
  sql += `SET @f${r.id}_sha  := '${r.sha}';\n`;
}
const sqlPath = path.join(__dirname, 'assets', 'files.generated.sql');
fs.writeFileSync(sqlPath, sql, 'utf8');

console.log('[gen-demo-files] demo payloads ready under ' + path.join(DATA, 'projects'));
for (const r of rows) console.log(`  - project ${r.id}: ${path.basename(r.path)} ${r.size} bytes  sha256=${r.sha.slice(0, 12)}...`);
placeholders.forEach((p) => console.log('  - build dir ' + p));
console.log('[gen-demo-files] wrote ' + sqlPath);
