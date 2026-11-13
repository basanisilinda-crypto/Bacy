// Only known installer objects and expired session credentials are eligible.
export function installerCleanupPlan(objects:any[], currentUrls:string[], now=Date.now()){
 const pattern=/^(client|admin|preview)-[0-9.]+-[a-f0-9]{64}\.apk$/;
 const keep=new Set(currentUrls.map(url=>{try{return new URL(url).pathname.split('/').pop();}catch{return '';}}));
 const groups=new Map<string,any[]>();
 for(const object of objects){const match=pattern.exec(object.name);if(!match)continue;const list=groups.get(match[1])||[];list.push(object);groups.set(match[1],list);}
 for(const list of groups.values())list.sort((a,b)=>Date.parse(b.created_at)-Date.parse(a.created_at)).slice(0,3).forEach(o=>keep.add(o.name));
 return objects.filter(o=>pattern.test(o.name)&&!keep.has(o.name)&&Number.isFinite(Date.parse(o.created_at))&&Date.parse(o.created_at)<now-86400000);
}
export async function maintainStorage(db:any, dryRun=false){
 const now=Date.now(), checkpoint=await db.from('loop_config').select('value').eq('key','maintenance:last').maybeSingle();
 if(checkpoint.error)throw new Error('Maintenance status unavailable');
 if(!dryRun&&now-(checkpoint.data?.value?.at||0)<86400000)return {skipped:true};
 const meta=await db.from('loop_config').select('value').in('key',['installer_current','admin_installer_current','preview_installer_current']);
 if(meta.error||meta.data?.length!==3)throw new Error('Current installer metadata unavailable; cleanup stopped');
 const listed=await db.storage.from('loop-phone-installers').list('',{limit:1000,sortBy:{column:'created_at',order:'desc'}});
 if(listed.error)throw new Error('Installer inventory unavailable');
 // Fail closed if inventory reaches the page limit; no partial-retention decision.
 if(listed.data.length>=1000)throw new Error('Installer inventory needs paginated review');
 const plan=installerCleanupPlan(listed.data,meta.data.map((r:any)=>r.value.apk_url),now);
 const bytes=plan.reduce((n:number,o:any)=>n+Number(o.metadata?.size||0),0);
 if(dryRun)return {dry_run:true,candidates:plan.length,reclaimable_bytes:bytes,retention:'Current installers, latest three of each type and files under 24 hours'};
 // Re-read pointers immediately before deletion. Upload-time maintenance and overview maintenance share the same retention rules.
 const latest=await db.from('loop_config').select('value').in('key',['installer_current','admin_installer_current','preview_installer_current']);
 if(latest.error||latest.data?.length!==3)throw new Error('Installer protection check failed');
 const safe=installerCleanupPlan(listed.data,latest.data.map((r:any)=>r.value.apk_url),now).map((o:any)=>o.name);
 if(safe.length){const result=await db.storage.from('loop-phone-installers').remove(safe);if(result.error)throw new Error('Installer cleanup failed');}
 const temporary=await db.rpc('loop_cleanup_expired');if(temporary.error)throw new Error('Expired session cleanup failed');
 const report={at:now,removed_installers:safe.length,reclaimed_bytes:bytes,expired_credentials:temporary.data};
 const saved=await db.from('loop_config').upsert({key:'maintenance:last',value:report});if(saved.error)throw new Error('Maintenance report could not be saved');
 return report;
}
