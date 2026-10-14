'use strict';
const { spawn } = require('node:child_process');
const fs = require('node:fs/promises');
const path = require('node:path');
const os = require('node:os');
const crypto = require('node:crypto');
const {requestStream,proxyCandidates,abortError}=require('./http-stream.cjs');

const DEFAULT_MODEL = 'eleven_flash_v2_5';

function validKey(key) { return typeof key === 'string' && /^[A-Za-z0-9_.-]{20,300}$/.test(key); }
function validVoiceId(value) { return typeof value === 'string' && /^[A-Za-z0-9_-]{5,120}$/.test(value); }

function apiError(status, data) {
  const message = String(data?.detail?.message || data?.detail || data?.message || '').toLowerCase();
  if (status === 401) return 'ElevenLabs did not accept this API key. Create or copy a valid key from your ElevenLabs account.';
  if (status === 403) return 'ElevenLabs blocked this request. In your ElevenLabs API key settings, allow Speech to Text and Text to Speech, and check any credit or IP restriction.';
  if (status === 429) return 'ElevenLabs has reached a usage or rate limit. Check your remaining credits and try again.';
  if (status === 404) return 'The selected ElevenLabs voice is unavailable. Choose another voice.';
  if (status === 422) return 'ElevenLabs rejected the voice request. Choose another voice or try shorter text.';
  if (status >= 500) return 'ElevenLabs is temporarily unavailable. Try again shortly.';
  if (/quota|credit/.test(message)) return 'ElevenLabs does not have enough credits for this request.';
  return 'The ElevenLabs request did not complete.';
}


function runCurl({ url, key, body, form, env, signal, binary, proxy, timeoutMs = 30000 }) {
  const config = [
    'url = ' + JSON.stringify(url),
    'header = ' + JSON.stringify('xi-api-key: ' + key)
  ];
  if (!Array.isArray(form) || !form.length) config.push('header = "Content-Type: application/json"');
  if (proxy) config.push('proxy = ' + JSON.stringify(proxy));
  else config.push('noproxy = "*"');
  if (body !== undefined) config.push('request = "POST"', 'data = ' + JSON.stringify(JSON.stringify(body)));
  if (Array.isArray(form) && form.length) {
    config.push('request = "POST"');
    for (const item of form) config.push('form = ' + JSON.stringify(item));
  }

  return new Promise((resolve, reject) => {
    const executable = process.platform === 'win32' ? 'curl.exe' : 'curl';
    const childEnv = { ...env };
    for (const name of ['HTTPS_PROXY','https_proxy','HTTP_PROXY','http_proxy','ALL_PROXY','all_proxy']) delete childEnv[name];
    const child = spawn(executable, ['--config', '-', '--silent', '--show-error', '--connect-timeout','5', '--max-time',String(Math.ceil(timeoutMs/1000)), '--write-out', '\n__BACY_HTTP__%{http_code}'], {
      env: childEnv, windowsHide: true, stdio: ['pipe', 'pipe', 'pipe']
    });
    const chunks = [];
    let size = 0, stderr = '', settled = false, cancelled = false;
    const timer = setTimeout(() => { cancelled = true; child.kill(); }, timeoutMs+1000);
    const abort = () => { cancelled = true; child.kill(); };
    function finish(error, value) {
      if (settled) return;
      settled = true;
      clearTimeout(timer);
      signal?.removeEventListener('abort', abort);
      if (error) reject(error); else resolve(value);
    }
    child.stdout.on('data', chunk => {
      size += chunk.length;
      if (size > 12 * 1024 * 1024) { cancelled = true; child.kill(); return; }
      chunks.push(chunk);
    });
    child.stderr.setEncoding('utf8');
    child.stderr.on('data', chunk => { stderr = (stderr + chunk).slice(-8000); });
    child.stdin.on('error', () => {});
    child.on('error', () => {
      const error = new Error('The secure HTTP client could not start. Windows curl.exe is required.');
      error.network = true; finish(error);
    });
    child.on('close', code => {
      if (cancelled) {
        const error = new Error(signal?.aborted ? 'Voice request cancelled.' : 'The ElevenLabs request timed out.');
        error.network = true; return finish(error);
      }
      if (code !== 0) {
        const error = new Error('ElevenLabs network connection failed.');
        error.network = true; error.detail = stderr; return finish(error);
      }
      const output = Buffer.concat(chunks);
      const marker = Buffer.from('\n__BACY_HTTP__');
      const at = output.lastIndexOf(marker);
      if (at < 0) {
        const error = new Error('The voice service returned an incomplete response.');
        error.network = true; return finish(error);
      }
      const status = Number(output.subarray(at + marker.length).toString('utf8').trim());
      const payload = output.subarray(0, at);
      if (!Number.isInteger(status) || status < 200 || status >= 300) {
        let data = null;
        try { data = JSON.parse(payload.toString('utf8')); } catch {}
        const error = new Error(apiError(status, data));
        error.status = status; return finish(error);
      }
      if (binary) return finish(null, payload);
      try { return finish(null, JSON.parse(payload.toString('utf8'))); }
      catch {
        const error = new Error('ElevenLabs returned an unreadable response.');
        error.status = status; return finish(error);
      }
    });
    signal?.addEventListener('abort', abort, { once: true });
    if (signal?.aborted) abort(); else child.stdin.end(config.join('\n') + '\n');
  });
}

async function curlRequest({ url, key, body, form, env = process.env, signal, binary = false, timeoutMs=30000 }) {
  if(signal?.aborted)throw abortError();
  if (!validKey(key)) throw new Error('Enter an ElevenLabs API key privately in Voice settings.');
  const destination = new URL(url);
  if (destination.protocol !== 'https:' || destination.hostname !== 'api.elevenlabs.io') throw new Error('Unsupported voice API destination.');
  const attempts = proxyCandidates(env);
  let lastNetworkError;
  for (const proxy of attempts) {
    try { return await runCurl({ url, key, body, form, env, signal, binary, proxy, timeoutMs }); }
    catch (error) {
      if (signal?.aborted) throw abortError();
      if (!error.network) throw error;
      lastNetworkError = error;
    }
  }
  const error = new Error('ElevenLabs could not be reached. Bacy tried a direct secure connection' + (attempts.some(Boolean) ? ' and your Windows proxy' : '') + '. Check that the laptop is online, then try again.');
  error.network = true;
  error.cause = lastNetworkError;
  throw error;
}

function cryptWindows(text, decrypt = false) {
  return new Promise((resolve, reject) => {
    const script = decrypt
      ? "Add-Type -AssemblyName System.Security; $s=[Console]::In.ReadToEnd();$b=[Convert]::FromBase64String($s);$o=[Security.Cryptography.ProtectedData]::Unprotect($b,$null,[Security.Cryptography.DataProtectionScope]::CurrentUser);[Console]::Write([Text.Encoding]::UTF8.GetString($o))"
      : "Add-Type -AssemblyName System.Security; $s=[Console]::In.ReadToEnd();$b=[Text.Encoding]::UTF8.GetBytes($s);$o=[Security.Cryptography.ProtectedData]::Protect($b,$null,[Security.Cryptography.DataProtectionScope]::CurrentUser);[Console]::Write([Convert]::ToBase64String($o))";
    const child = spawn('powershell.exe', ['-NoProfile', '-EncodedCommand', Buffer.from(script, 'utf16le').toString('base64')], { windowsHide: true, stdio: ['pipe', 'pipe', 'pipe'] });
    let output = '';
    const timer = setTimeout(() => { child.kill(); reject(new Error('Windows encrypted storage timed out.')); }, 20000);
    child.stdout.setEncoding('utf8');
    child.stdout.on('data', value => { output += value; });
    child.stderr.resume(); child.stdin.on('error', () => {});
    child.on('error', () => { clearTimeout(timer); reject(new Error('Windows encrypted storage is unavailable.')); });
    child.on('close', code => { clearTimeout(timer); code === 0 ? resolve(output.trim()) : reject(new Error('Windows could not access the encrypted ElevenLabs settings.')); });
    child.stdin.end(text);
  });
}

function defaultStore() {
  const file = process.platform === 'win32' && process.env.LOCALAPPDATA ? path.join(process.env.LOCALAPPDATA, 'Bacy', 'elevenlabs-config.dpapi') : null;
  return {
    async load() {
      if (!file) return null;
      try { return JSON.parse(await cryptWindows(await fs.readFile(file, 'utf8'), true)); }
      catch { return null; }
    },
    async save(value) {
      if (!file) throw new Error('Remembering the ElevenLabs key securely is available on Windows. Leave Remember unchecked for this session only.');
      const encrypted = await cryptWindows(JSON.stringify(value));
      await fs.mkdir(path.dirname(file), { recursive: true });
      await fs.writeFile(file, encrypted, { mode: 0o600 });
    },
    async clear() { if (file) await fs.rm(file, { force: true }); }
  };
}

function cleanVoices(data) {
  const source = Array.isArray(data?.voices) ? data.voices : [];
  return source.slice(0, 100).filter(v => validVoiceId(v.voice_id)).map(v => ({
    id: v.voice_id,
    name: String(v.name || 'Voice').slice(0, 100),
    gender: String(v.labels?.gender || '').slice(0, 30),
    age: String(v.labels?.age || '').slice(0, 30),
    accent: String(v.labels?.accent || '').slice(0, 40),
    language: String(v.language || v.labels?.language || '').slice(0, 20)
  }));
}

function createElevenLabs({ env = process.env, transport = curlRequest, store = defaultStore() } = {}) {
  let key = validKey(env.ELEVENLABS_API_KEY) ? env.ELEVENLABS_API_KEY : '';
  let voiceId = validVoiceId(env.ELEVENLABS_VOICE_ID) ? env.ELEVENLABS_VOICE_ID : '';
  let verified = false;
  let cachedVoices = [];
  const ready = key ? Promise.resolve() : store.load().then(saved => {
    if (saved && validKey(saved.key)) {
      key = saved.key;
      if (validVoiceId(saved.voiceId)) voiceId = saved.voiceId;
    }
  });

  return {
    async status() {
      await ready;
      return { configured: Boolean(key), voiceId, verified, model: DEFAULT_MODEL };
    },

    async configure(settings) {
      await ready;
      if (settings.forget) {
        await store.clear();
        key = ''; voiceId = ''; verified = false; cachedVoices = [];
        return this.status();
      }
      const nextKey = typeof settings.key === 'string' && settings.key.trim() ? settings.key.trim() : key;
      const nextVoice = typeof settings.voiceId === 'string' && settings.voiceId.trim() ? settings.voiceId.trim() : voiceId;
      if (!validKey(nextKey)) throw new Error('Enter a valid ElevenLabs API key in this local form.');
      if (nextVoice && !validVoiceId(nextVoice)) throw new Error('Choose a valid ElevenLabs voice.');
      if (settings.remember) await store.save({ key: nextKey, voiceId: nextVoice });
      else await store.clear();
      key = nextKey; voiceId = nextVoice; verified = false;
      return this.status();
    },

    async test() {
      await ready;
      const data = await transport({ url: 'https://api.elevenlabs.io/v2/voices?page_size=50&include_total_count=true', key, env });
      cachedVoices = cleanVoices(data);
      if (!cachedVoices.length) throw new Error('ElevenLabs accepted the key but no usable voices were returned for this account.');
      if (!validVoiceId(voiceId) || !cachedVoices.some(v => v.id === voiceId)) voiceId = cachedVoices[0].id;
      verified = true;
      return { verified: true, voiceId, voices: cachedVoices, model: DEFAULT_MODEL, message: 'ElevenLabs is connected. Choose the voice you want Bacy to use.' };
    },

    async voices() {
      await ready;
      if (!key) throw new Error('Connect ElevenLabs first.');
      if (!cachedVoices.length) return (await this.test()).voices;
      return cachedVoices;
    },

    async synthesize(text, { signal } = {}) {
      await ready;
      if (!key) throw new Error('Connect ElevenLabs in Voice settings first.');
      if (!validVoiceId(voiceId)) await this.test();
      const cleaned = String(text || '').trim().slice(0, 3000);
      if (!cleaned) throw new Error('There is no text to speak.');
      const audio = await transport({
        url: `https://api.elevenlabs.io/v1/text-to-speech/${voiceId}?output_format=mp3_22050_32`,
        key, env, signal, binary: true,
        body: {
          text: cleaned,
          model_id: DEFAULT_MODEL,
          voice_settings: { stability: 0.35, similarity_boost: 0.72, style: 0.08, use_speaker_boost: true, speed: 1.02 }
        }
      });
      verified = true;
      return audio;
    },

    async synthesizeStream(text, { signal } = {}) {
      await ready;
      if (!key) throw new Error('Connect ElevenLabs in Voice settings first.');
      if (!validVoiceId(voiceId)) await this.test();
      const cleaned = String(text || '').trim().slice(0, 3000);
      if (!cleaned) throw new Error('There is no text to speak.');
      const response=await requestStream({url:`https://api.elevenlabs.io/v1/text-to-speech/${voiceId}/stream?output_format=mp3_22050_32`,method:'POST',env,signal,timeoutMs:30000,
        headers:{'xi-api-key':key,'Content-Type':'application/json','Accept':'audio/mpeg'},
        body:{text:cleaned,model_id:DEFAULT_MODEL,voice_settings:{stability:.35,similarity_boost:.72,style:.08,use_speaker_boost:true,speed:1.02}}
      });verified=true;return response.body;
    },

    async transcribe(audio, mimeType = 'audio/webm', { signal } = {}) {
      await ready;
      if (!key) throw new Error('Connect ElevenLabs in Voice settings first.');
      if (!Buffer.isBuffer(audio) || audio.length < 100) throw new Error('The voice note was empty. Try recording again.');
      if (audio.length > 10 * 1024 * 1024) throw new Error('The voice note is too large. Keep it under about one minute.');
      const extension = /ogg/i.test(mimeType) ? '.ogg' : /mp4|m4a/i.test(mimeType) ? '.m4a' : '.webm';
      const file = path.join(os.tmpdir(), 'bacy-voice-' + crypto.randomBytes(8).toString('hex') + extension);
      try {
        await fs.writeFile(file, audio);
        const data = await transport({
          url: 'https://api.elevenlabs.io/v1/speech-to-text', key, env, signal,
          form: ['file=@' + file + ';type=' + mimeType, 'model_id=scribe_v2']
        });
        const text = String(data?.text || '').trim();
        if (!text) throw new Error('ElevenLabs could not hear any speech in that voice note.');
        verified = true;
        return text.slice(0, 8000);
      } finally {
        await fs.rm(file, { force: true }).catch(() => {});
      }
    }
  };
}

module.exports = { createElevenLabs, curlRequest, validKey, validVoiceId, cleanVoices };
