#!/usr/bin/env python3
"""Compare two versions of a CoffeeSUD world folder, object by object.

    tools/worlddiff.py OLD_WORLD NEW_WORLD
    tools/worlddiff.py --git REV [WORLD]

The first form compares two world folders (WORLDDIR, "world" by default).
The second compares the world folder as git revision REV has it with the
folder as it is now (WORLD, by default CoffeeMud/world).

Rooms are compared by ID: their kind, title, description, behaviours,
effects and exits, and the mobs and items placed in them, by name.  Each
difference is one line, such as

    New Area#1: title 'Rocky Room' -> 'Mossy Ledge'
    New Area#0: mob 'a baker': +Scriptable
    New Area#3: room added

Other files (custom objects, the catalog, quests, journals, settings) are
reported as added, removed or changed.  Reads both the readable world files
(things' own texts as XML, marked xml="1") and the older escaped ones.
Exits with 1 if there are differences, 0 if none.
"""
import os
import re
import subprocess
import sys


def unescape(s):
    return s.replace('&lt;', '<').replace('&gt;', '>')


class Node:
    def __init__(self, tag, attrs):
        self.tag = tag
        self.attrs = attrs
        self.kids = []
        self.text = ''

    def find(self, tag):
        for k in self.kids:
            if k.tag == tag:
                return k
        return None

    def all(self, tag):
        return [k for k in self.kids if k.tag == tag]

    def val(self, tag, default=''):
        k = self.find(tag)
        return default if k is None else k.text


TOKEN = re.compile(r'<(/?)([A-Za-z_][\w.-]*)([^<>]*?)(/?)>')


def parse(xml):
    """A forgiving parser for the engine's XML: elements and their text."""
    root = Node('#root', {})
    stack = [root]
    pos = 0
    for m in TOKEN.finditer(xml):
        text = xml[pos:m.start()]
        if text.strip() and len(stack) > 1:
            stack[-1].text += text
        pos = m.end()
        closing, name, attrs, empty = m.group(1), m.group(2), m.group(3), m.group(4)
        if closing:
            while len(stack) > 1:
                top = stack.pop()
                if top.tag == name:
                    break
            continue
        node = Node(name, dict(re.findall(r'(\w+)="([^"]*)"', attrs)))
        stack[-1].kids.append(node)
        if not empty:
            stack.append(node)
    return root


def own_text(node):
    """A thing's own text (MTEXT, ITEXT, RTEXT) as a parsed tree."""
    if node is None:
        return Node('#none', {})
    if node.attrs.get('xml') == '1':
        return node
    return parse(unescape(node.text))


def behaviours(tree):
    names = []
    for holder in walk(tree):
        if holder.tag in ('BHAVE', 'BEHAVE'):
            cls = holder.val('BCLASS')
            if cls:
                names.append(cls)
    return sorted(names)


def effects(tree):
    names = []
    for holder in walk(tree):
        if holder.tag in ('AFF', 'AFFECT'):
            cls = holder.val('ACLASS')
            if cls:
                names.append(cls)
    return sorted(names)


def walk(node):
    yield node
    for k in node.kids:
        yield from walk(k)


def thing_name(tree, fallback):
    for tag in ('NAME', 'MNAME', 'INAME'):
        for n in walk(tree):
            if n.tag == tag and n.text.strip():
                return unescape(n.text.strip())
    return fallback


def describe_thing(node, text_tag, class_tag):
    tree = own_text(node.find(text_tag))
    cls = node.val(class_tag)
    return {
        'name': thing_name(tree, cls),
        'class': cls,
        'behaviours': behaviours(tree),
        'effects': effects(tree),
        'text': flatten(tree),
    }


def flatten(node):
    """A stable form of a tree, for telling whether two texts differ."""
    parts = [node.tag, node.text.strip()]
    for k in node.kids:
        parts.append(flatten(k))
    return '<' + '|'.join(parts) + '>'


def rooms_of(xml):
    rooms = {}
    for room in walk(parse(xml)):
        if room.tag != 'AROOM':
            continue
        rid = room.val('ROOMID')
        rtext = own_text(room.find('RTEXT'))
        exits = {}
        holder = room.find('ROOMEXITS')
        for x in (holder.all('REXIT') if holder else []):
            exits[x.val('XDIRE')] = x.val('XDOOR')
        content = room.find('ROOMCONTENT')
        mobs, items = [], []
        if content is not None:
            for holder_tag, tag, text_tag, class_tag, out in (('ROOMMOBS', 'RMOB', 'MTEXT', 'MCLAS', mobs),
                                                               ('ROOMITEMS', 'RITEM', 'ITEXT', 'ICLAS', items)):
                h = content.find(holder_tag)
                for thing in (h.all(tag) if h else []):
                    out.append(describe_thing(thing, text_tag, class_tag))
        rooms[rid] = {
            'class': room.val('RCLAS'),
            'title': room.val('RDISP'),
            'desc': room.val('RDESC'),
            'behaviours': behaviours(rtext),
            'effects': effects(rtext),
            'text': flatten(rtext),
            'exits': exits,
            'mobs': mobs,
            'items': items,
        }
    return rooms


def list_diff(label, old, new):
    out = []
    for name in sorted(set(old) | set(new)):
        o, n = old.count(name), new.count(name)
        if n > o:
            out.append('%s+%s' % (label, name) + ('' if n - o == 1 else ' x%d' % (n - o)))
        elif o > n:
            out.append('%s-%s' % (label, name) + ('' if o - n == 1 else ' x%d' % (o - n)))
    return out


def things_diff(rid, kind, old, new):
    lines = []
    old_by = {}
    new_by = {}
    for t in old:
        old_by.setdefault(t['name'], []).append(t)
    for t in new:
        new_by.setdefault(t['name'], []).append(t)
    for name in sorted(set(old_by) | set(new_by)):
        o, n = old_by.get(name, []), new_by.get(name, [])
        if len(n) > len(o):
            lines.append("%s: + %s '%s'" % (rid, kind, name) + ('' if len(n) - len(o) == 1 else ' x%d' % (len(n) - len(o))))
        elif len(o) > len(n):
            lines.append("%s: - %s '%s'" % (rid, kind, name) + ('' if len(o) - len(n) == 1 else ' x%d' % (len(o) - len(n))))
        for a, b in zip(o, n):
            changes = list_diff('', a['behaviours'], b['behaviours']) + list_diff('effect ', a['effects'], b['effects'])
            if a['class'] != b['class']:
                changes.insert(0, '%s -> %s' % (a['class'], b['class']))
            if changes:
                lines.append("%s: %s '%s': %s" % (rid, kind, name, ', '.join(changes)))
            elif a['text'] != b['text']:
                lines.append("%s: %s '%s': changed" % (rid, kind, name))
    return lines


def compare_areas(old_xml, new_xml):
    old, new = rooms_of(old_xml), rooms_of(new_xml)
    lines = []
    for rid in sorted(set(old) | set(new)):
        if rid not in old:
            lines.append('%s: room added (%s)' % (rid, new[rid]['title']))
            continue
        if rid not in new:
            lines.append('%s: room removed (%s)' % (rid, old[rid]['title']))
            continue
        a, b = old[rid], new[rid]
        if a['class'] != b['class']:
            lines.append('%s: kind %s -> %s' % (rid, a['class'], b['class']))
        if a['title'] != b['title']:
            lines.append("%s: title '%s' -> '%s'" % (rid, a['title'], b['title']))
        if a['desc'] != b['desc']:
            lines.append('%s: description changed' % rid)
        changes = list_diff('', a['behaviours'], b['behaviours']) + list_diff('effect ', a['effects'], b['effects'])
        if changes:
            lines.append('%s: %s' % (rid, ', '.join(changes)))
        elif a['text'] != b['text']:
            lines.append('%s: properties changed' % rid)
        for d in sorted(set(a['exits']) | set(b['exits'])):
            if a['exits'].get(d) != b['exits'].get(d):
                lines.append('%s: exit %s %s -> %s' % (rid, d, a['exits'].get(d, 'none'), b['exits'].get(d, 'none')))
        lines += things_diff(rid, 'mob', a['mobs'], b['mobs'])
        lines += things_diff(rid, 'item', a['items'], b['items'])
    return lines


def files_in(root):
    found = {}
    for base, _, names in os.walk(root):
        for n in names:
            path = os.path.join(base, n)
            found[os.path.relpath(path, root)] = path
    return found


def read(path):
    with open(path, encoding='utf-8', errors='replace') as f:
        return f.read()


def git_files(rev, world):
    top = subprocess.run(['git', '-C', world, 'rev-parse', '--show-toplevel'], capture_output=True, text=True, check=True).stdout.strip()
    rel = os.path.relpath(os.path.abspath(world), top)
    listing = subprocess.run(['git', '-C', top, 'ls-tree', '-r', '--name-only', rev, '--', rel], capture_output=True, text=True, check=True).stdout.split('\n')
    out = {}
    for name in listing:
        if name:
            text = subprocess.run(['git', '-C', top, 'show', '%s:%s' % (rev, name)], capture_output=True, text=True, errors='replace').stdout
            out[os.path.relpath(name, rel)] = text
    return out


def main(argv):
    if len(argv) >= 2 and argv[0] == '--git':
        world = argv[2] if len(argv) > 2 else os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'CoffeeMud', 'world')
        old = git_files(argv[1], world)
        new = {k: read(v) for k, v in files_in(world).items()}
    elif len(argv) == 2:
        old = {k: read(v) for k, v in files_in(argv[0]).items()}
        new = {k: read(v) for k, v in files_in(argv[1]).items()}
    else:
        print(__doc__.strip(), file=sys.stderr)
        return 2
    lines = []
    for name in sorted(set(old) | set(new)):
        if name.startswith('areas' + os.sep) and name.endswith('.cmare'):
            lines += compare_areas(old.get(name, ''), new.get(name, ''))
        elif name not in old:
            lines.append('%s: added' % name)
        elif name not in new:
            lines.append('%s: removed' % name)
        elif old[name] != new[name]:
            lines.append('%s: changed' % name)
    for line in lines:
        print(line)
    return 1 if lines else 0


if __name__ == '__main__':
    sys.exit(main(sys.argv[1:]))
