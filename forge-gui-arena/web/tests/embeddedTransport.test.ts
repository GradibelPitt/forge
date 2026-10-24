import test from 'node:test';
import assert from 'node:assert/strict';

class PushSource {
  static instances:PushSource[]=[];
  onopen:(()=>void)|null=null;
  onmessage:((e:{data:string})=>void)|null=null;
  onerror:(()=>void)|null=null;
  closed=false;
  constructor(readonly url:string){PushSource.instances.push(this);}
  close(){this.closed=true;}
}
Object.defineProperty(globalThis,'location',{configurable:true,value:{search:'?embedded',hash:'#test-token'}});
const windowTarget=new EventTarget();
const documentTarget=Object.assign(new EventTarget(),{documentElement:{dataset:{}},visibilityState:'visible',title:''});
Object.defineProperty(globalThis,'window',{configurable:true,value:windowTarget});
Object.defineProperty(globalThis,'document',{configurable:true,value:documentTarget});
Object.defineProperty(globalThis,'EventSource',{configurable:true,value:PushSource});
const {createArenaSocket}=await import('../src/client/embeddedTransport');

test('embedded board receives pushes and heartbeats without polling',()=>{
  let requests=0;
  globalThis.fetch=async()=>{requests++;throw new Error('Unexpected poll');};
  const socket=createArenaSocket('unused'),source=PushSource.instances.at(-1)!;
  const received:unknown[]=[];let opened=0;
  socket.onopen=()=>opened++;
  socket.onmessage=e=>received.push(JSON.parse(e.data));
  source.onopen?.();
  source.onmessage?.({data:JSON.stringify([{type:'view',room:{revision:4}},{type:'heartbeat'}])});
  assert.equal(opened,1);assert.equal(socket.readyState,1);
  assert.equal(source.url,'/events?token=test-token');
  assert.deepEqual(received,[{type:'view',room:{revision:4}},{type:'heartbeat'}]);
  assert.equal(requests,0);
  socket.close();assert.equal(source.closed,true);
  source.onmessage?.({data:'[{"type":"heartbeat"}]'});
  assert.equal(received.length,2);
});

test('commands retain authenticated receipts and explicit resync',async()=>{
  const requests:{url:string;options:RequestInit|undefined}[]=[];
  globalThis.fetch=async(url,options)=>{
    requests.push({url:String(url),options});
    return new Response(JSON.stringify(url==='/command'?{type:'receipt',receipt:{status:'accepted'}}:[{type:'view',room:{revision:5}}]),{status:200});
  };
  const socket=createArenaSocket('unused'),received:unknown[]=[];
  socket.onmessage=e=>received.push(JSON.parse(e.data));
  socket.send(JSON.stringify({type:'command',command:{commandId:'once'}}));
  socket.send(JSON.stringify({type:'resync'}));
  await new Promise(resolve=>setImmediate(resolve));
  assert.equal(requests.length,2);
  assert.equal(requests[0].options?.method,'POST');
  assert.equal((requests[0].options?.headers as Record<string,string>)['X-Forge-Token'],'test-token');
  assert.equal(received.length,2);
  socket.close();
});

test('a broken push connection closes once for the existing reconnect path',()=>{
  const socket=createArenaSocket('unused'),source=PushSource.instances.at(-1)!;
  let errors=0,closed=0,cleaned=0;
  socket.onerror=()=>errors++;socket.onclose=()=>closed++;
  socket.addEventListener('close',()=>cleaned++);
  source.onerror?.();source.onerror?.();socket.close();
  assert.equal(errors,1);assert.equal(closed,1);assert.equal(cleaned,1);
  assert.equal(source.closed,true);assert.equal(socket.readyState,3);
});

test('focus and visibility restoration fetch one fresh state and detach on close',async()=>{
  let requests=0,release:((response:Response)=>void)|undefined;
  globalThis.fetch=async()=>{requests++;return await new Promise<Response>(resolve=>{release=resolve;});};
  const socket=createArenaSocket('unused'),received:unknown[]=[];
  socket.onmessage=e=>received.push(JSON.parse(e.data));
  windowTarget.dispatchEvent(new Event('focus'));
  documentTarget.dispatchEvent(new Event('visibilitychange'));
  socket.send(JSON.stringify({type:'resync'}));
  assert.equal(requests,1,'concurrent restore events must share one state request');
  release!(new Response('[{"type":"view","room":{"revision":21}}]'));
  await new Promise(resolve=>setImmediate(resolve));
  assert.deepEqual(received,[{type:'view',room:{revision:21}}]);
  documentTarget.visibilityState='hidden';
  documentTarget.dispatchEvent(new Event('visibilitychange'));
  assert.equal(requests,1);
  socket.close();documentTarget.visibilityState='visible';
  windowTarget.dispatchEvent(new Event('focus'));
  assert.equal(requests,1);
});

test('closing a socket cancels outstanding requests and ignores late responses',async()=>{
  let signal:AbortSignal|undefined,release:((response:Response)=>void)|undefined;
  globalThis.fetch=async(_url,options)=>{signal=options?.signal??undefined;return await new Promise<Response>(resolve=>{release=resolve;});};
  const socket=createArenaSocket('unused');let delivered=0;
  socket.onmessage=()=>delivered++;
  socket.send(JSON.stringify({type:'resync'}));
  socket.close();assert.equal(signal?.aborted,true);
  release!(new Response('[{"type":"view","room":{"revision":99}}]'));
  await new Promise(resolve=>setImmediate(resolve));
  assert.equal(delivered,0);
});
