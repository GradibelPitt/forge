import {useEffect,useRef,useState} from 'react';
import type {ArenaEvent} from '../shared/arenaProtocol';
import {AnimationTimeline} from './animationTimeline';
import {createParticleRenderer} from './particleRenderer';

export function SemanticCanvas({events,ownPlayerId,baseline=0,active=true}:{events:ArenaEvent[];ownPlayerId:string;baseline?:number;active?:boolean}){
  const canvas=useRef<HTMLCanvasElement>(null),labels=useRef<HTMLDivElement>(null),timeline=useRef(new AnimationTimeline(baseline)),wake=useRef(()=>{});
  const viewer=useRef(ownPlayerId);viewer.current=ownPlayerId;
  const [rendererEpoch,setRendererEpoch]=useState(0);
  const failed=useRef(false),[rendererFailed,setRendererFailed]=useState(false);
  useEffect(()=>{
    timeline.current.advance(baseline);
    timeline.current.setReduced(!active||matchMedia('(prefers-reduced-motion: reduce)').matches||document.hidden);
    timeline.current.push(events,performance.now());wake.current();
  },[events,baseline,active]);
  useEffect(()=>{
    const media=matchMedia('(prefers-reduced-motion: reduce)');const update=()=>{timeline.current.setReduced(!active||media.matches||document.hidden);wake.current();};
    update();media.addEventListener('change',update);document.addEventListener('visibilitychange',update);
    return()=>{media.removeEventListener('change',update);document.removeEventListener('visibilitychange',update);};
  },[active]);
  useEffect(()=>{
    const node=canvas.current,overlay=labels.current;if(!node||!overlay)return;
    const renderer=createParticleRenderer(node);if(!renderer){failed.current=true;setRendererFailed(true);return;}
    const {gl,size,color}=renderer,textNodes=new Map<number,HTMLSpanElement>();let frame=0,stopped=false;
    const anchor=(cardId?:string,playerId?:string):[number,number]=>{
      const root=node.parentElement;
      const card=cardId?root?.querySelector(`[data-card-id="${CSS.escape(cardId)}"]`):null;
      const player=playerId?root?.querySelector(`[data-player-id="${CSS.escape(playerId)}"]`):null;
      const rect=(card??player)?.getBoundingClientRect(),bounds=node.getBoundingClientRect();
      return rect?[(rect.x+rect.width/2-bounds.x)/bounds.width,(rect.y+rect.height/2-bounds.y)/bounds.height]:[.5,playerId===viewer.current?.74:.16];
    };
    const draw=(time:number)=>{
      frame=0;if(stopped||!node.isConnected)return;
      try{
      const ratio=Math.min(devicePixelRatio,2),w=Math.floor(node.clientWidth*ratio),h=Math.floor(node.clientHeight*ratio);
      if(node.width!==w||node.height!==h){node.width=w;node.height=h;gl.viewport(0,0,w,h);}
      gl.clearColor(0,0,0,0);gl.clear(gl.COLOR_BUFFER_BIT);
      const cues=timeline.current.frame(time),alive=new Set(cues.map(cue=>cue.event.sequence));
      for(const [id,element] of textNodes)if(!alive.has(id)){element.remove();textNodes.delete(id);}
      for(const {event,progress:t} of cues){
        const data=event.data;let [x,y]=anchor(data.cardId,data.playerId);
        if(event.kind==='cast'&&(data.targetCardIds?.length||data.targetPlayerIds?.length)){
          const [tx,ty]=anchor(data.targetCardIds?.[0],data.targetPlayerIds?.[0]);x+=(tx-x)*t;y+=(ty-y)*t-Math.sin(t*Math.PI)*.15;
        }
        const delta=(data.after??0)-(data.before??0),gain=event.kind==='life'&&delta>0;
        const rgb=event.kind==='damage'||(event.kind==='life'&&!gain)?[1,.24,.14]:gain?[.4,1,.54]:event.kind==='tap'?[.35,.76,1]:[1,.75,.28];
        const points=[];for(let i=0;i<60;i++){const angle=i*2.39996+event.sequence*.17,r=Math.pow(t,.65)*(14+(i%7)*8);points.push(x*2-1+Math.cos(angle)*r*2/node.clientWidth,1-y*2+Math.sin(angle)*r*2/node.clientHeight);}
        gl.bufferData(gl.ARRAY_BUFFER,new Float32Array(points),gl.STREAM_DRAW);gl.uniform1f(size,(8+(1-t)*20)*ratio);gl.uniform4f(color,rgb[0],rgb[1],rgb[2],(1-t)*.85);gl.drawArrays(gl.POINTS,0,60);gl.uniform1f(size,4*ratio);gl.uniform4f(color,1,.94,.78,(1-t)*.8);gl.drawArrays(gl.POINTS,0,60);
        const text=event.kind==='life'?`${delta>0?'+':''}${delta}`:event.kind==='damage'?`−${data.amount}`:event.kind==='phase'?(data.playerId===viewer.current?'你的回合':'对手回合'):'';
        if(text){let element=textNodes.get(event.sequence);if(!element){element=document.createElement('span');element.textContent=text;element.className=event.kind==='phase'?'forge-turn-banner':'forge-damage-number';overlay.appendChild(element);textNodes.set(event.sequence,element);}
          element.style.left=`${event.kind==='phase'?50:x*100}%`;element.style.top=`${event.kind==='phase'?43:y*100}%`;element.style.opacity=String(Math.min(1,(1-t)*3));element.style.color=`rgb(${rgb.map(n=>Math.round(n*255)).join(',')})`;element.style.transform=`translate(-50%,${-50-t*70}%)`;
        }
      }
      if(cues.length)frame=requestAnimationFrame(draw);
      }catch(error){
        stopped=true;failed.current=true;setRendererFailed(true);timeline.current.clear();textNodes.forEach(element=>element.remove());textNodes.clear();renderer.dispose();node.hidden=true;
        console.warn('Arena particles stopped',error);
      }
    };
    wake.current=()=>{if(!stopped&&!frame)frame=requestAnimationFrame(draw);};wake.current();
    const lost=(event:Event)=>{event.preventDefault();stopped=true;timeline.current.clear();cancelAnimationFrame(frame);frame=0;textNodes.forEach(element=>element.remove());textNodes.clear();};
    const restored=()=>{if(!failed.current)setRendererEpoch(value=>value+1);};
    const hidden=()=>{if(document.hidden)timeline.current.clear();wake.current();};
    node.addEventListener('webglcontextlost',lost);node.addEventListener('webglcontextrestored',restored);window.addEventListener('resize',wake.current);document.addEventListener('visibilitychange',hidden);
    return()=>{stopped=true;cancelAnimationFrame(frame);node.removeEventListener('webglcontextlost',lost);node.removeEventListener('webglcontextrestored',restored);window.removeEventListener('resize',wake.current);document.removeEventListener('visibilitychange',hidden);wake.current=()=>{};textNodes.forEach(element=>element.remove());renderer.dispose();};
  },[rendererEpoch]);
  return <><canvas ref={canvas} className="forge-semantic-canvas" aria-hidden="true" hidden={!active||rendererFailed}/><div ref={labels} className="forge-semantic-labels" aria-hidden="true" hidden={!active||rendererFailed}/></>;
}
