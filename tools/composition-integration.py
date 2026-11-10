#!/usr/bin/env python3
"""Actual commands and fresh-process restores, isolated from all real campaigns."""
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys

ROOT = Path(__file__).resolve().parent.parent
GAME = ROOT / 'CoffeeMud'
scratch = Path(sys.argv[1]).resolve()
scratch.mkdir(parents=True, exist_ok=True)
world, db, saves = (scratch / name for name in ('world', 'db', 'saves'))
shutil.copytree(GAME / 'world', world)
db.mkdir()
shutil.copyfile(GAME / 'resources/fakedb/fakedb.schema', db / 'fakedb.schema')
for part in ('modules', 'compositions'):
    shutil.copytree(ROOT / 'SUDDocs/creator/examples/ability-composition' / part, world / part)
# Long-lived snapshots allow several commands/save/quit while keeping both effects underway.
ember = world / 'compositions/example.ember.v1.json'
data = json.loads(ember.read_text())
data['stats']['cooldown'] = 1000
ember.write_text(json.dumps(data))
burning = world / 'compositions/example.burning.v1.json'
data = json.loads(burning.read_text())
data['modifiers'][1]['value'] = 20
data['modifiers'][2]['value'] = 100
burning.write_text(json.dumps(data))
count = 0


def check(condition, name, detail=''):
    global count
    if not condition:
        raise AssertionError(name + ': ' + detail)
    count += 1
    print('FIXTEST ok composition-integration/' + name, flush=True)


def config(enabled):
    (world / 'world.ini').write_text('MODULES=' + ('abilities.composition' if enabled else '') + '\n')


def play(name, script, *settings):
    path = scratch / (name + '.script')
    path.write_text(script)
    with (scratch / (name + '.txt')).open('w') as log:
        result = subprocess.run([sys.executable, str(ROOT / 'tools/sudplay.py'), str(path),
            'DBSERVICE=jdbc:fakedb:' + str(db), 'WORLDDIR=' + str(world), 'SAVEDIR=' + str(saves),
            'TIMEMODE=TURNS', 'RANDOMSEED=42', 'DEV=1', *settings],
            env=dict(os.environ, SUDPLAY_LIMIT='420'), stdout=log, stderr=subprocess.STDOUT, timeout=480)
    text = (scratch / (name + '.txt')).read_text(errors='replace')
    check(result.returncode == 0, name + ' process', text[-3500:])
    check('JS error in JRUN' not in text and 'FIXTEST FAIL' not in text and 'FIXTEST done' in text,
          name + ' assertions', text[-3500:])
    return text


def values(slot):
    modules = json.loads((saves / slot / 'modules.json').read_text())['modules']
    module = next(m for m in modules if m['id'] == 'abilities.composition')
    return next(r['values'] for r in module['records'] if json.loads(r['values']['loadouts']))


try:
    config(True)
    text = play('composed', 'Composer\nY\nN\nHuman\nY\nM\nN\nApprentice\nY\ngood\n\n'
        'jrun /resources/_fixes/composition-setup.js\n'
        'compose learn example.ember\ncompose link example.ember example.chain\n'
        'compose link example.ember example.burning\ncompose link example.ember example.power\n'
        'compose show example.ember\ncompose cast example.ember CinderTarget\nsave composed\nquit\nn\n', 'RESTORE=NEW')
    check('Unconditional result' in text and 'example.power v1' in text, 'actual inspection command explains sources', text[-3000:])
    state = values('composed')
    check(len(json.loads(state['loadouts'])['example.ember']['supports']) == 3, 'actual links saved')
    check('example.ember' in json.loads(state['cooldowns']), 'actual cast stores cooldown')
    check('CompositionBurn' in (saves / 'composed/world.xml').read_text(), 'in-progress burn saved')

    text = play('restored', 'jrun /resources/_fixes/composition-restored.js\n'
        'compose unlink example.ember example.burning\ncompose show example.ember\nsave removed\nquit\nn\n', 'RESTORE=composed')
    state = values('removed')
    check(len(json.loads(state['loadouts'])['example.ember']['supports']) == 2, 'actual unlink persists')
    check('example.ember' in json.loads(state['cooldowns']), 'unlink keeps cooldown')
    check('burn_damage=0' in text, 'unlink recomputes future casts', text[-3000:])

    config(False)
    play('disabled', 'jrun /resources/_fixes/composition-disabled.js\nsave disabled\nquit\nn\n', 'RESTORE=removed')
    state = values('disabled')
    check(len(json.loads(state['loadouts'])['example.ember']['supports']) == 2, 'disabled module retains loadout')
    check('example.ember' in json.loads(state['cooldowns']), 'disabled module retains cooldown')
    check('CompositionBurn' not in (saves / 'disabled/world.xml').read_text(), 'disabled burn removed from subsequent save')
    print('FIXTEST done composition-integration (%d checks)' % count, flush=True)
except Exception as exc:
    print('FIXTEST FAIL composition-integration: ' + str(exc), flush=True)
    raise
