import type {ArenaEvent} from '../shared/arenaProtocol';

export class EventCursor {
  private sequence=0;
  constructor(sequence=0){this.advance(sequence);}
  advance(sequence:number){
    if(Number.isSafeInteger(sequence)&&sequence>this.sequence)this.sequence=sequence;
  }
  take(events:ArenaEvent[]):ArenaEvent[]{
    const fresh:ArenaEvent[]=[];
    for(const event of events){
      if(!Number.isSafeInteger(event.sequence)||event.sequence<=this.sequence)continue;
      this.sequence=event.sequence;
      fresh.push(event);
    }
    return fresh;
  }
}
