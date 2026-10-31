"""Makes tools/audit-review.html, a page to review audit/README.md item by item.

   python3 tools/audit-review.py ITEMS.json [OUT.html]

ITEMS.json holds {"themes": [{prefix, name, scope}], "items": [{id, theme,
title, kind, value, effort, body, and optionally d (yes/no) and note}]}, as
the audit returned them; recorded decisions show unless the browser has its
own.  The page
needs no server: open it in a browser.  Decisions are kept in the browser,
and EXPORT gives them as text ("S1 yes", one per line) to paste back."""
import html
import json
import sys

src = sys.argv[1]
out = sys.argv[2] if len(sys.argv) > 2 else 'tools/audit-review.html'
data = json.load(open(src, encoding='utf-8'))
slim = {
    'themes': [{'prefix': t['prefix'], 'name': t['name'], 'scope': t.get('scope', '')} for t in data['themes']],
    'items': [{k: it[k] for k in ('id', 'theme', 'title', 'kind', 'value', 'effort', 'body', 'd', 'note') if k in it}
              for it in data['items']],
}
payload = json.dumps(slim, ensure_ascii=False).replace('</', '<\\/')

PAGE = r'''<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Builder Audit Review</title>
<style>
:root {
  --bg: #f7f6f2; --panel: #ffffff; --text: #1d1d1b; --muted: #66645e; --line: #dedbd2;
  --accent: #2f5d8a; --yes: #2e7d4f; --yes-bg: #e3f2e8; --no: #a33a2e; --no-bg: #f7e4e1;
  --chip: #eeece6; --code: #efede7;
}
@media (prefers-color-scheme: dark) {
  :root:not([data-theme="light"]) {
    --bg: #161615; --panel: #201f1d; --text: #e9e7e1; --muted: #a29f97; --line: #35332f;
    --accent: #7fa8d4; --yes: #7cc79a; --yes-bg: #1e3326; --no: #e48d80; --no-bg: #3a2220;
    --chip: #2c2b28; --code: #2a2926;
  }
}
:root[data-theme="dark"] {
  --bg: #161615; --panel: #201f1d; --text: #e9e7e1; --muted: #a29f97; --line: #35332f;
  --accent: #7fa8d4; --yes: #7cc79a; --yes-bg: #1e3326; --no: #e48d80; --no-bg: #3a2220;
  --chip: #2c2b28; --code: #2a2926;
}
* { box-sizing: border-box; }
body { margin: 0; background: var(--bg); color: var(--text);
  font: 15px/1.55 system-ui, -apple-system, "Segoe UI", sans-serif; }
header { position: sticky; top: 0; z-index: 2; background: var(--bg); border-bottom: 1px solid var(--line); }
.wrap { max-width: 920px; margin: 0 auto; padding: 0 16px; }
h1 { font-size: 20px; margin: 14px 0 4px; }
.tally { color: var(--muted); font-size: 14px; margin-bottom: 8px; }
.tally b { color: var(--text); }
.bar { display: flex; flex-wrap: wrap; gap: 8px; padding-bottom: 12px; }
select, input[type=search], button, textarea { font: inherit; color: var(--text); background: var(--panel);
  border: 1px solid var(--line); border-radius: 6px; }
select, input[type=search] { padding: 6px 8px; min-width: 0; }
input[type=search] { flex: 1 1 180px; }
button { padding: 6px 12px; cursor: pointer; }
button:hover { border-color: var(--accent); }
main { padding: 16px 0 60px; }
.theme { margin: 28px 0 8px; font-size: 17px; }
.scope { color: var(--muted); margin: 0 0 8px; font-size: 14px; }
.item { background: var(--panel); border: 1px solid var(--line); border-left: 4px solid var(--line);
  border-radius: 8px; padding: 12px 14px; margin: 10px 0; }
.item.yes { border-left-color: var(--yes); }
.item.no { border-left-color: var(--no); opacity: .72; }
.head { display: flex; gap: 10px; align-items: baseline; cursor: pointer; }
.id { font-weight: 700; color: var(--accent); min-width: 3.2em; }
.title { font-weight: 600; flex: 1; }
.meta { display: flex; flex-wrap: wrap; gap: 6px; margin: 6px 0 0 calc(3.2em + 10px); }
.chip { background: var(--chip); border-radius: 10px; padding: 1px 8px; font-size: 12px; color: var(--muted); }
.chip.high { color: var(--text); font-weight: 600; }
.body { margin: 8px 0 0 calc(3.2em + 10px); display: none; }
.item.open .body { display: block; }
.body p { margin: 0 0 8px; }
.body code { background: var(--code); padding: 0 4px; border-radius: 4px; font-size: 13px; }
.body ul { margin: 0 0 8px; padding-left: 20px; }
.decide { display: flex; gap: 6px; margin: 10px 0 0 calc(3.2em + 10px); flex-wrap: wrap; }
.decide button.on.y { background: var(--yes-bg); border-color: var(--yes); color: var(--yes); font-weight: 600; }
.decide button.on.n { background: var(--no-bg); border-color: var(--no); color: var(--no); font-weight: 600; }
.note { flex: 1 1 200px; padding: 6px 8px; font: inherit; color: var(--text); background: var(--panel);
  border: 1px solid var(--line); border-radius: 6px; }
dialog { background: var(--panel); color: var(--text); border: 1px solid var(--line); border-radius: 10px;
  width: min(640px, calc(100vw - 32px)); }
dialog textarea { width: 100%; height: 50vh; padding: 8px; font: 13px/1.4 ui-monospace, Consolas, monospace; }
.empty { color: var(--muted); padding: 30px 0; text-align: center; }
@media (max-width: 560px) {
  header { position: static; }
  .meta, .body, .decide { margin-left: 0; }
}
</style>
</head>
<body>
<header><div class="wrap">
  <h1>Builder capabilities audit</h1>
  <div class="tally" id="tally"></div>
  <div class="bar">
    <input type="search" id="q" placeholder="Search titles and text">
    <select id="fTheme"><option value="">All themes</option></select>
    <select id="fValue"><option value="">Any value</option><option>high</option><option>medium</option><option>low</option></select>
    <select id="fEffort"><option value="">Any effort</option><option>small</option><option>medium</option><option>large</option></select>
    <select id="fState"><option value="">All items</option><option value="u">Undecided</option><option value="yes">Yes</option><option value="no">No</option></select>
    <button id="openAll">Expand all</button>
    <button id="export">Export</button>
    <button id="import">Import</button>
  </div>
</div></header>
<main class="wrap" id="list"></main>
<dialog id="dlg">
  <p id="dlgText"></p>
  <textarea id="dlgArea" spellcheck="false"></textarea>
  <div class="bar" style="padding-top:10px">
    <button id="dlgCopy">Copy</button>
    <button id="dlgApply">Apply</button>
    <button id="dlgClose">Close</button>
  </div>
</dialog>
<script>
const DATA = __DATA__;
const KEY = 'coffeesud-audit-review';
let state = {};
try { state = JSON.parse(localStorage.getItem(KEY) || '{}') || {}; } catch (e) { state = {}; }
function save() { try { localStorage.setItem(KEY, JSON.stringify(state)); } catch (e) {} }
// decisions already recorded in the audit, unless this browser has its own
for (const it of DATA.items) if (!(it.id in state) && (it.d || it.note)) state[it.id] = { d: it.d || '', note: it.note || '' };

const themeName = {};
for (const t of DATA.themes) {
  themeName[t.prefix] = t;
  const o = document.createElement('option'); o.value = t.prefix; o.textContent = t.name;
  document.getElementById('fTheme').appendChild(o);
}
function esc(s) { return String(s).replace(/[&<>"]/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c])); }
function inline(s) {
  return esc(s).replace(/`([^`]+)`/g, '<code>$1</code>').replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>')
    .replace(/(^|[^*])\*([^*\s][^*]*)\*/g, '$1<em>$2</em>');
}
function render(md) {
  return md.trim().split(/\n\s*\n/).map(block => {
    const lines = block.split('\n');
    if (lines.every(l => /^\s*[-*] /.test(l))) return '<ul>' + lines.map(l => '<li>' + inline(l.replace(/^\s*[-*] /, '')) + '</li>').join('') + '</ul>';
    return '<p>' + inline(block.replace(/\n/g, ' ')) + '</p>';
  }).join('');
}
function decision(id) { return (state[id] && state[id].d) || ''; }
function tally() {
  let y = 0, n = 0;
  for (const it of DATA.items) { const d = decision(it.id); if (d === 'yes') y++; else if (d === 'no') n++; }
  document.getElementById('tally').innerHTML = `<b>${DATA.items.length}</b> items · <b>${y}</b> yes · <b>${n}</b> no · <b>${DATA.items.length - y - n}</b> undecided`;
}
function setDecision(id, d, el) {
  state[id] = state[id] || {};
  state[id].d = (state[id].d === d) ? '' : d;
  save(); tally(); paint(el, id);
}
function paint(el, id) {
  const d = decision(id);
  el.classList.toggle('yes', d === 'yes'); el.classList.toggle('no', d === 'no');
  el.querySelector('.y').classList.toggle('on', d === 'yes');
  el.querySelector('.n').classList.toggle('on', d === 'no');
}
function draw() {
  const q = document.getElementById('q').value.trim().toLowerCase();
  const ft = document.getElementById('fTheme').value, fv = document.getElementById('fValue').value;
  const fe = document.getElementById('fEffort').value, fs = document.getElementById('fState').value;
  const list = document.getElementById('list'); list.innerHTML = '';
  let shown = 0, lastTheme = null;
  for (const it of DATA.items) {
    const d = decision(it.id);
    if (ft && it.theme !== ft) continue;
    if (fv && it.value !== fv) continue;
    if (fe && it.effort !== fe) continue;
    if (fs === 'u' && d) continue;
    if (fs && fs !== 'u' && d !== fs) continue;
    if (q && !(it.id + ' ' + it.title + ' ' + it.kind + ' ' + it.body).toLowerCase().includes(q)) continue;
    if (it.theme !== lastTheme) {
      const t = themeName[it.theme] || { name: it.theme, scope: '' };
      const h = document.createElement('h2'); h.className = 'theme'; h.textContent = t.name; list.appendChild(h);
      if (t.scope) { const p = document.createElement('p'); p.className = 'scope'; p.textContent = t.scope; list.appendChild(p); }
      lastTheme = it.theme;
    }
    const el = document.createElement('section'); el.className = 'item';
    el.innerHTML = `<div class="head"><span class="id">${esc(it.id)}</span><span class="title">${esc(it.title)}</span></div>
      <div class="meta"><span class="chip">${esc(it.kind)}</span><span class="chip ${it.value}">value: ${esc(it.value)}</span><span class="chip">effort: ${esc(it.effort)}</span></div>
      <div class="body">${render(it.body)}</div>
      <div class="decide"><button class="y">Yes</button><button class="n">No</button>
      <input class="note" placeholder="Note (optional)" value="${esc((state[it.id] && state[it.id].note) || '')}"></div>`;
    el.querySelector('.head').onclick = () => el.classList.toggle('open');
    el.querySelector('.y').onclick = () => setDecision(it.id, 'yes', el);
    el.querySelector('.n').onclick = () => setDecision(it.id, 'no', el);
    el.querySelector('.note').oninput = e => { state[it.id] = state[it.id] || {}; state[it.id].note = e.target.value; save(); };
    if (openAll) el.classList.add('open');
    paint(el, it.id); list.appendChild(el); shown++;
  }
  if (!shown) list.innerHTML = '<p class="empty">No items match.</p>';
}
let openAll = false;
document.getElementById('openAll').onclick = e => { openAll = !openAll; e.target.textContent = openAll ? 'Collapse all' : 'Expand all'; draw(); };
for (const id of ['q', 'fTheme', 'fValue', 'fEffort', 'fState']) document.getElementById(id).addEventListener('input', draw);

const dlg = document.getElementById('dlg'), area = document.getElementById('dlgArea');
function openDlg(text, value, canApply) {
  document.getElementById('dlgText').textContent = text; area.value = value;
  document.getElementById('dlgApply').style.display = canApply ? '' : 'none';
  dlg.showModal();
}
document.getElementById('export').onclick = () => {
  const lines = [];
  for (const it of DATA.items) {
    const s = state[it.id] || {};
    if (!s.d && !s.note) continue;
    lines.push(`${it.id} ${s.d || '?'}${s.note ? ' - ' + s.note.replace(/\s+/g, ' ') : ''}`);
  }
  openDlg('Your decisions, one per line. Copy them and paste them back to Claude.', lines.join('\n'), false);
};
document.getElementById('import').onclick = () => openDlg('Paste decisions exported earlier ("S1 yes", one per line), then Apply.', '', true);
document.getElementById('dlgCopy').onclick = () => { area.select(); try { navigator.clipboard.writeText(area.value); } catch (e) { document.execCommand('copy'); } };
document.getElementById('dlgClose').onclick = () => dlg.close();
document.getElementById('dlgApply').onclick = () => {
  for (const line of area.value.split('\n')) {
    const m = line.trim().match(/^([A-Z]{1,2}\d+)\s+(yes|no|\?)(?:\s+-\s+(.*))?$/i);
    if (!m) continue;
    state[m[1]] = { d: m[2] === '?' ? '' : m[2].toLowerCase(), note: m[3] || '' };
  }
  save(); tally(); draw(); dlg.close();
};
tally(); draw();
</script>
</body>
</html>
'''
open(out, 'w', encoding='utf-8').write(PAGE.replace('__DATA__', payload))
print('wrote', out, len(slim['items']), 'items')
