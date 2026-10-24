const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const os = require('node:os');
const http = require('node:http');
const {createHash} = require('node:crypto');
const {spawn} = require('node:child_process');

const args = process.argv.slice(2);
function option(name, fallback) {
  const index = args.indexOf('--' + name);
  if (index < 0) return fallback;
  if (!args[index + 1] || args[index + 1].startsWith('--')) throw Error('Missing --' + name + ' value');
  return args[index + 1];
}
const dist = path.resolve(option('dist', path.join(__dirname, '../dist')));
const browserPath = option('browser', process.env.CHROME_PATH || (process.platform === 'win32'
  ? 'C:/Program Files/Google/Chrome/Application/chrome.exe'
  : process.platform === 'darwin' ? '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome' : '/usr/bin/google-chrome'));
const output = path.resolve(option('output', fs.mkdtempSync(path.join(os.tmpdir(), 'forge-renderer-'))));
fs.mkdirSync(output, {recursive: true});
assert.ok(fs.existsSync(path.join(dist, 'index.html')), 'Build the Vite client before running this check');
const build = Object.fromEntries(['index.html', ...fs.readdirSync(path.join(dist, 'assets')).filter(name => /\.(js|css)$/.test(name)).map(name => 'assets/' + name)]
  .map(name => [name, createHash('sha256').update(fs.readFileSync(path.join(dist, name))).digest('hex')]));
const sleep = ms => new Promise(resolve => setTimeout(resolve, ms));
const cardArt = Buffer.from('<svg xmlns="http://www.w3.org/2000/svg" width="208" height="292"><rect width="208" height="292" rx="12" fill="#233a54"/><rect x="8" y="8" width="192" height="276" rx="8" fill="none" stroke="#ddb66a" stroke-width="5"/><path d="M25 180L100 35L180 180Z" fill="#60bde5"/><text x="104" y="235" text-anchor="middle" fill="white" font-size="22">Fixture card</text></svg>');
const clients = new Set(), observations = [], errors = [];
let revision = 1, sequence = 0, history = [], stage = 'startup', commandCount = 0;
const card = (id, owner = 'p1') => ({id: 'c' + id, name: 'Fixture card ' + id, ownerId: owner, controllerId: owner,
  kind: id % 3 ? 'creature' : 'land', tapped: false, hidden: false, actionable: true, power: 2, toughness: 2, counters: {}});
const player = (id, enemy) => ({id, name: enemy ? 'Fixture opponent' : 'Fixture player', life: 20, libraryCount: 53,
  handCount: 7, hand: enemy ? [] : Array.from({length: 7}, (_, i) => card(i + 1)),
  battlefield: Array.from({length: 30}, (_, i) => card(i + (enemy ? 60 : 20), id)), graveyard: [], exile: []});
const snapshot = {players: [player('p1', false), player('p2', true)], stack: [], combat: [], phase: 'MAIN1',
  activePlayerId: 'p1', gameOver: false, lastEventSequence: 0};
let room = {matchId: 'renderer-fixture', code: 'TEST', playerId: 'p1', seat: 0, revision, status: 'playing', snapshot,
  prompt: {seat: 0, requestId: '1', kind: 'input', message: 'Renderer regression fixture', okEnabled: true,
    cancelEnabled: true, okLabel: 'OK', cancelLabel: 'Cancel'},
  fullControl: false, nativeDialog: false, shortcuts: [], imageVersions: {}, recentStackEvents: []};
const messages = () => [{type: 'view', room}, ...history.map(event => ({type: 'event', matchId: room.matchId, event}))];
function broadcast() {
  const data = 'data: ' + JSON.stringify(messages()) + '\n\n';
  for (const client of clients) client.write(data);
}
function update(changes = {}) { room = {...room, ...changes, revision: ++revision}; broadcast(); }
const server = http.createServer((request, response) => {
  const url = new URL(request.url, 'http://127.0.0.1');
  if (url.pathname === '/events') {
    response.writeHead(200, {'Content-Type': 'text/event-stream', 'Cache-Control': 'no-store'});
    clients.add(response); response.write('data: ' + JSON.stringify(messages()) + '\n\n');
    request.on('close', () => clients.delete(response)); return;
  }
  if (url.pathname === '/state') {
    response.writeHead(200, {'Content-Type': 'application/json'}); response.end(JSON.stringify(messages())); return;
  }
  if (url.pathname === '/image') {
    response.writeHead(200, {'Content-Type': 'image/svg+xml', 'Cache-Control': 'private, max-age=3600'}); response.end(cardArt); return;
  }
  if (url.pathname === '/command') {
    let body = ''; request.on('data', data => { body += data; });
    request.on('end', () => {
      const command = JSON.parse(body).command; commandCount++;
      response.writeHead(200, {'Content-Type': 'application/json'});
      response.end(JSON.stringify({type: 'receipt', receipt: {commandId: command.commandId, status: 'accepted', revision: room.revision}}));
    }); return;
  }
  const file = path.resolve(dist, '.' + (url.pathname === '/' ? '/index.html' : url.pathname));
  if (!file.startsWith(dist + path.sep) || !fs.existsSync(file) || !fs.statSync(file).isFile()) {
    response.writeHead(404); response.end(); return;
  }
  const types = {'.js': 'text/javascript', '.css': 'text/css', '.png': 'image/png', '.svg': 'image/svg+xml', '.jpg': 'image/jpeg'};
  response.writeHead(200, {'Content-Type': types[path.extname(file)] || 'text/html'}); fs.createReadStream(file).pipe(response);
});

const instrumentation = `
window.__renderer = {animations:0, sequences:{}, glCreated:0, glLost:0, maxLayers:0, maxCues:0};
window.__failure = '';
document.addEventListener('animationstart', event => {
  if (!event.target.matches?.('.event-cue')) return;
  __renderer.animations++;
  const id = event.target.getAttribute('data-event-sequence');
  if (id) __renderer.sequences[id] = (__renderer.sequences[id] || 0) + 1;
}, true);
const originalGetContext = HTMLCanvasElement.prototype.getContext;
HTMLCanvasElement.prototype.getContext = function(type, ...options) {
  if (type === 'webgl' && __failure === 'context') throw Error('Fixture WebGL allocation failure');
  const context = originalGetContext.call(this, type, ...options);
  if (type === 'webgl' && context && !this.__counted) {
    this.__counted = true; __renderer.glCreated++;
    this.addEventListener('webglcontextlost', () => __renderer.glLost++);
    const drawArrays = context.drawArrays.bind(context);
    context.drawArrays = (...args) => { if (__failure === 'raf') throw Error('Fixture WebGL draw failure'); return drawArrays(...args); };
  }
  return context;
};
const originalEscape = CSS.escape;
CSS.escape = value => { if (__failure === 'effect') throw Error('Fixture animation effect failure'); return originalEscape(value); };
document.addEventListener('DOMContentLoaded', () => new MutationObserver(() => {
  __renderer.maxLayers = Math.max(__renderer.maxLayers, document.querySelectorAll('.event-motion').length);
  __renderer.maxCues = Math.max(__renderer.maxCues, document.querySelectorAll('.event-cue').length);
}).observe(document.body, {subtree:true, childList:true}));
`;

async function run() {
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  const origin = 'http://127.0.0.1:' + server.address().port;
  // The CDP endpoint always comes from this new private profile, never an existing browser.
  const profile = fs.mkdtempSync(path.join(output, 'profile-'));
  const browser = spawn(browserPath, ['--headless=new', '--remote-debugging-port=0', '--user-data-dir=' + profile,
    '--no-first-run', '--no-default-browser-check', '--disable-background-timer-throttling', '--disable-renderer-backgrounding',
    '--disable-backgrounding-occluded-windows', '--enable-precise-memory-info', '--window-size=1600,1000', 'about:blank'],
    {stdio: 'ignore', windowsHide: true});
  let launchError, ws, call;
  browser.on('error', error => { launchError = error; });
  try {
    const portFile = path.join(profile, 'DevToolsActivePort');
    for (let i = 0; i < 150 && !fs.existsSync(portFile); i++) { if (launchError) throw launchError; await sleep(100); }
    assert.ok(fs.existsSync(portFile), 'The isolated Chrome instance did not expose CDP');
    const port = fs.readFileSync(portFile, 'utf8').split(/\r?\n/)[0];
    const targets = await (await fetch('http://127.0.0.1:' + port + '/json/list')).json();
    ws = new WebSocket(targets.find(target => target.type === 'page').webSocketDebuggerUrl);
    await new Promise((resolve, reject) => { ws.onopen = resolve; ws.onerror = reject; });
    let id = 0; const pending = new Map();
    ws.onmessage = event => {
      const value = JSON.parse(event.data);
      if (value.id) {
        const request = pending.get(value.id); if (!request) return;
        pending.delete(value.id); clearTimeout(request.timer);
        value.error ? request.reject(Error(value.error.message)) : request.resolve(value.result);
      } else if (value.method === 'Runtime.exceptionThrown' || value.method === 'Inspector.targetCrashed') {
        errors.push({stage, method: value.method, text: value.params?.exceptionDetails?.exception?.description || value.params?.exceptionDetails?.text});
      } else if (value.method === 'Runtime.consoleAPICalled' && ['error', 'warning'].includes(value.params.type)) {
        errors.push({stage, method: value.method, text: value.params.args.map(arg => arg.value || arg.description).join(' ')});
      }
    };
    call = (method, params = {}) => new Promise((resolve, reject) => {
      const requestId = ++id, timer = setTimeout(() => { pending.delete(requestId); reject(Error('CDP timeout: ' + method)); }, 10000);
      pending.set(requestId, {resolve, reject, timer}); ws.send(JSON.stringify({id: requestId, method, params}));
    });
    const evaluate = async expression => {
      const result = await call('Runtime.evaluate', {expression, returnByValue: true, awaitPromise: true});
      if (result.exceptionDetails) throw Error(result.exceptionDetails.exception?.description || result.exceptionDetails.text);
      return result.result.value;
    };
    const waitFor = async expression => {
      for (let i = 0; i < 100; i++) { if (await evaluate(expression)) return; await sleep(50); }
      throw Error('Page condition did not complete: ' + expression);
    };
    async function observe(label, {layers = 1, gl = 1} = {}) {
      stage = label;
      await waitFor(`document.querySelector('[data-revision]')?.getAttribute('data-revision') === '${revision}'`);
      const dom = await evaluate(`({...__renderer, revision:document.querySelector('[data-revision]')?.getAttribute('data-revision'),
        cards:document.querySelectorAll('.forge-card').length,layers:document.querySelectorAll('.event-motion').length,
        cues:document.querySelectorAll('.event-cue').length,canvases:document.querySelectorAll('canvas').length,
        rootChildren:document.querySelector('#root').childElementCount,heap:performance.memory?.usedJSHeapSize})`);
      const metrics = await call('Performance.getMetrics');
      const value = {label, ...dom, commandCount, metrics: Object.fromEntries(metrics.metrics
        .filter(metric => ['Nodes', 'JSHeapUsedSize', 'LayoutCount', 'RecalcStyleCount'].includes(metric.name))
        .map(metric => [metric.name, metric.value]))};
      observations.push(value); fs.writeFileSync(path.join(output, 'results.json'), JSON.stringify({build, observations, errors}, null, 2));
      assert.equal(dom.cards, 67, label + ': the card board must remain mounted');
      assert.equal(dom.layers, layers, label + ': one flight layer per match');
      assert.equal(dom.cues, 0, label + ': expired flights must leave the DOM');
      assert.equal(dom.glCreated, gl, label + ': no unexpected WebGL reallocation');
      assert.equal(dom.canvases, 1, label + ': one particle canvas per match');
      assert.ok(dom.maxLayers <= 1, label + ': old flight layers leaked');
      assert.ok(dom.maxCues <= 24, label + ': flight DOM exceeded the component bound');
      console.log(JSON.stringify({label, cards: dom.cards, layers: dom.layers, cues: dom.cues, gl: dom.glCreated, animations: dom.animations, commandCount}));
      return value;
    }
    async function commandStillWorks() {
      const previous = commandCount;
      const clicked = await evaluate(`(() => {const button=[...document.querySelectorAll('.forge-input button')].find(node=>node.textContent==='确认'); if(!button||button.disabled)return false;button.click();return true;})()`);
      assert.ok(clicked, 'The board confirmation must remain usable');
      for (let i = 0; i < 100 && commandCount === previous; i++) await sleep(50);
      assert.equal(commandCount, previous + 1, 'The board command must reach the fixture bridge');
      await waitFor(`[...document.querySelectorAll('.forge-input button')].some(node=>node.textContent==='确认'&&!node.disabled)`);
    }
    await call('Runtime.enable'); await call('Page.enable'); await call('Performance.enable');
    await call('Page.addScriptToEvaluateOnNewDocument', {source: instrumentation});
    await call('Page.navigate', {url: origin + '/?embedded#renderer-fixture'});
    await waitFor(`document.querySelectorAll('.forge-card').length===67`); await sleep(500); await observe('loaded');
    stage = 'draw-events';
    for (let i = 0; i < 40; i++) {
      snapshot.lastEventSequence = ++sequence;
      history.push({sequence, kind: 'zone', data: {playerId: 'p1', cardId: 'c1', name: 'Fixture card 1', from: 'Library', to: 'Hand'}});
      history = history.slice(-48); update(); await sleep(60);
    }
    await sleep(900); const drawn = await observe('after-40-draw-events');
    assert.equal(drawn.animations, 40, 'Each draw must animate once');
    stage = 'duplicate-sse';
    for (let batch = 0; batch < 100; batch++) { for (let i = 0; i < 100; i++) broadcast(); await sleep(10); }
    await sleep(900); assert.equal((await observe('after-10000-duplicate-sse')).animations, 40);
    stage = 'image-versions';
    for (let i = 0; i < 100; i++) { update({imageVersions: {'Fixture card 1': i + 1}}); await sleep(25); }
    await sleep(900); assert.equal((await observe('after-100-image-updates')).animations, 40);
    stage = 'curtain';
    for (let i = 0; i < 24; i++) {
      update({snapshot: null, status: 'starting', nativeDialog: true}); await sleep(100);
      update({snapshot, status: 'playing', nativeDialog: false}); await sleep(100);
    }
    await sleep(900); assert.equal((await observe('after-24-curtains')).animations, 40);
    stage = 'native-dialog';
    for (let i = 0; i < 24; i++) { update({nativeDialog: true}); await sleep(60); update({nativeDialog: false}); await sleep(60); }
    await sleep(900); assert.equal((await observe('after-24-native-dialogs')).animations, 40);
    await commandStillWorks();
    assert.equal(errors.length, 0, 'Ordinary updates must not produce runtime errors or warnings');
    const screenshot = await call('Page.captureScreenshot', {format: 'png'});
    fs.writeFileSync(path.join(output, 'normal.png'), Buffer.from(screenshot.data, 'base64'));
    stage = 'injected-webgl-failure'; await evaluate(`__failure='context'`);
    update({matchId: 'renderer-fixture-context-failure'}); await sleep(1000);
    await observe('after-webgl-allocation-failure'); await commandStillWorks();
    stage = 'injected-effect-failure'; await evaluate(`__failure='effect'`);
    snapshot.lastEventSequence = ++sequence;
    history.push({sequence, kind: 'zone', data: {playerId: 'p1', cardId: 'c1', name: 'Fixture card 1', from: 'Library', to: 'Hand'}});
    update(); await sleep(1000); await observe('after-animation-effect-failure', {layers: 0}); await commandStillWorks();
    await evaluate(`__failure=''`); update({matchId: 'renderer-fixture-recovered'}); await sleep(1000);
    await observe('new-match-after-failures', {gl: 2}); await commandStillWorks();
    stage = 'injected-raf-failure'; await evaluate(`__failure='raf'`);
    snapshot.lastEventSequence = ++sequence;
    history.push({sequence, kind: 'zone', data: {playerId: 'p1', cardId: 'c1', name: 'Fixture card 1', from: 'Library', to: 'Hand'}});
    update(); await sleep(1000); await observe('after-webgl-draw-failure', {gl: 2});
    assert.equal(await evaluate(`document.querySelector('.forge-semantic-canvas').hidden`), true, 'A failed particle renderer must stay hidden');
    update({nativeDialog: true}); await sleep(100); update({nativeDialog: false}); await sleep(500);
    assert.equal(await evaluate(`document.querySelector('.forge-semantic-canvas').hidden`), true, 'Native dialog restoration must not expose a failed canvas');
    await observe('failed-canvas-after-native-dialog', {gl: 2}); await commandStillWorks();
    await evaluate(`__failure=''`); update({matchId: 'renderer-fixture-final-recovery'}); await sleep(1000);
    await observe('new-match-after-draw-failure', {gl: 3}); await commandStillWorks();
    assert.equal(await evaluate(`document.querySelector('.forge-semantic-canvas').hidden`), false, 'A new match must recover particles');
    const finalScreenshot = await call('Page.captureScreenshot', {format: 'png'});
    fs.writeFileSync(path.join(output, 'recovered.png'), Buffer.from(finalScreenshot.data, 'base64'));
    fs.writeFileSync(path.join(output, 'results.json'), JSON.stringify({passed: true, build, observations, errors, commandCount}, null, 2));
    console.log('RENDERER_CHECK_OK output=' + output);
  } finally {
    if (call && ws?.readyState === WebSocket.OPEN) await call('Browser.close').catch(() => {});
    ws?.close(); for (const client of clients) client.end(); server.close();
    if (browser.exitCode === null) browser.kill();
  }
}
run().catch(error => {
  fs.writeFileSync(path.join(output, 'failure.json'), JSON.stringify({error: error.stack, build, observations, errors}, null, 2));
  console.error(error.stack); for (const client of clients) client.end(); server.close(); process.exitCode = 1;
});
