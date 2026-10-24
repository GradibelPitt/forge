import test from 'node:test';
import assert from 'node:assert/strict';
import {desktopKeyboardIntent,javafxKeyCode,needsRevisionResync} from '../src/client/desktopShortcuts';
import type {DesktopShortcut} from '../src/shared/arenaProtocol';

const key=(code:string,extra:Record<string,unknown>={})=>({code,key:code,ctrlKey:false,shiftKey:false,altKey:false,metaKey:false,repeat:false,isComposing:false,...extra});
const binding=(action:string,key:string|null,extra:Partial<DesktopShortcut>={}):DesktopShortcut=>({action,key,ctrl:false,shift:false,alt:false,repeats:false,...extra});
const context=(bindings:DesktopShortcut[],extra:Record<string,unknown>={})=>({bindings,editing:false,overlay:false,ready:true,nativeDialog:false,...extra});

test('published bindings control both number rows and custom modifier shortcuts',()=>{
  for(const n of [1,2,6,0])for(const prefix of ['Digit','Numpad']){
    const code=prefix+n,fx=prefix.toUpperCase()+n;
    assert.equal(javafxKeyCode(code),fx);
    assert.deepEqual(desktopKeyboardIntent(key(code),context([binding('PASS_PRIORITY',fx)])),{kind:'shortcut',code:fx,ctrl:false,shift:false,alt:false});
  }
  const ctrlE=key('KeyE',{ctrlKey:true});
  assert.deepEqual(desktopKeyboardIntent(ctrlE,context([binding('PASS_TURN','E',{ctrl:true})])),{kind:'shortcut',code:'E',ctrl:true,shift:false,alt:false});
  assert.equal(desktopKeyboardIntent(ctrlE,context([binding('PASS_TURN','E')])),null);
  assert.equal(desktopKeyboardIntent(key('Digit6'),context([])),null,'disabled native shortcuts must not be reintroduced by the browser');
});

test('modifier-only bindings and repeat permissions follow native preferences',()=>{
  assert.deepEqual(desktopKeyboardIntent(key('ShiftLeft',{ctrlKey:true,shiftKey:true}),context([binding('FULL_CONTROL',null,{ctrl:true,shift:true})])),{kind:'shortcut',code:'SHIFT',ctrl:true,shift:true,alt:false});
  assert.equal(desktopKeyboardIntent(key('KeyP',{repeat:true}),context([binding('FULL_CONTROL','P')])),null);
  assert.deepEqual(desktopKeyboardIntent(key('Space',{repeat:true}),context([binding('PASS_PRIORITY','SPACE',{repeats:true})])),{kind:'shortcut',code:'SPACE',ctrl:false,shift:false,alt:false});
  assert.equal(javafxKeyCode('Unidentified'),null);
});

test('typing, composition and native dialogs do not play a game action',()=>{
  const bindings=[binding('PASS_PRIORITY','SPACE'),binding('ZOOM_CARD','Z')];
  assert.equal(desktopKeyboardIntent(key('Space'),context(bindings,{editing:true})),null);
  assert.equal(desktopKeyboardIntent(key('KeyZ',{isComposing:true}),context(bindings)),null);
  assert.equal(desktopKeyboardIntent(key('KeyZ',{key:'Dead'}),context(bindings)),null);
  assert.equal(desktopKeyboardIntent(key('Space'),context(bindings))?.kind,'shortcut','published Space remains a match shortcut after a card receives focus');
  assert.equal(desktopKeyboardIntent(key('KeyZ'),context(bindings,{nativeDialog:true})),null);
  assert.equal(desktopKeyboardIntent(key('Escape'),context(bindings,{nativeDialog:true})),null);
});

test('Escape dismisses browser overlays before native menus; zoom and stack remain usable while previewing',()=>{
  assert.deepEqual(desktopKeyboardIntent(key('Escape'),context([],{overlay:true})),{kind:'dismiss'});
  assert.deepEqual(desktopKeyboardIntent(key('Escape'),context([],{dismissible:true})),{kind:'dismiss'});
  assert.deepEqual(desktopKeyboardIntent(key('Escape'),context([])),{kind:'shortcut',code:'ESCAPE',ctrl:false,shift:false,alt:false});
  assert.equal(desktopKeyboardIntent(key('Escape',{repeat:true}),context([])),null);
  assert.deepEqual(desktopKeyboardIntent(key('KeyS'),context([binding('EXPAND_STACK','S')],{dismissible:true})),{kind:'stack'});
  assert.equal(desktopKeyboardIntent(key('KeyZ'),context([binding('ZOOM_CARD','Z')],{dismissible:true}))?.kind,'shortcut');
  assert.equal(desktopKeyboardIntent(key('KeyZ'),context([binding('ZOOM_CARD','Z')],{overlay:true})),null);
});

test('a live receipt or heartbeat cannot conceal a missed state revision',()=>{
  const current={matchId:'game-a',revision:12};
  assert.equal(needsRevisionResync(current,{revision:13}),true);
  assert.equal(needsRevisionResync(current,{revision:12}),false);
  assert.equal(needsRevisionResync(current,{revision:11}),false);
  assert.equal(needsRevisionResync(current,{matchId:'game-b',revision:1}),true);
  assert.equal(needsRevisionResync(null,{revision:0}),true);
  assert.equal(needsRevisionResync(current,{}),false);
});
