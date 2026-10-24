import test from 'node:test';
import assert from 'node:assert/strict';
import {EventCursor} from '../src/client/eventCursor';
import {AnimationTimeline} from '../src/client/animationTimeline';
import {createParticleRenderer} from '../src/client/particleRenderer';
import type {ArenaEvent} from '../src/shared/arenaProtocol';

const draw=(sequence:number):ArenaEvent=>({sequence,kind:'zone',data:{cardId:`card-${sequence}`,playerId:'self',from:'Library',to:'Hand'}});

test('repeated snapshots and image updates cannot restart an already played draw',()=>{
  const cursor=new EventCursor(),timeline=new AnimationTimeline(),event=draw(1);
  assert.deepEqual(cursor.take([event]),[event]);timeline.push([event],0);
  for(let now=1;now<=4000;now++){
    assert.deepEqual(cursor.take([structuredClone(event)]),[]);
    timeline.push([structuredClone(event)],now);
  }
  assert.deepEqual(timeline.frame(4000),[]);
  assert.deepEqual(cursor.take([event,draw(2)]),[draw(2)]);
  timeline.push([event,draw(2)],4001);assert.equal(timeline.frame(4001).length,1);
});

test('suspending for a native dialog consumes events without replay on restoration',()=>{
  const timeline=new AnimationTimeline();
  timeline.push([draw(1)],0);assert.equal(timeline.frame(1).length,1);
  timeline.setReduced(true);timeline.push([draw(1),draw(2)],2);
  assert.deepEqual(timeline.frame(3),[]);
  timeline.setReduced(false);timeline.push([draw(1),draw(2)],4);
  assert.deepEqual(timeline.frame(5),[]);
  timeline.push([draw(1),draw(2),draw(3)],6);
  assert.deepEqual(timeline.frame(7).map(c=>c.event.sequence),[3]);
});

test('reconnect advances the existing cursor and never resurrects discarded history',()=>{
  const cursor=new EventCursor(10),timeline=new AnimationTimeline(10);
  timeline.push([draw(11)],0);cursor.take([draw(11)]);
  cursor.advance(30);timeline.advance(30);
  assert.deepEqual(cursor.take([draw(11),draw(30)]),[]);
  timeline.push([draw(11),draw(30)],100);assert.deepEqual(timeline.frame(100),[]);
  cursor.advance(5);timeline.advance(5);
  assert.deepEqual(cursor.take([draw(30),draw(31)]),[draw(31)]);
  timeline.push([draw(30),draw(31)],101);assert.deepEqual(timeline.frame(102).map(c=>c.event.sequence),[31]);
});

test('animation queues stay bounded during a burst and drain when input stops',()=>{
  const timeline=new AnimationTimeline();
  for(let n=1;n<=10000;n++)timeline.push([draw(n)],0);
  assert.equal(timeline.frame(1).length,48);
  assert.deepEqual(timeline.frame(1101),[]);
  timeline.push(Array.from({length:10000},(_,i)=>draw(i+1)),1102);
  assert.deepEqual(timeline.frame(1103),[]);
});

test('invalid sequence numbers are ignored while distinct engine moves remain playable',()=>{
  const cursor=new EventCursor(),timeline=new AnimationTimeline();
  const events=[draw(NaN),draw(Infinity),draw(-1),draw(1.5),{...draw(1),data:{from:'Hand',to:'Hand'}},draw(2)];
  assert.deepEqual(cursor.take(events).map(e=>e.sequence),[1,2]);
  timeline.push(events,0);assert.deepEqual(timeline.frame(1).map(c=>c.event.sequence),[1,2]);
});

function fakeCanvas(failure:string=''){
  let created=0;
  const deleted:string[]=[];
  const gl={
    VERTEX_SHADER:1,FRAGMENT_SHADER:2,COMPILE_STATUS:3,LINK_STATUS:4,
    createShader:()=>failure==='shader'&&created++===1?null:{kind:'shader'},
    shaderSource:()=>{},compileShader:()=>{},getShaderParameter:()=>failure!=='compile',
    createProgram:()=>failure==='program'?null:{kind:'program'},
    attachShader:()=>{},linkProgram:()=>{},getProgramParameter:()=>failure!=='link',
    createBuffer:()=>failure==='buffer'?null:{kind:'buffer'},
    useProgram:()=>{},bindBuffer:()=>{},getAttribLocation:()=>0,enableVertexAttribArray:()=>{},vertexAttribPointer:()=>{},enable:()=>{},blendFunc:()=>{},getUniformLocation:()=>({}),
    deleteShader:()=>deleted.push('shader'),deleteProgram:()=>deleted.push('program'),deleteBuffer:()=>deleted.push('buffer')
  };
  const canvas={getContext:()=>{if(failure==='context')throw new Error('Context creation failed');return gl;}} as unknown as HTMLCanvasElement;
  return {canvas,deleted};
}

test('WebGL setup failures release every allocated resource without escaping to the board',()=>{
  const warn=console.warn;console.warn=()=>{};
  try{
    for(const [failure,expected] of [
      ['context',[]],['shader',['shader']],['compile',['shader']],
      ['program',['shader','shader']],['link',['program','shader','shader']],['buffer',['program','shader','shader']]
    ] as [string,string[]][]){
      const {canvas,deleted}=fakeCanvas(failure);
      assert.equal(createParticleRenderer(canvas),null,failure);
      assert.deepEqual(deleted,expected,failure);
    }
  }finally{console.warn=warn;}
});

test('particle renderer cleanup deletes its resources exactly once',()=>{
  const {canvas,deleted}=fakeCanvas();
  const renderer=createParticleRenderer(canvas);assert.ok(renderer);
  renderer.dispose();renderer.dispose();
  assert.deepEqual(deleted,['buffer','program','shader','shader']);
});
