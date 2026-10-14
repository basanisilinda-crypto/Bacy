'use strict';const {test}=require('node:test');const assert=require('node:assert/strict');const {createGemini}=require('../gemini.cjs');const {requestStream,proxyCandidates}=require('../http-stream.cjs');
const store={load:async()=>null,clear:async()=>{},save:async()=>{}};const env={GEMINI_API_KEY:'FAKE_TEST_KEY_NOT_A_CREDENTIAL'};
const answer={candidates:[{content:{parts:[{text:'{"reply":"Hello","mood":"neutral","preview":{"kind":"none","title":"","items":[]}}'}]}}]};
test('Gemini calls selected model first and stops on quota/service errors',async()=>{
 const calls=[];const google=createGemini({env:{...env,GEMINI_MODEL:'selected-text-model'},store,transport:async req=>{calls.push(req.url);return answer;}});await google.generate('Hello');assert.match(calls[0],/selected-text-model:generateContent/);assert.equal(calls.length,1);
 for(const status of [429,503]){let count=0;const service=createGemini({env,store,transport:async()=>{count++;const error=Error('Service unavailable');error.status=status;throw error;}});await assert.rejects(service.generate('Hello'));assert.equal(count,1);}
});
test('Missing model refreshes available catalog once and does not guess model IDs',async()=>{
 const calls=[];const google=createGemini({env:{...env,GEMINI_MODEL:'removed-model'},store,transport:async req=>{calls.push(req.url);if(req.url.includes('removed-model')){const error=Error('Not found');error.status=404;throw error;}if(!req.body)return{models:[{name:'models/available-flash-lite',supportedGenerationMethods:['generateContent']}]};return answer;}});
 await google.generate('Hello');assert.equal(calls.length,3);assert.match(calls[2],/available-flash-lite:generateContent/);
});
test('Gemini honours cancellation and a total deadline',async()=>{
 let count=0;const google=createGemini({env,store,requestTimeoutMs:50,transport:req=>{count++;return new Promise((resolve,reject)=>{req.signal.addEventListener('abort',()=>reject(Error('Stopped')),{once:true});});}});
 await assert.rejects(google.generate('Hello'),/within 1 seconds/);assert.equal(count,1);
 const stop=new AbortController();stop.abort();await assert.rejects(google.generate('Hello',{signal:stop.signal}),{name:'AbortError'});assert.equal(count,1);
});
test('Current Windows proxy is honoured without a direct probing fallback',()=>{assert.deepEqual(proxyCandidates({HTTPS_PROXY:'http://old.invalid',BACY_SYSTEM_PROXY:'http://current.invalid'}),['http://current.invalid']);assert.deepEqual(proxyCandidates({}),['']);});
test('Secure client delivers first audio before upstream ends and keeps headers in stdin',async()=>{
 const script="let s='';process.stdin.on('data',c=>s+=c);process.stdin.on('end',()=>{if(!s.includes('xi-api-key: FAKE_TEST_KEY_NOT_A_CREDENTIAL'))process.exit(2);process.stdout.write('HTTP/1.1 200 OK\\r\\nContent-Type: audio/mpeg\\r\\n\\r\\nfirst-audio');setTimeout(()=>process.stdout.end('last-audio'),5000);})";
 const started=Date.now();const response=await requestStream({url:'https://api.elevenlabs.io/v1/test',headers:{'xi-api-key':env.GEMINI_API_KEY},command:{executable:process.execPath,args:['-e',script]},env:{},timeoutMs:6000});const reader=response.body.getReader();const part=await reader.read();assert.equal(Buffer.from(part.value).toString(),'first-audio');assert.ok(Date.now()-started<1500);await reader.cancel();
});

test('Gmail SSE resolves its JSON-RPC result without waiting for a persistent connection to close',async()=>{
 const {defaultTransport}=require('../gmail-mcp.cjs');let cancelled=false;
 const stream=new ReadableStream({start(controller){controller.enqueue(new TextEncoder().encode('event: message\ndata: {"jsonrpc":"2.0","id":7,"result":{"tools":[]}}\n\n'));},cancel(){cancelled=true;}});
 const result=await defaultTransport({url:'https://gmailmcp.googleapis.com/mcp/v1',streamTransport:async()=>({headers:{'content-type':'text/event-stream'},body:stream})});assert.deepEqual(result.result,{tools:[]});assert.equal(cancelled,true);
});
