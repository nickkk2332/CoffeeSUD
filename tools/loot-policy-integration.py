#!/usr/bin/env python3
"""I02 loot policies in real play, across fresh processes: coffers whose loot table makes policy items (MODBASE=
"POLICY=...") beside a unique catalog token (cap 1, once ever); LOOT shows them by rarity through a character's filter;
a real save and restore keep the coffer's once-only roll, the token's cap and the policy's guarantee counter."""
import importlib.util
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys

ROOT = Path(__file__).resolve().parent.parent
EXAMPLES = ROOT / 'docs/creator/examples'
spec = importlib.util.spec_from_file_location('forge_sample', EXAMPLES / 'ember-trial/make_world.py')
factory = importlib.util.module_from_spec(spec); spec.loader.exec_module(factory)
scratch = Path(sys.argv[1]).resolve(); scratch.mkdir(parents=True, exist_ok=True)
dest = factory.create(scratch / 'deployment')
world = dest / 'world'
shutil.copytree(EXAMPLES / 'loot-policies', world, dirs_exist_ok=True, ignore=shutil.ignore_patterns('README.md'))
ini = (world / 'world.ini').read_text()
(world / 'world.ini').write_text(re.sub(r'(?m)^MODULES=(.*)$', r'MODULES=\1,items.loot', ini, count=1))
newchar = 'Y\nN\nHuman\nY\nM\nN\nApprentice\nY\ngood\n\n'
count = 0


def check(condition, name, detail=''):
    global count
    if not condition:
        raise AssertionError(name + ': ' + detail)
    count += 1
    print('FIXTEST ok loot-integration/' + name, flush=True)


def play(name, commands, restore, *settings):
    """A session; restore None for a builder session (the publish's own character and saves)."""
    path = scratch / (name + '.input'); path.write_text(commands)
    with (scratch / (name + '.txt')).open('w') as output:
        result = subprocess.run([sys.executable, str(ROOT / 'tools/sudplay.py'), str(path),
            'WORLDDIR=' + str(world), 'DBSERVICE=jdbc:fakedb:' + str(dest / 'db'),
            'SAVEDIR=' + str(dest / ('.build/saves' if restore is None else 'saves')),
            'BUILDER=1' if restore is None else 'RESTORE=' + restore, 'TIMEMODE=TURNS', 'RANDOMSEED=42', *settings],
            cwd=ROOT, env=dict(os.environ, SUDPLAY_LIMIT='480'), stdout=output, stderr=subprocess.STDOUT, timeout=540)
    text = (scratch / (name + '.txt')).read_text(errors='replace')
    check(result.returncode == 0, name + ' process', text[-2500:])
    check('JS error' not in text and 'JavaScript error' not in text and 'Huh?' not in text, name + ' commands', text[-2500:])
    return text


def loot_views(text):
    """Each LOOT listing in a session's output: its lines, from 'In ...:' to the next prompt."""
    views, current = [], None
    for line in text.splitlines():
        if re.search(r'In a policy coffer:', line):
            current = []
            views.append(current)
            continue
        if current is not None:
            if line.startswith('<') or not line.strip():
                current = None
            else:
                current.append(line.strip())
    return views


def guarantee(slot):
    data = json.loads((dest / 'saves' / slot / 'modules.json').read_text())
    module = next(m for m in data['modules'] if m['id'] == 'items.loot')
    for record in module['records']:
        raw = record['values'].get('guarantees', '')
        if raw:
            return json.loads(raw).get('example.coffer', 0)
    return 0


BUILD = """var CM = Packages.com.planet_ink.coffee_mud, CC = CM.core.CMClass, CL = CM.core.CMLib, CS = CM.Libraries.interfaces.CatalogLibrary.CataSpawn;
var room = CL.map().getRoom('EmberTrial#0');
for (var i = 0; i < 2; i++) {
	var C = CC.getItem('GenContainer'); C.setName('a policy coffer'); C.setDisplayText('A policy coffer stands here.'); C.setCapacity(500);
	C.setDoorsNLocks(true, false, true, false, false, false); C.basePhyStats().setWeight(50); C.recoverPhyStats();
	var L = CC.getAbility('Prop_LootTable'); L.setMiscText('FILE=randareas/loot-policies.xml ID=lootpolicy_supply ON=OPEN'); C.addNonUninvokableEffect(L);
	room.addItem(C);
}
var T = CC.getItem('GenItem'); T.setName('an ember token'); T.setDisplayText('An ember token lies here.'); T.recoverPhyStats(); CL.catalog().addCatalog(T); T.destroy();
var D = CL.catalog().getCatalogItemData('an ember token');
D.setSpawn(CS.CONTAINER); D.setRate(1.0); D.setCap(1); D.setWhen('OPEN'); D.setMaskStr('-NAMES "+a policy coffer"'); D.setUnique('ONCE');
CL.database().DBUpdateItem('CATALOG_ITEMS', CL.catalog().getCatalogItem('an ember token')); // as CATALOG EDIT saves its data
CL.database().DBUpdateItems(room);
mob().tell('FIXSETUP coffers ready');
"""

try:
    factory.publish(dest)
    check(True, 'trial published with items.loot selected')
    creator = world / 'resources/creator'; creator.mkdir(parents=True, exist_ok=True)
    (creator / 'loot-build.js').write_text(BUILD)
    built = play('build', 'Trialbuilder\njrun /resources/creator/loot-build.js\nworld check only=modules,itemmods,loot\nsave\nquit\nY\n', None)
    check('FIXSETUP coffers ready' in built and 'WORLD CHECK: 0 errors' in built, 'coffers and the unique token set up', built[-2500:])

    # a new game: open the first coffer, look through LOOT and a filter, save
    # OPEN takes a turn and LOOT none, so each LOOT waits for the opening to be told
    first = play('first', 'Lootseeker\n' + newchar + 'open policy coffer\n#expect You open\nloot policy coffer\nloot filter rare\nloot policy coffer\n'
                 'loot filter all\nsave first\nquit\nn\n', 'NEW')
    views = loot_views(first)
    check(len(views) == 2, 'two LOOT listings', first[-2500:])
    opened = views[0]
    mantles = [l for l in opened if 'a ' in l and 'coffer mantle' in l]
    check(len(mantles) == 2 and any(l.startswith(('Plain:', 'Magic:', 'Rare:')) for l in mantles), 'policy mantles listed by rarity', '\n'.join(opened))
    check(any('1 other item' in l for l in opened), 'the unique token counted beside them', '\n'.join(opened))
    filtered = views[1]
    check(all(not l.startswith(('Plain:', 'Magic:')) for l in filtered), 'the filter leaves out other rarities', '\n'.join(filtered))
    before = guarantee('first')
    check(0 <= before <= 2, 'guarantee counter saved', str(before))

    # a real restore: the opened coffer stays rolled, the second coffer makes policy items but no second token
    # the restored coffer comes back open, as it was saved, and still rolled
    second = play('second', 'world loot preview example.coffer 1\nopen policy coffer\n#expect already open\nloot policy coffer\nopen 2.policy coffer\n#expect You open\nloot 2.policy coffer\n'
                  'save second\nquit\nn\n', 'first', 'DEV=1')
    again = loot_views(second)
    check(len(again) == 2, 'two LOOT listings after the restore', second[-2500:])
    check(again[0] == opened, 'the opened coffer rolled once: the same loot after the restore', '\n'.join(opened) + '\n--\n' + '\n'.join(again[0]))
    other = again[1]
    check(len([l for l in other if 'coffer mantle' in l]) == 2 and not any('other item' in l for l in other), 'the token cap holds across the restore', '\n'.join(other))
    normal = ' '.join(second.split())
    check('Drops since the last: ' + str(before) in normal, 'the guarantee counter came back with the save', normal[-1500:])
    after = guarantee('second')
    check(after == before + 2 or after <= 1, 'the counter went on from the save', str(before) + ' -> ' + str(after))
    print('FIXTEST done loot-integration (%d checks)' % count, flush=True)
except Exception as error:
    print('FIXTEST FAIL loot-integration: ' + str(error), flush=True)
    raise
