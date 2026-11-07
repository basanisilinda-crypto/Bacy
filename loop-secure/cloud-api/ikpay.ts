/** iK Pay request/callback signing. Credentials stay in server storage. */
export const IK_ORIGIN='https://api.ikhokha.com';
const encoder=new TextEncoder();
export function escapedIkPayload(path:string,rawBody=''){return (path+rawBody).replace(/[\\"']/g,'\\$&').replace(/\u0000/g,'\\0');}
async function key(secret:string){if(!secret.trim())throw new Error('Missing payment signing secret');return crypto.subtle.importKey('raw',encoder.encode(secret.trim()),{name:'HMAC',hash:'SHA-256'},false,['sign','verify']);}
export async function ikSign(path:string,rawBody:string,secret:string){const bytes=new Uint8Array(await crypto.subtle.sign('HMAC',await key(secret),encoder.encode(escapedIkPayload(path,rawBody))));return Array.from(bytes,b=>b.toString(16).padStart(2,'0')).join('');}
export async function verifyIkCallback(path:string,rawBody:string,signature:string,secret:string){
 if(!/^[a-f0-9]{64}$/i.test(signature))return false;
 const bytes=Uint8Array.from(signature.match(/../g)!,x=>parseInt(x,16));
 const variants=[rawBody];
 // The official webhook example signs JSON.stringify(body) after removing its text wrapper.
 try{const parsed=JSON.parse(rawBody);if(!parsed||Array.isArray(parsed)||typeof parsed!=='object')return false;delete parsed.text;const canonical=JSON.stringify(parsed);if(canonical!==rawBody)variants.push(canonical);}catch{return false;}
 const signingKey=await key(secret);for(const body of variants)if(await crypto.subtle.verify('HMAC',signingKey,bytes,encoder.encode(escapedIkPayload(path,body))))return true;return false;
}
export function isPaidForIntent(intent:any,result:any){
 return Number.isSafeInteger(intent?.amount_minor)&&intent.amount_minor>0&&intent.amount_minor<=30000&&intent.amount_minor%100===0&&intent?.currency==='ZAR'&&typeof intent.paylink_id==='string'&&result?.status==='PAID'&&result.paylinkID===intent.paylink_id&&Number.isSafeInteger(result.amount)&&result.amount===intent.amount_minor&&(result.currency===undefined||result.currency==='ZAR')&&(result.externalTransactionID===undefined||result.externalTransactionID===intent.id);
}
export function trustedCheckoutUrl(value:string){try{const url=new URL(value);return url.protocol==='https:'&&!url.username&&!url.password&&!url.port&&!url.hash&&(url.hostname==='ikhokha.com'||url.hostname.endsWith('.ikhokha.com')||url.hostname==='ikhokha.red'||url.hostname.endsWith('.ikhokha.red'));}catch{return false;}}
export async function ikRequest(config:{app_id:string;secret:string},path:string,body?:Record<string,unknown>){
 if(!/^\/public-api\/v1\/api\/(?:payment|getStatus\/[A-Za-z0-9_-]{3,100})$/.test(path))throw new Error('Invalid provider API path');
 const raw=body===undefined?'':JSON.stringify(body);const response=await fetch(IK_ORIGIN+path,{method:body===undefined?'GET':'POST',headers:{'Content-Type':'application/json',Accept:'application/json','IK-APPID':config.app_id.trim(),'IK-SIGN':await ikSign(path,raw,config.secret)},body:body===undefined?undefined:raw,redirect:'manual',signal:AbortSignal.timeout(20000)});
 const reader=response.body?.getReader();let size=0;const chunks:Uint8Array[]=[];
 if(reader)while(true){const {value,done}=await reader.read();if(done)break;size+=value.length;if(size>65536){await reader.cancel();throw new Error('Payment provider returned an oversized response');}chunks.push(value);}
 if(!response.ok)throw new Error('Payment provider request failed ('+response.status+'). Check the merchant application configuration.');
 const all=new Uint8Array(size);let offset=0;for(const chunk of chunks){all.set(chunk,offset);offset+=chunk.length;}
 let result;try{result=JSON.parse(new TextDecoder().decode(all));}catch{throw new Error('Payment provider returned an unreadable response');}
 if(!result||typeof result!=='object'||Array.isArray(result))throw new Error('Unexpected payment provider response');return result;
}
