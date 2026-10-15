// coc-platform WebSocket 自测（Node 22 内置 WebSocket + fetch）
const BASE = 'http://127.0.0.1:8090/api/v1';
const WS = 'ws://127.0.0.1:8090/ws';
const log = (...a) => console.log(...a);

async function api(method, path, body, token) {
  const headers = {};
  if (body) headers['Content-Type'] = 'application/json';
  if (token) headers['Authorization'] = 'Bearer ' + token;
  const r = await fetch(BASE + path, { method, headers, body: body ? JSON.stringify(body) : undefined });
  const txt = await r.text();
  let json = null;
  try { json = JSON.parse(txt); } catch {}
  return { status: r.status, json, txt };
}

const dev = await api('POST', '/auth/login', { qq_account: '900001', password: 'selftest123' });
const adm = await api('POST', '/auth/login', { qq_account: '900000', password: 'admin12345' });
const devToken = dev.json.data.token;
const admToken = adm.json.data.token;
const admId = adm.json.data.user.user_id;
log('1.login dev/admin ok, admin_id =', admId);

const direct = await api('POST', '/im/conversations/direct', { user_id: admId }, devToken);
const sid = direct.json.data.im_session_id;
log('2.direct session =', sid);

const received = [];
const ws = new WebSocket(`${WS}?token=${devToken}`);
await new Promise((resolve, reject) => {
  const timer = setTimeout(() => reject(new Error('open timeout')), 8000);
  ws.addEventListener('open', () => { clearTimeout(timer); resolve(); });
  ws.addEventListener('error', (e) => { clearTimeout(timer); reject(new Error('ws error: ' + (e.message || 'unknown'))); });
});
log('3.ws connected');
ws.addEventListener('message', (e) => { received.push(e.data); log('   [recv]', e.data); });

await new Promise((r) => setTimeout(r, 600));   // 等 hello
ws.send(JSON.stringify({ type: 'ping' }));      // 心跳
await new Promise((r) => setTimeout(r, 600));

const sent = await api('POST', `/im/conversations/${sid}/messages`, { content: 'ws push selftest (node)' }, admToken);
log('4.http send by admin -> HTTP', sent.status);
await new Promise((r) => setTimeout(r, 1200));

log('5.received frames =', received.length, JSON.stringify(received));
ws.close(1000, 'bye');
await new Promise((r) => setTimeout(r, 300));

// 无 token 连接应被拒绝
const bad = new WebSocket(WS);
const badEvents = [];
const badResult = await new Promise((resolve) => {
  const t = setTimeout(() => resolve('timeout'), 5000);
  bad.addEventListener('open', () => { badEvents.push('open'); });
  bad.addEventListener('close', (e) => { clearTimeout(t); badEvents.push(`close code=${e.code} reason=${e.reason}`); resolve(badEvents.join(' + ')); });
  bad.addEventListener('error', () => {});
});
log('6.no-token connect =', badResult);

const ok = received.some((m) => m.includes('hello')) && received.some((m) => m.includes('pong'))
  && received.some((m) => m.includes('ws push selftest (node)'));
log(ok ? 'WS_TEST_PASS' : 'WS_TEST_FAIL');
process.exit(ok ? 0 : 1);
