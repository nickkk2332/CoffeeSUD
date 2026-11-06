#!/usr/bin/env python3
"""Real commands and fresh-process transactions over existing generated loot."""
import importlib.util
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parent.parent
pack = ROOT / 'docs/creator/examples/ember-trial'
spec = importlib.util.spec_from_file_location('forge_sample', pack / 'make_world.py')
factory = importlib.util.module_from_spec(spec); spec.loader.exec_module(factory)
scratch = Path(sys.argv[1]).resolve(); scratch.mkdir(parents=True, exist_ok=True)
dest = factory.create(scratch / 'deployment')
count = 0


def check(condition, name, detail=''):
    global count
    if not condition:
        raise AssertionError(name + ': ' + detail)
    count += 1
    print('FIXTEST ok item-integration/' + name, flush=True)


def play(name, commands, restore, expected=()):
    """A session of the game; restore None for a builder session (the publish's own character and saves)."""
    path = scratch / (name + '.input'); path.write_text(commands)
    with (scratch / (name + '.txt')).open('w') as output:
        result = subprocess.run([sys.executable, str(ROOT / 'tools/sudplay.py'), str(path),
            'WORLDDIR=' + str(dest / 'world'), 'DBSERVICE=jdbc:fakedb:' + str(dest / 'db'),
            'SAVEDIR=' + str(dest / ('.build/saves' if restore is None else 'saves')),
            'BUILDER=1' if restore is None else 'RESTORE=' + restore, 'TIMEMODE=TURNS', 'RANDOMSEED=42'],
            cwd=ROOT, env=dict(os.environ, SUDPLAY_LIMIT='480'), stdout=output, stderr=subprocess.STDOUT, timeout=540)
    text = (scratch / (name + '.txt')).read_text(errors='replace')
    check(result.returncode == 0, name + ' process', text[-2500:])
    check('JS error' not in text and 'JavaScript error' not in text, name + ' scripts', text[-2500:])
    normal = ' '.join(text.split())
    for marker in expected:
        check(marker in normal, name + ' / ' + marker, text[-2500:])
    return text


# A mob whose SPAWN loot is tracked: its life begins as the world loads, before the game thread runs.
SPAWN_SETUP = """var CM = Packages.com.planet_ink.coffee_mud, CC = CM.core.CMClass, CL = CM.core.CMLib;
var room = CL.map().getRoom('EmberTrial#1'), warden = CC.getMOB('GenMob');
warden.setName('a spawn warden'); warden.setDisplayText('A spawn warden keeps watch here.');
warden.basePhyStats().setLevel(1); warden.basePhyStats().setRejuv(CM.Common.interfaces.PhyStats.NO_REJUV); warden.recoverPhyStats();
warden.setStartRoom(room); warden.bringToLife(room, true);
var loot = CC.getAbility('Prop_LootTable'); // added after this life began: the next boot rolls it
loot.setMiscText('FILE=randareas/embertrial-loot.xml ID=embertrial_mantle ON=SPAWN'); warden.addNonUninvokableEffect(loot);
CL.database().DBUpdateMOBs(room);
mob().tell('FIXSETUP spawn warden carries ' + warden.numItems());
"""
SPAWN_CHECK = """var CM = Packages.com.planet_ink.coffee_mud, IM = CM.core.ItemModifiers;
var room = CM.core.CMLib.map().getRoom('EmberTrial#1'), warden = null, tracked = 0;
for (var i = 0; i < room.numInhabitants(); i++) if (String(room.fetchInhabitant(i).Name()) === 'a spawn warden') warden = room.fetchInhabitant(i);
if (warden != null) for (var j = 0; j < warden.numItems(); j++) if (IM.isManaged(warden.getItem(j)) && IM.data(warden.getItem(j)).get('modifiers').length == 2) tracked++;
mob().tell('FIXBOOT spawn warden ' + (warden == null ? 'missing' : 'carries ' + tracked + ' tracked of ' + warden.numItems()));
"""


def module(slot):
    data = json.loads((dest / 'saves' / slot / 'modules.json').read_text())
    return next(m for m in data['modules'] if m['id'] == 'items.modifiers')


def ledger(slot):
    records = module(slot)['records']
    return records[0]['values'] if records else {'serial': 0, 'settled': 0, 'pending': '', 'receipts': '{}'}


def saved_items(slot):
    root = ET.fromstring((dest / 'saves' / slot / 'world.xml').read_text())
    result = []
    for item in root.findall('PLAYER/INVENTORY/ITEM'):
        generic = ET.fromstring('<GEN>' + (item.findtext('TEXT') or '') + '</GEN>')
        mods = None
        for effect in generic.findall('AFFECS/AFF'):
            if effect.findtext('ACLASS') == 'Prop_ItemModifiers':
                mods = json.loads(effect.findtext('ATEXT'))
        prop = generic.find('PROP')
        weight = int(prop.get('WEIGHT')) if prop is not None and prop.get('WEIGHT') else int(prop.text.split('|')[8]) if prop is not None else 0
        result.append({'class': item.findtext('CLASS'), 'name': generic.findtext('NAME'), 'weight': weight, 'record': mods})
    return result


def item_record(slot):
    return next(i['record'] for i in saved_items(slot) if i['record'])


def iron(slot):
    return sum(i['weight'] for i in saved_items(slot) if i['class'] == 'GenResource')


try:
    factory.publish(dest)
    check(True, 'sample published using existing templates, loot and station')
    play('prepared', 'Forgester\nY\nN\nHuman\nY\nM\nN\nApprentice\nY\ngood\n\n'
         'save unopened\nopen supply coffer\nget "trial mantle" coffer\nget "cinder iron" coffer\n'
         'wear trial mantle\ncombatresult stats\nremove trial mantle\n'
         'itemmods trial mantle\nmodcraft list\nmodcraft pool "trial mantle"\nmodcraft preview embertrial.reroll "trial mantle" 1\n'
         'modcraft prepare embertrial.reroll "trial mantle" 1\nsave prepared\nquit\nn\n', 'NEW',
         ['source embertrial.', 'Affix pool for', 'exclusive group', 'No inputs spent or random outcomes drawn.', 'Prepared craft #1'])
    original = item_record('prepared')
    check(len(original['modifiers']) == 2 and iron('prepared') == 8, 'generated rolls and all materials saved before payment')
    check(json.loads(ledger('prepared')['pending'])['serial'] == 1 and ledger('prepared')['settled'] == 0, 'prepared request survives in native module state')

    play('committed', 'modcraft status\nmodcraft commit 1\nmodcraft commit 1\n'
         'modcraft prepare embertrial.protect "trial mantle" 1\nsave committed\nquit\nn\n', 'prepared',
         ['Pending craft #1', 'Committed craft #1', 'Already settled craft #1', 'Prepared craft #2'])
    once = item_record('committed')
    check(iron('committed') == 7 and once['revision'] == 1, 'restored commit charges exactly once')
    check(once['modifiers'][0]['uid'] == original['modifiers'][0]['uid'] and once['modifiers'][1] == original['modifiers'][1], 'selective reroll preserves identities and other roll')

    play('protected', 'modcraft commit 2\nmodcraft prepare embertrial.reroll "trial mantle" 1\n'
         'modcraft prepare embertrial.remove "trial mantle" 1\n'
         'modcraft prepare embertrial.reroll "trial mantle" 2\nmodcraft commit 3\nitemmods trial mantle\n'
         'save protected\nquit\nn\n', 'committed', ['Committed craft #2', 'modifier is protected', 'Committed craft #3', '[protected]'])
    locked = item_record('protected')
    check(iron('protected') == 5 and locked['modifiers'][0]['protected'], 'failed protected edits consume neither a request nor materials')
    check(locked['modifiers'][0]['values'] == once['modifiers'][0]['values'], 'protected roll unchanged while another modifier rerolls')
    replaced = locked['modifiers'][0]['pin']['id']

    play('rebuilt', 'modcraft prepare embertrial.unprotect "trial mantle" 1\nmodcraft commit 4\n'
         'modcraft prepare embertrial.remove "trial mantle" 1\nmodcraft commit 5\n'
         'modcraft prepare embertrial.add "trial mantle" ' + replaced + '\nmodcraft commit 6\n'
         'modcraft prepare embertrial.protect "trial mantle" 1\nmodcraft commit 7\n'
         'close supply coffer\nopen supply coffer\nget all coffer\nitemmods trial mantle\n'
         'save rebuilt\nquit\nn\n', 'protected', ['Committed craft #4', 'Committed craft #5', 'Committed craft #6', 'Committed craft #7'])
    rebuilt = item_record('rebuilt')
    check(iron('rebuilt') == 0 and rebuilt['revision'] == 7, 'partial and whole resource consumption reconcile with item revisions')
    check(len([i for i in saved_items('rebuilt') if i['record']]) == 1, 'reopening saved loot coffer cannot duplicate generated equipment')
    check(len(rebuilt['modifiers']) == 2 and all(m['uid'] != locked['modifiers'][0]['uid'] for m in rebuilt['modifiers']), 'remove/add creates a new modifier identity')

    play('replayed', 'modcraft commit 7\nmodcraft receipt 7\nitemmods trial mantle\nsave replayed\nquit\nn\n', 'rebuilt',
         ['Already settled craft #7', 'embertrial.protect'])
    check(item_record('replayed') == rebuilt and iron('replayed') == 0, 'repeated completion after fresh restore has no economic effect')
    ini = dest / 'world/world.ini'; configured = ini.read_text()
    ini.write_text(configured.replace('MODULES=embertrial.adventure', 'MODULES=combat.profiles'))
    play('disabled', 'close supply coffer\nopen supply coffer\nitemmods trial mantle\nmodcraft status\nsave disabled\nquit\nn\n', 'replayed', ['bonuses/crafting suspended', 'items.modifiers is inactive'])
    check(item_record('disabled') == rebuilt and module('disabled')['requires_handler'] == 'items.modifiers', 'suspension preserves rolled data and native reader requirement')
    ini.write_text(configured)
    play('resumed', 'itemmods trial mantle\nmodcraft commit 7\nsave resumed\nquit\nn\n', 'disabled', ['Already settled craft #7'])
    check(item_record('resumed') == rebuilt and ledger('resumed')['settled'] == 7, 'reactivation retains transaction settlement and exact rolls')
    play('fresh', 'Freshforge\nY\nN\nHuman\nY\nM\nN\nApprentice\nY\ngood\n\n'
         'open supply coffer\nget "trial mantle" coffer\nget "cinder iron" coffer\nsave fresh\nquit\nn\n', 'NEW')
    fresh = item_record('fresh')
    check(fresh['item'] != rebuilt['item'] and fresh['revision'] == 0 and iron('fresh') == 8, 'new campaign starts with fresh item identity, loot and costs')
    check(ledger('fresh')['serial'] == 0, 'new campaign inherits no pending or settled requests')
    ini.write_text(configured.replace('MODULES=embertrial.adventure', 'MODULES=combat.profiles'))
    play('suspended-loot', 'open supply coffer\nsave suspended-loot\nquit\nn\n', 'unopened',
         ['Its loot remains unspent.'])
    check('ROLLED=TRUE' not in (dest / 'saves/suspended-loot/world.xml').read_text(), 'inactive module leaves unopened tracked loot unspent')
    ini.write_text(configured)
    play('resumed-loot', 'open supply coffer\nget "trial mantle" coffer\nget "cinder iron" coffer\n'
         'save resumed-loot\nquit\nn\n', 'suspended-loot')
    check(len(item_record('resumed-loot')['modifiers']) == 2 and iron('resumed-loot') == 8,
          'reactivated module can claim previously suspended coffer normally')
    creator = dest / 'world/resources/creator'
    (creator / 'fix-spawn.js').write_text(SPAWN_SETUP); (creator / 'fix-spawn-check.js').write_text(SPAWN_CHECK)
    play('spawn-setup', 'Trialbuilder\njrun /resources/creator/fix-spawn.js\nsave\nquit\nY\n', None, ['FIXSETUP spawn warden carries 0'])
    play('spawn-boot', 'Trialbuilder\njrun /resources/creator/fix-spawn-check.js\nquit\nY\n', None,
         ['FIXBOOT spawn warden carries 1 tracked of 1'])  # its tracked SPAWN loot was made at boot
    print('FIXTEST done item-integration (%d checks)' % count, flush=True)
except Exception as error:
    print('FIXTEST FAIL item-integration: ' + str(error), flush=True)
    raise
