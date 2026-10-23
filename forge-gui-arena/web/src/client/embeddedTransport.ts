import {createContext} from 'react';
export const EmbeddedArtVersions=createContext<Record<string,number>>({});
// The desktop owns the match; this transport only forwards its seat-scoped view.
export const embedded = new URLSearchParams(location.search).has('embedded');
const token = location.hash.slice(1);
if(embedded)document.documentElement.dataset.forgeEmbedded='true';
export const embeddedImage = (name?:string) => `/image?name=${encodeURIComponent(name??'')}&token=${encodeURIComponent(token)}`;

export function createArenaSocket(endpoint:string):WebSocket {
  if (!embedded) return new WebSocket(endpoint);
  let closed=false;
  const listeners=new Map<string,Set<()=>void>>();
  const stream=new EventSource(`/events?token=${encodeURIComponent(token)}`);
  const deliver=(messages:unknown[])=>{if(!closed)for(const message of messages)socket.onmessage?.({data:JSON.stringify(message)});};
  const socket={readyState:0,onopen:null,onmessage:null,onclose:null,onerror:null,
    addEventListener(type:string,listener:()=>void){if(!listeners.has(type))listeners.set(type,new Set());listeners.get(type)!.add(listener);},
    close(){if(closed)return;closed=true;stream.close();socket.readyState=3;socket.onclose?.({code:1000});listeners.get('close')?.forEach(fn=>fn());},
    send(raw:string){
      const message=JSON.parse(raw);
      if(message.type==='command'||message.type==='leave'){
        void fetch('/command',{method:'POST',headers:{'Content-Type':'application/json','X-Forge-Token':token},body:raw})
          .then(async response=>{if(!response.ok)throw new Error('Command failed');deliver([await response.json()]);})
          .catch(()=>{if(!closed)socket.onerror?.({});});
      }else if(message.type==='resync'){
        void fetch('/state',{headers:{'X-Forge-Token':token},cache:'no-store'})
          .then(async response=>{if(!response.ok)throw new Error('State unavailable');deliver(await response.json());})
          .catch(()=>{if(!closed)socket.onerror?.({});});
      }
    }
  } as {readyState:number;onopen:null|(()=>void);onmessage:null|((e:{data:string})=>void);onclose:null|((e:{code:number})=>void);onerror:null|((e:object)=>void);addEventListener:(type:string,listener:()=>void)=>void;close:()=>void;send:(raw:string)=>void};
  stream.onopen=()=>{if(!closed){socket.readyState=1;socket.onopen?.();}};
  stream.onmessage=e=>{try{deliver(JSON.parse(e.data));}catch{socket.onerror?.({});socket.close();}};
  stream.onerror=()=>{if(!closed){socket.onerror?.({});socket.close();}};
  return socket as unknown as WebSocket;
}
