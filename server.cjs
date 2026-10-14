'use strict';
const http = require('node:http');
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const { spawn, spawnSync } = require('node:child_process');
const ROOT = __dirname;
const MAX_BODY = 65536;
const CLI_ARGS = ['exec', '--json', '--color', 'never', '--ephemeral', '--ignore-user-config', '--ignore-rules', '--skip-git-repo-check', '--sandbox', 'read-only', '-c', 'features.shell_tool=false', '-c', 'features.apps=false', '-c', 'features.plugins=false', '-c', 'features.hooks=false', '-c', 'features.multi_agent=false', '-c', 'features.view_image=false', '-c', 'project_doc_max_bytes=0', '-c', 'web_search=disabled', '-'];

function configuredEnvironment() {
  const env = { ...process.env };
  if (process.platform === 'win32' && !env.HTTPS_PROXY && !env.https_proxy) {
    const script = "$p=Get-ItemProperty 'HKCU:\\Software\\Microsoft\\Windows\\CurrentVersion\\Internet Settings'; if($p.ProxyEnable -eq 1){$p.ProxyServer}";
    const result = spawnSync('powershell.exe', ['-NoProfile', '-NonInteractive', '-Command', script], { encoding: 'utf8', timeout: 5000, windowsHide: true });
    if (!result.error && result.status === 0) {
      const entries = result.stdout.trim().split(';');
      const specific = entries.find(value => value.startsWith('https='));
      const fallback = entries.find(value => value.startsWith('http='));
      const proxy = specific ? specific.slice(6) : fallback ? fallback.slice(5) : entries.length === 1 ? entries[0] : '';
      if (/^[A-Za-z0-9.-]+:\d{1,5}$/.test(proxy)) env.HTTPS_PROXY = `http://${proxy}`;
    }
  }
  env.NO_PROXY = [env.NO_PROXY || env.no_proxy || '', 'localhost', '127.0.0.1'].filter(Boolean).join(',');
  return env;
}

function commandFor(args) {
  // Windows .cmd files need cmd.exe. This command is static; chat text is stdin.
  return process.platform === 'win32'
    ? { executable: 'cmd.exe', args: ['/d', '/s', '/c', 'codex.cmd ' + args.join(' ')] }
    : { executable: 'codex', args };
}

function stopProcess(child) {
  if (!child.pid || child.exitCode !== null) return;
  if (process.platform === 'win32') {
    const killer = spawn('taskkill.exe', ['/pid', String(child.pid), '/t', '/f'], { windowsHide: true, stdio: 'ignore' });
    killer.on('error', () => child.kill());
  } else {
    try { process.kill(-child.pid, 'SIGTERM'); } catch { child.kill(); }
  }
}

function safeError(detail) {
  const text = String(detail || '').toLowerCase();
  if (/enoent|not recognized|not found|missing optional dependency/.test(text)) return 'Codex could not start. Check codex.cmd --version in PowerShell.';
  if (/unauthorized|401|not logged|authentication|login|token.*expired/.test(text)) return 'Codex needs a valid sign-in. Run codex.cmd login in PowerShell, then try again.';
  if (/429|rate.limit|usage.limit|quota|exceeded/.test(text)) return 'Your Codex plan has reached a usage limit. Check the reset time in your account before retrying.';
  if (/proxy|timeout|timed.out|dns|connect|network|ssl|tls|certificate|websocket/.test(text)) return 'The online AI connection did not complete. Check that your required proxy is still connected. Keep certificate verification enabled.';
  return 'Codex did not complete this request. Check that your direct Codex test still works in PowerShell.';
}

function buildPrompt(messages) {
  return [
    'You are Bacy, a respectful, friendly personal AI assistant. Answer naturally and clearly.',
    'This release supports text conversation only. Email, camera, desktop-control and voice are not connected.',
    'Never claim to have opened apps, sent messages, accessed accounts or completed actions outside this conversation.',
    'Do not use tools, inspect files or run commands. Provide conversational help and explain unavailable capabilities honestly.',
    'Reply to the last user message in this conversation. Conversation data follows:',
    JSON.stringify(messages)
  ].join('\n\n');
}

function runCodex(prompt, { signal, env, cwd, timeoutMs = 180000, command } = {}) {
  return new Promise((resolve, reject) => {
    const selected = command || commandFor(CLI_ARGS);
    const child = spawn(selected.executable, selected.args, { cwd, env, windowsHide: true, detached: process.platform !== 'win32', stdio: ['pipe', 'pipe', 'pipe'] });
    let settled = false, pendingError = null, buffer = '', answer = '', completed = false, failed = false, eventError = '', stderr = '', bytes = 0;
    const timer = setTimeout(() => stop(new Error('The AI took longer than three minutes. The request was stopped. Try a shorter message.')), timeoutMs);
    const abort = () => stop(new Error('Request cancelled.'));
    function stop(error) {
      if (settled || pendingError) return;
      pendingError = error;
      stopProcess(child);
    }
    function finish(error, value) {
      if (settled) return;
      settled = true;
      clearTimeout(timer);
      signal?.removeEventListener('abort', abort);
      if (error) reject(error); else resolve(value);
    }
    function parse(line) {
      let event;
      try { event = JSON.parse(line); } catch { return; }
      if (event.type === 'item.completed' && event.item?.type === 'agent_message' && typeof event.item.text === 'string') answer = event.item.text;
      if (event.type === 'turn.completed') completed = true;
      if (event.type === 'turn.failed') { failed = true; eventError = event.error?.message || ''; }
      if (event.type === 'error') eventError = event.message || '';
    }
    child.stdout.setEncoding('utf8');
    child.stdout.on('data', chunk => {
      bytes += Buffer.byteLength(chunk);
      if (bytes > 2097152) return stop(new Error('The reply exceeded the size limit. Try a shorter request.'));
      buffer += chunk;
      let newline;
      while ((newline = buffer.indexOf('\n')) !== -1) { parse(buffer.slice(0, newline)); buffer = buffer.slice(newline + 1); }
    });
    child.stderr.setEncoding('utf8');
    child.stderr.on('data', chunk => { stderr = (stderr + chunk).slice(-16000); });
    child.on('error', error => finish(new Error(safeError(error.message))));
    child.stdin.on('error', () => {});
    child.on('close', code => {
      parse(buffer);
      if (pendingError) return finish(pendingError);
      if (code !== 0 || failed || !completed || !answer.trim()) return finish(new Error(safeError(eventError || stderr)));
      finish(null, { reply: answer });
    });
    signal?.addEventListener('abort', abort, { once: true });
    if (signal?.aborted) abort(); else child.stdin.end(prompt);
  });
}

function validMessages(body) {
  if (!body || !['normal', 'professional', 'hardcore'].includes(body.mode) || !Array.isArray(body.messages) || body.messages.length < 1 || body.messages.length > 20) return false;
  let total = 0;
  for (let i = 0; i < body.messages.length; i++) {
    const item = body.messages[i];
    if (!item || !['user', 'assistant'].includes(item.role) || typeof item.content !== 'string' || !item.content.trim() || item.content.length > 8000) return false;
    if (i && body.messages[i - 1].role === item.role) return false;
    total += item.content.length;
  }
  return total <= 24000 && body.messages[0].role === 'user' && body.messages.at(-1).role === 'user';
}

function createBacyServer({ runner = runCodex, env = configuredEnvironment(), cwd = path.join(ROOT, '.bacy-session'), statusProvider } = {}) {
  fs.mkdirSync(cwd, { recursive: true });
  const token = crypto.randomBytes(32).toString('hex');
  let busy = false, activeAbort;
  const headers = {
    'Cache-Control': 'no-store', 'X-Content-Type-Options': 'nosniff', 'Referrer-Policy': 'no-referrer',
    'Content-Security-Policy': "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; connect-src 'self'; object-src 'none'; base-uri 'none'; frame-ancestors 'none'; form-action 'self'",
    'Permissions-Policy': 'camera=(), microphone=(), geolocation=()'
  };
  function checkCodex() {
    return new Promise(resolve => {
      const command = commandFor(['--version']);
      const child = spawn(command.executable, command.args, { env, windowsHide: true, detached: process.platform !== 'win32', stdio: ['ignore', 'pipe', 'ignore'] });
      let output = '';
      const timer = setTimeout(() => { stopProcess(child); resolve(null); }, 10000);
      child.stdout.on('data', chunk => { output += chunk.toString(); });
      child.on('error', () => { clearTimeout(timer); resolve(null); });
      child.on('close', code => { clearTimeout(timer); resolve(code === 0 ? output.trim().slice(0, 100) : null); });
    });
  }
  const server = http.createServer(async (req, res) => {
    const hosts = ['localhost:' + server.address().port, '127.0.0.1:' + server.address().port];
    function send(code, value) {
      if (res.destroyed || res.writableEnded) return;
      res.writeHead(code, { ...headers, 'Content-Type': 'application/json; charset=utf-8' });
      res.end(JSON.stringify(value));
    }
    if (!hosts.includes(req.headers.host)) return send(403, { error: 'Invalid local host.' });
    if (req.headers.origin && !hosts.map(host => 'http://' + host).includes(req.headers.origin)) return send(403, { error: 'Open Bacy on this laptop to send messages.' });
    if (req.method === 'GET' && req.url === '/api/status') {
      const version = await (statusProvider ? statusProvider() : checkCodex());
      return send(200, { available: Boolean(version), version, platform: process.platform, proxyConfigured: Boolean(env.HTTPS_PROXY || env.https_proxy), detail: version ? 'Codex detected. Your first message checks sign-in and the online connection.' : 'Codex is unavailable. Check codex.cmd --version in PowerShell, then check again.' });
    }
    if (req.method === 'GET') {
      const files = { '/': ['index.html', 'text/html'], '/app.js': ['app.js', 'text/javascript'], '/styles.css': ['styles.css', 'text/css'] };
      const file = files[req.url];
      if (!file) return send(404, { error: 'Not found.' });
      try {
        let content = fs.readFileSync(path.join(ROOT, 'public', file[0]), 'utf8');
        if (req.url === '/') content = content.replace('__BACY_TOKEN__', token);
        res.writeHead(200, { ...headers, 'Content-Type': file[1] + '; charset=utf-8' });
        return res.end(content);
      } catch { return send(500, { error: 'Interface files are missing. Extract all Bacy files together.' }); }
    }
    if (req.method !== 'POST' || req.url !== '/api/chat') return send(404, { error: 'Not found.' });
    if (req.headers['x-bacy-token'] !== token) return send(403, { error: 'Refresh the Bacy page, then try again.' });
    if (!/^application\/json(?:;|$)/i.test(req.headers['content-type'] || '')) return send(415, { error: 'A JSON message is required.' });
    const chunks = [];
    let length = 0;
    try {
      for await (const chunk of req) {
        length += chunk.length;
        if (length > MAX_BODY) { send(413, { error: 'The conversation is too large. Start a new chat.' }); return; }
        chunks.push(chunk);
      }
    } catch { return; }
    let body;
    try { body = JSON.parse(Buffer.concat(chunks).toString('utf8')); } catch { return send(400, { error: 'Invalid message format.' }); }
    if (!validMessages(body)) return send(400, { error: 'Use alternating messages, up to 8,000 characters each. Start a new chat if the history is too long.' });
    if (busy) return send(409, { error: 'Bacy is already answering a message. Wait for it to finish.' });
    busy = true;
    const abort = new AbortController();
    activeAbort = abort;
    const disconnect = () => { if (!res.writableEnded) abort.abort(); };
    res.on('close', disconnect);
    try { send(200, await runner(buildPrompt(body.messages), { signal: abort.signal, env, cwd })); }
    catch (error) { send(502, { error: error.message }); }
    finally { res.off('close', disconnect); activeAbort = undefined; busy = false; }
  });
  server.headersTimeout = 10000;
  server.requestTimeout = 15000;
  server.stopCurrent = () => activeAbort?.abort();
  return server;
}

if (require.main === module) {
  const server = createBacyServer();
  server.on('error', error => {
    console.error(error.code === 'EADDRINUSE' ? 'Port 3000 is already in use. Close the previous Bacy window and try again.' : 'Bacy could not start: ' + error.message);
    process.exitCode = 1;
  });
  server.listen(3000, '127.0.0.1', () => {
    console.log('Bacy is running. Open your browser and type localhost:3000');
    console.log('Keep this window open. Press Ctrl+C to stop Bacy.');
    console.log('Real AI replies use your signed-in Codex account and its usage limits.');
  });
  const stop = () => { server.stopCurrent(); server.close(); };
  process.on('SIGINT', stop);
  process.on('SIGTERM', stop);
}
module.exports = { createBacyServer, runCodex, buildPrompt, validMessages };
