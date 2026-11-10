#!/usr/bin/env python3
"""Fresh-process module persistence, opt-in, migration, and refusal regressions."""
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
shutil.copytree(ROOT / 'SUDDocs/creator/examples/optional-modules/modules', world / 'modules')
count = 0
newchar = 'Y\nN\nHuman\nY\nM\nN\nApprentice\nY\ngood\n\n'


def check(condition, name, detail=''):
    global count
    if not condition:
        raise AssertionError(name + ': ' + detail)
    count += 1
    print('FIXTEST ok modules-integration/' + name, flush=True)


def config(enabled='', step=2):
    (world / 'world.ini').write_text('MODULES=' + enabled + '\nMODULESETTINGS=' +
        json.dumps({'example.counter': {'step': step}}, separators=(',', ':')) + '\n')


def play(name, script, *settings, expected=0):
    path = scratch / (name + '.script')
    path.write_text(script)
    with (scratch / (name + '.txt')).open('w') as log:
        result = subprocess.run([sys.executable, str(ROOT / 'tools/sudplay.py'), str(path),
            'DBSERVICE=jdbc:fakedb:' + str(db), 'WORLDDIR=' + str(world),
            'SAVEDIR=' + str(saves), 'TIMEMODE=TURNS', 'RANDOMSEED=42', *settings],
            env=dict(os.environ, SUDPLAY_LIMIT='420'), stdout=log, stderr=subprocess.STDOUT, timeout=480)
    text = (scratch / (name + '.txt')).read_text(errors='replace')
    check(result.returncode == expected, name + ' process', text[-3000:])
    check('JS error in JRUN' not in text and 'FIXTEST FAIL' not in text, name + ' assertions', text[-3000:])
    return text


def module(slot, name='example.counter'):
    data = json.loads((saves / slot / 'modules.json').read_text())
    return next(item for item in data['modules'] if item['id'] == name)


def field(slot, name='count'):
    return module(slot)['records'][0]['values'][name]


def digest(slot):
    return {str(p.relative_to(saves / slot)): hashlib.sha256(p.read_bytes()).hexdigest()
            for p in (saves / slot).rglob('*') if p.is_file()}


try:
    config()
    text = play('off', 'Modtester\n' + newchar + 'modules\nmodules demo example.counter\nsave legacy\nquit\nn\n', 'RESTORE=NEW')
    check('disabled by the world' in text, 'disabled demo has no effect', text[-1800:])
    check(not (saves / 'legacy/modules.json').exists(), 'unmodified worlds keep legacy save layout')

    config('example.counter')
    text = play('legacy', 'modules\nmodules demo example.counter\nsave old\nquit\nn\n', 'RESTORE=legacy')
    check('not adopted' in text, 'new world module not forced onto old campaign', text[-2000:])
    check('WORLD MODULES ADOPT' not in text and 'another version of the world' not in text,
          'no adoption hint for a player who cannot adopt', text[-2000:])
    check(not (saves / 'old/modules.json').exists(), 'legacy campaign remains module free')

    text = play('adopt', 'world modules adopt example.counter\nmodules demo example.counter\nsave adopted\nquit\nn\n', 'RESTORE=old', 'DEV=1')
    check('Practice journal: 2.' in text, 'explicit adoption invokes real module', text[-2000:])
    check('available for explicit WORLD MODULES ADOPT' in text, 'adoption hint kept for DEV', text[-2000:])
    check(field('adopted') == 2, 'adopted state saved by character identity')
    # A fresh normal player can use the module, without developer or builder commands.
    text = play('active', 'Modtester\n' + newchar + 'modules demo example.counter\nmodules demo example.counter\nworld modules\npause\nsave active\nquit\nn\n', 'RESTORE=NEW')
    check('Practice journal: 4.' in text, 'ordinary player runs configured counter', text[-2000:])
    check('WORLD MODULES is for builder mode or a DEV=1 game' in text, 'creator inspection permission enforced')
    check(field('active') == 4 and module('active')['settings']['step'] == 2, 'state and effective settings captured')
    check('version=2\n' in (saves / 'active/header.properties').read_text(), 'module saves require a module-aware reader')

    config('example.counter', 9)
    text = play('pinned', 'modules\nmodules demo example.counter\nsave pinned\nquit\nn\n', 'RESTORE=active')
    check('Practice journal: 6.' in text, 'fresh-process restore retains saved settings', text[-2000:])
    check(field('pinned') == 6 and module('pinned')['settings']['step'] == 2, 'world edits do not rewrite campaign rules')
    owners = module('pinned')['records']
    check(len(owners) == 1 and owners[0]['owner'].startswith('@instance:'), 'persistent identity survives fresh process')

    config()
    text = play('suspended', 'modules\nmodules demo example.counter\nsave suspended\nquit\nn\n', 'RESTORE=pinned')
    check('SUSPENDED' in text and 'not enabled' in text, 'disabled module suspended visibly', text[-2000:])
    check(field('suspended') == 6, 'disabled state is retained in subsequent save')

    config('example.counter')
    text = play('resumed', 'modules demo example.counter\nsave resumed\nquit\nn\n', 'RESTORE=suspended')
    check('Practice journal: 8.' in text and field('resumed') == 8, 're-enable resumes preserved state', text[-2000:])

    text = play('configured', 'world modules configure example.counter step 3\nworld modules configure example.counter step 99\nmodules demo example.counter\nsave configured\nquit\nn\n', 'RESTORE=resumed', 'DEV=1')
    check('Practice journal: 11.' in text and field('configured') == 11 and module('configured')['settings']['step'] == 3,
          'explicit campaign setting is typed and invalid change is atomic', text[-2000:])

    # A broken draft the selection does not use, a stale world affix file and a leftover setting stop no
    # game; the save's module notes are not world changes; an Archon of a normal game cannot rewrite rules.
    (world / 'modules/draft.json').write_text('{"schema":1,')
    (world / 'affixes.xml').write_text('<AFFIXES></AFFIXES>\n')
    (world / 'world.ini').write_text('MODULES=example.counter\nMODULESETTINGS={"example.counter":{"step":2},"gone.module":{}}\n')
    text = play('report', 'get book\nread book\nworld modules check\nworld modules configure example.counter step 4\n'
                'world modules adopt example.counter\nworld modules reload\nmodules demo example.counter\nquit\nn\n', 'RESTORE=configured')
    check('another version of the world' not in text and 'FRESH=CHANGED' not in text,
          'module notes and module files are not world changes', text[-3000:])
    check('saved settings retained' not in text, 'creator-only module notes kept from a normal player', text[-3000:])
    check('modules/draft.json' in text and 'unknown module gone.module' in text and 'Warnings' in text,
          'unselected problems shown as warnings', text[-3000:])
    words = ' '.join(text.split())  # the game wraps long lines
    check(words.count('changes this campaign\'s modules: it is for builder mode or a DEV=1 game') == 3,
          'rule changes refused to a normal game\'s Archon', text[-3000:])
    check('Practice journal: 14.' in text, 'refused configure keeps the campaign setting', text[-3000:])
    (world / 'modules/draft.json').unlink()
    (world / 'affixes.xml').unlink()
    text = play('report-dev', 'quit\nn\n', 'RESTORE=configured', 'DEV=1')
    check('saved settings retained' in text and 'another version of the world' not in text,
          'creator notes reported apart in a DEV game', text[-3000:])
    config('example.counter')

    path = world / 'modules/example.counter.json'
    definition = json.loads(path.read_text())
    definition['version'] = 2
    definition['handler'] = 'data'
    definition['state'] = {'total': definition['state']['count']}
    definition['migrations'] = [{'from': 1, 'to': 2, 'state_renames': {'count': 'total'}}]
    path.write_text(json.dumps(definition))
    text = play('migrated', 'modules\nworld modules plan\nsave migrated\nquit\nn\n', 'RESTORE=configured', 'DEV=1')
    check('migrated v1 to v2' in text, 'migration reported to player', text[-3000:])
    check(module('migrated')['version'] == 2 and field('migrated', 'total') == 11, 'renamed state survives actual restore')
    check('count' not in module('migrated')['records'][0]['values'], 'old field transformed once')

    definition['version'] = 3
    definition.pop('migrations')
    path.write_text(json.dumps(definition))
    text = play('unmapped', 'modules\nsave unmapped\nquit\nn\n', 'RESTORE=migrated')
    check('no migration' in text and module('unmapped')['version'] == 2 and field('unmapped', 'total') == 11,
          'missing migration suspends original payload without partial conversion', text[-2500:])

    (world / 'world.ini').write_text('MODULES=\nMODULESETTINGS={}\n')
    path.unlink()
    text = play('removed', 'modules\nsave removed\nquit\nn\n', 'RESTORE=unmapped')
    check('definition removed' in text and field('removed', 'total') == 11,
          'removed definition retains capsule through actual restore', text[-2000:])

    definition = json.loads((ROOT / 'SUDDocs/creator/examples/optional-modules/modules/example.counter.json').read_text())
    config('example.counter')
    definition['on_disable'] = 'refuse'
    path.write_text(json.dumps(definition))
    text = play('protected', 'Modtester\n' + newchar + 'modules demo example.counter\nsave protected\nquit\nn\n', 'RESTORE=NEW')
    check(field('protected') == 2, 'refusal-policy campaign saved')
    before = digest('protected')
    config()
    text = play('refused', '', 'RESTORE=protected', expected=1)
    check('refused' in text and 'example.counter' in text, 'incompatible restore refused before boot', text)
    check(digest('protected') == before, 'refusal does not modify source save')

    text = play('invalid-check', '', 'CHECK=1', 'MODULES=missing.module', expected=1)
    check('unknown module missing.module' in text, 'headless world check diagnoses invalid module selection', text[-2000:])

    # A second world in this process tree must not inherit the selected modules.
    other = scratch / 'other-world'
    shutil.copytree(GAME / 'world', other)
    text = play('second-world', 'Othermod\n' + newchar + 'modules\nsave secondworld\nquit\nn\n',
                'RESTORE=NEW', 'WORLDDIR=' + str(other))
    check('example.counter' not in text and not (saves / 'secondworld/modules.json').exists(), 'separate world remains unaffected')
    print('FIXTEST done modules-integration (%d checks)' % count, flush=True)
except Exception as exc:
    print('FIXTEST FAIL modules-integration: ' + str(exc), flush=True)
    raise
