#!/usr/bin/env python3
"""Q04: tools/simulate.py on the example scenarios and their variants: the same seeds give the same runs, a boss that
takes no damage stalls, the turn and work limits stop a batch, and the report keeps the sampled results apart from
the combat profile's exact figures. A boss that acts fights back with its composed skill and combat rounds: its elite
trait is applied, the guard pool, ailments, combos and roles are counted, a frail caster falls, and the same seeds
give the same runs."""
import json
from pathlib import Path
import subprocess
import sys

ROOT = Path(__file__).resolve().parent.parent
EXAMPLE = ROOT / 'SUDDocs/creator/examples/simulation/ember-vs-strike.json'
ACTING = ROOT / 'SUDDocs/creator/examples/simulation/frost-warden.json'
scratch = Path(sys.argv[1]).resolve()
scratch.mkdir(parents=True, exist_ok=True)
count = 0


def check(condition, name, detail=''):
    global count
    if not condition:
        raise AssertionError(name + ': ' + detail)
    count += 1
    print('FIXTEST ok simulate/' + name, flush=True)


def simulate(name, scenario, *extra):
    path = scratch / (name + '.json')
    path.write_text(json.dumps(scenario))
    out = scratch / (name + '.out.json')
    result = subprocess.run([sys.executable, str(ROOT / 'tools/simulate.py'), str(path), '--json', str(out), '--work', str(scratch / ('work-' + name)), *extra],
                            capture_output=True, text=True, timeout=1500)
    check(result.returncode == 0, name + ' ran', (result.stdout + result.stderr)[-2000:])
    return result.stdout, json.loads(out.read_text())


try:
    base = json.loads(EXAMPLE.read_text())
    base['seeds'] = [11, 12, 13]
    text, first = simulate('example', base)
    check('Observed over 3 seeded runs (sampled):' in text and "Exact, from the combat profile's chances (not sampled):" in text
          and 'analytical estimate:' in text, 'report sections', text)
    builds = {r['build']: r for r in first['results']}
    check(set(builds) == {'ember', 'strike'} and all(len(r['runs']) == 3 for r in builds.values())
          and all(run['stop'] == 'killed' for r in builds.values() for run in r['runs']), 'both builds kill', json.dumps(first['results'])[:1500])
    strike = next(s for s in first['summary'] if s['build'] == 'strike')
    check(abs(strike['turns_to_kill']['mean'] - strike['estimate_turns']) <= 0.25 * strike['estimate_turns'], 'observed near exact',
          '%s vs %s' % (strike['turns_to_kill']['mean'], strike['estimate_turns']))
    _, again = simulate('again', base)
    check([r['runs'] for r in again['results']] == [r['runs'] for r in first['results']], 'deterministic', json.dumps(again['results'])[:800])

    # a boss nothing hurts: every run stalls
    immune = dict(base, builds=[b for b in base['builds'] if b['name'] == 'strike'], stall_turns=5, seeds=[1, 2],
                  boss=dict(base['boss'], stats={'schema': 1, 'immune': ['physical', 'fire']}))
    text, out = simulate('immune', immune)
    runs = out['results'][0]['runs']
    check(all(r['stop'] == 'stalled' and r['dealt'] == 0 and r['turns'] == 5 for r in runs) and 'stalled 2' in text, 'stalls', json.dumps(runs))

    # the turn limit and the work limit
    tough = dict(base, builds=[b for b in base['builds'] if b['name'] == 'strike'], turn_limit=10, work_limit=15, seeds=[1, 2],
                 boss=dict(base['boss'], hp=100000))
    text, out = simulate('limits', tough)
    runs = out['results'][0]['runs']
    check(runs[0]['stop'] == 'turn limit' and runs[0]['turns'] == 10 and runs[1]['stop'] == 'work limit' and runs[1]['casts'] == 5
          and 'stopped at the turn limit 1; at the work limit 1' in text, 'limits', json.dumps(runs))

    # a boss that fights back (phase 2): an elite trait, a skill that chills, a guard pool on one build, a combo, a guard ally
    acting = json.loads(ACTING.read_text())
    acting['seeds'] = [21, 22, 23]
    text, act = simulate('acting', acting)
    check('Boss: a frost warden, 300 hit points (400 with its elite traits: Brutish); it fights back with sim.frostclaw' in text
          and 'Allies: Gil the shield-bearer, 80 hit points, role guard' in text and 'the caster fell' in text
          and "the boss's sim.frostclaw on the caster: expected" in text, 'acting report', text)
    results = {r['build']: r for r in act['results']}
    sums = {s['build']: s for s in act['summary']}
    check(all(r['traits'] == ['sim.brute'] and r['boss_hp'] == 400 for r in results.values())
          and all(run['counters'].get('encounters | sim.brute | traits given') == 1 for r in results.values() for run in r['runs']),
          'traits applied', json.dumps(act['results'])[:1500])
    check(all(run['stop'] in ('killed', 'caster fell') and 0 <= run['caster_hp'] <= 150 and run['boss_casts'] > 0 and run['boss_rounds'] > 0
              and run['taken'] > 0 and (run['stop'] != 'caster fell' or run['caster_hp'] == 0) for r in results.values() for run in r['runs']),
          'boss acts', json.dumps(act['results'])[:1500])
    warded, bare = sums['warded'], sums['bare']
    check(warded['guard_absorbed']['caster'] > 0 and 'guard_absorbed' not in bare
          and warded['counters'].get('resources | sim.ward | spent') == warded['guard_absorbed']['caster']
          and 'guard pool absorbed: %d of the boss\'s hits on the caster' % warded['guard_absorbed']['caster'] in text,
          'guard absorbed', json.dumps(warded) + ' / ' + json.dumps(bare))
    check(sum(s.get('ailments', {}).get('caster', {}).get('chill', 0) for s in sums.values()) > 0 and 'ailments begun:' in text
          and 'on the caster chill' in text, 'ailments begun', json.dumps(sums))
    check(all(s['counters'].get('resources | sim.combo | gained', 0) > 0 and s['counters'].get('resources | sim.combo | spent', 0) > 0
              and s['counters'].get('formations | guard | assigned') == 3 for s in sums.values()), 'combos and roles counted', json.dumps(sums))
    check(all('allies_fallen' in s and s['allies_dealt'] >= 0 and 'caster_hp_left' in s for s in sums.values()) and 'allies fallen:' in text,
          'allies reported', json.dumps(sums))
    _, again = simulate('acting-again', dict(acting, builds=[acting['builds'][0]]))
    check(again['results'][0]['runs'] == results['warded']['runs'], 'acting deterministic', json.dumps(again['results'][0]['runs'])[:800])

    # few hit points and no guard beside it: the boss makes the caster fall; a reservation too big for its pool holds
    # nothing, each turn the caster ends standing (every turn but the last)
    frail = dict(acting, seeds=[1, 2], allies=[], builds=[dict(acting['builds'][0], name='frail', hp=20,
                 effects=[{'id': 'Prop_Reserve', 'text': 'POOL=sim.ward AMOUNT=40 NAME="Frost aura"'}])])
    text, out = simulate('frail', frail)
    runs = out['results'][0]['runs']
    check(all(r['stop'] == 'caster fell' and r['caster_hp'] == 0 and r['turns'] >= 2 and r['reservations_suspended'] == {'Frost aura': r['turns'] - 1} for r in runs)
          and 'the caster fell 2' in text and '0 left on average' in text and 'reservations suspended (turns): Frost aura' in text, 'caster falls', json.dumps(runs) + text)
    print('FIXTEST done simulate (%d checks)' % count, flush=True)
except Exception as error:
    print('FIXTEST FAIL simulate: ' + str(error), flush=True)
    raise
