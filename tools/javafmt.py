#!/usr/bin/env python3
"""Lays out Java in the code base's style: one statement per line, Allman braces, tab indents, a braceless body
on its own line, and statements longer than 150 columns broken at their shallowest operators.  Only the whitespace
between tokens changes; the tokens are checked to be the same, in the same order, before anything is written.

Usage: python3 tools/javafmt.py IN [OUT]     (OUT defaults to IN)

A layout change compiles to the same class files.  tools/javafmt-verify.sh compares them byte for byte, compiled
without debug information, between the files as committed and as they are now."""
import re
import sys

TOKEN = re.compile(r'''
    (?P<ws>[ \t\r\n]+)
  | (?P<lcom>//[^\n]*)
  | (?P<bcom>/\*.*?\*/)
  | (?P<str>"(?:\\.|[^"\\\n])*")
  | (?P<chr>'(?:\\.|[^'\\\n])+')
  | (?P<word>[A-Za-z_$][A-Za-z0-9_$]*)
  | (?P<num>(?:0[xX][0-9a-fA-F_]+|\d[\d_]*(?:\.\d*)?(?:[eE][+-]?\d+)?)[lLfFdD]?|\.\d+(?:[eE][+-]?\d+)?[fFdD]?)
  | (?P<op>->|::|\+\+|--|&&|\|\||[=!<>]=|[-+*/%&|^]=|<<=|>>>=|>>=|\.\.\.|[{}()\[\];,.?:~!<>=+\-*/%&|^@])
''', re.S | re.X)
CONTROL = {'if', 'for', 'while', 'switch', 'catch', 'synchronized'}
BRACELESS = {'if', 'for', 'while'}


def tokenize(text):
    out, pos, nl, ws = [], 0, 0, False
    while pos < len(text):
        m = TOKEN.match(text, pos)
        if not m:
            raise SystemExit('cannot read at %d: %r' % (pos, text[pos:pos + 40]))
        pos = m.end()
        kind = m.lastgroup
        if kind == 'ws':
            nl += m.group().count('\n')
            ws = True
            continue
        out.append({'kind': kind, 'text': m.group(), 'nl': nl, 'ws': ws})
        nl, ws = 0, False
    return out


def layout(tokens):
    lines, continued = [], set()
    st = {'line_cont': False, 'indent': 0, 'pend': [], 'paren': 0, 'cur': [], 'cont': False}
    stack = []           # per '{': ('init',) or ('block', saved state)
    headers = []         # paren depths at which a control header's '(' opened, with its keyword
    pending_header = None

    def indent_now():
        return st['indent'] + len(st['pend']) + (1 if st['cont'] else 0)

    def flush(cont=False):
        if st['cur']:
            if st['line_cont']:
                continued.add(len(lines))
            lines.append('\t' * st['line_indent'] + ''.join(st['cur']))
            st['cur'] = []
        st['cont'] = cont

    def put(text, space):
        if not st['cur']:
            st['line_indent'] = indent_now()
            st['line_cont'] = st['cont']
            st['cur'].append(text)
        else:
            st['cur'].append((' ' if space else '') + text)

    def nxt(i):
        j = i + 1
        while j < len(tokens) and tokens[j]['kind'] in ('lcom', 'bcom'):
            j += 1
        return tokens[j]['text'] if j < len(tokens) else ''

    def complete(i):
        # a statement ended: each braceless body it finished is done, up to an 'if' whose 'else' follows
        while st['pend']:
            kind = st['pend'].pop()
            if kind == 'if' and nxt(i) == 'else':
                return

    prev = ''
    for i, t in enumerate(tokens):
        text, kind = t['text'], t['kind']
        # original line breaks: a blank line between statements is kept; one inside a statement continues it
        if t['nl'] and kind not in ('lcom', 'bcom'):
            if st['cur'] and not (text == '{' and (not stack or stack[-1][0] == 'block') and prev not in ('=', ']', ',', '(', '{')):
                annotation = re.fullmatch(r'@\w+(\(.*\))?', ''.join(st['cur']).strip())
                flush(cont=not annotation)
            if t['nl'] >= 2 and not st['cur'] and not st['cont'] and lines and lines[-1].strip() not in ('{', ''):
                lines.append('')
        if kind == 'lcom':
            if t['nl'] or not st['cur']:
                flush(st['cont'] or bool(st['cur']))
                put(text, False)
                flush(st['cont'])
            else:
                put(text, True)
                flush(not (prev in (';', '{', '}')))
            continue
        if kind == 'bcom':
            body = text.split('\n')
            if t['nl'] or not st['cur']:
                flush(st['cont'])
                if t['nl'] >= 2 and lines and lines[-1].strip() not in ('{', ''):
                    lines.append('')
                ind = '\t' * indent_now()
                lines.append(ind + body[0].strip())
                for b in body[1:]:
                    b = b.strip()
                    lines.append(ind + (' ' + b if b.startswith('*') else b))
            else:
                put(text, t['ws'])
            continue
        if text == '{':
            is_init = (stack and stack[-1][0] == 'init' and prev in ('{', ',')) or prev in ('=', ']', ',', '(', 'return')
            if is_init and prev != ')':
                put(text, t['ws'])
                stack.append(('init',))
            else:
                flush(False)
                put('{', False)
                flush(False)
                stack.append(('block', dict(indent=st['indent'], pend=st['pend'], paren=st['paren'], cont=st['cont']), headers))
                st['indent'] = indent_now() + 1
                st['pend'], st['paren'], st['cont'] = [], 0, False
                headers = []
            prev = text
            continue
        if text == '}':
            top = stack.pop()
            if top[0] == 'init':
                put(text, t['ws'])
                prev = text
                continue
            flush(False)
            saved = top[1]
            headers = top[2]
            st['indent'], st['pend'], st['paren'], st['cont'] = saved['indent'], saved['pend'], saved['paren'], saved['cont']
            put('}', False)
            after = nxt(i)
            if after in (')', ',', ';', '.', ']'):
                prev = text
                continue                     # a block inside an expression: the statement goes on
            flush(False)
            if after not in ('catch', 'finally', 'else', 'while'):
                complete(i)
            prev = text
            continue
        put(text, t['ws'])
        if text == '(':
            st['paren'] += 1
            if prev in CONTROL:
                headers.append((st['paren'], prev))
        elif text == ')':
            if headers and headers[-1][0] == st['paren']:
                word = headers.pop()[1]
                after = nxt(i)
                if word in BRACELESS and after not in ('{', ';', ''):
                    flush(False)
                    st['pend'].append(word)
            st['paren'] -= 1
        elif text == ';' and st['paren'] == 0:
            flush(False)
            complete(i)
        elif text in ('else', 'do'):
            after = nxt(i)
            if after not in ('{', 'if'):
                flush(False)
                st['pend'].append(text)
        prev = text
    flush(False)
    return lines, continued


LIMIT = 150


def width(line):
    return len(line.expandtabs(4))


def wrap(line, continuation=False):
    """a long statement line broken at its shallowest operators: before . + ? : && ||, after a comma"""
    if width(line) <= LIMIT or line.lstrip().startswith(('//', '/*', '*')):
        return [line]
    tabs = len(line) - len(line.lstrip('\t'))
    body = line[tabs:]
    toks, pos = [], 0
    while pos < len(body):
        m = TOKEN.match(body, pos)
        toks.append(m.group())
        pos = m.end()
    if any(t.startswith('//') for t in toks):
        return [line]
    out, cur, depth, best = [], '', 0, None
    pieces = []          # (text, depth at its start, may break before it)
    for t in toks:
        if t in (')', ']', '}'):
            depth -= 1
        breakable = t in ('.', '+', '?', ':', '&&', '||') or (pieces and pieces[-1][0] == ',')
        pieces.append((t, depth, breakable))
        if t in ('(', '[', '{'):
            depth += 1
    lines, start = [], 0
    first = True
    while True:
        prefix = '\t' * (tabs if first or continuation else tabs + 1)
        text, candidates = '', []
        k = start
        while k < len(pieces):
            if k > start and pieces[k][2] and pieces[k][0].strip() and width(prefix + text) > 40:
                candidates.append(k)
            text += pieces[k][0]
            if width(prefix + text) > LIMIT and candidates:
                break
            k += 1
        cut = None
        if candidates:
            # the shallowest break; at the same depth ? : first, then && ||, then + and commas, a '.' last; the latest of those
            rank = {'?': 0, ':': 0, '&&': 1, '||': 1, '+': 2, '.': 4}
            cut = min(candidates, key=lambda c: (pieces[c][1], rank.get(pieces[c][0], 3), -c))
        if k >= len(pieces):
            lines.append(prefix + ''.join(p[0] for p in pieces[start:]).strip())
            break
        lines.append(prefix + ''.join(p[0] for p in pieces[start:cut]).rstrip())
        start = cut
        while start < len(pieces) and not pieces[start][0].strip():
            start += 1
        first = False
    return lines


if __name__ == '__main__':
    src = open(sys.argv[1], encoding='utf-8').read()
    laid, continued = layout(tokenize(src))
    out = '\n'.join(w for k, line in enumerate(laid) for w in wrap(line, k in continued)) + '\n'
    # the same tokens, in the same order
    def norm(t):
        return '\n'.join(l.strip() for l in t['text'].split('\n')) if t['kind'] == 'bcom' else t['text']
    a = [norm(t) for t in tokenize(src)]
    b = [norm(t) for t in tokenize(out)]
    if a != b:
        for k, (x, y) in enumerate(zip(a, b)):
            if x != y:
                raise SystemExit('token %d differs: %r vs %r' % (k, x, y))
        raise SystemExit('token count differs')
    open(sys.argv[2] if len(sys.argv) > 2 else sys.argv[1], 'w', encoding='utf-8').write(out)
