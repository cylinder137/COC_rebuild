#!/usr/bin/env node
/**
 * coc-platform ops/seed : BCrypt password hash generator
 *
 * Uses `bcryptjs` (pure JS, no native build). The resulting hash is what the
 * Spring Boot backend (BCryptPasswordEncoder-compatible) verifies.
 *
 * Usage:
 *   node gen-hash.js                 -> hash for the default trial password
 *   node gen-hash.js "MyPass123"     -> hash for a custom password
 *   node gen-hash.js "MyPass123" sql -> prints: SET @pw := '<hash>';  (for seed.sql)
 *   node gen-hash.js "MyPass123" verify <hash>  -> checks a hash against a password
 */
const bcrypt = require('bcryptjs');

const DEFAULT_PASSWORD = 'Coc@2026';
const ROUNDS = 10;

const args = process.argv.slice(2);
const mode = (args[1] || 'plain').toLowerCase();

if (mode === 'verify') {
  const hash = args[2];
  if (!hash) {
    console.error('usage: node gen-hash.js <password> verify <bcrypt-hash>');
    process.exit(2);
  }
  const ok = bcrypt.compareSync(args[0] || DEFAULT_PASSWORD, hash);
  console.log(ok ? 'MATCH' : 'NO-MATCH');
  process.exit(ok ? 0 : 1);
}

const password = args[0] || DEFAULT_PASSWORD;
const hash = bcrypt.hashSync(password, bcrypt.genSaltSync(ROUNDS));

if (mode === 'sql') {
  console.log("SET @pw := '" + hash + "';");
} else if (mode === 'json') {
  console.log(JSON.stringify({ password, rounds: ROUNDS, hash }, null, 2));
} else {
  console.log(hash);
}
