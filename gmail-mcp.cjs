'use strict';
const { spawn } = require('node:child_process');
const fs = require('node:fs/promises');
const path = require('node:path');
const crypto = require('node:crypto');
const {requestStream}=require('./http-stream.cjs');

const GMAIL_MCP_URL = 'https://gmailmcp.googleapis.com/mcp/v1';
const PROTOCOL_VERSION = '2026-07-28';
const GMAIL_SCOPES = [
  'https://www.googleapis.com/auth/gmail.readonly',
  'https://www.googleapis.com/auth/gmail.compose'
];

function cryptWindows(text, decrypt = false) {
  return new Promise((resolve, reject) => {
    if (process.platform !== 'win32') return reject(new Error('Windows encrypted storage is unavailable on this platform.'));
    const script = decrypt
      ? "Add-Type -AssemblyName System.Security; $s=[Console]::In.ReadToEnd();$b=[Convert]::FromBase64String($s);$o=[Security.Cryptography.ProtectedData]::Unprotect($b,$null,[Security.Cryptography.DataProtectionScope]::CurrentUser);[Console]::Write([Text.Encoding]::UTF8.GetString($o))"
      : "Add-Type -AssemblyName System.Security; $s=[Console]::In.ReadToEnd();$b=[Text.Encoding]::UTF8.GetBytes($s);$o=[Security.Cryptography.ProtectedData]::Protect($b,$null,[Security.Cryptography.DataProtectionScope]::CurrentUser);[Console]::Write([Convert]::ToBase64String($o))";
    const child = spawn('powershell.exe', ['-NoProfile', '-EncodedCommand', Buffer.from(script, 'utf16le').toString('base64')], { windowsHide: true, stdio: ['pipe', 'pipe', 'pipe'] });
    let output = '';
    const timer = setTimeout(() => { child.kill(); reject(new Error('Windows encrypted storage timed out.')); }, 20000);
    child.stdout.setEncoding('utf8');
    child.stdout.on('data', chunk => { output += chunk; });
    child.stderr.resume(); child.stdin.on('error', () => {});
    child.on('error', () => { clearTimeout(timer); reject(new Error('Windows encrypted storage could not start.')); });
    child.on('close', code => { clearTimeout(timer); code === 0 ? resolve(output.trim()) : reject(new Error('Windows encrypted storage failed.')); });
    child.stdin.end(text);
  });
}

function defaultStore() {
  const file = process.platform === 'win32' && process.env.LOCALAPPDATA ? path.join(process.env.LOCALAPPDATA, 'Bacy', 'gmail-mcp.dpapi') : null;
  return {
    async load() {
      if (!file) return null;
      try { return JSON.parse(await cryptWindows(await fs.readFile(file, 'utf8'), true)); }
      catch { return null; }
    },
    async save(value) {
      if (!file) throw new Error('Secure Gmail connection storage is available on Windows only.');
      const encrypted = await cryptWindows(JSON.stringify(value));
      await fs.mkdir(path.dirname(file), { recursive: true });
      await fs.writeFile(file, encrypted, { mode: 0o600 });
    },
    async clear() { if (file) await fs.rm(file, { force: true }); }
  };
}

async function defaultTransport({ url, method='GET', headers={}, body, signal, timeoutMs=30000, env=process.env, streamTransport=requestStream }){
  const response=await streamTransport({url,method,headers,body,signal,timeoutMs,env});
  const reader=response.body.getReader(),decoder=new TextDecoder();let text='',pending='';
  const sse=String(response.headers['content-type']||'').includes('text/event-stream');
  try{
    while(true){const part=await reader.read();if(part.done)break;const chunk=decoder.decode(part.value,{stream:true});text+=chunk;if(text.length>3000000)throw new Error('The Gmail result was too large.');
      if(sse){pending+=chunk;const events=pending.split(/\r?\n\r?\n/);pending=events.pop();
        for(const event of events){const data=event.split(/\r?\n/).filter(line=>line.startsWith('data:')).map(line=>line.slice(5).trim()).join('\n');if(!data||data==='[DONE]')continue;let payload;try{payload=JSON.parse(data);}catch{continue;}if(payload.id!==undefined&&('result'in payload||'error'in payload)){await reader.cancel();return payload;}}
      }
    }
    return text?JSON.parse(text):{};
  }finally{await reader.cancel().catch(()=>{});}
}

function validateClientId(value) {
  return typeof value === 'string' && value.length >= 20 && value.length <= 300 && /^[A-Za-z0-9._-]+$/.test(value.trim());
}
function validateSecret(value) {
  return typeof value === 'string' && value.length >= 8 && value.length <= 300 && /^[A-Za-z0-9._-]+$/.test(value.trim());
}
function cleanTools(value) {
  const tools = Array.isArray(value?.result?.tools) ? value.result.tools : Array.isArray(value?.tools) ? value.tools : [];
  return tools.slice(0, 80).filter(tool => tool && typeof tool.name === 'string' && /^[A-Za-z0-9_.-]{1,120}$/.test(tool.name)).map(tool => ({
    name: tool.name,
    description: String(tool.description || '').slice(0, 1200),
    inputSchema: tool.inputSchema && typeof tool.inputSchema === 'object' ? tool.inputSchema : { type: 'object', properties: {} }
  }));
}

function createGmailMcp({ transport = defaultTransport, store = defaultStore(), now = () => Date.now(), env = process.env } = {}) {
  let clientId = '', clientSecret = '', accessToken = '', refreshToken = '', expiresAt = 0;
  let remember = true, connected = false, cachedTools = [], pendingState = '';
  const ready = store.load().then(saved => {
    if (!saved || typeof saved !== 'object') return;
    if (validateClientId(saved.clientId)) clientId = saved.clientId;
    if (validateSecret(saved.clientSecret)) clientSecret = saved.clientSecret;
    if (typeof saved.accessToken === 'string') accessToken = saved.accessToken;
    if (typeof saved.refreshToken === 'string') refreshToken = saved.refreshToken;
    if (Number.isFinite(saved.expiresAt)) expiresAt = saved.expiresAt;
    remember = saved.remember !== false;
    connected = Boolean(refreshToken || (accessToken && expiresAt > now()));
  }).catch(() => {});

  async function persist() {
    if (!remember) return store.clear().catch(() => {});
    await store.save({ clientId, clientSecret, accessToken, refreshToken, expiresAt, remember: true });
  }

  async function tokenRequest(params, signal) {
    const data = await transport({ env,
      url: 'https://oauth2.googleapis.com/token', method: 'POST', signal,
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: new URLSearchParams(params).toString()
    });
    if (!data || typeof data.access_token !== 'string') throw new Error('Google did not return an OAuth access token.');
    accessToken = data.access_token;
    if (typeof data.refresh_token === 'string' && data.refresh_token) refreshToken = data.refresh_token;
    expiresAt = now() + Math.max(60, Number(data.expires_in || 3600)) * 1000;
    connected = true;
    await persist();
    return accessToken;
  }

  async function ensureAccess(signal) {
    await ready;
    if (accessToken && expiresAt - now() > 60_000) return accessToken;
    if (!refreshToken) throw new Error('Gmail is not connected. Open Connections and connect Gmail first.');
    return tokenRequest({ client_id: clientId, client_secret: clientSecret, refresh_token: refreshToken, grant_type: 'refresh_token' }, signal);
  }

  async function mcp(method, params = {}, { signal, name = '' } = {}) {
    const token = await ensureAccess(signal);
    const headers = {
      'Content-Type': 'application/json',
      'Accept': 'application/json, text/event-stream',
      'Authorization': 'Bearer ' + token,
      'MCP-Protocol-Version': PROTOCOL_VERSION,
      'Mcp-Method': method
    };
    if (name) headers['Mcp-Name'] = name;
    const meta = { 'io.modelcontextprotocol/protocolVersion': PROTOCOL_VERSION, 'io.modelcontextprotocol/clientInfo': { name: 'Bacy', version: '4.0' } };
    const requestParams = { ...params, _meta: { ...(params?._meta || {}), ...meta } };
    const payload = { jsonrpc: '2.0', id: crypto.randomInt(1, 1_000_000_000), method, params: requestParams };
    const data = await transport({ env, url: GMAIL_MCP_URL, method: 'POST', signal, headers, body: JSON.stringify(payload), timeoutMs: 45000 });
    if (data?.error) throw new Error(String(data.error.message || 'Gmail MCP returned an error.'));
    return data;
  }

  return {
    async status() {
      await ready;
      return { configured: Boolean(clientId && clientSecret), connected, endpoint: GMAIL_MCP_URL, tools: cachedTools.map(t => t.name) };
    },
    async configure(settings = {}) {
      await ready;
      if (settings.forget) {
        clientId = ''; clientSecret = ''; accessToken = ''; refreshToken = ''; expiresAt = 0; connected = false; cachedTools = []; pendingState = '';
        await store.clear();
        return this.status();
      }
      const nextId = String(settings.clientId || clientId).trim();
      const nextSecret = String(settings.clientSecret || clientSecret).trim();
      if (!validateClientId(nextId)) throw new Error('Enter the Google OAuth Client ID for your Gmail MCP project.');
      if (!validateSecret(nextSecret)) throw new Error('Enter the Google OAuth Client Secret for your Gmail MCP project.');
      clientId = nextId; clientSecret = nextSecret; remember = settings.remember !== false;
      if (remember) await persist(); else await store.clear().catch(() => {});
      return this.status();
    },
    async beginAuth(baseUrl) {
      await ready;
      if (!clientId || !clientSecret) throw new Error('Save your Google OAuth Client ID and Client Secret first.');
      pendingState = crypto.randomBytes(24).toString('hex');
      const redirectUri = String(baseUrl).replace(/\/$/, '') + '/api/gmail/oauth/callback';
      const url = new URL('https://accounts.google.com/o/oauth2/v2/auth');
      url.searchParams.set('client_id', clientId);
      url.searchParams.set('redirect_uri', redirectUri);
      url.searchParams.set('response_type', 'code');
      url.searchParams.set('scope', GMAIL_SCOPES.join(' '));
      url.searchParams.set('access_type', 'offline');
      url.searchParams.set('prompt', 'consent');
      url.searchParams.set('state', pendingState);
      return { url: url.toString(), redirectUri };
    },
    async completeAuth({ code, state, baseUrl, signal }) {
      await ready;
      if (!pendingState || !state || state !== pendingState) throw new Error('The Gmail connection request expired. Start the Gmail connection again.');
      pendingState = '';
      const redirectUri = String(baseUrl).replace(/\/$/, '') + '/api/gmail/oauth/callback';
      await tokenRequest({ code, client_id: clientId, client_secret: clientSecret, redirect_uri: redirectUri, grant_type: 'authorization_code' }, signal);
      cachedTools = cleanTools(await mcp('tools/list', {}, { signal }));
      connected = true;
      return { connected: true, tools: cachedTools.map(t => t.name) };
    },
    async disconnect() {
      await ready;
      accessToken = ''; refreshToken = ''; expiresAt = 0; connected = false; cachedTools = [];
      if (remember) await persist();
      return this.status();
    },
    async listTools({ signal, refresh = false } = {}) {
      await ready;
      if (!refresh && cachedTools.length) return cachedTools;
      cachedTools = cleanTools(await mcp('tools/list', {}, { signal }));
      connected = true;
      return cachedTools;
    },
    async callTool(name, args = {}, { signal } = {}) {
      await ready;
      if (typeof name !== 'string' || !/^[A-Za-z0-9_.-]{1,120}$/.test(name)) throw new Error('Invalid Gmail MCP tool.');
      const tools = await this.listTools({ signal });
      if (!tools.some(tool => tool.name === name)) throw new Error('That Gmail MCP tool is not available for this account.');
      const response = await mcp('tools/call', { name, arguments: args && typeof args === 'object' ? args : {} }, { signal, name });
      if (response?.result?.isError) throw new Error('Gmail MCP could not complete ' + name + '.');
      return response?.result ?? response;
    }
  };
}

module.exports = { createGmailMcp, cleanTools, GMAIL_MCP_URL, PROTOCOL_VERSION, GMAIL_SCOPES, defaultTransport };
