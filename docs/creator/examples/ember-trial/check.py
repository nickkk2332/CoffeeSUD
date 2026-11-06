#!/usr/bin/env python3
"""Publish, play and restore the sample using ordinary characters, not DEV grants."""
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import tempfile
import xml.etree.ElementTree as ET

from make_world import HERE, ROOT, create, publish


def main():
    scratch = Path(tempfile.mkdtemp(prefix='sud-ember-trial.'))
    dest = create(scratch / 'deployment')
    count = 0
    passed = False

    def check(condition, name, detail=''):
        nonlocal count
        if not condition:
            raise AssertionError(name + ': ' + detail)
        count += 1
        print('FIXTEST ok ember-trial/' + name, flush=True)

    def sums(folder):
        return {str(p.relative_to(folder)): hashlib.sha256(p.read_bytes()).hexdigest()
                for p in folder.rglob('*') if p.is_file()}

    def play(name, commands, restore, expected=()):
        script = scratch / (name + '.input'); script.write_text(commands, encoding='utf-8')
        env = dict(os.environ, SUDPLAY_LIMIT='480', SUDPLAY_EXPECT_LIMIT='30')
        env['JAVA_TOOL_OPTIONS'] = '-Djava.awt.headless=true ' + env.get('JAVA_TOOL_OPTIONS', '')
        with (scratch / (name + '.txt')).open('w', encoding='utf-8') as log:
            result = subprocess.run([sys.executable, str(ROOT / 'tools/sudplay.py'), str(script),
                'WORLDDIR=' + str(dest / 'world'), 'DBSERVICE=jdbc:fakedb:' + str(dest / 'db'),
                'SAVEDIR=' + str(dest / 'saves'), 'TIMEMODE=TURNS', 'RANDOMSEED=42', 'RESTORE=' + restore],
                cwd=ROOT, env=env, stdout=log, stderr=subprocess.STDOUT, timeout=540)
        text = (scratch / (name + '.txt')).read_text(encoding='utf-8', errors='replace')
        check(result.returncode == 0, name + ' process', text[-2500:])
        check(not re.search(r'JS error|JavaScript error|Script error', text, re.I), name + ' script execution', text[-2500:])
        # Game output wraps at the console width; match normalized text.
        normalized = ' '.join(text.split())
        for marker in expected:
            check(marker in normalized, name + ' / ' + marker, text[-2500:])
        return text

    def capsule(slot, name):
        data = json.loads((dest / 'saves' / slot / 'modules.json').read_text())
        return next(m for m in data['modules'] if m['id'] == name)

    def records(slot, name='embertrial.adventure'):
        return [r['values'] for r in capsule(slot, name)['records']]

    def hero_state(slot):
        return next(r for r in records(slot) if r['claimed'])

    character = 'Trialhero\nY\nN\nHuman\nY\nM\nN\nApprentice\nY\ngood\n\n'
    try:
        original = sums(ROOT / 'CoffeeMud/world')
        publish(dest)
        check((dest / '.ember-trial').exists(), 'builder published the seven-room adventure')
        published = sums(dest / 'world')
        refused = False
        try:
            create(dest)
        except FileExistsError:
            refused = True
        check(refused, 'creator tool refuses an existing destination')

        play('practice', character + 'trial help\ntrial status\nworld combat list\nnorth\nnorth\nsouth\neast\n'
             'compose link embertrial.ember embertrial.burning\n'
             'compose cast embertrial.ember ward effigy\ncombatresult\ncombatresult 2\n'
             'save practice\nquit\nn\n', 'NEW', [
                 'THE EMBER TRIAL', 'TRIAL STATUS: your expedition journal.',
                 'WORLD COMBAT is for builder mode or a DEV=1 game.',
                 'Clear both ash creatures before approaching the slag gate.',
                 'world-approved 12, HP removed 12', 'world-approved 3, HP removed 3'])
        xml = (dest / 'saves/practice/world.xml').read_text()
        check('CompositionBurn' in xml and 'example.final' in xml, 'saved training burn has a pinned profile')
        creatures = {}
        for m in ET.fromstring(xml).iter('MOB'):
            name = ET.fromstring('<GEN>' + (m.findtext('TEXT') or '') + '</GEN>').findtext('NAME')
            if name:
                creatures[name] = int(m.findtext('HP'))
        check(creatures.get('Ashling Red') == 42 and creatures.get('Slag Sentinel') == 52
              and creatures.get('Furnace Warden') == 100, 'new campaign initializes the authored encounter HP budgets')
        check(capsule('practice','combat.profiles')['requires_handler'] == 'combat.profiles', 'native handler requirement saved')

        tour = ('save checkpoint\ntrial status\ncompose show embertrial.ember\nwait 1\ncombatresult\ntrial leave\ntrial rest\n'
                'trial rules history\ncompose show embertrial.transmute\ntrial rules final\ncompose show embertrial.transmute\n'
                'say lessons\n#expect Choose a lesson:\n1\n'
                'compose link embertrial.ember embertrial.chain\ncompose link embertrial.ember embertrial.power\n'
                'north\ncompose cast embertrial.ember Ashling Red\nwait 2\ntrial status\n'
                'east\nget ember mantle\nget quilted mantle\nget piercing signet\nwear quilted mantle\nwear piercing signet\ncombatresult stats\n'
                'west\nnorth\ncompose cast embertrial.ember Slag Sentinel\nwait 2\ntrial status\n'
                'remove quilted mantle\nwear ember mantle\ncombatresult stats\nnorth\n'
                + ('compose cast embertrial.ember Furnace Warden\nwait 2\n' * 5)
                + 'trial status\nnorth\ntrial claim\ntrial claim\nsouth\nsouth\nsouth\nsouth\ntrial rest\n'
                + 'trial rules history\nnorth\nnorth\nnorth\nnorth\nsave victory\nquit\nn\n')
        play('adventure', tour, 'practice', [
            'Ember Trial restored:', 'embertrial.burning v1', 'HP removed 3',
            'damage.fire=54', 'damage.fire=44', 'The cinder road is clear.',
            'armor=120', 'penetration.fire=20', 'Slag Sentinel: defeated',
            'resistance.fire=50', 'Furnace Warden: defeated', 'Cinder spit', 'Slag hammer',
            'EMBER TRIAL COMPLETE', 'already claimed your cooling heart'])
        restored_creatures = {}
        restored = ET.fromstring((dest / 'saves/checkpoint/world.xml').read_text())
        for m in restored.iter('MOB'):
            name = ET.fromstring('<GEN>' + (m.findtext('TEXT') or '') + '</GEN>').findtext('NAME')
            if name:
                restored_creatures[name] = int(m.findtext('HP'))
        check(restored_creatures == creatures, 'restore preserves encounter HP and training wounds before another tick')
        done = next(json.loads(r['cleared']) for r in records('victory') if r['cleared'] != '{}')
        check(all(done.get(key) for key in ('RED','BLUE','SENTINEL','WARDEN')), 'all authored encounters cleared through normal play')
        check(hero_state('victory')['initialized'], 'character-owned completion record saved')
        check(json.loads(capsule('victory','combat.profiles')['settings']['profile'])['id'] == 'example.history',
              'camp profile choice persists without changing world defaults')
        loadouts = [json.loads(r['loadouts']) for r in records('victory','abilities.composition')]
        check(any(len(r.get('embertrial.ember',{}).get('supports',[])) == 3 for r in loadouts), 'three-support player build saved')

        play('victory', 'trial status\ntrial claim\ninventory\nsave returned\nquit\nn\n', 'victory', [
            'Cooling heart: claimed - expedition complete', 'already claimed your cooling heart', 'The cooling heart'])
        check(hero_state('returned')['claimed'], 'victory survives fresh process')
        # One regular item reward, not an endlessly reclaimable pickup.
        saved = ET.fromstring((dest / 'saves/returned/world.xml').read_text())
        names = [ET.fromstring('<GEN>' + (i.findtext('TEXT') or '') + '</GEN>').findtext('NAME')
                 for i in saved.findall('PLAYER/INVENTORY/ITEM')]
        check(names.count('the cooling heart') == 1,
              'repeat claim after restore does not duplicate the keepsake')

        # Turn off this root, retaining the child definitions/files. Restore must
        # suspend the adventure data and its native dependencies without regranting.
        ini = dest / 'world/world.ini'; before = ini.read_text()
        ini.write_text(before.replace('MODULES=embertrial.adventure','MODULES='))
        play('disabled', 'trial status\nsave disabled\nquit\nn\n', 'returned', ['The Ember Trial module is not active here.'])
        check(hero_state('disabled')['claimed'], 'disabled adventure retains character progress')
        ini.write_text(before)
        play('resumed', 'trial status\nsave resumed\nquit\nn\n', 'disabled', ['Cooling heart: claimed - expedition complete'])
        check(hero_state('resumed')['claimed'], 'reenabling restores the saved adventure state')

        fresh = character.replace('Trialhero','Freshtrial')
        play('fresh', fresh + 'trial status\ncompose show embertrial.ember\n'
             'north\nkill Ashling Red\n' + ('wait 1\n' * 40) + 'trial status\ntrial rest\nsave rescued\nquit\nn\n', 'NEW', [
                 'Defeat both ash creatures', 'Cooling heart: unclaimed', 'Rescues: 0. Profile: example.final.v1',
                 'The keeper pulls you back to camp', 'Rescues: 1.', 'The brazier restores your health'])
        fresh_state = records('rescued')
        check(all(not r['claimed'] for r in fresh_state), 'new campaign does not inherit completion')
        check(any(r['rescues'] == 1 for r in fresh_state), 'sample death safety saves its rescue count')
        check(sums(dest / 'world') == published, 'normal campaigns never modify published world content')
        check(sums(ROOT / 'CoffeeMud/world') == original, 'starter world remains unchanged')
        passed = True
        print('FIXTEST done ember-trial (%d checks)' % count, flush=True)
        return 0
    except (AssertionError, OSError, RuntimeError, subprocess.TimeoutExpired, StopIteration) as error:
        print('FIXTEST FAIL ember-trial: ' + str(error), flush=True)
        return 1
    finally:
        if passed and os.environ.get('KEEP') != '1':
            shutil.rmtree(scratch)
        else:
            print('Artifacts: ' + str(scratch), flush=True)


if __name__ == '__main__':
    sys.exit(main())
