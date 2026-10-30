"""Save a static, browsable snapshot of a running MUDGrinder.

    python3 grindsnap.py BASE_URL LOGIN PASSWORD OUTDIR [SEEDS]

SEEDS is an optional file of extra page URLs (path?query, one per line) for
pages that the Grinder only reaches through values JavaScript fills in at
click time, such as a room picked on the map.

Logs in with a real browser (Chromium through Playwright for Node, driven by
browse.js next to this file), then visits every Grinder page it can reach:
links, window.open() calls and the JavaScript form submits the Grinder uses
for navigation.  Pages that change the world (names ending in do/done/gone,
create, delete, import and so on) are never requested.  Pages the server
answers with an error are dropped.  Each page is saved as rendered HTML;
links between saved pages are rewritten to local files, and shim.js maps any
remaining form submit or popup to the closest saved page, or to
notcaptured.html.
"""
import json
import os
import re
import sys
import urllib.parse
import base64
import html as htmllib
import json as _json
import subprocess

BASE, LOGIN, PASSWORD, OUT = sys.argv[1:5]
SEEDS = open(sys.argv[5]).read().split() if len(sys.argv) > 5 else []
MAX_VARIANTS = 3      # saved copies of one page with different parameters
MAX_TRIES = 8         # requests for one page, counting ones the server rejected
ERROR_PAGE = re.compile(r'<title>\s*S[45]\d\d_', re.I)
MAX_PAGES = 400
DENY = re.compile(r'(do|done|gone)\.cmvp$|delete|/del|create|import|save|reset|link(exit|area)|'
                  r'masscreate|recovery|rtemplatenew|newarea2|paintarea|editnew|failedlogin|logout', re.I)
REF = re.compile(r"""(?:[\w./-]*/)?[\w-]+\.cmvp(?:\?[^'"\s<>)]*)?""")
SKIP_PARAMS = {'LOGIN', 'PASSWORD', 'LOGINBUTTON'}

FORM_JS = """
() => {
  const forms = Array.from(document.forms).map(f => {
    const d = {};
    for (const el of Array.from(f.elements)) {
      if (!el.name || el.disabled) continue;
      if ((el.type === 'checkbox' || el.type === 'radio') && !el.checked) continue;
      if (el.type === 'button' || el.type === 'submit' || el.type === 'file') continue;
      if (el.tagName === 'SELECT' && el.multiple) {
        const v = Array.from(el.selectedOptions).map(o => o.value);
        if (v.length) d[el.name] = v[0];
      } else if (el.value !== undefined && el.value !== '') d[el.name] = el.value;
    }
    return {fields: d, action: f.getAttribute('action') || ''};
  });
  const refs = [];
  const attrs = ['onclick', 'onchange', 'href', 'src', 'action', 'ondblclick'];
  for (const el of Array.from(document.querySelectorAll('*'))) {
    for (const a of attrs) {
      const v = el.getAttribute(a);
      if (!v || v.indexOf('.cmvp') < 0) continue;
      const f = el.closest('form') || (el.form ? el.form : null);
      refs.push({text: v, form: f ? Array.from(document.forms).indexOf(f) : -1});
    }
  }
  for (const s of Array.from(document.scripts)) {
    if (s.text && s.text.indexOf('.cmvp') >= 0) refs.push({text: s.text, form: -2});
  }
  return {forms, refs};
}
"""


def norm_path(ref, page_path):
    path = ref.split('?', 1)[0]
    if not path.startswith('/'):
        path = os.path.normpath(os.path.join(os.path.dirname(page_path), path)).replace('\\', '/')
    return path


def key(path, params):
    return path + '?' + urllib.parse.urlencode(sorted(params.items()))


def main():
    pages = {}        # key -> {'path','params','file','html'}
    by_path = {}      # path -> [keys]
    queue = []
    seen = set()
    tries = {}        # path -> requests queued

    def enqueue(path, params):
        if not path.endswith('.cmvp') or DENY.search(path):
            return
        params = {k: v for k, v in params.items() if k not in SKIP_PARAMS and v != ''}
        k = key(path, params)
        if k in seen or len(by_path.get(path, [])) >= MAX_VARIANTS or tries.get(path, 0) >= MAX_TRIES:
            return
        seen.add(k)
        tries[path] = tries.get(path, 0) + 1
        queue.append((path, params))

    here = os.path.dirname(os.path.abspath(__file__))
    js_file = os.path.join(OUT, '.formjs')
    os.makedirs(OUT, exist_ok=True)
    open(js_file, 'w').write(FORM_JS)
    node = subprocess.Popen(['node', os.path.join(here, 'browse.js'), BASE, LOGIN, PASSWORD, js_file],
                            stdin=subprocess.PIPE, stdout=subprocess.PIPE, text=True, bufsize=1)

    def ask(**req):
        node.stdin.write(_json.dumps(req) + '\n')
        node.stdin.flush()
        return _json.loads(node.stdout.readline())

    first_reply = _json.loads(node.stdout.readline())
    login_html = first_reply['html']
    menu_path = urllib.parse.urlparse(first_reply['url']).path
    enqueue(menu_path, {})
    for seed in SEEDS:
        enqueue(seed.split('?', 1)[0], dict(urllib.parse.parse_qsl(seed.split('?', 1)[1])) if '?' in seed else {})
    while queue and len(pages) < MAX_PAGES:
        path, params = queue.pop(0)
        url = BASE + path + ('?' + urllib.parse.urlencode(params) if params else '')
        r = ask(cmd='page', url=url)
        if not r['ok']:
            print('skip', url, r.get('error', '')[:200])
            continue
        html = r['html']
        if ERROR_PAGE.search(html) or len(by_path.get(path, [])) >= MAX_VARIANTS:
            continue  # the server could not show this page with these parameters
        k = key(path, params)
        n = len(by_path.setdefault(path, []))
        name = os.path.splitext(path.lstrip('/'))[0] + ('' if n == 0 else '-%d' % n) + '.html'
        pages[k] = {'path': path, 'params': params, 'file': name, 'html': html}
        by_path[path].append(k)
        print('%3d %s' % (len(pages), url[len(BASE):][:150]), flush=True)
        info = r['info']
        forms = info['forms']
        first = forms[0]['fields'] if forms else {}
        for ref_info in info['refs']:
            fields = forms[ref_info['form']]['fields'] if ref_info['form'] >= 0 else first
            for m in REF.finditer(ref_info['text']):
                ref = m.group(0)
                tpath = norm_path(ref, path)
                if '?' in ref:
                    q = dict(urllib.parse.parse_qsl(ref.split('?', 1)[1], keep_blank_values=False))
                    if any('+' in v or v.endswith('=') for v in q.values()):
                        q = {}  # built by JavaScript at click time; take the page as is
                    enqueue(tpath, q)
                else:
                    enqueue(tpath, dict(fields))
    # static resources the pages use
    assets = set()
    for pinfo in pages.values():
        for m in re.finditer(r"""(?:src|href|background)\s*=\s*["']?(/?[\w./-]+\.(?:css|js|jpg|jpeg|gif|png|ico|bmp))""", pinfo['html'], re.I):
            assets.add(norm_path(m.group(1), pinfo['path']))
    for a in sorted(assets):
        r = ask(cmd='get', url=BASE + a)
        if r['ok']:
            dest = os.path.join(OUT, a.lstrip('/'))
            os.makedirs(os.path.dirname(dest), exist_ok=True)
            open(dest, 'wb').write(base64.b64decode(r['body']))
    node.stdin.write(_json.dumps({'cmd': 'quit'}) + '\n')
    node.stdin.flush()
    try:
        node.wait(timeout=30)
    except subprocess.TimeoutExpired:
        node.kill()
    os.remove(js_file)

    write_snapshot(pages, by_path, login_html, menu_path)


def rel(from_file, to_file):
    return os.path.relpath(to_file, os.path.dirname(from_file) or '.').replace('\\', '/')


def write_snapshot(pages, by_path, login_html, menu_path):
    index = {}
    for path, keys in by_path.items():
        index[path] = [{'params': pages[k]['params'], 'file': pages[k]['file']} for k in keys]

    def target_for(path, params, from_file):
        cands = index.get(path)
        if not cands:
            if path.startswith('/pub/guides/'):
                return rel(from_file, '../../../CoffeeMud/guides/' + path[len('/pub/guides/'):])
            return rel(from_file, 'notcaptured.html') + '#' + urllib.parse.quote(path)
        best = max(cands, key=lambda c: sum(1 for k, v in c['params'].items() if params.get(k) == v))
        return rel(from_file, best['file'])

    attr = re.compile(r"""\b((?:href|src|action|background)\s*=\s*)(?:"([^"]*)"|'([^']*)'|([^\s>"']+))""", re.I)
    local = re.compile(r'^/?[\w./-]+(?:\.cmvp(?:\?.*)?|\.(?:css|js|jpg|jpeg|gif|png|ico|bmp|html))$', re.I)

    def rewrite(html, page_file, page_path):
        def fix(m):
            raw = next(g for g in m.groups()[1:] if g is not None)
            ref = htmllib.unescape(raw).strip()
            if not local.match(ref):
                return m.group(0)
            path = norm_path(ref, page_path)
            if '.cmvp' in ref:
                q = dict(urllib.parse.parse_qsl(ref.split('?', 1)[1])) if '?' in ref else {}
                new = target_for(path, q, page_file)
            elif path.startswith('/pub/guides/'):
                new = target_for(path, {}, page_file)
            else:
                new = rel(page_file, path.lstrip('/'))
            return m.group(1) + '"' + new + '"'
        html = attr.sub(fix, html)
        shim = '<script src="%s"></script>' % rel(page_file, 'shim.js')
        return re.sub(r'(<head[^>]*>)', r'\1' + shim, html, count=1, flags=re.I) if re.search(r'<head', html, re.I) else shim + html

    for pinfo in pages.values():
        dest = os.path.join(OUT, pinfo['file'])
        os.makedirs(os.path.dirname(dest), exist_ok=True)
        open(dest, 'w', encoding='utf-8').write(rewrite(pinfo['html'], pinfo['file'], pinfo['path']))
    open(os.path.join(OUT, 'login.html'), 'w', encoding='utf-8').write(rewrite(login_html, 'login.html', '/index.cmvp'))
    menu = index[menu_path][0]['file']
    open(os.path.join(OUT, 'index.html'), 'w', encoding='utf-8').write(
        '<!DOCTYPE html><meta charset="utf-8"><meta http-equiv="refresh" content="0; url=%s">'
        '<a href="%s">MUDGrinder snapshot</a>' % (menu, menu))
    open(os.path.join(OUT, 'notcaptured.html'), 'w', encoding='utf-8').write(NOTCAPTURED)
    open(os.path.join(OUT, 'shim.js'), 'w', encoding='utf-8').write(SHIM.replace('__INDEX__', json.dumps(index, indent=0)))
    print('saved %d pages' % len(pages))


NOTCAPTURED = """<!DOCTYPE html><html><head><meta charset="utf-8"><title>Not captured</title>
<style>body{background:#000;color:#ddd;font-family:sans-serif;padding:2em}a{color:cyan}</style></head>
<body><h2>Not in the snapshot</h2>
<p>This MUDGrinder page, <code id="p"></code>, either changes the world (save, delete, create) or was
not reached while the snapshot was taken. The snapshot has no server behind it.</p>
<p>The page's template and the Java behind it are in <code>archive/mudgrinder/web</code> and
<code>archive/mudgrinder/WebMacros</code>.</p>
<p><a href="javascript:history.back()">Back</a> | <a href="index.html">Grinder menu</a></p>
<script>document.getElementById('p').textContent=decodeURIComponent(location.hash.substring(1))||'(unknown)';</script>
</body></html>
"""

SHIM = r"""// Turns the MUDGrinder's form submits, popups and links into navigation
// between the saved pages of this snapshot.  There is no server.
(function () {
  var INDEX = __INDEX__;
  var here = document.currentScript.src;
  var root = here.substring(0, here.lastIndexOf('/') + 1);
  function pagePath() {
    var file = location.href.substring(root.length).split('#')[0].split('?')[0];
    for (var p in INDEX) for (var i = 0; i < INDEX[p].length; i++) if (INDEX[p][i].file === file) return p;
    return '/grinder/loggedin.cmvp';
  }
  function resolve(ref) {
    var path = ref.split('?')[0], params = {};
    if (ref.indexOf('?') >= 0) ref.split('?')[1].split('&').forEach(function (kv) {
      var x = kv.split('='); if (x[0]) params[decodeURIComponent(x[0])] = decodeURIComponent((x[1] || '').replace(/\+/g, ' '));
    });
    if (path.charAt(0) !== '/') {
      var base = pagePath(); path = base.substring(0, base.lastIndexOf('/') + 1) + path;
    }
    return {path: path.replace(/\/\.\//g, '/'), params: params};
  }
  function target(path, params) {
    if (path.indexOf('/pub/guides/') === 0) return root + '../../../CoffeeMud/guides/' + path.substring(12);
    var c = INDEX[path];
    if (!c) return root + 'notcaptured.html#' + encodeURIComponent(path);
    var best = c[0], score = -1;
    c.forEach(function (v) {
      var s = 0; for (var k in v.params) if (params[k] === v.params[k]) s++;
      if (s > score) { score = s; best = v; }
    });
    return root + best.file;
  }
  function formParams(f) {
    var d = {};
    Array.prototype.forEach.call(f.elements, function (el) {
      if (!el.name) return;
      if ((el.type === 'checkbox' || el.type === 'radio') && !el.checked) return;
      d[el.name] = el.value;
    });
    return d;
  }
  function go(f) {
    var r = resolve(f.getAttribute('action') || f.action || pagePath());
    var p = formParams(f); for (var k in r.params) p[k] = r.params[k];
    var t = target(r.path, p);
    var w = (f.target && f.target !== '_top' && f.target !== '_self' && f.target !== '_parent') ? window.open(t, f.target) : null;
    if (!w) (f.target === '_top' ? window.top : window).location.href = t;
  }
  HTMLFormElement.prototype.submit = function () { go(this); };
  document.addEventListener('submit', function (e) { e.preventDefault(); go(e.target); }, true);
  var open = window.open;
  window.open = function (url, name, feat) {
    if (url && String(url).indexOf('.cmvp') >= 0) { var r = resolve(String(url)); url = target(r.path, r.params); }
    return open.call(window, url, name, feat);
  };
  document.addEventListener('click', function (e) {
    var a = e.target.closest ? e.target.closest('a') : null;
    if (!a) return;
    var h = a.getAttribute('href') || '';
    if (h.indexOf('.cmvp') >= 0 && h.indexOf('javascript:') !== 0) {
      e.preventDefault(); var r = resolve(h); window.location.href = target(r.path, r.params);
    }
  }, true);
})();
"""

if __name__ == '__main__':
    main()
