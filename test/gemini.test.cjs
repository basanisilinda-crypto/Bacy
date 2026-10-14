'use strict';
const {test}=require('node:test');
const assert=require('node:assert/strict');
const {createGemini,curlRequest}=require('../gemini.cjs');
const {createBacyServer}=require('../server.cjs');
const key='FAKE_TEST_KEY_NOT_A_CREDENTIAL';
const store={load:async()=>null,clear:async()=>{},save:async()=>{}};
test('Gemini uses structured generation and excludes thought text and keys from status',async()=>{
 const calls=[];
 const google=createGemini({env:{},store,transport:async request=>{calls.push(request);return request.body?{candidates:[{finishReason:'STOP',content:{parts:[{thought:true,text:'private reasoning'},{text:JSON.stringify({reply:'Hello',mood:'happy',preview:{kind:'none',title:'',items:[]}})}]}}]}:{models:[{name:'models/gemini-flash-latest',supportedGenerationMethods:['generateContent']}]};}});
 assert.equal((await google.status()).configured,false);
 await google.configure({key});
 assert.equal((await google.test()).models[0].id,'gemini-flash-latest');
 assert.equal(JSON.parse(await google.generate('Hello')).reply,'Hello');
 assert.equal(calls[1].body.generationConfig.responseMimeType,'application/json');
 assert.equal(calls[1].key,key);
 assert.equal(JSON.stringify(await google.status()).includes(key),false);
 await google.configure({forget:true});assert.equal((await google.status()).configured,false);
});
test('curl protocol sanitizes quota errors and sends credentials only through stdin',async()=>{
 const script="let s='';process.stdin.on('data',c=>s+=c);process.stdin.on('end',()=>{if(!s.includes('x-goog-api-key: '+process.env.TEST_FAKE_KEY))process.exit(2);process.stdout.write(JSON.stringify({error:{message:process.env.TEST_FAKE_KEY}})+'\\n__BACY_HTTP__429')})";
 await assert.rejects(curlRequest({url:'https://generativelanguage.googleapis.com/v1beta/models',key,env:{...process.env,TEST_FAKE_KEY:key},command:{executable:process.execPath,args:['-e',script]}}),error=>/quota/.test(error.message)&&!error.message.includes(key));
});
test('Gemini server configuration and chat avoid Codex and never return the key',async t=>{
 const google=createGemini({env:{},store,transport:async()=>({candidates:[{content:{parts:[{text:JSON.stringify({reply:'Gemini reply',mood:'happy',preview:{kind:'none',title:'',items:[]}})}]}}]})});
 const server=createBacyServer({env:{},gemini:google,defaultProvider:'gemini',runner:async()=>{throw new Error('Codex must not run');}});
 await new Promise(r=>server.listen(0,'127.0.0.1',r));t.after(()=>new Promise(r=>server.close(r)));
 const base='http://127.0.0.1:'+server.address().port;
 const html=await(await fetch(base)).text();const token=html.match(/name="bacy-token" content="([a-f0-9]{64})"/)[1];
 const post=(route,body)=>fetch(base+route,{method:'POST',headers:{'content-type':'application/json','x-bacy-token':token},body:JSON.stringify(body)});
 assert.equal((await(await fetch(base+'/api/status')).json()).available,false);
 const configuration=await(await post('/api/ai/configure',{provider:'gemini',key})).json();assert.equal(JSON.stringify(configuration).includes(key),false);
 const response=await(await post('/api/chat',{mode:'normal',messages:[{role:'user',content:'Hello'}]})).json();
 assert.equal(response.reply,'Gemini reply');assert.equal(response.provider,'gemini');assert.equal(response.mood,'happy');
 assert.equal(JSON.stringify(await(await fetch(base+'/api/status')).json()).includes(key),false);
});
