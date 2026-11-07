#!/usr/bin/env python3
"""I07 and I17 saved choices in real play, across fresh processes: a coffer's veiled charms; a reveal at the seer's table
and an offering at the ember altar, each drawn once; a real save and restore show the same options; a pick then settles
each, once."""
import importlib.util
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
for pack in ('loot-policies', 'saved-choices'):
    shutil.copytree(EXAMPLES / pack, world, dirs_exist_ok=True, ignore=shutil.ignore_patterns('README.md'))
ini = (world / 'world.ini').read_text()
(world / 'world.ini').write_text(re.sub(r'(?m)^MODULES=(.*)$', r'MODULES=\1,items.loot,items.choices', ini, count=1))
newchar = 'Y\nN\nHuman\nY\nM\nN\nApprentice\nY\ngood\n\n'
count = 0


def check(condition, name, detail=''):
    global count
    if not condition:
        raise AssertionError(name + ': ' + detail)
    count += 1
    print('FIXTEST ok choices-integration/' + name, flush=True)


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


def options(text):
    """The numbered option lines of a session's output, in order."""
    return [l.strip() for l in text.splitlines() if re.match(r'^\s+\d\) ', l)]


BUILD = """var CM = Packages.com.planet_ink.coffee_mud, CC = CM.core.CMClass, CL = CM.core.CMLib;
var room = CL.map().getRoom('EmberTrial#0');
['seer-table', 'ember-altar'].forEach(function (tag) {
	var S = CC.getItem('GenItem'); S.setName('a ' + tag); S.setDisplayText('A ' + tag + ' stands here.'); S.basePhyStats().setWeight(500); S.recoverPhyStats();
	var P = CC.getAbility('Prop_Workstation'); P.setMiscText('TAG=' + tag); S.addNonUninvokableEffect(P); room.addItem(S);
});
var C = CC.getItem('GenContainer'); C.setName('a seer coffer'); C.setDisplayText('A seer coffer stands here.'); C.setCapacity(500);
C.setDoorsNLocks(true, false, true, false, false, false); C.basePhyStats().setWeight(50); C.recoverPhyStats();
var L = CC.getAbility('Prop_LootTable'); L.setMiscText('FILE=randareas/saved-choices.xml ID=choices_supply ON=OPEN'); C.addNonUninvokableEffect(L);
room.addItem(C);
var A = CC.getItem('GenResource'); A.setName('some ember amber'); A.setMaterial(CM.Items.interfaces.RawMaterial.RESOURCE_AMBER); A.basePhyStats().setWeight(4); A.setBaseValue(4); A.recoverPhyStats(); room.addItem(A);
var T = CC.getArmor('GenArmor'); T.setName('an altar charm'); T.setDisplayText('An altar charm lies here.'); T.recoverPhyStats(); CL.catalog().addCatalog(T); T.destroy();
CL.database().DBUpdateItem('CATALOG_ITEMS', CL.catalog().getCatalogItem('an altar charm'));
CL.database().DBUpdateItems(room);
mob().tell('FIXSETUP seer and altar ready');
"""

try:
    factory.publish(dest)
    check(True, 'trial published with items.loot and items.choices selected')
    creator = world / 'resources/creator'; creator.mkdir(parents=True, exist_ok=True)
    (creator / 'choices-build.js').write_text(BUILD)
    built = play('build', 'Trialbuilder\njrun /resources/creator/choices-build.js\nworld check only=modules,itemmods,loot,choices\nsave\nquit\nY\n', None)
    check('FIXSETUP seer and altar ready' in built and 'WORLD CHECK: 0 errors' in built, 'stations, coffer and catalog item set up', built[-2500:])

    # a new game: two veiled charms; reveal the first, offer the second; save
    first = play('first', 'Seeker\n' + newchar + 'open seer coffer\n#expect You open\nget all from seer coffer\nget amber\n'
                 'choices open choices.seer charm\nchoices open choices.altar 2.charm\nchoices\nsave first\nquit\nn\n', 'NEW')
    norm = ' '.join(first.split())
    check('You reveal veiled modifier 1 of' in norm and 'it is gone' in norm, 'revealed and offered', first[-3000:])
    shown = options(first)
    # the open commands' menus, then CHOICES lists both again: the same lines
    half = len(shown) // 2
    check(half >= 4 and sorted(shown[:half]) == sorted(shown[half:]), 'CHOICES lists what was drawn', '\n'.join(shown))

    # a real restore: the same options; opening the reveal again is refused; each pick settles once
    second = play('second', 'choices\nchoices open choices.seer charm\nchoices pick charm 1\nchoices pick charm 1\nchoices pick offering 1\n'
                  'choices pick offering 1\ninventory\nsave second\nquit\nn\n', 'first')
    again = options(second)
    listed = again[:half]
    check(sorted(listed) == sorted(shown[half:]), 'the same options after the restore', '\n'.join(shown[half:]) + '\n--\n' + '\n'.join(listed))
    norm2 = ' '.join(second.split())
    check('is revealed already' in norm2, 'the reveal is not drawn again', second[-2500:])
    check(norm2.count('You settle') == 1 and 'has no veiled modifier' in norm2, 'the reveal is settled once', second[-2500:])
    check(norm2.count('You take') == 1 and 'no offering is open' in norm2, 'the offering is settled once', second[-2500:])
    print('FIXTEST done choices-integration (%d checks)' % count, flush=True)
except Exception as error:
    print('FIXTEST FAIL choices-integration: ' + str(error), flush=True)
    raise
