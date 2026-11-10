#!/usr/bin/env python3
"""Q04: a bounded, deterministic combat simulation of composed builds against a fixed boss.

A scenario file (JSON) names the example packs that make the world, extra definitions, the modules, two or more
builds (composed skills with their supports and the caster's combat stats), a boss (hit points and combat stats), a
seed set, a turn limit, a stall limit and a work limit. Each build runs in its own engine process: for each seed the
campaign dice are seeded, a caster and the boss are made, and the caster casts its first ready skill each turn (the
clock moves one tick a turn, so cooldowns pass) until the boss falls, the turn limit or the work limit is reached, or
the run stalls (no damage for the stall limit's turns in a row). The boss does not act.

The report keeps what was sampled apart from what is exact: the observed results over the seeds (kills, turns to
kill, damage per turn, how each run stopped), and the combat profile's exact expected damage per resolved cast of
each skill (COMPOSE COMPARE's figures), with the analytical estimate of turns that follows from them. The same
scenario and seeds give the same results.

    python3 tools/simulate.py SUDDocs/creator/examples/simulation/ember-vs-strike.json
    python3 tools/simulate.py SCENARIO --seeds 1,2,3 --json results.json
"""
import argparse
import json
import os
from pathlib import Path
import re
import shutil
import statistics
import subprocess
import sys
import tempfile

ROOT = Path(__file__).resolve().parent.parent
GAME = ROOT / 'CoffeeMud'
EXAMPLES = ROOT / 'SUDDocs/creator/examples'
NEWCHAR = 'Y\nN\nHuman\nY\nM\nN\nApprentice\nY\ngood\n\n'

HARNESS = r"""
var CM = Packages.com.planet_ink.coffee_mud, CMC = CM.core.CMClass, AC = CM.core.AbilityComposition, CP = CM.core.CombatProfiles;
var BC = CM.core.BuildCompare, CMath = CM.core.CMath, GT = CM.core.GameTime, CMProps = CM.core.CMProps;
var S = __SCENARIO__, B = __BUILD__;
var room = mob().location();
function stats(m, json) { if (!json) return; var p = CMC.getAbility("Prop_CombatStats"); p.setMiscText(JSON.stringify(json)); m.addNonUninvokableEffect(p); }
function boss() {
	var b = CMC.getMOB("GenMob"); b.setName(S.boss.name || "a training boss"); b.basePhyStats().setLevel(S.boss.level || 1); b.recoverPhyStats();
	b.bringToLife(room, true); b.baseState().setHitPoints(S.boss.hp); b.recoverMaxState(); b.resetToMaxState(); stats(b, S.boss.stats); return b;
}
function caster() {
	var c = CMC.getMOB("GenMob"); c.setName("a simulated " + B.name); c.bringToLife(room, true);
	c.baseState().setMana(B.mana || 100000); c.baseState().setHitPoints(100000); c.recoverMaxState(); c.resetToMaxState();
	var A = CMC.getAbility("Skill_Compose"); A.setProficiency(100); c.addAbility(A);
	for (var k = 0; k < B.skills.length; k++) {
		AC.learn(c, B.skills[k].id);
		var sup = B.skills[k].supports || [];
		for (var j = 0; j < sup.length; j++) AC.link(c, B.skills[k].id, sup[j]);
	}
	stats(c, B.stats); return [c, A];
}
function gone(b) { return b.amDead() || b.amDestroyed() || b.curState().getHitPoints() <= 0; }
function clean() {
	for (var i = room.numItems() - 1; i >= 0; i--) { var I = room.getItem(i); if (I instanceof CM.Items.interfaces.DeadBody) I.destroy(); }
}
var tick = CMProps.getTickMillis(), runs = [], exact = null, work = 0, profile = "";
try {
	for (var si = 0; si < S.seeds.length; si++) {
		CMath.setRandState(S.seeds[si]);
		var b = boss(), made = caster(), c = made[0], A = made[1];
		if (exact == null) {
			exact = []; var p = CP.current(); profile = String(p.key());
			var target = CP.stats(b, p).stats;
			for (var k = 0; k < B.skills.length; k++) {
				var o = BC.outcome(c, AC.compiled(c, B.skills[k].id), target, p);
				exact.push({ skill: String(o.id), expected: o.expected, hit: CM.core.CombatRules.probability(o.hit), critical: CM.core.CombatRules.probability(o.critical),
					blocked: CM.core.CombatRules.probability(o.block), cooldown_ticks: Number(AC.compiled(c, B.skills[k].id).value("cooldown")) });
			}
		}
		var turns = 0, dealt = 0, idle = 0, casts = 0, stop = "";
		while (true) {
			if (gone(b)) { stop = "killed"; break; }
			if (turns >= S.turn_limit) { stop = "turn limit"; break; }
			if (work >= S.work_limit) { stop = "work limit"; break; }
			turns++;
			var id = null;
			for (var k = 0; k < B.skills.length && id == null; k++) if (AC.remaining(c, B.skills[k].id) == 0) id = B.skills[k].id;
			var before = b.curState().getHitPoints();
			if (id != null) { c.curState().setMana(c.maxState().getMana()); A.invoke(c, CM.core.CMParms.parse("CAST " + id), b, false, 0); casts++; work++; }
			var after = gone(b) ? 0 : b.curState().getHitPoints();
			var d = Math.max(0, before - after); dealt += d; idle = (d > 0) ? 0 : idle + 1;
			if (!gone(b) && idle >= S.stall_turns) { stop = "stalled"; break; }
			c.makePeace(true); if (!gone(b)) b.makePeace(true);
			GT.advance(tick);
		}
		runs.push({ seed: S.seeds[si], turns: turns, dealt: dealt, casts: casts, stop: stop });
		c.destroy(); if (!b.amDestroyed()) b.destroy(); clean();
	}
	mob().tell("SIMRESULT " + JSON.stringify({ build: B.name, profile: profile, runs: runs, exact: exact }));
} catch (e) {
	mob().tell("SIMERROR " + B.name + ": " + e);
}
"""


def world_for(scenario, scratch):
    """The world the scenario names: the base world, its packs, its extra definitions and its modules."""
    world = scratch / 'world'
    shutil.copytree(GAME / 'world', world)
    for pack in scenario.get('packs', []):
        source = EXAMPLES / pack
        if not source.is_dir():
            raise SystemExit('scenario: no example pack ' + pack)
        shutil.copytree(source, world, dirs_exist_ok=True, ignore=shutil.ignore_patterns('README.md', '*.py'))
    for folder, items in scenario.get('definitions', {}).items():
        if not re.fullmatch(r'[a-z]+', folder):
            raise SystemExit('scenario: a definitions folder is a plain lower-case name')
        (world / folder).mkdir(parents=True, exist_ok=True)
        for item in items:
            (world / folder / ('%s.v%d.json' % (item['id'], item['version']))).write_text(json.dumps(item))
    ini = world / 'world.ini'
    text = ini.read_text() if ini.exists() else ''
    text = re.sub(r'(?m)^MODULES=.*\n?', '', text) + '\nMODULES=' + scenario['modules'] + '\n'
    ini.write_text(text)
    return world


def run_build(scenario, build, world, scratch, timeout):
    """One build's batch in its own engine process: its runs over every seed."""
    name = re.sub(r'[^a-z0-9]+', '-', build['name'].lower())
    creator = world / 'resources/creator'
    creator.mkdir(parents=True, exist_ok=True)
    (creator / ('sim-%s.js' % name)).write_text(HARNESS.replace('__SCENARIO__', json.dumps(scenario)).replace('__BUILD__', json.dumps(build)))
    batch = scratch / ('batch-' + name)
    (batch / 'db').mkdir(parents=True)
    shutil.copyfile(GAME / 'resources/fakedb/fakedb.schema', batch / 'db/fakedb.schema')
    script = batch / 'input'
    script.write_text('Simulator\n' + NEWCHAR + 'jrun /resources/creator/sim-%s.js\nquit\nn\n' % name)
    with (batch / 'output.txt').open('w') as log:
        result = subprocess.run([sys.executable, str(ROOT / 'tools/sudplay.py'), str(script), 'DBSERVICE=jdbc:fakedb:' + str(batch / 'db'),
                                 'WORLDDIR=' + str(world), 'SAVEDIR=' + str(batch / 'saves'), 'RESTORE=NEW', 'DEV=1', 'TIMEMODE=TURNS',
                                 'RANDOMSEED=%d' % scenario['seeds'][0]],
                                env=dict(os.environ, SUDPLAY_LIMIT=str(timeout)), stdout=log, stderr=subprocess.STDOUT, timeout=timeout + 60)
    text = ' '.join((batch / 'output.txt').read_text(errors='replace').split())
    if 'SIMERROR' in text:
        raise SystemExit('build %s: %s' % (build['name'], text[text.index('SIMERROR'):][:400]))
    at = text.find('SIMRESULT ')
    if result.returncode != 0 or at < 0:
        raise SystemExit('build %s: the engine gave no result; see %s' % (build['name'], batch / 'output.txt'))
    return json.JSONDecoder().raw_decode(text, at + len('SIMRESULT '))[0]


def summary(scenario, result):
    runs = result['runs']
    killed = [r for r in runs if r['stop'] == 'killed']
    turns = [r['turns'] for r in killed]
    out = {
        'build': result['build'], 'runs': len(runs), 'killed': len(killed),
        'stalled': sum(1 for r in runs if r['stop'] == 'stalled'),
        'turn_limit': sum(1 for r in runs if r['stop'] == 'turn limit'),
        'work_limit': sum(1 for r in runs if r['stop'] == 'work limit'),
        'turns_to_kill': None if not turns else {'min': min(turns), 'median': statistics.median(turns), 'mean': round(statistics.mean(turns), 2), 'max': max(turns)},
        'damage_per_turn': round(sum(r['dealt'] for r in runs) / max(1, sum(r['turns'] for r in runs)), 2),
    }
    first = result['exact'][0] if result['exact'] else None
    out['exact'] = result['exact']
    # one skill, cast whenever ready: one cast every max(1, cooldown) turns, as the clock moves a tick a turn
    out['estimate_turns'] = None if (len(result['exact']) != 1 or first['expected'] <= 0) else round(scenario['boss']['hp'] / first['expected'] * max(1, first['cooldown_ticks']), 2)
    return out


def report(scenario, results):
    lines = ['Simulation: ' + scenario.get('title', '(untitled)'),
             'World: packs %s; modules %s; profile %s' % (', '.join(scenario.get('packs', [])), scenario['modules'], results[0]['profile'] if results else '?'),
             'Boss: %s, %d hit points; seeds %s; turn limit %d; stalled after %d turns without damage; work limit %d casts per build'
             % (scenario['boss'].get('name', 'a training boss'), scenario['boss']['hp'], scenario['seeds'], scenario['turn_limit'], scenario['stall_turns'], scenario['work_limit'])]
    sums = []
    for result in results:
        s = summary(scenario, result)
        sums.append(s)
        lines.append('')
        lines.append('Build %s (its own engine process):' % s['build'])
        lines.append('  Observed over %d seeded runs (sampled):' % s['runs'])
        lines.append('    killed %d; stalled %d; stopped at the turn limit %d; at the work limit %d' % (s['killed'], s['stalled'], s['turn_limit'], s['work_limit']))
        if s['turns_to_kill']:
            t = s['turns_to_kill']
            lines.append('    turns to kill: min %d, median %s, mean %s, max %d' % (t['min'], t['median'], t['mean'], t['max']))
        lines.append('    damage per turn: %s' % s['damage_per_turn'])
        lines.append("  Exact, from the combat profile's chances (not sampled):")
        for e in s['exact']:
            lines.append('    %s: expected %.2f damage per resolved cast (hit %d%%, blocked %d%%, critical %d%%); cooldown %d tick(s)'
                         % (e['skill'], e['expected'], round(e['hit'] * 100), round(e['blocked'] * 100), round(e['critical'] * 100), e['cooldown_ticks']))
        if s['estimate_turns'] is not None:
            lines.append('    analytical estimate: %s turns to kill (hit points over the expected damage of a cast, one cast whenever the skill is ready)' % s['estimate_turns'])
        elif len(s['exact']) > 1:
            lines.append('    analytical estimate: none for a build of several skills (the order they come ready in decides it)')
    lines.append('')
    ranked = [s for s in sums if s['turns_to_kill']]
    if len(ranked) >= 2:
        ranked.sort(key=lambda s: s['turns_to_kill']['mean'])
        lines.append('Observed means: ' + '; '.join('%s kills in %s turns' % (s['build'], s['turns_to_kill']['mean']) for s in ranked) + '.')
    lines.append("The exact figures leave out a cast's proficiency check and the world's own rules; the observed runs include them.")
    return '\n'.join(lines), sums


def validate(scenario):
    for key in ('modules', 'builds', 'boss', 'seeds', 'turn_limit', 'stall_turns', 'work_limit'):
        if key not in scenario:
            raise SystemExit('scenario: needs ' + key)
    if not (1 <= len(scenario['seeds']) <= 64):
        raise SystemExit('scenario: 1 to 64 seeds')
    if not (1 <= scenario['turn_limit'] <= 1000 and 1 <= scenario['stall_turns'] <= 100 and 1 <= scenario['work_limit'] <= 100000):
        raise SystemExit('scenario: turn_limit 1..1000, stall_turns 1..100, work_limit 1..100000')
    if not (1 <= len(scenario['builds']) <= 8):
        raise SystemExit('scenario: 1 to 8 builds')
    for build in scenario['builds']:
        if not build.get('skills'):
            raise SystemExit('scenario: build %s names no skill' % build.get('name'))


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('scenario')
    parser.add_argument('--seeds', help='comma-separated seeds instead of the scenario\'s')
    parser.add_argument('--json', help='also write the results as JSON here')
    parser.add_argument('--work', help='a scratch directory (kept); by default a temporary one, removed')
    parser.add_argument('--timeout', type=int, default=420, help='seconds each engine process may take')
    args = parser.parse_args()
    scenario = json.loads(Path(args.scenario).read_text())
    if args.seeds:
        scenario['seeds'] = [int(x) for x in args.seeds.split(',') if x.strip()]
    validate(scenario)
    scratch = Path(args.work).resolve() if args.work else Path(tempfile.mkdtemp(prefix='sud-sim-'))
    scratch.mkdir(parents=True, exist_ok=True)
    try:
        world = world_for(scenario, scratch)
        results = [run_build(scenario, build, world, scratch, args.timeout) for build in scenario['builds']]
        text, sums = report(scenario, results)
        print(text)
        if args.json:
            Path(args.json).write_text(json.dumps({'scenario': scenario, 'results': results, 'summary': sums}, indent=1, sort_keys=True))
    finally:
        if not args.work:
            shutil.rmtree(scratch, ignore_errors=True)
    return 0


if __name__ == '__main__':
    sys.exit(main())
