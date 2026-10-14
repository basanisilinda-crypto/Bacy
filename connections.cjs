'use strict';
const crypto=require('node:crypto');const fs=require('node:fs/promises');const path=require('node:path');
const {request}=require('./secure-http.cjs');const {cryptWindows}=require('./gemini.cjs');
const SCOPES=['openid','email','https://www.googleapis.com/auth/gmail.modify','https://www.googleapis.com/auth/calendar.events'];
function encryptedStore(){const file=process.platform==='win32'&&process.env.LOCALAPPDATA?path.join(process.env.LOCALAPPDATA,'Bacy','google-connection.dpapi'):null;return{
 async load(){if(!file)return null;try{return JSON.parse(await cryptWindows(await fs.readFile(file,'utf8'),true));}catch{return null;}},
 async save(data){if(!file)throw Error('Remembering Google sign-in requires Windows encrypted storage.');const text=await cryptWindows(JSON.stringify(data));await fs.mkdir(path.dirname(file),{recursive:true});await fs.writeFile(file,text,{mode:0o600});},
 async clear(){if(file)await fs.rm(file,{force:true});}
};}
function clean(value,limit=300){return typeof value==='string'?value.slice(0,limit):'';}
function createConnections({env=process.env,transport=request,store=encryptedStore(),now=()=>Date.now()}={}){
 let config={},tokens={},pending=null,remember=false,refreshing;
 const ready=store.load().then(data=>{if(data){config=data.config||{};tokens=data.tokens||{};remember=true;}});
 const persist=()=>remember?store.save({config,tokens}):Promise.resolve();
 async function authorized(url,options={}){
  await ready;if(!tokens.access_token)throw Error('Connect Gmail and Google Calendar in Connections first.');
  if(now()>=tokens.expiresAt){if(!tokens.refresh_token)throw Error('Google sign-in expired. Connect again.');
   refreshing ||= transport({url:'https://oauth2.googleapis.com/token',form:{client_id:config.clientId,client_secret:config.clientSecret||'',refresh_token:tokens.refresh_token,grant_type:'refresh_token'},env}).then(async data=>{tokens={...tokens,...data,expiresAt:now()+Math.max(0,Number(data.expires_in||3600)-60)*1000};await persist();}).finally(()=>{refreshing=null;});await refreshing;
  }
  return transport({url,...options,headers:{Authorization:'Bearer '+tokens.access_token},env});
 }
 return{
  async status(){await ready;return{configured:!!config.clientId,connected:!!tokens.access_token,email:clean(tokens.email),remembered:remember,services:['Gmail','Google Calendar']};},
  async configure(body){await ready;const id=clean(body.clientId,300)||config.clientId;const secret=clean(body.clientSecret,300)||config.clientSecret||'';
   if(!/^[A-Za-z0-9_.-]+\.apps\.googleusercontent\.com$/.test(id))throw Error('Enter your Google Desktop OAuth client ID. The Gemini API key does not connect Gmail.');
   if(/[\r\n]/.test(secret))throw Error('Invalid OAuth client secret.');
   const changed=id!==config.clientId||secret!==config.clientSecret;config={clientId:id,clientSecret:secret};if(changed)tokens={};remember=body.remember===true;if(!remember)await store.clear();await persist();return this.status();
  },
  async begin(port){await ready;if(!config.clientId)throw Error('Set up a Google Desktop OAuth client in Connections first.');
   const state=crypto.randomBytes(32).toString('hex'),verifier=crypto.randomBytes(48).toString('base64url');const redirect='http://127.0.0.1:'+port+'/oauth/google';pending={state,verifier,redirect,expires:now()+600000};
   const query=new URLSearchParams({client_id:config.clientId,redirect_uri:redirect,response_type:'code',scope:SCOPES.join(' '),state,code_challenge:crypto.createHash('sha256').update(verifier).digest('base64url'),code_challenge_method:'S256',access_type:'offline',prompt:'consent'});
   return{url:'https://accounts.google.com/o/oauth2/v2/auth?'+query};
  },
  async callback(query){await ready;const state=query.get('state');if(!pending||!state||state!==pending.state||now()>pending.expires)throw Error('This sign-in request expired or does not match. Start again in Bacy.');const flow=pending;pending=null;
   if(query.get('error'))throw Error('Google sign-in was cancelled or denied.');const code=query.get('code');if(!code||code.length>4096)throw Error('Google did not return a valid sign-in code.');
   const data=await transport({url:'https://oauth2.googleapis.com/token',form:{client_id:config.clientId,client_secret:config.clientSecret||'',code,code_verifier:flow.verifier,redirect_uri:flow.redirect,grant_type:'authorization_code'},env});
   if(!data.access_token)throw Error('Google did not complete sign-in.');tokens={...data,expiresAt:now()+Math.max(0,Number(data.expires_in||3600)-60)*1000};
   const info=await authorized('https://www.googleapis.com/oauth2/v2/userinfo');tokens.email=clean(info.email);await persist();return this.status();
  },
  async disconnect(){await ready;tokens={};pending=null;await persist();return this.status();},
  async inbox(){const list=await authorized('https://gmail.googleapis.com/gmail/v1/users/me/messages?maxResults=12&labelIds=INBOX');return{messages:await Promise.all((list.messages||[]).map(async item=>{
    const data=await authorized('https://gmail.googleapis.com/gmail/v1/users/me/messages/'+encodeURIComponent(item.id)+'?format=metadata&metadataHeaders=Subject&metadataHeaders=From&metadataHeaders=Date');const headers=data.payload?.headers||[];const header=name=>clean(headers.find(h=>h.name.toLowerCase()===name.toLowerCase())?.value,500);return{id:data.id,subject:header('Subject')||'(No subject)',from:header('From'),date:header('Date'),snippet:clean(data.snippet,500),unread:data.labelIds?.includes('UNREAD')||false};
   }))};},
  async message(id){if(!/^[a-zA-Z0-9_-]{1,150}$/.test(id))throw Error('Choose a message from your inbox.');const data=await authorized('https://gmail.googleapis.com/gmail/v1/users/me/messages/'+id+'?format=full');
   const headers=data.payload?.headers||[],header=name=>clean(headers.find(h=>h.name.toLowerCase()===name.toLowerCase())?.value,500);let parts=[];function walk(p){if(p?.mimeType==='text/plain'&&p.body?.data)parts.push(Buffer.from(p.body.data,'base64url').toString('utf8'));for(const child of p?.parts||[])walk(child);}walk(data.payload);return{id,subject:header('Subject'),from:header('From'),text:parts.join('\n').slice(0,30000)||clean(data.snippet,2000)};
  },
  async modifyMessage(body){if(!/^[a-zA-Z0-9_-]{1,150}$/.test(body.id))throw Error('Choose a message from your inbox.');const actions={archive:{removeLabelIds:['INBOX']},read:{removeLabelIds:['UNREAD']},unread:{addLabelIds:['UNREAD']},star:{addLabelIds:['STARRED']}};if(!Object.hasOwn(actions,body.action))throw Error('Choose a supported inbox action.');await authorized('https://gmail.googleapis.com/gmail/v1/users/me/messages/'+body.id+'/modify',{method:'POST',body:actions[body.action]});return{message:'Message updated in Gmail.'};},
  async draft(body){if(typeof body.to!=='string'||!/^[^\s<>\r\n]+@[^\s<>\r\n]+\.[^\s<>\r\n]+$/.test(body.to||'')||body.to.length>200)throw Error('Enter a recipient email address.');if(typeof body.subject!=='string'||!body.subject||body.subject.length>300||/[\r\n]/.test(body.subject)||typeof body.text!=='string'||body.text.length>15000)throw Error('Enter a short subject and message.');const subject='=?UTF-8?B?'+Buffer.from(body.subject).toString('base64')+'?=';const mime=['To: '+body.to,'Subject: '+subject,'MIME-Version: 1.0','Content-Type: text/plain; charset=UTF-8','Content-Transfer-Encoding: base64','',''+Buffer.from(body.text).toString('base64')].join('\r\n');await authorized('https://gmail.googleapis.com/gmail/v1/users/me/drafts',{method:'POST',body:{message:{raw:Buffer.from(mime).toString('base64url')}}});return{message:'Draft saved in Gmail. Review and send it from Gmail.'};},
  async calendar(){const q=new URLSearchParams({maxResults:'20',singleEvents:'true',orderBy:'startTime',timeMin:new Date(now()).toISOString()});const data=await authorized('https://www.googleapis.com/calendar/v3/calendars/primary/events?'+q);return{events:(data.items||[]).map(e=>({id:clean(e.id),title:clean(e.summary)||'(Untitled)',start:clean(e.start?.dateTime||e.start?.date),end:clean(e.end?.dateTime||e.end?.date),link:clean(e.htmlLink,1000)}))};},
  async event(body){if(body.confirm!==true)throw Error('Review the event and click Create event to confirm.');const start=new Date(body.start),end=new Date(body.end);if(typeof body.title!=='string'||!body.title||body.title.length>300||!Number.isFinite(start.getTime())||!Number.isFinite(end.getTime())||end<=start)throw Error('Enter a title, start time and a later end time.');const data=await authorized('https://www.googleapis.com/calendar/v3/calendars/primary/events',{method:'POST',body:{summary:body.title,start:{dateTime:start.toISOString()},end:{dateTime:end.toISOString()}}});return{message:'Event created in your primary Google Calendar.',id:clean(data.id)};}
 };
}
module.exports={createConnections};
