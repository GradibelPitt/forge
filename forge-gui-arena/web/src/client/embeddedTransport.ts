import {createContext} from 'react';
export const EmbeddedArtVersions=createContext<Record<string,number>>({});
// The desktop owns the match; this transport only forwards its seat-scoped view.
export const embedded = new URLSearchParams(location.search).has('embedded');
const token = location.hash.slice(1);
if(embedded){document.documentElement.dataset.forgeEmbedded='true';document.title='Forge DIY · 对局';}
export const embeddedImage = (name?:string) => `/image?name=${encodeURIComponent(name??'')}&token=${encodeURIComponent(token)}`;

export function createArenaSocket(endpoint:string):WebSocket {
  if (!embedded) return new WebSocket(endpoint);
  let closed=false;
  let resyncing:Promise<void>|null=null;
  const requests=new Set<AbortController>();
  const listeners=new Map<string,Set<()=>void>>();
  const stream=new EventSource(`/events?token=${encodeURIComponent(token)}`);
  const deliver=(messages:unknown[])=>{if(!closed)for(const message of messages)socket.onmessage?.({data:JSON.stringify(message)});};
  const request=async(path:string,options:RequestInit={})=>{
    const controller=new AbortController();requests.add(controller);
    const timeout=setTimeout(()=>controller.abort(),8000);
    try{
      const response=await fetch(path,{...options,signal:controller.signal});
      if(!response.ok)throw new Error('Bridge request failed');
      return await response.json();
    }finally{clearTimeout(timeout);requests.delete(controller);}
  };
  const resync=()=>{
    if(closed||resyncing)return;
    resyncing=request('/state',{headers:{'X-Forge-Token':token},cache:'no-store'})
      .then(deliver).catch(()=>{if(!closed)socket.onerror?.({});}).finally(()=>{resyncing=null;});
  };
  const restore=()=>{if(document.visibilityState!=='hidden')resync();};
  window.addEventListener('focus',restore);
  document.addEventListener('visibilitychange',restore);
  const socket={readyState:0,onopen:null,onmessage:null,onclose:null,onerror:null,
    addEventListener(type:string,listener:()=>void){if(!listeners.has(type))listeners.set(type,new Set());listeners.get(type)!.add(listener);},
    close(){if(closed)return;closed=true;stream.close();requests.forEach(controller=>controller.abort());window.removeEventListener('focus',restore);document.removeEventListener('visibilitychange',restore);socket.readyState=3;socket.onclose?.({code:1000});listeners.get('close')?.forEach(fn=>fn());},
    send(raw:string){
      if(closed)return;
      const message=JSON.parse(raw);
      if(message.type==='command'||message.type==='leave'){
        void request('/command',{method:'POST',headers:{'Content-Type':'application/json','X-Forge-Token':token},body:raw})
          .then(receipt=>deliver([receipt]))
          .catch(()=>{if(!closed)socket.onerror?.({});});
      }else if(message.type==='resync'){
        resync();
      }
    }
  } as {readyState:number;onopen:null|(()=>void);onmessage:null|((e:{data:string})=>void);onclose:null|((e:{code:number})=>void);onerror:null|((e:object)=>void);addEventListener:(type:string,listener:()=>void)=>void;close:()=>void;send:(raw:string)=>void};
  stream.onopen=()=>{if(!closed){socket.readyState=1;socket.onopen?.();}};
  stream.onmessage=e=>{try{deliver(JSON.parse(e.data));}catch{socket.onerror?.({});socket.close();}};
  stream.onerror=()=>{if(!closed){socket.onerror?.({});socket.close();}};
  return socket as unknown as WebSocket;
}
