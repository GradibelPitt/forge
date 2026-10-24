import {Component,type ErrorInfo,type ReactNode} from 'react';
import type {ArenaEvent} from '../shared/arenaProtocol';
import {EventMotion} from './EventMotion';
import {SemanticCanvas} from './SemanticCanvas';

class EffectBoundary extends Component<{children:ReactNode},{failed:boolean}> {
  state={failed:false};
  static getDerivedStateFromError(){return {failed:true};}
  componentDidCatch(error:Error,info:ErrorInfo){console.error('Arena animation disabled',error,info.componentStack);}
  render(){return this.state.failed?null:this.props.children;}
}

export function MatchEffects(props:{events:ArenaEvent[];ownPlayerId:string;baseline:number;active:boolean}){
  return <><EffectBoundary key="flight"><EventMotion {...props}/></EffectBoundary><EffectBoundary key="particles"><SemanticCanvas {...props}/></EffectBoundary></>;
}
