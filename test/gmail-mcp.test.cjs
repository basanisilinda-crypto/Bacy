'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const { createGmailMcp, GMAIL_MCP_URL, PROTOCOL_VERSION } = require('../gmail-mcp.cjs');

function memoryStore(saved = null) {
  let value = saved;
  return { async load(){return value;}, async save(next){value=next;}, async clear(){value=null;}, get value(){return value;} };
}

test('Gmail MCP OAuth configuration keeps secrets private and discovers tools', async () => {
  const store = memoryStore();
  const calls = [];
  const gmail = createGmailMcp({
    store,
    now: () => 1_000_000,
    transport: async request => {
      calls.push(request);
      if (request.url.includes('oauth2.googleapis.com/token')) return { access_token: 'access-token', refresh_token: 'refresh-token', expires_in: 3600 };
      const body = JSON.parse(request.body);
      assert.equal(request.url, GMAIL_MCP_URL);
      assert.equal(request.headers['MCP-Protocol-Version'], PROTOCOL_VERSION);
      if (body.method === 'tools/list') return { jsonrpc:'2.0', id:body.id, result:{ tools:[{ name:'search_threads', description:'Search Gmail', inputSchema:{type:'object',properties:{query:{type:'string'}}} }] } };
      throw new Error('unexpected request');
    }
  });
  const clientId = '1234567890-abcdefghijklmnopqrstuvwxyz.apps.googleusercontent.com';
  const clientSecret = 'GOCSPX-abcdefghijklmnopqrstuvwxyz';
  const configured = await gmail.configure({ clientId, clientSecret, remember:true });
  assert.equal(configured.configured, true);
  assert.equal(JSON.stringify(configured).includes(clientSecret), false);
  const auth = await gmail.beginAuth('http://127.0.0.1:3000');
  const state = new URL(auth.url).searchParams.get('state');
  const result = await gmail.completeAuth({ code:'oauth-code', state, baseUrl:'http://127.0.0.1:3000' });
  assert.equal(result.connected, true);
  assert.deepEqual(result.tools, ['search_threads']);
  assert.equal((await gmail.status()).connected, true);
  assert.equal(JSON.stringify(await gmail.status()).includes(clientSecret), false);
  assert.equal(store.value.refreshToken, 'refresh-token');
  assert.ok(calls.length >= 2);
});

test('Gmail MCP only calls tools returned by the server', async () => {
  const store = memoryStore({
    clientId:'1234567890-abcdefghijklmnopqrstuvwxyz.apps.googleusercontent.com',
    clientSecret:'GOCSPX-abcdefghijklmnopqrstuvwxyz',
    accessToken:'access-token', refreshToken:'refresh-token', expiresAt:9_999_999_999, remember:true
  });
  const gmail = createGmailMcp({
    store, now:()=>1_000_000,
    transport: async request => {
      const body=JSON.parse(request.body);
      if(body.method==='tools/list') return {jsonrpc:'2.0',id:body.id,result:{tools:[{name:'search_threads',description:'Search',inputSchema:{type:'object',properties:{}}}]}};
      if(body.method==='tools/call') return {jsonrpc:'2.0',id:body.id,result:{content:[{type:'text',text:'ok'}]}};
      throw new Error('unexpected');
    }
  });
  const result = await gmail.callTool('search_threads',{query:'latest'});
  assert.equal(result.content[0].text,'ok');
  await assert.rejects(gmail.callTool('send_email',{}),/not available/);
});
