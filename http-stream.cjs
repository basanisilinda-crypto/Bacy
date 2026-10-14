'use strict';
const {spawn}=require('node:child_process');
const HOSTS=new Set(['api.elevenlabs.io','gmailmcp.googleapis.com','oauth2.googleapis.com','accounts.google.com']);
function proxyCandidates(env={}){return [env.BACY_SYSTEM_PROXY||env.HTTPS_PROXY||env.https_proxy||env.HTTP_PROXY||env.http_proxy||env.ALL_PROXY||env.all_proxy||''];}
function abortError(){const e=new Error('Request cancelled.');e.name='AbortError';return e;}
function requestStream({url,method='GET',headers={},body,env=process.env,signal,timeoutMs=30000,command}={}){
 if(signal?.aborted)return Promise.reject(abortError());
 const target=new URL(url);if(target.protocol!=='https:'||!HOSTS.has(target.hostname)||target.username||target.password)return Promise.reject(new Error('Unsupported service destination.'));
 const config=['url = '+JSON.stringify(url),'request = '+JSON.stringify(method)];
 for(const [name,value]of Object.entries(headers)){if(!/^[A-Za-z0-9-]+$/.test(name)||/[\r\n]/.test(value))return Promise.reject(new Error('Invalid HTTP header.'));config.push('header = '+JSON.stringify(name+': '+value));}
 const proxy=proxyCandidates(env)[0];config.push(proxy?'proxy = '+JSON.stringify(proxy):'noproxy = "*"');if(body!==undefined)config.push('data = '+JSON.stringify(typeof body==='string'?body:JSON.stringify(body)));
 const childEnv={...env};for(const key of ['HTTPS_PROXY','https_proxy','HTTP_PROXY','http_proxy','ALL_PROXY','all_proxy'])delete childEnv[key];
 return new Promise((resolve,reject)=>{
  const chosen=command||{executable:process.platform==='win32'?'curl.exe':'curl',args:['--config','-','--silent','--show-error','--include','--no-buffer','--suppress-connect-headers','--connect-timeout','5','--max-time',String(Math.ceil(timeoutMs/1000))]};
  const child=spawn(chosen.executable,chosen.args,{env:childEnv,windowsHide:true,stdio:['pipe','pipe','pipe']});
  let pending=Buffer.alloc(0),head=false,done=false,control,status=0;
  const stream=new ReadableStream({start(controller){control=controller;},cancel(){finish(undefined,true);}});
  const timeout=setTimeout(()=>finish(new Error('The service request timed out. Check your proxy.')),timeoutMs+1000);
  const abort=()=>finish(abortError());
  function finish(error,cancel=false){if(done)return;done=true;clearTimeout(timeout);signal?.removeEventListener('abort',abort);child.kill();if(!head)reject(error||abortError());else if(error)control.error(error);else if(cancel){/* cancelled reader owns stream */}else control.close();}
  child.stdin.on('error',()=>{});child.stderr.resume();
  child.on('error',()=>finish(new Error('The secure HTTP client could not start. Windows curl.exe is required.')));
  child.stdout.on('data',chunk=>{
   if(done)return;if(head){control.enqueue(new Uint8Array(chunk));return;}pending=Buffer.concat([pending,chunk]);
   while(!head){const boundary=pending.indexOf('\r\n\r\n');if(boundary<0){if(pending.length>65536)finish(new Error('Invalid HTTP headers.'));return;}
    const lines=pending.subarray(0,boundary).toString('utf8').split('\r\n');pending=pending.subarray(boundary+4);status=Number(lines[0].match(/^HTTP\/\S+ (\d{3})/)?.[1]);if(status>=100&&status<200)continue;
    if(status<200||status>=300||!Number.isInteger(status)){const e=new Error(status===401||status===403?'The service denied account access. Check your key or Google sign-in.':status===429?'The service quota or rate limit was reached.':`The service returned HTTP ${status||'unknown'}.`);e.status=status;return finish(e);}
    const fields={};for(const line of lines.slice(1)){const i=line.indexOf(':');if(i>0)fields[line.slice(0,i).toLowerCase()]=line.slice(i+1).trim();}
    head=true;resolve({status,headers:fields,body:stream});if(pending.length)control.enqueue(new Uint8Array(pending));pending=Buffer.alloc(0);
   }
  });
  child.on('close',code=>{if(done)return;if(code!==0)return finish(new Error('The secure service connection failed. Check your Windows proxy.'));if(!head)return finish(new Error('The service returned an incomplete response.'));finish();});
  signal?.addEventListener('abort',abort,{once:true});if(signal?.aborted)abort();else child.stdin.end(config.join('\n')+'\n');
 });
}
module.exports={requestStream,proxyCandidates,abortError};
