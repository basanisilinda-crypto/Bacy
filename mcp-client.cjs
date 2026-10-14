'use strict';
const crypto=require('node:crypto');const {validate}=require('./tool-registry.cjs');
const MODERN='2026-07-28',LEGACY=['2025-11-25','2025-06-18','2025-03-26','2024-11-05','2024-10-07'];
function createMcpClient({exchange,now=()=>Date.now()}={}){
 let epoch=0,verificationController=null;let version=MODERN,era='modern',session='',verified=false,checking=null,serverInfo=null,tools=[],checkedAt=null;
 async function rpc(method,params={},signal,notification=false){
  if(signal?.aborted)throw new Error('MCP request cancelled.');const generation=epoch;const id=crypto.randomUUID();
  const meta={'io.modelcontextprotocol/protocolVersion':version,'io.modelcontextprotocol/clientInfo':{name:'Bacy',version:'5.0'},'io.modelcontextprotocol/clientCapabilities':{}};
  const body={jsonrpc:'2.0',...(notification?{}:{id}),method,params:era==='modern'?{...params,_meta:{...params._meta,...meta}}:params};
  const headers={'MCP-Protocol-Version':version,'Mcp-Method':method};if(session)headers['Mcp-Session-Id']=session;if(method==='tools/call')headers['Mcp-Name']=params.name;
  const data=await exchange(body,headers,{signal,expectedId:notification?undefined:id});
  if(signal?.aborted||epoch!==generation)throw new Error('MCP connection changed or request cancelled.');
  if(notification)return data;if(!data||data.jsonrpc!=='2.0'||data.id!==id)throw new Error('MCP returned a mismatched or invalid JSON-RPC response.');
  if(data.error){const error=new Error('MCP rejected '+method+' ('+Number(data.error.code)+').');error.code=Number(data.error.code);error.data=data.error.data;throw error;}
  if(!Object.hasOwn(data,'result')||!data.result||typeof data.result!=='object')throw new Error('MCP returned no valid result.');
  const returnedSession=data.httpHeaders?.['mcp-session-id'];if(returnedSession){if(typeof returnedSession!=='string'||returnedSession.length>512||/[\r\n]/.test(returnedSession))throw new Error('MCP returned an invalid session identifier.');session=returnedSession;}
  return data.result;
 }
 async function initialize(signal){
  version=MODERN;era='modern';session='';let discovery;
  try{discovery=await rpc('server/discover',{},signal);if(!Array.isArray(discovery.supportedVersions)||!discovery.supportedVersions.includes(MODERN)||!discovery.capabilities||typeof discovery.capabilities!=='object')throw new Error('MCP server discovery did not advertise a compatible protocol.');serverInfo=discovery._meta?.['io.modelcontextprotocol/serverInfo']||null;}
  catch(error){if(error.code!==-32601&&error.code!==-32022&&error.status!==400)throw error;
   era='legacy';version=LEGACY[0];discovery=await rpc('initialize',{protocolVersion:version,capabilities:{},clientInfo:{name:'Bacy',version:'5.0'}},signal);
   if(!LEGACY.includes(discovery.protocolVersion)||!discovery.capabilities||typeof discovery.capabilities!=='object')throw new Error('MCP initialization returned an unsupported protocol or capabilities.');version=discovery.protocolVersion;serverInfo=discovery.serverInfo||null;await rpc('notifications/initialized',{},signal,true);
  }
  if(!Object.hasOwn(discovery.capabilities,'tools'))throw new Error('This MCP server did not advertise tool support.');
 }
 async function discover(signal){
  const found=[],names=new Set();let cursor;const cursors=new Set();
  for(let page=0;page<8;page++){
   const data=await rpc('tools/list',cursor?{cursor}:{},signal);if(!Array.isArray(data.tools))throw new Error('MCP returned an invalid tool catalog.');
   for(const tool of data.tools){if(!tool||!/^[A-Za-z_][A-Za-z0-9_.-]{0,63}$/.test(tool.name)||names.has(tool.name)||!tool.inputSchema||tool.inputSchema.type!=='object'||JSON.stringify(tool.inputSchema).length>12000)throw new Error('MCP returned an invalid or duplicate tool definition.');names.add(tool.name);found.push({name:tool.name,description:String(tool.description||'').slice(0,1800),inputSchema:tool.inputSchema,annotations:tool.annotations||{}});}
   if(found.length>128)throw new Error('MCP returned too many tools.');if(!data.nextCursor)return found;
   if(typeof data.nextCursor!=='string'||data.nextCursor.length>1000||cursors.has(data.nextCursor))throw new Error('MCP returned an invalid pagination cursor.');cursor=data.nextCursor;cursors.add(cursor);
  }throw new Error('MCP tool discovery exceeded its page limit.');
 }
 return{
  state(){return{connectionId:epoch,verified,protocolVersion:verified?version:null,era:verified?era:null,checkedAt,serverInfo:serverInfo?{name:String(serverInfo.name||'').slice(0,100),version:String(serverInfo.version||'').slice(0,60)}:null};},
  reset(){epoch++;verificationController?.abort();verificationController=null;verified=false;checking=null;session='';tools=[];checkedAt=null;serverInfo=null;},
  async list({signal,refresh=false}={}){if(verified&&!refresh)return tools;if(checking)return checking;const generation=epoch,controller=new AbortController();verificationController=controller;const abort=()=>controller.abort();signal?.addEventListener('abort',abort,{once:true});if(signal?.aborted)controller.abort();let job;job=(async()=>{verified=false;await initialize(controller.signal);const found=await discover(controller.signal);if(epoch!==generation||controller.signal.aborted)throw new Error('MCP connection changed during verification.');tools=found;verified=true;checkedAt=now();return tools;})().finally(()=>{signal?.removeEventListener('abort',abort);if(checking===job)checking=null;if(verificationController===controller)verificationController=null;});checking=job;return job;},
  async call(name,args,{signal}={}){const catalog=await this.list({signal});const tool=catalog.find(item=>item.name===name);if(!tool)throw new Error('That tool is not in the verified MCP catalog.');validate(tool.inputSchema,args);const result=await rpc('tools/call',{name,arguments:args},signal);if(result.isError)throw new Error('MCP tool '+name+' reported a failure.');return result;}
 };
}
module.exports={createMcpClient,MODERN,LEGACY};
