'use strict';const crypto=require('node:crypto');
function createPendingActions({now=()=>Date.now()}={}){const pending=new Map();return{
 propose(name,args,run){for(const[id,entry]of pending)if(entry.expires<now())pending.delete(id);if(pending.size>=8)throw new Error('Review or cancel an existing action first.');const id=crypto.randomBytes(6).toString('hex');const encoded=JSON.stringify(args);if(encoded.length>8000)throw new Error('The proposed action is too large to review. Narrow the request.');const snapshot=JSON.parse(encoded);pending.set(id,{name,args:snapshot,run,expires:now()+300000});return{pending:true,id,message:'Review '+name+': '+JSON.stringify(snapshot)+'. Type confirm '+id+' to run this exact action, or cancel '+id+'. The action has not run.'};},
 async confirm(id){const item=pending.get(id);pending.delete(id);if(!item||item.expires<now())throw new Error('That action expired or was already handled. Request it again.');return item.run(item.args);},
 cancel(id){const deleted=pending.delete(id);return{message:deleted?'Action cancelled.':'No pending action with that ID.'};}
};}
module.exports={createPendingActions};
