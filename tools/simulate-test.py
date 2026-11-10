#!/usr/bin/env python3
"""Q04: tools/simulate.py on the example scenario and its variants: the same seeds give the same runs, a boss that
takes no damage stalls, the turn and work limits stop a batch, and the report keeps the sampled results apart from
the combat profile's exact figures."""
import json
from pathlib import Path
import subprocess
import sys

ROOT = Path(__file__).resolve().parent.parent
EXAMPLE = ROOT / 'SUDDocs/creator/examples/simulation/ember-vs-strike.json'
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
    print('FIXTEST done simulate (%d checks)' % count, flush=True)
except Exception as error:
    print('FIXTEST FAIL simulate: ' + str(error), flush=True)
    raise
