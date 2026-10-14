'use strict';
const fs=require('node:fs');const path=require('node:path');
const DEFAULTS={name:'Basani',honorific:'boss',theme:'dark',speak:true,handsfree:false};
function createPreferences(directory){const file=path.join(directory,'preferences.json');let value={...DEFAULTS};try{const saved=JSON.parse(fs.readFileSync(file,'utf8'));value={...value,...sanitize(saved)};}catch{}
 function sanitize(body){const out={};for(const k of ['name','honorific'])if(typeof body[k]==='string')out[k]=body[k].replace(/[\r\n\x00-\x1f]/g,'').slice(0,k==='name'?100:30);if(['dark','light','midnight','aurora'].includes(body.theme))out.theme=body.theme;for(const k of ['speak','handsfree'])if(typeof body[k]==='boolean')out[k]=body[k];return out;}
 return{get:()=>({...value}),save:body=>{value={...value,...sanitize(body)};fs.mkdirSync(directory,{recursive:true});fs.writeFileSync(file+'.tmp',JSON.stringify(value),{mode:0o600});fs.renameSync(file+'.tmp',file);return{...value};}};
}
module.exports={createPreferences};
