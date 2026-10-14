'use strict';
const fs=require('node:fs/promises');const path=require('node:path');
function createMemory({directory,store}={}){
 const file=directory?path.join(directory,'bacy-memory.json'):null;
 const storage=store||{async load(){try{return JSON.parse(await fs.readFile(file,'utf8'));}catch(e){if(e.code==='ENOENT')return{};throw new Error('Local memory could not be loaded. Your saved file was not overwritten.');}},async save(data){await fs.mkdir(directory,{recursive:true});await fs.writeFile(file+'.tmp',JSON.stringify(data),{mode:0o600});await fs.rename(file+'.tmp',file);}};
 let facts=Object.create(null),queue=Promise.resolve();
 const ready=storage.load().then(data=>{for(const[key,item]of Object.entries(data||{}))if(/^[a-z0-9 _.-]{1,64}$/i.test(key)&&item&&typeof item.value==='string'&&item.value.length<=1000)facts[key]={value:item.value,updated:Number(item.updated)||0};});
 ready.catch(()=>{}); // Keep a failed load reviewable on use without crashing startup.
 const transaction=fn=>{const job=queue.then(async()=>{await ready;const next=Object.assign(Object.create(null),facts);const result=fn(next);await storage.save(next);facts=next;return result;});queue=job.catch(()=>{});return job;};
 return{
  async remember(key,value){key=String(key||'').trim().toLowerCase();value=String(value||'').trim();if(!/^[a-z0-9 _.-]{1,64}$/i.test(key)||!value||value.length>1000)throw new Error('Use remember topic = fact, up to 1,000 characters.');if(/password|api.?key|secret|access.?token|private.?key/i.test(key))throw new Error('Use the private connection settings for credentials, not assistant memory.');return transaction(next=>{if(!Object.hasOwn(next,key)&&Object.keys(next).length>=200)throw new Error('Memory has 200 facts. Forget an old fact before adding another.');next[key]={value,updated:Date.now()};return{message:'Remembered '+key+'.'};});},
  async forget(key){key=String(key||'').trim().toLowerCase();return transaction(next=>{if(!Object.hasOwn(next,key))return{message:'No saved fact called '+key+'.'};delete next[key];return{message:'Forgot '+key+'.'};});},
  async recall(query=''){await ready;await queue;const words=String(query).toLowerCase().match(/[a-z0-9]{2,}/g)||[];return Object.entries(facts).map(([key,item])=>({key,...item,score:words.reduce((n,w)=>n+(key.includes(w)?3:item.value.toLowerCase().includes(w)?1:0),0)})).filter(item=>!words.length||item.score).sort((a,b)=>b.score-a.score||b.updated-a.updated).slice(0,12).map(({key,value})=>({key,value}));},
  async context(query){const relevant=await this.recall(query),recent=await this.recall();const selected=[];let budget=0;for(const item of [...relevant,...recent]){if(selected.some(x=>x.key===item.key))continue;const length=JSON.stringify(item).length;if(budget+length>1200)continue;selected.push(item);budget+=length;}return JSON.stringify({facts:selected,otherKeys:Object.keys(facts).filter(key=>!selected.some(x=>x.key===key)).slice(0,12)});}
 };
}
module.exports={createMemory};
