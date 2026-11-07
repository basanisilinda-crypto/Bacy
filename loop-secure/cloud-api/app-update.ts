/** Only device-bound, short-lived signed manifests may authorize native self-update. */
export function clientUpdateManifest(installer:any,deviceId:string,currentVersion:number,at:number){
 const common={kind:'client_update',device_id:deviceId,server_time:at,expires:at+3600};
 const code=installer?.version_code;
 if(!Number.isSafeInteger(code)||code<=currentVersion)return {...common,available:false};
 const expected=new URL('https://ourxezawbfclmhbggskv.supabase.co');let u:URL;
 try{u=new URL(installer.apk_url);}catch{throw new Error('Invalid client update storage URL');}
 if(u.origin!==expected.origin||u.username||u.password||u.search||u.hash||!/^\/storage\/v1\/object\/public\/loop-phone-installers\/client-[0-9.]+-[a-f0-9]{64}\.apk$/.test(u.pathname)||!Number.isSafeInteger(installer.bytes)||installer.bytes<1024||installer.bytes>30*1024*1024||!/^[A-Za-z0-9_-]{43}$/.test(installer.apk_sha256))throw new Error('Invalid client update metadata');
 return {...common,available:true,package_name:'za.co.loopmanager',version_name:installer.version,version_code:code,min_sdk:30,apk_url:u.href,apk_sha256:installer.apk_sha256,bytes:installer.bytes};
}
