#!/usr/bin/env python3
"""Opt-in profile commands, equipment and in-flight burns across fresh processes."""
import hashlib
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
for pack, parts in [('ability-composition', ('modules', 'compositions')),
                    ('combat-profiles', ('modules', 'combat'))]:
    for part in parts:
        shutil.copytree(ROOT / 'SUDDocs/creator/examples' / pack / part, world / part, dirs_exist_ok=True)
# Stable thresholds and long-lived effects are fixture changes, not world defaults.
for profile in (world / 'combat').glob('*.json'):
    data = json.loads(profile.read_text())
    data['hit'] = {'minimum': 100, 'maximum': 100}
    profile.write_text(json.dumps(data))
profile = json.loads((world / 'combat/example.final.v1.json').read_text())
pin = {'id': profile['id'], 'version': 1, 'hash': hashlib.sha256(
    json.dumps(profile, sort_keys=True, separators=(',', ':')).encode()).hexdigest()}
manifest = world / 'modules/combat.profiles.json'
data = json.loads(manifest.read_text())
data['settings']['profile']['default'] = json.dumps(pin, separators=(',', ':'))
manifest.write_text(json.dumps(data))
ember = world / 'compositions/example.ember.v1.json'
data = json.loads(ember.read_text()); data['stats']['cooldown'] = 1000
ember.write_text(json.dumps(data))
burn = world / 'compositions/example.burning.v1.json'
data = json.loads(burn.read_text())
for mod, value in zip(data['modifiers'], [10, 20, 100, 2]):
    mod['value'] = value
burn.write_text(json.dumps(data))
count = 0


def check(condition, name, detail=''):
    global count
    if not condition:
        raise AssertionError(name + ': ' + detail)
    count += 1
    print('FIXTEST ok combat-integration/' + name, flush=True)


def play(name, script, restore):
    path = scratch / (name + '.script'); path.write_text(script)
    with (scratch / (name + '.txt')).open('w') as log:
        result = subprocess.run([sys.executable, str(ROOT / 'tools/sudplay.py'), str(path),
            'DBSERVICE=jdbc:fakedb:' + str(db), 'WORLDDIR=' + str(world), 'SAVEDIR=' + str(saves),
            'TIMEMODE=TURNS', 'RANDOMSEED=42', 'DEV=1', 'RESTORE=' + restore],
            env=dict(os.environ, SUDPLAY_LIMIT='420'), stdout=log, stderr=subprocess.STDOUT, timeout=480)
    text = (scratch / (name + '.txt')).read_text(errors='replace')
    check(result.returncode == 0, name + ' process', text[-3500:])
    check('JS error in JRUN' not in text and 'FIXTEST FAIL' not in text and 'FIXTEST done' in text,
          name + ' assertions', text[-3500:])
    return text


def module(slot):
    modules = json.loads((saves / slot / 'modules.json').read_text())['modules']
    return next(m for m in modules if m['id'] == 'combat.profiles')


try:
    (world / 'world.ini').write_text('MODULES=combat.profiles\n')
    text = play('profiled', 'Profiler\nY\nN\nHuman\nY\nM\nN\nApprentice\nY\ngood\n\n'
        'jrun /resources/_fixes/combat-setup.js\n'
        'compose learn example.ember\ncompose link example.ember example.burning\n'
        'compose show example.ember\ncompose cast example.ember ProfileTarget\ncombatresult\ncombatresult 2\n'
        'world combat use example.history 1\nsave profiled\nquit\nn\n', 'NEW')
    check('HP removed 8' in text and 'Profile example.final.v1' in text,
          'actual cast and inspection commands', text[-3500:])
    check(json.loads(module('profiled')['settings']['profile'])['id'] == 'example.history',
          'actual selection command saved')
    check('CompositionBurn' in (saves / 'profiled/world.xml').read_text(), 'profile burn saved')

    # Merely installing v2 must not change the saved v1 profile selection.
    data = json.loads((world / 'combat/example.history.v1.json').read_text())
    data['version'] = 2
    (world / 'combat/example.history.v2.json').write_text(json.dumps(data))
    play('restored', 'jrun /resources/_fixes/combat-restored.js\nsave restored\nquit\nn\n', 'profiled')
    check(json.loads(module('restored')['settings']['profile'])['version'] == 1,
          'new-file-does-not-upgrade-saved-pin')
    (world / 'world.ini').write_text('MODULES=abilities.composition\n')
    play('disabled', 'jrun /resources/_fixes/combat-disabled.js\nsave disabled\nquit\nn\n', 'restored')
    check(module('disabled')['requires_handler'] == 'combat.profiles', 'suspended capability saved')
    check(json.loads(module('disabled')['settings']['profile'])['id'] == 'example.history', 'suspended selection retained')
    check('CompositionBurn' not in (saves / 'disabled/world.xml').read_text(), 'disabled burn removed')
    print('FIXTEST done combat-integration (%d checks)' % count, flush=True)
except Exception as exc:
    print('FIXTEST FAIL combat-integration: ' + str(exc), flush=True)
    raise
