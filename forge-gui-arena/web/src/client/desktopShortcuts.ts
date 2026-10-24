import type {DesktopShortcut} from '../shared/arenaProtocol';

type KeyInput = {key:string;code:string;ctrlKey:boolean;shiftKey:boolean;altKey:boolean;metaKey:boolean;repeat:boolean;isComposing:boolean};
type ShortcutContext = {bindings:DesktopShortcut[];editing:boolean;overlay:boolean;dismissible?:boolean;ready:boolean;nativeDialog:boolean};
export type DesktopKeyIntent = {kind:'dismiss'}|{kind:'stack'}|{kind:'shortcut';code:string;ctrl:boolean;shift:boolean;alt:boolean};

const keyCodes:Record<string,string>={
  Escape:'ESCAPE',Space:'SPACE',Enter:'ENTER',NumpadEnter:'ENTER',Tab:'TAB',Backspace:'BACK_SPACE',Delete:'DELETE',Insert:'INSERT',
  ArrowUp:'UP',ArrowDown:'DOWN',ArrowLeft:'LEFT',ArrowRight:'RIGHT',Home:'HOME',End:'END',PageUp:'PAGE_UP',PageDown:'PAGE_DOWN',
  ControlLeft:'CONTROL',ControlRight:'CONTROL',ShiftLeft:'SHIFT',ShiftRight:'SHIFT',AltLeft:'ALT',AltRight:'ALT',MetaLeft:'META',MetaRight:'META',
  Minus:'MINUS',Equal:'EQUALS',BracketLeft:'OPEN_BRACKET',BracketRight:'CLOSE_BRACKET',Backslash:'BACK_SLASH',Semicolon:'SEMICOLON',
  Quote:'QUOTE',Comma:'COMMA',Period:'PERIOD',Slash:'SLASH',Backquote:'BACK_QUOTE',NumpadAdd:'ADD',NumpadSubtract:'SUBTRACT',
  NumpadMultiply:'MULTIPLY',NumpadDivide:'DIVIDE',NumpadDecimal:'DECIMAL',CapsLock:'CAPS',NumLock:'NUM_LOCK',ScrollLock:'SCROLL_LOCK',
  Pause:'PAUSE',PrintScreen:'PRINTSCREEN',ContextMenu:'CONTEXT_MENU'
};
const modifierKeys=new Set(['CONTROL','SHIFT','ALT','META']);

export function javafxKeyCode(code:string):string|null {
  if(/^Key[A-Z]$/.test(code))return code.slice(3);
  if(/^Digit[0-9]$/.test(code))return code.toUpperCase();
  if(/^Numpad[0-9]$/.test(code))return code.toUpperCase();
  if(/^F(?:[1-9]|1[0-9]|2[0-4])$/.test(code))return code;
  return keyCodes[code]??null;
}

export function desktopKeyboardIntent(event:KeyInput,context:ShortcutContext):DesktopKeyIntent|null {
  if(event.isComposing||event.key==='Process'||event.key==='Dead'||context.editing||context.nativeDialog)return null;
  const code=javafxKeyCode(event.code);
  if(!code)return null;
  if(code==='ESCAPE'){
    if(event.repeat)return null;
    if(context.overlay||context.dismissible)return {kind:'dismiss'};
    return context.ready?{kind:'shortcut',code,ctrl:event.ctrlKey||event.metaKey,shift:event.shiftKey,alt:event.altKey}:null;
  }
  if(!context.ready||context.overlay)return null;
  const ctrl=event.ctrlKey||event.metaKey||code==='CONTROL'||code==='META';
  const shift=event.shiftKey||code==='SHIFT',alt=event.altKey||code==='ALT';
  const binding=context.bindings.find(b=>b.key===(modifierKeys.has(code)?null:code)&&b.ctrl===ctrl&&b.shift===shift&&b.alt===alt);
  if(!binding||(event.repeat&&!binding.repeats))return null;
  return binding.action==='EXPAND_STACK'?{kind:'stack'}:{kind:'shortcut',code,ctrl,shift,alt};
}

export function needsRevisionResync(current:{matchId:string;revision:number}|null,message:{matchId?:string;revision?:number}):boolean {
  if(typeof message.revision!=='number')return false;
  return !current||(message.matchId!==undefined&&message.matchId!==current.matchId)||message.revision>current.revision;
}
