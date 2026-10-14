'use strict';
const { spawn } = require('node:child_process');
const fs = require('node:fs/promises');
const path = require('node:path');
const schema = require('./response-schema.json');
const DEFAULT_MODEL = 'gemini-flash-latest';

function validKey(key) { return typeof key === 'string' && /^[A-Za-z0-9_.-]{20,300}$/.test(key); }
function validModel(model) { return typeof model === 'string' && /^[A-Za-z0-9_.-]{1,150}$/.test(model); }
function apiError(status, data) {
  const description = String(data?.error?.message || '').toLowerCase();
  if (status === 429) return 'Gemini quota is exhausted or requests are too frequent. Check your Google project’s free quota and reset time.';
  if (status === 401 || status === 403 || (status === 400 && /api.key|api_key/.test(description))) return 'Google did not accept this key or project access. Check the Gemini API key, restrictions, and availability in your region.';
  if (status === 404) return 'This Gemini model is unavailable. Choose an available text model in AI settings.';
  if (status === 400) return 'Google rejected the request. Check the model selection and whether it supports structured text replies.';
  if (status >= 500) return 'Gemini is temporarily unavailable. Please try again later.';
  return 'The Gemini request did not complete. Check the connection and project settings.';
}

function curlRequest({ url, key, body, env = process.env, signal, command }) {
  if (!validKey(key)) return Promise.reject(new Error('Enter a Gemini API key privately in AI settings.'));
  const destination = new URL(url);
  if (destination.protocol !== 'https:' || destination.hostname !== 'generativelanguage.googleapis.com') return Promise.reject(new Error('Unsupported API destination.'));
  const proxy = env.HTTPS_PROXY || env.https_proxy || env.HTTP_PROXY || env.http_proxy;
  const config = ['url = ' + JSON.stringify(url), 'header = ' + JSON.stringify('x-goog-api-key: ' + key), 'header = "Content-Type: application/json"'];
  if (proxy) config.push('proxy = ' + JSON.stringify(proxy));
  if (body) config.push('request = "POST"', 'data = ' + JSON.stringify(JSON.stringify(body)));
  return new Promise((resolve, reject) => {
    // Credentials and chat content are stdin, never command-line arguments.
    const selected = command || { executable: process.platform === 'win32' ? 'curl.exe' : 'curl', args: ['--config', '-', '--silent', '--show-error', '--max-time', '120', '--write-out', '\n__BACY_HTTP__%{http_code}'] };
    const child = spawn(selected.executable, selected.args, { env, windowsHide: true, stdio: ['pipe', 'pipe', 'pipe'] });
    let output = '', settled = false, cancellation = false;
    const timer = setTimeout(() => { cancellation = true; child.kill(); }, 125000);
    const abort = () => { cancellation = true; child.kill(); };
    function finish(error, value) {
      if (settled) return; settled = true; clearTimeout(timer); signal?.removeEventListener('abort', abort);
      if (error) reject(error); else resolve(value);
    }
    child.stdout.setEncoding('utf8');
    child.stdout.on('data', chunk => { output += chunk; if (output.length > 2097152) { cancellation = true; child.kill(); } });
    child.stderr.resume();
    child.stdin.on('error', () => {});
    child.on('error', () => finish(new Error('The secure HTTP client could not start. Windows curl.exe is required.')));
    child.on('close', code => {
      if (cancellation) return finish(new Error(signal?.aborted ? 'Request cancelled.' : 'The Gemini request timed out or exceeded its size limit.'));
      if (code !== 0) return finish(new Error('Gemini could not be reached securely. Check your current Windows proxy and internet connection.'));
      const separator = output.lastIndexOf('\n__BACY_HTTP__');
      if (separator < 0) return finish(new Error('The HTTP client returned an incomplete result.'));
      const status = Number(output.slice(separator + 14).trim());
      let data;
      try { data = JSON.parse(output.slice(0, separator)); } catch { return finish(new Error('Google returned an unreadable response.'));
      }
      if (!Number.isInteger(status) || status < 200 || status >= 300 || data.error) return finish(new Error(apiError(status, data)));
      finish(null, data);
    });
    signal?.addEventListener('abort', abort, { once: true });
    if (signal?.aborted) abort(); else child.stdin.end(config.join('\n') + '\n');
  });
}

function cryptWindows(text, decrypt = false) {
  return new Promise((resolve, reject) => {
    const script = decrypt
      ? "Add-Type -AssemblyName System.Security; $s=[Console]::In.ReadToEnd();$b=[Convert]::FromBase64String($s);$o=[Security.Cryptography.ProtectedData]::Unprotect($b,$null,[Security.Cryptography.DataProtectionScope]::CurrentUser);[Console]::Write([Text.Encoding]::UTF8.GetString($o))"
      : "Add-Type -AssemblyName System.Security; $s=[Console]::In.ReadToEnd();$b=[Text.Encoding]::UTF8.GetBytes($s);$o=[Security.Cryptography.ProtectedData]::Protect($b,$null,[Security.Cryptography.DataProtectionScope]::CurrentUser);[Console]::Write([Convert]::ToBase64String($o))";
    const child = spawn('powershell.exe', ['-NoProfile', '-EncodedCommand', Buffer.from(script, 'utf16le').toString('base64')], { windowsHide: true, stdio: ['pipe', 'pipe', 'pipe'] });
    let output = '';
    const timer = setTimeout(() => { child.kill(); reject(new Error('Windows encrypted storage timed out.')); }, 20000);
    child.stdout.setEncoding('utf8'); child.stdout.on('data', value => { output += value; }); child.stderr.resume(); child.stdin.on('error', () => {});
    child.on('error', () => { clearTimeout(timer); reject(new Error('Windows encrypted storage is unavailable.')); });
    child.on('close', code => { clearTimeout(timer); code === 0 ? resolve(output.trim()) : reject(new Error('Windows could not access the encrypted Gemini settings.')); });
    child.stdin.end(text);
  });
}

function defaultStore() {
  const file = process.platform === 'win32' && process.env.LOCALAPPDATA ? path.join(process.env.LOCALAPPDATA, 'Bacy', 'gemini-config.dpapi') : null;
  return {
    async load() {
      if (!file) return null;
      try { return JSON.parse(await cryptWindows(await fs.readFile(file, 'utf8'), true)); }
      catch { return null; }
    },
    async save(value) {
      if (!file) throw new Error('Remembering the key securely is available on Windows. Leave Remember unchecked for session-only use.');
      const encrypted = await cryptWindows(JSON.stringify(value));
      await fs.mkdir(path.dirname(file), { recursive: true });
      await fs.writeFile(file, encrypted, { mode: 0o600 });
    },
    async clear() { if (file) await fs.rm(file, { force: true }); }
  };
}

function createGemini({ env = process.env, transport = curlRequest, store = defaultStore() } = {}) {
  let key = validKey(env.GEMINI_API_KEY) ? env.GEMINI_API_KEY : '';
  let model = validModel(env.GEMINI_MODEL) ? env.GEMINI_MODEL : DEFAULT_MODEL;
  let verified = false;
  const ready = key ? Promise.resolve() : store.load().then(saved => { if (saved && validKey(saved.key)) { key = saved.key; if (validModel(saved.model)) model = saved.model; } });
  return {
    async status() { await ready; return { configured: Boolean(key), model, verified }; },
    async configure(settings) {
      await ready;
      if (settings.forget) { await store.clear(); key = ''; verified = false; return this.status(); }
      const nextKey = typeof settings.key === 'string' && settings.key.trim() ? settings.key.trim() : key;
      const nextModel = settings.model || model;
      if (!validKey(nextKey)) throw new Error('Enter a valid Gemini API key in this local form.');
      if (!validModel(nextModel)) throw new Error('Enter a valid Gemini model ID.');
      if (settings.remember) await store.save({ key: nextKey, model: nextModel });
      else await store.clear();
      key = nextKey; model = nextModel; verified = false;
      return this.status();
    },
    async test() {
      await ready;
      const data = await transport({ url: 'https://generativelanguage.googleapis.com/v1beta/models?pageSize=1000', key, env });
      const models = (data.models || []).filter(item => item.supportedGenerationMethods?.includes('generateContent') && typeof item.name === 'string').map(item => ({ id: item.name.replace(/^models\//, ''), name: item.displayName || item.name })).filter(item => validModel(item.id));
      verified = true;
      return { verified: true, models, model, message: 'Google accepted the key. Your first message checks generation access and quota.' };
    },
    async generate(prompt, { signal } = {}) {
      await ready;
      if (!key) throw new Error('Open AI settings and enter your Gemini key privately on this laptop.');
      const data = await transport({ url: `https://generativelanguage.googleapis.com/v1beta/models/${model}:generateContent`, key, env, signal, body: {
        contents: [{ role: 'user', parts: [{ text: prompt }] }],
        generationConfig: { responseMimeType: 'application/json', responseJsonSchema: schema, maxOutputTokens: 8192 }
      } });
      const candidate = data.candidates?.[0];
      if (!candidate || candidate.finishReason === 'SAFETY' || data.promptFeedback?.blockReason) throw new Error('Gemini could not answer this request. Try rephrasing it.');
      if (candidate.finishReason === 'MAX_TOKENS') throw new Error('Gemini’s response reached its size limit. Try a shorter request.');
      const text = (candidate.content?.parts || []).filter(part => !part.thought && typeof part.text === 'string').map(part => part.text).join('');
      if (!text.trim()) throw new Error('Gemini returned no text. Choose an available text model.');
      verified = true;
      return text;
    }
  };
}
module.exports = { createGemini, curlRequest, validKey, validModel };
