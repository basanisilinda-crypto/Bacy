'use strict';
const {spawn}=require('node:child_process');
const HOSTS=new Set(['oauth2.googleapis.com','gmail.googleapis.com','www.googleapis.com']);
function request({url,headers={},body,form,method,env=process.env,signal}){
 const target=new URL(url);if(target.protocol!=='https:'||!HOSTS.has(target.hostname)||target.username||target.password)throw Error('Unsupported account service.');
 const config=['url = '+JSON.stringify(url),'header = "Accept: application/json"'];
 for(const [name,value]of Object.entries(headers)){if(!/^[A-Za-z-]+$/.test(name)||/[\r\n]/.test(value))throw Error('Invalid request header.');config.push('header = '+JSON.stringify(name+': '+value));}
 const proxy=env.HTTPS_PROXY||env.https_proxy;if(proxy)config.push('proxy = '+JSON.stringify(proxy));
 if(method)config.push('request = '+JSON.stringify(method));
 if(form||body){config.push('header = '+JSON.stringify('Content-Type: '+(form?'application/x-www-form-urlencoded':'application/json')),'data = '+JSON.stringify(form?new URLSearchParams(form).toString():JSON.stringify(body)));}
 return new Promise((resolve,reject)=>{
  const child=spawn(process.platform==='win32'?'curl.exe':'curl',['--config','-','--silent','--show-error','--max-time','60','--write-out','\n__BACY_HTTP__%{http_code}'],{env,windowsHide:true,stdio:['pipe','pipe','pipe']});
  let output='',settled=false;const timer=setTimeout(()=>{child.kill();done(Error('The account request timed out. Check your proxy.'));},65000);
  const abort=()=>{child.kill();done(Error('Account request cancelled.'));};
  function done(error,data){if(settled)return;settled=true;clearTimeout(timer);signal?.removeEventListener('abort',abort);error?reject(error):resolve(data);}
  child.stdout.setEncoding('utf8');child.stdout.on('data',c=>{output+=c;if(output.length>4000000){child.kill();done(Error('The account response was too large.'));}});child.stderr.resume();child.stdin.on('error',()=>{});
  child.on('error',()=>done(Error('The secure Windows HTTP client could not start.')));
  child.on('close',code=>{if(settled)return;if(code!==0)return done(Error('Could not reach Google securely. Check your Windows proxy.'));
   const i=output.lastIndexOf('\n__BACY_HTTP__'),status=Number(output.slice(i+14));let data;
   try{data=JSON.parse(output.slice(0,i));}catch{return done(Error('Google returned an unreadable account response.'));}
   if(i<0||!Number.isInteger(status)||status<200||status>=300){const e=Error(status===401?'Google sign-in expired. Connect your account again.':status===403?'Google denied access. Check enabled APIs, consent scopes and test users.':status===429?'Google account API quota reached. Try later.':'Google could not complete this account request.');e.status=status;return done(e);}done(null,data);
  });signal?.addEventListener('abort',abort,{once:true});if(signal?.aborted)abort();else child.stdin.end(config.join('\n')+'\n');
 });
}
module.exports={request};
