#!/usr/bin/env node
/**
 * coc-platform ops/seed : placeholder cover generator
 *
 * Writes gradient/plain PNG covers (no external dependency: raw PNG encoder on
 * top of zlib). Each file is written to two places:
 *   ops/seed/assets/covers/cover-XX.png   (versioned source of truth)
 *   <repo>/.data/covers/cover-XX.png      (runtime location used by cover_path)
 *
 * Usage: node gen-covers.js [repoRoot]
 */
const fs = require('fs');
const path = require('path');
const zlib = require('zlib');

const W = 640, H = 360;

// ---- minimal PNG (8-bit RGB, no alpha) -------------------------------------
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

function chunk(type, data) {
  const len = Buffer.alloc(4);
  len.writeUInt32BE(data.length, 0);
  const typeBuf = Buffer.from(type, 'ascii');
  const crcBuf = Buffer.alloc(4);
  crcBuf.writeUInt32BE(crc32(Buffer.concat([typeBuf, data])), 0);
  return Buffer.concat([len, typeBuf, data, crcBuf]);
}

function encodePng(width, height, pixelAt) {
  const raw = Buffer.alloc(height * (1 + width * 3));
  let o = 0;
  for (let y = 0; y < height; y++) {
    raw[o++] = 0; // filter: none
    for (let x = 0; x < width; x++) {
      const [r, g, b] = pixelAt(x, y);
      raw[o++] = r & 0xff;
      raw[o++] = g & 0xff;
      raw[o++] = b & 0xff;
    }
  }
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(width, 0);
  ihdr.writeUInt32BE(height, 4);
  ihdr[8] = 8;   // bit depth
  ihdr[9] = 2;   // colour type: truecolour
  ihdr[10] = 0; ihdr[11] = 0; ihdr[12] = 0;
  return Buffer.concat([
    Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
    chunk('IHDR', ihdr),
    chunk('IDAT', zlib.deflateSync(raw, { level: 9 })),
    chunk('IEND', Buffer.alloc(0))
  ]);
}

function hex(h) {
  return [parseInt(h.slice(1, 3), 16), parseInt(h.slice(3, 5), 16), parseInt(h.slice(5, 7), 16)];
}

// ---- the six demo covers ---------------------------------------------------
// brand colour of the platform is #4F6EF7
const COVERS = [
  { file: 'cover-01.png', from: '#4F6EF7', to: '#7BD3FF', label: 'bounce' },
  { file: 'cover-02.png', from: '#FF7A45', to: '#FFD666', label: 'firework' },
  { file: 'cover-03.png', from: '#36CFC9', to: '#B5F5EC', label: 'maze' },
  { file: 'cover-04.png', from: '#9254DE', to: '#D3ADF7', label: 'mole' },
  { file: 'cover-05.png', from: '#8B6B4A', to: '#D9C7A7', label: 'tavern' },
  { file: 'cover-06.png', from: '#8C8C8C', to: '#D9D9D9', label: 'downed' }
];

const repoRoot = process.argv[2] || path.resolve(__dirname, '..', '..');
const assetDir = path.join(__dirname, 'assets', 'covers');
const dataDir = path.join(repoRoot, '.data', 'covers');

for (const d of [assetDir, dataDir]) fs.mkdirSync(d, { recursive: true });

const written = [];
COVERS.forEach((c) => {
  const a = hex(c.from), b = hex(c.to);
  const png = encodePng(W, H, (x, y) => {
    const t = (x / W) * 0.65 + (y / H) * 0.35;
    const band = 0.06 * Math.sin((x + y) / 46);
    const k = Math.min(1, Math.max(0, t + band));
    let r = Math.round(a[0] + (b[0] - a[0]) * k);
    let g = Math.round(a[1] + (b[1] - a[1]) * k);
    let bl = Math.round(a[2] + (b[2] - a[2]) * k);
    // subtle diagonal grid so the placeholder looks intentional
    if ((x + y) % 64 < 2 || (x - y + 10000) % 64 < 2) { r = Math.min(255, r + 14); g = Math.min(255, g + 14); bl = Math.min(255, bl + 14); }
    return [r, g, bl];
  });
  fs.writeFileSync(path.join(assetDir, c.file), png);
  fs.writeFileSync(path.join(dataDir, c.file), png);
  written.push(`${c.file} (${png.length} bytes, ${c.label})`);
});

console.log('[gen-covers] wrote ' + written.length + ' covers');
console.log('  source : ' + assetDir);
console.log('  runtime: ' + dataDir);
written.forEach((w) => console.log('  - ' + w));
