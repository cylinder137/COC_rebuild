#!/usr/bin/env node
/**
 * coc-platform packer : command line helper
 *
 *   node pack-cli.js <input.sb|sb2|sb3|project.json> [output.html]
 *
 * Packs a Scratch project into a single-file HTML player using
 * @turbowarp/packager (the same code path as POST /pack), and prints timing
 * for troubleshooting the 4s budget the backend allows.
 *
 * Exit codes: 0 ok, 1 failure.
 */
const fs = require('fs');
const path = require('path');

const input = process.argv[2];
if (!input) {
  console.error('usage: node pack-cli.js <input.sb3> [output.html]');
  process.exit(2);
}
const output = process.argv[3] || path.join(path.dirname(input), 'build', 'index.html');

(async () => {
  const Packager = require('@turbowarp/packager');
  const t0 = Date.now();
  const data = fs.readFileSync(input);
  const loaded = await Packager.loadProject(data);
  const t1 = Date.now();

  const packager = new Packager.Packager();
  packager.project = loaded;
  const result = await packager.package();
  const t2 = Date.now();

  fs.mkdirSync(path.dirname(output), { recursive: true });
  fs.writeFileSync(output, result.data);
  const size = fs.statSync(output).size;

  console.log(`[pack-cli] ok
  input      : ${input} (${data.length} bytes)
  output     : ${output} (${size} bytes)
  target     : ${packager.options.target}
  loadProject: ${t1 - t0} ms
  package    : ${t2 - t1} ms
  total      : ${t2 - t0} ms
  packager   : turbowarp`);
})().catch((e) => {
  console.error('[pack-cli] failed:', (e && e.stack) || e);
  process.exit(1);
});
