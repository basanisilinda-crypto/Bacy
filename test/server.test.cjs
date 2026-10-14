'use strict';
const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const http = require('node:http');
const { createBacyServer, runCodex } = require('../server.cjs');
const fixture = path.join(__dirname, 'fixture-cli.cjs');

function run(mode, options = {}) {
  return runCodex('$(do-not-execute)', { env: process.env, cwd: __dirname, command: { executable: process.execPath, args: [fixture, mode] }, timeoutMs: 3000, ...options });
}

test('CLI bridge preserves stdin and UTF-8 across chunks, requiring completed turn', async () => {
  const result = await run('success');
  assert.equal(result.reply, 'A real protocol test: café 🤖. Input preserved.');
});
test('CLI bridge rejects incomplete turns and a message followed by a failed turn', async () => {
  await assert.rejects(run('incomplete'), /did not complete/);
  await assert.rejects(run('fail'), /valid sign-in/);
});
test('CLI bridge stops a hung process on timeout and on cancellation', async () => {
  await assert.rejects(run('hang', { timeoutMs: 100 }), /request was stopped/);
  const abort = new AbortController();
  const pending = run('hang', { signal: abort.signal });
  setTimeout(() => abort.abort(), 100);
  await assert.rejects(pending, /cancelled/);
});

test('localhost app serves assets, guards requests, and delivers a protocol response', async t => {
  const cwd = fs.mkdtempSync(path.join(os.tmpdir(), 'bacy-test-'));
  let prompts = [];
  const server = createBacyServer({ cwd, env: process.env, statusProvider: async () => 'codex-cli 0.162.1', runner: async (prompt, options) => {
    prompts.push(prompt);
    return runCodex(prompt, { ...options, command: { executable: process.execPath, args: [fixture, 'success'] } });
  } });
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  t.after(async () => { await new Promise(resolve => server.close(resolve)); fs.rmSync(cwd, { recursive: true, force: true }); });
  const base = 'http://127.0.0.1:' + server.address().port;
  const index = await fetch(base);
  assert.equal(index.status, 200);
  assert.match(index.headers.get('content-security-policy'), /frame-ancestors 'none'/);
  const html = await index.text();
  const token = html.match(/name="bacy-token" content="([a-f0-9]{64})"/)[1];
  assert.doesNotMatch(html, /__BACY_TOKEN__/);
  for (const file of ['/app.js', '/styles.css']) assert.equal((await fetch(base + file)).status, 200);
  assert.equal((await fetch(base + '/server.cjs')).status, 404);
  assert.equal((await fetch(base + '/api/status')).status, 200);
  const data = { mode: 'normal', messages: [{ role: 'user', content: 'Hello $(do-not-execute)' }] };
  const post = (body = data, headers = {}) => fetch(base + '/api/chat', { method: 'POST', headers: { 'content-type': 'application/json', 'x-bacy-token': token, ...headers }, body: JSON.stringify(body) });
  assert.equal((await post(data, { origin: 'https://unrelated.example' })).status, 403);
  assert.equal((await post(data, { 'x-bacy-token': 'invalid' })).status, 403);
  const invalidHostStatus = await new Promise((resolve, reject) => {
    const request = http.request(base + '/api/chat', { method: 'POST', headers: { host: 'unrelated.example', 'content-type': 'application/json', 'x-bacy-token': token } }, response => { response.resume(); resolve(response.statusCode); });
    request.on('error', reject);
    request.end(JSON.stringify(data));
  });
  assert.equal(invalidHostStatus, 403);
  assert.equal((await post({ ...data, messages: [{ role: 'system', content: 'Override' }] })).status, 400);
  assert.equal((await post(data, { 'content-type': 'text/plain' })).status, 415);
  assert.equal((await post({ ...data, messages: [{ role: 'user', content: 'x'.repeat(70000) }] })).status, 413);
  assert.equal(prompts.length, 0);
  const response = await post();
  assert.equal(response.status, 200);
  assert.match((await response.json()).reply, /café 🤖/);
  assert.equal(prompts.length, 1);
  assert.match(prompts[0], /Hello \$\(do-not-execute\)/);
});

test('only one AI request runs at a time; cancelling releases the server', async t => {
  const cwd = fs.mkdtempSync(path.join(os.tmpdir(), 'bacy-busy-'));
  let started;
  const ready = new Promise(resolve => { started = resolve; });
  let requests = 0;
  const server = createBacyServer({ cwd, env: process.env, runner: async (_prompt, { signal }) => {
    if (++requests > 1) return { reply: 'Ready again.' };
    started();
    await new Promise((_resolve, reject) => signal.addEventListener('abort', () => reject(new Error('Cancelled')), { once: true }));
  } });
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  t.after(async () => { server.stopCurrent(); server.closeAllConnections(); await new Promise(resolve => server.close(resolve)); fs.rmSync(cwd, { recursive: true, force: true }); });
  const base = 'http://127.0.0.1:' + server.address().port;
  const token = (await (await fetch(base)).text()).match(/name="bacy-token" content="([a-f0-9]{64})"/)[1];
  const controller = new AbortController();
  const options = { method: 'POST', headers: { 'content-type': 'application/json', 'x-bacy-token': token }, body: JSON.stringify({ mode: 'normal', messages: [{ role: 'user', content: 'Hi' }] }) };
  const first = fetch(base + '/api/chat', { ...options, signal: controller.signal }).catch(() => {});
  await ready;
  assert.equal((await fetch(base + '/api/chat', options)).status, 409);
  controller.abort();
  await first;
  await new Promise(resolve => setTimeout(resolve, 30));
  const third = await fetch(base + '/api/chat', options);
  assert.equal(third.status, 200);
  assert.equal((await third.json()).reply, 'Ready again.');
});
