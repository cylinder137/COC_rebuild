#!/usr/bin/env node
/**
 * 造物集（coc-platform）Scratch 打包服务  —  port 8091
 * =====================================================================
 * 把 .sb / .sb2 / .sb3 打包成单文件 HTML 播放器，供后端的 /run/{project_id}/
 * 直出。默认走 @turbowarp/packager（实测本项目 demo 约 55 ms / 1.8 MB），
 * 因此 POST /pack 采用「同步 + 软超时」策略，保证 4 秒内一定应答。
 *
 * 接口
 *   GET  /health -> {status:'ok', packager:'turbowarp'|'fallback', version}
 *   POST /pack   -> 见 README.md
 *
 * 后端实际发送的 body（以它为权威）：
 *   {project_id, project_version_id, project_file_id, file_name,
 *    source_path, output_dir, file_type}
 * 同时兼容旧字段名 storage_path / version_no / build_dir。
 *
 * 产物固定写到 <output_dir>/index.html。
 *
 * 降级保证：打包失败 / 超时 / 源码不是 Scratch 文件时，立刻写入占位播放器，
 * 返回 {ok:true, fallback:true}，并在后台重试真实打包（成功则覆盖产物）。
 * 后端因此永远不会因为打包卡住 —— 最坏情况是「能跑，但只是占位页」。
 */
'use strict';

const http = require('http');
const fs = require('fs');
const path = require('path');

const PKG = require('./package.json');
const PORT = Number(process.env.COC_PACKER_PORT || 8091);
const HOST = process.env.COC_PACKER_HOST || '127.0.0.1';

// 后端 4s 超时；我们最多用 3000ms 自己做同步打包，留 1s 余量给网络与写盘
const SOFT_TIMEOUT_MS = Number(process.env.COC_PACKER_SOFT_TIMEOUT_MS || 3000);
const MAX_BODY_BYTES = 2 * 1024 * 1024;

const SCRATCH_EXT = new Set(['.sb', '.sb2', '.sb3']);

// ---------------------------------------------------------------- packager
let Packager = null;
let packagerState = 'fallback';   // 'turbowarp' | 'fallback'
let packagerError = null;
try {
  Packager = require('@turbowarp/packager');
  if (typeof Packager.loadProject === 'function' && typeof Packager.Packager === 'function') {
    packagerState = 'turbowarp';
  } else {
    packagerError = 'unexpected @turbowarp/packager exports';
  }
} catch (e) {
  packagerError = e && e.message;
}

// ---------------------------------------------------------------- logging
function log(...args) {
  const ts = new Date().toISOString().replace('T', ' ').slice(0, 19);
  console.log(`[${ts}]`, ...args);
}

// ---------------------------------------------------------------- helpers
function json(res, status, obj) {
  const body = JSON.stringify(obj);
  res.writeHead(status, {
    'Content-Type': 'application/json; charset=utf-8',
    'Content-Length': Buffer.byteLength(body),
    'Cache-Control': 'no-store'
  });
  res.end(body);
}

function fallbackHtml(info) {
  const esc = (s) => String(s == null ? '' : s).replace(/[&<>"]/g, (c) => (
    { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]));
  return `<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>${esc(info.title || 'Scratch 项目')}</title>
<style>
  html,body{margin:0;height:100%;font-family:"Microsoft YaHei",system-ui,sans-serif;
    background:#0b1026;color:#e8ecff;display:flex;align-items:center;justify-content:center}
  .card{max-width:600px;padding:34px 38px;border:1px solid #4F6EF7;border-radius:16px;
    background:linear-gradient(160deg,rgba(79,110,247,.18),rgba(11,16,38,.92));text-align:center}
  h1{margin:0 0 10px;font-size:20px}
  p{margin:10px 0 0;line-height:1.75;font-size:14px;color:#b9c6ee}
  code{background:rgba(123,211,255,.14);padding:2px 6px;border-radius:6px;color:#7BD3FF}
  .tag{display:inline-block;margin-top:20px;padding:4px 12px;border-radius:999px;
    background:rgba(255,122,69,.18);color:#FFB08A;font-size:12px}
  .k{color:#8fa2d8}
</style>
</head>
<body>
  <div class="card">
    <h1>Scratch 打包占位播放器</h1>
    <p>这个页面由 coc-platform 打包服务在<b>降级模式</b>下生成：
       真实的单文件播放器（Turbowarp）这次没能产出。</p>
    <p class="k">项目：<code>${esc(info.title || '-')}</code> ·
       文件：<code>${esc(info.fileName || '-')}</code> ·
       时间：<code>${esc(new Date().toISOString().slice(0, 19).replace('T', ' '))}</code></p>
    <p class="k">原因：<code>${esc(info.reason || 'unknown')}</code></p>
    <p>管理员可以在后台点「重新打包」（<code>/projects/{id}/build/retry</code>）再试一次。</p>
    <div class="tag">packer fallback mode</div>
  </div>
</body>
</html>
`;
}

function logToDir(outputDir, lines) {
  try {
    fs.mkdirSync(outputDir, { recursive: true });
    fs.appendFileSync(path.join(outputDir, 'pack.log'), lines.map((l) => `[${new Date().toISOString()}] ${l}`).join('\n') + '\n');
  } catch (_) { /* logging must never break packing */ }
}

// ---------------------------------------------------------------- core
async function runTurbowarp(sourcePath) {
  const data = fs.readFileSync(sourcePath);
  const t0 = Date.now();
  const loaded = await Packager.loadProject(data);
  const t1 = Date.now();
  const packager = new Packager.Packager();
  packager.project = loaded;
  const result = await packager.package();
  const t2 = Date.now();
  return { html: result.data, loadMs: t1 - t0, packMs: t2 - t1, totalMs: t2 - t0 };
}

function raceTimeout(promise, ms) {
  let timer;
  const timeout = new Promise((_, reject) => {
    timer = setTimeout(() => reject(new Error(`soft timeout after ${ms}ms`)), ms);
  });
  return Promise.race([promise, timeout]).finally(() => clearTimeout(timer));
}

/**
 * Returns { ok, fallback, outputPath, ms, size, reason }
 * Always writes <outputDir>/index.html before returning.
 */
async function pack(req) {
  const started = Date.now();
  const sourcePath = req.source_path || req.storage_path;
  const outputDir = req.output_dir || req.build_dir
    || (req.project_id != null && req.version_no != null
      ? path.resolve(__dirname, '..', '.data', 'projects', String(req.project_id), 'v' + req.version_no, 'build')
      : null);
  const fileName = req.file_name || (sourcePath ? path.basename(sourcePath) : null);
  const outputPath = outputDir ? path.join(outputDir, 'index.html') : null;
  const ext = fileName ? path.extname(fileName).toLowerCase() : (sourcePath ? path.extname(sourcePath).toLowerCase() : '');

  const base = {
    ok: true,
    fallback: false,
    output_path: outputPath,
    build_path: outputPath,
    output_dir: outputDir,
    source_path: sourcePath,
    project_id: req.project_id,
    packager: packagerState
  };

  if (!outputDir) {
    return { ...base, ok: false, fallback: false, reason: 'output_dir could not be derived', ms: Date.now() - started };
  }
  if (!sourcePath || !fs.existsSync(sourcePath)) {
    log(`WARN source not found: ${sourcePath}`);
    fs.mkdirSync(outputDir, { recursive: true });
    fs.writeFileSync(outputPath, fallbackHtml({
      title: req.title || ('project ' + req.project_id),
      fileName, reason: 'source file not found'
    }));
    return { ...base, ok: false, fallback: true, reason: 'source file not found', ms: Date.now() - started };
  }
  if (!SCRATCH_EXT.has(ext)) {
    // html / structure projects never need packing
    return { ...base, ok: false, fallback: false, reason: `not a scratch file (${ext || 'no extension'})`, ms: Date.now() - started };
  }

  fs.mkdirSync(outputDir, { recursive: true });

  if (packagerState !== 'turbowarp') {
    fs.writeFileSync(outputPath, fallbackHtml({
      title: req.title || ('project ' + req.project_id), fileName,
      reason: 'turbowarp packager unavailable: ' + (packagerError || 'unknown')
    }));
    logToDir(outputDir, [`fallback placeholder written (packager unavailable: ${packagerError || 'unknown'})`]);
    return { ...base, fallback: true, reason: 'packager unavailable', ms: Date.now() - started };
  }

  try {
    const r = await raceTimeout(runTurbowarp(sourcePath), SOFT_TIMEOUT_MS);
    fs.writeFileSync(outputPath, r.html);
    const size = Buffer.byteLength(r.html);
    logToDir(outputDir, [
      `turbowarp ok: ${fileName} -> index.html (${size} bytes)`,
      `loadProject=${r.loadMs}ms package=${r.packMs}ms total=${r.totalMs}ms`
    ]);
    log(`PACK ok project=${req.project_id} file=${fileName} ${size} bytes in ${Date.now() - started}ms`);
    return { ...base, fallback: false, ms: Date.now() - started, size, engine_ms: r.totalMs };
  } catch (e) {
    const reason = (e && e.message) || String(e);
    log(`PACK fallback project=${req.project_id} file=${fileName}: ${reason}`);
    fs.writeFileSync(outputPath, fallbackHtml({
      title: req.title || ('project ' + req.project_id), fileName, reason
    }));
    logToDir(outputDir, [`fallback placeholder written (reason: ${reason})`]);
    // best-effort background retry without the soft timeout
    setTimeout(() => {
      runTurbowarp(sourcePath).then((r2) => {
        fs.writeFileSync(outputPath, r2.html);
        logToDir(outputDir, [`background retry succeeded: index.html (${Buffer.byteLength(r2.html)} bytes)`]);
        log(`PACK background retry ok project=${req.project_id} in ${r2.totalMs}ms`);
      }).catch((e2) => {
        logToDir(outputDir, [`background retry failed: ${(e2 && e2.message) || e2}`]);
      });
    }, 0).unref();
    return { ...base, fallback: true, reason, ms: Date.now() - started };
  }
}

// ---------------------------------------------------------------- server
const server = http.createServer((req, res) => {
  if (req.method === 'GET' && (req.url === '/health' || req.url === '/healthz')) {
    return json(res, 200, {
      status: 'ok',
      packager: packagerState,
      version: PKG.version,
      soft_timeout_ms: SOFT_TIMEOUT_MS,
      packager_error: packagerError,
      port: PORT
    });
  }

  if (req.method === 'GET' && req.url === '/') {
    return json(res, 200, {
      service: 'coc-platform packer',
      version: PKG.version,
      endpoints: ['GET /health', 'POST /pack'],
      packager: packagerState
    });
  }

  if (req.method === 'POST' && (req.url === '/pack' || req.url === '/pack/')) {
    let body = '';
    let tooBig = false;
    req.on('data', (c) => {
      body += c;
      if (body.length > MAX_BODY_BYTES) { tooBig = true; req.destroy(); }
    });
    req.on('end', async () => {
      if (tooBig) return json(res, 413, { ok: false, fallback: false, reason: 'body too large' });
      let payload;
      try {
        payload = body ? JSON.parse(body) : {};
      } catch (e) {
        return json(res, 400, { ok: false, fallback: false, reason: 'invalid JSON body' });
      }
      try {
        const r = await pack(payload);
        log(`POST /pack project=${payload.project_id} ok=${r.ok} fallback=${r.fallback} ${r.ms}ms`);
        return json(res, 200, r);
      } catch (e) {
        log('ERROR /pack:', (e && e.stack) || e);
        return json(res, 200, {
          ok: false, fallback: false,
          reason: (e && e.message) || String(e),
          output_path: null, build_path: null
        });
      }
    });
    return;
  }

  json(res, 404, { ok: false, reason: 'not found' });
});

server.listen(PORT, HOST, () => {
  log(`coc-platform packer v${PKG.version} listening on http://${HOST}:${PORT}`);
  log(`packager=${packagerState}${packagerError ? ' (' + packagerError + ')' : ''} softTimeout=${SOFT_TIMEOUT_MS}ms`);
  log('GET /health  ·  POST /pack');
});

process.on('SIGINT', () => { log('SIGINT, shutting down'); server.close(() => process.exit(0)); });
process.on('SIGTERM', () => { log('SIGTERM, shutting down'); server.close(() => process.exit(0)); });
